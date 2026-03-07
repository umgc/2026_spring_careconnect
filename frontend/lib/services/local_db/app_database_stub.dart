/// Web-safe in-memory fallback for local DB APIs.
///
/// This file intentionally avoids importing Drift/SQLite packages so web builds
/// never compile native FFI storage code.
class Mood {
  const Mood({
    required this.id,
    required this.userId,
    required this.score,
    required this.label,
    required this.createdAt,
  });

  final int id;
  final int userId;
  final int score;
  final String label;
  final DateTime createdAt;
}

class AppDatabase {
  final List<Mood> _moods = <Mood>[];
  int _nextId = 1;

  Future<bool> isEncrypted() async => false;

  Future<int> insertMood({
    required int userId,
    required int score,
    required String label,
    DateTime? createdAt,
  }) async {
    final id = _nextId++;
    _moods.add(
      Mood(
        id: id,
        userId: userId,
        score: score,
        label: label,
        createdAt: createdAt ?? DateTime.now(),
      ),
    );
    return id;
  }

  Future<List<Mood>> getMoodsForUser(int userIdValue) async {
    return _moods.where((m) => m.userId == userIdValue).toList();
  }

  Future<List<Mood>> getMoodsForUserOldestFirst(int userIdValue) async {
    final rows = _moods.where((m) => m.userId == userIdValue).toList();
    rows.sort((a, b) => a.createdAt.compareTo(b.createdAt));
    return rows;
  }

  Future<Mood?> getMoodByIdForUser({
    required int moodId,
    required int userIdValue,
  }) async {
    for (final mood in _moods) {
      if (mood.id == moodId && mood.userId == userIdValue) {
        return mood;
      }
    }
    return null;
  }

  Future<int> deleteMoodById(int moodId) async {
    final before = _moods.length;
    _moods.removeWhere((m) => m.id == moodId);
    return before - _moods.length;
  }

  Future<void> deleteMoodsByIds(Iterable<int> ids) async {
    final idSet = ids.toSet();
    if (idSet.isEmpty) {
      return;
    }
    _moods.removeWhere((m) => idSet.contains(m.id));
  }

  Future<void> close() async {}
}
