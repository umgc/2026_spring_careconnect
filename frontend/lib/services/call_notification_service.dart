import 'dart:async';
import 'dart:convert';
import 'package:flutter/material.dart';
import 'package:web_socket_channel/web_socket_channel.dart';
import 'package:web_socket_channel/status.dart' as status;
import '../widgets/incoming_call_popup.dart';
import '../widgets/hybrid_video_call_widget.dart';
import '../config/env_constant.dart';
import '../services/auth_token_manager.dart';

/// Service to handle real-time call notifications for caregivers
class CallNotificationService {
  static WebSocketChannel? _channel;
  static bool _isConnected = false;
  static String? _currentUserId;
  static String? _currentUserRole;
  static BuildContext? _context;
  static String? _activeCallId;
  static String? _currentIncomingCallId;
  static bool _isIncomingDialogVisible = false;
  static final Map<String, DateTime> _suppressedIncomingCallIds =
      <String, DateTime>{};

  // Stream controllers for call events
  static final StreamController<Map<String, dynamic>> _incomingCallController =
      StreamController<Map<String, dynamic>>.broadcast();

  // Getters
  static Stream<Map<String, dynamic>> get incomingCallStream =>
      _incomingCallController.stream;
  static bool get isConnected => _isConnected;

  /// Initialize the real-time notification service
  static Future<bool> initialize({
    required String userId,
    required String userRole, // 'CAREGIVER' or 'PATIENT'
    required BuildContext context,
    String? websocketUrl, // Optional: pass WebSocket URL for flexibility
  }) async {
    try {
      _currentUserId = userId;
      _currentUserRole = userRole;
      _context = context;

      debugPrint('🔔 Initializing CallNotificationService for $userRole: $userId');

      if (_isConnected && _currentUserId == userId && _channel != null) {
        // Reuse existing connection, only refresh context/role references
        return true;
      }

      if (_isConnected) {
        dispose();
      }

      // Connect to backend call WebSocket endpoint
      final String wsUrl = websocketUrl ?? getCallNotificationWebSocketUrl();
      debugPrint('Connecting to notification WebSocket: $wsUrl');
      _channel = WebSocketChannel.connect(Uri.parse(wsUrl));
      _isConnected = true;

      final token = await AuthTokenManager.getJwtToken();
      if (token == null || token.isEmpty) {
        debugPrint('❌ Cannot initialize call notifications: missing JWT token');
        dispose();
        return false;
      }

      // Authenticate and join user room
      _channel!.sink.add(
        _encode({
          'type': 'authenticate',
          'token': token,
        }),
      );
      _channel!.sink.add(
        _encode({
          'type': 'join-user-room',
        }),
      );

      // Listen for messages
      _channel!.stream.listen(
        (message) {
          final data = _decode(message);
          if (data == null || data.isEmpty) return;
          final type = data['type'] as String?;
          if (type == null) return;

          if (type == 'incoming-video-call') {
            debugPrint('📞 Received incoming video call: $data');
            _incomingCallController.add(data);
            _handleIncomingCall(data);
          } else if (type == 'call-ended') {
            debugPrint('📞 Call ended: $data');
            _incomingCallController.add(data);
            final endedCallId = (data['callId'] ?? '').toString();
            if (endedCallId.isNotEmpty) {
              _suppressIncomingCallId(endedCallId);
              if (_activeCallId == endedCallId) {
                _activeCallId = null;
              }
              if (_currentIncomingCallId == endedCallId) {
                _dismissIncomingCallPopup();
              }
            }
            _notifyCallEnded(data);
          } else if (type == 'call-answered') {
            debugPrint('📞 Call answered: $data');
            _incomingCallController.add(data);
            final answeredCallId = (data['callId'] ?? '').toString();
            if (answeredCallId.isNotEmpty) {
              _activeCallId = answeredCallId;
            }
            _notifyCallAnswered(data);
          } else if (type == 'call-declined') {
            debugPrint('📞 Call declined: $data');
            _incomingCallController.add(data);
            final declinedCallId = (data['callId'] ?? '').toString();
            if (declinedCallId.isNotEmpty) {
              _suppressIncomingCallId(declinedCallId);
            }
            _notifyCallDeclined(data);
          } else if (type == 'sentiment-update') {
            _incomingCallController.add(data);
          }
        },
        onDone: () {
          _isConnected = false;
          debugPrint('❌ CallNotificationService WebSocket closed');
        },
        onError: (e) {
          _isConnected = false;
          debugPrint('❌ CallNotificationService WebSocket error: $e');
        },
      );

      return true;
    } catch (e) {
      debugPrint('❌ Error initializing CallNotificationService: $e');
      return false;
    }
  }

  /// Handle incoming call notification
  static void _handleIncomingCall(Map<String, dynamic> callData) {
    if (_context == null) return;

    // Extract call information
    final callId = (callData['callId'] ?? '').toString();
    final callerId = (callData['senderId'] ?? callData['callerId'] ?? '').toString();
    final callerName = (callData['senderName'] ?? callData['callerName'] ?? 'Unknown Caller').toString();
    final isVideoCall = callData['isVideoCall'] ?? true;
    final callerRole = (callData['senderRole'] ?? callData['callerRole'] ?? 'PATIENT').toString();

    if (callId.isEmpty) return;
    _pruneSuppressedIncomingCallIds();

    if (_activeCallId == callId || _isIncomingCallSuppressed(callId)) {
      debugPrint('⏭️ Suppressing duplicate incoming call popup for callId: $callId');
      return;
    }

    if (_isIncomingDialogVisible) {
      if (_currentIncomingCallId == callId) {
        debugPrint('⏭️ Incoming popup already visible for callId: $callId');
        return;
      }
      debugPrint('⏭️ Ignoring incoming call while another incoming popup is visible');
      return;
    }

    debugPrint('📞 Processing incoming call from $callerName ($callerRole)');

    // Show incoming call popup
    _showIncomingCallPopup(
      callId: callId,
      callerId: callerId,
      callerName: callerName,
      isVideoCall: isVideoCall,
      callerRole: callerRole,
    );
  }

  /// Show incoming call popup UI
  static void _showIncomingCallPopup({
    required String callId,
    required String callerId,
    required String callerName,
    required bool isVideoCall,
    required String callerRole,
  }) {
    if (_context == null) return;

    _currentIncomingCallId = callId;
    _isIncomingDialogVisible = true;

    showDialog(
      context: _context!,
      useRootNavigator: true,
      barrierDismissible: false,
      builder: (context) => IncomingCallPopup(
        callId: callId,
        callerId: callerId,
        callerName: callerName,
        isVideoCall: isVideoCall,
        callerRole: callerRole,
        onAccept: () => _acceptCall(
          callId: callId,
          callerId: callerId,
          callerName: callerName,
          isVideoCall: isVideoCall,
          dialogContext: context,
        ),
        onDecline: () => _declineCall(
          callId: callId,
          callerId: callerId,
          dialogContext: context,
        ),
      ),
    ).whenComplete(() {
      if (_currentIncomingCallId == callId) {
        _isIncomingDialogVisible = false;
        _currentIncomingCallId = null;
      }
    });
  }

  /// Accept incoming call
  static void _acceptCall({
    required String callId,
    required String callerId,
    required String callerName,
    required bool isVideoCall,
    BuildContext? dialogContext,
  }) {
    if (_context == null || _currentUserId == null) return;

    debugPrint('✅ Accepting call: $callId');
    _activeCallId = callId;
    _suppressIncomingCallId(callId, duration: const Duration(seconds: 45));

    // Notify backend that call was accepted
    if (_channel != null && _isConnected) {
      final msg = {
        'type': 'accept-call',
        'callId': callId,
        'senderId': callerId,
      };
      _channel!.sink.add(_encode(msg));
    }

    _dismissIncomingCallPopup(dialogContext: dialogContext);

    // Navigate to video call screen
    Navigator.of(_context!).push(
      MaterialPageRoute(
        builder: (context) => HybridVideoCallWidget(
          userId: _currentUserId!,
          callId: callId,
          recipientId: callerId,
          isInitiator: false, // This user is joining the call
          isVideoEnabled: isVideoCall,
          isAudioEnabled: true,
          userName: _getCurrentUserName(),
          recipientName: callerName,
        ),
      ),
    );
  }

  /// Decline incoming call
  static void _declineCall({
    required String callId,
    required String callerId,
    BuildContext? dialogContext,
  }) {
    debugPrint('❌ Declining call: $callId');
    _suppressIncomingCallId(callId);

    // Notify backend that call was declined
    if (_channel != null && _isConnected) {
      final msg = {
        'type': 'decline-call',
        'callId': callId,
        'senderId': callerId,
      };
      _channel!.sink.add(_encode(msg));
    }

    _dismissIncomingCallPopup(dialogContext: dialogContext);
  }

  static void _dismissIncomingCallPopup({BuildContext? dialogContext}) {
    if (!_isIncomingDialogVisible) return;

    final dialogNavigator = dialogContext != null
        ? Navigator.maybeOf(dialogContext, rootNavigator: true)
        : null;
    if (dialogNavigator != null && dialogNavigator.canPop()) {
      dialogNavigator.pop();
    } else if (_context != null) {
      final navigator = Navigator.maybeOf(_context!, rootNavigator: true);
      navigator?.maybePop();
    }

    _isIncomingDialogVisible = false;
    _currentIncomingCallId = null;
  }

  static void _suppressIncomingCallId(
    String callId, {
    Duration duration = const Duration(seconds: 30),
  }) {
    if (callId.isEmpty) return;
    _suppressedIncomingCallIds[callId] = DateTime.now().add(duration);
  }

  static bool _isIncomingCallSuppressed(String callId) {
    final expiresAt = _suppressedIncomingCallIds[callId];
    if (expiresAt == null) return false;
    if (DateTime.now().isAfter(expiresAt)) {
      _suppressedIncomingCallIds.remove(callId);
      return false;
    }
    return true;
  }

  static void _pruneSuppressedIncomingCallIds() {
    final now = DateTime.now();
    final expired = <String>[];
    _suppressedIncomingCallIds.forEach((callId, expiresAt) {
      if (now.isAfter(expiresAt)) {
        expired.add(callId);
      }
    });
    for (final callId in expired) {
      _suppressedIncomingCallIds.remove(callId);
    }
  }

  static void _notifyCallDeclined(Map<String, dynamic> data) {
    final context = _context;
    if (context == null) return;

    final declinedByName =
        (data['declinedByName'] ?? data['senderName'] ?? 'The recipient')
            .toString();
    final reason = (data['reason'] ?? 'declined').toString();
    final normalizedReason = reason.trim().isEmpty ? 'declined' : reason;

    _showCallFeedback(
      '$declinedByName declined the call ($normalizedReason).',
      backgroundColor: Colors.orange.shade800,
    );
  }

  static void _notifyCallAnswered(Map<String, dynamic> data) {
    final answeredBy =
        (data['answeredByName'] ?? data['senderName'] ?? 'Recipient').toString();
    _showCallFeedback(
      '$answeredBy answered. Connecting now…',
      backgroundColor: Colors.green.shade700,
    );
  }

  static void _notifyCallEnded(Map<String, dynamic> data) {
    final endedBy =
        (data['endedByName'] ?? data['senderName'] ?? 'Other participant')
            .toString();
    _showCallFeedback(
      'Call ended by $endedBy.',
      backgroundColor: Colors.blueGrey.shade700,
    );
  }

  static void _showCallFeedback(
    String message, {
    Duration duration = const Duration(seconds: 3),
    Color? backgroundColor,
  }) {
    final context = _context;
    if (context == null) return;
    final messenger = ScaffoldMessenger.maybeOf(context);
    if (messenger == null) return;

    messenger
      ..hideCurrentSnackBar()
      ..showSnackBar(
        SnackBar(
          content: Text(message),
          duration: duration,
          behavior: SnackBarBehavior.floating,
          backgroundColor: backgroundColor,
        ),
      );
  }

  /// Send outgoing call notification
  static Future<bool> sendCallInvitation({
    required String recipientId,
    required String recipientRole, // 'CAREGIVER' or 'PATIENT'
    required String callId,
    required bool isVideoCall,
  }) async {
    if (!_isConnected || _channel == null) {
      debugPrint('❌ Cannot send call invitation - not connected');
      _showCallFeedback(
        'Unable to start call: notifications are not connected.',
        backgroundColor: Colors.red.shade700,
      );
      return false;
    }
    try {
      debugPrint('📤 Sending call invitation to $recipientRole: $recipientId');
      final msg = {
        'type': 'send-video-call-invitation',
        'callId': callId,
        'callerId': _currentUserId,
        'callerName': _getCurrentUserName(),
        'callerRole': _currentUserRole,
        'recipientId': recipientId,
        'recipientRole': recipientRole,
        'isVideoCall': isVideoCall,
        'timestamp': DateTime.now().toIso8601String(),
      };
      _channel!.sink.add(_encode(msg));
      _activeCallId = callId;
      _showCallFeedback(
        'Calling $recipientRole… waiting for response.',
        backgroundColor: Colors.blue.shade700,
      );
      return true;
    } catch (e) {
      debugPrint('❌ Error sending call invitation: $e');
      _showCallFeedback(
        'Failed to send call invitation. Please try again.',
        backgroundColor: Colors.red.shade700,
      );
      return false;
    }
  }

  // Helper to encode/decode JSON
  static String _encode(Map<String, dynamic> data) {
    return jsonEncode(data);
  }

  static Map<String, dynamic>? _decode(dynamic message) {
    try {
      if (message is String) {
        final decoded = jsonDecode(message);
        if (decoded is Map<String, dynamic>) {
          return decoded;
        }
      }
    } catch (e) {
      debugPrint('❌ Error decoding WebSocket message: $e');
    }
    return null;
    // removed extra closing brace here
  }

  /// Get current user name from context or default
  static String _getCurrentUserName() {
    // You can implement this to get the actual user name
    // For now, return a placeholder based on role
    return _currentUserRole == 'CAREGIVER' ? 'Caregiver' : 'Patient';
  }

  /// Dispose and cleanup
  static void dispose() {
    debugPrint('🧹 Disposing CallNotificationService');

    _channel?.sink.close(status.normalClosure);
    _channel = null;

    _isConnected = false;
    _currentUserId = null;
    _currentUserRole = null;
    _context = null;
    _activeCallId = null;
    _currentIncomingCallId = null;
    _isIncomingDialogVisible = false;
    _suppressedIncomingCallIds.clear();

    // Keep stream controller alive for app lifetime.
  }
}
