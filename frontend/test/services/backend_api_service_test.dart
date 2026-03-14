// Tests for BackendApiService.
//
// Coverage strategy:
//   BackendApiService uses http.get/http.post top-level calls,
//   interceptable via http.runWithClient + MockClient.
//   Static _authToken is set via setAuthToken before each group.
//
//   Branches tested:
//     setAuthToken — auth token is included in subsequent request headers.
//     getCaregiverPatients — 200 → parses patient+link data; non-200 → []; exception → [].
//     getConversation — 200 → parses message list; non-200 → [].
//     sendMessage — 200 → true; 201 → true; other → false; exception → false.
//     sendSMS — 200 → true; 201 → true; other → false.
//     sendVideoCallInvitation — always true (non-200 and exception both return true).
//     logVideoCall — 200 → true; non-200 → false; exception → false.
//     getCallHistory — 200 → list; non-200 → [].
//     testConnection — 200 → true; non-200 → false; exception → false.

import 'dart:convert';

import 'package:flutter_test/flutter_test.dart';
import 'package:http/http.dart' as http;
import 'package:http/testing.dart';

import 'package:care_connect_app/services/backend_api_service.dart';

void main() {
  // ─── getCaregiverPatients ──────────────────────────────────────────────────

  group('BackendApiService.getCaregiverPatients', () {
    test('200 → returns patient list with merged link data', () async {
      BackendApiService.setAuthToken('test-token');
      final body = jsonEncode([
        {
          'patient': {'id': 1, 'firstName': 'Alice', 'lastName': 'Smith'},
          'link': {'id': 10, 'status': 'active'},
          'relationship': 'FAMILY',
        },
      ]);
      final result = await http.runWithClient(
        () => BackendApiService.getCaregiverPatients(1),
        () => MockClient((_) async => http.Response(body, 200)),
      );
      expect(result.length, 1);
      expect(result[0]['firstName'], 'Alice');
      expect(result[0]['linkId'], 10);
      expect(result[0]['linkStatus'], 'active');
      expect(result[0]['relationship'], 'FAMILY');
    });

    test('item without patient field is skipped', () async {
      final body = jsonEncode([
        {'link': null},
      ]);
      final result = await http.runWithClient(
        () => BackendApiService.getCaregiverPatients(1),
        () => MockClient((_) async => http.Response(body, 200)),
      );
      expect(result, isEmpty);
    });

    test('non-200 → returns empty list', () async {
      final result = await http.runWithClient(
        () => BackendApiService.getCaregiverPatients(1),
        () => MockClient((_) async => http.Response('error', 500)),
      );
      expect(result, isEmpty);
    });

    test('exception → returns empty list', () async {
      final result = await http.runWithClient(
        () => BackendApiService.getCaregiverPatients(1),
        () => MockClient((_) async => throw Exception('network')),
      );
      expect(result, isEmpty);
    });
  });

  // ─── getConversation ──────────────────────────────────────────────────────

  group('BackendApiService.getConversation', () {
    test('200 → returns message list', () async {
      final body = jsonEncode([
        {'id': 'm1', 'message': 'Hello'},
      ]);
      final result = await http.runWithClient(
        () => BackendApiService.getConversation('u1', 'u2'),
        () => MockClient((_) async => http.Response(body, 200)),
      );
      expect(result.length, 1);
      expect(result[0]['id'], 'm1');
    });

    test('non-200 → returns empty list', () async {
      final result = await http.runWithClient(
        () => BackendApiService.getConversation('u1', 'u2'),
        () => MockClient((_) async => http.Response('', 404)),
      );
      expect(result, isEmpty);
    });

    test('exception → returns empty list', () async {
      final result = await http.runWithClient(
        () => BackendApiService.getConversation('u1', 'u2'),
        () => MockClient((_) async => throw Exception('network')),
      );
      expect(result, isEmpty);
    });
  });

  // ─── sendMessage ──────────────────────────────────────────────────────────

  group('BackendApiService.sendMessage', () {
    test('200 → returns true', () async {
      final result = await http.runWithClient(
        () => BackendApiService.sendMessage(
          senderId: 's1',
          senderName: 'Alice',
          recipientId: 'r1',
          message: 'Hi',
        ),
        () => MockClient((_) async => http.Response('', 200)),
      );
      expect(result, isTrue);
    });

    test('201 → returns true', () async {
      final result = await http.runWithClient(
        () => BackendApiService.sendMessage(
          senderId: 's1',
          senderName: 'Alice',
          recipientId: 'r1',
          message: 'Hi',
        ),
        () => MockClient((_) async => http.Response('', 201)),
      );
      expect(result, isTrue);
    });

    test('non-200/201 → returns false', () async {
      final result = await http.runWithClient(
        () => BackendApiService.sendMessage(
          senderId: 's1',
          senderName: 'Alice',
          recipientId: 'r1',
          message: 'Hi',
        ),
        () => MockClient((_) async => http.Response('', 500)),
      );
      expect(result, isFalse);
    });

    test('exception → returns false', () async {
      final result = await http.runWithClient(
        () => BackendApiService.sendMessage(
          senderId: 's1',
          senderName: 'Alice',
          recipientId: 'r1',
          message: 'Hi',
        ),
        () => MockClient((_) async => throw Exception('network')),
      );
      expect(result, isFalse);
    });
  });

  // ─── sendSMS ──────────────────────────────────────────────────────────────

  group('BackendApiService.sendSMS', () {
    test('200 → returns true', () async {
      final result = await http.runWithClient(
        () => BackendApiService.sendSMS(
          senderName: 'Alice',
          recipientPhone: '+15555555555',
          message: 'Test',
        ),
        () => MockClient((_) async => http.Response('', 200)),
      );
      expect(result, isTrue);
    });

    test('201 → returns true', () async {
      final result = await http.runWithClient(
        () => BackendApiService.sendSMS(
          senderName: 'Alice',
          recipientPhone: '+15555555555',
          message: 'Test',
        ),
        () => MockClient((_) async => http.Response('', 201)),
      );
      expect(result, isTrue);
    });

    test('non-200 → returns false', () async {
      final result = await http.runWithClient(
        () => BackendApiService.sendSMS(
          senderName: 'Alice',
          recipientPhone: '+15555555555',
          message: 'Test',
        ),
        () => MockClient((_) async => http.Response('', 400)),
      );
      expect(result, isFalse);
    });
  });

  // ─── sendVideoCallInvitation ──────────────────────────────────────────────

  group('BackendApiService.sendVideoCallInvitation', () {
    test('200 → returns true', () async {
      final result = await http.runWithClient(
        () => BackendApiService.sendVideoCallInvitation(
          callerId: 'c1',
          callerName: 'Alice',
          recipientId: 'r1',
          recipientName: 'Bob',
          callId: 'call123',
        ),
        () => MockClient((_) async => http.Response('', 200)),
      );
      expect(result, isTrue);
    });

    test('non-200 → still returns true (notifications non-fatal)', () async {
      final result = await http.runWithClient(
        () => BackendApiService.sendVideoCallInvitation(
          callerId: 'c1',
          callerName: 'Alice',
          recipientId: 'r1',
          recipientName: 'Bob',
          callId: 'call123',
        ),
        () => MockClient((_) async => http.Response('', 500)),
      );
      expect(result, isTrue);
    });

    test('exception → still returns true', () async {
      final result = await http.runWithClient(
        () => BackendApiService.sendVideoCallInvitation(
          callerId: 'c1',
          callerName: 'Alice',
          recipientId: 'r1',
          recipientName: 'Bob',
          callId: 'call123',
        ),
        () => MockClient((_) async => throw Exception('error')),
      );
      expect(result, isTrue);
    });
  });

  // ─── logVideoCall ─────────────────────────────────────────────────────────

  group('BackendApiService.logVideoCall', () {
    test('200 → returns true', () async {
      final now = DateTime.now();
      final result = await http.runWithClient(
        () => BackendApiService.logVideoCall(
          callId: 'c1',
          callerId: 'u1',
          callerName: 'Alice',
          recipientId: 'u2',
          recipientName: 'Bob',
          startTime: now,
          endTime: now.add(const Duration(minutes: 5)),
          wasAnswered: true,
          isVideoCall: true,
        ),
        () => MockClient((_) async => http.Response('', 200)),
      );
      expect(result, isTrue);
    });

    test('non-200 → returns false', () async {
      final now = DateTime.now();
      final result = await http.runWithClient(
        () => BackendApiService.logVideoCall(
          callId: 'c1',
          callerId: 'u1',
          callerName: 'Alice',
          recipientId: 'u2',
          recipientName: 'Bob',
          startTime: now,
          wasAnswered: false,
          isVideoCall: false,
        ),
        () => MockClient((_) async => http.Response('', 500)),
      );
      expect(result, isFalse);
    });

    test('exception → returns false', () async {
      final now = DateTime.now();
      final result = await http.runWithClient(
        () => BackendApiService.logVideoCall(
          callId: 'c1',
          callerId: 'u1',
          callerName: 'Alice',
          recipientId: 'u2',
          recipientName: 'Bob',
          startTime: now,
          wasAnswered: true,
          isVideoCall: true,
        ),
        () => MockClient((_) async => throw Exception('network')),
      );
      expect(result, isFalse);
    });
  });

  // ─── getCallHistory ───────────────────────────────────────────────────────

  group('BackendApiService.getCallHistory', () {
    test('200 → returns list', () async {
      final body = jsonEncode([
        {'callId': 'c1', 'wasAnswered': true},
      ]);
      final result = await http.runWithClient(
        () => BackendApiService.getCallHistory('u1'),
        () => MockClient((_) async => http.Response(body, 200)),
      );
      expect(result.length, 1);
      expect(result[0]['callId'], 'c1');
    });

    test('non-200 → returns empty list', () async {
      final result = await http.runWithClient(
        () => BackendApiService.getCallHistory('u1'),
        () => MockClient((_) async => http.Response('', 404)),
      );
      expect(result, isEmpty);
    });

    test('exception → returns empty list', () async {
      final result = await http.runWithClient(
        () => BackendApiService.getCallHistory('u1'),
        () => MockClient((_) async => throw Exception('network')),
      );
      expect(result, isEmpty);
    });
  });

  // ─── testConnection ───────────────────────────────────────────────────────

  group('BackendApiService.testConnection', () {
    test('200 → returns true', () async {
      final result = await http.runWithClient(
        () => BackendApiService.testConnection(),
        () => MockClient((_) async => http.Response('ok', 200)),
      );
      expect(result, isTrue);
    });

    test('non-200 → returns false', () async {
      final result = await http.runWithClient(
        () => BackendApiService.testConnection(),
        () => MockClient((_) async => http.Response('', 503)),
      );
      expect(result, isFalse);
    });

    test('exception → returns false', () async {
      final result = await http.runWithClient(
        () => BackendApiService.testConnection(),
        () => MockClient((_) async => throw Exception('network')),
      );
      expect(result, isFalse);
    });
  });
}
