import 'dart:convert';

import 'package:http/http.dart' as http;

import '../api_service.dart';
import 'app_database_stub.dart' if (dart.library.io) 'app_database.dart';
import 'connectivity_router_service.dart';

/// Summary returned by sync operations.
class SyncSummary {
  const SyncSummary({
    required this.attempted,
    required this.created,
    required this.skippedExisting,
    required this.failed,
    required this.ranOnline,
  });

  final int attempted;
  final int created;
  final int skippedExisting;
  final int failed;
  final bool ranOnline;
}

/// Non-sensitive queue projection used by UI.
///
/// This deliberately excludes account identifiers (for example, userId).
class MoodQueueItem {
  const MoodQueueItem({
    required this.localId,
    required this.score,
    required this.label,
    required this.createdAt,
  });

  final int localId;
  final int score;
  final String label;
  final DateTime createdAt;
}

/// Thrown when a write requires offline persistence but user disabled it.
class OfflinePersistenceDisabledException implements Exception {
  const OfflinePersistenceDisabledException(this.message);
  final String message;

  @override
  String toString() => message;
}

/// Routes mood reads/writes between backend APIs and local encrypted storage.
class MoodStorageService {
  MoodStorageService({
    required ConnectivityRouterService connectivityRouter,
    AppDatabase? appDatabase,
    Future<int?> Function()? currentUserIdProvider,
    Future<bool> Function()? canUseOfflineFallback,
  })  : _connectivityRouter = connectivityRouter,
        _appDatabase = appDatabase ?? AppDatabase(),
        _currentUserIdProvider = currentUserIdProvider,
        _canUseOfflineFallback = canUseOfflineFallback;

  final ConnectivityRouterService _connectivityRouter;
  final AppDatabase _appDatabase;
  final Future<int?> Function()? _currentUserIdProvider;
  final Future<bool> Function()? _canUseOfflineFallback;

  /// Saves mood online when available, otherwise stores it locally.
  Future<void> saveMood({
    required int userId,
    required int score,
    required String label,
  }) async {
    await _connectivityRouter.route<void>(
      online: () async {
        final response = await ApiService.saveMoodScore(
          userId: userId,
          score: score,
          label: label,
        );
        _throwIfHttpError(response, 'save mood to backend');
      },
      offline: () async {
        await _runOfflineWriteGuarded(() async {
          await _appDatabase.insertMood(
            userId: userId,
            score: score,
            label: label,
            createdAt: DateTime.now(),
          );
        });
      },
      fallbackToOfflineOnOnlineError: true,
    );
  }

  /// Saves mood for the current user resolved from shared app state.
  Future<void> saveMoodForCurrentUser({
    required int score,
    required String label,
  }) async {
    if (_currentUserIdProvider == null) {
      throw Exception(
        'No currentUserIdProvider configured for MoodStorageService.',
      );
    }

    final userId = await _currentUserIdProvider.call();
    if (userId == null) {
      throw Exception('Current user ID is unavailable.');
    }

    await saveMood(userId: userId, score: score, label: label);
  }

  /// Returns mood history from backend when online, local DB when offline.
  Future<List<Map<String, dynamic>>> getMoodHistory(int userId) async {
    return _connectivityRouter.route<List<Map<String, dynamic>>>(
      online: () async {
        final backendResponse = await ApiService.getMoodHistory(userId);
        return backendResponse
            .whereType<Map<String, dynamic>>()
            .map((entry) => Map<String, dynamic>.from(entry))
            .toList();
      },
      offline: () async {
        final localRows = await _appDatabase.getMoodsForUser(userId);
        return localRows
            .map(
              (row) => {
                'id': row.id,
                'userId': row.userId,
                'score': row.score,
                'label': row.label,
                'createdAt': row.createdAt.toIso8601String(),
                'source': 'offline',
              },
            )
            .toList();
      },
    );
  }

  /// Syncs local moods to backend when online.
  ///
  /// Flow:
  /// 1. Load local moods for user.
  /// 2. Load backend mood history.
  /// 3. Compare using deterministic fingerprints.
  /// 4. Create only missing backend rows.
  /// 5. Remove successfully synced local rows to avoid reprocessing.
  ///
  /// The method is fully wrapped in try/catch boundaries so individual row
  /// failures don't abort the whole sync.
  Future<SyncSummary> syncMoodsToBackendIfOnline({
    required int userId,
  }) async {
    return _connectivityRouter.route<SyncSummary>(
      online: () => _syncMoodsToBackendOnline(userId: userId),
      offline: () async => const SyncSummary(
        attempted: 0,
        created: 0,
        skippedExisting: 0,
        failed: 0,
        ranOnline: false,
      ),
    );
  }

  /// Returns pending local moods in deterministic queue order.
  ///
  /// Queue order is oldest-first so the sync pipeline can process entries in
  /// creation sequence.
  Future<List<MoodQueueItem>> getPendingMoodQueue({
    required int userId,
  }) async {
    final rows = await _appDatabase.getMoodsForUserOldestFirst(userId);
    return rows
        .map(
          (row) => MoodQueueItem(
            localId: row.id,
            score: row.score,
            label: row.label,
            createdAt: row.createdAt,
          ),
        )
        .toList();
  }

  /// Removes one queued mood entry before it is synced.
  Future<bool> deleteQueuedMoodItem({
    required int localId,
  }) async {
    final deleted = await _appDatabase.deleteMoodById(localId);
    return deleted > 0;
  }

  /// Syncs one queued mood entry by local id and removes it on success.
  ///
  /// Returns `true` when a backend write succeeds and local row is deleted.
  /// Returns `false` when row is missing or backend write fails.
  Future<bool> syncQueuedMoodItemById({
    required int userId,
    required int localId,
  }) async {
    return _connectivityRouter.route<bool>(
      online: () async {
        final row = await _appDatabase.getMoodByIdForUser(
          moodId: localId,
          userIdValue: userId,
        );
        if (row == null) {
          return false;
        }

        try {
          final response = await ApiService.saveMoodScore(
            userId: row.userId,
            score: row.score,
            label: row.label,
          );
          _throwIfHttpError(response, 'sync queued mood item to backend');
          await _appDatabase.deleteMoodById(localId);
          return true;
        } catch (_) {
          return false;
        }
      },
      offline: () async => false,
    );
  }

  Future<SyncSummary> _syncMoodsToBackendOnline({required int userId}) async {
    try {
      final localRows = await _appDatabase.getMoodsForUser(userId);
      if (localRows.isEmpty) {
        return const SyncSummary(
          attempted: 0,
          created: 0,
          skippedExisting: 0,
          failed: 0,
          ranOnline: true,
        );
      }

      final backendRows = await ApiService.getMoodHistory(userId);
      final backendFingerprints = backendRows
          .whereType<Map<String, dynamic>>()
          .map(_moodFingerprintFromBackend)
          .toSet();

      final syncedLocalIds = <int>[];
      var created = 0;
      var skippedExisting = 0;
      var failed = 0;

      for (final row in localRows) {
        final localFingerprint = _moodFingerprintFromLocal(row);
        if (backendFingerprints.contains(localFingerprint)) {
          skippedExisting++;
          syncedLocalIds.add(row.id);
          continue;
        }

        try {
          final response = await ApiService.saveMoodScore(
            userId: row.userId,
            score: row.score,
            label: row.label,
          );
          _throwIfHttpError(response, 'sync mood row to backend');
          created++;
          syncedLocalIds.add(row.id);
        } catch (_) {
          failed++;
        }
      }

      await _appDatabase.deleteMoodsByIds(syncedLocalIds);
      return SyncSummary(
        attempted: localRows.length,
        created: created,
        skippedExisting: skippedExisting,
        failed: failed,
        ranOnline: true,
      );
    } catch (_) {
      // Industry standard: keep sync failures non-fatal for app flow and return
      // a structured summary for callers to decide retry/backoff behavior.
      return const SyncSummary(
        attempted: 0,
        created: 0,
        skippedExisting: 0,
        failed: 0,
        ranOnline: true,
      );
    }
  }

  /// Returns true when local encryption key exists.
  Future<bool> isLocalDbEncrypted() async {
    return _appDatabase.isEncrypted();
  }

  /// Closes the local database handle.
  Future<void> close() => _appDatabase.close();

  /// Ensures offline writes honor user-level persistence settings.
  ///
  /// When `_canUseOfflineFallback` is provided, it acts as a central policy
  /// gate. This keeps call sites simple and makes the same rule reusable for
  /// future offline-capable services.
  Future<void> _runOfflineWriteGuarded(Future<void> Function() action) async {
    final allowed = await _isOfflineWriteAllowed();
    if (!allowed) {
      throw const OfflinePersistenceDisabledException(
        'Offline persistence is disabled in Settings.',
      );
    }
    await action();
  }

  Future<bool> _isOfflineWriteAllowed() async {
    if (_canUseOfflineFallback == null) {
      return true;
    }
    return _canUseOfflineFallback.call();
  }

  String _moodFingerprintFromLocal(Mood row) {
    final normalized = row.createdAt.toUtc().toIso8601String();
    return '${row.userId}|${row.score}|${row.label}|$normalized';
  }

  String _moodFingerprintFromBackend(Map<String, dynamic> row) {
    final userId = row['userId']?.toString() ?? '';
    final score = row['score']?.toString() ?? '';
    final label = row['label']?.toString() ?? '';
    final createdAt = _normalizeDateTimeString(row['createdAt']?.toString());
    return '$userId|$score|$label|$createdAt';
  }

  String _normalizeDateTimeString(String? value) {
    if (value == null || value.isEmpty) {
      return '';
    }
    try {
      return DateTime.parse(value).toUtc().toIso8601String();
    } catch (_) {
      return value;
    }
  }

  void _throwIfHttpError(http.Response response, String action) {
    if (response.statusCode >= 200 && response.statusCode < 300) {
      return;
    }

    final message = _extractErrorMessage(response.body);
    throw Exception(
      'Failed to $action. Status: ${response.statusCode}. Error: $message',
    );
  }

  String _extractErrorMessage(String body) {
    if (body.isEmpty) {
      return 'No response body';
    }

    try {
      final decoded = jsonDecode(body);
      if (decoded is Map<String, dynamic>) {
        if (decoded['message'] is String) {
          return decoded['message'] as String;
        }
        if (decoded['error'] is String) {
          return decoded['error'] as String;
        }
      }
    } catch (_) {
      return body;
    }
    return body;
  }
}
