import 'package:flutter_test/flutter_test.dart';
import 'package:care_connect_app/services/local_db/local_db_startup_web.dart';

void main() {
  test('web initializeLocalDbOnStartup completes without throwing', () async {
    await initializeLocalDbOnStartup();
  });
}
