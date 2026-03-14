// Tests for CheckinService.
//
// Coverage strategy:
//   CheckinService has two public static methods — addCheckin() and
//   getCheckinCount() — that each issue a single HTTP request.  No auth
//   headers or storage are involved, so the tests only need an http.MockClient
//   supplied via http.runWithClient().
//
//   Every status-code branch is exercised, the outgoing request shape is
//   asserted, and the body/query-parameter mappings are verified.

import 'dart:convert';

import 'package:flutter_test/flutter_test.dart';
import 'package:http/http.dart' as http;
import 'package:http/testing.dart';

import 'package:care_connect_app/services/checkin_service.dart';

void main() {
  // ─── addCheckin ───────────────────────────────────────────────────────────

  group('CheckinService.addCheckin()', () {
    test('returns true when server responds with 201 Created', () async {
      // Verifies the primary success path: 201 is the normal "created" status.
      final result = await http.runWithClient(
        () => CheckinService.addCheckin('patient-1', 'caregiver-1'),
        () => MockClient((_) async => http.Response('', 201)),
      );
      expect(result, isTrue);
    });

    test('returns true when server responds with 200 OK', () async {
      // Some backends return 200 instead of 201 on creation; both are accepted.
      final result = await http.runWithClient(
        () => CheckinService.addCheckin('patient-2', 'caregiver-2'),
        () => MockClient((_) async => http.Response('', 200)),
      );
      expect(result, isTrue);
    });

    test('returns false when server responds with 400', () async {
      // Any status other than 200/201 must be treated as failure.
      final result = await http.runWithClient(
        () => CheckinService.addCheckin('patient-3', 'caregiver-3'),
        () => MockClient((_) async => http.Response('Bad Request', 400)),
      );
      expect(result, isFalse);
    });

    test('returns false when server responds with 500', () async {
      // Server-side errors must propagate as a false result.
      final result = await http.runWithClient(
        () => CheckinService.addCheckin('patient-4', 'caregiver-4'),
        () => MockClient((_) async => http.Response('Server Error', 500)),
      );
      expect(result, isFalse);
    });

    test('returns false when server responds with 404', () async {
      // 404 is not 200 or 201, so it must return false.
      final result = await http.runWithClient(
        () => CheckinService.addCheckin('patient-5', 'caregiver-5'),
        () => MockClient((_) async => http.Response('Not Found', 404)),
      );
      expect(result, isFalse);
    });

    test('sends a POST request to the checkins endpoint', () async {
      // Verifies the request uses the correct HTTP method and path.
      http.Request? captured;
      await http.runWithClient(
        () => CheckinService.addCheckin('patient-6', 'caregiver-6'),
        () => MockClient((req) async {
          captured = req;
          return http.Response('', 201);
        }),
      );
      expect(captured, isNotNull);
      expect(captured!.method, 'POST');
      expect(captured!.url.path, contains('/api/checkins'));
    });

    test('request body contains patientId and caregiverId', () async {
      // Verifies that the outgoing JSON payload carries both identifiers.
      http.Request? captured;
      await http.runWithClient(
        () => CheckinService.addCheckin('p1', 'c1'),
        () => MockClient((req) async {
          captured = req;
          return http.Response('', 201);
        }),
      );
      final body = jsonDecode(captured!.body) as Map<String, dynamic>;
      expect(body['patientId'], 'p1');
      expect(body['caregiverId'], 'c1');
    });

    test('request body includes status "completed"', () async {
      // Check-in status is always "completed" per business logic.
      http.Request? captured;
      await http.runWithClient(
        () => CheckinService.addCheckin('p2', 'c2'),
        () => MockClient((req) async {
          captured = req;
          return http.Response('', 201);
        }),
      );
      final body = jsonDecode(captured!.body) as Map<String, dynamic>;
      expect(body['status'], 'completed');
    });

    test('request body includes a timestamp field', () async {
      // A timestamp is required for the backend to record when the check-in
      // occurred; it must always be present in the payload.
      http.Request? captured;
      await http.runWithClient(
        () => CheckinService.addCheckin('p3', 'c3'),
        () => MockClient((req) async {
          captured = req;
          return http.Response('', 201);
        }),
      );
      final body = jsonDecode(captured!.body) as Map<String, dynamic>;
      expect(body.containsKey('timestamp'), isTrue);
      expect(body['timestamp'], isA<String>());
    });

    test('request includes Content-Type: application/json header', () async {
      // The backend expects JSON-encoded bodies; the header must be set.
      http.Request? captured;
      await http.runWithClient(
        () => CheckinService.addCheckin('p4', 'c4'),
        () => MockClient((req) async {
          captured = req;
          return http.Response('', 201);
        }),
      );
      expect(
        captured!.headers['Content-Type'],
        contains('application/json'),
      );
    });
  });

  // ─── getCheckinCount ──────────────────────────────────────────────────────

  group('CheckinService.getCheckinCount()', () {
    test('returns the count from JSON body on HTTP 200', () async {
      // Verifies that the "count" field is extracted from the response and
      // returned as an integer.
      final result = await http.runWithClient(
        () => CheckinService.getCheckinCount('caregiver-1'),
        () => MockClient((_) async =>
            http.Response(jsonEncode({'count': 42}), 200)),
      );
      expect(result, 42);
    });

    test('returns 0 when count field is absent from response body', () async {
      // Verifies the null-coalescing default: missing "count" becomes 0.
      final result = await http.runWithClient(
        () => CheckinService.getCheckinCount('caregiver-2'),
        () => MockClient((_) async =>
            http.Response(jsonEncode({'other': 'field'}), 200)),
      );
      expect(result, 0);
    });

    test('returns 0 when count is explicitly null in response body', () async {
      // Verifies that an explicit null value also triggers the default of 0.
      final result = await http.runWithClient(
        () => CheckinService.getCheckinCount('caregiver-3'),
        () => MockClient((_) async =>
            http.Response(jsonEncode({'count': null}), 200)),
      );
      expect(result, 0);
    });

    test('returns 0 when server responds with 500', () async {
      // Verifies the fallback to 0 on any non-200 status.
      final result = await http.runWithClient(
        () => CheckinService.getCheckinCount('caregiver-4'),
        () => MockClient((_) async => http.Response('Error', 500)),
      );
      expect(result, 0);
    });

    test('returns 0 when server responds with 404', () async {
      // A 404 also returns 0 since only 200 is handled.
      final result = await http.runWithClient(
        () => CheckinService.getCheckinCount('caregiver-5'),
        () => MockClient((_) async => http.Response('Not Found', 404)),
      );
      expect(result, 0);
    });

    test('sends GET request to the count endpoint', () async {
      // Verifies the request method and path are correct.
      http.Request? captured;
      await http.runWithClient(
        () => CheckinService.getCheckinCount('cg-99'),
        () => MockClient((req) async {
          captured = req;
          return http.Response(jsonEncode({'count': 0}), 200);
        }),
      );
      expect(captured, isNotNull);
      expect(captured!.method, 'GET');
      expect(captured!.url.path, contains('count'));
    });

    test('includes caregiverId as a query parameter', () async {
      // The endpoint filters by caregiverId; it must be in the query string.
      http.Request? captured;
      await http.runWithClient(
        () => CheckinService.getCheckinCount('cg-77'),
        () => MockClient((req) async {
          captured = req;
          return http.Response(jsonEncode({'count': 5}), 200);
        }),
      );
      expect(captured!.url.queryParameters['caregiverId'], 'cg-77');
    });

    test('returns correct count of zero without mistaking for error', () async {
      // A legitimate count of 0 must not be confused with an error default.
      final result = await http.runWithClient(
        () => CheckinService.getCheckinCount('new-caregiver'),
        () => MockClient((_) async =>
            http.Response(jsonEncode({'count': 0}), 200)),
      );
      expect(result, 0);
    });
  });
}
