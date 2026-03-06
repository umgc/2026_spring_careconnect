import 'local_db_startup_stub.dart'
    if (dart.library.html) 'local_db_startup_web.dart'
    if (dart.library.io) 'local_db_startup_io.dart' as impl;

/// Platform-aware startup hook for local DB initialization.
///
/// Uses IO implementation on mobile/desktop and a no-op stub elsewhere.
Future<void> initializeLocalDbOnStartup() {
  return impl.initializeLocalDbOnStartup();
}
