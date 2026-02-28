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

      print('🔔 Initializing CallNotificationService for $userRole: $userId');

      if (_isConnected && _currentUserId == userId && _channel != null) {
        // Reuse existing connection, only refresh context/role references
        return true;
      }

      if (_isConnected) {
        dispose();
      }

      // Connect to backend call WebSocket endpoint
      final String wsUrl = websocketUrl ?? getCallNotificationWebSocketUrl();
      print('Connecting to notification WebSocket: $wsUrl');
      _channel = WebSocketChannel.connect(Uri.parse(wsUrl));
      _isConnected = true;

      final token = await AuthTokenManager.getJwtToken();
      if (token == null || token.isEmpty) {
        print('❌ Cannot initialize call notifications: missing JWT token');
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
          if (data['type'] == 'incoming-video-call') {
            print('📞 Received incoming video call: $data');
            _handleIncomingCall(data);
          } else if (data['type'] == 'call-ended') {
            print('📞 Call ended: $data');
          } else if (data['type'] == 'call-answered') {
            print('📞 Call answered: $data');
          } else if (data['type'] == 'call-declined') {
            print('📞 Call declined: $data');
          }
        },
        onDone: () {
          _isConnected = false;
          print('❌ CallNotificationService WebSocket closed');
        },
        onError: (e) {
          _isConnected = false;
          print('❌ CallNotificationService WebSocket error: $e');
        },
      );

      return true;
    } catch (e) {
      print('❌ Error initializing CallNotificationService: $e');
      return false;
    }
  }

  /// Handle incoming call notification
  static void _handleIncomingCall(Map<String, dynamic> callData) {
    if (_context == null) return;

    // Extract call information
    final callId = callData['callId'] ?? '';
    final callerId = (callData['senderId'] ?? callData['callerId'] ?? '').toString();
    final callerName = (callData['senderName'] ?? callData['callerName'] ?? 'Unknown Caller').toString();
    final isVideoCall = callData['isVideoCall'] ?? true;
    final callerRole = (callData['senderRole'] ?? callData['callerRole'] ?? 'PATIENT').toString();

    print('📞 Processing incoming call from $callerName ($callerRole)');

    // Emit to stream for any listeners
    _incomingCallController.add(callData);

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

    showDialog(
      context: _context!,
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
        ),
        onDecline: () => _declineCall(callId: callId, callerId: callerId),
      ),
    );
  }

  /// Accept incoming call
  static void _acceptCall({
    required String callId,
    required String callerId,
    required String callerName,
    required bool isVideoCall,
  }) {
    if (_context == null || _currentUserId == null) return;

    print('✅ Accepting call: $callId');

    // Notify backend that call was accepted
    if (_channel != null && _isConnected) {
      final msg = {
        'type': 'accept-call',
        'callId': callId,
        'senderId': callerId,
      };
      _channel!.sink.add(_encode(msg));
    }

    // Close the incoming call popup
    Navigator.of(_context!).pop();

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
  static void _declineCall({required String callId, required String callerId}) {
    print('❌ Declining call: $callId');

    // Notify backend that call was declined
    if (_channel != null && _isConnected) {
      final msg = {
        'type': 'decline-call',
        'callId': callId,
        'senderId': callerId,
      };
      _channel!.sink.add(_encode(msg));
    }

    // Close the incoming call popup
    if (_context != null) {
      Navigator.of(_context!).pop();
    }
  }

  /// Send outgoing call notification
  static Future<bool> sendCallInvitation({
    required String recipientId,
    required String recipientRole, // 'CAREGIVER' or 'PATIENT'
    required String callId,
    required bool isVideoCall,
  }) async {
    if (!_isConnected || _channel == null) {
      print('❌ Cannot send call invitation - not connected');
      return false;
    }
    try {
      print('📤 Sending call invitation to $recipientRole: $recipientId');
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
      return true;
    } catch (e) {
      print('❌ Error sending call invitation: $e');
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
      print('❌ Error decoding WebSocket message: $e');
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
    print('🧹 Disposing CallNotificationService');

    _channel?.sink.close(status.goingAway);
    _channel = null;

    _isConnected = false;
    _currentUserId = null;
    _currentUserRole = null;
    _context = null;

    // Keep stream controller alive for app lifetime.
  }
}
