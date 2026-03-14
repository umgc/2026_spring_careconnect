import 'offline_sync_row.dart';

class AppDatabase {
  final List<OfflineSyncDbRow> _queue = <OfflineSyncDbRow>[];

  Future<bool> isEncrypted() async => false;

  Future<void> ensureOfflineSyncTable() async {}

  Future<String> upsertOfflineSyncOperation({
    required String id,
    required String method,
    required String url,
    required String headersJson,
    String? bodyJson,
    required String createdAtIso,
    required String fingerprint,
  }) async {
    final existing = _queue.where((item) => item.fingerprint == fingerprint).toList();

    if (existing.isNotEmpty) {
      return existing.first.id;
    }

    _queue.add(
      OfflineSyncDbRow(
        id: id,
        fingerprint: fingerprint,
        method: method,
        url: url,
        headersJson: headersJson,
        bodyJson: bodyJson,
        createdAt: DateTime.tryParse(createdAtIso) ?? DateTime.now().toUtc(),
        status: 'pending',
        retryCount: 0,
        lastError: null,
      ),
    );
    _queue.sort((a, b) => a.createdAt.compareTo(b.createdAt));
    return id;
  }

  Future<List<OfflineSyncDbRow>> getPendingOfflineSyncQueue({
    int limit = 200,
  }) async {
    final rows = _queue.where((item) {
      return item.status == 'pending' ||
          item.status == 'failed' ||
          item.status == 'syncing';
    }).toList()
      ..sort((a, b) => a.createdAt.compareTo(b.createdAt));
    return rows.take(limit).toList();
  }

  Future<int> getPendingOfflineSyncCount() async {
    return _queue
        .where((item) =>
            item.status == 'pending' ||
            item.status == 'failed' ||
            item.status == 'syncing')
        .length;
  }

  Future<OfflineSyncDbRow?> getOfflineSyncById(String id) async {
    for (final item in _queue) {
      if (item.id == id) {
        return item;
      }
    }
    return null;
  }

  Future<void> markOfflineSyncAsSyncing(String id) async {
    final index = _queue.indexWhere((item) => item.id == id);
    if (index < 0) {
      return;
    }
    final item = _queue[index];
    _queue[index] = OfflineSyncDbRow(
      id: item.id,
      fingerprint: item.fingerprint,
      method: item.method,
      url: item.url,
      headersJson: item.headersJson,
      bodyJson: item.bodyJson,
      createdAt: item.createdAt,
      status: 'syncing',
      retryCount: item.retryCount,
      lastError: item.lastError,
    );
  }

  Future<void> markOfflineSyncAsFailed({
    required String id,
    required String errorMessage,
  }) async {
    final index = _queue.indexWhere((item) => item.id == id);
    if (index < 0) {
      return;
    }
    final item = _queue[index];
    _queue[index] = OfflineSyncDbRow(
      id: item.id,
      fingerprint: item.fingerprint,
      method: item.method,
      url: item.url,
      headersJson: item.headersJson,
      bodyJson: item.bodyJson,
      createdAt: item.createdAt,
      status: 'failed',
      retryCount: item.retryCount + 1,
      lastError: errorMessage,
    );
  }

  Future<void> deleteOfflineSyncById(String id) async {
    _queue.removeWhere((item) => item.id == id);
  }

  Future<void> close() async {}

  Future<void> closeDb() async {
    await close();
  }
}
