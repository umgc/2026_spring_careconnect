import 'app_database.dart';

/// Shared app-level database instance used during startup initialization.
AppDatabase? _startupDb;

/// Ensures configured offline tables exist on platforms supporting `dart:io`.
Future<void> initializeLocalDbOnStartup() async {
  _startupDb ??= AppDatabase();
  final db = _startupDb!;
  await db.ensureOfflineSyncTable();
}
