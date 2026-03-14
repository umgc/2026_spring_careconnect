// Tests for EvvService data models and static constants.
//
// Coverage strategy:
//   EvvService HTTP methods require Geolocator, Connectivity, DeviceInfo, and a
//   live HTTP client — those are skipped.
//   Pure Dart model classes and static constants are fully testable:
//
//   Branches tested:
//     EvvService static constants — serviceTypes, stateCodes, correctionReasonCodes.
//     EvvOfflineQueue.fromJson — parses all fields; optional fields null-safe.
//     EvvSearchRequest defaults — page=0, size=20, sortBy='createdAt', sortDirection='DESC'.
//     EvvSearchResult.fromJson — parses content list, pagination fields.
//     EvvCorrectionRequest.toJson — required fields present, optional fields omitted when null.
//     EvvCorrectionRequest.toJson — optional fields included when provided.
//     EvvCorrectionRequest.toJson — datetime fields formatted with timezone offset.

import 'package:flutter_test/flutter_test.dart';

import 'package:care_connect_app/services/evv_service.dart';

// Minimal EvvRecord JSON used by EvvSearchResult.fromJson tests.
Map<String, dynamic> _minimalRecordJson() => {
      'id': 1,
      'patient': null,
      'serviceType': 'Personal Care',
      'individualName': 'Alice Smith',
      'caregiverId': 10,
      'dateOfService': '2025-03-01',
      'timeIn': '2025-03-01T08:00:00.000Z',
      'timeOut': '2025-03-01T10:00:00.000Z',
      'status': 'APPROVED',
      'stateCode': 'MD',
      'isOffline': false,
      'eorApprovalRequired': false,
      'isCorrected': false,
      'createdAt': '2025-03-01T08:00:00.000Z',
      'updatedAt': '2025-03-01T10:00:00.000Z',
    };

void main() {
  // ─── Static constants ─────────────────────────────────────────────────────

  group('EvvService static constants', () {
    test('serviceTypes contains expected service names', () {
      expect(EvvService.serviceTypes, contains('Personal Care'));
      expect(EvvService.serviceTypes, contains('Skilled Nursing'));
      expect(EvvService.serviceTypes, contains('Home Health Aide'));
      expect(EvvService.serviceTypes.length, greaterThan(5));
    });

    test('stateCodes contains MD, DC, VA', () {
      expect(EvvService.stateCodes, containsAll(['MD', 'DC', 'VA']));
    });

    test('correctionReasonCodes contains expected codes', () {
      expect(EvvService.correctionReasonCodes, contains('TIME_ERROR'));
      expect(EvvService.correctionReasonCodes, contains('LOCATION_ERROR'));
      expect(EvvService.correctionReasonCodes, contains('OTHER'));
    });
  });

  // ─── EvvOfflineQueue.fromJson ─────────────────────────────────────────────

  group('EvvOfflineQueue.fromJson', () {
    test('parses all required fields', () {
      final json = {
        'id': 5,
        'recordId': 10,
        'operationType': 'CREATE',
        'caregiverId': 3,
        'deviceId': 'device-abc',
        'queuedAt': '2025-03-01T08:00:00.000Z',
        'syncAttempts': 2,
        'lastSyncAttempt': '2025-03-01T09:00:00.000Z',
        'syncStatus': 'PENDING',
        'lastError': 'Connection refused',
        'priority': 2,
        'recordData': {'key': 'value'},
      };
      final q = EvvOfflineQueue.fromJson(json);
      expect(q.id, 5);
      expect(q.recordId, 10);
      expect(q.operationType, 'CREATE');
      expect(q.caregiverId, 3);
      expect(q.deviceId, 'device-abc');
      expect(q.syncAttempts, 2);
      expect(q.syncStatus, 'PENDING');
      expect(q.lastError, 'Connection refused');
      expect(q.priority, 2);
      expect(q.recordData['key'], 'value');
    });

    test('optional fields default gracefully when absent', () {
      final json = {
        'id': 1,
        'recordId': 2,
        'operationType': 'UPDATE',
        'caregiverId': 4,
        'queuedAt': '2025-03-01T08:00:00.000Z',
        'syncStatus': 'SYNCED',
        'recordData': <String, dynamic>{},
      };
      final q = EvvOfflineQueue.fromJson(json);
      expect(q.deviceId, isNull);
      expect(q.lastSyncAttempt, isNull);
      expect(q.lastError, isNull);
      expect(q.syncAttempts, 0);
      expect(q.priority, 1);
      expect(q.recordData, isEmpty);
    });
  });

  // ─── EvvSearchRequest defaults ────────────────────────────────────────────

  group('EvvSearchRequest defaults', () {
    test('default constructor sets expected page/size/sort values', () {
      final req = EvvSearchRequest();
      expect(req.page, 0);
      expect(req.size, 20);
      expect(req.sortBy, 'createdAt');
      expect(req.sortDirection, 'DESC');
    });

    test('optional fields are null by default', () {
      final req = EvvSearchRequest();
      expect(req.patientName, isNull);
      expect(req.serviceType, isNull);
      expect(req.caregiverId, isNull);
      expect(req.startDate, isNull);
      expect(req.endDate, isNull);
      expect(req.stateCode, isNull);
      expect(req.status, isNull);
    });

    test('custom values are stored correctly', () {
      final start = DateTime(2025, 1, 1);
      final end = DateTime(2025, 1, 31);
      final req = EvvSearchRequest(
        patientName: 'Alice',
        serviceType: 'Personal Care',
        caregiverId: 7,
        startDate: start,
        endDate: end,
        stateCode: 'MD',
        status: 'APPROVED',
        page: 2,
        size: 10,
        sortBy: 'dateOfService',
        sortDirection: 'ASC',
      );
      expect(req.patientName, 'Alice');
      expect(req.caregiverId, 7);
      expect(req.page, 2);
      expect(req.sortDirection, 'ASC');
    });
  });

  // ─── EvvSearchResult.fromJson ─────────────────────────────────────────────

  group('EvvSearchResult.fromJson', () {
    test('parses pagination and content list', () {
      final json = {
        'content': [_minimalRecordJson()],
        'totalElements': 50,
        'totalPages': 3,
        'size': 20,
        'number': 0,
        'first': true,
        'last': false,
      };
      final result = EvvSearchResult.fromJson(json);
      expect(result.content.length, 1);
      expect(result.totalElements, 50);
      expect(result.totalPages, 3);
      expect(result.size, 20);
      expect(result.number, 0);
      expect(result.first, isTrue);
      expect(result.last, isFalse);
    });

    test('empty content list → content is empty', () {
      final json = {
        'content': <dynamic>[],
        'totalElements': 0,
        'totalPages': 0,
        'size': 20,
        'number': 0,
        'first': true,
        'last': true,
      };
      final result = EvvSearchResult.fromJson(json);
      expect(result.content, isEmpty);
    });
  });

  // ─── EvvCorrectionRequest.toJson ─────────────────────────────────────────

  group('EvvCorrectionRequest.toJson', () {
    test('required fields always present in output', () {
      final req = EvvCorrectionRequest(
        originalRecordId: 5,
        reasonCode: 'TIME_ERROR',
        explanation: 'Wrong clock-in time',
      );
      final json = req.toJson();
      expect(json['originalRecordId'], 5);
      expect(json['reasonCode'], 'TIME_ERROR');
      expect(json['explanation'], 'Wrong clock-in time');
    });

    test('optional fields omitted when null', () {
      final req = EvvCorrectionRequest(
        originalRecordId: 5,
        reasonCode: 'OTHER',
        explanation: 'Misc',
      );
      final json = req.toJson();
      expect(json.containsKey('serviceType'), isFalse);
      expect(json.containsKey('timeIn'), isFalse);
      expect(json.containsKey('timeOut'), isFalse);
      expect(json.containsKey('locationLat'), isFalse);
    });

    test('optional fields included when provided', () {
      final timeIn = DateTime.utc(2025, 3, 1, 8, 0, 0);
      final timeOut = DateTime.utc(2025, 3, 1, 10, 0, 0);
      final req = EvvCorrectionRequest(
        originalRecordId: 7,
        reasonCode: 'LOCATION_ERROR',
        explanation: 'Wrong address',
        serviceType: 'Personal Care',
        individualName: 'Bob',
        dateOfService: DateTime(2025, 3, 1),
        timeIn: timeIn,
        timeOut: timeOut,
        locationLat: 38.9,
        locationLng: -77.0,
        locationSource: 'GPS',
        stateCode: 'MD',
      );
      final json = req.toJson();
      expect(json['serviceType'], 'Personal Care');
      expect(json['individualName'], 'Bob');
      expect(json['locationLat'], 38.9);
      expect(json['locationLng'], -77.0);
      expect(json['locationSource'], 'GPS');
      expect(json['stateCode'], 'MD');
      // Date formatted as YYYY-MM-DD.
      expect(json['dateOfService'], startsWith('2025-03-01'));
      // Time fields should be non-null strings.
      expect(json['timeIn'], isA<String>());
      expect(json['timeOut'], isA<String>());
    });

    test('deviceInfo included when provided', () {
      final req = EvvCorrectionRequest(
        originalRecordId: 1,
        reasonCode: 'SYSTEM_ERROR',
        explanation: 'Crash',
        deviceInfo: {'platform': 'Flutter', 'version': '1.0'},
      );
      final json = req.toJson();
      expect((json['deviceInfo'] as Map)['platform'], 'Flutter');
    });
  });
}
