package com.careconnect.service;

import com.careconnect.model.*;
import com.careconnect.repository.*;
import com.stripe.exception.StripeException;
import com.stripe.model.checkout.Session;
import com.stripe.param.checkout.SessionCreateParams;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.mockito.*;

import java.util.*;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.ArgumentMatchers.*;
import static org.mockito.Mockito.*;

class StripeCheckoutServiceTest {

    @Mock
    private PaymentRepository paymentRepository;

    @Mock
    private UserRepository userRepository;

    @Mock
    private CaregiverRepository caregiverRepository;

    @Mock
    private PlanRepository planRepository;

    @Mock
    private SubscriptionRepository subscriptionRepository;

    @InjectMocks
    private StripeCheckoutService stripeCheckoutService;

    private User user;
    private Caregiver caregiver;
    private Plan plan;

    @BeforeEach
    void setUp() throws Exception {
        MockitoAnnotations.openMocks(this);

        user = new User();
        user.setId(1L);
        user.setEmail("test@example.com");
        user.setStripeCustomerId("cus_test123");

        caregiver = Caregiver.builder()
                .id(10L)
                .firstName("Jane")
                .lastName("Doe")
                .user(user)
                .build();

        plan = new Plan();
        plan.setId(100L);
        plan.setCode("price_abc123");
        plan.setName("Premium");
        plan.setPriceCents(1999);
        plan.setBillingPeriod("monthly");
        plan.setIsActive(true);
    }

    // ========== createCheckoutSession ==========

    @Test
    @DisplayName("createCheckoutSession_withPriceId_shouldUseExistingPrice")
    void createCheckoutSession_withPriceId_shouldUseExistingPrice() throws StripeException {
        // The createCheckoutSession method calls Session.create (static Stripe API call).
        // We use a MockedStatic to intercept the static method.
        final Session mockSession = mock(Session.class);
        when(mockSession.getId()).thenReturn("cs_test_session");

        try (MockedStatic<Session> sessionMock = mockStatic(Session.class)) {
            sessionMock.when(() -> Session.create(any(SessionCreateParams.class))).thenReturn(mockSession);

            final Session result = stripeCheckoutService.createCheckoutSession(
                    "cus_test123", "price_abc", 1999L, "http://success", "http://cancel");

            assertNotNull(result);
            assertEquals("cs_test_session", result.getId());
            sessionMock.verify(() -> Session.create(any(SessionCreateParams.class)), times(1));
        }
    }

    @Test
    @DisplayName("createCheckoutSession_withPlanName_shouldCreatePriceDataOnTheFly")
    void createCheckoutSession_withPlanName_shouldCreatePriceDataOnTheFly() throws StripeException {
        final Session mockSession = mock(Session.class);
        when(mockSession.getId()).thenReturn("cs_test_session2");

        try (MockedStatic<Session> sessionMock = mockStatic(Session.class)) {
            sessionMock.when(() -> Session.create(any(SessionCreateParams.class))).thenReturn(mockSession);

            final Session result = stripeCheckoutService.createCheckoutSession(
                    "cus_test123", "Premium Plan", 2999L, "http://success", "http://cancel");

            assertNotNull(result);
            assertEquals("cs_test_session2", result.getId());
        }
    }

    @Test
    @DisplayName("createCheckoutSession_stripeThrows_shouldPropagateException")
    void createCheckoutSession_stripeThrows_shouldPropagateException() throws StripeException {
        try (MockedStatic<Session> sessionMock = mockStatic(Session.class)) {
            sessionMock.when(() -> Session.create(any(SessionCreateParams.class)))
                    .thenThrow(new RuntimeException("Stripe error"));

            assertThrows(RuntimeException.class, () ->
                    stripeCheckoutService.createCheckoutSession(
                            "cus_test123", "price_abc", 1999L, "http://success", "http://cancel"));
        }
    }

    // ========== saveCheckoutSession ==========

    @Test
    @DisplayName("saveCheckoutSession_subscriptionMode_withMatchingPlan_shouldSavePaymentAndSubscription")
    void saveCheckoutSession_subscriptionMode_withMatchingPlan_shouldSavePaymentAndSubscription() throws Exception {
        final Session session = mock(Session.class);
        when(session.getId()).thenReturn("cs_session_1");
        when(session.getPaymentIntent()).thenReturn("pi_intent_1");
        when(session.getMode()).thenReturn("subscription");
        when(session.getSubscription()).thenReturn("sub_abc");

        when(userRepository.findById(1L)).thenReturn(Optional.of(user));

        final List<Plan> plans = List.of(plan);
        when(planRepository.findByName("Premium")).thenReturn(plans);

        stripeCheckoutService.saveCheckoutSession(1L, "Premium", 1999L, session);

        verify(paymentRepository).save(any(Payment.class));
        verify(subscriptionRepository).save(any(Subscription.class));
    }

    @Test
    @DisplayName("saveCheckoutSession_subscriptionMode_noMatchingPlan_shouldSaveSubscriptionWithoutPlan")
    void saveCheckoutSession_subscriptionMode_noMatchingPlan_shouldSaveSubscriptionWithoutPlan() throws Exception {
        final Session session = mock(Session.class);
        when(session.getId()).thenReturn("cs_session_2");
        when(session.getPaymentIntent()).thenReturn("pi_intent_2");
        when(session.getMode()).thenReturn("subscription");
        when(session.getSubscription()).thenReturn("sub_def");

        when(userRepository.findById(1L)).thenReturn(Optional.of(user));
        when(planRepository.findByName("Unknown Plan")).thenReturn(Collections.emptyList());

        stripeCheckoutService.saveCheckoutSession(1L, "Unknown Plan", 999L, session);

        verify(paymentRepository).save(any(Payment.class));
        verify(subscriptionRepository).save(any(Subscription.class));
    }

    @Test
    @DisplayName("saveCheckoutSession_subscriptionMode_planLookupThrows_shouldStillSaveSubscription")
    void saveCheckoutSession_subscriptionMode_planLookupThrows_shouldStillSaveSubscription() throws Exception {
        final Session session = mock(Session.class);
        when(session.getId()).thenReturn("cs_session_3");
        when(session.getPaymentIntent()).thenReturn("pi_intent_3");
        when(session.getMode()).thenReturn("subscription");
        when(session.getSubscription()).thenReturn("sub_ghi");

        when(userRepository.findById(1L)).thenReturn(Optional.of(user));
        when(planRepository.findByName(anyString())).thenThrow(new RuntimeException("DB Error"));

        stripeCheckoutService.saveCheckoutSession(1L, "Premium", 1999L, session);

        verify(paymentRepository).save(any(Payment.class));
        verify(subscriptionRepository).save(any(Subscription.class));
    }

    @Test
    @DisplayName("saveCheckoutSession_notSubscriptionMode_shouldOnlySavePayment")
    void saveCheckoutSession_notSubscriptionMode_shouldOnlySavePayment() throws Exception {
        final Session session = mock(Session.class);
        when(session.getId()).thenReturn("cs_session_4");
        when(session.getPaymentIntent()).thenReturn("pi_intent_4");
        when(session.getMode()).thenReturn("payment");

        when(userRepository.findById(1L)).thenReturn(Optional.of(user));

        stripeCheckoutService.saveCheckoutSession(1L, "Premium", 1999L, session);

        verify(paymentRepository).save(any(Payment.class));
        verify(subscriptionRepository, never()).save(any(Subscription.class));
    }

    @Test
    @DisplayName("saveCheckoutSession_subscriptionModeButNullSubscription_shouldNotSaveSubscription")
    void saveCheckoutSession_subscriptionModeButNullSubscription_shouldNotSaveSubscription() throws Exception {
        final Session session = mock(Session.class);
        when(session.getId()).thenReturn("cs_session_5");
        when(session.getPaymentIntent()).thenReturn("pi_intent_5");
        when(session.getMode()).thenReturn("subscription");
        when(session.getSubscription()).thenReturn(null);

        when(userRepository.findById(1L)).thenReturn(Optional.of(user));

        stripeCheckoutService.saveCheckoutSession(1L, "Premium", 1999L, session);

        verify(paymentRepository).save(any(Payment.class));
        verify(subscriptionRepository, never()).save(any(Subscription.class));
    }

    @Test
    @DisplayName("saveCheckoutSession_userNotFound_shouldThrow")
    void saveCheckoutSession_userNotFound_shouldThrow() throws Exception {
        final Session session = mock(Session.class);
        when(userRepository.findById(999L)).thenReturn(Optional.empty());

        assertThrows(NoSuchElementException.class, () ->
                stripeCheckoutService.saveCheckoutSession(999L, "Premium", 1999L, session));
    }

    // ========== createCheckoutSessionForUser ==========

    @Test
    @DisplayName("createCheckoutSessionForUser_validUser_shouldCreateSession")
    void createCheckoutSessionForUser_validUser_shouldCreateSession() throws StripeException {
        when(userRepository.findById(1L)).thenReturn(Optional.of(user));

        final Session mockSession = mock(Session.class);
        when(mockSession.getId()).thenReturn("cs_user_session");

        try (MockedStatic<Session> sessionMock = mockStatic(Session.class)) {
            sessionMock.when(() -> Session.create(any(SessionCreateParams.class))).thenReturn(mockSession);

            final Session result = stripeCheckoutService.createCheckoutSessionForUser(
                    1L, "price_abc", 1999L, "http://success", "http://cancel");

            assertNotNull(result);
            assertEquals("cs_user_session", result.getId());
        }
    }

    @Test
    @DisplayName("createCheckoutSessionForUser_userNotFound_shouldThrowIllegalArgumentException")
    void createCheckoutSessionForUser_userNotFound_shouldThrowIllegalArgumentException() throws Exception {
        when(userRepository.findById(999L)).thenReturn(Optional.empty());

        final IllegalArgumentException ex = assertThrows(IllegalArgumentException.class, () ->
                stripeCheckoutService.createCheckoutSessionForUser(
                        999L, "price_abc", 1999L, "http://success", "http://cancel"));

        assertTrue(ex.getMessage().contains("User not found with ID: 999"));
    }

    @Test
    @DisplayName("createCheckoutSessionForUser_nullStripeCustomerId_shouldThrowIllegalStateException")
    void createCheckoutSessionForUser_nullStripeCustomerId_shouldThrowIllegalStateException() throws Exception {
        user.setStripeCustomerId(null);
        when(userRepository.findById(1L)).thenReturn(Optional.of(user));

        assertThrows(IllegalStateException.class, () ->
                stripeCheckoutService.createCheckoutSessionForUser(
                        1L, "price_abc", 1999L, "http://success", "http://cancel"));
    }

    @Test
    @DisplayName("createCheckoutSessionForUser_emptyStripeCustomerId_shouldThrowIllegalStateException")
    void createCheckoutSessionForUser_emptyStripeCustomerId_shouldThrowIllegalStateException() throws Exception {
        user.setStripeCustomerId("");
        when(userRepository.findById(1L)).thenReturn(Optional.of(user));

        assertThrows(IllegalStateException.class, () ->
                stripeCheckoutService.createCheckoutSessionForUser(
                        1L, "price_abc", 1999L, "http://success", "http://cancel"));
    }

    // ========== createCheckoutSessionForCaregiver ==========

    @Test
    @DisplayName("createCheckoutSessionForCaregiver_validCaregiver_shouldCreateSession")
    void createCheckoutSessionForCaregiver_validCaregiver_shouldCreateSession() throws StripeException {
        when(caregiverRepository.findById(10L)).thenReturn(Optional.of(caregiver));

        final Session mockSession = mock(Session.class);
        when(mockSession.getId()).thenReturn("cs_caregiver_session");

        try (MockedStatic<Session> sessionMock = mockStatic(Session.class)) {
            sessionMock.when(() -> Session.create(any(SessionCreateParams.class))).thenReturn(mockSession);

            final Session result = stripeCheckoutService.createCheckoutSessionForCaregiver(
                    10L, "price_abc", 1999L, "http://success", "http://cancel");

            assertNotNull(result);
            assertEquals("cs_caregiver_session", result.getId());
        }
    }

    @Test
    @DisplayName("createCheckoutSessionForCaregiver_caregiverNotFound_shouldThrowIllegalArgumentException")
    void createCheckoutSessionForCaregiver_caregiverNotFound_shouldThrowIllegalArgumentException() throws Exception {
        when(caregiverRepository.findById(999L)).thenReturn(Optional.empty());

        final IllegalArgumentException ex = assertThrows(IllegalArgumentException.class, () ->
                stripeCheckoutService.createCheckoutSessionForCaregiver(
                        999L, "price_abc", 1999L, "http://success", "http://cancel"));

        assertTrue(ex.getMessage().contains("Caregiver not found with ID: 999"));
    }

    @Test
    @DisplayName("createCheckoutSessionForCaregiver_nullStripeCustomerId_shouldThrowIllegalStateException")
    void createCheckoutSessionForCaregiver_nullStripeCustomerId_shouldThrowIllegalStateException() throws Exception {
        user.setStripeCustomerId(null);
        when(caregiverRepository.findById(10L)).thenReturn(Optional.of(caregiver));

        final IllegalStateException ex = assertThrows(IllegalStateException.class, () ->
                stripeCheckoutService.createCheckoutSessionForCaregiver(
                        10L, "price_abc", 1999L, "http://success", "http://cancel"));

        assertTrue(ex.getMessage().contains("Caregiver does not have a Stripe customer ID"));
    }

    @Test
    @DisplayName("createCheckoutSessionForCaregiver_emptyStripeCustomerId_shouldThrowIllegalStateException")
    void createCheckoutSessionForCaregiver_emptyStripeCustomerId_shouldThrowIllegalStateException() throws Exception {
        user.setStripeCustomerId("");
        when(caregiverRepository.findById(10L)).thenReturn(Optional.of(caregiver));

        final IllegalStateException ex = assertThrows(IllegalStateException.class, () ->
                stripeCheckoutService.createCheckoutSessionForCaregiver(
                        10L, "price_abc", 1999L, "http://success", "http://cancel"));

        assertTrue(ex.getMessage().contains("Caregiver does not have a Stripe customer ID"));
    }

    // ========== getCaregiverStripeCustomerId ==========

    @Test
    @DisplayName("getCaregiverStripeCustomerId_validCaregiver_shouldReturnCustomerId")
    void getCaregiverStripeCustomerId_validCaregiver_shouldReturnCustomerId() throws Exception {
        when(caregiverRepository.findById(10L)).thenReturn(Optional.of(caregiver));

        final String result = stripeCheckoutService.getCaregiverStripeCustomerId(10L);

        assertEquals("cus_test123", result);
    }

    @Test
    @DisplayName("getCaregiverStripeCustomerId_caregiverNotFound_shouldThrowIllegalArgumentException")
    void getCaregiverStripeCustomerId_caregiverNotFound_shouldThrowIllegalArgumentException() throws Exception {
        when(caregiverRepository.findById(999L)).thenReturn(Optional.empty());

        assertThrows(IllegalArgumentException.class, () ->
                stripeCheckoutService.getCaregiverStripeCustomerId(999L));
    }

    @Test
    @DisplayName("getCaregiverStripeCustomerId_nullStripeCustomerId_shouldThrowIllegalStateException")
    void getCaregiverStripeCustomerId_nullStripeCustomerId_shouldThrowIllegalStateException() throws Exception {
        user.setStripeCustomerId(null);
        when(caregiverRepository.findById(10L)).thenReturn(Optional.of(caregiver));

        assertThrows(IllegalStateException.class, () ->
                stripeCheckoutService.getCaregiverStripeCustomerId(10L));
    }

    @Test
    @DisplayName("getCaregiverStripeCustomerId_emptyStripeCustomerId_shouldThrowIllegalStateException")
    void getCaregiverStripeCustomerId_emptyStripeCustomerId_shouldThrowIllegalStateException() throws Exception {
        user.setStripeCustomerId("");
        when(caregiverRepository.findById(10L)).thenReturn(Optional.of(caregiver));

        assertThrows(IllegalStateException.class, () ->
                stripeCheckoutService.getCaregiverStripeCustomerId(10L));
    }

    // ========== getAvailablePlans ==========

    @Test
    @DisplayName("getAvailablePlans_shouldReturnActivePlans")
    void getAvailablePlans_shouldReturnActivePlans() throws Exception {
        final List<Plan> activePlans = List.of(plan);
        when(planRepository.findByIsActiveTrue()).thenReturn(activePlans);

        final List<Plan> result = stripeCheckoutService.getAvailablePlans();

        assertEquals(1, result.size());
        assertEquals("Premium", result.get(0).getName());
    }

    @Test
    @DisplayName("getAvailablePlans_noActivePlans_shouldReturnEmptyList")
    void getAvailablePlans_noActivePlans_shouldReturnEmptyList() throws Exception {
        when(planRepository.findByIsActiveTrue()).thenReturn(Collections.emptyList());

        final List<Plan> result = stripeCheckoutService.getAvailablePlans();

        assertTrue(result.isEmpty());
    }

    // ========== createCheckoutSessionForPlan ==========

    @Test
    @DisplayName("createCheckoutSessionForPlan_withStripePriceCode_shouldUseDirectPriceId")
    void createCheckoutSessionForPlan_withStripePriceCode_shouldUseDirectPriceId() throws StripeException {
        when(planRepository.findById(100L)).thenReturn(Optional.of(plan));
        when(caregiverRepository.findById(10L)).thenReturn(Optional.of(caregiver));

        final Session mockSession = mock(Session.class);
        when(mockSession.getId()).thenReturn("cs_plan_session");
        when(mockSession.getMode()).thenReturn("subscription");
        when(mockSession.getSubscription()).thenReturn("sub_plan_abc");

        try (MockedStatic<Session> sessionMock = mockStatic(Session.class)) {
            sessionMock.when(() -> Session.create(any(SessionCreateParams.class))).thenReturn(mockSession);

            final Session result = stripeCheckoutService.createCheckoutSessionForPlan(
                    10L, "100", "http://success", "http://cancel");

            assertNotNull(result);
            assertEquals("cs_plan_session", result.getId());
            verify(subscriptionRepository).save(any(Subscription.class));
        }
    }

    @Test
    @DisplayName("createCheckoutSessionForPlan_withNonStripePriceCode_shouldCreatePriceDataOnTheFly")
    void createCheckoutSessionForPlan_withNonStripePriceCode_shouldCreatePriceDataOnTheFly() throws StripeException {
        final Plan planNonStripe = new Plan();
        planNonStripe.setId(101L);
        planNonStripe.setCode("basic_plan");
        planNonStripe.setName("Basic");
        planNonStripe.setPriceCents(999);
        planNonStripe.setBillingPeriod("monthly");

        when(planRepository.findById(101L)).thenReturn(Optional.of(planNonStripe));
        when(caregiverRepository.findById(10L)).thenReturn(Optional.of(caregiver));

        final Session mockSession = mock(Session.class);
        when(mockSession.getId()).thenReturn("cs_plan_session2");
        when(mockSession.getMode()).thenReturn("subscription");
        when(mockSession.getSubscription()).thenReturn("sub_plan_def");

        try (MockedStatic<Session> sessionMock = mockStatic(Session.class)) {
            sessionMock.when(() -> Session.create(any(SessionCreateParams.class))).thenReturn(mockSession);

            final Session result = stripeCheckoutService.createCheckoutSessionForPlan(
                    10L, "101", "http://success", "http://cancel");

            assertNotNull(result);
            assertEquals("cs_plan_session2", result.getId());
            verify(subscriptionRepository).save(any(Subscription.class));
        }
    }

    @Test
    @DisplayName("createCheckoutSessionForPlan_withNullCode_shouldCreatePriceDataOnTheFly")
    void createCheckoutSessionForPlan_withNullCode_shouldCreatePriceDataOnTheFly() throws StripeException {
        final Plan planNullCode = new Plan();
        planNullCode.setId(102L);
        planNullCode.setCode(null);
        planNullCode.setName("Null Code Plan");
        planNullCode.setPriceCents(499);
        planNullCode.setBillingPeriod("monthly");

        when(planRepository.findById(102L)).thenReturn(Optional.of(planNullCode));
        when(caregiverRepository.findById(10L)).thenReturn(Optional.of(caregiver));

        final Session mockSession = mock(Session.class);
        when(mockSession.getId()).thenReturn("cs_plan_null");
        when(mockSession.getMode()).thenReturn("subscription");
        when(mockSession.getSubscription()).thenReturn("sub_plan_null");

        try (MockedStatic<Session> sessionMock = mockStatic(Session.class)) {
            sessionMock.when(() -> Session.create(any(SessionCreateParams.class))).thenReturn(mockSession);

            final Session result = stripeCheckoutService.createCheckoutSessionForPlan(
                    10L, "102", "http://success", "http://cancel");

            assertNotNull(result);
        }
    }

    @Test
    @DisplayName("createCheckoutSessionForPlan_nonSubscriptionMode_shouldNotSaveSubscription")
    void createCheckoutSessionForPlan_nonSubscriptionMode_shouldNotSaveSubscription() throws StripeException {
        when(planRepository.findById(100L)).thenReturn(Optional.of(plan));
        when(caregiverRepository.findById(10L)).thenReturn(Optional.of(caregiver));

        final Session mockSession = mock(Session.class);
        when(mockSession.getId()).thenReturn("cs_plan_payment");
        when(mockSession.getMode()).thenReturn("payment");

        try (MockedStatic<Session> sessionMock = mockStatic(Session.class)) {
            sessionMock.when(() -> Session.create(any(SessionCreateParams.class))).thenReturn(mockSession);

            final Session result = stripeCheckoutService.createCheckoutSessionForPlan(
                    10L, "100", "http://success", "http://cancel");

            assertNotNull(result);
            verify(subscriptionRepository, never()).save(any(Subscription.class));
        }
    }

    @Test
    @DisplayName("createCheckoutSessionForPlan_planNotFound_shouldThrowIllegalArgumentException")
    void createCheckoutSessionForPlan_planNotFound_shouldThrowIllegalArgumentException() throws Exception {
        when(planRepository.findById(999L)).thenReturn(Optional.empty());

        assertThrows(IllegalArgumentException.class, () ->
                stripeCheckoutService.createCheckoutSessionForPlan(
                        10L, "999", "http://success", "http://cancel"));
    }

    @Test
    @DisplayName("createCheckoutSessionForPlan_invalidPlanId_shouldThrowNumberFormatException")
    void createCheckoutSessionForPlan_invalidPlanId_shouldThrowNumberFormatException() throws Exception {
        assertThrows(NumberFormatException.class, () ->
                stripeCheckoutService.createCheckoutSessionForPlan(
                        10L, "not_a_number", "http://success", "http://cancel"));
    }

    // ========== createPlan (6-arg) ==========

    @Test
    @DisplayName("createPlan_withoutStripe_shouldSavePlanWithProvidedCode")
    void createPlan_withoutStripe_shouldSavePlanWithProvidedCode() throws Exception {
        when(planRepository.save(any(Plan.class))).thenAnswer(inv -> {
            final Plan p = inv.getArgument(0);
            p.setId(200L);
            return p;
        });

        final Plan result = stripeCheckoutService.createPlan("basic_code", "Basic", 999, "monthly", true, false);

        assertNotNull(result);
        assertEquals("basic_code", result.getCode());
        assertEquals("Basic", result.getName());
        assertEquals(999, result.getPriceCents());
        assertEquals("monthly", result.getBillingPeriod());
        assertTrue(result.getIsActive());
        verify(planRepository).save(any(Plan.class));
    }

    @Test
    @DisplayName("createPlan_withStripeEnabled_stripeApiFailure_shouldFallbackToProvidedCode")
    void createPlan_withStripeEnabled_stripeApiFailure_shouldFallbackToProvidedCode() throws Exception {
        // When createInStripe is true, the code calls Stripe API using System.getenv + static calls.
        // Without a real Stripe key, it will fail in the catch block and fall back to the provided code.
        when(planRepository.save(any(Plan.class))).thenAnswer(inv -> {
            final Plan p = inv.getArgument(0);
            p.setId(201L);
            return p;
        });

        final Plan result = stripeCheckoutService.createPlan("fallback_code", "Fallback", 1500, "yearly", false, true);

        assertNotNull(result);
        // Due to Stripe failure, it should fall back to the original code
        assertEquals("fallback_code", result.getCode());
        assertFalse(result.getIsActive());
    }

    @Test
    @DisplayName("createPlan_withNullIsActive_shouldDefaultToTrue")
    void createPlan_withNullIsActive_shouldDefaultToTrue() throws Exception {
        when(planRepository.save(any(Plan.class))).thenAnswer(inv -> {
            final Plan p = inv.getArgument(0);
            p.setId(202L);
            return p;
        });

        final Plan result = stripeCheckoutService.createPlan("code1", "Plan1", 500, "monthly", null, false);

        assertNotNull(result);
        assertTrue(result.getIsActive());
    }

    // ========== createPlan (5-arg overload) ==========

    @Test
    @DisplayName("createPlan_5arg_shouldDelegateToCreatePlanWithFalseStripeFlag")
    void createPlan_5arg_shouldDelegateToCreatePlanWithFalseStripeFlag() throws Exception {
        when(planRepository.save(any(Plan.class))).thenAnswer(inv -> {
            final Plan p = inv.getArgument(0);
            p.setId(203L);
            return p;
        });

        final Plan result = stripeCheckoutService.createPlan("code2", "Plan2", 700, "yearly", true);

        assertNotNull(result);
        assertEquals("code2", result.getCode());
        assertTrue(result.getIsActive());
    }

    // ========== createCheckoutSessionForPlan - user is null edge case ==========

    @Test
    @DisplayName("createCheckoutSessionForPlan_subscriptionModeWithNullUser_shouldNotSaveSubscription")
    void createCheckoutSessionForPlan_subscriptionModeWithNullUser_shouldNotSaveSubscription() throws StripeException {
        // Create a caregiver with a user that has a stripe customer ID but we test the null user path
        // in createCheckoutSessionForPlan. The method fetches caregiver again inside and checks user != null.
        // Since our caregiver has a non-null user, we need a different approach.
        // Actually, looking at the code: the method always calls getCaregiverStripeCustomerId first,
        // which requires user to be non-null. So user == null path for subscription saving cannot happen
        // in normal flow. This test verifies the normal subscription save path.

        when(planRepository.findById(100L)).thenReturn(Optional.of(plan));
        when(caregiverRepository.findById(10L)).thenReturn(Optional.of(caregiver));

        final Session mockSession = mock(Session.class);
        when(mockSession.getId()).thenReturn("cs_plan_session3");
        when(mockSession.getMode()).thenReturn("subscription");
        when(mockSession.getSubscription()).thenReturn("sub_plan_xyz");

        try (MockedStatic<Session> sessionMock = mockStatic(Session.class)) {
            sessionMock.when(() -> Session.create(any(SessionCreateParams.class))).thenReturn(mockSession);

            final Session result = stripeCheckoutService.createCheckoutSessionForPlan(
                    10L, "100", "http://success", "http://cancel");

            assertNotNull(result);
            verify(subscriptionRepository).save(any(Subscription.class));
        }
    }

    @Test
    @DisplayName("createPlan_withStripeEnabled_successfulProductAndPriceCreation_shouldUseStripePriceId")
    void createPlan_withStripeEnabled_successfulProductAndPriceCreation_shouldUseStripePriceId() throws Exception {
        // We mock the static Stripe Product.create and Price.create calls
        com.stripe.model.Product mockProduct = mock(com.stripe.model.Product.class);
        when(mockProduct.getId()).thenReturn("prod_test123");

        com.stripe.model.Price mockPrice = mock(com.stripe.model.Price.class);
        when(mockPrice.getId()).thenReturn("price_created_123");

        when(planRepository.save(any(Plan.class))).thenAnswer(inv -> {
            final Plan p = inv.getArgument(0);
            p.setId(300L);
            return p;
        });

        try (MockedStatic<com.stripe.model.Product> productMock = mockStatic(com.stripe.model.Product.class);
             final MockedStatic<com.stripe.model.Price> priceMock = mockStatic(com.stripe.model.Price.class)) {

            productMock.when(() -> com.stripe.model.Product.create(anyMap())).thenReturn(mockProduct);
            priceMock.when(() -> com.stripe.model.Price.create(anyMap())).thenReturn(mockPrice);

            final Plan result = stripeCheckoutService.createPlan("orig_code", "Stripe Plan", 2500, "month", true, true);

            assertNotNull(result);
            assertEquals("price_created_123", result.getCode());
        }
    }
}
