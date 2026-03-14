package com.careconnect.controller;

import com.careconnect.security.Permission;
import com.careconnect.security.RequirePermission;

import com.careconnect.model.Message;
import com.careconnect.model.User;
import com.careconnect.dto.InboxMessageDto;
import com.careconnect.repository.MessageRepository;
import com.careconnect.repository.UserRepository;

import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;

import com.careconnect.security.AuthorizationService;
import com.careconnect.security.UnauthorizedException;
import com.careconnect.util.SecurityUtil;

import java.time.LocalDateTime;
import java.util.*;

@RestController
@RequestMapping("/v1/api/messages")
public class MessageController {

    @Autowired
    private MessageRepository messageRepo;

    @Autowired
    private UserRepository userRepo;

    @Autowired
    private SecurityUtil securityUtil;

    @Autowired
    private AuthorizationService authorizationService;

    // ✅ Send a new message
    @RequirePermission(Permission.CREATE_TASKS)

    @PostMapping("/send")
    public ResponseEntity<Message> sendMessage(@RequestBody Message message) throws UnauthorizedException {
        User currentUser = securityUtil.resolveCurrentUser();
        authorizationService.requireSelfOrAdmin(currentUser, message.getSenderId());
        message.setTimestamp(LocalDateTime.now());
        message.setRead(false);
        Message saved = messageRepo.save(message);
        return ResponseEntity.ok(saved);
    }

    // ✅ Fetch full conversation between two users
    @RequirePermission(Permission.VIEW_ASSIGNED_PATIENTS)

    @GetMapping("/conversation")
    public ResponseEntity<List<Message>> getConversation(
            @RequestParam Long user1,
            @RequestParam Long user2
    ) throws UnauthorizedException {
        User currentUser = securityUtil.resolveCurrentUser();
        Long currentUserId = currentUser.getId();
        if (!currentUserId.equals(user1) && !currentUserId.equals(user2) && !currentUser.isAdmin()) {
            throw new UnauthorizedException("You can only view conversations you are a participant in");
        }
        List<Message> conversation = messageRepo.findConversation(user1, user2);
        return ResponseEntity.ok(conversation);
    }

    // ✅ Inbox view: list all recent conversations with peer info
    @RequirePermission(Permission.VIEW_ASSIGNED_PATIENTS)

    @GetMapping("/inbox/{userId}")
    public ResponseEntity<List<InboxMessageDto>> getInbox(@PathVariable Long userId) throws UnauthorizedException {
        User currentUser = securityUtil.resolveCurrentUser();
        authorizationService.requireSelfOrAdmin(currentUser, userId);
        List<Message> messages = messageRepo.findAllUserMessages(userId);
        Map<Long, InboxMessageDto> map = new LinkedHashMap<>(); // keep order

        for (Message m : messages) {
            Long peerId = m.getSenderId().equals(userId) ? m.getReceiverId() : m.getSenderId();
            if (map.containsKey(peerId)) continue; // already got latest from this peer

            Optional<User> peer = userRepo.findById(peerId);
            if (peer.isPresent()) {
                User u = peer.get();
                InboxMessageDto dto = new InboxMessageDto(
                        m.getId(),
                        peerId,
                        u.getName(),
                        u.getEmail(),
                        u.getRole().toString(),
                        m.getContent(),
                        m.getTimestamp(),
                        !m.isRead()
                );
                map.put(peerId, dto);
            }
        }

        return ResponseEntity.ok(new ArrayList<>(map.values()));
    }
}