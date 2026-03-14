package com.careconnect.controller;

import com.careconnect.model.User;
import com.careconnect.security.Role;
import com.careconnect.security.AuthorizationService;
import com.careconnect.service.WebSocketNotificationService;
import com.careconnect.util.SecurityUtil;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;

import java.util.HashMap;
import java.util.List;
import java.util.Map;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.*;
import static org.mockito.Mockito.*;

@ExtendWith(MockitoExtension.class)
class WebSocketControllerTest {

    @Mock
    private WebSocketNotificationService webSocketNotificationService;

    @Mock
    private SecurityUtil securityUtil;
    @Mock
    private AuthorizationService authorizationService;

    @InjectMocks
    private WebSocketController controller;

    // ─── initializeWebSocketService ───────────────────────────────────────────

    @Test
    void initializeWebSocketService_returnsOkWithSuccessTrue() throws Exception {
        final ResponseEntity<Map<String, Object>> response = controller.initializeWebSocketService(null);

        assertThat(response.getStatusCode()).isEqualTo(HttpStatus.OK);
        assertThat(response.getBody()).containsEntry("success", true);
        assertThat(response.getBody()).containsEntry("message", "WebSocket service initialized");
    }

    @Test
    void initializeWebSocketService_withRequestBody_returnsOk() throws Exception {
        final ResponseEntity<Map<String, Object>> response =
                controller.initializeWebSocketService(Map.of("key", "value"));

        assertThat(response.getStatusCode()).isEqualTo(HttpStatus.OK);
    }

    // ─── registerUserForWebSocket ─────────────────────────────────────────────

    @Test
    void registerUser_missingUserId_returnsBadRequest() throws Exception {
        final Map<String, Object> request = new HashMap<>();
        request.put("userId", null);
        request.put("userName", "Alice");

        final ResponseEntity<Map<String, Object>> response = controller.registerUserForWebSocket(request);

        assertThat(response.getStatusCode()).isEqualTo(HttpStatus.BAD_REQUEST);
        assertThat(response.getBody()).containsEntry("success", false);
    }

    @Test
    void registerUser_missingUserName_returnsBadRequest() throws Exception {
        final Map<String, Object> request = new HashMap<>();
        request.put("userId", "user-1");
        request.put("userName", null);

        final ResponseEntity<Map<String, Object>> response = controller.registerUserForWebSocket(request);

        assertThat(response.getStatusCode()).isEqualTo(HttpStatus.BAD_REQUEST);
        assertThat(response.getBody()).containsEntry("success", false);
    }

    @Test
    void registerUser_validFields_returnsOk() throws Exception {
        doNothing().when(webSocketNotificationService).registerUser("user-1", "Alice");

        final ResponseEntity<Map<String, Object>> response = controller.registerUserForWebSocket(
                Map.of("userId", "user-1", "userName", "Alice"));

        assertThat(response.getStatusCode()).isEqualTo(HttpStatus.OK);
        assertThat(response.getBody()).containsEntry("success", true);
        assertThat(response.getBody()).containsEntry("userId", "user-1");
        assertThat(response.getBody()).containsEntry("userName", "Alice");
    }

    @Test
    void registerUser_serviceThrows_returnsBadRequest() throws Exception {
        doThrow(new RuntimeException("Registration failed"))
                .when(webSocketNotificationService).registerUser("bad", "user");

        final ResponseEntity<Map<String, Object>> response = controller.registerUserForWebSocket(
                Map.of("userId", "bad", "userName", "user"));

        assertThat(response.getStatusCode()).isEqualTo(HttpStatus.BAD_REQUEST);
        assertThat(response.getBody()).containsEntry("success", false);
    }

    // ─── sendCallInvitation ───────────────────────────────────────────────────

    @Test
    void sendCallInvitation_success_returnsOk() throws Exception {
        final Map<String, Object> request = new HashMap<>();
        request.put("recipientId", "rec-1");
        request.put("senderId", "send-1");
        request.put("senderName", "Bob");
        request.put("callId", "call-99");
        request.put("isVideoCall", true);
        request.put("callType", "video");

        final ResponseEntity<Map<String, Object>> response = controller.sendCallInvitation(request);

        assertThat(response.getStatusCode()).isEqualTo(HttpStatus.OK);
        assertThat(response.getBody()).containsEntry("success", true);
        assertThat(response.getBody()).containsEntry("callId", "call-99");
        verify(webSocketNotificationService).sendCallInvitation("rec-1", "send-1", "Bob", "call-99", true, "video");
    }

    @Test
    void sendCallInvitation_defaultsApplied_sendsWithDefaults() throws Exception {
        final Map<String, Object> request = new HashMap<>();
        request.put("recipientId", "rec-2");
        request.put("senderId", "send-2");
        request.put("senderName", "Carol");
        request.put("callId", "call-100");
        // isVideoCall and callType omitted → defaults: true, "general"

        final ResponseEntity<Map<String, Object>> response = controller.sendCallInvitation(request);

        assertThat(response.getStatusCode()).isEqualTo(HttpStatus.OK);
        verify(webSocketNotificationService).sendCallInvitation("rec-2", "send-2", "Carol", "call-100", true, "general");
    }

    @Test
    void sendCallInvitation_withIsVideoCallFalse_returnsOk() throws Exception {
        final Map<String, Object> request = new HashMap<>();
        request.put("recipientId", "rec-5");
        request.put("senderId", "send-5");
        request.put("senderName", "Frank");
        request.put("callId", "call-200");
        request.put("isVideoCall", false);
        request.put("callType", "audio");

        final ResponseEntity<Map<String, Object>> response = controller.sendCallInvitation(request);

        assertThat(response.getStatusCode()).isEqualTo(HttpStatus.OK);
        assertThat(response.getBody()).containsEntry("recipientId", "rec-5");
        verify(webSocketNotificationService).sendCallInvitation("rec-5", "send-5", "Frank", "call-200", false, "audio");
    }

    @Test
    void sendCallInvitation_serviceThrows_returnsBadRequest() throws Exception {
        doThrow(new RuntimeException("Connection lost"))
                .when(webSocketNotificationService)
                .sendCallInvitation(any(), any(), any(), any(), anyBoolean(), any());

        final Map<String, Object> request = new HashMap<>();
        request.put("recipientId", "r");
        request.put("senderId", "s");
        request.put("senderName", "n");
        request.put("callId", "c");

        final ResponseEntity<Map<String, Object>> response = controller.sendCallInvitation(request);

        assertThat(response.getStatusCode()).isEqualTo(HttpStatus.BAD_REQUEST);
        assertThat(response.getBody()).containsEntry("success", false);
    }

    // ─── sendSMSNotification ──────────────────────────────────────────────────

    @Test
    void sendSMSNotification_success_returnsOk() throws Exception {
        final Map<String, Object> request = new HashMap<>();
        request.put("recipientId", "rec-1");
        request.put("senderId", "send-1");
        request.put("senderName", "Dave");
        request.put("message", "You have a reminder");
        request.put("messageType", "reminder");

        final ResponseEntity<Map<String, Object>> response = controller.sendSMSNotification(request);

        assertThat(response.getStatusCode()).isEqualTo(HttpStatus.OK);
        assertThat(response.getBody()).containsEntry("success", true);
        verify(webSocketNotificationService).sendSMSNotification("rec-1", "send-1", "Dave", "You have a reminder", "reminder");
    }

    @Test
    void sendSMSNotification_defaultMessageType_sendsGeneral() throws Exception {
        final Map<String, Object> request = new HashMap<>();
        request.put("recipientId", "rec-3");
        request.put("senderId", "send-3");
        request.put("senderName", "Eve");
        request.put("message", "Hello");
        // messageType omitted → defaults to "general"

        controller.sendSMSNotification(request);

        verify(webSocketNotificationService).sendSMSNotification("rec-3", "send-3", "Eve", "Hello", "general");
    }

    @Test
    void sendSMSNotification_serviceThrows_returnsBadRequest() throws Exception {
        doThrow(new RuntimeException("SMS error"))
                .when(webSocketNotificationService)
                .sendSMSNotification(any(), any(), any(), any(), any());

        final Map<String, Object> request = new HashMap<>();
        request.put("recipientId", "r");
        request.put("senderId", "s");
        request.put("senderName", "n");
        request.put("message", "m");

        final ResponseEntity<Map<String, Object>> response = controller.sendSMSNotification(request);

        assertThat(response.getStatusCode()).isEqualTo(HttpStatus.BAD_REQUEST);
    }

    // ─── sendMedicationReminder ───────────────────────────────────────────────

    @Test
    void sendMedicationReminder_success_returnsOk() throws Exception {
        final Map<String, Object> request = Map.of(
                "patientId", "p-1",
                "medicationName", "Aspirin",
                "reminderTime", "08:00",
                "dosage", "100mg");

        final ResponseEntity<Map<String, Object>> response = controller.sendMedicationReminder(request);

        assertThat(response.getStatusCode()).isEqualTo(HttpStatus.OK);
        assertThat(response.getBody()).containsEntry("success", true);
        assertThat(response.getBody()).containsEntry("medicationName", "Aspirin");
        verify(webSocketNotificationService).sendMedicationReminder("p-1", "Aspirin", "08:00", "100mg");
    }

    @Test
    void sendMedicationReminder_serviceThrows_returnsBadRequest() throws Exception {
        doThrow(new RuntimeException("Reminder error"))
                .when(webSocketNotificationService)
                .sendMedicationReminder(any(), any(), any(), any());

        final Map<String, Object> request = new HashMap<>();
        request.put("patientId", "p-2");
        request.put("medicationName", "Metformin");
        request.put("reminderTime", "09:00");
        request.put("dosage", "500mg");

        final ResponseEntity<Map<String, Object>> response = controller.sendMedicationReminder(request);

        assertThat(response.getStatusCode()).isEqualTo(HttpStatus.BAD_REQUEST);
    }

    // ─── sendVitalSignsAlert ──────────────────────────────────────────────────

    @Test
    void sendVitalSignsAlert_success_returnsOk() throws Exception {
        final Map<String, Object> request = new HashMap<>();
        request.put("patientId", "p-1");
        request.put("patientName", "John Doe");
        request.put("alertType", "HIGH_BP");
        request.put("alertMessage", "Blood pressure is high");
        request.put("severity", "HIGH");
        request.put("recipientIds", List.of("doc-1", "doc-2"));

        final ResponseEntity<Map<String, Object>> response = controller.sendVitalSignsAlert(request);

        assertThat(response.getStatusCode()).isEqualTo(HttpStatus.OK);
        assertThat(response.getBody()).containsEntry("success", true);
        assertThat(response.getBody()).containsEntry("recipientCount", 2);
        verify(webSocketNotificationService).sendVitalSignsAlert(
                eq("p-1"), eq("John Doe"), eq("HIGH_BP"), eq("Blood pressure is high"), eq("HIGH"),
                any(String[].class));
    }

    @Test
    void sendVitalSignsAlert_serviceThrows_returnsBadRequest() throws Exception {
        doThrow(new RuntimeException("Alert error"))
                .when(webSocketNotificationService)
                .sendVitalSignsAlert(any(), any(), any(), any(), any(), any(String[].class));

        final Map<String, Object> request = new HashMap<>();
        request.put("patientId", "p-2");
        request.put("patientName", "Jane");
        request.put("alertType", "LOW_O2");
        request.put("alertMessage", "Low oxygen");
        request.put("severity", "CRITICAL");
        request.put("recipientIds", List.of("doc-3"));

        final ResponseEntity<Map<String, Object>> response = controller.sendVitalSignsAlert(request);

        assertThat(response.getStatusCode()).isEqualTo(HttpStatus.BAD_REQUEST);
    }

    // ─── sendEmergencyAlert ───────────────────────────────────────────────────

    @Test
    void sendEmergencyAlert_success_returnsOk() throws Exception {
        final Map<String, Object> request = new HashMap<>();
        request.put("patientId", "p-10");
        request.put("patientName", "Sam Lee");
        request.put("alertMessage", "Patient needs immediate help");
        request.put("emergencyContactIds", List.of("contact-1", "contact-2"));

        final ResponseEntity<Map<String, Object>> response = controller.sendEmergencyAlert(request);

        assertThat(response.getStatusCode()).isEqualTo(HttpStatus.OK);
        assertThat(response.getBody()).containsEntry("success", true);
        assertThat(response.getBody()).containsEntry("contactCount", 2);
        verify(webSocketNotificationService).sendEmergencyAlert(
                eq("p-10"), eq("Sam Lee"), eq("Patient needs immediate help"),
                any(String[].class));
    }

    @Test
    void sendEmergencyAlert_serviceThrows_returnsBadRequest() throws Exception {
        doThrow(new RuntimeException("Emergency error"))
                .when(webSocketNotificationService)
                .sendEmergencyAlert(any(), any(), any(), any(String[].class));

        final Map<String, Object> request = new HashMap<>();
        request.put("patientId", "p-11");
        request.put("patientName", "Pat");
        request.put("alertMessage", "Help");
        request.put("emergencyContactIds", List.of("c-1"));

        final ResponseEntity<Map<String, Object>> response = controller.sendEmergencyAlert(request);

        assertThat(response.getStatusCode()).isEqualTo(HttpStatus.BAD_REQUEST);
    }

    // ─── sendAppointmentReminder ──────────────────────────────────────────────

    @Test
    void sendAppointmentReminder_success_returnsOk() throws Exception {
        final Map<String, Object> request = Map.of(
                "patientId", "p-5",
                "appointmentDetails", "Cardiology checkup",
                "appointmentTime", "2026-03-15 10:00",
                "providerName", "Dr. Smith");

        final ResponseEntity<Map<String, Object>> response = controller.sendAppointmentReminder(request);

        assertThat(response.getStatusCode()).isEqualTo(HttpStatus.OK);
        assertThat(response.getBody()).containsEntry("success", true);
        assertThat(response.getBody()).containsEntry("appointmentTime", "2026-03-15 10:00");
        verify(webSocketNotificationService).sendAppointmentReminder(
                "p-5", "Cardiology checkup", "2026-03-15 10:00", "Dr. Smith");
    }

    @Test
    void sendAppointmentReminder_serviceThrows_returnsBadRequest() throws Exception {
        doThrow(new RuntimeException("Appointment error"))
                .when(webSocketNotificationService)
                .sendAppointmentReminder(any(), any(), any(), any());

        final Map<String, Object> request = new HashMap<>();
        request.put("patientId", "p-6");
        request.put("appointmentDetails", "General");
        request.put("appointmentTime", "09:00");
        request.put("providerName", "Dr. Jones");

        final ResponseEntity<Map<String, Object>> response = controller.sendAppointmentReminder(request);

        assertThat(response.getStatusCode()).isEqualTo(HttpStatus.BAD_REQUEST);
    }

    // ─── broadcastSystemAnnouncement ─────────────────────────────────────────

    @Test
    void broadcastSystemAnnouncement_success_returnsOk() throws Exception {
        when(webSocketNotificationService.getOnlineUsersCount()).thenReturn(42);

        final Map<String, Object> request = Map.of(
                "title", "Maintenance",
                "message", "System will be down at midnight",
                "type", "warning");

        final ResponseEntity<Map<String, Object>> response = controller.broadcastSystemAnnouncement(request);

        assertThat(response.getStatusCode()).isEqualTo(HttpStatus.OK);
        assertThat(response.getBody()).containsEntry("success", true);
        assertThat(response.getBody()).containsEntry("onlineUsers", 42);
        verify(webSocketNotificationService).broadcastSystemAnnouncement("Maintenance", "System will be down at midnight", "warning");
    }

    @Test
    void broadcastSystemAnnouncement_defaultType_sendsInfo() throws Exception {
        when(webSocketNotificationService.getOnlineUsersCount()).thenReturn(5);

        final Map<String, Object> request = new HashMap<>();
        request.put("title", "Hello");
        request.put("message", "Welcome");
        // type omitted → defaults to "info"

        controller.broadcastSystemAnnouncement(request);

        verify(webSocketNotificationService).broadcastSystemAnnouncement("Hello", "Welcome", "info");
    }

    @Test
    void broadcastSystemAnnouncement_serviceThrows_returnsBadRequest() throws Exception {
        doThrow(new RuntimeException("Broadcast error"))
                .when(webSocketNotificationService)
                .broadcastSystemAnnouncement(any(), any(), any());

        final Map<String, Object> request = new HashMap<>();
        request.put("title", "Test");
        request.put("message", "Test msg");

        final ResponseEntity<Map<String, Object>> response = controller.broadcastSystemAnnouncement(request);

        assertThat(response.getStatusCode()).isEqualTo(HttpStatus.BAD_REQUEST);
    }

    // ─── getOnlineUsers ───────────────────────────────────────────────────────

    @Test
    void getOnlineUsers_success_returnsOkWithUsers() throws Exception {
        final Map<String, String> users = Map.of("user-1", "Alice", "user-2", "Bob");
        when(webSocketNotificationService.getOnlineUsers()).thenReturn(users);
        when(webSocketNotificationService.getOnlineUsersCount()).thenReturn(2);

        final ResponseEntity<Map<String, Object>> response = controller.getOnlineUsers();

        assertThat(response.getStatusCode()).isEqualTo(HttpStatus.OK);
        assertThat(response.getBody()).containsEntry("success", true);
        assertThat(response.getBody()).containsEntry("onlineUsers", users);
        assertThat(response.getBody()).containsEntry("onlineCount", 2);
    }

    @Test
    void getOnlineUsers_serviceThrows_returnsBadRequest() throws Exception {
        when(webSocketNotificationService.getOnlineUsers())
                .thenThrow(new RuntimeException("Service error"));

        final ResponseEntity<Map<String, Object>> response = controller.getOnlineUsers();

        assertThat(response.getStatusCode()).isEqualTo(HttpStatus.BAD_REQUEST);
        assertThat(response.getBody()).containsEntry("success", false);
    }

    // ─── getUserOnlineStatus ──────────────────────────────────────────────────

    @Test
    void getUserOnlineStatus_userOnline_returnsOkWithIsOnlineTrue() throws Exception {
        when(webSocketNotificationService.isUserOnline("user-1")).thenReturn(true);

        final ResponseEntity<Map<String, Object>> response = controller.getUserOnlineStatus("user-1");

        assertThat(response.getStatusCode()).isEqualTo(HttpStatus.OK);
        assertThat(response.getBody()).containsEntry("success", true);
        assertThat(response.getBody()).containsEntry("userId", "user-1");
        assertThat(response.getBody()).containsEntry("isOnline", true);
    }

    @Test
    void getUserOnlineStatus_userOffline_returnsOkWithIsOnlineFalse() throws Exception {
        when(webSocketNotificationService.isUserOnline("user-2")).thenReturn(false);

        final ResponseEntity<Map<String, Object>> response = controller.getUserOnlineStatus("user-2");

        assertThat(response.getStatusCode()).isEqualTo(HttpStatus.OK);
        assertThat(response.getBody()).containsEntry("isOnline", false);
    }

    @Test
    void getUserOnlineStatus_serviceThrows_returnsBadRequest() throws Exception {
        when(webSocketNotificationService.isUserOnline("bad-user"))
                .thenThrow(new RuntimeException("Lookup failed"));

        final ResponseEntity<Map<String, Object>> response = controller.getUserOnlineStatus("bad-user");

        assertThat(response.getStatusCode()).isEqualTo(HttpStatus.BAD_REQUEST);
        assertThat(response.getBody()).containsEntry("success", false);
    }

    // ─── sendSOSCall ──────────────────────────────────────────────────────────

    @Test
    void sendSOSCall_missingPatientUserId_returnsBadRequest() throws Exception {
        final User patient = User.builder().role(Role.PATIENT).build();
        when(securityUtil.resolveCurrentUser()).thenReturn(patient);

        final Map<String, Object> request = new HashMap<>();
        request.put("patientUserId", null);
        request.put("patientName", "Alice");
        request.put("callId", "sos-1");

        final ResponseEntity<Map<String, Object>> response = controller.sendSOSCall(request);

        assertThat(response.getStatusCode()).isEqualTo(HttpStatus.BAD_REQUEST);
        assertThat(response.getBody()).containsEntry("success", false);
    }

    @Test
    void sendSOSCall_missingPatientName_returnsBadRequest() throws Exception {
        final User patient = User.builder().role(Role.PATIENT).build();
        when(securityUtil.resolveCurrentUser()).thenReturn(patient);

        final Map<String, Object> request = new HashMap<>();
        request.put("patientUserId", "p-1");
        request.put("patientName", null);
        request.put("callId", "sos-2");

        final ResponseEntity<Map<String, Object>> response = controller.sendSOSCall(request);

        assertThat(response.getStatusCode()).isEqualTo(HttpStatus.BAD_REQUEST);
        assertThat(response.getBody()).containsEntry("success", false);
    }

    @Test
    void sendSOSCall_missingCallId_returnsBadRequest() throws Exception {
        final User patient = User.builder().role(Role.PATIENT).build();
        when(securityUtil.resolveCurrentUser()).thenReturn(patient);

        final Map<String, Object> request = new HashMap<>();
        request.put("patientUserId", "p-2");
        request.put("patientName", "Bob");
        request.put("callId", null);

        final ResponseEntity<Map<String, Object>> response = controller.sendSOSCall(request);

        assertThat(response.getStatusCode()).isEqualTo(HttpStatus.BAD_REQUEST);
        assertThat(response.getBody()).containsEntry("success", false);
    }

    @Test
    void sendSOSCall_caregiversNotified_returnsOk() throws Exception {
        final User patient = User.builder().role(Role.PATIENT).build();
        when(securityUtil.resolveCurrentUser()).thenReturn(patient);
        when(webSocketNotificationService.sendSOSCallToAllCaregivers(
                any(), any(), any(), any(), any(), any(), anyBoolean()))
                .thenReturn(3);

        final Map<String, Object> request = new HashMap<>();
        request.put("patientUserId", "p-3");
        request.put("patientName", "Charlie");
        request.put("callId", "sos-3");

        final ResponseEntity<Map<String, Object>> response = controller.sendSOSCall(request);

        assertThat(response.getStatusCode()).isEqualTo(HttpStatus.OK);
        assertThat(response.getBody()).containsEntry("success", true);
        assertThat(response.getBody()).containsEntry("notifiedCaregivers", 3);
        assertThat(response.getBody()).containsEntry("patientUserId", "p-3");
        assertThat(response.getBody()).containsEntry("callId", "sos-3");
        verify(webSocketNotificationService).sendSOSCallToAllCaregivers(
                any(), any(), any(), any(), any(), any(), anyBoolean());
    }

    @Test
    void sendSOSCall_withExplicitOptions_returnsOk() throws Exception {
        final User patient = User.builder().role(Role.PATIENT).build();
        when(securityUtil.resolveCurrentUser()).thenReturn(patient);
        when(webSocketNotificationService.sendSOSCallToAllCaregivers(
                eq("p-6"), eq("Frank"), eq("sos-6"),
                eq("CARDIAC"), eq("Room 302"), eq("Patient is conscious"), eq(false)))
                .thenReturn(2);

        final Map<String, Object> request = new HashMap<>();
        request.put("patientUserId", "p-6");
        request.put("patientName", "Frank");
        request.put("callId", "sos-6");
        request.put("emergencyType", "CARDIAC");
        request.put("location", "Room 302");
        request.put("additionalInfo", "Patient is conscious");
        request.put("isVideoCall", false);

        final ResponseEntity<Map<String, Object>> response = controller.sendSOSCall(request);

        assertThat(response.getStatusCode()).isEqualTo(HttpStatus.OK);
        assertThat(response.getBody()).containsEntry("success", true);
        assertThat(response.getBody()).containsEntry("emergencyType", "CARDIAC");
        assertThat(response.getBody()).containsEntry("notifiedCaregivers", 2);
        verify(webSocketNotificationService).sendSOSCallToAllCaregivers(
                "p-6", "Frank", "sos-6", "CARDIAC", "Room 302", "Patient is conscious", false);
    }

    @Test
    void sendSOSCall_noCaregiversOnline_returns404() throws Exception {
        final User patient = User.builder().role(Role.PATIENT).build();
        when(securityUtil.resolveCurrentUser()).thenReturn(patient);
        when(webSocketNotificationService.sendSOSCallToAllCaregivers(
                any(), any(), any(), any(), any(), any(), anyBoolean()))
                .thenReturn(0);

        final Map<String, Object> request = new HashMap<>();
        request.put("patientUserId", "p-4");
        request.put("patientName", "Dana");
        request.put("callId", "sos-4");

        final ResponseEntity<Map<String, Object>> response = controller.sendSOSCall(request);

        assertThat(response.getStatusCode()).isEqualTo(HttpStatus.NOT_FOUND);
        assertThat(response.getBody()).containsEntry("success", false);
    }

    @Test
    void sendSOSCall_serviceThrows_returnsBadRequest() throws Exception {
        final User patient = User.builder().role(Role.PATIENT).build();
        when(securityUtil.resolveCurrentUser()).thenReturn(patient);
        when(webSocketNotificationService.sendSOSCallToAllCaregivers(
                any(), any(), any(), any(), any(), any(), anyBoolean()))
                .thenThrow(new RuntimeException("SOS error"));

        final Map<String, Object> request = new HashMap<>();
        request.put("patientUserId", "p-5");
        request.put("patientName", "Eli");
        request.put("callId", "sos-5");

        final ResponseEntity<Map<String, Object>> response = controller.sendSOSCall(request);

        assertThat(response.getStatusCode()).isEqualTo(HttpStatus.BAD_REQUEST);
        assertThat(response.getBody()).containsEntry("success", false);
    }
}
