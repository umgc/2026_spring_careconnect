import 'package:connectivity_plus/connectivity_plus.dart';

class ConnectivityRouterService {
  ConnectivityRouterService({Connectivity? connectivity})
      : _connectivity = connectivity ?? Connectivity();

  final Connectivity _connectivity;

  Future<T> route<T>({
    required Future<T> Function() online,
    required Future<T> Function() offline,
    bool fallbackToOfflineOnOnlineError = false,
  }) async {
    final currentlyOnline = await _isCurrentlyOnline();

    if (!currentlyOnline) {
      return offline();
    }

    try {
      return await online();
    } catch (error) {
      if (fallbackToOfflineOnOnlineError && _isLikelyNetworkFailure(error)) {
        return offline();
      }
      rethrow;
    }
  }

  Future<bool> _isCurrentlyOnline() async {
    final result = await _connectivity.checkConnectivity();

    if (result is ConnectivityResult) {
      return result != ConnectivityResult.none;
    }

    if (result is List<ConnectivityResult>) {
      return result.any((entry) => entry != ConnectivityResult.none);
    }

    return true;
  }

  bool _isLikelyNetworkFailure(Object error) {
    final message = error.toString().toLowerCase();
    return message.contains('socketexception') ||
        message.contains('failed host lookup') ||
        message.contains('network is unreachable') ||
        message.contains('connection refused') ||
        message.contains('connection reset') ||
        message.contains('timed out');
  }
}
