// GENERATED CODE - DO NOT MODIFY BY HAND
// Source input: ../backend/core/src/main/java/com/careconnect/model
// Run: dart run tool/generate_sql_from_jpa.dart ...

import 'package:drift/drift.dart';

// Source: ../backend/core/src/main/java/com/careconnect/model\Mood.java
class Moods extends Table {
  IntColumn get id => integer().autoIncrement()();
  IntColumn get userId => integer()();
  IntColumn get score => integer()();
  TextColumn get label => text()();
  DateTimeColumn get createdAt => dateTime().withDefault(currentDateAndTime)();
}

// Source: ../backend/core/src/main/java/com/careconnect/model\Task.java
class Tasks extends Table {
  IntColumn get id => integer().autoIncrement()();
  IntColumn get patient => integer().named('patient_id').nullable()();
  TextColumn get name => text().nullable()();
  TextColumn get description => text().nullable()();
  TextColumn get date => text().nullable()();
  TextColumn get timeOfDay => text().nullable()();
  BoolColumn get isCompleted => boolean().nullable()();
  TextColumn get taskType => text().nullable()();
  TextColumn get frequency => text().nullable()();
  IntColumn get taskInterval => integer().nullable()();
  IntColumn get doCount => integer().nullable()();
  TextColumn get daysOfWeek => text().nullable()();
  IntColumn get createdAt => integer().nullable()();
  IntColumn get parentTaskId => integer().nullable()();
}

const Map<String, String> generatedCreateTableSql = {
  'moods': '''
CREATE TABLE IF NOT EXISTS moods (
  id INTEGER PRIMARY KEY AUTOINCREMENT,
  userId INTEGER NOT NULL,
  score INTEGER NOT NULL,
  label TEXT NOT NULL,
  createdAt TEXT NOT NULL
);
''',
  'tasks': '''
CREATE TABLE IF NOT EXISTS tasks (
  id INTEGER PRIMARY KEY AUTOINCREMENT,
  patient_id INTEGER,
  name TEXT,
  description TEXT,
  date TEXT,
  timeOfDay TEXT,
  isCompleted INTEGER,
  taskType TEXT,
  frequency TEXT,
  taskInterval INTEGER,
  doCount INTEGER,
  daysOfWeek TEXT,
  createdAt INTEGER,
  parentTaskId INTEGER
);
''',
};

String? generatedCreateTableFor(String tableName) {
  return generatedCreateTableSql[tableName.toLowerCase()];
}
