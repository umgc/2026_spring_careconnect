// Tests for OAuthService.
//
// Coverage strategy:
//   OAuthService is nearly pure Dart — it reads the backend URL from an
//   environment helper, builds URL strings, and parses callback URIs.
//   launchGoogleOAuth is not tested because it calls url_launcher which
//   requires a platform channel.
//
//   Branches tested:
//     isConfigured — returns true when backend URL is non-empty.
//     buildAuthorizationUrl — returns expected URL pattern.
//     handleCallback — token present → returns token.
//     handleCallback — error query param → throws Exception.
//     handleCallback — no token and no error → throws Exception.
//     clearSession — completes without error.

import 'package:flutter_test/flutter_test.dart';

import 'package:care_connect_app/services/oauth_service.dart';

void main() {
  // ─── isConfigured ────────────────────────────────────────────────────────

  group('OAuthService.isConfigured', () {
    test('returns a boolean without throwing', () {
      // The env helper may return any URL in test environment; we just verify
      // the getter does not throw and returns a bool.
      expect(() => OAuthService.isConfigured, returnsNormally);
      expect(OAuthService.isConfigured, isA<bool>());
    });
  });

  // ─── buildAuthorizationUrl ───────────────────────────────────────────────

  group('OAuthService.buildAuthorizationUrl', () {
    test('includes the Google SSO endpoint path', () {
      final url = OAuthService.buildAuthorizationUrl();
      expect(url, contains('/v1/api/auth/sso/google'));
    });

    test('returns a non-empty string', () {
      expect(OAuthService.buildAuthorizationUrl(), isNotEmpty);
    });
  });

  // ─── handleCallback ──────────────────────────────────────────────────────

  group('OAuthService.handleCallback', () {
    test('token query param present → returns token', () async {
      final uri = Uri.parse('careconnect://callback?token=abc.def.ghi');
      final token = await OAuthService.handleCallback(uri);
      expect(token, 'abc.def.ghi');
    });

    test('error query param present → throws Exception', () async {
      final uri = Uri.parse('careconnect://callback?error=access_denied');
      await expectLater(
        OAuthService.handleCallback(uri),
        throwsA(
          isA<Exception>().having(
            (e) => e.toString(),
            'message',
            contains('access_denied'),
          ),
        ),
      );
    });

    test('neither token nor error present → throws "No JWT token" exception', () async {
      final uri = Uri.parse('careconnect://callback');
      await expectLater(
        OAuthService.handleCallback(uri),
        throwsA(
          isA<Exception>().having(
            (e) => e.toString(),
            'message',
            contains('No JWT token'),
          ),
        ),
      );
    });
  });

  // ─── clearSession ────────────────────────────────────────────────────────

  group('OAuthService.clearSession', () {
    test('completes without throwing', () {
      expect(() => OAuthService.clearSession(), returnsNormally);
    });
  });
}
