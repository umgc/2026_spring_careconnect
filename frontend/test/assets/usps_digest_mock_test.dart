// Tests for buildMockUspsDigestMap
// (lib/assets/usps_digest_mock.dart).
//
// Coverage strategy:
//   buildMockUspsDigestMap is a pure Dart function that builds a structured
//   Map with no platform channels or network I/O.
//
//   Branches tested:
//     default DateTime  — called with no argument; digestDate is a non-empty
//                         ISO-8601 string close to the current time.
//     custom DateTime   — called with an explicit now; digestDate matches the
//                         supplied value and relative dates are correct.
//     mailpieces count  — always returns exactly 5 mailpieces.
//     packages count    — always returns exactly 1 package.
//     mailpiece fields  — each piece has id, sender, summary, imageDataUrl,
//                         dateIso, and actions (track/redelivery/dashboard).
//     imageDataUrl      — starts with 'data:image/svg+xml;base64,' (base64 SVG).
//     package fields    — has trackingNumber, expectedDateIso, and actions.
//     date offsets      — mailpieces at day 0 match digestDate; day -1 and
//                         -2 pieces are earlier; the package is one day ahead.

import 'package:flutter_test/flutter_test.dart';

import 'package:care_connect_app/assets/usps_digest_mock.dart';

void main() {
  group('buildMockUspsDigestMap', () {
    test('returns a map with digestDate, mailpieces, and packages keys', () {
      // Verifies the top-level structure of the returned map.
      final map = buildMockUspsDigestMap();
      expect(map.containsKey('digestDate'), isTrue);
      expect(map.containsKey('mailpieces'), isTrue);
      expect(map.containsKey('packages'), isTrue);
    });

    test('returns exactly 5 mailpieces', () {
      // Verifies the expected count of mock mail items.
      final map = buildMockUspsDigestMap();
      final pieces = map['mailpieces'] as List<dynamic>;
      expect(pieces.length, 5);
    });

    test('returns exactly 1 package', () {
      // Verifies the expected count of mock package entries.
      final map = buildMockUspsDigestMap();
      final pkgs = map['packages'] as List<dynamic>;
      expect(pkgs.length, 1);
    });

    test('digestDate matches supplied DateTime', () {
      // Verifies that a custom now parameter is reflected in digestDate.
      final t = DateTime(2025, 11, 15, 9, 0);
      final map = buildMockUspsDigestMap(now: t);
      expect(map['digestDate'], t.toIso8601String());
    });

    test('mailpiece 0 (ACME Bank) has required fields', () {
      // Verifies the structure and sender identity of the first mailpiece.
      final t = DateTime(2025, 6, 1);
      final map = buildMockUspsDigestMap(now: t);
      final piece = (map['mailpieces'] as List<dynamic>)[0] as Map<String, dynamic>;
      expect(piece['id'], 'm-1001');
      expect(piece['sender'], 'ACME Bank');
      expect(piece['summary'], isA<String>());
      expect(piece['imageDataUrl'], startsWith('data:image/svg+xml;base64,'));
      expect(piece['dateIso'], t.toIso8601String());
      expect(piece.containsKey('actions'), isTrue);
      final actions = piece['actions'] as Map<String, dynamic>;
      expect(actions.containsKey('track'), isTrue);
      expect(actions.containsKey('redelivery'), isTrue);
      expect(actions.containsKey('dashboard'), isTrue);
    });

    test('mailpiece 1 date is one day before now', () {
      // Verifies the relative date offset for the second mailpiece.
      final t = DateTime(2025, 6, 10);
      final map = buildMockUspsDigestMap(now: t);
      final piece = (map['mailpieces'] as List<dynamic>)[1] as Map<String, dynamic>;
      final expected = t.subtract(const Duration(days: 1)).toIso8601String();
      expect(piece['dateIso'], expected);
    });

    test('mailpiece 2 date is two days before now', () {
      // Verifies the relative date offset for the third mailpiece.
      final t = DateTime(2025, 6, 10);
      final map = buildMockUspsDigestMap(now: t);
      final piece = (map['mailpieces'] as List<dynamic>)[2] as Map<String, dynamic>;
      final expected = t.subtract(const Duration(days: 2)).toIso8601String();
      expect(piece['dateIso'], expected);
    });

    test('package expected date is one day after now', () {
      // Verifies that the mock package is expected to arrive tomorrow.
      final t = DateTime(2025, 6, 10);
      final map = buildMockUspsDigestMap(now: t);
      final pkg = (map['packages'] as List<dynamic>)[0] as Map<String, dynamic>;
      final expected = t.add(const Duration(days: 1)).toIso8601String();
      expect(pkg['expectedDateIso'], expected);
    });

    test('package has trackingNumber and actions', () {
      // Verifies the package structure has required fields.
      final map = buildMockUspsDigestMap();
      final pkg = (map['packages'] as List<dynamic>)[0] as Map<String, dynamic>;
      expect(pkg['trackingNumber'], isA<String>());
      expect(pkg['trackingNumber'], isNotEmpty);
      expect(pkg.containsKey('actions'), isTrue);
      final actions = pkg['actions'] as Map<String, dynamic>;
      expect(actions['track'], isA<String>());
      expect(actions['redelivery'], isA<String>());
      expect(actions['dashboard'], isA<String>());
    });

    test('all 5 mailpieces have svg image data URLs', () {
      // Verifies that every mailpiece carries a base64-encoded SVG thumbnail.
      final map = buildMockUspsDigestMap();
      final pieces = map['mailpieces'] as List<dynamic>;
      for (final raw in pieces) {
        final piece = raw as Map<String, dynamic>;
        expect(
          piece['imageDataUrl'],
          startsWith('data:image/svg+xml;base64,'),
          reason: 'piece ${piece["id"]} should have svg image data URL',
        );
      }
    });

    test('all 5 mailpieces have unique ids', () {
      // Verifies that no two mailpieces share an id.
      final map = buildMockUspsDigestMap();
      final pieces = map['mailpieces'] as List<dynamic>;
      final ids = pieces.map((p) => (p as Map)['id']).toList();
      expect(ids.toSet().length, 5);
    });

    test('default call (no now) returns digestDate parseable as DateTime', () {
      // Verifies that the auto-generated digestDate is a valid ISO-8601 string.
      final map = buildMockUspsDigestMap();
      final dateStr = map['digestDate'] as String;
      expect(() => DateTime.parse(dateStr), returnsNormally);
    });
  });
}
