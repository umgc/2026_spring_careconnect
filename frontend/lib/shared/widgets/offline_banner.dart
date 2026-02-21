import 'package:flutter/material.dart';
import 'package:shared_preferences/shared_preferences.dart';

class OfflineBanner extends StatefulWidget {
  const OfflineBanner({super.key});

  @override
  State<OfflineBanner> createState() => _OfflineBannerState();
}

class _OfflineBannerState extends State<OfflineBanner> {
  bool _isOffline = false;

  @override
  void initState() {
    super.initState();
    _loadOffline();
  }

  Future<void> _loadOffline() async {
    final prefs = await SharedPreferences.getInstance();
    setState(() {
      _isOffline = prefs.getBool('offline_mode') ?? false;
    });
  }

  @override
  Widget build(BuildContext context) {
    if (!_isOffline) return const SizedBox();

    return Container(
      width: double.infinity,
      padding: const EdgeInsets.symmetric(vertical: 10, horizontal: 16),
      color: Colors.orange.shade700,
      child: const Row(
        children: [
          Icon(Icons.cloud_off, color: Colors.white),
          SizedBox(width: 10),
          Expanded(
            child: Text(
              "Offline Mode Active – Data will sync when reconnected",
              style: TextStyle(color: Colors.white),
            ),
          ),
        ],
      ),
    );
  }
}
