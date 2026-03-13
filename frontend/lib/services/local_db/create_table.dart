import 'generated/jpa_drift_bundle.dart';

/// Access point for generated `CREATE TABLE` SQL strings.
///
/// Backed by `generated/jpa_drift_bundle.dart`.
class CreateTable {
  const CreateTable._();

  /// Returns SQL for one table name (lowercase table name expected).
  static String? forTable(String tableName) {
    return generatedCreateTableFor(tableName);
  }

  /// Returns all generated table SQL statements.
  static Map<String, String> all() {
    return Map<String, String>.from(generatedCreateTableSql);
  }
}
