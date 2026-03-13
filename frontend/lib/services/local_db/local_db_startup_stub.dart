/// Stub startup hook for platforms not using native sqlite.
Future<void> initializeLocalDbOnStartup() async {
  // No-op for platforms where native sqlite setup is not used.
}
