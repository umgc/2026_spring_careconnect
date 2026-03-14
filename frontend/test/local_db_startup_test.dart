import 'package:care_connect_app/services/local_db/local_db_startup.dart';
import 'package:flutter_test/flutter_test.dart';

import 'test_support/local_db_test_bindings.dart';

void main() {
  group('local_db_startup.dart', () {
    setUpAll(LocalDbTestBindings.install);
    tearDownAll(LocalDbTestBindings.uninstall);

    test('delegates to platform startup implementation without throwing', () async {
      await initializeLocalDbOnStartup();
    });
  });
}