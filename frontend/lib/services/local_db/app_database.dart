import 'dart:io';

import 'package:drift/drift.dart';
import 'package:drift/native.dart';
import 'package:path/path.dart' as p;
import 'package:path_provider/path_provider.dart';
import 'package:sqlcipher_flutter_libs/sqlcipher_flutter_libs.dart';
import 'package:sqlite3/open.dart';

import 'db_encryption_service.dart';
import 'generated/jpa_drift_bundle.dart';

part 'app_database.g.dart';

/// Encrypted Drift database used by mobile offline storage.
///
/// Table definitions are generated from backend JPA models and imported from
/// `generated/jpa_drift_bundle.dart`.
@DriftDatabase(tables: [Moods, Tasks])
class AppDatabase extends _$AppDatabase {
  AppDatabase({DbEncryptionService? encryptionService})
      : _encryptionService = encryptionService ?? DbEncryptionService(),
        super(_openConnection(encryptionService ?? DbEncryptionService()));

  final DbEncryptionService _encryptionService;

  @override
  int get schemaVersion => 1;

  /// Indicates whether an encryption key exists in secure storage.
  Future<bool> isEncrypted() async {
    return _encryptionService.hasEncryptionKey();
  }

  /// Inserts one mood row into the local encrypted database.
  Future<int> insertMood({
    required int userId,
    required int score,
    required String label,
    DateTime? createdAt,
  }) {
    return into(moods).insert(
      MoodsCompanion.insert(
        userId: userId,
        score: score,
        label: label,
        createdAt: Value(createdAt ?? DateTime.now()),
      ),
    );
  }

  /// Returns moods for one user, newest first.
  Future<List<Mood>> getMoodsForUser(int userIdValue) {
    final query = select(moods)
      ..where((tbl) => tbl.userId.equals(userIdValue))
      ..orderBy([(tbl) => OrderingTerm.desc(tbl.createdAt)]);
    return query.get();
  }

  /// Returns moods for one user, oldest first.
  ///
  /// This order is useful when presenting a queue where the oldest unsynced
  /// item should be processed first.
  Future<List<Mood>> getMoodsForUserOldestFirst(int userIdValue) {
    final query = select(moods)
      ..where((tbl) => tbl.userId.equals(userIdValue))
      ..orderBy([(tbl) => OrderingTerm.asc(tbl.createdAt)]);
    return query.get();
  }

  /// Returns one mood row by local primary key for a specific user.
  Future<Mood?> getMoodByIdForUser({
    required int moodId,
    required int userIdValue,
  }) {
    final query = select(moods)
      ..where((tbl) => tbl.id.equals(moodId))
      ..where((tbl) => tbl.userId.equals(userIdValue))
      ..limit(1);
    return query.getSingleOrNull();
  }

  /// Deletes one mood row by local primary key.
  Future<int> deleteMoodById(int moodId) {
    return (delete(moods)..where((tbl) => tbl.id.equals(moodId))).go();
  }

  /// Deletes mood rows by primary key.
  Future<void> deleteMoodsByIds(Iterable<int> ids) async {
    final values = ids.toList();
    if (values.isEmpty) {
      return;
    }
    await (delete(moods)..where((tbl) => tbl.id.isIn(values))).go();
  }
}

/// Opens the encrypted sqlite database and applies SQLCipher keying.
QueryExecutor _openConnection(DbEncryptionService encryptionService) {
  return LazyDatabase(() async {
    // SQLCipher requires overriding sqlite loader on Android.
    if (Platform.isAndroid) {
      open.overrideFor(OperatingSystem.android, openCipherOnAndroid);
      await applyWorkaroundToOpenSqlCipherOnOldAndroidVersions();
    }

    final dir = await getApplicationDocumentsDirectory();
    final file = File(p.join(dir.path, 'careconnect_mobile.sqlite'));
    final encryptionKey = await encryptionService.getOrCreateEncryptionKey();

    return NativeDatabase(
      file,
      setup: (database) {
        final escaped = encryptionService.escapeForPragma(encryptionKey);
        database.execute("PRAGMA key = '$escaped';");
        database.execute('PRAGMA foreign_keys = ON;');
      },
    );
  });
}
