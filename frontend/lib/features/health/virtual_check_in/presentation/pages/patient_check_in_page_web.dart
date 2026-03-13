import 'package:flutter/material.dart';

class PatientVirtualCheckIn extends StatelessWidget {
  const PatientVirtualCheckIn({super.key});

  @override
  Widget build(BuildContext context) {
    return Scaffold(
      appBar: AppBar(title: const Text('Virtual Check-In')),
      body: const Center(
        child: Padding(
          padding: EdgeInsets.all(24),
          child: Text(
            'Virtual check-in camera flow is available on mobile apps. '
            'Use mood and text check-in features on web.',
            textAlign: TextAlign.center,
          ),
        ),
      ),
    );
  }
}
