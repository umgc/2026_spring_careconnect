package com.careconnect.websocket;

import com.careconnect.model.User;
import com.careconnect.repository.UserRepository;
import com.careconnect.security.JwtTokenProvider;
import com.careconnect.security.Role;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.web.socket.CloseStatus;
import org.springframework.web.socket.TextMessage;
import org.springframework.web.socket.WebSocketSession;

import java.util.Map;
import java.util.Optional;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.*;

@ExtendWith(MockitoExtension.class)
class CallNotificationHandlerTest {

    @Mock UserRepository     userRepository;
    @Mock JwtTokenProvider   jwtTokenProvider;
    @Mock WebSocketSession   session;
    @Mock WebSocketSession   recipientSession;
    @Mock User               user;
    @Mock User               recipientUser;

    @InjectMocks CallNotificationHandler handler;

    // ────────────────── helpers ───────────────────────────────────────────────

    /** Authenticate `sess` as `usr` (userId, email). Stubs are lenient so they
     *  can safely be declared even when not exercised by a particular code path. */
    private void authenticate(WebSocketSession sess, String sessionId,
                              User usr, Long userId, String email, String token) throws Exception {
        lenient().when(sess.getId()).thenReturn(sessionId);
        lenient().when(jwtTokenProvider.validateToken(token)).thenReturn(true);
        lenient().when(jwtTokenProvider.getEmailFromToken(token)).thenReturn(email);
        lenient().when(userRepository.findByEmail(email)).thenReturn(Optional.of(usr));
        lenient().when(usr.getId()).thenReturn(userId);
        lenient().when(usr.getEmail()).thenReturn(email);
        lenient().when(usr.getRole()).thenReturn(Role.PATIENT);

        final String json = "{\"type\":\"authenticate\",\"token\":\"" + token + "\"}";
        handler.handleTextMessage(sess, new TextMessage(json));
    }

    // ─── afterConnectionEstablished() ────────────────────────────────────────

    @Test
    void afterConnectionEstablished_sendsConnectionEstablishedMessage() throws Exception {
        when(session.getId()).thenReturn("s1");
        handler.afterConnectionEstablished(session);
        verify(session).sendMessage(any(TextMessage.class));
    }

    // ─── handleTextMessage() — exception in JSON parsing ─────────────────────

    @Test
    void handleTextMessage_invalidJson_catchesExceptionAndSendsError() throws Exception {
        when(session.getId()).thenReturn("s1");
        handler.handleTextMessage(session, new TextMessage("not-valid-json{{{"));
        verify(session).sendMessage(any(TextMessage.class));
    }

    @Test
    void handleTextMessage_invalidJson_sendMessageThrows_doesNotPropagate() throws Exception {
        when(session.getId()).thenReturn("s1");
        doThrow(new RuntimeException("io-err")).when(session).sendMessage(any());
        handler.handleTextMessage(session, new TextMessage("not-valid-json{{{"));
        // No exception escapes
    }

    // ─── authenticate — token null / invalid ─────────────────────────────────

    @Test
    void authenticate_nullToken_sendsAuthFailedAndClosesSession() throws Exception {
        when(session.getId()).thenReturn("s1");
        handler.handleTextMessage(session, new TextMessage("{\"type\":\"authenticate\"}"));
        verify(session, atLeastOnce()).sendMessage(any(TextMessage.class));
        verify(session).close(any(CloseStatus.class));
    }

    @Test
    void authenticate_invalidToken_sendsAuthFailedAndClosesSession() throws Exception {
        when(session.getId()).thenReturn("s1");
        when(jwtTokenProvider.validateToken("bad-token")).thenReturn(false);
        handler.handleTextMessage(session,
                new TextMessage("{\"type\":\"authenticate\",\"token\":\"bad-token\"}"));
        verify(session, atLeastOnce()).sendMessage(any(TextMessage.class));
        verify(session).close(any(CloseStatus.class));
    }

    @Test
    void authenticate_userNotFound_sendsAuthFailedAndClosesSession() throws Exception {
        when(session.getId()).thenReturn("s1");
        when(jwtTokenProvider.validateToken("t")).thenReturn(true);
        when(jwtTokenProvider.getEmailFromToken("t")).thenReturn("x@x.com");
        when(userRepository.findByEmail("x@x.com")).thenReturn(Optional.empty());
        handler.handleTextMessage(session,
                new TextMessage("{\"type\":\"authenticate\",\"token\":\"t\"}"));
        verify(session, atLeastOnce()).sendMessage(any(TextMessage.class));
        verify(session).close(any(CloseStatus.class));
    }

    @Test
    void authenticate_validToken_sendsAuthSuccessAndStoresSession() throws Exception {
        when(session.getId()).thenReturn("s1");
        when(user.getId()).thenReturn(1L);
        when(user.getEmail()).thenReturn("u@u.com");
        when(user.getRole()).thenReturn(Role.PATIENT);
        when(jwtTokenProvider.validateToken("tok")).thenReturn(true);
        when(jwtTokenProvider.getEmailFromToken("tok")).thenReturn("u@u.com");
        when(userRepository.findByEmail("u@u.com")).thenReturn(Optional.of(user));

        handler.handleTextMessage(session,
                new TextMessage("{\"type\":\"authenticate\",\"token\":\"tok\"}"));
        verify(session, atLeastOnce()).sendMessage(any(TextMessage.class));
    }

    // ─── join-user-room ───────────────────────────────────────────────────────

    @Test
    void joinUserRoom_notAuthenticated_sendsError() throws Exception {
        when(session.getId()).thenReturn("s1");
        handler.handleTextMessage(session,
                new TextMessage("{\"type\":\"join-user-room\"}"));
        verify(session).sendMessage(any(TextMessage.class));
    }

    @Test
    void joinUserRoom_authenticated_sendsUserJoined() throws Exception {
        authenticate(session, "s1", user, 1L, "u@u.com", "tok1");

        handler.handleTextMessage(session,
                new TextMessage("{\"type\":\"join-user-room\"}"));
        verify(session, atLeast(2)).sendMessage(any(TextMessage.class));
    }

    // ─── send-video-call-invitation ───────────────────────────────────────────

    @Test
    void callInvitation_notAuthenticated_sendsError() throws Exception {
        when(session.getId()).thenReturn("s1");
        handler.handleTextMessage(session,
                new TextMessage("{\"type\":\"send-video-call-invitation\",\"recipientId\":\"2\",\"callId\":\"c1\"}"));
        verify(session).sendMessage(any(TextMessage.class));
    }

    @Test
    void callInvitation_recipientNotFound_sendsError() throws Exception {
        authenticate(session, "s1", user, 1L, "u@u.com", "tok1");
        when(userRepository.findById(99L)).thenReturn(Optional.empty());

        handler.handleTextMessage(session,
                new TextMessage("{\"type\":\"send-video-call-invitation\",\"recipientId\":\"99\",\"callId\":\"c1\"}"));
        verify(session, atLeast(2)).sendMessage(any(TextMessage.class));
    }

    @Test
    void callInvitation_recipientOnline_sendsInvitationAndConfirmation() throws Exception {
        // Authenticate sender
        authenticate(session, "s1", user, 1L, "u@u.com", "tok1");
        when(user.getName()).thenReturn("Alice");

        // Authenticate recipient so their session is stored
        authenticate(recipientSession, "s2", recipientUser, 2L, "r@r.com", "tok2");
        when(recipientUser.getName()).thenReturn("Bob");
        when(recipientSession.isOpen()).thenReturn(true);

        when(userRepository.findById(2L)).thenReturn(Optional.of(recipientUser));

        handler.handleTextMessage(session,
                new TextMessage("{\"type\":\"send-video-call-invitation\","
                        + "\"recipientId\":\"2\",\"callId\":\"c1\"}"));

        verify(recipientSession, atLeast(1)).sendMessage(any(TextMessage.class));
        verify(session, atLeast(2)).sendMessage(any(TextMessage.class));
    }

    @Test
    void callInvitation_recipientOffline_sendsFailureToSender() throws Exception {
        authenticate(session, "s1", user, 1L, "u@u.com", "tok1");
        when(userRepository.findById(2L)).thenReturn(Optional.of(recipientUser));
        when(recipientUser.getEmail()).thenReturn("r@r.com");

        // recipientUser session is NOT registered → offline
        handler.handleTextMessage(session,
                new TextMessage("{\"type\":\"send-video-call-invitation\","
                        + "\"recipientId\":\"2\",\"callId\":\"c1\"}"));

        verify(session, atLeast(2)).sendMessage(any(TextMessage.class));
    }

    // getUserDisplayName — null/empty name → email branch

    @Test
    void callInvitation_senderNameNull_usesEmail() throws Exception {
        // Authenticate sender (name=null → falls back to email in getUserDisplayName)
        authenticate(session, "s1", user, 1L, "u@u.com", "tok1");
        when(user.getName()).thenReturn(null);

        // Authenticate recipient so the online path is taken and getUserDisplayName is exercised
        authenticate(recipientSession, "s2", recipientUser, 2L, "r@r.com", "tok2");
        when(recipientUser.getName()).thenReturn(null);
        when(recipientUser.getEmail()).thenReturn("r@r.com");
        when(recipientSession.isOpen()).thenReturn(true);

        when(userRepository.findById(2L)).thenReturn(Optional.of(recipientUser));

        handler.handleTextMessage(session,
                new TextMessage("{\"type\":\"send-video-call-invitation\","
                        + "\"recipientId\":\"2\",\"callId\":\"c1\"}"));
        verify(session, atLeast(2)).sendMessage(any(TextMessage.class));
    }

    // ─── send-sms-notification ────────────────────────────────────────────────

    @Test
    void smsNotification_notAuthenticated_sendsError() throws Exception {
        when(session.getId()).thenReturn("s1");
        handler.handleTextMessage(session,
                new TextMessage("{\"type\":\"send-sms-notification\",\"recipientId\":\"2\",\"message\":\"hi\"}"));
        verify(session).sendMessage(any(TextMessage.class));
    }

    @Test
    void smsNotification_recipientNotFound_sendsError() throws Exception {
        authenticate(session, "s1", user, 1L, "u@u.com", "tok1");
        when(userRepository.findById(99L)).thenReturn(Optional.empty());

        handler.handleTextMessage(session,
                new TextMessage("{\"type\":\"send-sms-notification\","
                        + "\"recipientId\":\"99\",\"message\":\"hi\"}"));
        verify(session, atLeast(2)).sendMessage(any(TextMessage.class));
    }

    @Test
    void smsNotification_recipientOnline_deliversSmsAndConfirms() throws Exception {
        authenticate(session, "s1", user, 1L, "u@u.com", "tok1");
        when(user.getName()).thenReturn("Alice");
        authenticate(recipientSession, "s2", recipientUser, 2L, "r@r.com", "tok2");
        when(recipientUser.getName()).thenReturn("Bob");
        when(recipientSession.isOpen()).thenReturn(true);
        when(userRepository.findById(2L)).thenReturn(Optional.of(recipientUser));

        handler.handleTextMessage(session,
                new TextMessage("{\"type\":\"send-sms-notification\","
                        + "\"recipientId\":\"2\",\"message\":\"hi\"}"));

        verify(recipientSession, atLeast(1)).sendMessage(any(TextMessage.class));
        verify(session, atLeast(2)).sendMessage(any(TextMessage.class));
    }

    @Test
    void smsNotification_recipientOffline_sendsFailureToSender() throws Exception {
        authenticate(session, "s1", user, 1L, "u@u.com", "tok1");
        when(userRepository.findById(2L)).thenReturn(Optional.of(recipientUser));
        when(recipientUser.getEmail()).thenReturn("r@r.com");

        handler.handleTextMessage(session,
                new TextMessage("{\"type\":\"send-sms-notification\","
                        + "\"recipientId\":\"2\",\"message\":\"hi\"}"));
        verify(session, atLeast(2)).sendMessage(any(TextMessage.class));
    }

    // ─── accept-call ──────────────────────────────────────────────────────────

    @Test
    void acceptCall_notAuthenticated_sendsError() throws Exception {
        when(session.getId()).thenReturn("s1");
        handler.handleTextMessage(session,
                new TextMessage("{\"type\":\"accept-call\",\"callId\":\"c1\",\"senderId\":\"2\"}"));
        verify(session).sendMessage(any(TextMessage.class));
    }

    @Test
    void acceptCall_authenticated_senderOnline_notifiesSender() throws Exception {
        authenticate(session, "s1", user, 1L, "u@u.com", "tok1");
        when(user.getName()).thenReturn("Alice");

        authenticate(recipientSession, "s2", recipientUser, 2L, "r@r.com", "tok2");
        when(recipientSession.isOpen()).thenReturn(true);

        handler.handleTextMessage(session,
                new TextMessage("{\"type\":\"accept-call\",\"callId\":\"c1\",\"senderId\":\"2\"}"));
        verify(recipientSession, atLeast(1)).sendMessage(any(TextMessage.class));
    }

    @Test
    void acceptCall_authenticated_senderOffline_noNotification() throws Exception {
        authenticate(session, "s1", user, 1L, "u@u.com", "tok1");

        // sender "99" has no registered session
        handler.handleTextMessage(session,
                new TextMessage("{\"type\":\"accept-call\",\"callId\":\"c1\",\"senderId\":\"99\"}"));
        // Only the authentication success message is sent
        verify(session, atMost(2)).sendMessage(any(TextMessage.class));
    }

    // ─── decline-call ─────────────────────────────────────────────────────────

    @Test
    void declineCall_notAuthenticated_sendsError() throws Exception {
        when(session.getId()).thenReturn("s1");
        handler.handleTextMessage(session,
                new TextMessage("{\"type\":\"decline-call\",\"callId\":\"c1\",\"senderId\":\"2\"}"));
        verify(session).sendMessage(any(TextMessage.class));
    }

    @Test
    void declineCall_authenticated_senderOnline_notifiesSender() throws Exception {
        authenticate(session, "s1", user, 1L, "u@u.com", "tok1");
        when(user.getName()).thenReturn("Alice");

        authenticate(recipientSession, "s2", recipientUser, 2L, "r@r.com", "tok2");
        when(recipientSession.isOpen()).thenReturn(true);

        handler.handleTextMessage(session,
                new TextMessage("{\"type\":\"decline-call\",\"callId\":\"c1\","
                        + "\"senderId\":\"2\",\"reason\":\"busy\"}"));
        verify(recipientSession, atLeast(1)).sendMessage(any(TextMessage.class));
    }

    @Test
    void declineCall_authenticated_senderOffline_noNotification() throws Exception {
        authenticate(session, "s1", user, 1L, "u@u.com", "tok1");

        handler.handleTextMessage(session,
                new TextMessage("{\"type\":\"decline-call\",\"callId\":\"c1\",\"senderId\":\"99\"}"));
        verify(session, atMost(2)).sendMessage(any(TextMessage.class));
    }

    // ─── end-call ─────────────────────────────────────────────────────────────

    @Test
    void endCall_notAuthenticated_sendsError() throws Exception {
        when(session.getId()).thenReturn("s1");
        handler.handleTextMessage(session,
                new TextMessage("{\"type\":\"end-call\",\"callId\":\"c1\",\"otherPartyId\":\"2\"}"));
        verify(session).sendMessage(any(TextMessage.class));
    }

    @Test
    void endCall_authenticated_otherPartyOnline_notifiesOtherParty() throws Exception {
        authenticate(session, "s1", user, 1L, "u@u.com", "tok1");
        when(user.getName()).thenReturn("Alice");

        authenticate(recipientSession, "s2", recipientUser, 2L, "r@r.com", "tok2");
        when(recipientSession.isOpen()).thenReturn(true);

        handler.handleTextMessage(session,
                new TextMessage("{\"type\":\"end-call\",\"callId\":\"c1\",\"otherPartyId\":\"2\"}"));
        verify(recipientSession, atLeast(1)).sendMessage(any(TextMessage.class));
    }

    @Test
    void endCall_authenticated_otherPartyOffline_noNotification() throws Exception {
        authenticate(session, "s1", user, 1L, "u@u.com", "tok1");

        handler.handleTextMessage(session,
                new TextMessage("{\"type\":\"end-call\",\"callId\":\"c1\",\"otherPartyId\":\"99\"}"));
        verify(session, atMost(2)).sendMessage(any(TextMessage.class));
    }

    // ─── heartbeat ────────────────────────────────────────────────────────────

    @Test
    void heartbeat_sendsHeartbeatResponse() throws Exception {
        when(session.getId()).thenReturn("s1");
        handler.handleTextMessage(session,
                new TextMessage("{\"type\":\"heartbeat\"}"));
        verify(session).sendMessage(any(TextMessage.class));
    }

    // ─── unknown type ─────────────────────────────────────────────────────────

    @Test
    void unknownType_logsWarningAndSendsError() throws Exception {
        when(session.getId()).thenReturn("s1");
        handler.handleTextMessage(session,
                new TextMessage("{\"type\":\"some-unknown-type\"}"));
        verify(session).sendMessage(any(TextMessage.class));
    }

    // ─── afterConnectionClosed() ─────────────────────────────────────────────

    @Test
    void afterConnectionClosed_withAuthenticatedUser_removesEntries() throws Exception {
        authenticate(session, "s1", user, 1L, "u@u.com", "tok1");

        handler.afterConnectionClosed(session, CloseStatus.NORMAL);

        // User should be gone — getOnlineUsers returns empty
        assertThat(handler.getOnlineUsers()).doesNotContainKey("1");
    }

    @Test
    void afterConnectionClosed_withNoUser_doesNotThrow() throws Exception {
        when(session.getId()).thenReturn("s1");
        handler.afterConnectionClosed(session, CloseStatus.NORMAL);
        // Just verifying no exception
    }

    // ─── handleTransportError() ──────────────────────────────────────────────

    @Test
    void handleTransportError_withAuthenticatedUser_logsUserEmail() throws Exception {
        authenticate(session, "s1", user, 1L, "u@u.com", "tok1");
        handler.handleTransportError(session, new RuntimeException("err"));
        // No exception expected
    }

    @Test
    void handleTransportError_withNoUser_logsUnknown() throws Exception {
        when(session.getId()).thenReturn("s1");
        handler.handleTransportError(session, new RuntimeException("err"));
        // No exception expected
    }

    // ─── sendNotificationToUser() (public) ───────────────────────────────────

    @Test
    void sendNotificationToUser_sessionOpen_sendsMessage() throws Exception {
        authenticate(session, "s1", user, 1L, "u@u.com", "tok1");
        when(session.isOpen()).thenReturn(true);

        handler.sendNotificationToUser("1", Map.of("type", "test"));

        verify(session, atLeast(2)).sendMessage(any(TextMessage.class));
    }

    @Test
    void sendNotificationToUser_sessionNotOpen_skips() throws Exception {
        authenticate(session, "s1", user, 1L, "u@u.com", "tok1");
        when(session.isOpen()).thenReturn(false);

        handler.sendNotificationToUser("1", Map.of("type", "test"));

        // Only the auth-success message was sent
        verify(session, atMost(2)).sendMessage(any(TextMessage.class));
    }

    @Test
    void sendNotificationToUser_sendThrows_doesNotPropagate() throws Exception {
        authenticate(session, "s1", user, 1L, "u@u.com", "tok1");
        when(session.isOpen()).thenReturn(true);
        doThrow(new RuntimeException("io")).when(session).sendMessage(any());

        // Must not propagate
        handler.sendNotificationToUser("1", Map.of("type", "test"));
    }

    @Test
    void sendNotificationToUser_unknownUser_skips() throws Exception {
        handler.sendNotificationToUser("999", Map.of("type", "test"));
        // No interaction with session
        verifyNoInteractions(session);
    }

    // ─── getOnlineUsers() ────────────────────────────────────────────────────

    @Test
    void getOnlineUsers_returnsMapOfIdToEmail() throws Exception {
        authenticate(session, "s1", user, 1L, "u@u.com", "tok1");

        final Map<String, String> online = handler.getOnlineUsers();

        assertThat(online).containsEntry("1", "u@u.com");
    }

    // ─── sendCallInvitation() & sendSMSNotification() ────────────────────────

    @Test
    void sendCallInvitation_delegatesToSendNotificationToUser() throws Exception {
        authenticate(session, "s1", user, 1L, "u@u.com", "tok1");
        when(session.isOpen()).thenReturn(true);

        handler.sendCallInvitation("1", Map.of("type", "call-invite"));

        verify(session, atLeast(2)).sendMessage(any(TextMessage.class));
    }

    @Test
    void sendSMSNotification_delegatesToSendNotificationToUser() throws Exception {
        authenticate(session, "s1", user, 1L, "u@u.com", "tok1");
        when(session.isOpen()).thenReturn(true);

        handler.sendSMSNotification("1", Map.of("type", "sms"));

        verify(session, atLeast(2)).sendMessage(any(TextMessage.class));
    }
}
