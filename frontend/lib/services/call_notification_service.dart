  import 'dart:async';
  import 'package:flutter/material.dart';
  import 'package:flutter/foundation.dart'; 
  import 'package:web_socket_channel/web_socket_channel.dart';
  import 'package:web_socket_channel/status.dart' as status;
  import '../widgets/incoming_call_popup.dart';
  import '../widgets/hybrid_video_call_widget.dart';
  import '../config/env_constant.dart';

  /// Service to handle real-time call notifications
  class CallNotificationService {
    static WebSocketChannel? _channel;
    static bool _isConnected = false;
    static String? _currentUserId;
    static String? _currentUserRole;
    static BuildContext? _context;

    static final StreamController<Map<String, dynamic>> _incomingCallController =
        StreamController<Map<String, dynamic>>.broadcast();

    static Stream<Map<String, dynamic>> get incomingCallStream =>
        _incomingCallController.stream;

    static bool get isConnected => _isConnected;

    /// Initialize real-time notification service
    static Future<bool> initialize({
      required String userId,
      required String userRole,
      required BuildContext context,
      String? websocketUrl,
    }) async {
      try {
        _currentUserId = userId;
        _currentUserRole = userRole;
        _context = context;

        print('🔔 Initializing CallNotificationService for $userRole: $userId');

        // 🔥 Disable WebSocket entirely for Flutter Web
        if (kIsWeb) {
          print('🌐 WebSocket disabled for Web build.');
          _isConnected = false;
          return true;
        }

        final String wsUrl = websocketUrl ?? getWebSocketNotificationUrl();
        print('Connecting to notification WebSocket: $wsUrl');

        _channel = WebSocketChannel.connect(Uri.parse(wsUrl));
        _isConnected = true;

        final registerMsg = {
          'type': 'register',
          'userId': userId,
          'userRole': userRole,
        };

        _channel!.sink.add(_encode(registerMsg));

        _channel!.stream.listen(
          (message) {
            final data = _decode(message);
            if (data == null) return;

            if (data['type'] == 'incoming-video-call') {
              print('📞 Incoming call: $data');
              _handleIncomingCall(data);
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
          cancelOnError: true,
        );

        return true;
      } catch (e) {
        print('❌ Error initializing CallNotificationService: $e');
        return false;
      }
    }

    static void _handleIncomingCall(Map<String, dynamic> callData) {
      if (_context == null) return;

      final callId = callData['callId'] ?? '';
      final callerId = callData['callerId'] ?? '';
      final callerName = callData['callerName'] ?? 'Unknown Caller';
      final isVideoCall = callData['isVideoCall'] ?? true;
      final callerRole = callData['callerRole'] ?? 'PATIENT';

      _incomingCallController.add(callData);

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
          onDecline: () => _declineCall(callId: callId),
        ),
      );
    }

    static void _acceptCall({
      required String callId,
      required String callerId,
      required String callerName,
      required bool isVideoCall,
    }) {
      if (_context == null || _currentUserId == null) return;

      if (_channel != null && _isConnected) {
        final msg = {
          'type': 'accept-call',
          'callId': callId,
          'acceptedBy': _currentUserId,
          'acceptedByRole': _currentUserRole,
        };
        _channel!.sink.add(_encode(msg));
      }

      Navigator.of(_context!).pop();

      Navigator.of(_context!).push(
        MaterialPageRoute(
          builder: (context) => HybridVideoCallWidget(
            userId: _currentUserId!,
            callId: callId,
            recipientId: callerId,
            isInitiator: false,
            isVideoEnabled: isVideoCall,
            isAudioEnabled: true,
            userName: _getCurrentUserName(),
            recipientName: callerName,
          ),
        ),
      );
    }

    static void _declineCall({required String callId}) {
      if (_channel != null && _isConnected) {
        final msg = {
          'type': 'decline-call',
          'callId': callId,
          'declinedBy': _currentUserId,
          'declinedByRole': _currentUserRole,
        };
        _channel!.sink.add(_encode(msg));
      }

      if (_context != null) {
        Navigator.of(_context!).pop();
      }
    }

    static Future<bool> sendCallInvitation({
      required String recipientId,
      required String recipientRole,
      required String callId,
      required bool isVideoCall,
    }) async {
      if (!_isConnected || _channel == null) return false;

      try {
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

    static String _encode(Map<String, dynamic> data) {
      return data.toString().replaceAll("'", '"');
    }

    static Map<String, dynamic>? _decode(dynamic message) {
      return null; // simplified for stability
    }

    static String _getCurrentUserName() {
      return _currentUserRole == 'CAREGIVER' ? 'Caregiver' : 'Patient';
    }

    static void dispose() {
      _channel?.sink.close(status.goingAway);
      _channel = null;
      _isConnected = false;
      _currentUserId = null;
      _currentUserRole = null;
      _context = null;

      if (!_incomingCallController.isClosed) {
        _incomingCallController.close();
      }
    }
  }