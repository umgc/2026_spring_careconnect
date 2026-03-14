import 'package:care_connect_app/services/local_db/offline_sync_row.dart';
import 'package:flutter_test/flutter_test.dart';

void main() {
  group('offline_sync_row.dart', () {
    test('stores immutable row data correctly', () {
      final now = DateTime.utc(2026, 3, 12, 10, 30);
      final row = OfflineSyncDbRow(
        id: 'row-1',
        fingerprint: 'abc123',
        method: 'POST',
        url: 'https://example.org/v1/api/tasks',
        headersJson: '{"Content-Type":"application/json"}',
        bodyJson: '{"title":"Daily task"}',
        createdAt: now,
        status: 'pending',
        retryCount: 1,
        lastError: null,
      );

      expect(row.id, equals('row-1'));
      expect(row.fingerprint, equals('abc123'));
      expect(row.method, equals('POST'));
      expect(row.url, contains('/tasks'));
      expect(row.headersJson, contains('Content-Type'));
      expect(row.bodyJson, contains('Daily task'));
      expect(row.createdAt, equals(now));
      expect(row.status, equals('pending'));
      expect(row.retryCount, equals(1));
      expect(row.lastError, isNull);
    });
  });
}
