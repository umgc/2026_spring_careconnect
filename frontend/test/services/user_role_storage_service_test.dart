import 'package:care_connect_app/services/user_role_storage_service.dart';
import 'package:flutter_test/flutter_test.dart';
import 'package:shared_preferences/shared_preferences.dart';

void main() {
  TestWidgetsFlutterBinding.ensureInitialized();

  setUp(() async {
    // Replace the backing store so every test starts with a clean slate.
    SharedPreferences.setMockInitialValues({});
    // The singleton caches its SharedPreferences reference, so explicitly
    // clear persisted values between tests.
    await UserRoleStorageService.instance.clearUserData();
  });

  // --------------- singleton ---------------

  test('instance returns the same object on repeated calls', () {
    final a = UserRoleStorageService.instance;
    final b = UserRoleStorageService.instance;

    expect(identical(a, b), true);
  });

  // --------------- initialize ---------------

  test('initialize sets isInitialized to true', () async {
    final service = UserRoleStorageService.instance;
    await service.initialize();

    expect(service.isInitialized, true);
  });

  test('methods auto-initialize without explicit initialize call', () async {
    final service = UserRoleStorageService.instance;
    // Call a method directly without calling initialize() first.
    await service.setUserData(role: 'patient', userId: 1);

    expect(await service.getUserRole(), 'patient');
    expect(service.isInitialized, true);
  });

  // --------------- setUserData / getters ---------------

  test('setUserData stores role, userId, and login status', () async {
    final service = UserRoleStorageService.instance;

    await service.setUserData(role: 'patient', userId: 1);

    expect(await service.getUserRole(), 'patient');
    expect(await service.getUserId(), 1);
    expect(await service.isLoggedIn(), true);
  });

  test('setUserData stores optional patientId and caregiverId', () async {
    final service = UserRoleStorageService.instance;

    await service.setUserData(
      role: 'caregiver',
      userId: 2,
      patientId: 10,
      caregiverId: 20,
    );

    expect(await service.getPatientId(), 10);
    expect(await service.getCaregiverId(), 20);
  });

  test('setUserData without optional IDs leaves them null', () async {
    final service = UserRoleStorageService.instance;

    await service.setUserData(role: 'patient', userId: 1);

    expect(await service.getPatientId(), isNull);
    expect(await service.getCaregiverId(), isNull);
  });

  test('setUserData does not clear previously stored optional IDs', () async {
    final service = UserRoleStorageService.instance;

    // First call stores optional IDs.
    await service.setUserData(
      role: 'caregiver',
      userId: 1,
      patientId: 10,
      caregiverId: 20,
    );

    // Second call omits optional IDs — stale values remain in storage.
    await service.setUserData(role: 'patient', userId: 2);

    // The service does NOT remove old optional IDs when they are omitted,
    // so they survive across calls. This documents the current behaviour.
    expect(await service.getPatientId(), 10);
    expect(await service.getCaregiverId(), 20);

    final data = await service.getUserData();
    expect(data, isNotNull);
    expect(data!.role, 'patient');
    expect(data.userId, 2);
    expect(data.patientId, 10);
    expect(data.caregiverId, 20);
    expect(data.isLoggedIn, true);
  });

  test('setUserData overwrites previously stored values', () async {
    final service = UserRoleStorageService.instance;
    await service.setUserData(role: 'patient', userId: 1);

    await service.setUserData(role: 'caregiver', userId: 2, patientId: 99);

    expect(await service.getUserRole(), 'caregiver');
    expect(await service.getUserId(), 2);
    expect(await service.getPatientId(), 99);
  });

  // --------------- getters return null / false on empty store ---------------

  test('getUserRole returns null when no data is stored', () async {
    final service = UserRoleStorageService.instance;
    expect(await service.getUserRole(), isNull);
  });

  test('getUserId returns null when no data is stored', () async {
    final service = UserRoleStorageService.instance;
    expect(await service.getUserId(), isNull);
  });

  test('getPatientId returns null when no data is stored', () async {
    final service = UserRoleStorageService.instance;
    expect(await service.getPatientId(), isNull);
  });

  test('getCaregiverId returns null when no data is stored', () async {
    final service = UserRoleStorageService.instance;
    expect(await service.getCaregiverId(), isNull);
  });

  test('isLoggedIn returns false when no data is stored', () async {
    final service = UserRoleStorageService.instance;
    expect(await service.isLoggedIn(), false);
  });

  // --------------- getUserData ---------------

  test('getUserData returns null when not logged in', () async {
    final service = UserRoleStorageService.instance;
    expect(await service.getUserData(), isNull);
  });

  test('getUserData returns UserData when logged in', () async {
    final service = UserRoleStorageService.instance;
    await service.setUserData(
      role: 'patient',
      userId: 5,
      patientId: 50,
      caregiverId: 60,
    );

    final data = await service.getUserData();

    expect(data, isNotNull);
    expect(data!.role, 'patient');
    expect(data.userId, 5);
    expect(data.patientId, 50);
    expect(data.caregiverId, 60);
    expect(data.isLoggedIn, true);
  });

  test('getUserData returns null when role is missing', () async {
    final service = UserRoleStorageService.instance;
    // Store only userId and login flag without role.
    final prefs = await SharedPreferences.getInstance();
    await prefs.setInt('user_id', 1);
    await prefs.setBool('is_logged_in', true);

    expect(await service.getUserData(), isNull);
  });

  test('getUserData returns null when userId is missing', () async {
    final service = UserRoleStorageService.instance;
    final prefs = await SharedPreferences.getInstance();
    await prefs.setString('user_role', 'patient');
    await prefs.setBool('is_logged_in', true);

    expect(await service.getUserData(), isNull);
  });

  test('getUserData returns null when isLoggedIn is false even if role and userId exist', () async {
    final service = UserRoleStorageService.instance;
    final prefs = await SharedPreferences.getInstance();
    await prefs.setString('user_role', 'patient');
    await prefs.setInt('user_id', 1);
    await prefs.setBool('is_logged_in', false);

    expect(await service.getUserData(), isNull);
  });

  // --------------- clearUserData ---------------

  test('clearUserData removes all stored data and sets logged-in to false', () async {
    final service = UserRoleStorageService.instance;
    await service.setUserData(
      role: 'caregiver',
      userId: 3,
      patientId: 30,
      caregiverId: 40,
    );
    await service.storePatientModel('{"id":30}');
    await service.storeCaregiverModel('{"id":40}');

    await service.clearUserData();

    expect(await service.getUserRole(), isNull);
    expect(await service.getUserId(), isNull);
    expect(await service.getPatientId(), isNull);
    expect(await service.getCaregiverId(), isNull);
    expect(await service.isLoggedIn(), false);
    expect(await service.getPatientModel(), isNull);
    expect(await service.getCaregiverModel(), isNull);
  });

  // --------------- updateUserRole ---------------

  test('updateUserRole changes only the role', () async {
    final service = UserRoleStorageService.instance;
    await service.setUserData(
      role: 'patient',
      userId: 1,
      patientId: 10,
      caregiverId: 20,
    );
    await service.storePatientModel('{"id":10}');
    await service.storeCaregiverModel('{"id":20}');

    await service.updateUserRole('caregiver');

    expect(await service.getUserRole(), 'caregiver');
    expect(await service.getUserId(), 1);
    expect(await service.getPatientId(), 10);
    expect(await service.getCaregiverId(), 20);
    expect(await service.isLoggedIn(), true);
    expect(await service.getPatientModel(), '{"id":10}');
    expect(await service.getCaregiverModel(), '{"id":20}');
  });

  // --------------- updatePatientId ---------------

  test('updatePatientId sets patient ID', () async {
    final service = UserRoleStorageService.instance;
    await service.setUserData(
      role: 'caregiver',
      userId: 2,
      caregiverId: 20,
    );
    await service.storePatientModel('{"id":5}');
    await service.storeCaregiverModel('{"id":20}');

    await service.updatePatientId(99);

    expect(await service.getPatientId(), 99);
    expect(await service.getUserRole(), 'caregiver');
    expect(await service.getUserId(), 2);
    expect(await service.getCaregiverId(), 20);
    expect(await service.isLoggedIn(), true);
    expect(await service.getPatientModel(), '{"id":5}');
    expect(await service.getCaregiverModel(), '{"id":20}');
  });

  test('updatePatientId removes patient ID when null', () async {
    final service = UserRoleStorageService.instance;
    await service.setUserData(
      role: 'caregiver',
      userId: 2,
      patientId: 99,
      caregiverId: 20,
    );
    await service.storePatientModel('{"id":99}');
    await service.storeCaregiverModel('{"id":20}');

    await service.updatePatientId(null);

    expect(await service.getPatientId(), isNull);
    expect(await service.getUserRole(), 'caregiver');
    expect(await service.getUserId(), 2);
    expect(await service.getCaregiverId(), 20);
    expect(await service.isLoggedIn(), true);
    expect(await service.getPatientModel(), '{"id":99}');
    expect(await service.getCaregiverModel(), '{"id":20}');
  });

  // --------------- patient / caregiver model storage ---------------

  test('storePatientModel and getPatientModel round-trip', () async {
    final service = UserRoleStorageService.instance;
    const json = '{"id":1,"name":"Alice"}';

    await service.storePatientModel(json);

    expect(await service.getPatientModel(), json);
  });

  test('storeCaregiverModel and getCaregiverModel round-trip', () async {
    final service = UserRoleStorageService.instance;
    const json = '{"id":2,"name":"Bob"}';

    await service.storeCaregiverModel(json);

    expect(await service.getCaregiverModel(), json);
  });

  test('getPatientModel returns null when not stored', () async {
    final service = UserRoleStorageService.instance;
    expect(await service.getPatientModel(), isNull);
  });

  test('getCaregiverModel returns null when not stored', () async {
    final service = UserRoleStorageService.instance;
    expect(await service.getCaregiverModel(), isNull);
  });

  // --------------- UserData ---------------

  test('UserData toString produces expected format', () {
    const data = UserData(
      role: 'patient',
      userId: 1,
      patientId: 10,
      caregiverId: null,
      isLoggedIn: true,
    );

    expect(
      data.toString(),
      'UserData(role: patient, userId: 1, patientId: 10, caregiverId: null, isLoggedIn: true)',
    );
  });

  test('UserData copyWith creates modified copy', () {
    const original = UserData(
      role: 'patient',
      userId: 1,
      patientId: 10,
      isLoggedIn: true,
    );

    final updated = original.copyWith(role: 'caregiver', userId: 2);

    expect(updated.role, 'caregiver');
    expect(updated.userId, 2);
    expect(updated.patientId, 10);
    expect(updated.isLoggedIn, true);
  });

  test('UserData copyWith with no arguments returns equivalent copy', () {
    const original = UserData(
      role: 'patient',
      userId: 1,
      patientId: 10,
      caregiverId: 20,
      isLoggedIn: true,
    );

    final copy = original.copyWith();

    expect(copy.role, original.role);
    expect(copy.userId, original.userId);
    expect(copy.patientId, original.patientId);
    expect(copy.caregiverId, original.caregiverId);
    expect(copy.isLoggedIn, original.isLoggedIn);
  });
}
