import 'package:flutter_test/flutter_test.dart';

import 'package:care_connect_app/features/usps/data/providers/digest_fixtures.dart';
import 'package:care_connect_app/features/usps/domain/models/digest_raw.dart';

void main() {
  group('buildSimpleDigest', () {
    // --- Return type ---

    test('returns a DigestRaw instance', () {
      // Verifies the factory produces the correct domain type.
      expect(buildSimpleDigest(), isA<DigestRaw>());
    });

    // --- Title ---

    test('uses default title when title is omitted', () {
      // The h1 should contain the canonical USPS digest subject line.
      final result = buildSimpleDigest();
      expect(
        result.html,
        contains('Your USPS Informed Delivery Daily Digest'),
      );
    });

    test('uses provided title instead of default', () {
      // A caller-supplied title must appear in the HTML and the default must not.
      final result = buildSimpleDigest(title: 'Custom Digest Title');
      expect(result.html, contains('Custom Digest Title'));
      expect(
        result.html,
        isNot(contains('Your USPS Informed Delivery Daily Digest')),
      );
    });

    // --- receivedAt ---

    test('uses provided receivedAt timestamp', () {
      // The exact DateTime passed in must be stored verbatim.
      final stamp = DateTime(2025, 2, 14, 8, 30, 0);
      final result = buildSimpleDigest(receivedAt: stamp);
      expect(result.receivedAt, equals(stamp));
    });

    test('defaults receivedAt to approximately DateTime.now() when omitted', () {
      // The generated timestamp should fall within the current second.
      final before = DateTime.now();
      final result = buildSimpleDigest();
      final after = DateTime.now();

      expect(
        result.receivedAt.millisecondsSinceEpoch,
        greaterThanOrEqualTo(before.millisecondsSinceEpoch),
      );
      expect(
        result.receivedAt.millisecondsSinceEpoch,
        lessThanOrEqualTo(after.millisecondsSinceEpoch),
      );
    });

    // --- Image count / CIDs ---

    test('produces exactly one CID entry by default (imageCount=1)', () {
      // Default imageCount is 1, so the cids map should have a single entry.
      final result = buildSimpleDigest();
      expect(result.cids.length, 1);
    });

    test('names the default CID key "piece1"', () {
      final result = buildSimpleDigest();
      expect(result.cids.containsKey('piece1'), isTrue);
    });

    test('produces zero CID entries when imageCount is 0', () {
      // Edge case: no mail pieces means an empty cids map and no img tags.
      final result = buildSimpleDigest(imageCount: 0);
      expect(result.cids, isEmpty);
    });

    test('produces the correct number of CID entries for imageCount > 1', () {
      // Each mail piece should get its own key in the cids map.
      final result = buildSimpleDigest(imageCount: 3);
      expect(result.cids.length, 3);
    });

    test('CID keys follow the "pieceN" naming scheme', () {
      // Keys must be piece1, piece2, piece3 – 1-indexed.
      final result = buildSimpleDigest(imageCount: 3);
      expect(result.cids.containsKey('piece1'), isTrue);
      expect(result.cids.containsKey('piece2'), isTrue);
      expect(result.cids.containsKey('piece3'), isTrue);
    });

    test('each CID value contains the fixed 6-byte sentinel payload', () {
      // The fixture always stores [0,1,2,3,4,5] as fake image bytes.
      final result = buildSimpleDigest(imageCount: 2);
      for (final bytes in result.cids.values) {
        expect(bytes, equals([0, 1, 2, 3, 4, 5]));
      }
    });

    // --- HTML structure ---

    test('HTML contains valid top-level document structure', () {
      // The generated HTML must be a well-formed skeleton that a parser can handle.
      final result = buildSimpleDigest();
      expect(result.html, contains('<!doctype html>'));
      expect(result.html, contains('<html>'));
      expect(result.html, contains('<body>'));
      expect(result.html, contains('</html>'));
    });

    test('HTML wraps title in an h1 element', () {
      final result = buildSimpleDigest(title: 'My Title');
      expect(result.html, contains('<h1>My Title</h1>'));
    });

    test('HTML contains an img tag with cid: src for each mail piece', () {
      // The parser downstream relies on cid: references to resolve inline images.
      final result = buildSimpleDigest(imageCount: 2);
      expect(result.html, contains('src="cid:piece1"'));
      expect(result.html, contains('src="cid:piece2"'));
    });

    test('img alt attributes match their corresponding CID key', () {
      // Alt text must equal the CID key so assistive tools and parsers can correlate.
      final result = buildSimpleDigest(imageCount: 2);
      expect(result.html, contains('alt="piece1"'));
      expect(result.html, contains('alt="piece2"'));
    });

    test('HTML contains no img tags when imageCount is 0', () {
      final result = buildSimpleDigest(imageCount: 0);
      expect(result.html, isNot(contains('<img')));
    });
  });
}
