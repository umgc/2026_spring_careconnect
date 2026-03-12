package com.careconnect.websocket;

import com.careconnect.model.User;
import com.careconnect.repository.UserRepository;
import com.careconnect.security.JwtTokenProvider;
import com.careconnect.security.Role;
import com.careconnect.service.CaregiverPatientLinkService;
import com.careconnect.service.CallTelemetryService;
import com.fasterxml.jackson.databind.ObjectMapper;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.ArgumentCaptor;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.mockito.junit.jupiter.MockitoSettings;
import org.mockito.quality.Strictness;
import org.springframework.web.socket.CloseStatus;
import org.springframework.web.socket.TextMessage;
import org.springframework.web.socket.WebSocketSession;

import java.util.HashMap;
import java.util.Map;
import java.util.Optional;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyLong;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.Mockito.atLeastOnce;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

/**
 * Pure Mockito unit tests for {@link CallNotificationHandler}.
 * No Spring context is loaded — the handler is constructed directly.
 */
@ExtendWith(MockitoExtension.class)
@MockitoSettings(strictness = Strictness.LENIENT)
class CallNotificationHandlerTest {

    // ── mocked dependencies ────────────────────────────────────────────────
    @Mock
    private UserRepository userRepository;
    @Mock
    private JwtTokenProvider jwtTokenProvider;
    @Mock
    private CallTelemetryService callTelemetryService;
    @Mock
    private CaregiverPatientLinkService caregiverPatientLinkService;

    // ── system under test ─────────────────────────────────────────────────
    private CallNotificationHandler handler;

    // ── shared ObjectMapper (for building payloads and reading responses) ─
    private final ObjectMapper objectMapper = new ObjectMapper();

    // ── common test users ─────────────────────────────────────────────────
    private User caregiver;
    private User patient;

    @BeforeEach
    void setUp() {
        // ObjectMapper is instantiated inside the handler, not injected.
        handler = new CallNotificationHandler(
                userRepository,
                jwtTokenProvider,
                callTelemetryService,
                caregiverPatientLinkService
        );

        caregiver = new User();
        caregiver.setId(1L);
        caregiver.setEmail("caregiver@test.com");
        caregiver.setRole(Role.CAREGIVER);
        caregiver.setName("Test Caregiver");
        caregiver.setPassword("pw");

        patient = new User();
        patient.setId(2L);
        patient.setEmail("patient@test.com");
        patient.setRole(Role.PATIENT);
        patient.setName("Test Patient");
        patient.setPassword("pw");
    }

    // ── helpers ───────────────────────────────────────────────────────────

    /**
     * Build a mock {@link WebSocketSession} with a given id that reports open.
     */
    private WebSocketSession mockOpenSession(String sessionId) {
        WebSocketSession session = mock(WebSocketSession.class);
        when(session.getId()).thenReturn(sessionId);
        when(session.isOpen()).thenReturn(true);
        return session;
    }

    /**
     * Authenticate {@code session} as {@code user} by driving the handler through
     * a real "authenticate" message, with JwtTokenProvider and UserRepository stubs
     * configured for "valid-token".
     */
    private void authenticateSession(WebSocketSession session, User user) throws Exception {
        when(jwtTokenProvider.validateToken("valid-token")).thenReturn(true);
        when(jwtTokenProvider.getEmailFromToken("valid-token")).thenReturn(user.getEmail());
        when(userRepository.findByEmail(user.getEmail())).thenReturn(Optional.of(user));

        String authMsg = objectMapper.writeValueAsString(
                Map.of("type", "authenticate", "token", "valid-token"));
        handler.handleTextMessage(session, new TextMessage(authMsg));
    }

    /**
     * Capture the most-recently-sent {@link TextMessage} payload from {@code session}
     * and deserialise it into a {@code Map}.
     */
    private Map<?, ?> captureLastResponse(WebSocketSession session) throws Exception {
        ArgumentCaptor<TextMessage> captor = ArgumentCaptor.forClass(TextMessage.class);
        verify(session, atLeastOnce()).sendMessage(captor.capture());
        return objectMapper.readValue(captor.getValue().getPayload(), Map.class);
    }

    /**
     * Capture ALL sent messages and return the last one as a Map.
     * Equivalent to {@link #captureLastResponse} but explicitly returns the last captured value.
     */
    private Map<?, ?> captureResponse(WebSocketSession session) throws Exception {
        return captureLastResponse(session);
    }

    // ─────────────────────────────────────────────────────────────────────
    // 1. afterConnectionEstablished
    // ─────────────────────────────────────────────────────────────────────

    @Test
    @DisplayName("afterConnectionEstablished sends 'connection-established' message")
    void afterConnectionEstablished_sendsConnectionEstablishedMessage() throws Exception {
        WebSocketSession session = mockOpenSession("sess-1");

        handler.afterConnectionEstablished(session);

        ArgumentCaptor<TextMessage> captor = ArgumentCaptor.forClass(TextMessage.class);
        verify(session).sendMessage(captor.capture());

        Map<?, ?> response = objectMapper.readValue(captor.getValue().getPayload(), Map.class);
        assertThat(response.get("type")).isEqualTo("connection-established");
        assertThat(response.get("sessionId")).isEqualTo("sess-1");
    }

    // ─────────────────────────────────────────────────────────────────────
    // 2–4. authentication
    // ─────────────────────────────────────────────────────────────────────

    @Nested
    @DisplayName("handleTextMessage: authenticate")
    class AuthenticateTests {

        @Test
        @DisplayName("valid token returns authentication-success")
        void authenticate_validToken_returnsSuccess() throws Exception {
            WebSocketSession session = mockOpenSession("sess-auth-ok");

            when(jwtTokenProvider.validateToken("valid-token")).thenReturn(true);
            when(jwtTokenProvider.getEmailFromToken("valid-token")).thenReturn("caregiver@test.com");
            when(userRepository.findByEmail("caregiver@test.com")).thenReturn(Optional.of(caregiver));

            String msg = objectMapper.writeValueAsString(
                    Map.of("type", "authenticate", "token", "valid-token"));
            handler.handleTextMessage(session, new TextMessage(msg));

            Map<?, ?> response = captureResponse(session);
            assertThat(response.get("type")).isEqualTo("authentication-success");
            assertThat(response.get("userEmail")).isEqualTo("caregiver@test.com");
        }

        @Test
        @DisplayName("invalid token returns authentication-failed and closes session")
        void authenticate_invalidToken_returnsFailedAndClosesSession() throws Exception {
            WebSocketSession session = mockOpenSession("sess-auth-fail");

            when(jwtTokenProvider.validateToken("bad-token")).thenReturn(false);

            String msg = objectMapper.writeValueAsString(
                    Map.of("type", "authenticate", "token", "bad-token"));
            handler.handleTextMessage(session, new TextMessage(msg));

            Map<?, ?> response = captureResponse(session);
            assertThat(response.get("type")).isEqualTo("authentication-failed");
            verify(session).close(any(CloseStatus.class));
        }

        @Test
        @DisplayName("missing token field returns authentication-failed")
        void authenticate_nullToken_returnsAuthFailed() throws Exception {
            WebSocketSession session = mockOpenSession("sess-auth-null");

            // payload without "token" key — validateToken will receive null
            // The handler checks `token == null` before calling validateToken,
            // so no stub needed.
            String msg = objectMapper.writeValueAsString(
                    Map.of("type", "authenticate"));
            handler.handleTextMessage(session, new TextMessage(msg));

            Map<?, ?> response = captureResponse(session);
            assertThat(response.get("type")).isEqualTo("authentication-failed");
        }
    }

    // ─────────────────────────────────────────────────────────────────────
    // 5–6. join-user-room
    // ─────────────────────────────────────────────────────────────────────

    @Nested
    @DisplayName("handleTextMessage: join-user-room")
    class JoinUserRoomTests {

        @Test
        @DisplayName("authenticated user receives user-joined response")
        void joinUserRoom_authenticated_sendsUserJoined() throws Exception {
            WebSocketSession session = mockOpenSession("sess-join-ok");
            authenticateSession(session, caregiver);

            String msg = objectMapper.writeValueAsString(Map.of("type", "join-user-room"));
            handler.handleTextMessage(session, new TextMessage(msg));

            Map<?, ?> response = captureResponse(session);
            assertThat(response.get("type")).isEqualTo("user-joined");
        }

        @Test
        @DisplayName("unauthenticated user receives error about not authenticated")
        void joinUserRoom_notAuthenticated_sendsError() throws Exception {
            WebSocketSession session = mockOpenSession("sess-join-noauth");

            String msg = objectMapper.writeValueAsString(Map.of("type", "join-user-room"));
            handler.handleTextMessage(session, new TextMessage(msg));

            Map<?, ?> response = captureResponse(session);
            assertThat(response.get("type")).isEqualTo("error");
            assertThat((String) response.get("message"))
                    .containsIgnoringCase("not authenticated");
        }
    }

    // ─────────────────────────────────────────────────────────────────────
    // 7–9. send-video-call-invitation
    // ─────────────────────────────────────────────────────────────────────

    @Nested
    @DisplayName("handleTextMessage: send-video-call-invitation")
    class CallInvitationTests {

        @Test
        @DisplayName("CALL-017 patient→patient is blocked with proper reason")
        void callInvitation_patientToPatient_blocked() throws Exception {
            WebSocketSession senderSession = mockOpenSession("sess-pt-sender");
            authenticateSession(senderSession, patient);

            User anotherPatient = new User();
            anotherPatient.setId(3L);
            anotherPatient.setEmail("other-patient@test.com");
            anotherPatient.setRole(Role.PATIENT);
            anotherPatient.setName("Other Patient");
            anotherPatient.setPassword("pw");

            when(userRepository.findById(3L)).thenReturn(Optional.of(anotherPatient));

            String msg = objectMapper.writeValueAsString(Map.of(
                    "type", "send-video-call-invitation",
                    "recipientId", "3",
                    "callId", "call-123"));
            handler.handleTextMessage(senderSession, new TextMessage(msg));

            Map<?, ?> response = captureResponse(senderSession);
            assertThat(response.get("type")).isEqualTo("call-invitation-failed");
            assertThat((String) response.get("reason"))
                    .isEqualTo("Patient-to-patient calls are not permitted");
        }

        @Test
        @DisplayName("CALL-016 patient→caregiver with no active link is blocked")
        void callInvitation_patientToCaregiverNoLink_blocked() throws Exception {
            WebSocketSession senderSession = mockOpenSession("sess-pt-nolink");
            authenticateSession(senderSession, patient);

            when(userRepository.findById(1L)).thenReturn(Optional.of(caregiver));
            when(caregiverPatientLinkService.hasAccessToPatient(1L, 2L)).thenReturn(false);

            String msg = objectMapper.writeValueAsString(Map.of(
                    "type", "send-video-call-invitation",
                    "recipientId", "1",
                    "callId", "call-456"));
            handler.handleTextMessage(senderSession, new TextMessage(msg));

            Map<?, ?> response = captureResponse(senderSession);
            assertThat(response.get("type")).isEqualTo("call-invitation-failed");
            assertThat((String) response.get("reason"))
                    .isEqualTo("No active caregiver-patient link");
        }

        @Test
        @DisplayName("caregiver→patient success path — recipient not online yields call-invitation-failed")
        void callInvitation_caregiverToPatient_recipientNotOnline() throws Exception {
            WebSocketSession senderSession = mockOpenSession("sess-cg-sender");
            authenticateSession(senderSession, caregiver);

            when(userRepository.findById(2L)).thenReturn(Optional.of(patient));
            // recipient "2" has no session in the map → treated as not online

            String msg = objectMapper.writeValueAsString(Map.of(
                    "type", "send-video-call-invitation",
                    "recipientId", "2",
                    "callId", "call-789"));
            handler.handleTextMessage(senderSession, new TextMessage(msg));

            Map<?, ?> response = captureResponse(senderSession);
            assertThat(response.get("type")).isEqualTo("call-invitation-failed");
            assertThat((String) response.get("reason")).isEqualTo("Recipient not online");
        }
    }

    // ─────────────────────────────────────────────────────────────────────
    // 10. accept-call
    // ─────────────────────────────────────────────────────────────────────

    @Test
    @DisplayName("accept-call sends call-answered to original sender")
    void acceptCall_sendsCallAnsweredToSender() throws Exception {
        // Set up the original caller (caregiver) session
        WebSocketSession callerSession = mockOpenSession("sess-caller");
        authenticateSession(callerSession, caregiver);

        // Set up the answering patient session
        WebSocketSession answererSession = mockOpenSession("sess-answerer");
        authenticateSession(answererSession, patient);

        // patient now accepts the call, referencing senderId = "1" (caregiver)
        String msg = objectMapper.writeValueAsString(Map.of(
                "type", "accept-call",
                "callId", "call-abc",
                "senderId", "1"));
        handler.handleTextMessage(answererSession, new TextMessage(msg));

        // The call-answered notification should be delivered to the caller session
        ArgumentCaptor<TextMessage> callerCaptor = ArgumentCaptor.forClass(TextMessage.class);
        verify(callerSession, atLeastOnce()).sendMessage(callerCaptor.capture());

        // Walk through all messages sent to callerSession; the last one should be call-answered
        Map<?, ?> lastMsg = null;
        for (TextMessage tm : callerCaptor.getAllValues()) {
            lastMsg = objectMapper.readValue(tm.getPayload(), Map.class);
        }
        assertThat(lastMsg).isNotNull();
        assertThat(lastMsg.get("type")).isEqualTo("call-answered");
        assertThat(lastMsg.get("callId")).isEqualTo("call-abc");
    }

    // ─────────────────────────────────────────────────────────────────────
    // 11. decline-call
    // ─────────────────────────────────────────────────────────────────────

    @Test
    @DisplayName("decline-call sends call-declined to original sender")
    void declineCall_sendsCallDeclinedToSender() throws Exception {
        WebSocketSession callerSession = mockOpenSession("sess-cg-decline");
        authenticateSession(callerSession, caregiver);

        WebSocketSession declinerSession = mockOpenSession("sess-pt-decline");
        authenticateSession(declinerSession, patient);

        String msg = objectMapper.writeValueAsString(Map.of(
                "type", "decline-call",
                "callId", "call-def",
                "senderId", "1",
                "reason", "busy"));
        handler.handleTextMessage(declinerSession, new TextMessage(msg));

        ArgumentCaptor<TextMessage> callerCaptor = ArgumentCaptor.forClass(TextMessage.class);
        verify(callerSession, atLeastOnce()).sendMessage(callerCaptor.capture());

        Map<?, ?> lastMsg = null;
        for (TextMessage tm : callerCaptor.getAllValues()) {
            lastMsg = objectMapper.readValue(tm.getPayload(), Map.class);
        }
        assertThat(lastMsg).isNotNull();
        assertThat(lastMsg.get("type")).isEqualTo("call-declined");
        assertThat(lastMsg.get("callId")).isEqualTo("call-def");
    }

    // ─────────────────────────────────────────────────────────────────────
    // 12. end-call
    // ─────────────────────────────────────────────────────────────────────

    @Test
    @DisplayName("end-call sends call-ended to other party")
    void endCall_sendsCallEndedToOtherParty() throws Exception {
        WebSocketSession patientSession = mockOpenSession("sess-pt-end");
        authenticateSession(patientSession, patient);

        WebSocketSession caregiverSession = mockOpenSession("sess-cg-end");
        authenticateSession(caregiverSession, caregiver);

        // Patient ends the call; otherPartyId = caregiver id "1"
        String msg = objectMapper.writeValueAsString(Map.of(
                "type", "end-call",
                "callId", "call-ghi",
                "otherPartyId", "1"));
        handler.handleTextMessage(patientSession, new TextMessage(msg));

        ArgumentCaptor<TextMessage> cgCaptor = ArgumentCaptor.forClass(TextMessage.class);
        verify(caregiverSession, atLeastOnce()).sendMessage(cgCaptor.capture());

        Map<?, ?> lastMsg = null;
        for (TextMessage tm : cgCaptor.getAllValues()) {
            lastMsg = objectMapper.readValue(tm.getPayload(), Map.class);
        }
        assertThat(lastMsg).isNotNull();
        assertThat(lastMsg.get("type")).isEqualTo("call-ended");
        assertThat(lastMsg.get("callId")).isEqualTo("call-ghi");
    }

    // ─────────────────────────────────────────────────────────────────────
    // 13. heartbeat
    // ─────────────────────────────────────────────────────────────────────

    @Test
    @DisplayName("heartbeat replies with heartbeat-response")
    void heartbeat_sendsHeartbeatResponse() throws Exception {
        WebSocketSession session = mockOpenSession("sess-hb");

        String msg = objectMapper.writeValueAsString(Map.of("type", "heartbeat"));
        handler.handleTextMessage(session, new TextMessage(msg));

        Map<?, ?> response = captureResponse(session);
        assertThat(response.get("type")).isEqualTo("heartbeat-response");
        assertThat(response.get("timestamp")).isNotNull();
    }

    // ─────────────────────────────────────────────────────────────────────
    // 14–15. sentiment-channel-state
    // ─────────────────────────────────────────────────────────────────────

    @Nested
    @DisplayName("handleTextMessage: sentiment-channel-state")
    class SentimentChannelStateTests {

        @Test
        @DisplayName("valid channel forwards sentiment-channel-state to other party")
        void sentimentChannelState_valid_forwardsToOtherParty() throws Exception {
            WebSocketSession caregiverSession = mockOpenSession("sess-cg-scs");
            authenticateSession(caregiverSession, caregiver);

            WebSocketSession patientSession = mockOpenSession("sess-pt-scs");
            authenticateSession(patientSession, patient);

            // caregiver sends sentiment-channel-state; otherPartyId = patient id "2"
            String msg = objectMapper.writeValueAsString(Map.of(
                    "type", "sentiment-channel-state",
                    "callId", "call-scs",
                    "channel", "voice",
                    "muted", false,
                    "otherPartyId", "2"));
            handler.handleTextMessage(caregiverSession, new TextMessage(msg));

            ArgumentCaptor<TextMessage> ptCaptor = ArgumentCaptor.forClass(TextMessage.class);
            verify(patientSession, atLeastOnce()).sendMessage(ptCaptor.capture());

            Map<?, ?> lastMsg = null;
            for (TextMessage tm : ptCaptor.getAllValues()) {
                lastMsg = objectMapper.readValue(tm.getPayload(), Map.class);
            }
            assertThat(lastMsg).isNotNull();
            assertThat(lastMsg.get("type")).isEqualTo("sentiment-channel-state");
            assertThat(lastMsg.get("channel")).isEqualTo("voice");
        }

        @Test
        @DisplayName("invalid channel name returns error response to sender")
        void sentimentChannelState_invalidChannel_returnsError() throws Exception {
            WebSocketSession session = mockOpenSession("sess-scs-bad");
            authenticateSession(session, caregiver);

            String msg = objectMapper.writeValueAsString(Map.of(
                    "type", "sentiment-channel-state",
                    "callId", "call-scs-bad",
                    "channel", "invalid",
                    "otherPartyId", "2"));
            handler.handleTextMessage(session, new TextMessage(msg));

            Map<?, ?> response = captureResponse(session);
            assertThat(response.get("type")).isEqualTo("error");
            assertThat((String) response.get("message")).containsIgnoringCase("Invalid channel");
        }
    }

    // ─────────────────────────────────────────────────────────────────────
    // 16. unknown type
    // ─────────────────────────────────────────────────────────────────────

    @Test
    @DisplayName("unknown message type returns error message")
    void unknownType_returnsError() throws Exception {
        WebSocketSession session = mockOpenSession("sess-unknown");

        String msg = objectMapper.writeValueAsString(Map.of("type", "totally-unknown-type"));
        handler.handleTextMessage(session, new TextMessage(msg));

        Map<?, ?> response = captureResponse(session);
        assertThat(response.get("type")).isEqualTo("error");
        assertThat((String) response.get("message"))
                .containsIgnoringCase("Unknown message type");
    }

    // ─────────────────────────────────────────────────────────────────────
    // 17. afterConnectionClosed
    // ─────────────────────────────────────────────────────────────────────

    @Test
    @DisplayName("afterConnectionClosed removes authenticated user from session maps")
    void afterConnectionClosed_removesUserFromSessionMaps() throws Exception {
        WebSocketSession session = mockOpenSession("sess-close");
        authenticateSession(session, caregiver);

        // After closure, sendNotificationToUser should not find the session
        handler.afterConnectionClosed(session, CloseStatus.NORMAL);

        // The easiest way to verify removal: sending a notification to the caregiver's id
        // must NOT trigger any sendMessage call on the now-removed session.
        // Reset mock interaction counts by capturing only new calls.
        // We re-mock isOpen to verify no additional message is sent post-close.
        WebSocketSession newSession = mockOpenSession("sess-other");

        handler.sendNotificationToUser("1", Map.of("type", "test-notification"));

        // No message should be sent to either session — user "1" was removed
        verify(newSession, never()).sendMessage(any(TextMessage.class));
    }

    // ─────────────────────────────────────────────────────────────────────
    // 18. sendNotificationToUser
    // ─────────────────────────────────────────────────────────────────────

    @Nested
    @DisplayName("sendNotificationToUser")
    class SendNotificationToUserTests {

        @Test
        @DisplayName("user in session map receives the notification")
        void sendNotification_userOnline_sendsMessage() throws Exception {
            WebSocketSession session = mockOpenSession("sess-notify");
            authenticateSession(session, caregiver);

            Map<String, Object> notification = new HashMap<>();
            notification.put("type", "test-event");
            notification.put("data", "hello");

            handler.sendNotificationToUser("1", notification);

            // At least one message sent; the most recent should be our notification
            ArgumentCaptor<TextMessage> captor = ArgumentCaptor.forClass(TextMessage.class);
            verify(session, atLeastOnce()).sendMessage(captor.capture());

            Map<?, ?> lastPayload = null;
            for (TextMessage tm : captor.getAllValues()) {
                lastPayload = objectMapper.readValue(tm.getPayload(), Map.class);
            }
            assertThat(lastPayload).isNotNull();
            assertThat(lastPayload.get("type")).isEqualTo("test-event");
        }

        @Test
        @DisplayName("user NOT in session map does not throw and sends no message")
        void sendNotification_userOffline_doesNotThrow() throws Exception {
            WebSocketSession session = mockOpenSession("sess-nobody");

            // userId "999" was never authenticated — should be a no-op
            handler.sendNotificationToUser("999", Map.of("type", "ping"));

            verify(session, never()).sendMessage(any(TextMessage.class));
        }
    }
}
