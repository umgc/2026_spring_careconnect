import 'package:flutter_test/flutter_test.dart';
import 'package:care_connect_app/services/local_db/local_db_startup_stub.dart';

void main() {
  test('stub initializeLocalDbOnStartup completes without throwing', () async {
    await initializeLocalDbOnStartup();
  });
}
