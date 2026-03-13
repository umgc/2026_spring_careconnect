import 'app_database.dart';

/// Shared app-level database instance used during startup initialization.
AppDatabase? _startupDb;

/// Ensures configured offline tables exist on platforms supporting `dart:io`.
Future<void> initializeLocalDbOnStartup() async {
  _startupDb ??= AppDatabase();
  final db = _startupDb!;
  // Opening a connection once at startup triggers Drift's built-in table
  // creation/migration strategy for all tables declared in AppDatabase.
  await db.customSelect('SELECT 1').getSingle();
}
