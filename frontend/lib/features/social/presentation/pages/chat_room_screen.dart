  import 'dart:async';
  import 'package:care_connect_app/services/api_service.dart';
import 'package:flutter/foundation.dart';
  import 'package:flutter/material.dart';
import 'package:go_router/go_router.dart';
import 'package:provider/provider.dart';

  import '../../../../providers/user_provider.dart';
import '../model/message_dto.dart';

  class ChatRoomScreen extends StatefulWidget {
    final int peerUserId;
    final String peerName;

    const ChatRoomScreen({
      super.key,
      required this.peerUserId,
      required this.peerName,
    });

    @override
    State<ChatRoomScreen> createState() => _ChatRoomScreenState();
  }

  class _ChatRoomScreenState extends State<ChatRoomScreen> {
    final TextEditingController _controller = TextEditingController();

    int? _currentUserId;
    List<MessageDto> messages = [];
    bool isLoading = true;
    bool _initialLoading = true;
    Timer? _pollingTimer;
    bool _initialized = false;
    bool _videoCallAllowed = true;
    String? _videoCallBlockedReason;

    @override
    void didChangeDependencies() {
      super.didChangeDependencies();

      if (!_initialized) {
        final user = Provider.of<UserProvider>(context, listen: false).user;
        if (user == null) {
          ScaffoldMessenger.of(context).showSnackBar(
            const SnackBar(content: Text('User not logged in')),
          );
          return;
        }

        _currentUserId = user.id;
        fetchConversation();
        _startPolling();

        _initialized = true;
      }
    }

    Future<void> _refreshVideoCallPermission() async {
      final currentUser = Provider.of<UserProvider>(context, listen: false).user;
      if (currentUser == null || _currentUserId == null) {
        return;
      }

      final canCall = await ApiService.canInitiateVideoCall(
        currentUserId: _currentUserId!,
        currentUserRole: currentUser.role,
        caregiverId: currentUser.caregiverId,
        targetUserId: widget.peerUserId,
      );

      if (!mounted) return;
      setState(() {
        _videoCallAllowed = canCall;
        _videoCallBlockedReason = canCall
            ? null
            : (currentUser.role == 'PATIENT'
                  ? 'Video calling is disabled by your caregiver or no active link exists.'
                  : 'You can only call assigned patients/caregivers in your care circle.');
      });
    }

    Future<void> _handleVideoCallTap() async {
      final currentUser = Provider.of<UserProvider>(
        context,
        listen: false,
      ).user;

      if (currentUser == null || _currentUserId == null) {
        if (!mounted) return;
        ScaffoldMessenger.of(context).showSnackBar(
          const SnackBar(content: Text('User not logged in')),
        );
        return;
      }

      await _refreshVideoCallPermission();
      if (!mounted) return;

      if (!_videoCallAllowed) {
        ScaffoldMessenger.of(context).showSnackBar(
          SnackBar(
            content: Text(
              _videoCallBlockedReason ??
                  'Video calling is currently unavailable.',
            ),
          ),
        );
        return;
      }

      final callId = 'chime_call_${DateTime.now().millisecondsSinceEpoch}';
      context.push(
        '/video-call-chime'
        '?userId=$_currentUserId'
        '&recipientId=${widget.peerUserId}'
        '&userRole=${Uri.encodeComponent(currentUser.role)}'
        '&userName=${Uri.encodeComponent(currentUser.name ?? 'User')}'
        '&recipientName=${Uri.encodeComponent(widget.peerName)}'
        '&initiator=true'
        '&video=true'
        '&audio=true'
        '&callId=$callId',
      );
    }

    void _startPolling() {
      _pollingTimer = Timer.periodic(const Duration(seconds: 2), (_) {
        if (_currentUserId != null && mounted) {
          fetchConversation(silent: true);
        }
      });
    }

    @override
    void dispose() {
      _pollingTimer?.cancel();
      _controller.dispose();
      super.dispose();
    }

    Future<void> fetchConversation({bool silent = false}) async {
      if (_currentUserId == null) return;

      if (!silent && _initialLoading) {
        setState(() => isLoading = true);
      }


      try {
        final data = await ApiService.getConversation(
          user1: _currentUserId!,
          user2: widget.peerUserId,
        );

        final updatedMessages = (data)
            .map((json) => MessageDto.fromJson(json))
            .toList();

        //Only update UI if messages actually changed
        if (!listEquals(messages, updatedMessages)) {
          if (mounted) {
            setState(() {
              messages = updatedMessages;
              isLoading = false;
              _initialLoading = false;
            });
          }
        } else if (_initialLoading) {
          setState(() {
            isLoading = false;
            _initialLoading = false;
          });
        }
      } catch (e) {
        if (!silent) {
          ScaffoldMessenger.of(context).showSnackBar(
            SnackBar(content: Text('Failed to load conversation: $e')),
          );
        }
        setState(() {
          isLoading = false;
          _initialLoading = false;
        });
      }
    }

    Future<void> sendMessage() async {
      final content = _controller.text.trim();
      if (content.isEmpty || _currentUserId == null) return;

      try {
        await ApiService.sendMessage(
          senderId: _currentUserId!,
          receiverId: widget.peerUserId,
          content: content,
        );

        _controller.clear();
        await fetchConversation();
      } catch (e) {
        ScaffoldMessenger.of(
          context,
        ).showSnackBar(SnackBar(content: Text('Failed to send message')));
      }
    }

    Widget buildMessageBubble(MessageDto msg) {
      final isMe = msg.senderId == _currentUserId;

      return Align(
        alignment: isMe ? Alignment.centerRight : Alignment.centerLeft,
        child: Container(
          padding: const EdgeInsets.symmetric(vertical: 8, horizontal: 12),
          margin: const EdgeInsets.symmetric(vertical: 4, horizontal: 10),
          decoration: BoxDecoration(
            color: isMe ? Colors.blue.shade100 : Colors.grey.shade300,
            borderRadius: BorderRadius.circular(12),
          ),
          child: Text(msg.content),
        ),
      );
    }

    @override
    Widget build(BuildContext context) {
      if (_currentUserId == null) {
        return Scaffold(
          appBar: AppBar(title: const Text('Chat')),
          body: const Center(child: CircularProgressIndicator()),
        );
      }

      return Scaffold(
        appBar: AppBar(
          title: Text(widget.peerName),
          leading: IconButton(
            icon: const Icon(Icons.arrow_back),
            onPressed: () => Navigator.pop(context),
          ),
          actions: [
            IconButton(
              icon: Icon(
                Icons.videocam,
                color: null,
              ),
              tooltip: 'Start video call',
              onPressed: _handleVideoCallTap,
            ),
          ],
        ),
        body: Column(
          children: [
            Expanded(
              child: isLoading
                  ? const Center(child: CircularProgressIndicator())
                  : ListView.builder(
                      reverse: true,
                      itemCount: messages.length,
                      itemBuilder: (context, index) {
                        final reversedIndex = messages.length - 1 - index;
                        return buildMessageBubble(messages[reversedIndex]);
                      },
                    ),
            ),
            const Divider(height: 1),
            Padding(
              padding: const EdgeInsets.symmetric(horizontal: 8, vertical: 6),
              child: Row(
                children: [
                  Expanded(
                    child: TextField(
                      controller: _controller,
                      decoration: const InputDecoration(
                        hintText: 'Type a message...',
                        border: OutlineInputBorder(),
                        contentPadding: EdgeInsets.symmetric(
                          horizontal: 12,
                          vertical: 8,
                        ),
                      ),
                    ),
                  ),
                  const SizedBox(width: 8),
                  IconButton(
                    icon: const Icon(Icons.send),
                    onPressed: sendMessage,
                  ),
                ],
              ),
            ),
          ],
        ),
      );
    }
  }
