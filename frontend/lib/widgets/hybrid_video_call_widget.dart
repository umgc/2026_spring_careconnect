import 'dart:async';

import 'package:flutter/material.dart';
import '../services/video_call_service.dart';
import '../services/auth_token_manager.dart';
import '../services/call_notification_service.dart';
import '../services/user_role_storage_service.dart';
import '../config/theme/app_theme.dart';
import '../widgets/sentiment_dashboard_widget.dart';
import '../widgets/chime_meeting_embed.dart';
import '../features/health/caregiver-patient-list/page/patient_details_page.dart';

/// HybridVideoCallWidget — video call screen with live sentiment monitoring.
///
/// Layout:
///   - Top 60%: Chime video call view
///   - Bottom 40%: Collapsible sentiment dashboard (bar graphs)
///
/// The sentiment panel is shown only for caregivers and updates
/// in real time as the backend pushes WebSocket sentiment-update messages.
class HybridVideoCallWidget extends StatefulWidget {
  final String userId;
  final String callId;
  final String? recipientId;
  final String? userRole;
  final bool isVideoEnabled;
  final bool isAudioEnabled;
  final bool isInitiator;
  final String? userEmail;
  final String? userPhone;
  final String? userName;
  final String? recipientEmail;
  final String? recipientPhone;
  final String? recipientName;
  final String? returnPatientDetailsId;
  final bool forcePatientDetailsOnExit;
  final bool returnAsCaregiver;

  const HybridVideoCallWidget({
    super.key,
    required this.userId,
    required this.callId,
    this.recipientId,
    this.userRole,
    this.isVideoEnabled = true,
    this.isAudioEnabled = true,
    this.isInitiator = false,
    this.userEmail,
    this.userPhone,
    this.userName,
    this.recipientEmail,
    this.recipientPhone,
    this.recipientName,
    this.returnPatientDetailsId,
    this.forcePatientDetailsOnExit = false,
    this.returnAsCaregiver = false,
  });

  @override
  State<HybridVideoCallWidget> createState() => _HybridVideoCallWidgetState();
}

class _HybridVideoCallWidgetState extends State<HybridVideoCallWidget> {
  static const String _sentimentModeRaw = String.fromEnvironment(
    'CARECONNECT_SENTIMENT_MODE',
    defaultValue: 'balanced',
  );

  static const int _realtimeIntervalMs = 6000;
  static const int _balancedIntervalMs = 15000;
  static const Duration _adaptiveSwitchCooldown = Duration(seconds: 30);

  final VideoCallService _videoCallService = VideoCallService();

  ChimeCallSession? _callSession;
  bool _isLoading = true;
  String? _error;
  bool _sentimentPanelExpanded = true;
  bool _isCaregiverView = false;
  bool _isPatientView = false;
  bool _showCallRejectedSummary = false;
  String _rejectionSummaryText = 'The recipient declined the call.';
  bool _isRetryingRejectedCall = false;
  bool _isSendingAudioSample = false;
  bool _isSendingVideoSample = false;
  bool _isEndingCall = false;
  bool _isExitingCall = false;
  DateTime? _lastAudioSampleSentAt;
  DateTime? _lastVideoSampleSentAt;
  DateTime? _lastTranscriptSampleSentAt;
  bool _localAudioEnabled = true;
  bool _localVideoEnabled = true;
  bool _hasSentInitialInvitation = false;
  int _adaptiveActiveIntervalMs = _realtimeIntervalMs;
  int _adaptiveFailureStreak = 0;
  int _adaptiveSuccessStreak = 0;
  DateTime? _lastAdaptiveSwitchAt;

  // Latest sentiment data — updated via WebSocket push
  Map<String, dynamic> _sentimentData = {};

  String get _configuredSentimentMode => _sentimentModeRaw.trim().toLowerCase();

  bool get _isAdaptiveSentimentMode => _configuredSentimentMode == 'adaptive';

  bool get _isRealtimeSentimentMode => _configuredSentimentMode == 'realtime';

  int get _sentimentCaptureIntervalMs {
    if (_isAdaptiveSentimentMode) return _adaptiveActiveIntervalMs;
    return _isRealtimeSentimentMode ? _realtimeIntervalMs : _balancedIntervalMs;
  }

  String get _activeCaptureModeTag {
    if (_isAdaptiveSentimentMode) {
      return _adaptiveActiveIntervalMs <= _realtimeIntervalMs
          ? 'ADAPTIVE_REALTIME'
          : 'ADAPTIVE_BALANCED';
    }
    return _isRealtimeSentimentMode ? 'REALTIME' : 'BALANCED';
  }

  Duration get _sampleThrottleWindow {
    final throttleMs = (_sentimentCaptureIntervalMs - 1000).clamp(3000, 14000);
    return Duration(milliseconds: throttleMs);
  }

  int get _embedCaptureIntervalMs {
    if (_isAdaptiveSentimentMode) {
      return _realtimeIntervalMs;
    }
    return _sentimentCaptureIntervalMs;
  }

  @override
  void initState() {
    super.initState();
    _adaptiveActiveIntervalMs =
        (_isAdaptiveSentimentMode || _isRealtimeSentimentMode)
        ? _realtimeIntervalMs
        : _balancedIntervalMs;
    _loadCurrentRole();
    _initializeCall();
  }

  Future<void> _loadCurrentRole() async {
    try {
      final role = await _resolveCurrentRole();
      final isCaregiver = role?.toUpperCase() == 'CAREGIVER';
      final isPatient = role?.toUpperCase() == 'PATIENT';
      if (!mounted) return;
      setState(() {
        _isCaregiverView = isCaregiver;
        _isPatientView = isPatient;
        if (!isCaregiver) {
          _sentimentPanelExpanded = false;
        }
      });
    } catch (_) {
      // Keep safe default (no analytics panel) if role cannot be loaded.
    }
  }

  Future<void> _initializeCall() async {
    try {
      final role = await _resolveCurrentRole();
      final isCaregiverRole = role?.toUpperCase() == 'CAREGIVER';
      final isPatientRole = role?.toUpperCase() == 'PATIENT';

      if (mounted) {
        setState(() {
          _isCaregiverView = isCaregiverRole;
          _isPatientView = isPatientRole;
          if (!isCaregiverRole) {
            _sentimentPanelExpanded = false;
          }
        });
      }

      // Retrieve JWT from secure storage
      // Replace with your actual auth token retrieval
      final jwtToken = await _getJwtToken();

      await _videoCallService.initialize(
        userId: widget.userId,
        jwtToken: jwtToken,
        enablePatientSentimentCapture: isPatientRole,
        onCallEnded: () {
          if (_showCallRejectedSummary) return;
          _exitCallScreen();
        },
        onSentimentUpdate: (data) {
          if (mounted) {
            setState(() => _sentimentData = data);
          }
        },
        onCallDeclined: (event) {
          if (!mounted || !widget.isInitiator) return;
          final declinedBy =
              (event['declinedByName'] ?? widget.recipientName ?? 'Recipient')
                  .toString();
          final reason = (event['reason'] ?? 'declined').toString().trim();
          final reasonSuffix = reason.isEmpty ? '' : ' ($reason)';

          setState(() {
            _showCallRejectedSummary = true;
            _isLoading = false;
            _rejectionSummaryText =
                '$declinedBy declined the call$reasonSuffix.';
          });
        },
      );

      final session = await _videoCallService.joinCall(
        callId: widget.callId,
        otherPartyId: widget.recipientId ?? '',
        isVideoEnabled: widget.isVideoEnabled,
        isAudioEnabled: widget.isAudioEnabled,
      );

      if (widget.isInitiator && !_hasSentInitialInvitation) {
        final recipientId = widget.recipientId?.trim();
        if (recipientId == null || recipientId.isEmpty) {
          throw Exception('Missing recipient ID for outgoing call.');
        }

        final currentRole = (role ?? '').toUpperCase();
        final recipientRole = currentRole == 'CAREGIVER'
            ? 'PATIENT'
            : 'CAREGIVER';

        final invitationSent = await CallNotificationService.sendCallInvitation(
          recipientId: recipientId,
          recipientRole: recipientRole,
          callId: widget.callId,
          isVideoCall: widget.isVideoEnabled,
        );

        if (!invitationSent) {
          await _videoCallService.endCall();
          throw Exception(
            'Unable to notify callee after joining the call room.',
          );
        }

        _hasSentInitialInvitation = true;
      }

      setState(() {
        _callSession = session;
        _isLoading = false;
        _localAudioEnabled = session.isAudioEnabled;
        _localVideoEnabled = session.isVideoEnabled;
      });
    } catch (e) {
      setState(() {
        _error = e.toString();
        _isLoading = false;
      });
    }
  }

  Future<String> _getJwtToken() async {
    final token = await AuthTokenManager.getJwtToken();
    if (token == null || token.isEmpty) {
      throw Exception('No authentication token found. Please log in again.');
    }
    return token;
  }

  @override
  void dispose() {
    _videoCallService.dispose();
    super.dispose();
  }

  Future<String?> _resolveCurrentRole() async {
    final roleFromWidget = widget.userRole?.trim();
    if (roleFromWidget != null && roleFromWidget.isNotEmpty) {
      return roleFromWidget.toUpperCase();
    }

    final roleFromStorage = await UserRoleStorageService.instance.getUserRole();
    if (roleFromStorage != null && roleFromStorage.trim().isNotEmpty) {
      return roleFromStorage.trim().toUpperCase();
    }

    final session = await AuthTokenManager.getUserSession();
    final sessionRole = (session?['role'] as String?)?.trim();
    if (sessionRole != null && sessionRole.isNotEmpty) {
      return sessionRole.toUpperCase();
    }

    return null;
  }

  Future<void> _handleTranscriptSample(String transcript) async {
    if (!_isPatientView) return;
    final text = transcript.trim();
    if (text.length < 8) return;

    final now = DateTime.now();
    if (_lastTranscriptSampleSentAt != null &&
        now.difference(_lastTranscriptSampleSentAt!) <
            const Duration(seconds: 8)) {
      return;
    }

    _lastTranscriptSampleSentAt = now;
    try {
      await _videoCallService.sendTextForAnalysis(
        text,
        captureMode: _activeCaptureModeTag,
      );
    } catch (_) {}
  }

  void _maybeSwitchAdaptiveInterval({
    required bool success,
    required Duration requestLatency,
  }) {
    if (!_isAdaptiveSentimentMode) return;

    final now = DateTime.now();
    final switchedRecently =
        _lastAdaptiveSwitchAt != null &&
        now.difference(_lastAdaptiveSwitchAt!) < _adaptiveSwitchCooldown;

    final isSlowSuccess =
        success && requestLatency > const Duration(milliseconds: 2500);
    final effectiveFailure = !success || isSlowSuccess;

    if (effectiveFailure) {
      _adaptiveFailureStreak += 1;
      _adaptiveSuccessStreak = 0;
    } else {
      _adaptiveSuccessStreak += 1;
      _adaptiveFailureStreak = 0;
    }

    if (!switchedRecently &&
        _adaptiveActiveIntervalMs == _realtimeIntervalMs &&
        _adaptiveFailureStreak >= 2) {
      if (mounted) {
        setState(() {
          _adaptiveActiveIntervalMs = _balancedIntervalMs;
          _adaptiveFailureStreak = 0;
          _adaptiveSuccessStreak = 0;
          _lastAdaptiveSwitchAt = now;
        });
      } else {
        _adaptiveActiveIntervalMs = _balancedIntervalMs;
        _adaptiveFailureStreak = 0;
        _adaptiveSuccessStreak = 0;
        _lastAdaptiveSwitchAt = now;
      }
      return;
    }

    if (!switchedRecently &&
        _adaptiveActiveIntervalMs == _balancedIntervalMs &&
        _adaptiveSuccessStreak >= 6) {
      if (mounted) {
        setState(() {
          _adaptiveActiveIntervalMs = _realtimeIntervalMs;
          _adaptiveFailureStreak = 0;
          _adaptiveSuccessStreak = 0;
          _lastAdaptiveSwitchAt = now;
        });
      } else {
        _adaptiveActiveIntervalMs = _realtimeIntervalMs;
        _adaptiveFailureStreak = 0;
        _adaptiveSuccessStreak = 0;
        _lastAdaptiveSwitchAt = now;
      }
    }
  }

  Future<void> _handleAudioSample(
    String audioBase64,
    String audioFormat,
  ) async {
    if (!_isPatientView || _isSendingAudioSample) return;
    if (audioBase64.isEmpty) return;

    final now = DateTime.now();
    if (_lastAudioSampleSentAt != null &&
        now.difference(_lastAudioSampleSentAt!) < _sampleThrottleWindow) {
      return;
    }

    _isSendingAudioSample = true;
    try {
      final startedAt = DateTime.now();
      final success = await _videoCallService.sendAudioForAnalysis(
        audioBase64,
        audioFormat: audioFormat,
        captureMode: _activeCaptureModeTag,
      );

      _maybeSwitchAdaptiveInterval(
        success: success,
        requestLatency: DateTime.now().difference(startedAt),
      );

      if (success) {
        _lastAudioSampleSentAt = DateTime.now();
      }
    } catch (_) {
    } finally {
      _isSendingAudioSample = false;
    }
  }

  Future<void> _handleVideoSample(String imageBase64) async {
    if (!_isPatientView || _isSendingVideoSample) return;
    if (imageBase64.isEmpty) return;

    final now = DateTime.now();
    if (_lastVideoSampleSentAt != null &&
        now.difference(_lastVideoSampleSentAt!) < _sampleThrottleWindow) {
      return;
    }

    _isSendingVideoSample = true;
    try {
      final startedAt = DateTime.now();
      final success = await _videoCallService.sendVideoFrameForAnalysis(
        imageBase64,
        captureMode: _activeCaptureModeTag,
      );

      _maybeSwitchAdaptiveInterval(
        success: success,
        requestLatency: DateTime.now().difference(startedAt),
      );

      if (success) {
        _lastVideoSampleSentAt = DateTime.now();
      }
    } catch (_) {
    } finally {
      _isSendingVideoSample = false;
    }
  }

  Future<void> _toggleLocalAudio() async {
    final nextMuted = _localAudioEnabled;
    final toggled = await requestChimeAudioToggle(
      muted: nextMuted,
      meetingId: _callSession?.meetingId,
    );
    if (!toggled) {
      if (!mounted) return;
      ScaffoldMessenger.of(context).showSnackBar(
        const SnackBar(
          content: Text('Microphone toggle is not available for this session.'),
        ),
      );
      return;
    }
    setState(() {
      _localAudioEnabled = !_localAudioEnabled;
    });
  }

  Future<void> _toggleLocalVideo() async {
    final nextMuted = _localVideoEnabled;
    final toggled = await requestChimeVideoToggle(
      muted: nextMuted,
      meetingId: _callSession?.meetingId,
    );
    if (!toggled) {
      if (!mounted) return;
      ScaffoldMessenger.of(context).showSnackBar(
        const SnackBar(
          content: Text('Camera toggle is not available for this session.'),
        ),
      );
      return;
    }
    setState(() {
      _localVideoEnabled = !_localVideoEnabled;
    });
  }

  Future<void> _switchCamera() async {
    final switched = await requestChimeCameraSwitch(
      meetingId: _callSession?.meetingId,
    );
    if (!switched && mounted) {
      ScaffoldMessenger.of(context).showSnackBar(
        const SnackBar(
          content: Text('Camera switch is not available for this session.'),
        ),
      );
    }
  }

  Future<void> _exitCallScreen() async {
    if (_isExitingCall || !mounted) return;
    _isExitingCall = true;

    final returnPatientId = widget.returnPatientDetailsId?.trim();
    final shouldForcePatientDetails =
        widget.forcePatientDetailsOnExit &&
        returnPatientId != null &&
        returnPatientId.isNotEmpty;

    if (shouldForcePatientDetails) {
      Navigator.of(context).pushReplacement(
        MaterialPageRoute(
          builder: (_) => PatientDetailsPage(
            patientId: returnPatientId,
            isCaregiver: widget.returnAsCaregiver,
          ),
        ),
      );
      return;
    }

    if (!mounted) return;
    if (Navigator.canPop(context)) {
      Navigator.of(context).pop();
    }
  }

  Future<void> _endCallAndExit() async {
    if (_isEndingCall) return;

    setState(() {
      _isEndingCall = true;
    });

    try {
      await _videoCallService.endCall();
      await _exitCallScreen();
    } finally {
      if (mounted) {
        setState(() {
          _isEndingCall = false;
        });
      }
    }
  }

  @override
  Widget build(BuildContext context) {
    final isDark = Theme.of(context).brightness == Brightness.dark;
    final bg = isDark
        ? AppTheme.videoCallBackgroundDarkTheme
        : AppTheme.videoCallBackground;

    return Scaffold(
      backgroundColor: bg,
      appBar: AppBar(
        backgroundColor: bg,
        automaticallyImplyLeading: false,
        title: Text(
          widget.recipientName != null
              ? 'Call with ${widget.recipientName}'
              : 'Video Call',
          style: const TextStyle(color: AppTheme.videoCallText),
        ),
        iconTheme: const IconThemeData(color: AppTheme.videoCallText),
      ),
      body: _buildBody(),
    );
  }

  Widget _buildBody() {
    if (_showCallRejectedSummary) return _buildCallRejectedSummary();
    if (_isLoading) return _buildLoading();
    if (_error != null) return _buildError();
    if (_callSession == null) {
      return const Center(
        child: Text(
          'No active call session',
          style: TextStyle(color: AppTheme.videoCallText),
        ),
      );
    }
    return _buildCallLayout();
  }

  // ================================================================
  // MAIN LAYOUT — video on top, sentiment panel below
  // ================================================================

  Widget _buildCallLayout() {
    return Column(
      children: [
        // Video call area — takes remaining space above sentiment panel
        Expanded(
          flex: (_isCaregiverView && _sentimentPanelExpanded) ? 6 : 10,
          child: _buildChimeView(),
        ),

        _buildCallControlsBar(),

        // Sentiment dashboard — collapsible
        if (_isCaregiverView)
          AnimatedContainer(
            duration: const Duration(milliseconds: 300),
            curve: Curves.easeInOut,
            height: _sentimentPanelExpanded ? null : 0,
            child: _sentimentPanelExpanded
                ? SentimentDashboardWidget(
                    sentimentData: _sentimentData,
                    callId: widget.callId,
                  )
                : const SizedBox.shrink(),
          ),
      ],
    );
  }

  Widget _buildCallRejectedSummary() {
    final isDark = Theme.of(context).brightness == Brightness.dark;
    final cardColor = isDark ? const Color(0xFF1C1C1E) : Colors.white;

    return Center(
      child: Padding(
        padding: const EdgeInsets.all(24),
        child: AnimatedScale(
          scale: 1,
          duration: const Duration(milliseconds: 260),
          curve: Curves.easeOutBack,
          child: Container(
            constraints: const BoxConstraints(maxWidth: 460),
            padding: const EdgeInsets.symmetric(horizontal: 24, vertical: 28),
            decoration: BoxDecoration(
              color: cardColor,
              borderRadius: BorderRadius.circular(20),
              border: Border.all(color: Colors.white12),
              boxShadow: const [
                BoxShadow(
                  color: Colors.black26,
                  blurRadius: 18,
                  offset: Offset(0, 8),
                ),
              ],
            ),
            child: Column(
              mainAxisSize: MainAxisSize.min,
              children: [
                Container(
                  width: 74,
                  height: 74,
                  decoration: BoxDecoration(
                    shape: BoxShape.circle,
                    color: Colors.red.withValues(alpha: 0.12),
                  ),
                  child: const Icon(
                    Icons.call_end,
                    color: Colors.redAccent,
                    size: 38,
                  ),
                ),
                const SizedBox(height: 16),
                const Text(
                  'Call Rejected',
                  style: TextStyle(
                    fontSize: 22,
                    fontWeight: FontWeight.w700,
                    color: AppTheme.videoCallText,
                  ),
                ),
                const SizedBox(height: 10),
                Text(
                  _rejectionSummaryText,
                  textAlign: TextAlign.center,
                  style: const TextStyle(
                    fontSize: 14,
                    color: AppTheme.videoCallTextSecondary,
                    height: 1.35,
                  ),
                ),
                const SizedBox(height: 8),
                const Text(
                  'Would you like to try calling again?',
                  textAlign: TextAlign.center,
                  style: TextStyle(
                    fontSize: 13,
                    color: AppTheme.videoCallTextSecondary,
                  ),
                ),
                const SizedBox(height: 22),
                Row(
                  children: [
                    Expanded(
                      child: OutlinedButton.icon(
                        onPressed: _isRetryingRejectedCall
                            ? null
                            : () {
                                if (Navigator.canPop(context)) {
                                  Navigator.of(context).pop();
                                }
                              },
                        icon: const Icon(Icons.arrow_back),
                        label: const Text('Return'),
                      ),
                    ),
                    const SizedBox(width: 10),
                    Expanded(
                      child: ElevatedButton.icon(
                        onPressed: _isRetryingRejectedCall
                            ? null
                            : _retryRejectedCall,
                        icon: _isRetryingRejectedCall
                            ? const SizedBox(
                                width: 16,
                                height: 16,
                                child: CircularProgressIndicator(
                                  strokeWidth: 2,
                                ),
                              )
                            : const Icon(Icons.refresh),
                        label: Text(
                          _isRetryingRejectedCall ? 'Retrying…' : 'Try Again',
                        ),
                      ),
                    ),
                  ],
                ),
              ],
            ),
          ),
        ),
      ),
    );
  }

  Future<void> _retryRejectedCall() async {
    if (_isRetryingRejectedCall || !mounted) return;
    final recipientId = widget.recipientId;
    if (recipientId == null || recipientId.trim().isEmpty) {
      ScaffoldMessenger.of(context).showSnackBar(
        const SnackBar(
          content: Text('Unable to retry: missing recipient info.'),
        ),
      );
      return;
    }

    setState(() {
      _isRetryingRejectedCall = true;
    });

    try {
      await _videoCallService.endCall();

      final newCallId = 'chime_call_${DateTime.now().millisecondsSinceEpoch}';

      if (!mounted) return;
      Navigator.of(context).pushReplacement(
        MaterialPageRoute(
          builder: (_) => HybridVideoCallWidget(
            userId: widget.userId,
            callId: newCallId,
            recipientId: widget.recipientId,
            userRole: widget.userRole,
            isVideoEnabled: widget.isVideoEnabled,
            isAudioEnabled: widget.isAudioEnabled,
            isInitiator: true,
            userEmail: widget.userEmail,
            userPhone: widget.userPhone,
            userName: widget.userName,
            recipientEmail: widget.recipientEmail,
            recipientPhone: widget.recipientPhone,
            recipientName: widget.recipientName,
          ),
        ),
      );
    } catch (_) {
      if (!mounted) return;
      setState(() {
        _isRetryingRejectedCall = false;
      });
      ScaffoldMessenger.of(context).showSnackBar(
        const SnackBar(content: Text('Retry failed. Please try again.')),
      );
    }
  }

  // ================================================================
  // CHIME VIDEO VIEW
  // For the demo, renders the Chime meeting join URL in a container.
  // In a full native implementation, this would use the Chime Flutter SDK.
  // ================================================================

  Widget _buildChimeView() {
    if (_callSession == null) return const SizedBox.shrink();

    final mediaPlacement = _callSession!.mediaPlacement;
    final hasMediaEndpoints = mediaPlacement.values.whereType<String>().any(
      (value) => value.trim().isNotEmpty,
    );
    final isLocalMockSession = _callSession!.joinToken.startsWith(
      'local-join-token-',
    );

    if (hasMediaEndpoints && !isLocalMockSession) {
      return buildChimeMeetingEmbed(
        meetingId: _callSession!.meetingId,
        attendeeId: _callSession!.attendeeId,
        joinToken: _callSession!.joinToken,
        mediaPlacement: _callSession!.mediaPlacement,
        mediaRegion: _callSession!.mediaRegion,
        externalUserId: _callSession!.externalUserId,
        videoEnabled: _callSession!.isVideoEnabled,
        audioEnabled: _callSession!.isAudioEnabled,
        enableAutoSentimentCapture: _isPatientView,
        sentimentCaptureIntervalMs: _embedCaptureIntervalMs,
        onEndCallRequested: () async {
          await _endCallAndExit();
        },
        onTranscriptSample: _handleTranscriptSample,
        onAudioSample: _handleAudioSample,
        onVideoSample: _handleVideoSample,
      );
    }

    return Stack(
      children: [
        // In production: replace with flutter_inappwebview showing chimeUrl
        // or the native Chime Flutter SDK widget
        Container(
          color: Colors.black,
          child: Center(
            child: Column(
              mainAxisAlignment: MainAxisAlignment.center,
              children: [
                const Icon(Icons.videocam, color: Colors.white54, size: 64),
                const SizedBox(height: 16),
                Text(
                  isLocalMockSession
                      ? 'Connected to local mock session'
                      : 'Connected to call session',
                  style: const TextStyle(color: Colors.white70, fontSize: 18),
                ),
                const SizedBox(height: 8),
                const Text(
                  'Participant connection status is not available in this environment.',
                  style: TextStyle(color: Colors.white54, fontSize: 12),
                  textAlign: TextAlign.center,
                ),
                const SizedBox(height: 8),
                Text(
                  'Meeting: ${_callSession!.meetingId.substring(0, 8)}...',
                  style: const TextStyle(
                    color: Colors.white38,
                    fontSize: 12,
                    fontFamily: 'monospace',
                  ),
                ),
                const SizedBox(height: 24),
                // Duration counter
                _CallDurationTimer(startTime: DateTime.now()),
              ],
            ),
          ),
        ),
        // Chime badge
        Positioned(
          top: 12,
          right: 12,
          child: Container(
            padding: const EdgeInsets.symmetric(horizontal: 10, vertical: 4),
            decoration: BoxDecoration(
              color: Colors.black54,
              borderRadius: BorderRadius.circular(12),
            ),
            child: const Row(
              mainAxisSize: MainAxisSize.min,
              children: [
                Icon(Icons.videocam, color: Colors.white70, size: 14),
                SizedBox(width: 4),
                Text(
                  'AWS Chime',
                  style: TextStyle(color: Colors.white70, fontSize: 11),
                ),
              ],
            ),
          ),
        ),
      ],
    );
  }

  Widget _buildCallControlsBar() {
    return Container(
      padding: const EdgeInsets.symmetric(horizontal: 16, vertical: 10),
      color: Colors.black45,
      child: Row(
        mainAxisAlignment: MainAxisAlignment.center,
        mainAxisSize: MainAxisSize.max,
        children: [
          IconButton(
            tooltip: _localAudioEnabled
                ? 'Mute microphone'
                : 'Unmute microphone',
            onPressed: _toggleLocalAudio,
            icon: Icon(
              _localAudioEnabled ? Icons.mic : Icons.mic_off,
              color: Colors.white,
            ),
          ),
          const SizedBox(width: 8),
          IconButton(
            tooltip: _localVideoEnabled ? 'Turn camera off' : 'Turn camera on',
            onPressed: _toggleLocalVideo,
            icon: Icon(
              _localVideoEnabled ? Icons.videocam : Icons.videocam_off,
              color: Colors.white,
            ),
          ),
          const SizedBox(width: 8),
          IconButton(
            tooltip: 'Switch camera',
            onPressed: _switchCamera,
            icon: const Icon(Icons.cameraswitch, color: Colors.white),
          ),
          const SizedBox(width: 8),
          Container(
            decoration: const BoxDecoration(
              color: Colors.redAccent,
              shape: BoxShape.circle,
            ),
            child: IconButton(
              tooltip: 'End call',
              onPressed: _isEndingCall ? null : _endCallAndExit,
              icon: const Icon(Icons.call_end, color: Colors.white),
            ),
          ),
        ],
      ),
    );
  }

  // ================================================================
  // LOADING / ERROR STATES
  // ================================================================

  Widget _buildLoading() {
    return Center(
      child: Column(
        mainAxisAlignment: MainAxisAlignment.center,
        children: [
          const CircularProgressIndicator(color: AppTheme.videoCallText),
          const SizedBox(height: 16),
          const Text(
            'Connecting to call...',
            style: TextStyle(color: AppTheme.videoCallText),
          ),
          if (widget.recipientName != null) ...[
            const SizedBox(height: 8),
            Text(
              'with ${widget.recipientName}',
              style: const TextStyle(
                color: AppTheme.videoCallTextSecondary,
                fontSize: 14,
              ),
            ),
          ],
        ],
      ),
    );
  }

  Widget _buildError() {
    return Center(
      child: Padding(
        padding: const EdgeInsets.all(24),
        child: Column(
          mainAxisAlignment: MainAxisAlignment.center,
          children: [
            const Icon(Icons.error_outline, color: Colors.redAccent, size: 48),
            const SizedBox(height: 16),
            Text(
              'Could not connect to call',
              style: const TextStyle(
                color: AppTheme.videoCallText,
                fontSize: 18,
                fontWeight: FontWeight.w600,
              ),
            ),
            const SizedBox(height: 8),
            Text(
              _error ?? 'Unknown error',
              style: const TextStyle(
                color: AppTheme.videoCallTextSecondary,
                fontSize: 13,
              ),
              textAlign: TextAlign.center,
            ),
            const SizedBox(height: 24),
            ElevatedButton(
              onPressed: () => Navigator.of(context).pop(),
              child: const Text('Go Back'),
            ),
          ],
        ),
      ),
    );
  }
}

// ================================================================
// CALL DURATION TIMER — shows elapsed call time
// ================================================================

class _CallDurationTimer extends StatefulWidget {
  final DateTime startTime;
  const _CallDurationTimer({required this.startTime});

  @override
  State<_CallDurationTimer> createState() => _CallDurationTimerState();
}

class _CallDurationTimerState extends State<_CallDurationTimer> {
  late final Stream<int> _ticker;

  @override
  void initState() {
    super.initState();
    _ticker = Stream.periodic(const Duration(seconds: 1), (i) => i);
  }

  @override
  Widget build(BuildContext context) {
    return StreamBuilder<int>(
      stream: _ticker,
      builder: (context, snapshot) {
        final elapsed = DateTime.now().difference(widget.startTime);
        final minutes = elapsed.inMinutes
            .remainder(60)
            .toString()
            .padLeft(2, '0');
        final seconds = elapsed.inSeconds
            .remainder(60)
            .toString()
            .padLeft(2, '0');
        return Text(
          '$minutes:$seconds',
          style: const TextStyle(
            color: Colors.white60,
            fontSize: 24,
            fontFamily: 'monospace',
          ),
        );
      },
    );
  }
}
