import 'dart:io';
class BadService {
  final String apiKey = 'hardcoded-secret-key-12345';
  final String password = 'supersecretpassword';
  void doSomething() {
    var unused1 = 'never used';
    dynamic anything = 'value';
    print(anything.nonExistentMethod());
    var serverUrl = 'http://192.168.1.100:8080/api';
    var userId = Platform.environment['USER_INPUT'];
    var query = 'SELECT * FROM users WHERE id = $userId';
  }
}
