import 'package:flutter/widgets.dart';

import 'chime_meeting_embed_stub.dart'
    if (dart.library.html) 'chime_meeting_embed_web.dart' as platform;

Widget buildChimeMeetingEmbed({
  required String meetingId,
  required String attendeeId,
  required String joinToken,
  required Map<String, dynamic> mediaPlacement,
  String? mediaRegion,
  String? externalUserId,
  required bool videoEnabled,
  required bool audioEnabled,
  VoidCallback? onEndCallRequested,
}) {
  return platform.buildChimeMeetingEmbed(
    meetingId: meetingId,
    attendeeId: attendeeId,
    joinToken: joinToken,
    mediaPlacement: mediaPlacement,
    mediaRegion: mediaRegion,
    externalUserId: externalUserId,
    videoEnabled: videoEnabled,
    audioEnabled: audioEnabled,
    onEndCallRequested: onEndCallRequested,
  );
}
