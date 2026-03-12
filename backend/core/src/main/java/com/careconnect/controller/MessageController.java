package com.careconnect.controller;

import com.careconnect.model.Message;
import com.careconnect.model.User;
import com.careconnect.dto.InboxMessageDto;
import com.careconnect.dto.FileUploadResponse;
import com.careconnect.repository.CaregiverRepository;
import com.careconnect.repository.MessageRepository;
import com.careconnect.repository.PatientRepository;
import com.careconnect.repository.UserRepository;
import com.careconnect.security.Role;
import com.careconnect.service.CaregiverPatientLinkService;
import com.careconnect.service.FileManagementService;

import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.security.core.Authentication;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.web.bind.annotation.*;
import org.springframework.web.multipart.MultipartFile;

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
    private CaregiverPatientLinkService linkService;

    @Autowired
    private FileManagementService fileManagementService;

    @Autowired
    private PatientRepository patientRepo;

    @Autowired
    private CaregiverRepository caregiverRepo;

    /** Resolve a display name by checking the role-specific profile first,
     *  falling back to User.name, then to the email local-part (before @). */
    private String resolveDisplayName(User u) {
        Role role = u.getRole();
        if (role == Role.PATIENT) {
            String profileName = patientRepo.findByUserId(u.getId())
                    .map(p -> fullName(p.getFirstName(), p.getLastName()))
                    .filter(s -> !s.isBlank())
                    .orElse(null);
            if (profileName != null) {
                return profileName;
            }
        }
        if (role == Role.CAREGIVER) {
            String profileName = caregiverRepo.findByUserId(u.getId())
                    .map(c -> fullName(c.getFirstName(), c.getLastName()))
                    .filter(s -> !s.isBlank())
                    .orElse(null);
            if (profileName != null) {
                return profileName;
            }
        }

        if (u.getName() != null && !u.getName().isBlank()) {
            return u.getName().trim();
        }

        String email = u.getEmail();
        if (email != null && !email.isBlank()) {
            int atIndex = email.indexOf('@');
            return atIndex > 0 ? email.substring(0, atIndex) : email;
        }

        return "Unknown";
    }

    private static String fullName(String first, String last) {
        String f = (first != null) ? first.trim() : "";
        String l = (last  != null) ? last.trim()  : "";
        return (f + " " + l).trim();
    }

    // ✅ Send a new message
    @PostMapping("/send")
    public ResponseEntity<?> sendMessage(@RequestBody Message message) {
        // Determine which user is the caregiver and which is the patient by looking
        // at both link directions (caregiver→patient or patient→caregiver).
        Long a = message.getSenderId();
        Long b = message.getReceiverId();

        boolean caregiverToPatient = linkService.isPatientMessagingEnabled(a, b);
        boolean patientToCaregiver = linkService.isPatientMessagingEnabled(b, a);

        if (!caregiverToPatient && !patientToCaregiver) {
            return ResponseEntity.status(HttpStatus.FORBIDDEN)
                    .body("Messaging is not enabled for this caregiver-patient link.");
        }

        message.setTimestamp(LocalDateTime.now());
        message.setRead(false);
        Message saved = messageRepo.save(message);
        return ResponseEntity.ok(saved);
    }

    // ✅ Fetch full conversation between two users
    @GetMapping("/conversation")
    public ResponseEntity<List<Message>> getConversation(
            @RequestParam Long user1,
            @RequestParam Long user2
    ) {
        List<Message> conversation = messageRepo.findConversation(user1, user2);
        return ResponseEntity.ok(conversation);
    }

    // ✅ Inbox view: list all recent conversations with peer info
    @GetMapping("/inbox/{userId}")
    public ResponseEntity<List<InboxMessageDto>> getInbox(@PathVariable Long userId) {
        List<Message> messages = messageRepo.findAllUserMessages(userId);
        Map<Long, InboxMessageDto> map = new LinkedHashMap<>(); // keep order

        for (Message m : messages) {
            Long peerId = m.getSenderId().equals(userId) ? m.getReceiverId() : m.getSenderId();
            if (map.containsKey(peerId)) continue; // already got latest from this peer

            Optional<User> peer = userRepo.findById(peerId);
            if (peer.isPresent()) {
                User u = peer.get();
                String peerName = resolveDisplayName(u);
                boolean hasUnread = m.getReceiverId().equals(userId) && !m.isRead();
                String peerRole = u.getRole() != null ? u.getRole().name() : "UNKNOWN";
                InboxMessageDto dto = new InboxMessageDto(
                        m.getId(),
                        peerId,
                        peerName,
                        u.getEmail(),
                        peerRole,
                        m.getContent(),
                        m.getTimestamp(),
                        hasUnread
                );
                map.put(peerId, dto);
            }
        }

        return ResponseEntity.ok(new ArrayList<>(map.values()));
    }

    // ✅ Send a message with a file attachment (multipart)
    @PostMapping("/send-attachment")
    public ResponseEntity<?> sendAttachment(
            @RequestParam("file") MultipartFile file,
            @RequestParam Long senderId,
            @RequestParam Long receiverId
    ) {
        // Permission check (same as text message)
        boolean allowed = linkService.isPatientMessagingEnabled(senderId, receiverId)
                || linkService.isPatientMessagingEnabled(receiverId, senderId);
        if (!allowed) {
            return ResponseEntity.status(HttpStatus.FORBIDDEN)
                    .body("Messaging is not enabled for this caregiver-patient link.");
        }

        try {
            // Resolve sender role for storage
            User sender = userRepo.findById(senderId).orElse(null);
            String userType = sender != null ? sender.getRole().name() : "PATIENT";

            FileUploadResponse uploaded = fileManagementService.uploadFile(
                    file, senderId, userType, "CHAT_ATTACHMENT", "Chat attachment", null);

            Message message = new Message();
            message.setSenderId(senderId);
            message.setReceiverId(receiverId);
            message.setContent(""); // no text content for pure attachment
            message.setTimestamp(LocalDateTime.now());
            message.setRead(false);
            message.setAttachmentId(uploaded.getFileId());
            message.setAttachmentName(uploaded.getOriginalFilename());
            message.setAttachmentContentType(uploaded.getContentType());
            message.setAttachmentSize(file.getSize());

            Message saved = messageRepo.save(message);
            return ResponseEntity.ok(Map.of(
                    "message", saved,
                    "downloadUrl", "/v1/api/files/" + uploaded.getFileId() + "/download"
            ));
        } catch (Exception e) {
            return ResponseEntity.status(HttpStatus.INTERNAL_SERVER_ERROR)
                    .body("Failed to upload attachment: " + e.getMessage());
        }
    }
}
