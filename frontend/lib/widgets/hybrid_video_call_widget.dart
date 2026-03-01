import 'package:flutter/material.dart';
import '../services/video_call_service.dart';
import '../services/auth_token_manager.dart';
import '../services/call_notification_service.dart';
import '../services/user_role_storage_service.dart';
import '../config/theme/app_theme.dart';
import '../widgets/sentiment_dashboard_widget.dart';
import '../widgets/chime_meeting_embed.dart';

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
  final bool isVideoEnabled;
  final bool isAudioEnabled;
  final bool isInitiator;
  final String? userEmail;
  final String? userPhone;
  final String? userName;
  final String? recipientEmail;
  final String? recipientPhone;
  final String? recipientName;

  const HybridVideoCallWidget({
    super.key,
    required this.userId,
    required this.callId,
    this.recipientId,
    this.isVideoEnabled = true,
    this.isAudioEnabled = true,
    this.isInitiator = false,
    this.userEmail,
    this.userPhone,
    this.userName,
    this.recipientEmail,
    this.recipientPhone,
    this.recipientName,
  });

  @override
  State<HybridVideoCallWidget> createState() => _HybridVideoCallWidgetState();
}

class _HybridVideoCallWidgetState extends State<HybridVideoCallWidget> {
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
  DateTime? _lastAudioSampleSentAt;
  DateTime? _lastTranscriptSampleSentAt;
  bool _localAudioEnabled = true;
  bool _localVideoEnabled = true;

  // Latest sentiment data — updated via WebSocket push
  Map<String, dynamic> _sentimentData = {};

  @override
  void initState() {
    super.initState();
    _loadCurrentRole();
    _initializeCall();
  }

  Future<void> _loadCurrentRole() async {
    try {
      final role = await UserRoleStorageService.instance.getUserRole();
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
      // Retrieve JWT from secure storage
      // Replace with your actual auth token retrieval
      final jwtToken = await _getJwtToken();

      await _videoCallService.initialize(
        userId: widget.userId,
        jwtToken: jwtToken,
        onCallEnded: () {
          if (_showCallRejectedSummary) return;
          if (mounted && Navigator.canPop(context)) {
            Navigator.of(context).pop();
          }
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
            _rejectionSummaryText = '$declinedBy declined the call$reasonSuffix.';
          });
        },
      );

      final session = await _videoCallService.joinCall(
        callId:         widget.callId,
        otherPartyId:   widget.recipientId ?? '',
        isVideoEnabled: widget.isVideoEnabled,
        isAudioEnabled: widget.isAudioEnabled,
      );

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

  Future<void> _handleTranscriptSample(String transcript) async {
    if (!_isPatientView) return;
    final text = transcript.trim();
    if (text.length < 8) return;

    final now = DateTime.now();
    if (_lastTranscriptSampleSentAt != null &&
        now.difference(_lastTranscriptSampleSentAt!) < const Duration(seconds: 8)) {
      return;
    }

    _lastTranscriptSampleSentAt = now;
    try {
      await _videoCallService.sendTextForAnalysis(text);
    } catch (_) {}
  }

  Future<void> _handleAudioSample(String audioBase64, String audioFormat) async {
    if (!_isPatientView || _isSendingAudioSample) return;
    if (audioBase64.isEmpty) return;

    final now = DateTime.now();
    if (_lastAudioSampleSentAt != null &&
        now.difference(_lastAudioSampleSentAt!) < const Duration(seconds: 45)) {
      return;
    }

    _isSendingAudioSample = true;
    try {
      await _videoCallService.sendAudioForAnalysis(
        audioBase64,
        audioFormat: audioFormat,
      );
      _lastAudioSampleSentAt = DateTime.now();
    } catch (_) {
    } finally {
      _isSendingAudioSample = false;
    }
  }

  void _toggleLocalAudio() {
    setState(() {
      _localAudioEnabled = !_localAudioEnabled;
    });
  }

  void _toggleLocalVideo() {
    setState(() {
      _localVideoEnabled = !_localVideoEnabled;
    });
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
        child: Text('No active call session',
            style: TextStyle(color: AppTheme.videoCallText)),
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
                    onTextSend: (text) =>
                        _videoCallService.sendTextForAnalysis(text),
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
                                child: CircularProgressIndicator(strokeWidth: 2),
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
        const SnackBar(content: Text('Unable to retry: missing recipient info.')),
      );
      return;
    }

    setState(() {
      _isRetryingRejectedCall = true;
    });

    try {
      await _videoCallService.endCall();

      final newCallId = 'chime_call_${DateTime.now().millisecondsSinceEpoch}';
      final recipientRole = _isCaregiverView ? 'PATIENT' : 'CAREGIVER';

      final sent = await CallNotificationService.sendCallInvitation(
        recipientId: recipientId,
        recipientRole: recipientRole,
        callId: newCallId,
        isVideoCall: widget.isVideoEnabled,
      );

      if (!sent) {
        if (!mounted) return;
        setState(() {
          _isRetryingRejectedCall = false;
        });
        ScaffoldMessenger.of(context).showSnackBar(
          const SnackBar(content: Text('Could not place retry call. Please try again.')),
        );
        return;
      }

      if (!mounted) return;
      Navigator.of(context).pushReplacement(
        MaterialPageRoute(
          builder: (_) => HybridVideoCallWidget(
            userId: widget.userId,
            callId: newCallId,
            recipientId: widget.recipientId,
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
    final hasMediaEndpoints = mediaPlacement.values
        .whereType<String>()
        .any((value) => value.trim().isNotEmpty);
    final isLocalMockSession =
        _callSession!.joinToken.startsWith('local-join-token-');

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
        onEndCallRequested: () async {
          await _videoCallService.endCall();
        },
        onTranscriptSample: _handleTranscriptSample,
        onAudioSample: _handleAudioSample,
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
                      ? 'Connected to local mock meeting'
                      : 'Connected to meeting',
                  style: const TextStyle(color: Colors.white70, fontSize: 18),
                ),
                const SizedBox(height: 8),
                Text(
                  'Meeting: ${_callSession!.meetingId.substring(0, 8)}...',
                  style: const TextStyle(
                      color: Colors.white38,
                      fontSize: 12,
                      fontFamily: 'monospace'),
                ),
                const SizedBox(height: 24),
                // Duration counter
                _CallDurationTimer(startTime: DateTime.now()),
              ],
            ),
          ),
        ),
        Positioned(
          left: 0,
          right: 0,
          bottom: 16,
          child: Center(
            child: Container(
              padding: const EdgeInsets.symmetric(horizontal: 16, vertical: 10),
              decoration: BoxDecoration(
                color: Colors.black45,
                borderRadius: BorderRadius.circular(24),
              ),
              child: Row(
                mainAxisSize: MainAxisSize.min,
                children: [
                  IconButton(
                    tooltip: _localAudioEnabled ? 'Mute microphone' : 'Unmute microphone',
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
                  Container(
                    decoration: const BoxDecoration(
                      color: Colors.redAccent,
                      shape: BoxShape.circle,
                    ),
                    child: IconButton(
                      tooltip: 'End call',
                      onPressed: () async {
                        await _videoCallService.endCall();
                        if (mounted && Navigator.canPop(context)) {
                          Navigator.of(context).pop();
                        }
                      },
                      icon: const Icon(Icons.call_end, color: Colors.white),
                    ),
                  ),
                ],
              ),
            ),
          ),
        ),
        // Chime badge
        Positioned(
          top: 12,
          right: 12,
          child: Container(
            padding:
                const EdgeInsets.symmetric(horizontal: 10, vertical: 4),
            decoration: BoxDecoration(
              color: Colors.black54,
              borderRadius: BorderRadius.circular(12),
            ),
            child: const Row(
              mainAxisSize: MainAxisSize.min,
              children: [
                Icon(Icons.videocam, color: Colors.white70, size: 14),
                SizedBox(width: 4),
                Text('AWS Chime',
                    style:
                        TextStyle(color: Colors.white70, fontSize: 11)),
              ],
            ),
          ),
        ),
      ],
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
          const Text('Connecting to call...',
              style: TextStyle(color: AppTheme.videoCallText)),
          if (widget.recipientName != null) ...[
            const SizedBox(height: 8),
            Text(
              'with ${widget.recipientName}',
              style: const TextStyle(
                  color: AppTheme.videoCallTextSecondary, fontSize: 14),
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
                  fontWeight: FontWeight.w600),
            ),
            const SizedBox(height: 8),
            Text(
              _error ?? 'Unknown error',
              style: const TextStyle(
                  color: AppTheme.videoCallTextSecondary, fontSize: 13),
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
        final minutes = elapsed.inMinutes.remainder(60).toString().padLeft(2, '0');
        final seconds = elapsed.inSeconds.remainder(60).toString().padLeft(2, '0');
        return Text(
          '$minutes:$seconds',
          style: const TextStyle(
              color: Colors.white60, fontSize: 24, fontFamily: 'monospace'),
        );
      },
    );
  }
}
