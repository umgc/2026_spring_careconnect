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
        throw Exception('Failed to join call: ${response.statusCode} ${response.body}');
      }

      _meetingCredentials = jsonDecode(response.body) as Map<String, dynamic>;
      debugPrint('✅ Chime meeting credentials received for call: $callId');

      if (_isPatientSentimentSource) {
        _startSentimentTimer();
      }

      return ChimeCallSession(
        callId:          callId,
        meetingId:       _meetingCredentials!['meetingId'] as String,
        attendeeId:      _meetingCredentials!['attendeeId'] as String,
        joinToken:       _meetingCredentials!['joinToken'] as String,
        mediaPlacement:  _meetingCredentials!['mediaPlacement'] as Map<String, dynamic>,
        mediaRegion:     _meetingCredentials!['mediaRegion'] as String?,
        externalUserId:  _meetingCredentials!['externalUserId'] as String?,
        isVideoEnabled:  isVideoEnabled,
        isAudioEnabled:  isAudioEnabled,
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
        Uri.parse('${EnvironmentConfig.baseUrl}/api/v3/calls/$_currentCallId/end'),
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

  Future<Map<String, dynamic>> startRecording({
    required int patientUserId,
    required bool consentConfirmed,
    String? consentNote,
  }) async {
    if (!_isInCall || _currentCallId == null) {
      throw Exception('Cannot start recording when call is not active');
    }

    final response = await http.post(
      Uri.parse('${EnvironmentConfig.baseUrl}/api/v3/calls/$_currentCallId/recording/start'),
      headers: {
        'Content-Type': 'application/json',
        'Authorization': 'Bearer $_jwtToken',
      },
      body: jsonEncode({
        'patientUserId': patientUserId,
        'consentConfirmed': consentConfirmed,
        'consentNote': consentNote,
      }),
    );

    if (response.statusCode < 200 || response.statusCode >= 300) {
      String message = 'Failed to start recording (${response.statusCode})';
      try {
        final decoded = jsonDecode(response.body);
        if (decoded is Map) {
          final backendMessage =
              (decoded['message'] ?? decoded['error'] ?? '').toString().trim();
          if (backendMessage.isNotEmpty) {
            message = backendMessage;
          }
        }
      } catch (_) {
        // Keep fallback message.
      }
      throw Exception(message);
    }

    return Map<String, dynamic>.from(jsonDecode(response.body) as Map);
  }

  Future<Map<String, dynamic>> stopRecording({
    required int patientUserId,
    String? reason,
  }) async {
    if (!_isInCall || _currentCallId == null) {
      throw Exception('Cannot stop recording when call is not active');
    }

    final response = await http.post(
      Uri.parse('${EnvironmentConfig.baseUrl}/api/v3/calls/$_currentCallId/recording/stop'),
      headers: {
        'Content-Type': 'application/json',
        'Authorization': 'Bearer $_jwtToken',
      },
      body: jsonEncode({
        'patientUserId': patientUserId,
        'reason': reason,
      }),
    );

    if (response.statusCode < 200 || response.statusCode >= 300) {
      throw Exception('Failed to stop recording: ${response.statusCode} ${response.body}');
    }

    return Map<String, dynamic>.from(jsonDecode(response.body) as Map);
  }

  Future<Map<String, dynamic>> getRecordingStatus({
    required int patientUserId,
  }) async {
    if (!_isInCall || _currentCallId == null) {
      return const {'recordingActive': false, 'status': 'NOT_RECORDING'};
    }

    final response = await http.get(
      Uri.parse('${EnvironmentConfig.baseUrl}/api/v3/calls/$_currentCallId/recording/status?patientUserId=$patientUserId'),
      headers: {
        'Content-Type': 'application/json',
        'Authorization': 'Bearer $_jwtToken',
      },
    );

    if (response.statusCode < 200 || response.statusCode >= 300) {
      throw Exception('Failed to get recording status: ${response.statusCode} ${response.body}');
    }

    return Map<String, dynamic>.from(jsonDecode(response.body) as Map);
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
        Uri.parse('${EnvironmentConfig.baseUrl}/api/v3/calls/$_currentCallId/sentiment/text'),
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

      debugPrint('⚠️ Text sentiment request failed: ${response.statusCode} ${response.body}');
      return false;
    } catch (e) {
      debugPrint('⚠️ Text sentiment error: $e');
      return false;
    }
  }

  // ================================================================
  // SENTIMENT — VOICE
  // Called with a base64 audio chunk every ~15 seconds
  // ================================================================

  Future<bool> sendAudioForAnalysis(
    String audioBase64, {
    String audioFormat = 'wav',
    String? captureMode,
  }) async {
    if (!_isPatientSentimentSource || !_isInCall || _currentCallId == null) {
      return false;
    }

    try {
      final response = await http.post(
        Uri.parse('${EnvironmentConfig.baseUrl}/api/v3/calls/$_currentCallId/sentiment/voice'),
        headers: {
          'Content-Type': 'application/json',
          'Authorization': 'Bearer $_jwtToken',
        },
        body: jsonEncode({
          'audioBase64': audioBase64,
          'audioFormat': audioFormat,
          'otherPartyId': _otherPartyId,
          'captureMode': captureMode,
        }),
      );

      if (response.statusCode < 200 || response.statusCode >= 300) {
        debugPrint('⚠️ Voice sentiment request failed: ${response.statusCode} ${response.body}');
        return false;
      }

      return true;
    } catch (e) {
      debugPrint('⚠️ Voice sentiment error: $e');
      return false;
    }
  }

  // ================================================================
  // SENTIMENT — VIDEO FRAME
  // Called with a base64 JPEG frame capture every ~15 seconds
  // ================================================================

  Future<bool> sendVideoFrameForAnalysis(String imageBase64, {String? captureMode}) async {
    if (!_isPatientSentimentSource || !_isInCall || _currentCallId == null) {
      return false;
    }

    try {
      final response = await http.post(
        Uri.parse('${EnvironmentConfig.baseUrl}/api/v3/calls/$_currentCallId/sentiment/video'),
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

      debugPrint('⚠️ Video sentiment request failed: ${response.statusCode} ${response.body}');
      return false;
    } catch (e) {
      debugPrint('⚠️ Video sentiment error: $e');
      return false;
    }
  }

  // ================================================================
  // PRIVATE
  // ================================================================

  void _startSentimentTimer() {
    _sentimentTimer?.cancel();
    // Post combined sentiment every 15 seconds
    // In Phase 2, this will capture real audio/video data
    // For Phase 1 demo, voice and video fall back gracefully to neutral
    _sentimentTimer = Timer.periodic(const Duration(seconds: 15), (_) {
      _postCombinedSentiment();
    });
  }

  Future<void> _postCombinedSentiment() async {
    if (!_isPatientSentimentSource || !_isInCall || _currentCallId == null) return;
    try {
      // Phase 1: text-only combined sentiment
      // Phase 2: will include audioBase64 and imageBase64
      await http.post(
        Uri.parse('${EnvironmentConfig.baseUrl}/api/v3/calls/$_currentCallId/sentiment/combined'),
        headers: {
          'Content-Type': 'application/json',
          'Authorization': 'Bearer $_jwtToken',
        },
        body: jsonEncode({'otherPartyId': _otherPartyId}),
      );
    } catch (e) {
      debugPrint('⚠️ Combined sentiment error: $e');
    }
  }

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
    final hasPerChannelShape = payload.containsKey('text') ||
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

  Map<String, dynamic> _buildOverallFromChannels() {
    final channels = ['text', 'voice', 'video'];
    var scoreSum = 0.0;
    var count = 0;
    var hasDegraded = false;
    var hasAwaiting = false;

    for (final key in channels) {
      final channelData = _aggregatedSentiment[key];
      if (channelData is Map<String, dynamic>) {
        final status = (channelData['status'] as String? ?? 'AWAITING').toUpperCase();
        if (status == 'DEGRADED') hasDegraded = true;
        if (status == 'AWAITING') hasAwaiting = true;
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
    final normalizedChannel = (raw['channel'] as String? ?? sectionKey).toLowerCase();
    final rawScore = (raw['score'] as num?)?.toDouble();
    final clampedScore = rawScore == null ? 0.5 : rawScore.clamp(0.0, 1.0);

    final rawStatus = (raw['status'] as String?)?.toUpperCase();
    final status = rawStatus ?? (rawScore == null ? 'AWAITING' : 'COMPLETED');

    final notes = (raw['notes'] as String?)?.trim().isNotEmpty == true
        ? (raw['notes'] as String).trim()
        : (status == 'AWAITING'
            ? 'Awaiting $normalizedChannel sentiment sample.'
            : 'Sentiment sample received.');

    final updatedAt = _parseEventTime(raw['updatedAt']) ??
        _parseEventTime(raw['timestamp']) ??
        now;

    final label = (raw['label'] as String?)?.toUpperCase() ?? _labelFromScore(clampedScore);

    return {
      'score': clampedScore,
      'label': label,
      'notes': notes,
      'status': status,
      'channel': normalizedChannel,
      'updatedAt': updatedAt.toIso8601String(),
      'stale': false,
      'confidence': (raw['confidence'] as num?)?.toDouble() ?? 1.0,
    };
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
