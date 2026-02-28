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
  bool _isInitialized = false;
  bool _isInCall = false;
  String? _currentCallId;
  String? _currentUserId;
  String? _otherPartyId;
  String? _jwtToken;

  // Chime meeting credentials returned by the backend
  Map<String, dynamic>? _meetingCredentials;

  // Callbacks
  VoidCallback? _onCallEnded;
  Function(Map<String, dynamic>)? _onSentimentUpdate;

  // Sentiment posting timer — sends analysis data every 15 seconds
  Timer? _sentimentTimer;

  // Stream for sentiment updates received via WebSocket
  StreamSubscription? _wsSubscription;

  // ================================================================
  // INITIALIZE
  // ================================================================

  Future<void> initialize({
    required String userId,
    required String jwtToken,
    VoidCallback? onCallEnded,
    Function(Map<String, dynamic>)? onSentimentUpdate,
  }) async {
    _currentUserId = userId;
    _jwtToken = jwtToken;
    _onCallEnded = onCallEnded;
    _onSentimentUpdate = onSentimentUpdate;
    _isInitialized = true;

    // Listen for sentiment updates pushed via WebSocket
    _wsSubscription = CallNotificationService.incomingCallStream.listen((data) {
      final type = data['type'] as String?;
      if (type == 'sentiment-update' && _onSentimentUpdate != null) {
        _onSentimentUpdate!(data);
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

      // Start periodic sentiment analysis
      _startSentimentTimer();

      return ChimeCallSession(
        callId:          callId,
        meetingId:       _meetingCredentials!['meetingId'] as String,
        attendeeId:      _meetingCredentials!['attendeeId'] as String,
        joinToken:       _meetingCredentials!['joinToken'] as String,
        mediaPlacement:  _meetingCredentials!['mediaPlacement'] as Map<String, dynamic>,
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
    _onCallEnded?.call();
  }

  // ================================================================
  // SENTIMENT — TEXT
  // Called when a chat message is sent during the call
  // ================================================================

  Future<void> sendTextForAnalysis(String text) async {
    if (!_isInCall || _currentCallId == null) return;
    try {
      await http.post(
        Uri.parse('${EnvironmentConfig.baseUrl}/api/v3/calls/$_currentCallId/sentiment/text'),
        headers: {
          'Content-Type': 'application/json',
          'Authorization': 'Bearer $_jwtToken',
        },
        body: jsonEncode({
          'text':         text,
          'otherPartyId': _otherPartyId,
        }),
      );
    } catch (e) {
      debugPrint('⚠️ Text sentiment error: $e');
    }
  }

  // ================================================================
  // SENTIMENT — VOICE
  // Called with a base64 audio chunk every ~15 seconds
  // ================================================================

  Future<void> sendAudioForAnalysis(String audioBase64) async {
    if (!_isInCall || _currentCallId == null) return;
    try {
      await http.post(
        Uri.parse('${EnvironmentConfig.baseUrl}/api/v3/calls/$_currentCallId/sentiment/voice'),
        headers: {
          'Content-Type': 'application/json',
          'Authorization': 'Bearer $_jwtToken',
        },
        body: jsonEncode({
          'audioBase64':  audioBase64,
          'otherPartyId': _otherPartyId,
        }),
      );
    } catch (e) {
      debugPrint('⚠️ Voice sentiment error: $e');
    }
  }

  // ================================================================
  // SENTIMENT — VIDEO FRAME
  // Called with a base64 JPEG frame capture every ~15 seconds
  // ================================================================

  Future<void> sendVideoFrameForAnalysis(String imageBase64) async {
    if (!_isInCall || _currentCallId == null) return;
    try {
      await http.post(
        Uri.parse('${EnvironmentConfig.baseUrl}/api/v3/calls/$_currentCallId/sentiment/video'),
        headers: {
          'Content-Type': 'application/json',
          'Authorization': 'Bearer $_jwtToken',
        },
        body: jsonEncode({
          'imageBase64':  imageBase64,
          'imageFormat':  'jpeg',
          'otherPartyId': _otherPartyId,
        }),
      );
    } catch (e) {
      debugPrint('⚠️ Video sentiment error: $e');
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
    if (!_isInCall || _currentCallId == null) return;
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
    _onCallEnded?.call();
  }

  bool get isInCall => _isInCall;
  String? get currentCallId => _currentCallId;
  Map<String, dynamic>? get meetingCredentials => _meetingCredentials;

  void dispose() {
    _sentimentTimer?.cancel();
    _wsSubscription?.cancel();
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
  final bool isVideoEnabled;
  final bool isAudioEnabled;

  const ChimeCallSession({
    required this.callId,
    required this.meetingId,
    required this.attendeeId,
    required this.joinToken,
    required this.mediaPlacement,
    required this.isVideoEnabled,
    required this.isAudioEnabled,
  });
}
