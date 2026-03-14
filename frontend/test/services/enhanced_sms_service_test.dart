// Tests for EnhancedSMSService.
//
// Coverage strategy:
//   EnhancedSMSService.sendSMSViaBackend and sendEmergencySMS use http.post
//   (top-level), interceptable via http.runWithClient + MockClient.
//   sendSMSFallback uses url_launcher (platform channel) — skipped.
//   sendSMS uses kIsWeb branching and BuildContext — skipped.
//
//   Branches tested:
//     sendSMSViaBackend — 200 success:true → true; 200 success:false → false;
//                         non-200 → false; exception → false.
//     sendSMSViaBackend — authToken present → Authorization header sent.
//     sendEmergencySMS — 200 success:true → true; non-200 → false; exception → false.
//     sendEmergencySMS — location defaults to 'Unknown location' when omitted.

import 'dart:convert';

import 'package:flutter_test/flutter_test.dart';
import 'package:http/http.dart' as http;
import 'package:http/testing.dart';

import 'package:care_connect_app/services/enhanced_sms_service.dart';

void main() {
  // ─── sendSMSViaBackend ────────────────────────────────────────────────────

  group('EnhancedSMSService.sendSMSViaBackend', () {
    test('200 with success:true → returns true', () async {
      final body = jsonEncode({'success': true, 'message': 'sent'});
      final result = await http.runWithClient(
        () => EnhancedSMSService.sendSMSViaBackend(
          toPhone: '+15555555555',
          message: 'Test',
          fromUserId: 'u1',
          fromUserName: 'Alice',
        ),
        () => MockClient((_) async => http.Response(body, 200)),
      );
      expect(result, isTrue);
    });

    test('200 with success:false → returns false', () async {
      final body = jsonEncode({'success': false});
      final result = await http.runWithClient(
        () => EnhancedSMSService.sendSMSViaBackend(
          toPhone: '+15555555555',
          message: 'Test',
          fromUserId: 'u1',
          fromUserName: 'Alice',
        ),
        () => MockClient((_) async => http.Response(body, 200)),
      );
      expect(result, isFalse);
    });

    test('200 with no success field → returns false (null ?? false)', () async {
      final body = jsonEncode({'message': 'sent'});
      final result = await http.runWithClient(
        () => EnhancedSMSService.sendSMSViaBackend(
          toPhone: '+15555555555',
          message: 'Test',
          fromUserId: 'u1',
          fromUserName: 'Alice',
        ),
        () => MockClient((_) async => http.Response(body, 200)),
      );
      expect(result, isFalse);
    });

    test('non-200 → returns false', () async {
      final result = await http.runWithClient(
        () => EnhancedSMSService.sendSMSViaBackend(
          toPhone: '+15555555555',
          message: 'Test',
          fromUserId: 'u1',
          fromUserName: 'Alice',
        ),
        () => MockClient((_) async => http.Response('', 500)),
      );
      expect(result, isFalse);
    });

    test('exception → returns false', () async {
      final result = await http.runWithClient(
        () => EnhancedSMSService.sendSMSViaBackend(
          toPhone: '+15555555555',
          message: 'Test',
          fromUserId: 'u1',
          fromUserName: 'Alice',
        ),
        () => MockClient((_) async => throw Exception('network')),
      );
      expect(result, isFalse);
    });

    test('authToken present → Authorization header included', () async {
      String? capturedAuth;
      final body = jsonEncode({'success': true});
      await http.runWithClient(
        () => EnhancedSMSService.sendSMSViaBackend(
          toPhone: '+15555555555',
          message: 'Test',
          fromUserId: 'u1',
          fromUserName: 'Alice',
          authToken: 'my-token',
        ),
        () => MockClient((req) async {
          capturedAuth = req.headers['Authorization'];
          return http.Response(body, 200);
        }),
      );
      expect(capturedAuth, 'Bearer my-token');
    });

    test('no authToken → no Authorization header', () async {
      String? capturedAuth;
      final body = jsonEncode({'success': true});
      await http.runWithClient(
        () => EnhancedSMSService.sendSMSViaBackend(
          toPhone: '+15555555555',
          message: 'Test',
          fromUserId: 'u1',
          fromUserName: 'Alice',
        ),
        () => MockClient((req) async {
          capturedAuth = req.headers['Authorization'];
          return http.Response(body, 200);
        }),
      );
      expect(capturedAuth, isNull);
    });
  });

  // ─── sendEmergencySMS ─────────────────────────────────────────────────────

  group('EnhancedSMSService.sendEmergencySMS', () {
    test('200 with success:true → returns true', () async {
      final body = jsonEncode({'success': true, 'sentCount': 3});
      final result = await http.runWithClient(
        () => EnhancedSMSService.sendEmergencySMS(
          patientId: 'p1',
          message: 'Emergency!',
        ),
        () => MockClient((_) async => http.Response(body, 200)),
      );
      expect(result, isTrue);
    });

    test('location parameter is included in request body', () async {
      Map<String, dynamic>? capturedBody;
      final body = jsonEncode({'success': true, 'sentCount': 1});
      await http.runWithClient(
        () => EnhancedSMSService.sendEmergencySMS(
          patientId: 'p1',
          message: 'Help!',
          location: '123 Main St',
        ),
        () => MockClient((req) async {
          capturedBody = jsonDecode(req.body) as Map<String, dynamic>;
          return http.Response(body, 200);
        }),
      );
      expect(capturedBody?['location'], '123 Main St');
    });

    test('omitted location defaults to Unknown location in request', () async {
      Map<String, dynamic>? capturedBody;
      final body = jsonEncode({'success': true, 'sentCount': 1});
      await http.runWithClient(
        () => EnhancedSMSService.sendEmergencySMS(
          patientId: 'p1',
          message: 'Help!',
        ),
        () => MockClient((req) async {
          capturedBody = jsonDecode(req.body) as Map<String, dynamic>;
          return http.Response(body, 200);
        }),
      );
      expect(capturedBody?['location'], 'Unknown location');
    });

    test('non-200 → returns false', () async {
      final result = await http.runWithClient(
        () => EnhancedSMSService.sendEmergencySMS(
          patientId: 'p1',
          message: 'Emergency!',
        ),
        () => MockClient((_) async => http.Response('', 503)),
      );
      expect(result, isFalse);
    });

    test('exception → returns false', () async {
      final result = await http.runWithClient(
        () => EnhancedSMSService.sendEmergencySMS(
          patientId: 'p1',
          message: 'Emergency!',
        ),
        () => MockClient((_) async => throw Exception('network')),
      );
      expect(result, isFalse);
    });
  });
}
