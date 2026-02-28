// Web-specific stub for platformViewRegistry compatibility
// This file provides a mock platformViewRegistry for web builds
// to prevent compilation errors from unsupported platform view integrations

// Mock platformViewRegistry for web builds
class _MockPlatformViewRegistry {
  void registerViewFactory(String viewType, dynamic viewFactory) {
    // No-op for web in this stub
    print(
      'Mock platformViewRegistry: Ignoring registerViewFactory for $viewType',
    );
  }
}

// Export the mock registry
final platformViewRegistry = _MockPlatformViewRegistry();
