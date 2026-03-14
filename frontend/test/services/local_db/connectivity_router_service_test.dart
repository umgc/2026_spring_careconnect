// Tests for ConnectivityRouterService.
//
// Coverage strategy:
//   ConnectivityRouterService is a pure-Dart class with a single injected
//   dependency: an `IsOnlineFn` function.  No platform channels or network
//   calls are involved, so every branch is exercised directly.
//
//   Branches tested:
//     1. isOnline=true, no fallback  → online handler runs, result returned.
//     2. isOnline=false              → offline handler runs, result returned.
//     3. isOnline=true, fallbackToOfflineOnOnlineError=true, online throws
//                                   → offline handler runs as fallback.
//     4. isOnline=true, fallbackToOfflineOnOnlineError=true, online succeeds
//                                   → online result returned (no fallback).
//     5. isOnline=true, fallbackToOfflineOnOnlineError=false, online throws
//                                   → exception propagates to caller.

import 'package:flutter_test/flutter_test.dart';

import 'package:care_connect_app/services/local_db/connectivity_router_service.dart';

ConnectivityRouterService _online() =>
    ConnectivityRouterService(isOnline: () async => true);

ConnectivityRouterService _offline() =>
    ConnectivityRouterService(isOnline: () async => false);

void main() {
  // ─── Basic routing ────────────────────────────────────────────────────────

  group('ConnectivityRouterService — basic routing', () {
    test('calls online handler and returns its value when online', () async {
      // Verifies the primary happy-path: an online device executes the online
      // handler and the return value is correctly threaded back to the caller.
      final router = _online();
      final result = await router.route<String>(
        online: () async => 'online-result',
        offline: () async => 'offline-result',
      );
      expect(result, 'online-result');
    });

    test('calls offline handler and returns its value when offline', () async {
      // Verifies the offline fallback: a device with no connectivity executes
      // the offline handler instead.
      final router = _offline();
      final result = await router.route<String>(
        online: () async => 'online-result',
        offline: () async => 'offline-result',
      );
      expect(result, 'offline-result');
    });

    test('does NOT call offline handler when online and no fallback', () async {
      // Verifies that the offline handler is bypassed entirely on a connected
      // device (without error fallback enabled).
      var offlineCalled = false;
      final router = _online();
      await router.route<void>(
        online: () async {},
        offline: () async {
          offlineCalled = true;
        },
      );
      expect(offlineCalled, isFalse);
    });

    test('does NOT call online handler when offline', () async {
      // Verifies that the online handler is bypassed when there is no
      // connectivity.
      var onlineCalled = false;
      final router = _offline();
      await router.route<void>(
        online: () async {
          onlineCalled = true;
        },
        offline: () async {},
      );
      expect(onlineCalled, isFalse);
    });

    test('returns correct integer value from online handler', () async {
      // Verifies type inference: route<T> works for non-String types.
      final router = _online();
      final result = await router.route<int>(
        online: () async => 42,
        offline: () async => 0,
      );
      expect(result, 42);
    });

    test('returns correct integer value from offline handler', () async {
      final router = _offline();
      final result = await router.route<int>(
        online: () async => 42,
        offline: () async => 99,
      );
      expect(result, 99);
    });

    test('route<void> completes without error when online', () async {
      final router = _online();
      await expectLater(
        router.route<void>(
          online: () async {},
          offline: () async {},
        ),
        completes,
      );
    });

    test('route<void> completes without error when offline', () async {
      final router = _offline();
      await expectLater(
        router.route<void>(
          online: () async {},
          offline: () async {},
        ),
        completes,
      );
    });
  });

  // ─── fallbackToOfflineOnOnlineError ───────────────────────────────────────

  group('ConnectivityRouterService — fallbackToOfflineOnOnlineError', () {
    test(
        'calls offline handler when online throws and fallback is enabled',
        () async {
      // Verifies the resilience path: if the online operation fails (e.g. a
      // transient network error), the offline handler is used as a fallback so
      // the app stays functional.
      final router = _online();
      final result = await router.route<String>(
        online: () async => throw Exception('network error'),
        offline: () async => 'offline-fallback',
        fallbackToOfflineOnOnlineError: true,
      );
      expect(result, 'offline-fallback');
    });

    test(
        'returns online result when fallback enabled but online succeeds',
        () async {
      // Verifies that the fallback is only triggered on error; a successful
      // online operation must not invoke the offline handler.
      final router = _online();
      final result = await router.route<String>(
        online: () async => 'online-success',
        offline: () async => 'offline-fallback',
        fallbackToOfflineOnOnlineError: true,
      );
      expect(result, 'online-success');
    });

    test(
        'propagates exception when online throws and fallback is disabled',
        () async {
      // Verifies the strict path: without the fallback option, an online
      // failure bubbles up to the caller so it can be handled explicitly.
      final router = _online();
      await expectLater(
        router.route<String>(
          online: () async => throw Exception('hard failure'),
          offline: () async => 'offline-fallback',
        ),
        throwsA(isA<Exception>()),
      );
    });

    test(
        'offline handler NOT called when fallback disabled and online throws',
        () async {
      // Verifies that the offline handler is not silently invoked when the
      // exception is supposed to propagate.
      var offlineCalled = false;
      final router = _online();
      try {
        await router.route<void>(
          online: () async => throw Exception('fail'),
          offline: () async {
            offlineCalled = true;
          },
        );
      } catch (_) {}
      expect(offlineCalled, isFalse);
    });

    test('returns offline value of correct type after fallback', () async {
      // Verifies the return type is maintained through the fallback path.
      final router = _online();
      final result = await router.route<int>(
        online: () async => throw Exception('fail'),
        offline: () async => 77,
        fallbackToOfflineOnOnlineError: true,
      );
      expect(result, 77);
    });
  });

  // ─── isOnline function behaviour ─────────────────────────────────────────

  group('ConnectivityRouterService — isOnline function behaviour', () {
    test('isOnline is called exactly once per route() invocation', () async {
      // Verifies the router does not spam the connectivity check.
      var callCount = 0;
      final router = ConnectivityRouterService(
        isOnline: () async {
          callCount++;
          return true;
        },
      );
      await router.route<void>(
        online: () async {},
        offline: () async {},
      );
      expect(callCount, 1);
    });

    test('isOnline returning false routes to offline even if no exception',
        () async {
      // Verifies routing is based on isOnline() return value, not on whether
      // the online handler would throw.
      final router = _offline();
      var onlineCalled = false;
      await router.route<void>(
        online: () async {
          onlineCalled = true;
        },
        offline: () async {},
      );
      expect(onlineCalled, isFalse);
    });
  });
}
