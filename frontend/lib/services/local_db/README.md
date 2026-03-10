Encrypted Drift Local DB (Mobile)

Files:
- `db_encryption_service.dart`: Reusable DB key generation + key lookup.
- `app_database.dart`: Encrypted Drift DB with `moods` table.
- `connectivity_router_service.dart`: Generic online/offline router (no connectivity detection inside).
- `mood_storage_service.dart`: Uses router to decide backend vs local DB for mood operations.
- `create_table.dart`: Reusable lookup for generated `CREATE TABLE` statements.
- `generated/jpa_drift_bundle.dart`: Generated Drift table classes + SQL map.
- `offline_table_config.dart`: List of backend tables enabled for offline mode.
- `local_db_startup.dart`: Startup hook that creates enabled offline tables.

Quick usage:

```dart
final router = ConnectivityRouterService(
  isOnline: () => myConnectivityService.isOnline(),
);

final moodStorage = MoodStorageService(connectivityRouter: router);

await moodStorage.saveMood(userId: 10, score: 4, label: 'Calm');
final moods = await moodStorage.getMoodHistory(10);
```

Generate Drift files:

```bash
flutter pub run build_runner build --delete-conflicting-outputs
```

Generate Drift + SQL from backend JPA model(s):

```bash
dart run tool/generate_sql_from_jpa.dart --input ../backend/core/src/main/java/com/careconnect/model --entities Mood
```

Windows shortcut commands:

```powershell
.\tool\update-offline-schema.ps1 "Mood,Task,ChatMessage"
```

```bat
tool\update-offline-schema.bat Mood,Task,ChatMessage
```

Generate for multiple future offline tables:

```bash
dart run tool/generate_sql_from_jpa.dart --input ../backend/core/src/main/java/com/careconnect/model --entities Mood,Task,User
```

Generated outputs (default):

```bash
lib/services/local_db/generated/schema.sql
lib/services/local_db/generated/jpa_drift_bundle.dart
```

Use generated create table SQL in app code:

```dart
final moodCreateSql = CreateTable.forTable('moods');
final allCreateSql = CreateTable.all();
```

Enable tables for startup creation:

```dart
// offline_table_config.dart
const Set<String> offlineEnabledTables = {'moods'};
// later: {'moods', 'tasks', 'user_profiles'}
```
