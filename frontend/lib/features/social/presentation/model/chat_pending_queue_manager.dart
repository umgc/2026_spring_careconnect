import 'dart:convert';

import 'package:shared_preferences/shared_preferences.dart';

import 'message_dto.dart';

class ChatPendingQueueManager {
  static final Map<String, List<MessageDto>> _pendingMessageCache =
      <String, List<MessageDto>>{};

  ChatPendingQueueManager({
    this.storagePrefix = 'chat_pending_',
    this.prefsProvider = SharedPreferences.getInstance,
  });

  final String storagePrefix;
  final Future<SharedPreferences> Function() prefsProvider;

  String conversationKey(
      {required int currentUserId, required int peerUserId}) {
    final first = currentUserId < peerUserId ? currentUserId : peerUserId;
    final second = currentUserId < peerUserId ? peerUserId : currentUserId;
    return '${first}_$second';
  }

  void persistToCache(String key, List<MessageDto> pendingMessages) {
    if (pendingMessages.isEmpty) {
      _pendingMessageCache.remove(key);
      return;
    }
    _pendingMessageCache[key] = List<MessageDto>.from(pendingMessages);
  }

  List<MessageDto> restoreFromCache(String key) {
    final cached = _pendingMessageCache[key];
    if (cached == null || cached.isEmpty) {
      return <MessageDto>[];
    }
    return List<MessageDto>.from(cached);
  }

  Future<void> persistToDisk(
      String key, List<MessageDto> pendingMessages) async {
    final storageKey = '$storagePrefix$key';
    final prefs = await prefsProvider();
    if (pendingMessages.isEmpty) {
      await prefs.remove(storageKey);
      return;
    }

    final serialized =
        pendingMessages.map(_pendingMessageToJson).map(jsonEncode).toList();
    await prefs.setStringList(storageKey, serialized);
  }

  Future<List<MessageDto>> restoreFromDisk(String key) async {
    final storageKey = '$storagePrefix$key';
    final prefs = await prefsProvider();
    final serialized = prefs.getStringList(storageKey);
    if (serialized == null || serialized.isEmpty) {
      return <MessageDto>[];
    }

    final restored = <MessageDto>[];
    for (final item in serialized) {
      try {
        final decoded = jsonDecode(item);
        if (decoded is Map<String, dynamic>) {
          final msg = _pendingMessageFromJson(decoded);
          if (msg != null) {
            restored.add(msg);
          }
        } else if (decoded is Map) {
          final msg =
              _pendingMessageFromJson(Map<String, dynamic>.from(decoded));
          if (msg != null) {
            restored.add(msg);
          }
        }
      } catch (_) {}
    }

    if (restored.isEmpty) {
      await prefs.remove(storageKey);
    }

    return restored;
  }

  Map<String, dynamic> _pendingMessageToJson(MessageDto message) {
    return {
      'id': message.id,
      'senderId': message.senderId,
      'receiverId': message.receiverId,
      'content': message.content,
      'timestamp': message.timestamp.toIso8601String(),
      'attachmentId': message.attachmentId,
      'attachmentName': message.attachmentName,
      'attachmentContentType': message.attachmentContentType,
      'attachmentSize': message.attachmentSize,
    };
  }

  MessageDto? _pendingMessageFromJson(Map<String, dynamic> json) {
    final idRaw = json['id'];
    final senderRaw = json['senderId'];
    final receiverRaw = json['receiverId'];
    final contentRaw = json['content'];
    final timestampRaw = json['timestamp'];

    final id = idRaw is int ? idRaw : int.tryParse('$idRaw');
    final senderId = senderRaw is int ? senderRaw : int.tryParse('$senderRaw');
    final receiverId =
        receiverRaw is int ? receiverRaw : int.tryParse('$receiverRaw');
    final content = contentRaw?.toString();
    final timestamp = timestampRaw == null
        ? null
        : DateTime.tryParse(timestampRaw.toString());

    if (id == null ||
        senderId == null ||
        receiverId == null ||
        content == null ||
        timestamp == null) {
      return null;
    }

    return MessageDto(
      id: id,
      senderId: senderId,
      receiverId: receiverId,
      content: content,
      timestamp: timestamp,
      attachmentId: (json['attachmentId'] as num?)?.toInt(),
      attachmentName: json['attachmentName'] as String?,
      attachmentContentType: json['attachmentContentType'] as String?,
      attachmentSize: (json['attachmentSize'] as num?)?.toInt(),
    );
  }
}
