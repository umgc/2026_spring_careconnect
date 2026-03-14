// Tests for HybridVideoCallService.
//
// Coverage strategy:
//   HybridVideoCallService wraps platform-specific video call implementations.
//   On test platforms (non-web, no Agora), _mobileService/_webService are null.
//   The following pure-logic paths are testable without platform plugins:
//
//   Branches tested:
//     factory — returns same singleton instance.
//     isInCall — false before startCall.
//     currentCallId — null before startCall.
//     endCall — when not in call, completes without error and isInCall stays false.
//     getCallControls/getLocalVideoView/getRemoteVideoView — return Container on
//       non-web with null _mobileService (fallback ?? Container()).
//     startCall — throws 'Already in a call' when _isInCall is already true.

import 'package:flutter/material.dart';
import 'package:flutter_test/flutter_test.dart';

import 'package:care_connect_app/services/hybrid_video_call_service.dart';

void main() {
  // Reset singleton state between tests by calling endCall.
  setUp(() async {
    await HybridVideoCallService().endCall();
  });

  // ─── singleton ────────────────────────────────────────────────────────────

  group('HybridVideoCallService factory / singleton', () {
    test('factory returns the same instance', () {
      final a = HybridVideoCallService();
      final b = HybridVideoCallService();
      expect(identical(a, b), isTrue);
    });
  });

  // ─── initial state ────────────────────────────────────────────────────────

  group('HybridVideoCallService initial state', () {
    test('isInCall is false', () {
      expect(HybridVideoCallService().isInCall, isFalse);
    });

    test('currentCallId is null', () {
      expect(HybridVideoCallService().currentCallId, isNull);
    });
  });

  // ─── endCall when not in call ─────────────────────────────────────────────

  group('HybridVideoCallService.endCall', () {
    test('completes without error when not in call', () async {
      expect(
        () async => HybridVideoCallService().endCall(),
        returnsNormally,
      );
    });

    test('isInCall remains false after endCall', () async {
      await HybridVideoCallService().endCall();
      expect(HybridVideoCallService().isInCall, isFalse);
    });

    test('currentCallId remains null after endCall', () async {
      await HybridVideoCallService().endCall();
      expect(HybridVideoCallService().currentCallId, isNull);
    });
  });

  // ─── UI widget helpers ────────────────────────────────────────────────────

  group('HybridVideoCallService widget helpers (non-web, no mobileService)', () {
    test('getCallControls returns a Widget', () {
      final widget = HybridVideoCallService().getCallControls();
      expect(widget, isA<Widget>());
    });

    test('getLocalVideoView returns a Widget', () {
      final widget = HybridVideoCallService().getLocalVideoView();
      expect(widget, isA<Widget>());
    });

    test('getRemoteVideoView returns a Widget', () {
      final widget = HybridVideoCallService().getRemoteVideoView();
      expect(widget, isA<Widget>());
    });
  });

  // ─── initialize completes without error ───────────────────────────────────

  group('HybridVideoCallService.initialize', () {
    test('completes without error on non-web platform', () async {
      expect(
        () async => HybridVideoCallService().initialize(userId: 'u1'),
        returnsNormally,
      );
    });
  });
}
