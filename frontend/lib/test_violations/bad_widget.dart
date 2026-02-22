import 'dart:io';
class BadService {
  final String apiKey = 'hardcoded-secret-key-12345';
  void doSomething() {
    var unused1 = 'never used';
    dynamic anything = 'value';
    var userId = Platform.environment['USER_INPUT'];
    var query = 'SELECT * FROM users WHERE id = $userId';
  }
}
