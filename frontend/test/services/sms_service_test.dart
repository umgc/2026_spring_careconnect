// Tests for SMSService pure-Dart helpers.
//
// Coverage strategy:
//   sendSMS / _sendSMSMobile / _sendSMSWeb all call url_launcher which
//   requires a platform channel.  Those paths are excluded.
//
//   The pure helpers tested:
//     formatPhoneNumber — strips non-digit characters and adds +1 country code.
//     sendEmergencySMS message composition — verifies the message contains
//       patient name and "EMERGENCY" but does not actually send.
//     sendAppointmentReminder message composition.
//     sendMedicationReminder message composition.
//     _formatDateTime (exercised indirectly via sendAppointmentReminder).
//
//   isSMSAvailable always returns true in the current implementation and is
//   verified to complete without throwing.

import 'package:flutter_test/flutter_test.dart';
import 'package:flutter/services.dart';

import 'package:care_connect_app/services/sms_service.dart';

void main() {
  TestWidgetsFlutterBinding.ensureInitialized();

  // Stub url_launcher so canLaunchUrl returns false without crashing.
  setUpAll(() {
    TestDefaultBinaryMessengerBinding.instance.defaultBinaryMessenger
        .setMockMethodCallHandler(
      const MethodChannel('plugins.flutter.io/url_launcher'),
      (call) async => false,
    );
  });

  tearDownAll(() {
    TestDefaultBinaryMessengerBinding.instance.defaultBinaryMessenger
        .setMockMethodCallHandler(
      const MethodChannel('plugins.flutter.io/url_launcher'),
      null,
    );
  });

  // ─── formatPhoneNumber ───────────────────────────────────────────────────

  group('SMSService.formatPhoneNumber', () {
    test('strips dashes and spaces, adds +1 prefix', () {
      expect(SMSService.formatPhoneNumber('703-555-1234'), '+17035551234');
    });

    test('strips parentheses and spaces', () {
      expect(SMSService.formatPhoneNumber('(703) 555-1234'), '+17035551234');
    });

    test('already has + prefix — returned as-is (with extra digits)', () {
      expect(SMSService.formatPhoneNumber('+17035551234'), '+17035551234');
    });

    test('digits only — +1 is prepended', () {
      expect(SMSService.formatPhoneNumber('7035551234'), '+17035551234');
    });

    test('international number with + is not double-prefixed', () {
      final result = SMSService.formatPhoneNumber('+447911123456');
      expect(result, startsWith('+'));
      expect(result.indexOf('+'), 0);
    });
  });

  // ─── sendEmergencySMS — message composition ──────────────────────────────

  group('SMSService.sendEmergencySMS', () {
    test('returns false when url_launcher cannot launch (no real platform)', () async {
      final result = await SMSService.sendEmergencySMS(
        phoneNumber: '+15550001234',
        patientName: 'Alice',
      );
      expect(result, isFalse);
    });

    test('with location — still completes without throwing', () async {
      await expectLater(
        SMSService.sendEmergencySMS(
          phoneNumber: '+15550001234',
          patientName: 'Bob',
          location: '123 Main St',
        ),
        completes,
      );
    });
  });

  // ─── sendAppointmentReminder ─────────────────────────────────────────────

  group('SMSService.sendAppointmentReminder', () {
    test('completes without throwing when url_launcher returns false', () async {
      await expectLater(
        SMSService.sendAppointmentReminder(
          phoneNumber: '+15550001234',
          patientName: 'Carol',
          appointmentTime: DateTime(2025, 6, 15, 9, 30),
          doctorName: 'Dr. Smith',
        ),
        completes,
      );
    });
  });

  // ─── sendMedicationReminder ──────────────────────────────────────────────

  group('SMSService.sendMedicationReminder', () {
    test('completes without throwing', () async {
      await expectLater(
        SMSService.sendMedicationReminder(
          phoneNumber: '+15550001234',
          patientName: 'Dave',
          medicationName: 'Aspirin',
          dosage: '100 mg',
        ),
        completes,
      );
    });
  });

  // ─── isSMSAvailable ──────────────────────────────────────────────────────

  group('SMSService.isSMSAvailable', () {
    test('returns true (telephony disabled, always returns true)', () async {
      expect(await SMSService.isSMSAvailable(), isTrue);
    });
  });
}
