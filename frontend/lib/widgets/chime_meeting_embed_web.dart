// ignore_for_file: avoid_web_libraries_in_flutter

import 'dart:convert';
import 'dart:html' as html;
import 'dart:ui_web' as ui_web;
import 'dart:async';

import 'package:flutter/material.dart';
import 'package:flutter/foundation.dart';

const String _chimeSdkUrl = String.fromEnvironment(
  'CHIME_SDK_URL',
  defaultValue: '/amazon-chime-sdk.min.js',
);

const bool _allowExternalSdkFallback = bool.fromEnvironment(
  'CHIME_SDK_ALLOW_EXTERNAL_FALLBACK',
  defaultValue: !kReleaseMode,
);

final Map<String, html.IFrameElement> _activeMeetingIframes =
    <String, html.IFrameElement>{};

Future<bool> requestChimeCameraSwitch({String? meetingId}) async {
  Iterable<html.IFrameElement> targets;
  if (meetingId != null && meetingId.trim().isNotEmpty) {
    final frame = _activeMeetingIframes[meetingId.trim()];
    if (frame == null) return false;
    targets = [frame];
  } else {
    targets = _activeMeetingIframes.values;
  }

  var posted = false;
  for (final frame in targets) {
    final win = frame.contentWindow;
    if (win == null) continue;
    win.postMessage({
      'source': 'careconnect-flutter',
      'action': 'switch-camera',
      if (meetingId != null && meetingId.trim().isNotEmpty)
        'meetingId': meetingId.trim(),
    }, '*');
    posted = true;
  }

  return posted;
}

Future<bool> requestChimeAudioToggle({
  required bool muted,
  String? meetingId,
}) async {
  Iterable<html.IFrameElement> targets;
  if (meetingId != null && meetingId.trim().isNotEmpty) {
    final frame = _activeMeetingIframes[meetingId.trim()];
    if (frame == null) return false;
    targets = [frame];
  } else {
    targets = _activeMeetingIframes.values;
  }

  var posted = false;
  for (final frame in targets) {
    final win = frame.contentWindow;
    if (win == null) continue;
    win.postMessage({
      'source': 'careconnect-flutter',
      'action': 'toggle-audio',
      'muted': muted,
      if (meetingId != null && meetingId.trim().isNotEmpty)
        'meetingId': meetingId.trim(),
    }, '*');
    posted = true;
  }
  return posted;
}

Future<bool> requestChimeVideoToggle({
  required bool muted,
  String? meetingId,
}) async {
  Iterable<html.IFrameElement> targets;
  if (meetingId != null && meetingId.trim().isNotEmpty) {
    final frame = _activeMeetingIframes[meetingId.trim()];
    if (frame == null) return false;
    targets = [frame];
  } else {
    targets = _activeMeetingIframes.values;
  }

  var posted = false;
  for (final frame in targets) {
    final win = frame.contentWindow;
    if (win == null) continue;
    win.postMessage({
      'source': 'careconnect-flutter',
      'action': 'toggle-video',
      'muted': muted,
      if (meetingId != null && meetingId.trim().isNotEmpty)
        'meetingId': meetingId.trim(),
    }, '*');
    posted = true;
  }
  return posted;
}

Widget buildChimeMeetingEmbed({
  required String meetingId,
  required String attendeeId,
  required String joinToken,
  required Map<String, dynamic> mediaPlacement,
  String? mediaRegion,
  String? externalUserId,
  required bool videoEnabled,
  required bool audioEnabled,
  bool enableAutoSentimentCapture = false,
  int sentimentCaptureIntervalMs = 15000,
  VoidCallback? onEndCallRequested,
  void Function(String transcript)? onTranscriptSample,
  void Function(String audioBase64, String audioFormat)? onAudioSample,
  void Function(String imageBase64)? onVideoSample,
}) {
  return _ChimeMeetingEmbedWeb(
    meetingId: meetingId,
    attendeeId: attendeeId,
    joinToken: joinToken,
    mediaPlacement: mediaPlacement,
    mediaRegion: mediaRegion,
    externalUserId: externalUserId,
    videoEnabled: videoEnabled,
    audioEnabled: audioEnabled,
    enableAutoSentimentCapture: enableAutoSentimentCapture,
    sentimentCaptureIntervalMs: sentimentCaptureIntervalMs,
    onEndCallRequested: onEndCallRequested,
    onTranscriptSample: onTranscriptSample,
    onAudioSample: onAudioSample,
    onVideoSample: onVideoSample,
  );
}

class _ChimeMeetingEmbedWeb extends StatefulWidget {
  final String meetingId;
  final String attendeeId;
  final String joinToken;
  final Map<String, dynamic> mediaPlacement;
  final String? mediaRegion;
  final String? externalUserId;
  final bool videoEnabled;
  final bool audioEnabled;
  final bool enableAutoSentimentCapture;
  final int sentimentCaptureIntervalMs;
  final VoidCallback? onEndCallRequested;
  final void Function(String transcript)? onTranscriptSample;
  final void Function(String audioBase64, String audioFormat)? onAudioSample;
  final void Function(String imageBase64)? onVideoSample;

  const _ChimeMeetingEmbedWeb({
    required this.meetingId,
    required this.attendeeId,
    required this.joinToken,
    required this.mediaPlacement,
    required this.mediaRegion,
    required this.externalUserId,
    required this.videoEnabled,
    required this.audioEnabled,
    required this.enableAutoSentimentCapture,
    required this.sentimentCaptureIntervalMs,
    required this.onEndCallRequested,
    required this.onTranscriptSample,
    required this.onAudioSample,
    required this.onVideoSample,
  });

  @override
  State<_ChimeMeetingEmbedWeb> createState() => _ChimeMeetingEmbedWebState();
}

class _ChimeMeetingEmbedWebState extends State<_ChimeMeetingEmbedWeb> {
  late final String _viewType;
  StreamSubscription<html.MessageEvent>? _messageSubscription;
  String? _guardMessage;

  @override
  void initState() {
    super.initState();
    unawaited(_ensureMediaPermissions());
    _viewType =
        'chime-meeting-view-${DateTime.now().microsecondsSinceEpoch}-${widget.meetingId}';

    final config = {
      'meetingId': widget.meetingId,
      'attendeeId': widget.attendeeId,
      'joinToken': widget.joinToken,
      'mediaPlacement': widget.mediaPlacement,
      'mediaRegion': widget.mediaRegion ?? 'us-east-1',
      'externalUserId':
          widget.externalUserId ?? 'careconnect-${widget.attendeeId.substring(0, 8)}',
      'videoEnabled': widget.videoEnabled,
      'audioEnabled': widget.audioEnabled,
      'enableAutoSentimentCapture': widget.enableAutoSentimentCapture,
      'sentimentCaptureIntervalMs': widget.sentimentCaptureIntervalMs,
      'sdkUrl': _chimeSdkUrl,
      'allowExternalSdkFallback': _allowExternalSdkFallback,
    };

    final configJson = jsonEncode(config);

    _messageSubscription = html.window.onMessage.listen((event) {
      final data = event.data;
      if (data is Map && data['source'] == 'careconnect-chime') {
        final level = data['level'] ?? 'info';
        final message = data['message'] ?? '';
        debugPrint('[CareConnect][Chime][$level] $message');

        if (!mounted) return;
        if (data['action'] == 'end-call-request') {
          widget.onEndCallRequested?.call();
          return;
        }

        if (data['action'] == 'sentiment-transcript') {
          final payload = data['payload'];
          if (payload is Map) {
            final transcript = (payload['text'] ?? '').toString().trim();
            if (transcript.isNotEmpty) {
              widget.onTranscriptSample?.call(transcript);
            }
          }
          return;
        }

        if (data['action'] == 'sentiment-audio-sample') {
          final payload = data['payload'];
          if (payload is Map) {
            final audioBase64 = (payload['audioBase64'] ?? '').toString().trim();
            final audioFormat = (payload['audioFormat'] ?? 'wav').toString().trim();
            if (audioBase64.isNotEmpty) {
              widget.onAudioSample?.call(audioBase64, audioFormat.isEmpty ? 'wav' : audioFormat);
            }
          }
          return;
        }

        if (data['action'] == 'sentiment-video-sample') {
          final payload = data['payload'];
          if (payload is Map) {
            final imageBase64 = (payload['imageBase64'] ?? '').toString().trim();
            if (imageBase64.isNotEmpty) {
              widget.onVideoSample?.call(imageBase64);
            }
          }
          return;
        }

        if (level == 'info' &&
            (message.toString().contains('audioVideoDidStart') ||
                message.toString().contains('Local video tile bound') ||
                message.toString().contains('Remote video tile bound'))) {
          if (_guardMessage != null) {
            setState(() {
              _guardMessage = null;
            });
          }
          return;
        }

        if (level == 'error' && message.toString().contains('Chime SDK')) {
          setState(() {
            _guardMessage =
                'Chime SDK is unavailable. Host the SDK at $_chimeSdkUrl or set CHIME_SDK_URL to a valid asset.';
          });
        }
      }
    });

    ui_web.platformViewRegistry.registerViewFactory(_viewType, (int viewId) {
      final iframe = html.IFrameElement()
        ..style.border = '0'
        ..style.width = '100%'
        ..style.height = '100%'
        ..setAttribute('allow', 'camera; microphone; autoplay; fullscreen')
        ..srcdoc = _buildMeetingHtml(configJson);
      _activeMeetingIframes[widget.meetingId] = iframe;
      return iframe;
    });
  }

  Future<void> _ensureMediaPermissions() async {
    final needAudio = widget.audioEnabled;
    final needVideo = widget.videoEnabled;

    if (!needAudio && !needVideo) {
      return;
    }

    try {
      final mediaDevices = html.window.navigator.mediaDevices;
      if (mediaDevices == null) {
        if (mounted) {
          setState(() {
            _guardMessage =
                'Browser media devices are unavailable. Camera/mic access cannot be requested.';
          });
        }
        return;
      }

      final stream = await mediaDevices.getUserMedia({
        'audio': needAudio,
        'video': needVideo,
      });

      for (final track in stream.getTracks()) {
        track.stop();
      }
    } catch (e) {
      final errorText = e.toString();
      String guardMessage;

      if (errorText.contains('NotAllowedError') ||
          errorText.contains('PermissionDeniedError') ||
          errorText.contains('SecurityError')) {
        guardMessage =
            'Camera/mic permission is blocked. Please allow access in Chrome site settings and retry.';
      } else if (errorText.contains('NotReadableError') ||
          errorText.contains('TrackStartError') ||
          errorText.contains('OverconstrainedError')) {
        guardMessage =
            'Camera or microphone is busy/unavailable. Close other apps or use another device, then retry.';
      } else if (errorText.contains('NotFoundError')) {
        guardMessage =
            'No microphone/camera was found for this browser session. Connect a device and retry.';
      } else {
        guardMessage =
            'Unable to initialize camera/mic right now. Verify browser permissions and device availability, then retry.';
      }

      if (mounted) {
        setState(() {
          _guardMessage = guardMessage;
        });
      }
      debugPrint('[CareConnect][Chime][warn] getUserMedia permission check failed: $e');
    }
  }

  @override
  Widget build(BuildContext context) {
    return Stack(
      children: [
        HtmlElementView(viewType: _viewType),
        if (_guardMessage != null)
          Positioned(
            top: 10,
            left: 10,
            right: 10,
            child: Material(
              color: Colors.transparent,
              child: Container(
                padding: const EdgeInsets.all(10),
                decoration: BoxDecoration(
                  color: Colors.red.withValues(alpha: 0.9),
                  borderRadius: BorderRadius.circular(8),
                ),
                child: Text(
                  _guardMessage!,
                  style: const TextStyle(
                    color: Colors.white,
                    fontSize: 12,
                    fontWeight: FontWeight.w600,
                  ),
                ),
              ),
            ),
          ),
      ],
    );
  }

  @override
  void dispose() {
    _messageSubscription?.cancel();
    _activeMeetingIframes.remove(widget.meetingId);
    super.dispose();
  }

  String _buildMeetingHtml(String configJson) {
    return '''
<!doctype html>
<html>
  <head>
    <meta charset="utf-8" />
    <meta name="viewport" content="width=device-width, initial-scale=1" />
    <style>
      html, body { margin:0; padding:0; width:100%; height:100%; background:#000; overflow:hidden; }
      #stage { position:relative; width:100%; height:100%; background:#000; }
      #remoteVideo { width:100%; height:100%; object-fit:cover; background:#000; }
      #localVideo {
        position:absolute; right:16px; top:16px; width:22%; max-width:260px;
        aspect-ratio:16/9; object-fit:cover; border-radius:12px;
        border:2px solid rgba(255,255,255,0.75); background:#111;
      }
      #status {
        position:absolute; left:16px; bottom:88px; padding:6px 10px;
        border-radius:12px; font:12px system-ui; color:#fff; background:rgba(0,0,0,0.45);
      }
      #controls {
        position:absolute; left:50%; transform:translateX(-50%); bottom:16px;
        display:none !important;
        gap:clamp(12px, 1.8vw, 20px); align-items:center;
        padding:0;
        background:transparent;
        border:none;
      }
      .cc-btn {
        width:clamp(64px, 7.5vh, 88px);
        height:clamp(64px, 7.5vh, 88px);
        padding:0;
        border:1px solid rgba(255,255,255,0.2); border-radius:999px;
        display:flex; align-items:center; justify-content:center;
        color:#ffffff; cursor:pointer;
        background:rgba(98,98,102,0.96);
        box-shadow:0 1px 4px rgba(0,0,0,0.28);
        transition:background 140ms ease, transform 140ms ease, border-color 140ms ease;
      }
      .cc-btn .icon {
        display:inline-flex;
        align-items:center;
        justify-content:center;
      }
      .cc-btn .icon svg {
        width:clamp(30px, 3.7vh, 42px);
        height:clamp(30px, 3.7vh, 42px);
        stroke:currentColor;
        fill:none;
        stroke-width:2.2;
        stroke-linecap:round;
        stroke-linejoin:round;
      }
      #endBtn .icon svg {
        width:clamp(36px, 4.5vh, 52px);
        height:clamp(36px, 4.5vh, 52px);
        stroke-width:2.6;
      }
      .cc-btn.off {
        background:rgba(78,78,82,0.96);
        border-color:rgba(255,255,255,0.26);
      }
      .cc-btn.switch {
        background:rgba(98,98,102,0.96);
        border-color:rgba(255,255,255,0.2);
      }
      .cc-btn.switch:hover {
        background:rgba(112,112,116,0.96);
      }
      .cc-btn.switch:disabled {
        background:rgba(78,78,82,0.88);
        border-color:rgba(255,255,255,0.2);
      }
      .cc-btn.end {
        background:rgba(241,85,84,0.98);
        border-color:rgba(255,214,214,0.5);
      }
      .cc-btn:disabled { opacity:0.5; cursor:not-allowed; }
      .cc-btn:hover:not(:disabled) {
        background:rgba(112,112,116,0.98);
        transform:translateY(-1px);
      }
      .cc-btn.end:hover:not(:disabled) { background:rgba(225,69,68,0.98); }
    </style>
  </head>
  <body>
    <div id="stage">
      <video id="remoteVideo" autoplay playsinline muted></video>
      <video id="localVideo" autoplay playsinline muted></video>
      <audio id="remoteAudio" autoplay></audio>
      <div id="status">Connecting media...</div>
      <div id="controls">
        <button id="micBtn" class="cc-btn" type="button" title="Toggle microphone" aria-label="Toggle microphone"><span class="icon"></span></button>
        <button id="camBtn" class="cc-btn" type="button" title="Toggle camera" aria-label="Toggle camera"><span class="icon"></span></button>
        <button id="endBtn" class="cc-btn end" type="button" title="End call" aria-label="End call"><span class="icon"></span></button>
        <button id="switchCamBtn" class="cc-btn switch" type="button" title="Switch camera" aria-label="Switch camera"><span class="icon"></span></button>
      </div>
    </div>
    <script>
      (async function () {
        if (typeof window.global === 'undefined') {
          window.global = window;
        }

        if (!window.__careconnectPatchedGetStats &&
            window.RTCPeerConnection &&
            window.RTCPeerConnection.prototype &&
            typeof window.RTCPeerConnection.prototype.getStats === 'function') {
          const originalGetStats = window.RTCPeerConnection.prototype.getStats;
          const NativeMediaStreamTrack = window.MediaStreamTrack;

          window.RTCPeerConnection.prototype.getStats = function(...args) {
            try {
              if (args.length > 0) {
                const candidate = args[0];
                const isTrack = !!candidate && (
                  (NativeMediaStreamTrack && candidate instanceof NativeMediaStreamTrack) ||
                  (typeof candidate.kind === 'string' &&
                   typeof candidate.id === 'string' &&
                   typeof candidate.enabled === 'boolean')
                );

                if (!isTrack) {
                  return originalGetStats.call(this);
                }
              }

              return originalGetStats.apply(this, args);
            } catch (err) {
              const message = err && err.message ? String(err.message) : String(err);
              if (message.includes("parameter 1 is not of type 'MediaStreamTrack'")) {
                return originalGetStats.call(this);
              }
              throw err;
            }
          };

          window.__careconnectPatchedGetStats = true;
        }

        const config = $configJson;
        const statusEl = document.getElementById('status');
        const localVideo = document.getElementById('localVideo');
        const remoteVideo = document.getElementById('remoteVideo');
        const remoteAudio = document.getElementById('remoteAudio');
        const micBtn = document.getElementById('micBtn');
        const switchCamBtn = document.getElementById('switchCamBtn');
        const endBtn = document.getElementById('endBtn');
        const camBtn = document.getElementById('camBtn');
        const shouldAutoSentimentCapture =
          !!config.enableAutoSentimentCapture && (!!config.audioEnabled || !!config.videoEnabled);
        const sentimentCaptureIntervalMs =
          Number(config.sentimentCaptureIntervalMs) > 0
            ? Math.max(3000, Number(config.sentimentCaptureIntervalMs))
            : 15000;
        let isAudioMuted = !config.audioEnabled;
        let isVideoMuted = !config.videoEnabled;
        let availableVideoInputs = [];
        let sentimentAudioRecorder = null;
        let sentimentAudioStream = null;
        let sentimentVideoTimer = null;
        let speechRecognizer = null;
        let speechRestartTimer = null;
        let lastTranscriptSignature = '';
        let lastTranscriptAt = 0;

        function setStatus(msg) { statusEl.textContent = msg; }
        function report(level, msg) {
          try {
            window.parent.postMessage({ source: 'careconnect-chime', level, message: msg }, '*');
          } catch (_) {}
        }

        function emitAction(action, payload) {
          try {
            window.parent.postMessage(
              {
                source: 'careconnect-chime',
                action,
                payload: payload || {},
                meetingId: config.meetingId,
              },
              '*',
            );
          } catch (_) {}
        }

        window.addEventListener('message', async (event) => {
          const data = event && event.data ? event.data : null;
          if (!data || data.source !== 'careconnect-flutter') {
            return;
          }

          if (data.action === 'toggle-audio') {
            try {
              await setLocalAudioMuted(!!data.muted);
            } catch (audioErr) {
              report('warn', 'Flutter overlay audio toggle failed: ' + String(audioErr));
            }
            return;
          }

          if (data.action === 'toggle-video') {
            try {
              await setLocalVideoMuted(!!data.muted);
            } catch (videoErr) {
              report('warn', 'Flutter overlay video toggle failed: ' + String(videoErr));
            }
            return;
          }

          if (data.action === 'switch-camera') {
            try {
              if (audioVideo && typeof audioVideo.listVideoInputDevices === 'function') {
                availableVideoInputs = await audioVideo.listVideoInputDevices();
              }
              const switched = await switchVideoInput('flutter-overlay');
              if (switched) {
                localVideoBound = false;
                ensureLocalVideoTile();
                report('info', 'Camera switched by Flutter overlay');
              } else {
                report('warn', 'Flutter overlay requested camera switch but no alternative camera was found');
              }
              updateControlButtons();
            } catch (switchErr) {
              report('warn', 'Flutter overlay camera switch failed: ' + String(switchErr));
            }
          }
        });

        function normalizeAudioFormat(mimeType) {
          const lower = String(mimeType || '').toLowerCase();
          if (lower.includes('webm')) return 'webm';
          if (lower.includes('ogg')) return 'ogg';
          if (lower.includes('mpeg') || lower.includes('mp3')) return 'mp3';
          if (lower.includes('mp4') || lower.includes('aac')) return 'mp4';
          if (lower.includes('wav')) return 'wav';
          return 'wav';
        }

        function blobToBase64(blob) {
          return new Promise((resolve, reject) => {
            const reader = new FileReader();
            reader.onloadend = () => {
              const dataUrl = String(reader.result || '');
              const comma = dataUrl.indexOf(',');
              if (comma < 0) {
                reject(new Error('Invalid audio payload'));
                return;
              }
              resolve(dataUrl.substring(comma + 1));
            };
            reader.onerror = reject;
            reader.readAsDataURL(blob);
          });
        }

        function emitTranscriptSample(rawText) {
          const text = String(rawText || '').trim();
          if (text.length < 8) {
            return;
          }

          const signature = text.toLowerCase().replace(/\\s+/g, ' ').trim();
          const now = Date.now();
          if (signature === lastTranscriptSignature && (now - lastTranscriptAt) < 12000) {
            return;
          }

          lastTranscriptSignature = signature;
          lastTranscriptAt = now;
          emitAction('sentiment-transcript', {
            text,
            source: 'speech-recognition',
            capturedAt: new Date().toISOString(),
          });
        }

        function stopAutoSentimentCapture() {
          if (speechRestartTimer) {
            clearTimeout(speechRestartTimer);
            speechRestartTimer = null;
          }

          if (speechRecognizer) {
            try {
              speechRecognizer.onresult = null;
              speechRecognizer.onerror = null;
              speechRecognizer.onend = null;
              speechRecognizer.stop();
            } catch (_) {}
            speechRecognizer = null;
          }

          if (sentimentAudioRecorder) {
            try {
              if (sentimentAudioRecorder.state !== 'inactive') {
                sentimentAudioRecorder.stop();
              }
            } catch (_) {}
            sentimentAudioRecorder = null;
          }

          if (sentimentAudioStream) {
            try {
              sentimentAudioStream.getTracks().forEach((track) => track.stop());
            } catch (_) {}
            sentimentAudioStream = null;
          }

          if (sentimentVideoTimer) {
            clearInterval(sentimentVideoTimer);
            sentimentVideoTimer = null;
          }
        }

        function startSpeechRecognitionCapture() {
          if (!shouldAutoSentimentCapture) {
            return;
          }

          const SpeechRecognition = window.SpeechRecognition || window.webkitSpeechRecognition;
          if (!SpeechRecognition) {
            report('warn', 'SpeechRecognition API unavailable; transcript sentiment auto-capture disabled.');
            return;
          }

          speechRecognizer = new SpeechRecognition();
          speechRecognizer.continuous = true;
          speechRecognizer.interimResults = false;
          speechRecognizer.lang = config.speechLocale || navigator.language || 'en-US';

          speechRecognizer.onresult = (event) => {
            let transcript = '';
            for (let i = event.resultIndex; i < event.results.length; i += 1) {
              const result = event.results[i];
              if (result && result.isFinal && result[0] && result[0].transcript) {
                transcript += ' ' + result[0].transcript;
              }
            }
            if (transcript.trim()) {
              emitTranscriptSample(transcript.trim());
            }
          };

          speechRecognizer.onerror = (event) => {
            report('warn', 'Speech recognition error: ' + String(event && event.error ? event.error : 'unknown'));
          };

          speechRecognizer.onend = () => {
            if (!shouldAutoSentimentCapture) {
              return;
            }
            if (speechRestartTimer) {
              clearTimeout(speechRestartTimer);
            }
            speechRestartTimer = setTimeout(() => {
              try {
                if (speechRecognizer) {
                  speechRecognizer.start();
                }
              } catch (_) {}
            }, 1200);
          };

          try {
            speechRecognizer.start();
            report('info', 'Speech transcription capture started');
          } catch (speechStartErr) {
            report('warn', 'Unable to start speech recognition: ' + String(speechStartErr));
          }
        }

        async function startAudioSentimentCapture() {
          if (!shouldAutoSentimentCapture) {
            return;
          }
          if (!config.audioEnabled) {
            return;
          }
          if (!window.MediaRecorder || !navigator.mediaDevices || !navigator.mediaDevices.getUserMedia) {
            report('warn', 'MediaRecorder API unavailable; voice sentiment auto-capture disabled.');
            return;
          }

          try {
            sentimentAudioStream = await navigator.mediaDevices.getUserMedia({ audio: true, video: false });

            const mimeCandidates = [
              'audio/webm;codecs=opus',
              'audio/webm',
              'audio/ogg;codecs=opus',
              'audio/mp4',
            ];

            let selectedMime = '';
            if (typeof MediaRecorder.isTypeSupported === 'function') {
              for (const candidate of mimeCandidates) {
                if (MediaRecorder.isTypeSupported(candidate)) {
                  selectedMime = candidate;
                  break;
                }
              }
            }

            sentimentAudioRecorder = selectedMime
              ? new MediaRecorder(sentimentAudioStream, { mimeType: selectedMime })
              : new MediaRecorder(sentimentAudioStream);

            sentimentAudioRecorder.ondataavailable = async (event) => {
              if (!event.data || event.data.size === 0) {
                return;
              }

              try {
                const audioBase64 = await blobToBase64(event.data);
                if (!audioBase64 || audioBase64.length < 512) {
                  return;
                }

                const recorderMime = sentimentAudioRecorder && sentimentAudioRecorder.mimeType
                  ? sentimentAudioRecorder.mimeType
                  : selectedMime;

                emitAction('sentiment-audio-sample', {
                  audioBase64,
                  audioFormat: normalizeAudioFormat(recorderMime),
                  capturedAt: new Date().toISOString(),
                });
              } catch (audioEmitErr) {
                report('warn', 'Failed processing sentiment audio chunk: ' + String(audioEmitErr));
              }
            };

            sentimentAudioRecorder.start(sentimentCaptureIntervalMs);
            report('info', 'Voice sentiment capture started (' + sentimentCaptureIntervalMs + 'ms chunks)');
          } catch (audioCaptureErr) {
            report('warn', 'Unable to start voice sentiment capture: ' + String(audioCaptureErr));
            if (sentimentAudioStream) {
              try {
                sentimentAudioStream.getTracks().forEach((track) => track.stop());
              } catch (_) {}
              sentimentAudioStream = null;
            }
          }
        }

        function captureVideoSampleFrame() {
          try {
            if (!localVideo || localVideo.readyState < 2 || localVideo.videoWidth === 0 || localVideo.videoHeight === 0) {
              return;
            }

            const canvas = document.createElement('canvas');
            const maxWidth = 640;
            const scale = Math.min(1, maxWidth / localVideo.videoWidth);
            const width = Math.max(1, Math.floor(localVideo.videoWidth * scale));
            const height = Math.max(1, Math.floor(localVideo.videoHeight * scale));
            canvas.width = width;
            canvas.height = height;

            const ctx = canvas.getContext('2d', { alpha: false });
            if (!ctx) {
              return;
            }

            ctx.drawImage(localVideo, 0, 0, width, height);
            const dataUrl = canvas.toDataURL('image/jpeg', 0.68);
            const commaIndex = dataUrl.indexOf(',');
            if (commaIndex <= 0) {
              return;
            }

            const imageBase64 = dataUrl.substring(commaIndex + 1);
            if (!imageBase64 || imageBase64.length < 1024) {
              return;
            }

            emitAction('sentiment-video-sample', {
              imageBase64,
              imageFormat: 'jpeg',
              capturedAt: new Date().toISOString(),
            });
          } catch (videoFrameErr) {
            report('warn', 'Unable to capture video sentiment frame: ' + String(videoFrameErr));
          }
        }

        function startVideoSentimentCapture() {
          if (!shouldAutoSentimentCapture || !config.videoEnabled) {
            return;
          }
          if (sentimentVideoTimer) {
            clearInterval(sentimentVideoTimer);
          }

          captureVideoSampleFrame();
          sentimentVideoTimer = setInterval(() => {
            captureVideoSampleFrame();
          }, sentimentCaptureIntervalMs);
          report('info', 'Video sentiment capture started (' + sentimentCaptureIntervalMs + 'ms frames)');
        }

        async function startAutoSentimentCapture() {
          if (!shouldAutoSentimentCapture) {
            return;
          }
          await startAudioSentimentCapture();
          startVideoSentimentCapture();
          startSpeechRecognitionCapture();
        }

        function iconSvg(name) {
          switch (name) {
            case 'mic':
              return '<svg viewBox="0 0 24 24" aria-hidden="true"><rect x="9" y="3.5" width="6" height="11" rx="3"/><path d="M6.5 11.5a5.5 5.5 0 0 0 11 0"/><path d="M12 17v3"/><path d="M9.5 20h5"/></svg>';
            case 'mic-off':
              return '<svg viewBox="0 0 24 24" aria-hidden="true"><path d="M4 4l16 16"/><rect x="9" y="3.5" width="6" height="11" rx="3"/><path d="M6.5 11.5a5.5 5.5 0 0 0 11 0"/><path d="M12 17v3"/><path d="M9.5 20h5"/></svg>';
            case 'video':
              return '<svg viewBox="0 0 24 24" aria-hidden="true"><rect x="3.5" y="7" width="12.5" height="10" rx="2"/><path d="M16.2 10 20.5 8v8l-4.3-2z"/></svg>';
            case 'video-off':
              return '<svg viewBox="0 0 24 24" aria-hidden="true"><path d="M4 4l16 16"/><rect x="3.5" y="7" width="12.5" height="10" rx="2"/><path d="M16.2 10 20.5 8v8l-4.3-2z"/></svg>';
            case 'hangup':
              return '<svg viewBox="0 0 24 24" aria-hidden="true"><path d="M4.4 15.2c4.2-4.2 11-4.2 15.2 0"/><path d="M7.2 12.6 5 15.1l2.7 2"/><path d="M16.8 12.6 19 15.1l-2.7 2"/></svg>';
            case 'switch-cam':
            default:
              return '<svg viewBox="0 0 24 24" aria-hidden="true"><rect x="4" y="7" width="16" height="11" rx="2.4"/><path d="M9.2 7 10.6 5h2.8L14.8 7"/><path d="M8 11.2h3"/><path d="M9.4 9.8 11 11.2 9.4 12.6"/><path d="M16 13.8h-3"/><path d="M14.6 12.4 13 13.8 14.6 15.2"/></svg>';
          }
        }

        function updateControlButtons() {
          if (micBtn) {
            micBtn.innerHTML = '<span class="icon">' + iconSvg(isAudioMuted ? 'mic-off' : 'mic') + '</span>';
            micBtn.classList.toggle('off', isAudioMuted);
            micBtn.disabled = !config.audioEnabled;
          }
          if (camBtn) {
            camBtn.innerHTML = '<span class="icon">' + iconSvg(isVideoMuted ? 'video-off' : 'video') + '</span>';
            camBtn.classList.toggle('off', isVideoMuted);
            camBtn.disabled = !config.videoEnabled;
          }
          if (switchCamBtn) {
            const canSwitchCamera =
              config.videoEnabled &&
              !isVideoMuted &&
              Array.isArray(availableVideoInputs) &&
              availableVideoInputs.length > 1;
            switchCamBtn.innerHTML = '<span class="icon">' + iconSvg('switch-cam') + '</span>';
            switchCamBtn.disabled = !canSwitchCamera;
          }
          if (endBtn) {
            endBtn.innerHTML = '<span class="icon">' + iconSvg('hangup') + '</span>';
          }
        }

        async function loadChimeSdk() {
          if (window.AmazonChimeSDK || window.ChimeSDK) {
            return window.AmazonChimeSDK || window.ChimeSDK;
          }

          const scriptUrls = [config.sdkUrl];

          if (config.allowExternalSdkFallback) {
            scriptUrls.push(
              'https://unpkg.com/amazon-chime-sdk-js@3.20.0/build/amazon-chime-sdk.min.js',
              'https://cdn.jsdelivr.net/npm/amazon-chime-sdk-js@3.20.0/build/amazon-chime-sdk.min.js'
            );
          }

          for (const url of scriptUrls) {
            try {
              await new Promise((resolve, reject) => {
                const script = document.createElement('script');
                script.src = url;
                script.async = true;
                script.onload = resolve;
                script.onerror = reject;
                document.head.appendChild(script);
              });

              if (window.AmazonChimeSDK || window.ChimeSDK) {
                report('info', 'Chime SDK loaded from: ' + url);
                return window.AmazonChimeSDK || window.ChimeSDK;
              }

              report('warn', 'Script loaded but no SDK global exposed: ' + url);
            } catch (_) {
              report('warn', 'Failed loading Chime SDK from: ' + url);
            }
          }

          if (!config.allowExternalSdkFallback) {
            throw new Error(
              'Chime SDK not available at ' + config.sdkUrl +
              '. Provide CHIME_SDK_URL pointing to a hosted SDK asset.'
            );
          }

          const moduleUrls = [
            config.sdkUrl,
            'https://esm.run/amazon-chime-sdk-js@3.20.0',
            'https://ga.jspm.io/npm:amazon-chime-sdk-js@3.20.0/build/index.js'
          ];

          for (const moduleUrl of moduleUrls) {
            try {
              const mod = await import(moduleUrl);
              const sdk = mod.AmazonChimeSDK || mod.default || mod;
              if (sdk) {
                window.AmazonChimeSDK = sdk;
                report('info', 'Chime SDK imported from module: ' + moduleUrl);
                return window.AmazonChimeSDK;
              }
            } catch (_) {
              report('warn', 'Failed importing Chime SDK module: ' + moduleUrl);
            }
          }

          throw new Error('Amazon Chime SDK JS failed to load from all sources.');
        }

        try {
          report('info', 'Initializing Chime media session');
          const ChimeSDK = await loadChimeSdk();
          const meetingResponse = {
            Meeting: {
              MeetingId: config.meetingId,
              ExternalMeetingId: config.meetingId,
              MediaRegion: config.mediaRegion || 'us-east-1',
              MediaPlacement: {
                AudioHostUrl: config.mediaPlacement.audioHostUrl,
                AudioFallbackUrl: config.mediaPlacement.audioFallbackUrl,
                ScreenDataUrl: config.mediaPlacement.screenDataUrl,
                ScreenSharingUrl: config.mediaPlacement.screenSharingUrl,
                ScreenViewingUrl: config.mediaPlacement.screenViewingUrl,
                SignalingUrl: config.mediaPlacement.signalingUrl,
                TurnControlUrl: config.mediaPlacement.turnControlUrl,
                EventIngestionUrl: config.mediaPlacement.eventIngestionUrl || ''
              }
            }
          };

          const attendeeResponse = {
            Attendee: {
              AttendeeId: config.attendeeId,
              ExternalUserId: config.externalUserId,
              JoinToken: config.joinToken
            }
          };

          const logger = new ChimeSDK.ConsoleLogger('CareConnectChime', ChimeSDK.LogLevel.INFO);
          const deviceController = new ChimeSDK.DefaultDeviceController(logger);
          const meetingConfig = new ChimeSDK.MeetingSessionConfiguration(meetingResponse, attendeeResponse);
          const meetingSession = new ChimeSDK.DefaultMeetingSession(meetingConfig, logger, deviceController);
          const audioVideo = meetingSession.audioVideo;
          let localVideoBound = false;
          let localVideoStartAttempts = 0;
          let localVideoRetryTimer = null;
          let videoPublishRecoveryAttempts = 0;
          let localTileId = null;
          let remoteTileId = null;
          let remoteParticipantPresent = false;
          availableVideoInputs = [];
          let activeVideoDeviceId = null;
          let localVideoHealthTimer = null;

          function updateParticipantStatus() {
            if (remoteTileId !== null) {
              setStatus('Connected with participant');
              return;
            }

            if (remoteParticipantPresent) {
              setStatus('Connected with participant (audio only)');
              return;
            }

            setStatus('In call lobby: waiting for the other person to join...');
          }

          function ensureLocalVideoTile() {
            if (!config.videoEnabled || localVideoBound) {
              return;
            }
            if (typeof audioVideo.startLocalVideoTile === 'function') {
              audioVideo.startLocalVideoTile();
              localVideoStartAttempts += 1;
              report('info', 'Requested local video tile start (attempt ' + localVideoStartAttempts + ')');

              if (localVideoStartAttempts < 3) {
                if (localVideoRetryTimer) {
                  clearTimeout(localVideoRetryTimer);
                }
                localVideoRetryTimer = setTimeout(() => {
                  if (!localVideoBound) {
                    ensureLocalVideoTile();
                  }
                }, 1200);
              }
            }
          }

          async function recoverVideoPublish() {
            if (!config.videoEnabled || localVideoBound) {
              return;
            }

            videoPublishRecoveryAttempts += 1;
            report('warn', 'Attempting video publish recovery #' + videoPublishRecoveryAttempts);

            try {
              await switchVideoInput('recovery');
            } catch (videoRecoveryErr) {
              report('warn', 'Video input recovery failed: ' + String(videoRecoveryErr));
            }

            ensureLocalVideoTile();

            if (!localVideoBound && videoPublishRecoveryAttempts < 2) {
              setTimeout(() => {
                if (!localVideoBound) {
                  recoverVideoPublish();
                }
              }, 2000);
            }
          }

          async function selectVideoInput(deviceId) {
            if (!deviceId) return false;
            if (typeof audioVideo.startVideoInput === 'function') {
              await audioVideo.startVideoInput(deviceId);
              activeVideoDeviceId = deviceId;
              return true;
            }
            if (typeof audioVideo.chooseVideoInputDevice === 'function') {
              await audioVideo.chooseVideoInputDevice(deviceId);
              activeVideoDeviceId = deviceId;
              return true;
            }
            return false;
          }

          async function switchVideoInput(reason) {
            if (!config.videoEnabled) return false;

            if (!availableVideoInputs || availableVideoInputs.length === 0) {
              availableVideoInputs = await audioVideo.listVideoInputDevices();
            }

            if (!availableVideoInputs || availableVideoInputs.length === 0) {
              report('warn', 'No video input devices available during ' + reason);
              return false;
            }

            let startIndex = 0;
            if (activeVideoDeviceId) {
              const currentIndex = availableVideoInputs.findIndex(
                (input) => input.deviceId === activeVideoDeviceId,
              );
              if (currentIndex >= 0) {
                startIndex = (currentIndex + 1) % availableVideoInputs.length;
              }
            }

            for (let offset = 0; offset < availableVideoInputs.length; offset += 1) {
              const candidate = availableVideoInputs[(startIndex + offset) % availableVideoInputs.length];
              try {
                const selected = await selectVideoInput(candidate.deviceId);
                if (selected) {
                  report('info', 'Switched video input for ' + reason + ': ' + candidate.deviceId);
                  return true;
                }
              } catch (switchErr) {
                report('warn', 'Video input switch failed for ' + candidate.deviceId + ': ' + String(switchErr));
              }
            }

            return false;
          }

          function scheduleLocalVideoHealthCheck() {
            if (!config.videoEnabled) return;
            if (localVideoHealthTimer) {
              clearTimeout(localVideoHealthTimer);
              localVideoHealthTimer = null;
            }

            localVideoHealthTimer = setTimeout(async () => {
              const isBlackPreview =
                !localVideoBound ||
                localVideo.readyState < 2 ||
                localVideo.videoWidth === 0 ||
                localVideo.videoHeight === 0;

              if (!isBlackPreview) {
                return;
              }

              report('warn', 'Local video health check failed (black/empty preview), trying camera failover');
              const switched = await switchVideoInput('health-check');
              if (switched) {
                localVideoBound = false;
                ensureLocalVideoTile();
              }
            }, 2500);
          }

          async function setLocalAudioMuted(muted) {
            try {
              if (muted) {
                if (typeof audioVideo.realtimeMuteLocalAudio === 'function') {
                  audioVideo.realtimeMuteLocalAudio();
                } else if (typeof audioVideo.muteLocalAudio === 'function') {
                  audioVideo.muteLocalAudio();
                }
              } else {
                if (typeof audioVideo.realtimeUnmuteLocalAudio === 'function') {
                  audioVideo.realtimeUnmuteLocalAudio();
                } else if (typeof audioVideo.unmuteLocalAudio === 'function') {
                  audioVideo.unmuteLocalAudio();
                }
              }
              isAudioMuted = muted;
              updateControlButtons();
              report('info', 'Local audio ' + (muted ? 'muted' : 'unmuted'));
            } catch (audioToggleErr) {
              report('warn', 'Failed to toggle local audio: ' + String(audioToggleErr));
            }
          }

          async function setLocalVideoMuted(muted) {
            try {
              if (muted) {
                if (typeof audioVideo.stopLocalVideoTile === 'function') {
                  audioVideo.stopLocalVideoTile();
                }
                localVideoBound = false;
                isVideoMuted = true;
              } else {
                if (typeof audioVideo.startLocalVideoTile === 'function') {
                  audioVideo.startLocalVideoTile();
                }
                ensureLocalVideoTile();
                isVideoMuted = false;
              }
              updateControlButtons();
              report('info', 'Local video ' + (muted ? 'stopped' : 'started'));
            } catch (videoToggleErr) {
              report('warn', 'Failed to toggle local video: ' + String(videoToggleErr));
            }
          }

          async function bindAndPlayVideo(tileId, element, kind) {
            try {
              audioVideo.bindVideoElement(tileId, element);
            } catch (bindErr) {
              report('warn', 'Failed to bind ' + kind + ' video tile ' + tileId + ': ' + String(bindErr));
              return;
            }

            try {
              element.autoplay = true;
              element.playsInline = true;
              if (kind === 'remote') {
                element.muted = true;
              }

              const playPromise = element.play();
              if (playPromise && typeof playPromise.then === 'function') {
                await playPromise;
              }
            } catch (playErr) {
              report('warn', 'Video element play() failed for ' + kind + ' tile ' + tileId + ': ' + String(playErr));
            }
          }

          audioVideo.bindAudioElement(remoteAudio);
          updateControlButtons();

          if (micBtn) {
            micBtn.addEventListener('click', () => {
              setLocalAudioMuted(!isAudioMuted);
            });
          }
          if (camBtn) {
            camBtn.addEventListener('click', () => {
              setLocalVideoMuted(!isVideoMuted);
            });
          }
          if (switchCamBtn) {
            switchCamBtn.addEventListener('click', async () => {
              try {
                availableVideoInputs = await audioVideo.listVideoInputDevices();
                const switched = await switchVideoInput('manual-switch');
                if (switched) {
                  localVideoBound = false;
                  ensureLocalVideoTile();
                  report('info', 'Camera switched by user');
                } else {
                  report('warn', 'Unable to switch camera: no alternative device available');
                }
              } catch (switchErr) {
                report('warn', 'Manual camera switch failed: ' + String(switchErr));
              } finally {
                updateControlButtons();
              }
            });
          }
          if (endBtn) {
            endBtn.addEventListener('click', () => {
              stopAutoSentimentCapture();
              emitAction('end-call-request');
            });
          }

          audioVideo.addObserver({
            audioVideoDidStart: () => {
              updateParticipantStatus();
              ensureLocalVideoTile();
              report('info', 'audioVideoDidStart');

              setTimeout(() => {
                if (!localVideoBound) {
                  recoverVideoPublish();
                }
              }, 1800);
            },
            audioVideoDidStop: (sessionStatus) => {
              setStatus('Disconnected');
              stopAutoSentimentCapture();
              if (localVideoRetryTimer) {
                clearTimeout(localVideoRetryTimer);
                localVideoRetryTimer = null;
              }
              if (localVideoHealthTimer) {
                clearTimeout(localVideoHealthTimer);
                localVideoHealthTimer = null;
              }
              report('warn', 'audioVideoDidStop: ' + (sessionStatus ? sessionStatus.statusCode() : 'unknown'));
            },
            videoTileDidUpdate: (tileState) => {
              if (!tileState.tileId || tileState.isContent) return;

              const tileAttendeeId = tileState.boundAttendeeId || tileState.attendeeId || '';
              const isLocalByAttendee = !!tileAttendeeId && tileAttendeeId === config.attendeeId;
              const isLocalTile = !!tileState.localTile || isLocalByAttendee;

              report(
                'info',
                'Tile update: id=' + tileState.tileId +
                ', localFlag=' + String(!!tileState.localTile) +
                ', attendee=' + (tileAttendeeId || 'unknown') +
                ', classifiedLocal=' + String(isLocalTile),
              );

              if (isLocalTile) {
                localVideoBound = true;
                if (localVideoRetryTimer) {
                  clearTimeout(localVideoRetryTimer);
                  localVideoRetryTimer = null;
                }
                if (localTileId !== tileState.tileId) {
                  localTileId = tileState.tileId;
                  bindAndPlayVideo(tileState.tileId, localVideo, 'local');
                }
                scheduleLocalVideoHealthCheck();
                report('info', 'Local video tile bound');
              } else {
                remoteParticipantPresent = true;
                if (remoteTileId !== tileState.tileId) {
                  remoteTileId = tileState.tileId;
                  bindAndPlayVideo(tileState.tileId, remoteVideo, 'remote');
                }
                updateParticipantStatus();
                report('info', 'Remote video tile bound');
              }
            },
            videoTileWasRemoved: (tileId) => {
              if (tileId === localTileId) {
                localTileId = null;
                localVideoBound = false;
              }
              if (tileId === remoteTileId) {
                remoteTileId = null;
                updateParticipantStatus();
              }
            }
          });

          if (typeof audioVideo.realtimeSubscribeToAttendeeIdPresence === 'function') {
            audioVideo.realtimeSubscribeToAttendeeIdPresence((attendeeId, present, externalUserId, dropped) => {
              if (!attendeeId || attendeeId === config.attendeeId) {
                return;
              }

              remoteParticipantPresent = !!present;
              if (!remoteParticipantPresent) {
                remoteTileId = null;
              }

              updateParticipantStatus();
              report(
                'info',
                'Presence update: attendee=' + attendeeId +
                  ', present=' + String(!!present) +
                  ', dropped=' + String(!!dropped) +
                  ', externalUserId=' + String(externalUserId || ''),
              );
            });
          }

          if (config.audioEnabled) {
            const audioInputs = await audioVideo.listAudioInputDevices();
            report('info', 'Audio input devices: ' + audioInputs.length);
            if (audioInputs.length > 0) {
              let audioSelected = false;
              for (const input of audioInputs) {
                try {
                  if (typeof audioVideo.startAudioInput === 'function') {
                    await audioVideo.startAudioInput(input.deviceId);
                    audioSelected = true;
                    break;
                  } else if (typeof audioVideo.chooseAudioInputDevice === 'function') {
                    await audioVideo.chooseAudioInputDevice(input.deviceId);
                    audioSelected = true;
                    break;
                  } else {
                    report('warn', 'No supported audio input method found on audioVideo facade');
                    break;
                  }
                } catch (audioErr) {
                  report('warn', 'Audio device failed: ' + input.deviceId + ' (' + String(audioErr) + ')');
                }
              }

              if (!audioSelected) {
                report('warn', 'No audio input device could be started.');
              }
            }
          }

          if (config.videoEnabled) {
            availableVideoInputs = await audioVideo.listVideoInputDevices();
            report('info', 'Video input devices: ' + availableVideoInputs.length);
            if (availableVideoInputs.length > 0) {
              let videoSelected = false;
              for (const input of availableVideoInputs) {
                try {
                  const selected = await selectVideoInput(input.deviceId);
                  if (selected) {
                    videoSelected = true;
                    break;
                  }
                } catch (videoErr) {
                  report('warn', 'Video device failed: ' + input.deviceId + ' (' + String(videoErr) + ')');
                }
              }

              if (!videoSelected) {
                report('warn', 'No video input device could be started.');
              }
            } else {
              report('warn', 'Video is enabled but no video input devices were listed.');
            }
          }

          audioVideo.start();
          report('info', 'audioVideo.start() invoked');
          await startAutoSentimentCapture();

          if (config.videoEnabled) {
            setTimeout(() => {
              if (!localVideoBound) {
                ensureLocalVideoTile();
              }
            }, 900);
          }
        } catch (error) {
          stopAutoSentimentCapture();
          const msg = (error && error.message) ? error.message : String(error);
          setStatus('Media error: ' + msg);
          report('error', 'Media init failed: ' + msg);
          console.error('[CareConnect] Chime media init failed', error);
        }
      })();
    </script>
  </body>
</html>
''';
  }
}
