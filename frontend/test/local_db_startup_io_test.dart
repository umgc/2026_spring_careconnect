import 'package:care_connect_app/services/local_db/local_db_startup_io.dart' as io_startup;
import 'package:flutter_test/flutter_test.dart';

import 'test_support/local_db_test_bindings.dart';

void main() {
  group('local_db_startup_io.dart', () {
    setUpAll(LocalDbTestBindings.install);
    tearDownAll(LocalDbTestBindings.uninstall);

    test('initializeLocalDbOnStartup can be called repeatedly', () async {
      await io_startup.initializeLocalDbOnStartup();
      await io_startup.initializeLocalDbOnStartup();
    });
  });
}