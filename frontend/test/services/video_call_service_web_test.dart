// Tests for VideoCallService (video_call_service_web.dart).
//
// Coverage strategy:
//   VideoCallService is platform-agnostic (no native plugins).
//   A fresh instance is created per test using the public constructor.
//   Streams are listened to in order to verify state-change events.
//
//   Branches tested:
//     initializeService — static method returns true.
//     checkUserAvailability — static method returns true.
//     initial state — isCallActive false, video/audio enabled, no callId.
//     initialize — sets currentUserId.
//     startCallInternal — sets isCallActive, callId, userIds, emits true on stream.
//     answerCall — sets isCallActive, callId, userIds, emits true on stream.
//     toggleVideo — flips isVideoEnabled, emits event.
//     toggleAudio — flips isAudioEnabled, emits event.
//     toggleVideoWithParam — sets isVideoEnabled to given value.
//     toggleAudioWithParam — sets isAudioEnabled to given value.
//     endCall — resets all fields to initial state, emits false on call state stream.

import 'package:flutter/material.dart';
import 'package:flutter_test/flutter_test.dart';

import 'package:care_connect_app/services/video_call_service_web.dart';

void main() {
  setUpAll(() {
    TestWidgetsFlutterBinding.ensureInitialized();
  });
  // ─── static helpers ───────────────────────────────────────────────────────

  group('VideoCallService static methods', () {
    test('initializeService returns true', () async {
      final result = await VideoCallService.initializeService();
      expect(result, isTrue);
    });

    test('checkUserAvailability returns true for any userId', () async {
      expect(await VideoCallService.checkUserAvailability('u1'), isTrue);
      expect(await VideoCallService.checkUserAvailability(''), isTrue);
    });
  });

  // ─── initial state ────────────────────────────────────────────────────────

  group('VideoCallService initial state', () {
    test('fresh instance has expected defaults', () {
      final svc = VideoCallService();
      expect(svc.isCallActive, isFalse);
      expect(svc.isVideoEnabled, isTrue);
      expect(svc.isAudioEnabled, isTrue);
      expect(svc.currentCallId, isNull);
      expect(svc.currentUserId, isNull);
      expect(svc.remoteUserId, isNull);
    });
  });

  // ─── initialize ───────────────────────────────────────────────────────────

  group('VideoCallService.initialize', () {
    test('sets currentUserId', () async {
      final svc = VideoCallService();
      await svc.initialize(userId: 'user42');
      expect(svc.currentUserId, 'user42');
    });
  });

  // ─── startCallInternal ────────────────────────────────────────────────────

  group('VideoCallService.startCallInternal', () {
    test('returns true and sets call state', () async {
      final svc = VideoCallService();
      final result = await svc.startCallInternal(
        'call-001',
        'user1',
        'user2',
        videoEnabled: true,
        audioEnabled: true,
      );
      expect(result, isTrue);
      expect(svc.isCallActive, isTrue);
      expect(svc.currentCallId, 'call-001');
      expect(svc.currentUserId, 'user1');
      expect(svc.remoteUserId, 'user2');
      expect(svc.isVideoEnabled, isTrue);
      expect(svc.isAudioEnabled, isTrue);
    });

    test('video disabled is honoured', () async {
      final svc = VideoCallService();
      await svc.startCallInternal('c', 'u1', 'u2', videoEnabled: false);
      expect(svc.isVideoEnabled, isFalse);
    });

    test('emits true on callStateStream', () async {
      final svc = VideoCallService();
      final future = svc.callStateStream.first;
      await svc.startCallInternal('c', 'u1', 'u2');
      expect(await future, isTrue);
    });
  });

  // ─── answerCall ───────────────────────────────────────────────────────────

  group('VideoCallService.answerCall', () {
    test('returns true and sets call state', () async {
      final svc = VideoCallService();
      final result = await svc.answerCall('call-002', 'user2', 'user1');
      expect(result, isTrue);
      expect(svc.isCallActive, isTrue);
      expect(svc.currentCallId, 'call-002');
    });

    test('emits true on callStateStream', () async {
      final svc = VideoCallService();
      final future = svc.callStateStream.first;
      await svc.answerCall('call-002', 'user2', 'user1');
      expect(await future, isTrue);
    });
  });

  // ─── toggleVideo ─────────────────────────────────────────────────────────

  group('VideoCallService.toggleVideo', () {
    test('flips isVideoEnabled from true to false', () async {
      final svc = VideoCallService();
      expect(svc.isVideoEnabled, isTrue);
      await svc.toggleVideo();
      expect(svc.isVideoEnabled, isFalse);
    });

    test('flips isVideoEnabled back to true on second call', () async {
      final svc = VideoCallService();
      await svc.toggleVideo();
      await svc.toggleVideo();
      expect(svc.isVideoEnabled, isTrue);
    });

    test('emits video-toggled event on callEventStream', () async {
      final svc = VideoCallService();
      final future = svc.callEventStream.first;
      await svc.toggleVideo();
      final event = await future;
      expect(event['type'], 'video-toggled');
      expect(event['enabled'], isFalse);
    });
  });

  // ─── toggleAudio ─────────────────────────────────────────────────────────

  group('VideoCallService.toggleAudio', () {
    test('flips isAudioEnabled from true to false', () async {
      final svc = VideoCallService();
      await svc.toggleAudio();
      expect(svc.isAudioEnabled, isFalse);
    });

    test('emits audio-toggled event on callEventStream', () async {
      final svc = VideoCallService();
      final future = svc.callEventStream.first;
      await svc.toggleAudio();
      final event = await future;
      expect(event['type'], 'audio-toggled');
    });
  });

  // ─── toggleVideoWithParam / toggleAudioWithParam ──────────────────────────

  group('VideoCallService.toggleVideoWithParam', () {
    test('sets isVideoEnabled to the given value', () async {
      final svc = VideoCallService();
      await svc.toggleVideoWithParam(false);
      expect(svc.isVideoEnabled, isFalse);
      await svc.toggleVideoWithParam(true);
      expect(svc.isVideoEnabled, isTrue);
    });
  });

  group('VideoCallService.toggleAudioWithParam', () {
    test('sets isAudioEnabled to the given value', () async {
      final svc = VideoCallService();
      await svc.toggleAudioWithParam(false);
      expect(svc.isAudioEnabled, isFalse);
      await svc.toggleAudioWithParam(true);
      expect(svc.isAudioEnabled, isTrue);
    });
  });

  // ─── endCall ─────────────────────────────────────────────────────────────

  group('VideoCallService.endCall', () {
    test('resets all call state to defaults', () async {
      final svc = VideoCallService();
      await svc.startCallInternal('c1', 'u1', 'u2');
      await svc.endCall();
      expect(svc.isCallActive, isFalse);
      expect(svc.currentCallId, isNull);
      expect(svc.currentUserId, isNull);
      expect(svc.remoteUserId, isNull);
      expect(svc.isVideoEnabled, isTrue);
      expect(svc.isAudioEnabled, isTrue);
    });

    test('emits false on callStateStream', () async {
      final svc = VideoCallService();
      await svc.startCallInternal('c1', 'u1', 'u2');
      final future = svc.callStateStream.first;
      await svc.endCall();
      expect(await future, isFalse);
    });

    test('emits call-ended event on callEventStream', () async {
      final svc = VideoCallService();
      await svc.startCallInternal('c1', 'u1', 'u2');
      final future = svc.callEventStream.first;
      await svc.endCall();
      final event = await future;
      expect(event['type'], 'call-ended');
    });
  });

  // ─── initiateCall static ─────────────────────────────────────────────────

  group('VideoCallService.initiateCall', () {
    test('returns map with success:true and callId', () async {
      final result = await VideoCallService.initiateCall(
        callerId: 'c1',
        recipientId: 'r1',
        isVideoCall: true,
      );
      expect(result['success'], isTrue);
      expect(result['callId'], isA<String>());
      expect(result['callerId'], 'c1');
      expect(result['recipientId'], 'r1');
      expect(result['isVideoCall'], isTrue);
    });
  });

  // ─── getLocalVideoView / getRemoteVideoView / getCallControls ─────────────

  group('VideoCallService widget view helpers', () {
    test('getLocalVideoView returns a Widget', () {
      final svc = VideoCallService();
      expect(svc.getLocalVideoView(), isA<Widget>());
    });

    test('getRemoteVideoView returns a Widget', () {
      final svc = VideoCallService();
      expect(svc.getRemoteVideoView(), isA<Widget>());
    });

    test('getCallControls returns a Widget', () {
      final svc = VideoCallService();
      expect(svc.getCallControls(), isA<Widget>());
    });
  });

  // ─── startCall ────────────────────────────────────────────────────────────

  group('VideoCallService.startCall', () {
    test('returns a Widget and sets isCallActive', () async {
      final svc = VideoCallService();
      await svc.initialize(userId: 'caller1');
      final widget = await svc.startCall(
        callId: 'sc-001',
        recipientId: 'r1',
      );
      expect(widget, isA<Widget>());
      expect(svc.isCallActive, isTrue);
      expect(svc.currentCallId, 'sc-001');
    });

    test('audio disabled flag is honoured', () async {
      final svc = VideoCallService();
      await svc.initialize(userId: 'caller2');
      await svc.startCall(
        callId: 'sc-002',
        recipientId: 'r2',
        isAudioEnabled: false,
      );
      expect(svc.isAudioEnabled, isFalse);
    });
  });

  // ─── joinCallInstance ─────────────────────────────────────────────────────

  group('VideoCallService.joinCallInstance', () {
    test('returns a Widget and sets isCallActive', () async {
      final svc = VideoCallService();
      await svc.initialize(userId: 'joiner1');
      final widget = await svc.joinCallInstance(callId: 'jc-001');
      expect(widget, isA<Widget>());
      expect(svc.isCallActive, isTrue);
    });
  });

  // ─── static joinCall / endCallStatic ─────────────────────────────────────

  group('VideoCallService static joinCall / endCallStatic', () {
    test('joinCall returns true', () async {
      final result = await VideoCallService.joinCall('jc-static', 'user9');
      expect(result, isTrue);
    });

    test('endCallStatic completes without error', () async {
      expect(
        () async => VideoCallService.endCallStatic('jc-static', 'user9'),
        returnsNormally,
      );
    });
  });

  // ─── dispose ─────────────────────────────────────────────────────────────

  group('VideoCallService.dispose', () {
    test('completes without error', () async {
      final svc = VideoCallService();
      expect(() async => svc.dispose(), returnsNormally);
    });
  });
}
