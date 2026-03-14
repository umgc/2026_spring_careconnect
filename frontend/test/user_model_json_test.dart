import 'package:flutter_test/flutter_test.dart';
import 'package:care_connect_app/models/user_model.dart';

void main() {
  group('UserModel JSON Tests', () {
    test('UserModel.fromJson creates correct instance', () {
      final json = {
        'name': 'John Doe',
        'email': 'john@example.com',
        'userId': '123',
        'role': 'CAREGIVER',
      };

      final user = UserModel.fromJson(json);

      expect(user.name, 'John Doe');
      expect(user.email, 'john@example.com');
      expect(user.userId, '123');
      expect(user.role, 'CAREGIVER');
    });

    test('UserModel.toJson serializes correctly', () {
      final user = UserModel(
        name: 'Jane Doe',
        email: 'jane@example.com',
        userId: '456',
        role: 'PATIENT',
      );

      final json = user.toJson();

      expect(json['name'], 'Jane Doe');
      expect(json['email'], 'jane@example.com');
      expect(json['userId'], '456');
      expect(json['role'], 'PATIENT');
    });

    test('UserModel.fromJson handles missing fields with defaults', () {
      final json = <String, dynamic>{};

      final user = UserModel.fromJson(json);

      expect(user.name, '');
      expect(user.email, '');
      expect(user.userId, '');
      expect(user.role, '');
    });
  });
}