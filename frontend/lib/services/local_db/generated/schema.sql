-- Auto-generated from JPA entities.
-- Input: ../backend/core/src/main/java/com/careconnect/model

-- Source: ../backend/core/src/main/java/com/careconnect/model\Mood.java
CREATE TABLE IF NOT EXISTS moods (
  id INTEGER PRIMARY KEY AUTOINCREMENT,
  userId INTEGER NOT NULL,
  score INTEGER NOT NULL,
  label TEXT NOT NULL,
  createdAt TEXT NOT NULL
);

-- Source: ../backend/core/src/main/java/com/careconnect/model\Task.java
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
