import 'dart:async';
import 'dart:convert';
import 'package:flutter/material.dart';
import 'package:http/http.dart' as http;
import '../config/environment_config.dart';
import '../services/call_notification_service.dart';

/// VideoCallService — AWS Chime SDK video call implementation.
///
/// Replaces legacy non-Chime call implementations.
///
/// Flow:
///   1. Flutter calls joinCall() after call is accepted
///   2. Service hits POST /api/v3/calls/{callId}/join on Spring Boot
///   3. Spring Boot creates/joins the Chime meeting and returns credentials
///   4. Flutter uses those credentials to render the call UI
///   5. Sentiment data is posted periodically during the call
///   6. endCall() hits POST /api/v3/calls/{callId}/end
///
/// Note on Chime rendering: The AWS Chime SDK for Flutter renders video
/// using a platform view. For the capstone demo, we render a web view
/// pointing to the Chime meeting URL, which works on both mobile and web.
class VideoCallService {
  static const Duration _sentimentStaleThreshold = Duration(seconds: 45);

  bool _isInitialized = false;
  bool _isInCall = false;
  bool _isPatientSentimentSource = false;
  String? _currentCallId;
  String? _otherPartyId;
  String? _jwtToken;

  // Chime meeting credentials returned by the backend
  Map<String, dynamic>? _meetingCredentials;

  // Callbacks
  VoidCallback? _onCallEnded;
  Function(Map<String, dynamic>)? _onSentimentUpdate;
  Function(Map<String, dynamic>)? _onCallDeclined;

  // Sentiment posting timer — sends analysis data every 15 seconds
  Timer? _sentimentTimer;

  // Stream for sentiment updates received via WebSocket
  StreamSubscription? _wsSubscription;

  // Aggregated sentiment state used by caregiver dashboard UI
  final Map<String, dynamic> _aggregatedSentiment = {};

  // ================================================================
  // INITIALIZE
  // ================================================================

  Future<void> initialize({
    required String userId,
    required String jwtToken,
    required bool enablePatientSentimentCapture,
    VoidCallback? onCallEnded,
    Function(Map<String, dynamic>)? onSentimentUpdate,
    Function(Map<String, dynamic>)? onCallDeclined,
  }) async {
    _jwtToken = jwtToken;
    _onCallEnded = onCallEnded;
    _onSentimentUpdate = onSentimentUpdate;
    _onCallDeclined = onCallDeclined;
    _isPatientSentimentSource = enablePatientSentimentCapture;
    _isInitialized = true;

    // Listen for sentiment updates pushed via WebSocket
    _wsSubscription = CallNotificationService.incomingCallStream.listen((data) {
      final type = data['type'] as String?;
      if (type == 'sentiment-update' && _onSentimentUpdate != null) {
        final merged = _mergeSentimentUpdate(data);
        _onSentimentUpdate!(merged);
      }
      if (type == 'sentiment-channel-state' && _onSentimentUpdate != null) {
        final merged = _mergeChannelStateEvent(data);
        _onSentimentUpdate!(merged);
      }
      if (type == 'call-declined' && _onCallDeclined != null) {
        final declinedCallId = (data['callId'] ?? '').toString();
        if (declinedCallId.isNotEmpty &&
            (_currentCallId == null || declinedCallId == _currentCallId)) {
          _onCallDeclined!(data);
        }
      }
      if (type == 'call-ended') {
        _handleRemoteCallEnd();
      }
    });

    debugPrint('✅ VideoCallService initialized for user: $userId');
  }

  // ================================================================
  // JOIN CALL
  // Both initiator and recipient call this after call is accepted
  // ================================================================

  Future<ChimeCallSession> joinCall({
    required String callId,
    required String otherPartyId,
    required bool isVideoEnabled,
    required bool isAudioEnabled,
  }) async {
    if (!_isInitialized) throw Exception('VideoCallService not initialized');

    _currentCallId = callId;
    _otherPartyId = otherPartyId;
    _isInCall = true;
    _aggregatedSentiment.clear();
    _seedAwaitingSentimentState();

    debugPrint('📹 Joining Chime call: $callId');

    try {
      final response = await http.post(
        Uri.parse('${EnvironmentConfig.baseUrl}/api/v3/calls/$callId/join'),
        headers: {
          'Content-Type': 'application/json',
          'Authorization': 'Bearer $_jwtToken',
        },
      );

      if (response.statusCode != 200) {
        throw Exception(
          'Failed to join call: ${response.statusCode} ${response.body}',
        );
      }

      _meetingCredentials = jsonDecode(response.body) as Map<String, dynamic>;
      debugPrint('✅ Chime meeting credentials received for call: $callId');

      // Combined sentiment is now posted from real channel capture flow
      // (transcript/voice/video). Do not send periodic empty combined payloads.

      return ChimeCallSession(
        callId: callId,
        meetingId: _meetingCredentials!['meetingId'] as String,
        attendeeId: _meetingCredentials!['attendeeId'] as String,
        joinToken: _meetingCredentials!['joinToken'] as String,
        mediaPlacement:
            _meetingCredentials!['mediaPlacement'] as Map<String, dynamic>,
        mediaRegion: _meetingCredentials!['mediaRegion'] as String?,
        externalUserId: _meetingCredentials!['externalUserId'] as String?,
        isVideoEnabled: isVideoEnabled,
        isAudioEnabled: isAudioEnabled,
      );
    } catch (e) {
      _isInCall = false;
      debugPrint('❌ Failed to join Chime call: $e');
      rethrow;
    }
  }

  // ================================================================
  // END CALL
  // ================================================================

  Future<void> endCall() async {
    if (!_isInCall || _currentCallId == null) return;

    debugPrint('📴 Ending call: $_currentCallId');

    _sentimentTimer?.cancel();

    try {
      await http.post(
        Uri.parse(
          '${EnvironmentConfig.baseUrl}/api/v3/calls/$_currentCallId/end',
        ),
        headers: {
          'Content-Type': 'application/json',
          'Authorization': 'Bearer $_jwtToken',
        },
        body: jsonEncode({'otherPartyId': _otherPartyId}),
      );
    } catch (e) {
      debugPrint('⚠️ Error notifying backend of call end: $e');
    }

    _isInCall = false;
    _currentCallId = null;
    _meetingCredentials = null;
    _aggregatedSentiment.clear();
    _onCallEnded?.call();
  }

  // ================================================================
  // SENTIMENT — TEXT
  // Called when a chat message is sent during the call
  // ================================================================

  Future<bool> sendTextForAnalysis(String text, {String? captureMode}) async {
    if (!_isPatientSentimentSource || !_isInCall || _currentCallId == null) {
      return false;
    }

    try {
      final response = await http.post(
        Uri.parse(
          '${EnvironmentConfig.baseUrl}/api/v3/calls/$_currentCallId/sentiment/text',
        ),
        headers: {
          'Content-Type': 'application/json',
          'Authorization': 'Bearer $_jwtToken',
        },
        body: jsonEncode({
          'text': text,
          'otherPartyId': _otherPartyId,
          'captureMode': captureMode,
        }),
      );

      if (response.statusCode >= 200 && response.statusCode < 300) {
        return true;
      }

      debugPrint(
        '⚠️ Text sentiment request failed: ${response.statusCode} ${response.body}',
      );
      return false;
    } catch (e) {
      debugPrint('⚠️ Text sentiment error: $e');
      return false;
    }
  }

  Future<bool> sendVoiceMetricsForAnalysis({
    required double averageLevel,
    required double speechRatio,
    required double variability,
    String? captureMode,
  }) async {
    if (!_isPatientSentimentSource || !_isInCall || _currentCallId == null) {
      return false;
    }

    try {
      final response = await http.post(
        Uri.parse(
          '${EnvironmentConfig.baseUrl}/api/v3/calls/$_currentCallId/sentiment/voice',
        ),
        headers: {
          'Content-Type': 'application/json',
          'Authorization': 'Bearer $_jwtToken',
        },
        body: jsonEncode({
          'averageLevel': averageLevel.toStringAsFixed(4),
          'speechRatio': speechRatio.toStringAsFixed(4),
          'variability': variability.toStringAsFixed(4),
          'audioFormat': 'chime-metrics',
          'otherPartyId': _otherPartyId,
          'captureMode': captureMode,
        }),
      );

      if (response.statusCode >= 200 && response.statusCode < 300) {
        return true;
      }

      debugPrint(
        '⚠️ Voice metrics sentiment request failed: ${response.statusCode} ${response.body}',
      );
      return false;
    } catch (e) {
      debugPrint('⚠️ Voice metrics sentiment error: $e');
      return false;
    }
  }

  // ================================================================
  // SENTIMENT — VIDEO FRAME
  // Called with a base64 JPEG frame capture every ~15 seconds
  // ================================================================

  Future<bool> sendVideoFrameForAnalysis(
    String imageBase64, {
    String? captureMode,
  }) async {
    if (!_isPatientSentimentSource || !_isInCall || _currentCallId == null) {
      return false;
    }

    try {
      final response = await http.post(
        Uri.parse(
          '${EnvironmentConfig.baseUrl}/api/v3/calls/$_currentCallId/sentiment/video',
        ),
        headers: {
          'Content-Type': 'application/json',
          'Authorization': 'Bearer $_jwtToken',
        },
        body: jsonEncode({
          'imageBase64': imageBase64,
          'imageFormat': 'jpeg',
          'otherPartyId': _otherPartyId,
          'captureMode': captureMode,
        }),
      );

      if (response.statusCode >= 200 && response.statusCode < 300) {
        return true;
      }

      debugPrint(
        '⚠️ Video sentiment request failed: ${response.statusCode} ${response.body}',
      );
      return false;
    } catch (e) {
      debugPrint('⚠️ Video sentiment error: $e');
      return false;
    }
  }

  Future<bool> updateSentimentChannelState({
    required String channel,
    required bool muted,
    String? captureMode,
  }) async {
    if (!_isPatientSentimentSource || !_isInCall || _currentCallId == null) {
      return false;
    }

    if (_otherPartyId == null || _otherPartyId!.trim().isEmpty) {
      return false;
    }

    return CallNotificationService.sendSentimentChannelState(
      callId: _currentCallId!,
      otherPartyId: _otherPartyId!,
      channel: channel,
      muted: muted,
      captureMode: captureMode,
    );
  }

  // ================================================================
  // PRIVATE
  // ================================================================

  void _startSentimentTimer() {
    _sentimentTimer?.cancel();
  }

  Future<void> _postCombinedSentiment() async {}

  void _handleRemoteCallEnd() {
    if (!_isInCall) return;
    debugPrint('📴 Remote party ended the call');
    _sentimentTimer?.cancel();
    _isInCall = false;
    _aggregatedSentiment.clear();
    _onCallEnded?.call();
  }

  Map<String, dynamic> _mergeSentimentUpdate(Map<String, dynamic> event) {
    final payload = (event['sentiment'] is Map<String, dynamic>)
        ? Map<String, dynamic>.from(event['sentiment'] as Map<String, dynamic>)
        : <String, dynamic>{};
    final captureMode = (event['captureMode'] as String?)?.trim();

    final now = DateTime.now().toUtc();

    if (payload.isEmpty) {
      _markStaleChannels(now);
      _aggregatedSentiment['overall'] = _buildOverallFromChannels();
      return Map<String, dynamic>.from(_aggregatedSentiment);
    }

    final channel = (payload['channel'] as String?)?.toLowerCase();
    final hasPerChannelShape =
        payload.containsKey('text') ||
        payload.containsKey('voice') ||
        payload.containsKey('video') ||
        payload.containsKey('overall');

    if (hasPerChannelShape) {
      for (final key in ['text', 'voice', 'video', 'overall']) {
        final section = payload[key];
        if (section is Map<String, dynamic>) {
          _aggregatedSentiment[key] = _normalizeSentimentSection(
            key,
            Map<String, dynamic>.from(section),
            now,
          );
        }
      }
    } else if (channel == 'text' || channel == 'voice' || channel == 'video') {
      _aggregatedSentiment[channel!] = _normalizeSentimentSection(
        channel,
        payload,
        now,
      );
    }

    _markStaleChannels(now);
    _aggregatedSentiment['overall'] = _buildOverallFromChannels();
    if (captureMode != null && captureMode.isNotEmpty) {
      _aggregatedSentiment['_captureMode'] = captureMode.toUpperCase();
    }

    return Map<String, dynamic>.from(_aggregatedSentiment);
  }

  Map<String, dynamic> _mergeChannelStateEvent(Map<String, dynamic> event) {
    final channel = (event['channel'] as String?)?.trim().toLowerCase();
    if (channel != 'text' && channel != 'voice' && channel != 'video') {
      return Map<String, dynamic>.from(_aggregatedSentiment);
    }

    final muted = event['muted'] == true;
    final now = DateTime.now().toUtc();
    final existing = _aggregatedSentiment[channel] is Map<String, dynamic>
        ? Map<String, dynamic>.from(
            _aggregatedSentiment[channel] as Map<String, dynamic>,
          )
        : <String, dynamic>{};

    final score = (existing['score'] as num?)?.toDouble() ?? 0.5;
    final label =
        (existing['label'] as String?)?.toUpperCase() ?? _labelFromScore(score);

    _aggregatedSentiment[channel!] = {
      'score': score,
      'label': label,
      'notes': muted
          ? 'Channel Muted'
          : 'Awaiting ${channel.toLowerCase()} sentiment sample.',
      'status': muted ? 'MUTED' : 'AWAITING',
      'channel': channel,
      'updatedAt': now.toIso8601String(),
      'stale': false,
      'confidence': muted ? 0.0 : 0.5,
    };

    _markStaleChannels(now);
    _aggregatedSentiment['overall'] = _buildOverallFromChannels();

    final captureMode = (event['captureMode'] as String?)?.trim();
    if (captureMode != null && captureMode.isNotEmpty) {
      _aggregatedSentiment['_captureMode'] = captureMode.toUpperCase();
    }

    return Map<String, dynamic>.from(_aggregatedSentiment);
  }

  Map<String, dynamic> _buildOverallFromChannels() {
    final channels = ['text', 'voice', 'video'];
    var scoreSum = 0.0;
    var count = 0;
    var hasDegraded = false;
    var hasAwaiting = false;

    for (final key in channels) {
      final channelData = _aggregatedSentiment[key];
      if (channelData is Map<String, dynamic>) {
        final status = (channelData['status'] as String? ?? 'AWAITING')
            .toUpperCase();
        if (status == 'DEGRADED') hasDegraded = true;
        if (status == 'AWAITING' || status == 'MUTED') hasAwaiting = true;
        if (status != 'COMPLETED') {
          continue;
        }

        final score = (channelData['score'] as num?)?.toDouble();
        if (score != null) {
          scoreSum += score;
          count += 1;
        }
      }
    }

    if (count == 0) {
      return {
        'score': 0.5,
        'label': 'NEUTRAL',
        'status': hasDegraded ? 'DEGRADED' : 'AWAITING',
        'notes': hasDegraded
            ? 'Sentiment temporarily unavailable; call continues normally.'
            : 'Awaiting sentiment samples',
        'updatedAt': DateTime.now().toUtc().toIso8601String(),
      };
    }

    final average = scoreSum / count;
    return {
      'score': average,
      'label': _labelFromScore(average),
      'status': (hasDegraded || hasAwaiting) ? 'DEGRADED' : 'COMPLETED',
      'notes': (hasDegraded || hasAwaiting)
          ? 'Computed from available channels (partial data).'
          : 'Computed from all available channels.',
      'updatedAt': DateTime.now().toUtc().toIso8601String(),
    };
  }

  void _seedAwaitingSentimentState() {
    final now = DateTime.now().toUtc();
    for (final channel in ['text', 'voice', 'video']) {
      _aggregatedSentiment[channel] = {
        'score': 0.5,
        'label': 'NEUTRAL',
        'notes': 'Awaiting $channel sentiment sample.',
        'status': 'AWAITING',
        'channel': channel,
        'updatedAt': now.toIso8601String(),
        'stale': false,
        'confidence': 0.0,
      };
    }
    _aggregatedSentiment['overall'] = {
      'score': 0.5,
      'label': 'NEUTRAL',
      'status': 'AWAITING',
      'notes': 'Awaiting sentiment samples',
      'updatedAt': now.toIso8601String(),
    };
  }

  Map<String, dynamic> _normalizeSentimentSection(
    String sectionKey,
    Map<String, dynamic> raw,
    DateTime now,
  ) {
    final normalizedChannel = (raw['channel'] as String? ?? sectionKey)
        .toLowerCase();
    final rawScore = (raw['score'] as num?)?.toDouble();
    final clampedScore = rawScore == null ? 0.5 : rawScore.clamp(0.0, 1.0);

    final rawStatus = (raw['status'] as String?)?.toUpperCase();
    final rawNotes = (raw['notes'] as String?)?.trim() ?? '';
    final fallback =
        raw['fallback'] == true || _isFallbackSentimentNotes(rawNotes);
    final status =
        rawStatus ??
        (rawScore == null ? 'AWAITING' : (fallback ? 'DEGRADED' : 'COMPLETED'));

    final notes = rawNotes.isNotEmpty
        ? rawNotes
        : (status == 'AWAITING'
              ? 'Awaiting $normalizedChannel sentiment sample.'
              : 'Sentiment sample received.');

    final updatedAt =
        _parseEventTime(raw['updatedAt']) ??
        _parseEventTime(raw['timestamp']) ??
        now;

    final label =
        (raw['label'] as String?)?.toUpperCase() ??
        _labelFromScore(clampedScore);

    return {
      'score': clampedScore,
      'label': label,
      'notes': notes,
      'status': status,
      'channel': normalizedChannel,
      'updatedAt': updatedAt.toIso8601String(),
      'stale': false,
      'confidence':
          (raw['confidence'] as num?)?.toDouble() ?? (fallback ? 0.0 : 1.0),
    };
  }

  bool _isFallbackSentimentNotes(String notes) {
    if (notes.isEmpty) {
      return false;
    }

    final lower = notes.toLowerCase();
    return lower.contains('analysis unavailable') ||
        lower.contains('temporarily unavailable') ||
        lower.contains('bedrock disabled') ||
        lower.contains('parse error') ||
        lower.contains('empty response') ||
        lower.contains('no voice sample') ||
        lower.contains('no video sample') ||
        lower.contains('no text sample');
  }

  DateTime? _parseEventTime(dynamic value) {
    if (value is String && value.isNotEmpty) {
      return DateTime.tryParse(value)?.toUtc();
    }
    if (value is int) {
      return DateTime.fromMillisecondsSinceEpoch(value, isUtc: true);
    }
    return null;
  }

  void _markStaleChannels(DateTime nowUtc) {
    for (final key in ['text', 'voice', 'video']) {
      final channel = _aggregatedSentiment[key];
      if (channel is! Map<String, dynamic>) continue;

      final status = (channel['status'] as String? ?? 'AWAITING').toUpperCase();
      final updatedAt = _parseEventTime(channel['updatedAt']);

      if (status == 'COMPLETED' &&
          updatedAt != null &&
          nowUtc.difference(updatedAt) > _sentimentStaleThreshold) {
        channel['status'] = 'DEGRADED';
        channel['stale'] = true;
        channel['notes'] = 'Sentiment sample is stale; awaiting refresh.';
      } else if (status == 'COMPLETED') {
        channel['stale'] = false;
      }
    }
  }

  String _labelFromScore(double score) {
    if (score >= 0.65) return 'CALM';
    if (score >= 0.55) return 'POSITIVE';
    if (score >= 0.35) return 'NEUTRAL';
    if (score >= 0.25) return 'ANXIOUS';
    return 'DISTRESSED';
  }

  bool get isInCall => _isInCall;
  String? get currentCallId => _currentCallId;
  Map<String, dynamic>? get meetingCredentials => _meetingCredentials;

  void setPatientSentimentSourceEnabled(bool enabled) {
    _isPatientSentimentSource = enabled;
  }

  void dispose() {
    _sentimentTimer?.cancel();
    _wsSubscription?.cancel();
    _aggregatedSentiment.clear();
    _onCallDeclined = null;
    _isPatientSentimentSource = false;
    _isInitialized = false;
    _isInCall = false;
  }
}

// ================================================================
// DATA CLASS — Chime call session credentials
// ================================================================

class ChimeCallSession {
  final String callId;
  final String meetingId;
  final String attendeeId;
  final String joinToken;
  final Map<String, dynamic> mediaPlacement;
  final String? mediaRegion;
  final String? externalUserId;
  final bool isVideoEnabled;
  final bool isAudioEnabled;

  const ChimeCallSession({
    required this.callId,
    required this.meetingId,
    required this.attendeeId,
    required this.joinToken,
    required this.mediaPlacement,
    this.mediaRegion,
    this.externalUserId,
    required this.isVideoEnabled,
    required this.isAudioEnabled,
  });
}
