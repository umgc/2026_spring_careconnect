import 'package:flutter_test/flutter_test.dart';
import 'package:care_connect_app/services/local_db/connectivity_router_service.dart';

void main() {
  group('Connectivity Router Tests', () {
    test('Runs online branch when connected', () async {
      final router = ConnectivityRouterService(isOnline: () async => true);

      final result = await router.route(
        online: () async => "online",
        offline: () async => "offline",
      );

      expect(result, "online");
    });

    test('Runs offline branch when disconnected', () async {
      final router = ConnectivityRouterService(isOnline: () async => false);

      final result = await router.route(
        online: () async => "online",
        offline: () async => "offline",
      );

      expect(result, "offline");
    });
  });
}
