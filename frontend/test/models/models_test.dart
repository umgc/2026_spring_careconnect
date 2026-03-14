import 'package:flutter_test/flutter_test.dart';

import 'package:care_connect_app/models/AddressModel.dart';
import 'package:care_connect_app/models/caregiver_model.dart';
import 'package:care_connect_app/models/notification_settings.dart';
import 'package:care_connect_app/models/patient_model.dart';
import 'package:care_connect_app/models/role-enum.dart';
import 'package:care_connect_app/models/user_model.dart';

// The Address used by PatientUserModel and CaregiverModel comes from the
// dashboard feature package, not from AddressModel.dart.
import 'package:care_connect_app/features/dashboard/models/patient_model.dart'
    as dashboard;

void main() {
  // ─────────────────────────────────────────────────────────────
  // RoleEnum
  // ─────────────────────────────────────────────────────────────
  group('RoleEnum.fromJson', () {
    test('returns patient for lowercase "patient"', () {
      // Verifies the happy-path patient branch.
      expect(RoleEnum.fromJson('patient'), RoleEnum.patient);
    });

    test('returns caregiver for lowercase "caregiver"', () {
      // Verifies the happy-path caregiver branch.
      expect(RoleEnum.fromJson('caregiver'), RoleEnum.caregiver);
    });

    test('returns patient case-insensitively for "PATIENT"', () {
      // Verifies that the switch uses toLowerCase() before matching.
      expect(RoleEnum.fromJson('PATIENT'), RoleEnum.patient);
    });

    test('returns caregiver case-insensitively for "CAREGIVER"', () {
      // Verifies the uppercase caregiver path.
      expect(RoleEnum.fromJson('CAREGIVER'), RoleEnum.caregiver);
    });

    test('returns forbidden for an unrecognised role string', () {
      // Verifies the wildcard default branch produces forbidden.
      expect(RoleEnum.fromJson('admin'), RoleEnum.forbidden);
      expect(RoleEnum.fromJson(''), RoleEnum.forbidden);
    });
  });

  // ─────────────────────────────────────────────────────────────
  // UserModel
  // ─────────────────────────────────────────────────────────────
  group('UserModel', () {
    test('constructor stores all fields', () {
      // Verifies that every field assigned in the constructor is readable.
      final model = UserModel(
        name: 'Alice',
        email: 'alice@example.com',
        userId: 'u-001',
        role: 'PATIENT',
      );

      expect(model.name, 'Alice');
      expect(model.email, 'alice@example.com');
      expect(model.userId, 'u-001');
      expect(model.role, 'PATIENT');
    });

    test('toJson returns a map with all four keys', () {
      // Verifies the serialisation output used for API requests.
      final model = UserModel(
        name: 'Bob',
        email: 'bob@example.com',
        userId: 'u-002',
        role: 'CAREGIVER',
      );
      final json = model.toJson();

      expect(json['name'], 'Bob');
      expect(json['email'], 'bob@example.com');
      expect(json['userId'], 'u-002');
      expect(json['role'], 'CAREGIVER');
    });

    test('fromJson populates all fields from a complete map', () {
      // Verifies round-trip deserialisation from an API response.
      final json = <String, dynamic>{
        'name': 'Carol',
        'email': 'carol@example.com',
        'userId': 'u-003',
        'role': 'PATIENT',
      };
      final model = UserModel.fromJson(json);

      expect(model.name, 'Carol');
      expect(model.email, 'carol@example.com');
      expect(model.userId, 'u-003');
      expect(model.role, 'PATIENT');
    });

    test('fromJson defaults missing fields to empty strings', () {
      // Verifies null-safety defaults when keys are absent from the payload.
      final model = UserModel.fromJson(<String, dynamic>{});

      expect(model.name, '');
      expect(model.email, '');
      expect(model.userId, '');
      expect(model.role, '');
    });
  });

  // ─────────────────────────────────────────────────────────────
  // Address  (lib/models/AddressModel.dart)
  // ─────────────────────────────────────────────────────────────
  group('Address (AddressModel)', () {
    test('constructor stores all fields including optional phone', () {
      // Verifies that every field including the nullable phone is stored.
      final addr = Address(
        line1: '123 Main St',
        line2: 'Apt 4',
        city: 'Springfield',
        state: 'IL',
        zip: '62701',
        phone: '555-1234',
      );

      expect(addr.line1, '123 Main St');
      expect(addr.line2, 'Apt 4');
      expect(addr.city, 'Springfield');
      expect(addr.state, 'IL');
      expect(addr.zip, '62701');
      expect(addr.phone, '555-1234');
    });

    test('constructor phone defaults to null when omitted', () {
      // Verifies the optional phone parameter defaults to null.
      final addr = Address(
        line1: '1 Park Ave',
        line2: '',
        city: 'Albany',
        state: 'NY',
        zip: '12207',
      );

      expect(addr.phone, isNull);
    });

    test('toJson serialises all fields', () {
      // Verifies the map returned by toJson matches constructor values.
      final addr = Address(
        line1: '10 Elm St',
        line2: '',
        city: 'Boston',
        state: 'MA',
        zip: '02101',
        phone: '617-000-0000',
      );
      final json = addr.toJson();

      expect(json['line1'], '10 Elm St');
      expect(json['line2'], '');
      expect(json['city'], 'Boston');
      expect(json['state'], 'MA');
      expect(json['zip'], '02101');
      expect(json['phone'], '617-000-0000');
    });

    test('fromJson populates fields from a complete map', () {
      // Verifies deserialisation from a full address payload.
      final json = <String, dynamic>{
        'line1': '5 Oak Rd',
        'line2': 'Suite 2',
        'city': 'Denver',
        'state': 'CO',
        'zip': '80201',
        'phone': '303-111-2222',
      };
      final addr = Address.fromJson(json);

      expect(addr.line1, '5 Oak Rd');
      expect(addr.city, 'Denver');
      expect(addr.phone, '303-111-2222');
    });

    test('fromJson defaults required string fields to empty when missing', () {
      // Verifies null-safety defaults applied during deserialisation.
      final addr = Address.fromJson(<String, dynamic>{});

      expect(addr.line1, '');
      expect(addr.line2, '');
      expect(addr.city, '');
      expect(addr.state, '');
      expect(addr.zip, '');
    });

    test('fromJson keeps phone null when key is absent', () {
      // Verifies that an absent phone key stays null, not an empty string.
      final addr = Address.fromJson(<String, dynamic>{
        'line1': '1 Test Ln',
        'line2': '',
        'city': 'Miami',
        'state': 'FL',
        'zip': '33101',
      });

      expect(addr.phone, isNull);
    });
  });

  // ─────────────────────────────────────────────────────────────
  // NotificationSettings
  // ─────────────────────────────────────────────────────────────
  group('NotificationSettings', () {
    test('fromJson populates all fields including dates', () {
      // Verifies full deserialisation including ISO-8601 datetime strings.
      final json = <String, dynamic>{
        'id': 7,
        'userId': 42,
        'gamification': true,
        'emergency': false,
        'videoCall': true,
        'audioCall': false,
        'sms': true,
        'significantVitals': false,
        'createdAt': '2024-01-15T10:00:00.000Z',
        'updatedAt': '2024-06-01T08:30:00.000Z',
      };
      final settings = NotificationSettings.fromJson(json);

      expect(settings.id, 7);
      expect(settings.userId, 42);
      expect(settings.gamification, isTrue);
      expect(settings.emergency, isFalse);
      expect(settings.videoCall, isTrue);
      expect(settings.audioCall, isFalse);
      expect(settings.sms, isTrue);
      expect(settings.significantVitals, isFalse);
      expect(settings.createdAt, isNotNull);
      expect(settings.updatedAt, isNotNull);
    });

    test('fromJson handles null id and null dates', () {
      // Verifies that optional id and datetime fields are nullable.
      final json = <String, dynamic>{
        'userId': 10,
        'gamification': false,
        'emergency': true,
        'videoCall': true,
        'audioCall': true,
        'sms': true,
        'significantVitals': true,
      };
      final settings = NotificationSettings.fromJson(json);

      expect(settings.id, isNull);
      expect(settings.createdAt, isNull);
      expect(settings.updatedAt, isNull);
    });

    test('fromJson applies correct defaults for missing boolean fields', () {
      // Verifies the per-field defaults: gamification=false, others=true.
      final settings = NotificationSettings.fromJson(
        <String, dynamic>{'userId': 1},
      );

      expect(settings.gamification, isFalse);
      expect(settings.emergency, isTrue);
      expect(settings.videoCall, isTrue);
      expect(settings.audioCall, isTrue);
      expect(settings.sms, isTrue);
      expect(settings.significantVitals, isTrue);
    });

    test('toJson excludes id and datetime fields', () {
      // Verifies that only the seven editable fields appear in the API payload.
      final settings = NotificationSettings(
        id: 99,
        userId: 5,
        gamification: true,
        emergency: true,
        videoCall: false,
        audioCall: false,
        sms: true,
        significantVitals: true,
        createdAt: DateTime(2024, 1, 1),
        updatedAt: DateTime(2024, 6, 1),
      );
      final json = settings.toJson();

      expect(json.containsKey('id'), isFalse);
      expect(json.containsKey('createdAt'), isFalse);
      expect(json.containsKey('updatedAt'), isFalse);
      expect(json['userId'], 5);
      expect(json['gamification'], isTrue);
      expect(json['videoCall'], isFalse);
    });

    test('copyWith replaces only the specified fields', () {
      // Verifies that copyWith produces a new instance with targeted changes.
      final original = NotificationSettings(
        userId: 3,
        gamification: false,
        emergency: true,
        videoCall: true,
        audioCall: true,
        sms: true,
        significantVitals: true,
      );
      final copy = original.copyWith(gamification: true, sms: false);

      expect(copy.gamification, isTrue);
      expect(copy.sms, isFalse);
      // Unchanged fields should retain original values.
      expect(copy.userId, 3);
      expect(copy.emergency, isTrue);
      expect(copy.videoCall, isTrue);
      expect(copy.audioCall, isTrue);
      expect(copy.significantVitals, isTrue);
    });

    test('copyWith with no arguments returns an equivalent object', () {
      // Verifies that calling copyWith() without parameters preserves state.
      final original = NotificationSettings(
        userId: 8,
        gamification: true,
        emergency: false,
        videoCall: false,
        audioCall: true,
        sms: false,
        significantVitals: true,
      );
      final copy = original.copyWith();

      expect(copy.userId, original.userId);
      expect(copy.gamification, original.gamification);
      expect(copy.emergency, original.emergency);
      expect(copy.videoCall, original.videoCall);
      expect(copy.audioCall, original.audioCall);
      expect(copy.sms, original.sms);
      expect(copy.significantVitals, original.significantVitals);
    });

    test('copyWith can update id, createdAt, and updatedAt', () {
      // Verifies the nullable optional fields in copyWith.
      final original = NotificationSettings(
        userId: 1,
        gamification: false,
        emergency: true,
        videoCall: true,
        audioCall: true,
        sms: true,
        significantVitals: true,
      );
      final ts = DateTime(2025, 3, 1);
      final copy = original.copyWith(id: 42, createdAt: ts, updatedAt: ts);

      expect(copy.id, 42);
      expect(copy.createdAt, ts);
      expect(copy.updatedAt, ts);
    });
  });

  // ─────────────────────────────────────────────────────────────
  // ProfessionalInfo
  // ─────────────────────────────────────────────────────────────
  group('ProfessionalInfo', () {
    test('constructor stores all fields', () {
      // Verifies basic field storage.
      final info = ProfessionalInfo(
        licenseNumber: 'RN-999',
        issuingState: 'CA',
        yearsExperience: 10,
      );

      expect(info.licenseNumber, 'RN-999');
      expect(info.issuingState, 'CA');
      expect(info.yearsExperience, 10);
    });

    test('toJson serialises all three fields', () {
      // Verifies the JSON output used inside caregiver registration payloads.
      final info = ProfessionalInfo(
        licenseNumber: 'LPN-42',
        issuingState: 'TX',
        yearsExperience: 3,
      );
      final json = info.toJson();

      expect(json['licenseNumber'], 'LPN-42');
      expect(json['issuingState'], 'TX');
      expect(json['yearsExperience'], 3);
    });

    test('fromJson populates fields from a full map', () {
      // Verifies deserialisation from an API response containing credentials.
      final info = ProfessionalInfo.fromJson(<String, dynamic>{
        'licenseNumber': 'MD-001',
        'issuingState': 'NY',
        'yearsExperience': 15,
      });

      expect(info.licenseNumber, 'MD-001');
      expect(info.issuingState, 'NY');
      expect(info.yearsExperience, 15);
    });

    test('fromJson defaults missing fields', () {
      // Verifies safe defaults when the payload omits credential fields.
      final info = ProfessionalInfo.fromJson(<String, dynamic>{});

      expect(info.licenseNumber, '');
      expect(info.issuingState, '');
      expect(info.yearsExperience, 0);
    });
  });

  // ─────────────────────────────────────────────────────────────
  // PatientUserModel
  // ─────────────────────────────────────────────────────────────
  group('PatientUserModel', () {
    // Helper that builds a minimal dashboard Address.
    dashboard.Address addr({String line1 = '1 Test St'}) =>
        dashboard.Address(
          line1: line1,
          line2: '',
          city: 'Testville',
          state: 'VA',
          zip: '20001',
        );

    test('constructor stores all fields', () {
      // Verifies that all patient-specific fields are stored alongside base ones.
      final patient = PatientUserModel(
        name: 'Dana',
        email: 'dana@example.com',
        userId: 'p-1',
        role: 'PATIENT',
        firstName: 'Dana',
        lastName: 'Smith',
        phone: '703-555-0001',
        dob: '01/15/1990',
        gender: 'female',
        address: addr(),
      );

      expect(patient.firstName, 'Dana');
      expect(patient.lastName, 'Smith');
      expect(patient.phone, '703-555-0001');
      expect(patient.dob, '01/15/1990');
      expect(patient.gender, 'female');
    });

    test('toJson includes both base and patient-specific fields', () {
      // Verifies that the overridden toJson merges parent and child fields and
      // hard-codes the role as PATIENT.
      final patient = PatientUserModel(
        name: 'Eve',
        email: 'eve@example.com',
        userId: 'p-2',
        role: 'PATIENT',
        firstName: 'Eve',
        lastName: 'Jones',
        phone: '202-555-0002',
        dob: '03/20/1985',
        gender: 'female',
        address: addr(line1: '2 Oak Ave'),
      );
      final json = patient.toJson();

      // Base fields.
      expect(json['name'], 'Eve');
      expect(json['email'], 'eve@example.com');
      expect(json['userId'], 'p-2');
      // Patient-specific fields.
      expect(json['firstName'], 'Eve');
      expect(json['lastName'], 'Jones');
      expect(json['phone'], '202-555-0002');
      expect(json['dob'], '03/20/1985');
      expect(json['gender'], 'female');
      // Role is forced to PATIENT regardless of constructor arg.
      expect(json['role'], 'PATIENT');
      expect(json['address'], isA<Map<String, dynamic>>());
    });

    test('fromJson populates all fields from a complete payload', () {
      // Verifies full deserialisation of a patient API response.
      final json = <String, dynamic>{
        'name': 'Frank',
        'email': 'frank@example.com',
        'userId': 'p-3',
        'role': 'PATIENT',
        'firstName': 'Frank',
        'lastName': 'Brown',
        'phone': '404-555-0003',
        'dob': '07/04/1978',
        'gender': 'male',
        'address': <String, dynamic>{
          'line1': '3 Maple Dr',
          'line2': '',
          'city': 'Atlanta',
          'state': 'GA',
          'zip': '30301',
        },
      };
      final patient = PatientUserModel.fromJson(json);

      expect(patient.firstName, 'Frank');
      expect(patient.lastName, 'Brown');
      expect(patient.dob, '07/04/1978');
      expect(patient.address.city, 'Atlanta');
    });

    test('fromJson defaults missing fields to empty strings', () {
      // Verifies null-safety defaults when keys are absent from the payload.
      final patient = PatientUserModel.fromJson(<String, dynamic>{});

      expect(patient.name, '');
      expect(patient.email, '');
      expect(patient.userId, '');
      expect(patient.role, 'PATIENT');
      expect(patient.firstName, '');
      expect(patient.lastName, '');
      expect(patient.phone, '');
      expect(patient.dob, '');
      expect(patient.gender, '');
    });

    test('toString includes firstName and lastName', () {
      // Verifies the human-readable representation contains key identifiers.
      final patient = PatientUserModel(
        name: 'Gina',
        email: 'gina@example.com',
        userId: 'p-4',
        role: 'PATIENT',
        firstName: 'Gina',
        lastName: 'White',
        phone: '555-0004',
        dob: '02/28/2000',
        gender: 'female',
        address: addr(),
      );

      expect(patient.toString(), contains('Gina'));
      expect(patient.toString(), contains('White'));
    });
  });

  // ─────────────────────────────────────────────────────────────
  // CaregiverModel
  // ─────────────────────────────────────────────────────────────
  group('CaregiverModel', () {
    dashboard.Address addr() => dashboard.Address(
          line1: '99 Care Ln',
          line2: '',
          city: 'Richmond',
          state: 'VA',
          zip: '23220',
        );

    test('constructor stores all fields, professionalInfo optional', () {
      // Verifies the model can be created without professional credentials.
      final cg = CaregiverModel(
        name: 'Hank',
        email: 'hank@example.com',
        userId: 'cg-1',
        role: 'CAREGIVER',
        firstName: 'Hank',
        lastName: 'Green',
        phone: '804-555-0010',
        dob: '05/10/1975',
        gender: 'male',
        caregiverType: 'Family Member',
        address: addr(),
      );

      expect(cg.firstName, 'Hank');
      expect(cg.caregiverType, 'Family Member');
      expect(cg.professionalInfo, isNull);
    });

    test('toJson excludes professional key when professionalInfo is null', () {
      // Verifies the conditional serialisation branch for non-professional caregivers.
      final cg = CaregiverModel(
        name: 'Iris',
        email: 'iris@example.com',
        userId: 'cg-2',
        role: 'CAREGIVER',
        firstName: 'Iris',
        lastName: 'Blue',
        phone: '555-0011',
        dob: '11/11/1980',
        gender: 'female',
        caregiverType: 'Friend',
        address: addr(),
      );
      final json = cg.toJson();

      expect(json.containsKey('professional'), isFalse);
      expect(json['firstName'], 'Iris');
      expect(json['caregiverType'], 'Friend');
    });

    test('toJson includes professional key when professionalInfo is present', () {
      // Verifies the branch where professional credentials are serialised.
      final cg = CaregiverModel(
        name: 'Jack',
        email: 'jack@example.com',
        userId: 'cg-3',
        role: 'CAREGIVER',
        firstName: 'Jack',
        lastName: 'Black',
        phone: '555-0012',
        dob: '09/09/1970',
        gender: 'male',
        caregiverType: 'Professional',
        address: addr(),
        professionalInfo: ProfessionalInfo(
          licenseNumber: 'RN-100',
          issuingState: 'VA',
          yearsExperience: 8,
        ),
      );
      final json = cg.toJson();

      expect(json.containsKey('professional'), isTrue);
      expect(json['professional']['licenseNumber'], 'RN-100');
    });

    test('fromJson without professional key leaves professionalInfo null', () {
      // Verifies deserialisation of a non-professional caregiver payload.
      final json = <String, dynamic>{
        'name': 'Karen',
        'email': 'karen@example.com',
        'userId': 'cg-4',
        'firstName': 'Karen',
        'lastName': 'Lee',
        'phone': '555-0013',
        'dob': '04/01/1990',
        'gender': 'female',
        'caregiverType': 'Family Member',
        'address': <String, dynamic>{},
      };
      final cg = CaregiverModel.fromJson(json);

      expect(cg.professionalInfo, isNull);
      expect(cg.firstName, 'Karen');
    });

    test('fromJson with professional key populates professionalInfo', () {
      // Verifies deserialisation of a licensed caregiver with credentials.
      final json = <String, dynamic>{
        'name': 'Leo',
        'email': 'leo@example.com',
        'userId': 'cg-5',
        'firstName': 'Leo',
        'lastName': 'King',
        'phone': '555-0014',
        'dob': '08/08/1965',
        'gender': 'male',
        'caregiverType': 'Professional',
        'address': <String, dynamic>{},
        'professional': <String, dynamic>{
          'licenseNumber': 'MD-500',
          'issuingState': 'FL',
          'yearsExperience': 20,
        },
      };
      final cg = CaregiverModel.fromJson(json);

      expect(cg.professionalInfo, isNotNull);
      expect(cg.professionalInfo!.licenseNumber, 'MD-500');
      expect(cg.professionalInfo!.yearsExperience, 20);
    });

    test('fromJson defaults missing fields', () {
      // Verifies null-safety defaults for an empty caregiver payload.
      final cg = CaregiverModel.fromJson(<String, dynamic>{});

      expect(cg.name, '');
      expect(cg.email, '');
      expect(cg.userId, '');
      expect(cg.role, 'CAREGIVER');
      expect(cg.firstName, '');
      expect(cg.caregiverType, '');
      expect(cg.professionalInfo, isNull);
    });
  });
}
