import 'package:flutter/material.dart';

Widget buildChimeMeetingEmbed({
  required String meetingId,
  required String attendeeId,
  required String joinToken,
  required Map<String, dynamic> mediaPlacement,
  String? mediaRegion,
  String? externalUserId,
  required bool videoEnabled,
  required bool audioEnabled,
  bool enableAutoSentimentCapture = false,
  int sentimentCaptureIntervalMs = 15000,
  VoidCallback? onEndCallRequested,
  void Function(String transcript)? onTranscriptSample,
  void Function(String audioBase64, String audioFormat)? onAudioSample,
  void Function(String imageBase64)? onVideoSample,
}) {
  return Container(
    color: Colors.black,
    alignment: Alignment.center,
    child: const Text(
      'Real media rendering is currently available in web builds.',
      style: TextStyle(color: Colors.white70),
      textAlign: TextAlign.center,
    ),
  );
}
