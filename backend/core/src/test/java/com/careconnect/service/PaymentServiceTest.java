package com.careconnect.service;

import com.careconnect.model.Payment;
import com.careconnect.repository.PaymentRepository;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.MockitoAnnotations;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.Mockito.*;

class PaymentServiceTest {

    @Mock
    private PaymentRepository paymentRepository;

    @InjectMocks
    private PaymentService paymentService;

    @BeforeEach
    void setUp() throws Exception {
        MockitoAnnotations.openMocks(this);
    }

    @Test
    @DisplayName("savePayment - valid payment - delegates to repository save")
    void savePayment_validPayment_delegatesToRepositorySave() throws Exception {
        final Payment payment = Payment.builder()
                .id(1L)
                .amountCents(5000)
                .status("SUCCEEDED")
                .stripeSessionId("sess_123")
                .build();

        paymentService.savePayment(payment);

        verify(paymentRepository, times(1)).save(payment);
    }

    @Test
    @DisplayName("savePayment - null payment - delegates to repository save with null")
    void savePayment_nullPayment_delegatesToRepositorySaveWithNull() throws Exception {
        paymentService.savePayment(null);

        verify(paymentRepository, times(1)).save(null);
    }

    @Test
    @DisplayName("getByStripeSessionId - existing session id - returns payment")
    void getByStripeSessionId_existingSessionId_returnsPayment() throws Exception {
        final String sessionId = "sess_abc123";
        final Payment expected = Payment.builder()
                .id(1L)
                .stripeSessionId(sessionId)
                .amountCents(9900)
                .status("SUCCEEDED")
                .build();

        when(paymentRepository.findByStripeSessionId(sessionId)).thenReturn(expected);

        final Payment result = paymentService.getByStripeSessionId(sessionId);

        assertNotNull(result);
        assertEquals(sessionId, result.getStripeSessionId());
        assertEquals(9900, result.getAmountCents());
        verify(paymentRepository, times(1)).findByStripeSessionId(sessionId);
    }

    @Test
    @DisplayName("getByStripeSessionId - non-existing session id - returns null")
    void getByStripeSessionId_nonExistingSessionId_returnsNull() throws Exception {
        final String sessionId = "sess_nonexistent";

        when(paymentRepository.findByStripeSessionId(sessionId)).thenReturn(null);

        final Payment result = paymentService.getByStripeSessionId(sessionId);

        assertNull(result);
        verify(paymentRepository, times(1)).findByStripeSessionId(sessionId);
    }

    @Test
    @DisplayName("getByStripeSessionId - null session id - returns null from repository")
    void getByStripeSessionId_nullSessionId_returnsNullFromRepository() throws Exception {
        when(paymentRepository.findByStripeSessionId(null)).thenReturn(null);

        final Payment result = paymentService.getByStripeSessionId(null);

        assertNull(result);
        verify(paymentRepository, times(1)).findByStripeSessionId(null);
    }
}
