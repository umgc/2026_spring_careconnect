// GENERATED CODE - DO NOT MODIFY BY HAND

part of 'app_database.dart';

// ignore_for_file: type=lint
class $MoodsTable extends Moods with TableInfo<$MoodsTable, Mood> {
  @override
  final GeneratedDatabase attachedDatabase;
  final String? _alias;
  $MoodsTable(this.attachedDatabase, [this._alias]);
  static const VerificationMeta _idMeta = const VerificationMeta('id');
  @override
  late final GeneratedColumn<int> id = GeneratedColumn<int>(
    'id',
    aliasedName,
    false,
    hasAutoIncrement: true,
    type: DriftSqlType.int,
    requiredDuringInsert: false,
    defaultConstraints: GeneratedColumn.constraintIsAlways(
      'PRIMARY KEY AUTOINCREMENT',
    ),
  );
  static const VerificationMeta _userIdMeta = const VerificationMeta('userId');
  @override
  late final GeneratedColumn<int> userId = GeneratedColumn<int>(
    'user_id',
    aliasedName,
    false,
    type: DriftSqlType.int,
    requiredDuringInsert: true,
  );
  static const VerificationMeta _scoreMeta = const VerificationMeta('score');
  @override
  late final GeneratedColumn<int> score = GeneratedColumn<int>(
    'score',
    aliasedName,
    false,
    type: DriftSqlType.int,
    requiredDuringInsert: true,
  );
  static const VerificationMeta _labelMeta = const VerificationMeta('label');
  @override
  late final GeneratedColumn<String> label = GeneratedColumn<String>(
    'label',
    aliasedName,
    false,
    type: DriftSqlType.string,
    requiredDuringInsert: true,
  );
  static const VerificationMeta _createdAtMeta = const VerificationMeta(
    'createdAt',
  );
  @override
  late final GeneratedColumn<DateTime> createdAt = GeneratedColumn<DateTime>(
    'created_at',
    aliasedName,
    false,
    type: DriftSqlType.dateTime,
    requiredDuringInsert: false,
    defaultValue: currentDateAndTime,
  );
  @override
  List<GeneratedColumn> get $columns => [id, userId, score, label, createdAt];
  @override
  String get aliasedName => _alias ?? actualTableName;
  @override
  String get actualTableName => $name;
  static const String $name = 'moods';
  @override
  VerificationContext validateIntegrity(
    Insertable<Mood> instance, {
    bool isInserting = false,
  }) {
    final context = VerificationContext();
    final data = instance.toColumns(true);
    if (data.containsKey('id')) {
      context.handle(_idMeta, id.isAcceptableOrUnknown(data['id']!, _idMeta));
    }
    if (data.containsKey('user_id')) {
      context.handle(
        _userIdMeta,
        userId.isAcceptableOrUnknown(data['user_id']!, _userIdMeta),
      );
    } else if (isInserting) {
      context.missing(_userIdMeta);
    }
    if (data.containsKey('score')) {
      context.handle(
        _scoreMeta,
        score.isAcceptableOrUnknown(data['score']!, _scoreMeta),
      );
    } else if (isInserting) {
      context.missing(_scoreMeta);
    }
    if (data.containsKey('label')) {
      context.handle(
        _labelMeta,
        label.isAcceptableOrUnknown(data['label']!, _labelMeta),
      );
    } else if (isInserting) {
      context.missing(_labelMeta);
    }
    if (data.containsKey('created_at')) {
      context.handle(
        _createdAtMeta,
        createdAt.isAcceptableOrUnknown(data['created_at']!, _createdAtMeta),
      );
    }
    return context;
  }

  @override
  Set<GeneratedColumn> get $primaryKey => {id};
  @override
  Mood map(Map<String, dynamic> data, {String? tablePrefix}) {
    final effectivePrefix = tablePrefix != null ? '$tablePrefix.' : '';
    return Mood(
      id: attachedDatabase.typeMapping.read(
        DriftSqlType.int,
        data['${effectivePrefix}id'],
      )!,
      userId: attachedDatabase.typeMapping.read(
        DriftSqlType.int,
        data['${effectivePrefix}user_id'],
      )!,
      score: attachedDatabase.typeMapping.read(
        DriftSqlType.int,
        data['${effectivePrefix}score'],
      )!,
      label: attachedDatabase.typeMapping.read(
        DriftSqlType.string,
        data['${effectivePrefix}label'],
      )!,
      createdAt: attachedDatabase.typeMapping.read(
        DriftSqlType.dateTime,
        data['${effectivePrefix}created_at'],
      )!,
    );
  }

  @override
  $MoodsTable createAlias(String alias) {
    return $MoodsTable(attachedDatabase, alias);
  }
}

class Mood extends DataClass implements Insertable<Mood> {
  final int id;
  final int userId;
  final int score;
  final String label;
  final DateTime createdAt;
  const Mood({
    required this.id,
    required this.userId,
    required this.score,
    required this.label,
    required this.createdAt,
  });
  @override
  Map<String, Expression> toColumns(bool nullToAbsent) {
    final map = <String, Expression>{};
    map['id'] = Variable<int>(id);
    map['user_id'] = Variable<int>(userId);
    map['score'] = Variable<int>(score);
    map['label'] = Variable<String>(label);
    map['created_at'] = Variable<DateTime>(createdAt);
    return map;
  }

  MoodsCompanion toCompanion(bool nullToAbsent) {
    return MoodsCompanion(
      id: Value(id),
      userId: Value(userId),
      score: Value(score),
      label: Value(label),
      createdAt: Value(createdAt),
    );
  }

  factory Mood.fromJson(
    Map<String, dynamic> json, {
    ValueSerializer? serializer,
  }) {
    serializer ??= driftRuntimeOptions.defaultSerializer;
    return Mood(
      id: serializer.fromJson<int>(json['id']),
      userId: serializer.fromJson<int>(json['userId']),
      score: serializer.fromJson<int>(json['score']),
      label: serializer.fromJson<String>(json['label']),
      createdAt: serializer.fromJson<DateTime>(json['createdAt']),
    );
  }
  @override
  Map<String, dynamic> toJson({ValueSerializer? serializer}) {
    serializer ??= driftRuntimeOptions.defaultSerializer;
    return <String, dynamic>{
      'id': serializer.toJson<int>(id),
      'userId': serializer.toJson<int>(userId),
      'score': serializer.toJson<int>(score),
      'label': serializer.toJson<String>(label),
      'createdAt': serializer.toJson<DateTime>(createdAt),
    };
  }

  Mood copyWith({
    int? id,
    int? userId,
    int? score,
    String? label,
    DateTime? createdAt,
  }) => Mood(
    id: id ?? this.id,
    userId: userId ?? this.userId,
    score: score ?? this.score,
    label: label ?? this.label,
    createdAt: createdAt ?? this.createdAt,
  );
  Mood copyWithCompanion(MoodsCompanion data) {
    return Mood(
      id: data.id.present ? data.id.value : this.id,
      userId: data.userId.present ? data.userId.value : this.userId,
      score: data.score.present ? data.score.value : this.score,
      label: data.label.present ? data.label.value : this.label,
      createdAt: data.createdAt.present ? data.createdAt.value : this.createdAt,
    );
  }

  @override
  String toString() {
    return (StringBuffer('Mood(')
          ..write('id: $id, ')
          ..write('userId: $userId, ')
          ..write('score: $score, ')
          ..write('label: $label, ')
          ..write('createdAt: $createdAt')
          ..write(')'))
        .toString();
  }

  @override
  int get hashCode => Object.hash(id, userId, score, label, createdAt);
  @override
  bool operator ==(Object other) =>
      identical(this, other) ||
      (other is Mood &&
          other.id == this.id &&
          other.userId == this.userId &&
          other.score == this.score &&
          other.label == this.label &&
          other.createdAt == this.createdAt);
}

class MoodsCompanion extends UpdateCompanion<Mood> {
  final Value<int> id;
  final Value<int> userId;
  final Value<int> score;
  final Value<String> label;
  final Value<DateTime> createdAt;
  const MoodsCompanion({
    this.id = const Value.absent(),
    this.userId = const Value.absent(),
    this.score = const Value.absent(),
    this.label = const Value.absent(),
    this.createdAt = const Value.absent(),
  });
  MoodsCompanion.insert({
    this.id = const Value.absent(),
    required int userId,
    required int score,
    required String label,
    this.createdAt = const Value.absent(),
  }) : userId = Value(userId),
       score = Value(score),
       label = Value(label);
  static Insertable<Mood> custom({
    Expression<int>? id,
    Expression<int>? userId,
    Expression<int>? score,
    Expression<String>? label,
    Expression<DateTime>? createdAt,
  }) {
    return RawValuesInsertable({
      if (id != null) 'id': id,
      if (userId != null) 'user_id': userId,
      if (score != null) 'score': score,
      if (label != null) 'label': label,
      if (createdAt != null) 'created_at': createdAt,
    });
  }

  MoodsCompanion copyWith({
    Value<int>? id,
    Value<int>? userId,
    Value<int>? score,
    Value<String>? label,
    Value<DateTime>? createdAt,
  }) {
    return MoodsCompanion(
      id: id ?? this.id,
      userId: userId ?? this.userId,
      score: score ?? this.score,
      label: label ?? this.label,
      createdAt: createdAt ?? this.createdAt,
    );
  }

  @override
  Map<String, Expression> toColumns(bool nullToAbsent) {
    final map = <String, Expression>{};
    if (id.present) {
      map['id'] = Variable<int>(id.value);
    }
    if (userId.present) {
      map['user_id'] = Variable<int>(userId.value);
    }
    if (score.present) {
      map['score'] = Variable<int>(score.value);
    }
    if (label.present) {
      map['label'] = Variable<String>(label.value);
    }
    if (createdAt.present) {
      map['created_at'] = Variable<DateTime>(createdAt.value);
    }
    return map;
  }

  @override
  String toString() {
    return (StringBuffer('MoodsCompanion(')
          ..write('id: $id, ')
          ..write('userId: $userId, ')
          ..write('score: $score, ')
          ..write('label: $label, ')
          ..write('createdAt: $createdAt')
          ..write(')'))
        .toString();
  }
}

class $TasksTable extends Tasks with TableInfo<$TasksTable, Task> {
  @override
  final GeneratedDatabase attachedDatabase;
  final String? _alias;
  $TasksTable(this.attachedDatabase, [this._alias]);
  static const VerificationMeta _idMeta = const VerificationMeta('id');
  @override
  late final GeneratedColumn<int> id = GeneratedColumn<int>(
    'id',
    aliasedName,
    false,
    hasAutoIncrement: true,
    type: DriftSqlType.int,
    requiredDuringInsert: false,
    defaultConstraints: GeneratedColumn.constraintIsAlways(
      'PRIMARY KEY AUTOINCREMENT',
    ),
  );
  static const VerificationMeta _patientMeta = const VerificationMeta(
    'patient',
  );
  @override
  late final GeneratedColumn<int> patient = GeneratedColumn<int>(
    'patient_id',
    aliasedName,
    true,
    type: DriftSqlType.int,
    requiredDuringInsert: false,
  );
  static const VerificationMeta _nameMeta = const VerificationMeta('name');
  @override
  late final GeneratedColumn<String> name = GeneratedColumn<String>(
    'name',
    aliasedName,
    true,
    type: DriftSqlType.string,
    requiredDuringInsert: false,
  );
  static const VerificationMeta _descriptionMeta = const VerificationMeta(
    'description',
  );
  @override
  late final GeneratedColumn<String> description = GeneratedColumn<String>(
    'description',
    aliasedName,
    true,
    type: DriftSqlType.string,
    requiredDuringInsert: false,
  );
  static const VerificationMeta _dateMeta = const VerificationMeta('date');
  @override
  late final GeneratedColumn<String> date = GeneratedColumn<String>(
    'date',
    aliasedName,
    true,
    type: DriftSqlType.string,
    requiredDuringInsert: false,
  );
  static const VerificationMeta _timeOfDayMeta = const VerificationMeta(
    'timeOfDay',
  );
  @override
  late final GeneratedColumn<String> timeOfDay = GeneratedColumn<String>(
    'time_of_day',
    aliasedName,
    true,
    type: DriftSqlType.string,
    requiredDuringInsert: false,
  );
  static const VerificationMeta _isCompletedMeta = const VerificationMeta(
    'isCompleted',
  );
  @override
  late final GeneratedColumn<bool> isCompleted = GeneratedColumn<bool>(
    'is_completed',
    aliasedName,
    true,
    type: DriftSqlType.bool,
    requiredDuringInsert: false,
    defaultConstraints: GeneratedColumn.constraintIsAlways(
      'CHECK ("is_completed" IN (0, 1))',
    ),
  );
  static const VerificationMeta _taskTypeMeta = const VerificationMeta(
    'taskType',
  );
  @override
  late final GeneratedColumn<String> taskType = GeneratedColumn<String>(
    'task_type',
    aliasedName,
    true,
    type: DriftSqlType.string,
    requiredDuringInsert: false,
  );
  static const VerificationMeta _frequencyMeta = const VerificationMeta(
    'frequency',
  );
  @override
  late final GeneratedColumn<String> frequency = GeneratedColumn<String>(
    'frequency',
    aliasedName,
    true,
    type: DriftSqlType.string,
    requiredDuringInsert: false,
  );
  static const VerificationMeta _taskIntervalMeta = const VerificationMeta(
    'taskInterval',
  );
  @override
  late final GeneratedColumn<int> taskInterval = GeneratedColumn<int>(
    'task_interval',
    aliasedName,
    true,
    type: DriftSqlType.int,
    requiredDuringInsert: false,
  );
  static const VerificationMeta _doCountMeta = const VerificationMeta(
    'doCount',
  );
  @override
  late final GeneratedColumn<int> doCount = GeneratedColumn<int>(
    'do_count',
    aliasedName,
    true,
    type: DriftSqlType.int,
    requiredDuringInsert: false,
  );
  static const VerificationMeta _daysOfWeekMeta = const VerificationMeta(
    'daysOfWeek',
  );
  @override
  late final GeneratedColumn<String> daysOfWeek = GeneratedColumn<String>(
    'days_of_week',
    aliasedName,
    true,
    type: DriftSqlType.string,
    requiredDuringInsert: false,
  );
  static const VerificationMeta _createdAtMeta = const VerificationMeta(
    'createdAt',
  );
  @override
  late final GeneratedColumn<int> createdAt = GeneratedColumn<int>(
    'created_at',
    aliasedName,
    true,
    type: DriftSqlType.int,
    requiredDuringInsert: false,
  );
  static const VerificationMeta _parentTaskIdMeta = const VerificationMeta(
    'parentTaskId',
  );
  @override
  late final GeneratedColumn<int> parentTaskId = GeneratedColumn<int>(
    'parent_task_id',
    aliasedName,
    true,
    type: DriftSqlType.int,
    requiredDuringInsert: false,
  );
  @override
  List<GeneratedColumn> get $columns => [
    id,
    patient,
    name,
    description,
    date,
    timeOfDay,
    isCompleted,
    taskType,
    frequency,
    taskInterval,
    doCount,
    daysOfWeek,
    createdAt,
    parentTaskId,
  ];
  @override
  String get aliasedName => _alias ?? actualTableName;
  @override
  String get actualTableName => $name;
  static const String $name = 'tasks';
  @override
  VerificationContext validateIntegrity(
    Insertable<Task> instance, {
    bool isInserting = false,
  }) {
    final context = VerificationContext();
    final data = instance.toColumns(true);
    if (data.containsKey('id')) {
      context.handle(_idMeta, id.isAcceptableOrUnknown(data['id']!, _idMeta));
    }
    if (data.containsKey('patient_id')) {
      context.handle(
        _patientMeta,
        patient.isAcceptableOrUnknown(data['patient_id']!, _patientMeta),
      );
    }
    if (data.containsKey('name')) {
      context.handle(
        _nameMeta,
        name.isAcceptableOrUnknown(data['name']!, _nameMeta),
      );
    }
    if (data.containsKey('description')) {
      context.handle(
        _descriptionMeta,
        description.isAcceptableOrUnknown(
          data['description']!,
          _descriptionMeta,
        ),
      );
    }
    if (data.containsKey('date')) {
      context.handle(
        _dateMeta,
        date.isAcceptableOrUnknown(data['date']!, _dateMeta),
      );
    }
    if (data.containsKey('time_of_day')) {
      context.handle(
        _timeOfDayMeta,
        timeOfDay.isAcceptableOrUnknown(data['time_of_day']!, _timeOfDayMeta),
      );
    }
    if (data.containsKey('is_completed')) {
      context.handle(
        _isCompletedMeta,
        isCompleted.isAcceptableOrUnknown(
          data['is_completed']!,
          _isCompletedMeta,
        ),
      );
    }
    if (data.containsKey('task_type')) {
      context.handle(
        _taskTypeMeta,
        taskType.isAcceptableOrUnknown(data['task_type']!, _taskTypeMeta),
      );
    }
    if (data.containsKey('frequency')) {
      context.handle(
        _frequencyMeta,
        frequency.isAcceptableOrUnknown(data['frequency']!, _frequencyMeta),
      );
    }
    if (data.containsKey('task_interval')) {
      context.handle(
        _taskIntervalMeta,
        taskInterval.isAcceptableOrUnknown(
          data['task_interval']!,
          _taskIntervalMeta,
        ),
      );
    }
    if (data.containsKey('do_count')) {
      context.handle(
        _doCountMeta,
        doCount.isAcceptableOrUnknown(data['do_count']!, _doCountMeta),
      );
    }
    if (data.containsKey('days_of_week')) {
      context.handle(
        _daysOfWeekMeta,
        daysOfWeek.isAcceptableOrUnknown(
          data['days_of_week']!,
          _daysOfWeekMeta,
        ),
      );
    }
    if (data.containsKey('created_at')) {
      context.handle(
        _createdAtMeta,
        createdAt.isAcceptableOrUnknown(data['created_at']!, _createdAtMeta),
      );
    }
    if (data.containsKey('parent_task_id')) {
      context.handle(
        _parentTaskIdMeta,
        parentTaskId.isAcceptableOrUnknown(
          data['parent_task_id']!,
          _parentTaskIdMeta,
        ),
      );
    }
    return context;
  }

  @override
  Set<GeneratedColumn> get $primaryKey => {id};
  @override
  Task map(Map<String, dynamic> data, {String? tablePrefix}) {
    final effectivePrefix = tablePrefix != null ? '$tablePrefix.' : '';
    return Task(
      id: attachedDatabase.typeMapping.read(
        DriftSqlType.int,
        data['${effectivePrefix}id'],
      )!,
      patient: attachedDatabase.typeMapping.read(
        DriftSqlType.int,
        data['${effectivePrefix}patient_id'],
      ),
      name: attachedDatabase.typeMapping.read(
        DriftSqlType.string,
        data['${effectivePrefix}name'],
      ),
      description: attachedDatabase.typeMapping.read(
        DriftSqlType.string,
        data['${effectivePrefix}description'],
      ),
      date: attachedDatabase.typeMapping.read(
        DriftSqlType.string,
        data['${effectivePrefix}date'],
      ),
      timeOfDay: attachedDatabase.typeMapping.read(
        DriftSqlType.string,
        data['${effectivePrefix}time_of_day'],
      ),
      isCompleted: attachedDatabase.typeMapping.read(
        DriftSqlType.bool,
        data['${effectivePrefix}is_completed'],
      ),
      taskType: attachedDatabase.typeMapping.read(
        DriftSqlType.string,
        data['${effectivePrefix}task_type'],
      ),
      frequency: attachedDatabase.typeMapping.read(
        DriftSqlType.string,
        data['${effectivePrefix}frequency'],
      ),
      taskInterval: attachedDatabase.typeMapping.read(
        DriftSqlType.int,
        data['${effectivePrefix}task_interval'],
      ),
      doCount: attachedDatabase.typeMapping.read(
        DriftSqlType.int,
        data['${effectivePrefix}do_count'],
      ),
      daysOfWeek: attachedDatabase.typeMapping.read(
        DriftSqlType.string,
        data['${effectivePrefix}days_of_week'],
      ),
      createdAt: attachedDatabase.typeMapping.read(
        DriftSqlType.int,
        data['${effectivePrefix}created_at'],
      ),
      parentTaskId: attachedDatabase.typeMapping.read(
        DriftSqlType.int,
        data['${effectivePrefix}parent_task_id'],
      ),
    );
  }

  @override
  $TasksTable createAlias(String alias) {
    return $TasksTable(attachedDatabase, alias);
  }
}

class Task extends DataClass implements Insertable<Task> {
  final int id;
  final int? patient;
  final String? name;
  final String? description;
  final String? date;
  final String? timeOfDay;
  final bool? isCompleted;
  final String? taskType;
  final String? frequency;
  final int? taskInterval;
  final int? doCount;
  final String? daysOfWeek;
  final int? createdAt;
  final int? parentTaskId;
  const Task({
    required this.id,
    this.patient,
    this.name,
    this.description,
    this.date,
    this.timeOfDay,
    this.isCompleted,
    this.taskType,
    this.frequency,
    this.taskInterval,
    this.doCount,
    this.daysOfWeek,
    this.createdAt,
    this.parentTaskId,
  });
  @override
  Map<String, Expression> toColumns(bool nullToAbsent) {
    final map = <String, Expression>{};
    map['id'] = Variable<int>(id);
    if (!nullToAbsent || patient != null) {
      map['patient_id'] = Variable<int>(patient);
    }
    if (!nullToAbsent || name != null) {
      map['name'] = Variable<String>(name);
    }
    if (!nullToAbsent || description != null) {
      map['description'] = Variable<String>(description);
    }
    if (!nullToAbsent || date != null) {
      map['date'] = Variable<String>(date);
    }
    if (!nullToAbsent || timeOfDay != null) {
      map['time_of_day'] = Variable<String>(timeOfDay);
    }
    if (!nullToAbsent || isCompleted != null) {
      map['is_completed'] = Variable<bool>(isCompleted);
    }
    if (!nullToAbsent || taskType != null) {
      map['task_type'] = Variable<String>(taskType);
    }
    if (!nullToAbsent || frequency != null) {
      map['frequency'] = Variable<String>(frequency);
    }
    if (!nullToAbsent || taskInterval != null) {
      map['task_interval'] = Variable<int>(taskInterval);
    }
    if (!nullToAbsent || doCount != null) {
      map['do_count'] = Variable<int>(doCount);
    }
    if (!nullToAbsent || daysOfWeek != null) {
      map['days_of_week'] = Variable<String>(daysOfWeek);
    }
    if (!nullToAbsent || createdAt != null) {
      map['created_at'] = Variable<int>(createdAt);
    }
    if (!nullToAbsent || parentTaskId != null) {
      map['parent_task_id'] = Variable<int>(parentTaskId);
    }
    return map;
  }

  TasksCompanion toCompanion(bool nullToAbsent) {
    return TasksCompanion(
      id: Value(id),
      patient: patient == null && nullToAbsent
          ? const Value.absent()
          : Value(patient),
      name: name == null && nullToAbsent ? const Value.absent() : Value(name),
      description: description == null && nullToAbsent
          ? const Value.absent()
          : Value(description),
      date: date == null && nullToAbsent ? const Value.absent() : Value(date),
      timeOfDay: timeOfDay == null && nullToAbsent
          ? const Value.absent()
          : Value(timeOfDay),
      isCompleted: isCompleted == null && nullToAbsent
          ? const Value.absent()
          : Value(isCompleted),
      taskType: taskType == null && nullToAbsent
          ? const Value.absent()
          : Value(taskType),
      frequency: frequency == null && nullToAbsent
          ? const Value.absent()
          : Value(frequency),
      taskInterval: taskInterval == null && nullToAbsent
          ? const Value.absent()
          : Value(taskInterval),
      doCount: doCount == null && nullToAbsent
          ? const Value.absent()
          : Value(doCount),
      daysOfWeek: daysOfWeek == null && nullToAbsent
          ? const Value.absent()
          : Value(daysOfWeek),
      createdAt: createdAt == null && nullToAbsent
          ? const Value.absent()
          : Value(createdAt),
      parentTaskId: parentTaskId == null && nullToAbsent
          ? const Value.absent()
          : Value(parentTaskId),
    );
  }

  factory Task.fromJson(
    Map<String, dynamic> json, {
    ValueSerializer? serializer,
  }) {
    serializer ??= driftRuntimeOptions.defaultSerializer;
    return Task(
      id: serializer.fromJson<int>(json['id']),
      patient: serializer.fromJson<int?>(json['patient']),
      name: serializer.fromJson<String?>(json['name']),
      description: serializer.fromJson<String?>(json['description']),
      date: serializer.fromJson<String?>(json['date']),
      timeOfDay: serializer.fromJson<String?>(json['timeOfDay']),
      isCompleted: serializer.fromJson<bool?>(json['isCompleted']),
      taskType: serializer.fromJson<String?>(json['taskType']),
      frequency: serializer.fromJson<String?>(json['frequency']),
      taskInterval: serializer.fromJson<int?>(json['taskInterval']),
      doCount: serializer.fromJson<int?>(json['doCount']),
      daysOfWeek: serializer.fromJson<String?>(json['daysOfWeek']),
      createdAt: serializer.fromJson<int?>(json['createdAt']),
      parentTaskId: serializer.fromJson<int?>(json['parentTaskId']),
    );
  }
  @override
  Map<String, dynamic> toJson({ValueSerializer? serializer}) {
    serializer ??= driftRuntimeOptions.defaultSerializer;
    return <String, dynamic>{
      'id': serializer.toJson<int>(id),
      'patient': serializer.toJson<int?>(patient),
      'name': serializer.toJson<String?>(name),
      'description': serializer.toJson<String?>(description),
      'date': serializer.toJson<String?>(date),
      'timeOfDay': serializer.toJson<String?>(timeOfDay),
      'isCompleted': serializer.toJson<bool?>(isCompleted),
      'taskType': serializer.toJson<String?>(taskType),
      'frequency': serializer.toJson<String?>(frequency),
      'taskInterval': serializer.toJson<int?>(taskInterval),
      'doCount': serializer.toJson<int?>(doCount),
      'daysOfWeek': serializer.toJson<String?>(daysOfWeek),
      'createdAt': serializer.toJson<int?>(createdAt),
      'parentTaskId': serializer.toJson<int?>(parentTaskId),
    };
  }

  Task copyWith({
    int? id,
    Value<int?> patient = const Value.absent(),
    Value<String?> name = const Value.absent(),
    Value<String?> description = const Value.absent(),
    Value<String?> date = const Value.absent(),
    Value<String?> timeOfDay = const Value.absent(),
    Value<bool?> isCompleted = const Value.absent(),
    Value<String?> taskType = const Value.absent(),
    Value<String?> frequency = const Value.absent(),
    Value<int?> taskInterval = const Value.absent(),
    Value<int?> doCount = const Value.absent(),
    Value<String?> daysOfWeek = const Value.absent(),
    Value<int?> createdAt = const Value.absent(),
    Value<int?> parentTaskId = const Value.absent(),
  }) => Task(
    id: id ?? this.id,
    patient: patient.present ? patient.value : this.patient,
    name: name.present ? name.value : this.name,
    description: description.present ? description.value : this.description,
    date: date.present ? date.value : this.date,
    timeOfDay: timeOfDay.present ? timeOfDay.value : this.timeOfDay,
    isCompleted: isCompleted.present ? isCompleted.value : this.isCompleted,
    taskType: taskType.present ? taskType.value : this.taskType,
    frequency: frequency.present ? frequency.value : this.frequency,
    taskInterval: taskInterval.present ? taskInterval.value : this.taskInterval,
    doCount: doCount.present ? doCount.value : this.doCount,
    daysOfWeek: daysOfWeek.present ? daysOfWeek.value : this.daysOfWeek,
    createdAt: createdAt.present ? createdAt.value : this.createdAt,
    parentTaskId: parentTaskId.present ? parentTaskId.value : this.parentTaskId,
  );
  Task copyWithCompanion(TasksCompanion data) {
    return Task(
      id: data.id.present ? data.id.value : this.id,
      patient: data.patient.present ? data.patient.value : this.patient,
      name: data.name.present ? data.name.value : this.name,
      description: data.description.present
          ? data.description.value
          : this.description,
      date: data.date.present ? data.date.value : this.date,
      timeOfDay: data.timeOfDay.present ? data.timeOfDay.value : this.timeOfDay,
      isCompleted: data.isCompleted.present
          ? data.isCompleted.value
          : this.isCompleted,
      taskType: data.taskType.present ? data.taskType.value : this.taskType,
      frequency: data.frequency.present ? data.frequency.value : this.frequency,
      taskInterval: data.taskInterval.present
          ? data.taskInterval.value
          : this.taskInterval,
      doCount: data.doCount.present ? data.doCount.value : this.doCount,
      daysOfWeek: data.daysOfWeek.present
          ? data.daysOfWeek.value
          : this.daysOfWeek,
      createdAt: data.createdAt.present ? data.createdAt.value : this.createdAt,
      parentTaskId: data.parentTaskId.present
          ? data.parentTaskId.value
          : this.parentTaskId,
    );
  }

  @override
  String toString() {
    return (StringBuffer('Task(')
          ..write('id: $id, ')
          ..write('patient: $patient, ')
          ..write('name: $name, ')
          ..write('description: $description, ')
          ..write('date: $date, ')
          ..write('timeOfDay: $timeOfDay, ')
          ..write('isCompleted: $isCompleted, ')
          ..write('taskType: $taskType, ')
          ..write('frequency: $frequency, ')
          ..write('taskInterval: $taskInterval, ')
          ..write('doCount: $doCount, ')
          ..write('daysOfWeek: $daysOfWeek, ')
          ..write('createdAt: $createdAt, ')
          ..write('parentTaskId: $parentTaskId')
          ..write(')'))
        .toString();
  }

  @override
  int get hashCode => Object.hash(
    id,
    patient,
    name,
    description,
    date,
    timeOfDay,
    isCompleted,
    taskType,
    frequency,
    taskInterval,
    doCount,
    daysOfWeek,
    createdAt,
    parentTaskId,
  );
  @override
  bool operator ==(Object other) =>
      identical(this, other) ||
      (other is Task &&
          other.id == this.id &&
          other.patient == this.patient &&
          other.name == this.name &&
          other.description == this.description &&
          other.date == this.date &&
          other.timeOfDay == this.timeOfDay &&
          other.isCompleted == this.isCompleted &&
          other.taskType == this.taskType &&
          other.frequency == this.frequency &&
          other.taskInterval == this.taskInterval &&
          other.doCount == this.doCount &&
          other.daysOfWeek == this.daysOfWeek &&
          other.createdAt == this.createdAt &&
          other.parentTaskId == this.parentTaskId);
}

class TasksCompanion extends UpdateCompanion<Task> {
  final Value<int> id;
  final Value<int?> patient;
  final Value<String?> name;
  final Value<String?> description;
  final Value<String?> date;
  final Value<String?> timeOfDay;
  final Value<bool?> isCompleted;
  final Value<String?> taskType;
  final Value<String?> frequency;
  final Value<int?> taskInterval;
  final Value<int?> doCount;
  final Value<String?> daysOfWeek;
  final Value<int?> createdAt;
  final Value<int?> parentTaskId;
  const TasksCompanion({
    this.id = const Value.absent(),
    this.patient = const Value.absent(),
    this.name = const Value.absent(),
    this.description = const Value.absent(),
    this.date = const Value.absent(),
    this.timeOfDay = const Value.absent(),
    this.isCompleted = const Value.absent(),
    this.taskType = const Value.absent(),
    this.frequency = const Value.absent(),
    this.taskInterval = const Value.absent(),
    this.doCount = const Value.absent(),
    this.daysOfWeek = const Value.absent(),
    this.createdAt = const Value.absent(),
    this.parentTaskId = const Value.absent(),
  });
  TasksCompanion.insert({
    this.id = const Value.absent(),
    this.patient = const Value.absent(),
    this.name = const Value.absent(),
    this.description = const Value.absent(),
    this.date = const Value.absent(),
    this.timeOfDay = const Value.absent(),
    this.isCompleted = const Value.absent(),
    this.taskType = const Value.absent(),
    this.frequency = const Value.absent(),
    this.taskInterval = const Value.absent(),
    this.doCount = const Value.absent(),
    this.daysOfWeek = const Value.absent(),
    this.createdAt = const Value.absent(),
    this.parentTaskId = const Value.absent(),
  });
  static Insertable<Task> custom({
    Expression<int>? id,
    Expression<int>? patient,
    Expression<String>? name,
    Expression<String>? description,
    Expression<String>? date,
    Expression<String>? timeOfDay,
    Expression<bool>? isCompleted,
    Expression<String>? taskType,
    Expression<String>? frequency,
    Expression<int>? taskInterval,
    Expression<int>? doCount,
    Expression<String>? daysOfWeek,
    Expression<int>? createdAt,
    Expression<int>? parentTaskId,
  }) {
    return RawValuesInsertable({
      if (id != null) 'id': id,
      if (patient != null) 'patient_id': patient,
      if (name != null) 'name': name,
      if (description != null) 'description': description,
      if (date != null) 'date': date,
      if (timeOfDay != null) 'time_of_day': timeOfDay,
      if (isCompleted != null) 'is_completed': isCompleted,
      if (taskType != null) 'task_type': taskType,
      if (frequency != null) 'frequency': frequency,
      if (taskInterval != null) 'task_interval': taskInterval,
      if (doCount != null) 'do_count': doCount,
      if (daysOfWeek != null) 'days_of_week': daysOfWeek,
      if (createdAt != null) 'created_at': createdAt,
      if (parentTaskId != null) 'parent_task_id': parentTaskId,
    });
  }

  TasksCompanion copyWith({
    Value<int>? id,
    Value<int?>? patient,
    Value<String?>? name,
    Value<String?>? description,
    Value<String?>? date,
    Value<String?>? timeOfDay,
    Value<bool?>? isCompleted,
    Value<String?>? taskType,
    Value<String?>? frequency,
    Value<int?>? taskInterval,
    Value<int?>? doCount,
    Value<String?>? daysOfWeek,
    Value<int?>? createdAt,
    Value<int?>? parentTaskId,
  }) {
    return TasksCompanion(
      id: id ?? this.id,
      patient: patient ?? this.patient,
      name: name ?? this.name,
      description: description ?? this.description,
      date: date ?? this.date,
      timeOfDay: timeOfDay ?? this.timeOfDay,
      isCompleted: isCompleted ?? this.isCompleted,
      taskType: taskType ?? this.taskType,
      frequency: frequency ?? this.frequency,
      taskInterval: taskInterval ?? this.taskInterval,
      doCount: doCount ?? this.doCount,
      daysOfWeek: daysOfWeek ?? this.daysOfWeek,
      createdAt: createdAt ?? this.createdAt,
      parentTaskId: parentTaskId ?? this.parentTaskId,
    );
  }

  @override
  Map<String, Expression> toColumns(bool nullToAbsent) {
    final map = <String, Expression>{};
    if (id.present) {
      map['id'] = Variable<int>(id.value);
    }
    if (patient.present) {
      map['patient_id'] = Variable<int>(patient.value);
    }
    if (name.present) {
      map['name'] = Variable<String>(name.value);
    }
    if (description.present) {
      map['description'] = Variable<String>(description.value);
    }
    if (date.present) {
      map['date'] = Variable<String>(date.value);
    }
    if (timeOfDay.present) {
      map['time_of_day'] = Variable<String>(timeOfDay.value);
    }
    if (isCompleted.present) {
      map['is_completed'] = Variable<bool>(isCompleted.value);
    }
    if (taskType.present) {
      map['task_type'] = Variable<String>(taskType.value);
    }
    if (frequency.present) {
      map['frequency'] = Variable<String>(frequency.value);
    }
    if (taskInterval.present) {
      map['task_interval'] = Variable<int>(taskInterval.value);
    }
    if (doCount.present) {
      map['do_count'] = Variable<int>(doCount.value);
    }
    if (daysOfWeek.present) {
      map['days_of_week'] = Variable<String>(daysOfWeek.value);
    }
    if (createdAt.present) {
      map['created_at'] = Variable<int>(createdAt.value);
    }
    if (parentTaskId.present) {
      map['parent_task_id'] = Variable<int>(parentTaskId.value);
    }
    return map;
  }

  @override
  String toString() {
    return (StringBuffer('TasksCompanion(')
          ..write('id: $id, ')
          ..write('patient: $patient, ')
          ..write('name: $name, ')
          ..write('description: $description, ')
          ..write('date: $date, ')
          ..write('timeOfDay: $timeOfDay, ')
          ..write('isCompleted: $isCompleted, ')
          ..write('taskType: $taskType, ')
          ..write('frequency: $frequency, ')
          ..write('taskInterval: $taskInterval, ')
          ..write('doCount: $doCount, ')
          ..write('daysOfWeek: $daysOfWeek, ')
          ..write('createdAt: $createdAt, ')
          ..write('parentTaskId: $parentTaskId')
          ..write(')'))
        .toString();
  }
}

abstract class _$AppDatabase extends GeneratedDatabase {
  _$AppDatabase(QueryExecutor e) : super(e);
  $AppDatabaseManager get managers => $AppDatabaseManager(this);
  late final $MoodsTable moods = $MoodsTable(this);
  late final $TasksTable tasks = $TasksTable(this);
  @override
  Iterable<TableInfo<Table, Object?>> get allTables =>
      allSchemaEntities.whereType<TableInfo<Table, Object?>>();
  @override
  List<DatabaseSchemaEntity> get allSchemaEntities => [moods, tasks];
}

typedef $$MoodsTableCreateCompanionBuilder =
    MoodsCompanion Function({
      Value<int> id,
      required int userId,
      required int score,
      required String label,
      Value<DateTime> createdAt,
    });
typedef $$MoodsTableUpdateCompanionBuilder =
    MoodsCompanion Function({
      Value<int> id,
      Value<int> userId,
      Value<int> score,
      Value<String> label,
      Value<DateTime> createdAt,
    });

class $$MoodsTableFilterComposer extends Composer<_$AppDatabase, $MoodsTable> {
  $$MoodsTableFilterComposer({
    required super.$db,
    required super.$table,
    super.joinBuilder,
    super.$addJoinBuilderToRootComposer,
    super.$removeJoinBuilderFromRootComposer,
  });
  ColumnFilters<int> get id => $composableBuilder(
    column: $table.id,
    builder: (column) => ColumnFilters(column),
  );

  ColumnFilters<int> get userId => $composableBuilder(
    column: $table.userId,
    builder: (column) => ColumnFilters(column),
  );

  ColumnFilters<int> get score => $composableBuilder(
    column: $table.score,
    builder: (column) => ColumnFilters(column),
  );

  ColumnFilters<String> get label => $composableBuilder(
    column: $table.label,
    builder: (column) => ColumnFilters(column),
  );

  ColumnFilters<DateTime> get createdAt => $composableBuilder(
    column: $table.createdAt,
    builder: (column) => ColumnFilters(column),
  );
}

class $$MoodsTableOrderingComposer
    extends Composer<_$AppDatabase, $MoodsTable> {
  $$MoodsTableOrderingComposer({
    required super.$db,
    required super.$table,
    super.joinBuilder,
    super.$addJoinBuilderToRootComposer,
    super.$removeJoinBuilderFromRootComposer,
  });
  ColumnOrderings<int> get id => $composableBuilder(
    column: $table.id,
    builder: (column) => ColumnOrderings(column),
  );

  ColumnOrderings<int> get userId => $composableBuilder(
    column: $table.userId,
    builder: (column) => ColumnOrderings(column),
  );

  ColumnOrderings<int> get score => $composableBuilder(
    column: $table.score,
    builder: (column) => ColumnOrderings(column),
  );

  ColumnOrderings<String> get label => $composableBuilder(
    column: $table.label,
    builder: (column) => ColumnOrderings(column),
  );

  ColumnOrderings<DateTime> get createdAt => $composableBuilder(
    column: $table.createdAt,
    builder: (column) => ColumnOrderings(column),
  );
}

class $$MoodsTableAnnotationComposer
    extends Composer<_$AppDatabase, $MoodsTable> {
  $$MoodsTableAnnotationComposer({
    required super.$db,
    required super.$table,
    super.joinBuilder,
    super.$addJoinBuilderToRootComposer,
    super.$removeJoinBuilderFromRootComposer,
  });
  GeneratedColumn<int> get id =>
      $composableBuilder(column: $table.id, builder: (column) => column);

  GeneratedColumn<int> get userId =>
      $composableBuilder(column: $table.userId, builder: (column) => column);

  GeneratedColumn<int> get score =>
      $composableBuilder(column: $table.score, builder: (column) => column);

  GeneratedColumn<String> get label =>
      $composableBuilder(column: $table.label, builder: (column) => column);

  GeneratedColumn<DateTime> get createdAt =>
      $composableBuilder(column: $table.createdAt, builder: (column) => column);
}

class $$MoodsTableTableManager
    extends
        RootTableManager<
          _$AppDatabase,
          $MoodsTable,
          Mood,
          $$MoodsTableFilterComposer,
          $$MoodsTableOrderingComposer,
          $$MoodsTableAnnotationComposer,
          $$MoodsTableCreateCompanionBuilder,
          $$MoodsTableUpdateCompanionBuilder,
          (Mood, BaseReferences<_$AppDatabase, $MoodsTable, Mood>),
          Mood,
          PrefetchHooks Function()
        > {
  $$MoodsTableTableManager(_$AppDatabase db, $MoodsTable table)
    : super(
        TableManagerState(
          db: db,
          table: table,
          createFilteringComposer: () =>
              $$MoodsTableFilterComposer($db: db, $table: table),
          createOrderingComposer: () =>
              $$MoodsTableOrderingComposer($db: db, $table: table),
          createComputedFieldComposer: () =>
              $$MoodsTableAnnotationComposer($db: db, $table: table),
          updateCompanionCallback:
              ({
                Value<int> id = const Value.absent(),
                Value<int> userId = const Value.absent(),
                Value<int> score = const Value.absent(),
                Value<String> label = const Value.absent(),
                Value<DateTime> createdAt = const Value.absent(),
              }) => MoodsCompanion(
                id: id,
                userId: userId,
                score: score,
                label: label,
                createdAt: createdAt,
              ),
          createCompanionCallback:
              ({
                Value<int> id = const Value.absent(),
                required int userId,
                required int score,
                required String label,
                Value<DateTime> createdAt = const Value.absent(),
              }) => MoodsCompanion.insert(
                id: id,
                userId: userId,
                score: score,
                label: label,
                createdAt: createdAt,
              ),
          withReferenceMapper: (p0) => p0
              .map((e) => (e.readTable(table), BaseReferences(db, table, e)))
              .toList(),
          prefetchHooksCallback: null,
        ),
      );
}

typedef $$MoodsTableProcessedTableManager =
    ProcessedTableManager<
      _$AppDatabase,
      $MoodsTable,
      Mood,
      $$MoodsTableFilterComposer,
      $$MoodsTableOrderingComposer,
      $$MoodsTableAnnotationComposer,
      $$MoodsTableCreateCompanionBuilder,
      $$MoodsTableUpdateCompanionBuilder,
      (Mood, BaseReferences<_$AppDatabase, $MoodsTable, Mood>),
      Mood,
      PrefetchHooks Function()
    >;
typedef $$TasksTableCreateCompanionBuilder =
    TasksCompanion Function({
      Value<int> id,
      Value<int?> patient,
      Value<String?> name,
      Value<String?> description,
      Value<String?> date,
      Value<String?> timeOfDay,
      Value<bool?> isCompleted,
      Value<String?> taskType,
      Value<String?> frequency,
      Value<int?> taskInterval,
      Value<int?> doCount,
      Value<String?> daysOfWeek,
      Value<int?> createdAt,
      Value<int?> parentTaskId,
    });
typedef $$TasksTableUpdateCompanionBuilder =
    TasksCompanion Function({
      Value<int> id,
      Value<int?> patient,
      Value<String?> name,
      Value<String?> description,
      Value<String?> date,
      Value<String?> timeOfDay,
      Value<bool?> isCompleted,
      Value<String?> taskType,
      Value<String?> frequency,
      Value<int?> taskInterval,
      Value<int?> doCount,
      Value<String?> daysOfWeek,
      Value<int?> createdAt,
      Value<int?> parentTaskId,
    });

class $$TasksTableFilterComposer extends Composer<_$AppDatabase, $TasksTable> {
  $$TasksTableFilterComposer({
    required super.$db,
    required super.$table,
    super.joinBuilder,
    super.$addJoinBuilderToRootComposer,
    super.$removeJoinBuilderFromRootComposer,
  });
  ColumnFilters<int> get id => $composableBuilder(
    column: $table.id,
    builder: (column) => ColumnFilters(column),
  );

  ColumnFilters<int> get patient => $composableBuilder(
    column: $table.patient,
    builder: (column) => ColumnFilters(column),
  );

  ColumnFilters<String> get name => $composableBuilder(
    column: $table.name,
    builder: (column) => ColumnFilters(column),
  );

  ColumnFilters<String> get description => $composableBuilder(
    column: $table.description,
    builder: (column) => ColumnFilters(column),
  );

  ColumnFilters<String> get date => $composableBuilder(
    column: $table.date,
    builder: (column) => ColumnFilters(column),
  );

  ColumnFilters<String> get timeOfDay => $composableBuilder(
    column: $table.timeOfDay,
    builder: (column) => ColumnFilters(column),
  );

  ColumnFilters<bool> get isCompleted => $composableBuilder(
    column: $table.isCompleted,
    builder: (column) => ColumnFilters(column),
  );

  ColumnFilters<String> get taskType => $composableBuilder(
    column: $table.taskType,
    builder: (column) => ColumnFilters(column),
  );

  ColumnFilters<String> get frequency => $composableBuilder(
    column: $table.frequency,
    builder: (column) => ColumnFilters(column),
  );

  ColumnFilters<int> get taskInterval => $composableBuilder(
    column: $table.taskInterval,
    builder: (column) => ColumnFilters(column),
  );

  ColumnFilters<int> get doCount => $composableBuilder(
    column: $table.doCount,
    builder: (column) => ColumnFilters(column),
  );

  ColumnFilters<String> get daysOfWeek => $composableBuilder(
    column: $table.daysOfWeek,
    builder: (column) => ColumnFilters(column),
  );

  ColumnFilters<int> get createdAt => $composableBuilder(
    column: $table.createdAt,
    builder: (column) => ColumnFilters(column),
  );

  ColumnFilters<int> get parentTaskId => $composableBuilder(
    column: $table.parentTaskId,
    builder: (column) => ColumnFilters(column),
  );
}

class $$TasksTableOrderingComposer
    extends Composer<_$AppDatabase, $TasksTable> {
  $$TasksTableOrderingComposer({
    required super.$db,
    required super.$table,
    super.joinBuilder,
    super.$addJoinBuilderToRootComposer,
    super.$removeJoinBuilderFromRootComposer,
  });
  ColumnOrderings<int> get id => $composableBuilder(
    column: $table.id,
    builder: (column) => ColumnOrderings(column),
  );

  ColumnOrderings<int> get patient => $composableBuilder(
    column: $table.patient,
    builder: (column) => ColumnOrderings(column),
  );

  ColumnOrderings<String> get name => $composableBuilder(
    column: $table.name,
    builder: (column) => ColumnOrderings(column),
  );

  ColumnOrderings<String> get description => $composableBuilder(
    column: $table.description,
    builder: (column) => ColumnOrderings(column),
  );

  ColumnOrderings<String> get date => $composableBuilder(
    column: $table.date,
    builder: (column) => ColumnOrderings(column),
  );

  ColumnOrderings<String> get timeOfDay => $composableBuilder(
    column: $table.timeOfDay,
    builder: (column) => ColumnOrderings(column),
  );

  ColumnOrderings<bool> get isCompleted => $composableBuilder(
    column: $table.isCompleted,
    builder: (column) => ColumnOrderings(column),
  );

  ColumnOrderings<String> get taskType => $composableBuilder(
    column: $table.taskType,
    builder: (column) => ColumnOrderings(column),
  );

  ColumnOrderings<String> get frequency => $composableBuilder(
    column: $table.frequency,
    builder: (column) => ColumnOrderings(column),
  );

  ColumnOrderings<int> get taskInterval => $composableBuilder(
    column: $table.taskInterval,
    builder: (column) => ColumnOrderings(column),
  );

  ColumnOrderings<int> get doCount => $composableBuilder(
    column: $table.doCount,
    builder: (column) => ColumnOrderings(column),
  );

  ColumnOrderings<String> get daysOfWeek => $composableBuilder(
    column: $table.daysOfWeek,
    builder: (column) => ColumnOrderings(column),
  );

  ColumnOrderings<int> get createdAt => $composableBuilder(
    column: $table.createdAt,
    builder: (column) => ColumnOrderings(column),
  );

  ColumnOrderings<int> get parentTaskId => $composableBuilder(
    column: $table.parentTaskId,
    builder: (column) => ColumnOrderings(column),
  );
}

class $$TasksTableAnnotationComposer
    extends Composer<_$AppDatabase, $TasksTable> {
  $$TasksTableAnnotationComposer({
    required super.$db,
    required super.$table,
    super.joinBuilder,
    super.$addJoinBuilderToRootComposer,
    super.$removeJoinBuilderFromRootComposer,
  });
  GeneratedColumn<int> get id =>
      $composableBuilder(column: $table.id, builder: (column) => column);

  GeneratedColumn<int> get patient =>
      $composableBuilder(column: $table.patient, builder: (column) => column);

  GeneratedColumn<String> get name =>
      $composableBuilder(column: $table.name, builder: (column) => column);

  GeneratedColumn<String> get description => $composableBuilder(
    column: $table.description,
    builder: (column) => column,
  );

  GeneratedColumn<String> get date =>
      $composableBuilder(column: $table.date, builder: (column) => column);

  GeneratedColumn<String> get timeOfDay =>
      $composableBuilder(column: $table.timeOfDay, builder: (column) => column);

  GeneratedColumn<bool> get isCompleted => $composableBuilder(
    column: $table.isCompleted,
    builder: (column) => column,
  );

  GeneratedColumn<String> get taskType =>
      $composableBuilder(column: $table.taskType, builder: (column) => column);

  GeneratedColumn<String> get frequency =>
      $composableBuilder(column: $table.frequency, builder: (column) => column);

  GeneratedColumn<int> get taskInterval => $composableBuilder(
    column: $table.taskInterval,
    builder: (column) => column,
  );

  GeneratedColumn<int> get doCount =>
      $composableBuilder(column: $table.doCount, builder: (column) => column);

  GeneratedColumn<String> get daysOfWeek => $composableBuilder(
    column: $table.daysOfWeek,
    builder: (column) => column,
  );

  GeneratedColumn<int> get createdAt =>
      $composableBuilder(column: $table.createdAt, builder: (column) => column);

  GeneratedColumn<int> get parentTaskId => $composableBuilder(
    column: $table.parentTaskId,
    builder: (column) => column,
  );
}

class $$TasksTableTableManager
    extends
        RootTableManager<
          _$AppDatabase,
          $TasksTable,
          Task,
          $$TasksTableFilterComposer,
          $$TasksTableOrderingComposer,
          $$TasksTableAnnotationComposer,
          $$TasksTableCreateCompanionBuilder,
          $$TasksTableUpdateCompanionBuilder,
          (Task, BaseReferences<_$AppDatabase, $TasksTable, Task>),
          Task,
          PrefetchHooks Function()
        > {
  $$TasksTableTableManager(_$AppDatabase db, $TasksTable table)
    : super(
        TableManagerState(
          db: db,
          table: table,
          createFilteringComposer: () =>
              $$TasksTableFilterComposer($db: db, $table: table),
          createOrderingComposer: () =>
              $$TasksTableOrderingComposer($db: db, $table: table),
          createComputedFieldComposer: () =>
              $$TasksTableAnnotationComposer($db: db, $table: table),
          updateCompanionCallback:
              ({
                Value<int> id = const Value.absent(),
                Value<int?> patient = const Value.absent(),
                Value<String?> name = const Value.absent(),
                Value<String?> description = const Value.absent(),
                Value<String?> date = const Value.absent(),
                Value<String?> timeOfDay = const Value.absent(),
                Value<bool?> isCompleted = const Value.absent(),
                Value<String?> taskType = const Value.absent(),
                Value<String?> frequency = const Value.absent(),
                Value<int?> taskInterval = const Value.absent(),
                Value<int?> doCount = const Value.absent(),
                Value<String?> daysOfWeek = const Value.absent(),
                Value<int?> createdAt = const Value.absent(),
                Value<int?> parentTaskId = const Value.absent(),
              }) => TasksCompanion(
                id: id,
                patient: patient,
                name: name,
                description: description,
                date: date,
                timeOfDay: timeOfDay,
                isCompleted: isCompleted,
                taskType: taskType,
                frequency: frequency,
                taskInterval: taskInterval,
                doCount: doCount,
                daysOfWeek: daysOfWeek,
                createdAt: createdAt,
                parentTaskId: parentTaskId,
              ),
          createCompanionCallback:
              ({
                Value<int> id = const Value.absent(),
                Value<int?> patient = const Value.absent(),
                Value<String?> name = const Value.absent(),
                Value<String?> description = const Value.absent(),
                Value<String?> date = const Value.absent(),
                Value<String?> timeOfDay = const Value.absent(),
                Value<bool?> isCompleted = const Value.absent(),
                Value<String?> taskType = const Value.absent(),
                Value<String?> frequency = const Value.absent(),
                Value<int?> taskInterval = const Value.absent(),
                Value<int?> doCount = const Value.absent(),
                Value<String?> daysOfWeek = const Value.absent(),
                Value<int?> createdAt = const Value.absent(),
                Value<int?> parentTaskId = const Value.absent(),
              }) => TasksCompanion.insert(
                id: id,
                patient: patient,
                name: name,
                description: description,
                date: date,
                timeOfDay: timeOfDay,
                isCompleted: isCompleted,
                taskType: taskType,
                frequency: frequency,
                taskInterval: taskInterval,
                doCount: doCount,
                daysOfWeek: daysOfWeek,
                createdAt: createdAt,
                parentTaskId: parentTaskId,
              ),
          withReferenceMapper: (p0) => p0
              .map((e) => (e.readTable(table), BaseReferences(db, table, e)))
              .toList(),
          prefetchHooksCallback: null,
        ),
      );
}

typedef $$TasksTableProcessedTableManager =
    ProcessedTableManager<
      _$AppDatabase,
      $TasksTable,
      Task,
      $$TasksTableFilterComposer,
      $$TasksTableOrderingComposer,
      $$TasksTableAnnotationComposer,
      $$TasksTableCreateCompanionBuilder,
      $$TasksTableUpdateCompanionBuilder,
      (Task, BaseReferences<_$AppDatabase, $TasksTable, Task>),
      Task,
      PrefetchHooks Function()
    >;

class $AppDatabaseManager {
  final _$AppDatabase _db;
  $AppDatabaseManager(this._db);
  $$MoodsTableTableManager get moods =>
      $$MoodsTableTableManager(_db, _db.moods);
  $$TasksTableTableManager get tasks =>
      $$TasksTableTableManager(_db, _db.tasks);
}
