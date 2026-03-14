class OfflineSyncDbRow {
  const OfflineSyncDbRow({
    required this.id,
    required this.fingerprint,
    required this.method,
    required this.url,
    required this.headersJson,
    required this.bodyJson,
    required this.createdAt,
    required this.status,
    required this.retryCount,
    required this.lastError,
  });

  final String id;
  final String fingerprint;
  final String method;
  final String url;
  final String headersJson;
  final String? bodyJson;
  final DateTime createdAt;
  final String status;
  final int retryCount;
  final String? lastError;
}
