import 'package:flutter_test/flutter_test.dart';
import 'package:care_connect_app/features/telemetry/telemetry_guardrails.dart';

void main() {
  group('TelemetryGuardrails.sanitize', () {
    
    test('should return null if the event name is not whitelisted', () {
      final result = TelemetryGuardrails.sanitize('user_typed_password', {
        'count': 1,
      });
      expect(result, isNull);
    });

    test('should allow whitelisted events with valid properties', () {
      const event = 'button_tap';
      final props = {'button_id': 'submit_form', 'retry_count': 3};
      
      final result = TelemetryGuardrails.sanitize(event, props);
      
      expect(result, isNotNull);
      expect(result?['button_id'], 'submit_form');
      expect(result?['retry_count'], 3);
    });

    test('should strip blocked keys (case-insensitive) from properties', () {
      final props = {
        'button_id': 'save_medication',
        'medication': 'Advil', // Blocked key
        'PatientID': '12345',  // Blocked key (case sensitivity check)
        'is_active': true,
      };

      final result = TelemetryGuardrails.sanitize('feature.medications.add', props);

      expect(result, isNotNull);
      expect(result, contains('button_id'));
      expect(result, contains('is_active'));
      expect(result, isNot(contains('medication')));
      expect(result, isNot(contains('PatientID')));
    });

    test('should drop string values longer than 64 characters', () {
      final longString = 'A' * 65;
      final shortString = 'A' * 64;
      
      final props = {
        'valid_desc': shortString,
        'invalid_desc': longString,
      };

      final result = TelemetryGuardrails.sanitize('screen_view', props);

      expect(result?['valid_desc'], shortString);
      expect(result, isNot(contains('invalid_desc')));
    });

    test('should only allow primitives (String, num, bool)', () {
      final props = {
        'is_valid': true,
        'count': 42,
        'tag': 'v1',
        'nested_data': {'id': 1}, // Map (Not allowed)
        'list_data': [1, 2, 3],    // List (Not allowed)
      };

      final result = TelemetryGuardrails.sanitize('error_network', props);

      expect(result, containsPair('is_valid', true));
      expect(result, containsPair('count', 42));
      expect(result, containsPair('tag', 'v1'));
      expect(result, isNot(contains('nested_data')));
      expect(result, isNot(contains('list_data')));
    });

    test('should return an empty map if all properties are filtered out', () {
      final props = {
        'ssn': '000-00-0000',
        'notes': 'Patient is feeling better',
      };

      final result = TelemetryGuardrails.sanitize('screen_view', props);

      expect(result, isEmpty);
    });
  });
}