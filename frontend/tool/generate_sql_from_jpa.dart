import 'dart:io';

/// Generates offline sqlite `CREATE TABLE` SQL and Drift table definitions
/// from backend JPA entity classes.
///
/// This tool is intended for dev/build-time generation, not app runtime use.
/// Runtime only reads the generated outputs under
/// `lib/services/local_db/generated/`.

final _tableRegex = RegExp(r'@Table\(([^)]*)\)');
final _classRegex = RegExp(r'^\s*(?:public\s+)?class\s+([A-Za-z0-9_]+)\b');
final _idRegex = RegExp(r'@Id\b');
final _generatedValueRegex = RegExp(r'@GeneratedValue\b');
final _columnRegex = RegExp(r'@Column\(([^)]*)\)');
final _joinColumnRegex = RegExp(r'@JoinColumn\(([^)]*)\)');
final _fieldRegex = RegExp(
  r'private\s+([A-Za-z0-9_<>.]+)\s+([A-Za-z0-9_]+)(?:\s*=\s*[^;]+)?\s*;',
);
final _nameArgRegex = RegExp(r'name\s*=\s*"([^"]+)"');
final _nullableFalseRegex = RegExp(r'nullable\s*=\s*false');
final _relationshipRegex = RegExp(
  r'@(ManyToOne|OneToOne|OneToMany|ManyToMany)\b',
);
final _transientRegex = RegExp(r'@Transient\b');
final _entityRegex = RegExp(r'@Entity\b');

void main(List<String> args) {
  final options = _parseArgs(args);
  if (options.showHelp) {
    _printHelp();
    return;
  }

  final files = _resolveJavaFiles(options.inputPath, recursive: options.recursive);
  if (files.isEmpty) {
    stderr.writeln('No Java files found for ${options.inputPath}');
    exitCode = 1;
    return;
  }

  final specs = <_TableSpec>[];
  for (final file in files) {
    final spec = _parseEntity(file, options.entityFilter);
    if (spec != null) {
      specs.add(spec);
    }
  }

  if (specs.isEmpty) {
    stderr.writeln('No @Entity table specs generated from input.');
    exitCode = 1;
    return;
  }

  if (options.sqlOutPath != null && options.sqlOutPath!.isNotEmpty) {
    final sql = _renderSqlFile(specs, options.inputPath);
    _writeFile(options.sqlOutPath!, sql);
    stdout.writeln('Wrote SQL schema for ${specs.length} table(s) -> ${options.sqlOutPath}');
  } else {
    stdout.writeln(_renderSqlFile(specs, options.inputPath));
  }

  if (options.dartOutPath != null && options.dartOutPath!.isNotEmpty) {
    final dart = _renderDriftBundle(specs, options.inputPath);
    _writeFile(options.dartOutPath!, dart);
    stdout.writeln('Wrote Drift bundle for ${specs.length} table(s) -> ${options.dartOutPath}');
  }
}

/// Parses one Java entity file into an intermediate table specification.
_TableSpec? _parseEntity(File file, Set<String>? entityFilter) {
  final lines = file.readAsLinesSync();
  if (!lines.any((line) => _entityRegex.hasMatch(line))) {
    return null;
  }

  final className = _resolveClassName(lines);
  if (className == null) {
    return null;
  }

  if (entityFilter != null && !entityFilter.contains(className)) {
    return null;
  }

  final tableName = _resolveTableName(lines) ?? _toSnakeCase(className);
  final driftTableClassName = _toPascalCase(tableName);
  final columns = <_ColumnSpec>[];

  var pendingIsId = false;
  var pendingGenerated = false;
  var pendingNullable = true;
  String? pendingExplicitName;
  String? pendingRelationType;
  var pendingIsTransient = false;

  for (final line in lines) {
    if (_idRegex.hasMatch(line)) {
      pendingIsId = true;
      continue;
    }
    if (_generatedValueRegex.hasMatch(line)) {
      pendingGenerated = true;
      continue;
    }
    final relationMatch = _relationshipRegex.firstMatch(line);
    if (relationMatch != null) {
      pendingRelationType = relationMatch.group(1);
      continue;
    }
    if (_transientRegex.hasMatch(line)) {
      pendingIsTransient = true;
      continue;
    }

    final columnMatch = _columnRegex.firstMatch(line);
    if (columnMatch != null) {
      final argsText = columnMatch.group(1) ?? '';
      pendingNullable = !_nullableFalseRegex.hasMatch(argsText);
      pendingExplicitName = _extractNameArg(argsText) ?? pendingExplicitName;
      continue;
    }

    final joinMatch = _joinColumnRegex.firstMatch(line);
    if (joinMatch != null) {
      final argsText = joinMatch.group(1) ?? '';
      pendingNullable = !_nullableFalseRegex.hasMatch(argsText);
      pendingExplicitName = _extractNameArg(argsText) ?? pendingExplicitName;
      continue;
    }

    final fieldMatch = _fieldRegex.firstMatch(line);
    if (fieldMatch == null) {
      continue;
    }

    if (line.contains(' static ') || line.contains(' final ')) {
      pendingIsId = false;
      pendingGenerated = false;
      pendingNullable = true;
      pendingExplicitName = null;
      pendingRelationType = null;
      pendingIsTransient = false;
      continue;
    }

    if (pendingIsTransient) {
      pendingIsId = false;
      pendingGenerated = false;
      pendingNullable = true;
      pendingExplicitName = null;
      pendingRelationType = null;
      pendingIsTransient = false;
      continue;
    }

    final javaType = _stripGenerics(fieldMatch.group(1)!);
    final fieldName = fieldMatch.group(2)!;

    // Collection-side relations are represented by foreign keys in other tables,
    // so we skip them in this table schema.
    if (pendingRelationType == 'OneToMany' || pendingRelationType == 'ManyToMany') {
      pendingIsId = false;
      pendingGenerated = false;
      pendingNullable = true;
      pendingExplicitName = null;
      pendingRelationType = null;
      pendingIsTransient = false;
      continue;
    }

    final columnName = pendingExplicitName ?? fieldName;
    final mappedType =
        (pendingRelationType == 'ManyToOne' || pendingRelationType == 'OneToOne')
            ? _MappedType.integer
            : _mapJavaToMapped(javaType);

    columns.add(
      _ColumnSpec(
        fieldName: fieldName,
        columnName: columnName,
        mappedType: mappedType,
        nullable: pendingNullable,
        isId: pendingIsId && fieldName == 'id',
        autoIncrement: pendingIsId && pendingGenerated,
      ),
    );

    pendingIsId = false;
    pendingGenerated = false;
    pendingNullable = true;
    pendingExplicitName = null;
    pendingRelationType = null;
    pendingIsTransient = false;
  }

  if (columns.isEmpty) {
    return null;
  }

  return _TableSpec(
    entityClassName: className,
    tableName: tableName,
    driftClassName: driftTableClassName,
    sourceFilePath: file.path,
    columns: columns,
  );
}

/// Renders aggregated SQL schema output used by startup table creation.
String _renderSqlFile(List<_TableSpec> specs, String inputPath) {
  final out = StringBuffer()
    ..writeln('-- Auto-generated from JPA entities.')
    ..writeln('-- Input: $inputPath')
    ..writeln();

  for (var i = 0; i < specs.length; i++) {
    final spec = specs[i];
    if (i > 0) {
      out.writeln();
    }
    out.writeln('-- Source: ${spec.sourceFilePath}');
    out.writeln('CREATE TABLE IF NOT EXISTS ${spec.tableName} (');
    for (var c = 0; c < spec.columns.length; c++) {
      final col = spec.columns[c];
      final sql = _columnSql(col);
      final suffix = c == spec.columns.length - 1 ? '' : ',';
      out.writeln('  $sql$suffix');
    }
    out.writeln(');');
  }

  return out.toString();
}

/// Renders a Dart bundle with Drift table classes and SQL lookup map.
String _renderDriftBundle(List<_TableSpec> specs, String inputPath) {
  final out = StringBuffer()
    ..writeln('// GENERATED CODE - DO NOT MODIFY BY HAND')
    ..writeln('// Source input: $inputPath')
    ..writeln('// Run: dart run tool/generate_sql_from_jpa.dart ...')
    ..writeln()
    ..writeln("import 'package:drift/drift.dart';")
    ..writeln();

  for (final spec in specs) {
    out.writeln('// Source: ${spec.sourceFilePath}');
    out.writeln('class ${spec.driftClassName} extends Table {');
    for (final col in spec.columns) {
      out.writeln('  ${_columnDrift(col)}');
    }
    out.writeln('}');
    out.writeln();
  }

  out.writeln('const Map<String, String> generatedCreateTableSql = {');
  for (final spec in specs) {
    out.writeln("  '${spec.tableName}': '''");
    out.writeln('CREATE TABLE IF NOT EXISTS ${spec.tableName} (');
    for (var i = 0; i < spec.columns.length; i++) {
      final col = spec.columns[i];
      final suffix = i == spec.columns.length - 1 ? '' : ',';
      out.writeln('  ${_columnSql(col)}$suffix');
    }
    out.writeln(');');
    out.writeln("''',");
  }
  out.writeln('};');
  out.writeln();
  out.writeln('String? generatedCreateTableFor(String tableName) {');
  out.writeln("  return generatedCreateTableSql[tableName.toLowerCase()];");
  out.writeln('}');

  return out.toString();
}

String _columnSql(_ColumnSpec c) {
  if (c.isId) {
    return c.autoIncrement
        ? '${c.columnName} INTEGER PRIMARY KEY AUTOINCREMENT'
        : '${c.columnName} INTEGER PRIMARY KEY';
  }
  final type = switch (c.mappedType) {
    _MappedType.integer => 'INTEGER',
    _MappedType.real => 'REAL',
    _MappedType.text => 'TEXT',
    _MappedType.boolean => 'INTEGER',
    _MappedType.dateTime => 'TEXT',
  };
  final nullability = c.nullable ? '' : ' NOT NULL';
  return '${c.columnName} $type$nullability';
}

String _columnDrift(_ColumnSpec c) {
  if (c.isId) {
    return 'IntColumn get ${c.fieldName} => integer().autoIncrement()();';
  }

  String builder = switch (c.mappedType) {
    _MappedType.integer => 'integer()',
    _MappedType.real => 'real()',
    _MappedType.text => 'text()',
    _MappedType.boolean => 'boolean()',
    _MappedType.dateTime => 'dateTime()',
  };

  if (c.columnName != c.fieldName) {
    builder = "$builder.named('${c.columnName}')";
  }

  if (c.nullable) {
    builder = '$builder.nullable()';
  } else if (c.mappedType == _MappedType.dateTime &&
      c.fieldName.toLowerCase() == 'createdat') {
    builder = '$builder.withDefault(currentDateAndTime)';
  }

  return '${_driftTypeName(c.mappedType)} get ${c.fieldName} => $builder();';
}

String _driftTypeName(_MappedType type) {
  return switch (type) {
    _MappedType.integer => 'IntColumn',
    _MappedType.real => 'RealColumn',
    _MappedType.text => 'TextColumn',
    _MappedType.boolean => 'BoolColumn',
    _MappedType.dateTime => 'DateTimeColumn',
  };
}

List<File> _resolveJavaFiles(String input, {required bool recursive}) {
  final type = FileSystemEntity.typeSync(input);
  if (type == FileSystemEntityType.notFound) {
    return const [];
  }
  if (type == FileSystemEntityType.file && input.toLowerCase().endsWith('.java')) {
    return [File(input)];
  }
  if (type == FileSystemEntityType.directory) {
    final dir = Directory(input);
    return dir
        .listSync(recursive: recursive)
        .whereType<File>()
        .where((f) => f.path.toLowerCase().endsWith('.java'))
        .toList()
      ..sort((a, b) => a.path.compareTo(b.path));
  }
  return const [];
}

String? _resolveClassName(List<String> lines) {
  for (final line in lines) {
    final m = _classRegex.firstMatch(line);
    if (m != null) {
      return m.group(1);
    }
  }
  return null;
}

String? _resolveTableName(List<String> lines) {
  for (final line in lines) {
    final m = _tableRegex.firstMatch(line);
    if (m == null) {
      continue;
    }
    final argsText = m.group(1) ?? '';
    return _extractNameArg(argsText);
  }
  return null;
}

String? _extractNameArg(String argsText) {
  final name = _nameArgRegex.firstMatch(argsText);
  return name?.group(1);
}

String _stripGenerics(String javaType) {
  final idx = javaType.indexOf('<');
  if (idx == -1) {
    return javaType;
  }
  return javaType.substring(0, idx);
}

String _toSnakeCase(String input) {
  final withUnderscore = input.replaceAllMapped(
    RegExp(r'([a-z0-9])([A-Z])'),
    (m) => '${m.group(1)}_${m.group(2)}',
  );
  return withUnderscore.toLowerCase();
}

String _toPascalCase(String value) {
  final parts =
      value
          .split(RegExp(r'[_\-\s]+'))
          .where((e) => e.isNotEmpty)
          .map((part) => '${part[0].toUpperCase()}${part.substring(1)}')
          .join();
  return parts.isEmpty ? 'UnknownTable' : parts;
}

_MappedType _mapJavaToMapped(String javaType) {
  switch (javaType) {
    case 'Long':
    case 'long':
    case 'Integer':
    case 'int':
    case 'Short':
    case 'short':
      return _MappedType.integer;
    case 'Boolean':
    case 'boolean':
      return _MappedType.boolean;
    case 'Double':
    case 'double':
    case 'Float':
    case 'float':
    case 'BigDecimal':
      return _MappedType.real;
    case 'LocalDate':
    case 'LocalDateTime':
    case 'Instant':
    case 'ZonedDateTime':
      return _MappedType.dateTime;
    default:
      return _MappedType.text;
  }
}

_Options _parseArgs(List<String> args) {
  var inputPath = '../backend/core/src/main/java/com/careconnect/model';
  var sqlOutPath = 'lib/services/local_db/generated/schema.sql';
  var dartOutPath = 'lib/services/local_db/generated/jpa_drift_bundle.dart';
  Set<String>? entityFilter;
  var recursive = true;
  var showHelp = false;

  for (var i = 0; i < args.length; i++) {
    final arg = args[i];
    if (arg == '--help' || arg == '-h') {
      showHelp = true;
      continue;
    }
    if (!arg.startsWith('-') && i == 0) {
      inputPath = arg;
      continue;
    }
    if (arg == '--input' || arg == '-i') {
      if (i + 1 < args.length) {
        inputPath = args[++i];
      }
      continue;
    }
    if (arg == '--sql-out') {
      if (i + 1 < args.length) {
        sqlOutPath = args[++i];
      }
      continue;
    }
    if (arg == '--dart-out') {
      if (i + 1 < args.length) {
        dartOutPath = args[++i];
      }
      continue;
    }
    if (arg == '--no-sql') {
      sqlOutPath = '';
      continue;
    }
    if (arg == '--no-dart') {
      dartOutPath = '';
      continue;
    }
    if (arg == '--entities' || arg == '-e') {
      if (i + 1 < args.length) {
        final names =
            args[++i]
                .split(',')
                .map((e) => e.trim())
                .where((e) => e.isNotEmpty)
                .toSet();
        if (names.isNotEmpty) {
          entityFilter = names;
        }
      }
      continue;
    }
    if (arg == '--no-recursive') {
      recursive = false;
      continue;
    }
  }

  return _Options(
    inputPath: inputPath,
    sqlOutPath: sqlOutPath,
    dartOutPath: dartOutPath,
    entityFilter: entityFilter,
    recursive: recursive,
    showHelp: showHelp,
  );
}

/// Prints CLI usage for local schema generation workflow.
void _printHelp() {
  stdout.writeln('JPA -> SQLite + Drift bundle generator');
  stdout.writeln('');
  stdout.writeln('Usage:');
  stdout.writeln(
    '  dart run tool/generate_sql_from_jpa.dart [--input <file-or-dir>] [--entities <A,B>] [--sql-out <path>] [--dart-out <path>] [--no-recursive]',
  );
  stdout.writeln('');
  stdout.writeln('Defaults:');
  stdout.writeln('  --sql-out  lib/services/local_db/generated/schema.sql');
  stdout.writeln('  --dart-out lib/services/local_db/generated/jpa_drift_bundle.dart');
  stdout.writeln('');
  stdout.writeln('Examples:');
  stdout.writeln(
    '  dart run tool/generate_sql_from_jpa.dart --input ../backend/core/src/main/java/com/careconnect/model --entities Mood',
  );
  stdout.writeln(
    '  dart run tool/generate_sql_from_jpa.dart --input ../backend/core/src/main/java/com/careconnect/model --entities Mood,Task',
  );
}

void _writeFile(String path, String contents) {
  final file = File(path);
  file.parent.createSync(recursive: true);
  file.writeAsStringSync(contents);
}

class _Options {
  const _Options({
    required this.inputPath,
    required this.sqlOutPath,
    required this.dartOutPath,
    required this.entityFilter,
    required this.recursive,
    required this.showHelp,
  });

  final String inputPath;
  final String? sqlOutPath;
  final String? dartOutPath;
  final Set<String>? entityFilter;
  final bool recursive;
  final bool showHelp;
}

class _TableSpec {
  const _TableSpec({
    required this.entityClassName,
    required this.tableName,
    required this.driftClassName,
    required this.sourceFilePath,
    required this.columns,
  });

  final String entityClassName;
  final String tableName;
  final String driftClassName;
  final String sourceFilePath;
  final List<_ColumnSpec> columns;
}

class _ColumnSpec {
  const _ColumnSpec({
    required this.fieldName,
    required this.columnName,
    required this.mappedType,
    required this.nullable,
    required this.isId,
    required this.autoIncrement,
  });

  final String fieldName;
  final String columnName;
  final _MappedType mappedType;
  final bool nullable;
  final bool isId;
  final bool autoIncrement;
}

enum _MappedType { integer, real, text, boolean, dateTime }
