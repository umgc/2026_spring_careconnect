/// Web startup hook for local DB initialization.
///
/// The current offline storage implementation is mobile-first and uses SQLCipher
/// via native libraries. Web storage can be added here later (for example,
/// Drift Web/WASM), while keeping the same startup contract.
Future<void> initializeLocalDbOnStartup() async {
  // Intentionally no-op until web local storage strategy is defined.
}
