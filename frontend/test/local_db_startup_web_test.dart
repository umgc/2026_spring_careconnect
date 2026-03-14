import 'package:care_connect_app/services/local_db/local_db_startup_web.dart';
import 'package:flutter_test/flutter_test.dart';

void main() {
  group('local_db_startup_web.dart', () {
    test('initializeLocalDbOnStartup is a no-op that completes', () async {
      await initializeLocalDbOnStartup();
    });
  });
}