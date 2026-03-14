import 'package:flutter/material.dart';
import 'package:flutter_test/flutter_test.dart';
import 'package:shared_preferences/shared_preferences.dart';

import 'package:care_connect_app/providers/theme_provider.dart';

void main() {
  // TestWidgetsFlutterBinding is required because ThemeProvider.isDarkMode
  // reads SchedulerBinding.instance.platformDispatcher.platformBrightness.
  TestWidgetsFlutterBinding.ensureInitialized();

  setUp(() {
    // Start each test with an empty SharedPreferences store so that
    // saved preferences from one test do not bleed into the next.
    SharedPreferences.setMockInitialValues(<String, Object>{});
  });

  group('ThemeProvider initial state', () {
    test('defaults to ThemeMode.system before any preference is saved', () {
      // Verifies that a freshly constructed provider uses system mode,
      // which defers the light/dark decision to the OS.
      final provider = ThemeProvider();
      expect(provider.themeMode, ThemeMode.system);
    });
  });

  group('ThemeProvider.setThemeMode', () {
    test('updates themeMode to light and persists "light" to SharedPreferences',
        () async {
      // Verifies that setThemeMode changes the in-memory mode and writes
      // the canonical string value used by _loadThemePreference on restart.
      final provider = ThemeProvider();
      await provider.setThemeMode(ThemeMode.light);

      expect(provider.themeMode, ThemeMode.light);
      final prefs = await SharedPreferences.getInstance();
      expect(prefs.getString('theme_mode'), 'light');
    });

    test('updates themeMode to dark and persists "dark" to SharedPreferences',
        () async {
      // Verifies the dark-mode persistence branch.
      final provider = ThemeProvider();
      await provider.setThemeMode(ThemeMode.dark);

      expect(provider.themeMode, ThemeMode.dark);
      final prefs = await SharedPreferences.getInstance();
      expect(prefs.getString('theme_mode'), 'dark');
    });

    test('updates themeMode to system and persists "system" to SharedPreferences',
        () async {
      // Verifies that resetting to system mode also writes the correct key.
      final provider = ThemeProvider();
      await provider.setThemeMode(ThemeMode.dark);   // change away first
      await provider.setThemeMode(ThemeMode.system);

      expect(provider.themeMode, ThemeMode.system);
      final prefs = await SharedPreferences.getInstance();
      expect(prefs.getString('theme_mode'), 'system');
    });

    test('notifies listeners after every mode change', () async {
      // Verifies ChangeNotifier behaviour: every setThemeMode call fires
      // notifyListeners so that widgets rebuild correctly.
      // Await a microtask first so the constructor's async _loadThemePreference
      // finishes before we attach the listener (its own notifyListeners call
      // must not inflate our count).
      final provider = ThemeProvider();
      await Future<void>.delayed(Duration.zero);

      int notifyCount = 0;
      provider.addListener(() => notifyCount++);

      await provider.setThemeMode(ThemeMode.light);
      await provider.setThemeMode(ThemeMode.dark);

      expect(notifyCount, 2);
    });
  });

  group('ThemeProvider._loadThemePreference (via constructor)', () {
    test('loads saved "light" preference on construction', () async {
      // Verifies that a provider constructed after a "light" pref was saved
      // correctly restores that mode from SharedPreferences.
      SharedPreferences.setMockInitialValues(
          <String, Object>{'theme_mode': 'light'});
      final provider = ThemeProvider();
      // Wait one microtask cycle for the async _loadThemePreference to finish.
      await Future<void>.delayed(Duration.zero);

      expect(provider.themeMode, ThemeMode.light);
    });

    test('loads saved "dark" preference on construction', () async {
      // Verifies the dark restore branch.
      SharedPreferences.setMockInitialValues(
          <String, Object>{'theme_mode': 'dark'});
      final provider = ThemeProvider();
      await Future<void>.delayed(Duration.zero);

      expect(provider.themeMode, ThemeMode.dark);
    });

    test('loads saved "system" preference on construction', () async {
      // Verifies the system restore branch (also the switch default).
      SharedPreferences.setMockInitialValues(
          <String, Object>{'theme_mode': 'system'});
      final provider = ThemeProvider();
      await Future<void>.delayed(Duration.zero);

      expect(provider.themeMode, ThemeMode.system);
    });

    test('stays at system when no preference has been saved', () async {
      // Verifies that an absent key leaves the default system mode intact.
      SharedPreferences.setMockInitialValues(<String, Object>{});
      final provider = ThemeProvider();
      await Future<void>.delayed(Duration.zero);

      expect(provider.themeMode, ThemeMode.system);
    });
  });

  group('ThemeProvider.isDarkMode', () {
    test('returns false when mode is explicitly light', () async {
      // Verifies the non-system light branch of the getter.
      final provider = ThemeProvider();
      await provider.setThemeMode(ThemeMode.light);

      expect(provider.isDarkMode, isFalse);
    });

    test('returns true when mode is explicitly dark', () async {
      // Verifies the non-system dark branch of the getter.
      final provider = ThemeProvider();
      await provider.setThemeMode(ThemeMode.dark);

      expect(provider.isDarkMode, isTrue);
    });

    test('returns a bool when mode is system (delegates to platform brightness)',
        () async {
      // Verifies that the system branch falls through to the platform
      // brightness check without throwing. The actual value depends on
      // the test runner's reported platform brightness (light by default).
      final provider = ThemeProvider();
      expect(provider.isDarkMode, isA<bool>());
    });
  });

  group('ThemeProvider.toggleTheme', () {
    test('switches from light to dark', () async {
      // Verifies that toggle inverts explicit light mode.
      final provider = ThemeProvider();
      await provider.setThemeMode(ThemeMode.light);
      await provider.toggleTheme();

      expect(provider.themeMode, ThemeMode.dark);
    });

    test('switches from dark to light', () async {
      // Verifies that toggle inverts explicit dark mode.
      final provider = ThemeProvider();
      await provider.setThemeMode(ThemeMode.dark);
      await provider.toggleTheme();

      expect(provider.themeMode, ThemeMode.light);
    });

    test('switches from system (platform light) to dark', () async {
      // When system mode is active and the platform reports light brightness
      // (the test runner default), toggleTheme should choose dark.
      final provider = ThemeProvider();
      // provider starts in system mode; platform brightness is light in tests
      await provider.toggleTheme();

      expect(provider.themeMode, ThemeMode.dark);
    });

    test('notifies listeners after toggle', () async {
      // Verifies that the toggle notifies widgets so they can rebuild.
      final provider = ThemeProvider();
      await provider.setThemeMode(ThemeMode.light);

      bool notified = false;
      provider.addListener(() => notified = true);
      await provider.toggleTheme();

      expect(notified, isTrue);
    });
  });
}
