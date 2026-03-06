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
  void Function(double averageLevel, double speechRatio, double variability)? onVoiceMetricsSample,
  void Function(String imageBase64)? onVideoSample,
  void Function(String channel, bool muted)? onSentimentChannelState,
}) {
  return Container(
    color: Colors.black,
    alignment: Alignment.center,
    child: const Text(
      'Real media rendering is currently available in web builds. Participant connection status is unavailable in this environment.',
      style: TextStyle(color: Colors.white70),
      textAlign: TextAlign.center,
    ),
  );
}

Future<bool> requestChimeAudioToggle({
  required bool muted,
  String? meetingId,
}) async {
  return false;
}

Future<bool> requestChimeVideoToggle({
  required bool muted,
  String? meetingId,
}) async {
  return false;
}

Future<bool> requestChimeCameraSwitch({String? meetingId}) async {
  return false;
}

Future<bool> requestChimeSentimentChannelRestart({
  required String channel,
  String? meetingId,
}) async {
  return false;
}
