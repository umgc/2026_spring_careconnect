import 'package:care_connect_app/providers/shortcut_provider.dart';
import 'package:care_connect_app/l10n/app_localizations_en.dart';
import 'package:flutter/material.dart';
import 'package:flutter_test/flutter_test.dart';
import 'package:shared_preferences/shared_preferences.dart';

void main() {
  TestWidgetsFlutterBinding.ensureInitialized();

  group('ShortcutDef', () {
    test('isVisibleFor returns true when no role restrictions exist', () {
      const def = ShortcutDef(
        key: 'open',
        icon: Icons.home,
        label: 'Open',
        routeTemplate: '/open',
      );

      expect(def.isVisibleFor('PATIENT'), true);
      expect(def.isVisibleFor('CAREGIVER'), true);
    });

    test('isVisibleFor enforces role restrictions', () {
      const def = ShortcutDef(
        key: 'restricted',
        icon: Icons.lock,
        label: 'Restricted',
        routeTemplate: '/restricted',
        visibleFor: {'ADMIN'},
      );

      expect(def.isVisibleFor('ADMIN'), true);
      expect(def.isVisibleFor('PATIENT'), false);
    });

    test('resolveRoute replaces template values from context', () {
      const def = ShortcutDef(
        key: 'feed',
        icon: Icons.forum,
        label: 'Feed',
        routeTemplate: '/social-feed?userId={userId}&tab={tab}',
      );

      final route = def.resolveRoute({'userId': '42', 'tab': 'latest'});
      expect(route, '/social-feed?userId=42&tab=latest');
    });

    test('localizedLabel uses translation for known labelKey', () {
      const def = ShortcutDef(
        key: 'dash',
        icon: Icons.home,
        label: 'Dashboard',
        labelKey: 'dashboard',
        routeTemplate: '/dashboard',
      );

      final t = AppLocalizationsEn();
      expect(def.localizedLabel(t), 'Dashboard');
    });

    test('localizedLabel falls back to label for unknown labelKey', () {
      const def = ShortcutDef(
        key: 'custom',
        icon: Icons.star,
        label: 'Custom Label',
        labelKey: 'unknown-key',
        routeTemplate: '/custom',
      );

      final t = AppLocalizationsEn();
      expect(def.localizedLabel(t), 'Custom Label');
    });

    test('isVisibleFor is case-sensitive for role checks', () {
      const def = ShortcutDef(
        key: 'restricted',
        icon: Icons.lock,
        label: 'Restricted',
        routeTemplate: '/restricted',
        visibleFor: {'ADMIN'},
      );

      expect(def.isVisibleFor('ADMIN'), true);
      expect(def.isVisibleFor('admin'), false);
    });
  });

  group('ShortcutProvider', () {
    late ShortcutProvider provider;

    setUp(() async {
      SharedPreferences.setMockInitialValues({});
      provider = ShortcutProvider();
      await provider.init();
    });

    test('init seeds default built-in shortcuts on first run', () async {
      expect(provider.isLoaded, true);
      expect(provider.catalog.length, 9);
      expect(provider.activeKeys.length, 8);
      expect(provider.isActive('dash'), true);
      expect(provider.isActive('files'), true);
      expect(provider.isActive('gam'), false);

      final prefs = await SharedPreferences.getInstance();
      final saved = prefs.getStringList('shortcut_active_keys');
      expect(saved, isNotNull);
      expect(saved!.length, 8);
      expect(saved, containsAll(<String>[
        'dash',
        'inv',
        'cal',
        'feed',
        'meds',
        'evv',
        'wear',
        'files',
      ]));
    });

    test('init uses previously saved active keys instead of reseeding defaults', () async {
      SharedPreferences.setMockInitialValues({
        'shortcut_active_keys': <String>['gam', 'dash'],
      });
      final loaded = ShortcutProvider();
      await loaded.init();

      expect(loaded.activeKeys, unorderedEquals(<String>['gam', 'dash']));
      expect(loaded.isActive('inv'), false);
    });

    test('visibleActiveForRole ignores unknown keys loaded from saved preferences', () async {
      SharedPreferences.setMockInitialValues({
        'shortcut_active_keys': <String>['ghost', 'dash'],
      });
      final loaded = ShortcutProvider();
      await loaded.init();

      expect(loaded.isActive('ghost'), true);
      final visible = loaded.visibleActiveForRole('PATIENT');
      expect(visible.any((s) => s.key == 'ghost'), false);
      expect(visible.any((s) => s.key == 'dash'), true);
    });

    test('toggle removes and re-adds an active key', () async {
      expect(provider.isActive('dash'), true);

      await provider.toggle('dash');
      expect(provider.isActive('dash'), false);

      await provider.toggle('dash');
      expect(provider.isActive('dash'), true);
    });

    test('toggle ignores unknown key and does not change active selection', () async {
      final before = List<String>.from(provider.activeKeys);

      await provider.toggle('does-not-exist');

      expect(provider.activeKeys, before);
    });

    test('toggle enforces max shortcuts limit when already full', () async {
      expect(provider.activeKeys.length, ShortcutProvider.maxShortcuts);
      expect(provider.isActive('gam'), false);

      await provider.toggle('gam');
      expect(provider.isActive('gam'), false);
      expect(provider.activeKeys.length, ShortcutProvider.maxShortcuts);

      await provider.toggle('dash');
      await provider.toggle('gam');
      expect(provider.isActive('dash'), false);
      expect(provider.isActive('gam'), true);
      expect(provider.activeKeys.length, ShortcutProvider.maxShortcuts);
    });

    test('setAll applies only known keys and caps list size', () async {
      await provider.setAll(<String>[
        'gam',
        'dash',
        'inv',
        'cal',
        'feed',
        'meds',
        'evv',
        'wear',
        'files',
        'unknown',
      ]);

      expect(provider.activeKeys.length, ShortcutProvider.maxShortcuts);
      expect(provider.isActive('unknown'), false);
      expect(provider.isActive('files'), false);
      expect(provider.activeKeys, containsAll(<String>[
        'gam',
        'dash',
        'inv',
        'cal',
        'feed',
        'meds',
        'evv',
        'wear',
      ]));
    });

    test('setAll takes max entries before filtering unknown keys', () async {
      await provider.setAll(<String>[
        'unknown-a',
        'unknown-b',
        'dash',
        'inv',
        'cal',
        'feed',
        'meds',
        'evv',
        'wear',
        'files',
      ]);

      expect(provider.activeKeys.length, 6);
      expect(provider.isActive('dash'), true);
      expect(provider.isActive('evv'), true);
      expect(provider.isActive('wear'), false);
      expect(provider.isActive('files'), false);
    });

    test('visibleActiveForRole filters active shortcuts by role', () {
      final patientVisible = provider.visibleActiveForRole('PATIENT');
      final caregiverVisible = provider.visibleActiveForRole('CAREGIVER');

      expect(patientVisible.any((s) => s.key == 'evv'), false);
      expect(caregiverVisible.any((s) => s.key == 'evv'), true);
    });

    test('visibleCatalogForRole filters catalog by role restrictions', () {
      final patientCatalog = provider.visibleCatalogForRole('PATIENT');
      final caregiverCatalog = provider.visibleCatalogForRole('CAREGIVER');

      expect(patientCatalog.any((s) => s.key == 'evv'), false);
      expect(caregiverCatalog.any((s) => s.key == 'evv'), true);
      expect(patientCatalog.any((s) => s.key == 'gam'), true);
    });

    test('registerAll adds runtime shortcuts to catalog', () {
      provider.registerAll(const [
        ShortcutDef(
          key: 'custom',
          icon: Icons.star,
          label: 'Custom',
          routeTemplate: '/custom',
          visibleFor: {'ADMIN'},
        ),
      ]);

      expect(provider.catalog.any((s) => s.key == 'custom'), true);
      expect(
        provider.visibleCatalogForRole('PATIENT').any((s) => s.key == 'custom'),
        false,
      );
      expect(
        provider.visibleCatalogForRole('ADMIN').any((s) => s.key == 'custom'),
        true,
      );
    });

    test('registerAll overwrites existing shortcut when key collides', () {
      final before = provider.catalog.firstWhere((s) => s.key == 'dash');
      expect(before.label, 'Dashboard');

      provider.registerAll(const [
        ShortcutDef(
          key: 'dash',
          icon: Icons.star,
          label: 'Overwritten Dashboard',
          routeTemplate: '/new-dashboard',
        ),
      ]);

      final after = provider.catalog.firstWhere((s) => s.key == 'dash');
      expect(after.label, 'Overwritten Dashboard');
      expect(after.routeTemplate, '/new-dashboard');
      expect(after.icon, Icons.star);
    });
  });
}
