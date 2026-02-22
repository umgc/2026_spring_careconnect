/// Dart file with Semgrep violations for CI/CD testing.
class SemgrepViolation {
  final String apiSecret = 'hardcoded-api-secret-key-abc123';
  String getEndpoint() {
    return 'http://10.0.0.1:8080/api/patients';
  }
}
