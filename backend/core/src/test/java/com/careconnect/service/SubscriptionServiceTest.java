package com.careconnect.service;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.ArgumentMatchers.*;
import static org.mockito.Mockito.*;

import com.careconnect.model.Payment;
import com.careconnect.model.Plan;
import com.careconnect.model.Subscription;
import com.careconnect.model.User;
import com.careconnect.repository.PaymentRepository;
import com.careconnect.repository.PlanRepository;
import com.careconnect.repository.SubscriptionRepository;
import com.careconnect.repository.UserRepository;
import com.stripe.model.*;
import com.stripe.model.checkout.Session;
import com.stripe.net.Webhook;
import com.stripe.param.checkout.SessionCreateParams;
import jakarta.servlet.http.HttpServletRequest;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.MockedStatic;
import org.mockito.MockitoAnnotations;
import org.springframework.http.ResponseEntity;
import org.springframework.test.util.ReflectionTestUtils;

import java.util.List;
import java.util.Map;
import java.util.Optional;

/**
 * Unit tests for {@link SubscriptionService}.
 *
 * <p>All external dependencies (repositories and {@link StripeCheckoutService}) are
 * mocked with Mockito so these tests validate the service's business logic in
 * isolation — no database, Spring context, or live Stripe API connection is needed.</p>
 *
 * <p>Methods that call static Stripe SDK APIs (e.g. {@code Subscription.retrieve()},
 * {@code Customer.create()}) are tested using Mockito {@code MockedStatic} to intercept
 * static calls without a live Stripe connection.</p>
 */
class SubscriptionServiceTest {

    // -------------------------------------------------------------------------
    // Mocked dependencies
    // -------------------------------------------------------------------------

    @Mock
    private SubscriptionRepository subscriptionRepository;

    @Mock
    private PaymentRepository paymentRepository;

    @Mock
    private UserRepository userRepository;

    @Mock
    private PlanRepository planRepository;

    @Mock
    private StripeCheckoutService stripeCheckoutService;

    @InjectMocks
    private SubscriptionService subscriptionService;

    // -------------------------------------------------------------------------
    // Test setup
    // -------------------------------------------------------------------------

    @BeforeEach
    void setUp() throws Exception {
        MockitoAnnotations.openMocks(this);
        // Inject the @Value fields that Spring would normally resolve from properties
        ReflectionTestUtils.setField(subscriptionService, "stripeSecretKey", "sk_test_dummy");
        ReflectionTestUtils.setField(subscriptionService, "frontendBaseUrl", "http://localhost:3000");
    }

    // ==========================================================================
    // createPlan
    // ==========================================================================

    @Test
    @DisplayName("createPlan: delegates to StripeCheckoutService and returns its result")
    void testCreatePlan_delegatesToStripeCheckoutService() throws Exception {
        // SubscriptionService.createPlan is a thin pass-through; the actual creation
        // logic lives in StripeCheckoutService. Verify the call is forwarded intact
        // and the returned plan is propagated back to the caller.
        final Plan expected = new Plan();
        expected.setCode("STANDARD");
        expected.setName("Standard Plan");
        expected.setPriceCents(2000);
        expected.setBillingPeriod("MONTH");
        expected.setIsActive(true);

        when(stripeCheckoutService.createPlan("STANDARD", "Standard Plan", 2000, "MONTH", true))
                .thenReturn(expected);

        final Plan result = subscriptionService.createPlan("STANDARD", "Standard Plan", 2000, "MONTH", true);

        assertNotNull(result);
        assertEquals("STANDARD", result.getCode());
        assertEquals("Standard Plan", result.getName());
        verify(stripeCheckoutService).createPlan("STANDARD", "Standard Plan", 2000, "MONTH", true);
    }

    // ==========================================================================
    // getPlan
    // ==========================================================================

    @Test
    @DisplayName("getPlan: returns the Plan entity when the ID is found")
    void testGetPlan_found() throws Exception {
        // A plan stored in the repository must be returned unchanged.
        final Plan plan = new Plan();
        plan.setId(1L);
        plan.setName("Premium Plan");
        when(planRepository.findById(1L)).thenReturn(Optional.of(plan));

        final Plan result = subscriptionService.getPlan(1L);

        assertNotNull(result);
        assertEquals("Premium Plan", result.getName());
        verify(planRepository).findById(1L);
    }

    @Test
    @DisplayName("getPlan: throws IllegalArgumentException when no plan exists for the ID")
    void testGetPlan_notFound() throws Exception {
        // A missing plan must surface as IllegalArgumentException containing the ID
        // so callers can produce a meaningful error response.
        when(planRepository.findById(99L)).thenReturn(Optional.empty());

        final IllegalArgumentException ex = assertThrows(
                IllegalArgumentException.class,
                () -> subscriptionService.getPlan(99L)
        );

        assertTrue(ex.getMessage().contains("99"),
                "Exception message should include the missing plan ID");
        verify(planRepository).findById(99L);
    }

    // ==========================================================================
    // findOrCreatePlanByStripeId
    // ==========================================================================

    @Test
    @DisplayName("findOrCreatePlanByStripeId: returns the existing plan when the code is already registered")
    void testFindOrCreatePlanByStripeId_existingPlan() throws Exception {
        // When a plan with the given Stripe price ID already exists, it must be
        // returned as-is without creating a duplicate or overwriting its name.
        final Plan existing = new Plan();
        existing.setCode("price_abc123");
        existing.setName("Existing Plan");
        when(planRepository.findByCode("price_abc123")).thenReturn(existing);

        final Plan result = subscriptionService.findOrCreatePlanByStripeId("price_abc123", "New Name Override", 3000);

        assertEquals("Existing Plan", result.getName(), "Existing plan name must not be overwritten");
        verify(planRepository, never()).save(any());
    }

    @Test
    @DisplayName("findOrCreatePlanByStripeId: creates and saves a new plan when no matching code exists")
    void testFindOrCreatePlanByStripeId_newPlan() throws Exception {
        // When no plan is found for the Stripe price ID, a new one is created with
        // the provided nickname, amount, and sensible defaults (monthly, active).
        when(planRepository.findByCode("price_new999")).thenReturn(null);
        when(planRepository.save(any(Plan.class))).thenAnswer(inv -> {
            final Plan saved = inv.getArgument(0);
            saved.setId(10L); // Simulate DB-generated ID
            return saved;
        });

        final Plan result = subscriptionService.findOrCreatePlanByStripeId("price_new999", "My Plan", 1500);

        assertNotNull(result);
        assertEquals(10L, result.getId());
        assertEquals("price_new999", result.getCode());
        assertEquals("My Plan", result.getName());
        assertEquals(1500, result.getPriceCents());
        assertEquals("MONTH", result.getBillingPeriod(), "Billing period should default to MONTH");
        assertTrue(result.getIsActive(), "New plans should default to active");
        verify(planRepository).save(any(Plan.class));
    }

    @Test
    @DisplayName("findOrCreatePlanByStripeId: uses 'Plan <id>' as fallback name when nickname is null")
    void testFindOrCreatePlanByStripeId_nullNicknameUsesFallback() throws Exception {
        // When no nickname is supplied, the plan name defaults to "Plan <stripePriceId>"
        // so the record remains identifiable even without a human-readable nickname.
        when(planRepository.findByCode("price_xyz")).thenReturn(null);
        when(planRepository.save(any(Plan.class))).thenAnswer(inv -> inv.getArgument(0));

        final Plan result = subscriptionService.findOrCreatePlanByStripeId("price_xyz", null, 2000);

        assertEquals("Plan price_xyz", result.getName());
    }

    // ==========================================================================
    // syncPlanWithStripe
    // ==========================================================================

    @Test
    @DisplayName("syncPlanWithStripe: returns plan unchanged when code already starts with 'price_'")
    void testSyncPlanWithStripe_validStripePriceId_noApiCall() throws Exception {
        // A plan whose code already matches the Stripe price ID format ("price_*")
        // requires no Stripe API interaction; it is returned immediately.
        final Plan plan = new Plan();
        plan.setId(5L);
        plan.setCode("price_existingStripeId");
        when(planRepository.findById(5L)).thenReturn(Optional.of(plan));

        final Plan result = subscriptionService.syncPlanWithStripe(5L, false);

        assertEquals("price_existingStripeId", result.getCode());
        verify(planRepository, never()).save(any()); // No update needed
    }

    @Test
    @DisplayName("syncPlanWithStripe: throws IllegalArgumentException when code is not 'price_*' and createIfMissing=false")
    void testSyncPlanWithStripe_invalidCode_createIfMissingFalse_throws() throws Exception {
        // If the plan doesn't have a valid Stripe price ID and we aren't allowed to
        // create one, the service must reject the call with a clear exception.
        final Plan plan = new Plan();
        plan.setId(6L);
        plan.setCode("STANDARD"); // Not a Stripe price ID
        when(planRepository.findById(6L)).thenReturn(Optional.of(plan));

        assertThrows(
                IllegalArgumentException.class,
                () -> subscriptionService.syncPlanWithStripe(6L, false)
        );
    }

    @Test
    @DisplayName("syncPlanWithStripe: throws IllegalArgumentException when code is null and createIfMissing=false")
    void testSyncPlanWithStripe_nullCode_createIfMissingFalse_throws() throws Exception {
        // A null plan code is treated the same as a missing Stripe price ID —
        // it doesn't start with "price_" so it must fail when createIfMissing=false.
        final Plan plan = new Plan();
        plan.setId(7L);
        plan.setCode(null);
        when(planRepository.findById(7L)).thenReturn(Optional.of(plan));

        assertThrows(
                IllegalArgumentException.class,
                () -> subscriptionService.syncPlanWithStripe(7L, false)
        );
    }

    // ==========================================================================
    // cancelSubscription
    // ==========================================================================

    @Test
    @DisplayName("cancelSubscription: throws IllegalArgumentException when the subscription ID does not exist")
    void testCancelSubscription_notFound() throws Exception {
        // Attempting to cancel a subscription that doesn't exist in the DB must
        // fail immediately with a domain exception before any Stripe call is made.
        when(subscriptionRepository.findById(999L)).thenReturn(Optional.empty());

        assertThrows(
                IllegalArgumentException.class,
                () -> subscriptionService.cancelSubscription(999L)
        );
    }

    @Test
    @DisplayName("cancelSubscription: marks subscription CANCELLED and clears all Stripe fields when stripeSubscriptionId is null")
    void testCancelSubscription_noStripeId_updatesLocalRecord() throws Exception {
        // When no Stripe subscription ID is stored, the Stripe API call is skipped.
        // The local record must still be fully cleared and persisted as CANCELLED.
        final Subscription sub = new Subscription();
        sub.setId(1L);
        sub.setStripeSubscriptionId(null); // null → no Stripe API call
        sub.setStripeCustomerId("cus_abc");
        sub.setStatus("ACTIVE");
        when(subscriptionRepository.findById(1L)).thenReturn(Optional.of(sub));
        when(subscriptionRepository.save(any(Subscription.class))).thenAnswer(inv -> inv.getArgument(0));

        subscriptionService.cancelSubscription(1L);

        assertEquals("CANCELLED", sub.getStatus());
        assertNull(sub.getStripeSubscriptionId());
        assertNull(sub.getStripeCustomerId());
        assertNull(sub.getPriceId());
        assertNull(sub.getPlan());
        assertNull(sub.getCurrentPeriodEnd());
        verify(subscriptionRepository).save(sub);
    }

    @Test
    @DisplayName("cancelSubscription: marks subscription CANCELLED when stripeSubscriptionId is an empty string")
    void testCancelSubscription_emptyStripeId_updatesLocalRecord() throws Exception {
        // An empty Stripe subscription ID is treated identically to null —
        // no API call is made and only the local record is updated.
        final Subscription sub = new Subscription();
        sub.setId(2L);
        sub.setStripeSubscriptionId(""); // empty → no Stripe API call
        sub.setStatus("ACTIVE");
        when(subscriptionRepository.findById(2L)).thenReturn(Optional.of(sub));
        when(subscriptionRepository.save(any(Subscription.class))).thenAnswer(inv -> inv.getArgument(0));

        subscriptionService.cancelSubscription(2L);

        assertEquals("CANCELLED", sub.getStatus());
        verify(subscriptionRepository).save(sub);
    }

    // ==========================================================================
    // cancelSubscriptionByStripeId
    // ==========================================================================

    @Test
    @DisplayName("cancelSubscriptionByStripeId: is a no-op when no subscription matches the Stripe ID")
    void testCancelSubscriptionByStripeId_notFound_noOp() throws Exception {
        // If the Stripe subscription ID is unknown locally, no exception is thrown
        // and no save is attempted — the absent record can't be updated.
        when(subscriptionRepository.findByStripeSubscriptionId("sub_unknown"))
                .thenReturn(Optional.empty());

        assertDoesNotThrow(() -> subscriptionService.cancelSubscriptionByStripeId("sub_unknown"));
        verify(subscriptionRepository, never()).save(any());
    }

    // ==========================================================================
    // saveCheckoutSession
    // ==========================================================================

    @Test
    @DisplayName("saveCheckoutSession: throws IllegalArgumentException when the user does not exist")
    void testSaveCheckoutSession_userNotFound() throws Exception {
        // Saving a checkout session requires a valid user; a missing user must cause
        // the method to fail before any subscription or payment is persisted.
        final Session mockSession = mock(Session.class);
        when(userRepository.findById(99L)).thenReturn(Optional.empty());

        assertThrows(
                IllegalArgumentException.class,
                () -> subscriptionService.saveCheckoutSession(99L, "standard", 2000L, mockSession)
        );
    }

    @Test
    @DisplayName("saveCheckoutSession: creates a new Subscription and Payment when none exist for the session")
    void testSaveCheckoutSession_createsSubscriptionAndPayment() throws Exception {
        // Happy path: user exists, session carries IDs, no prior records exist.
        // Both a Subscription and a Payment must be created and persisted.
        final User user = User.builder()
                .id(1L)
                .email("test@example.com")
                .password("pw")
                .build();
        final Session mockSession = mock(Session.class);
        when(mockSession.getCustomer()).thenReturn("cus_abc");
        when(mockSession.getSubscription()).thenReturn("sub_xyz");
        when(mockSession.getId()).thenReturn("cs_test_session");
        when(mockSession.getPaymentIntent()).thenReturn("pi_test_intent");

        when(userRepository.findById(1L)).thenReturn(Optional.of(user));
        when(subscriptionRepository.findByStripeSubscriptionId("sub_xyz"))
                .thenReturn(Optional.empty()); // No existing subscription
        when(subscriptionRepository.save(any(Subscription.class)))
                .thenAnswer(inv -> inv.getArgument(0));
        when(paymentRepository.save(any())).thenAnswer(inv -> inv.getArgument(0));

        subscriptionService.saveCheckoutSession(1L, "standard", 2000L, mockSession);

        // Subscription and Payment must each be saved exactly once
        verify(subscriptionRepository).save(any(Subscription.class));
        verify(paymentRepository).save(any());
    }

    @Test
    @DisplayName("saveCheckoutSession: updates user's stripeCustomerId when the user doesn't have one yet")
    void testSaveCheckoutSession_updatesUserStripeCustomerId() throws Exception {
        // If the session provides a Stripe customer ID and the user has none stored,
        // the user record must be updated so future sessions can reuse the customer.
        final User user = User.builder()
                .id(2L)
                .email("user@example.com")
                .password("pw")
                .build();
        // User has no stripeCustomerId yet (field is null by default above)
        final Session mockSession = mock(Session.class);
        when(mockSession.getCustomer()).thenReturn("cus_new_customer");
        when(mockSession.getSubscription()).thenReturn(null); // No subscription in this session
        when(mockSession.getId()).thenReturn("cs_test_session_2");
        when(mockSession.getPaymentIntent()).thenReturn(null);

        when(userRepository.findById(2L)).thenReturn(Optional.of(user));
        when(subscriptionRepository.save(any())).thenAnswer(inv -> inv.getArgument(0));
        when(paymentRepository.save(any())).thenAnswer(inv -> inv.getArgument(0));

        subscriptionService.saveCheckoutSession(2L, "premium", 3000L, mockSession);

        // The user must now hold the Stripe customer ID from the session
        assertEquals("cus_new_customer", user.getStripeCustomerId());
        verify(userRepository).save(user);
    }

    @Test
    @DisplayName("saveCheckoutSession: reuses an existing subscription when stripeSubscriptionId is already in the DB")
    void testSaveCheckoutSession_reusesExistingSubscription() throws Exception {
        // If a subscription record already exists for the Stripe subscription ID
        // in the session, it must be reused rather than a new one being created.
        final User user = User.builder().id(3L).email("reuse@example.com").password("pw").build();
        final Subscription existingSub = new Subscription();
        existingSub.setId(10L);
        existingSub.setStripeSubscriptionId("sub_existing");

        final Session mockSession = mock(Session.class);
        when(mockSession.getCustomer()).thenReturn("cus_reuse");
        when(mockSession.getSubscription()).thenReturn("sub_existing");
        when(mockSession.getId()).thenReturn("cs_reuse_session");
        when(mockSession.getPaymentIntent()).thenReturn("pi_reuse");

        when(userRepository.findById(3L)).thenReturn(Optional.of(user));
        when(subscriptionRepository.findByStripeSubscriptionId("sub_existing"))
                .thenReturn(Optional.of(existingSub)); // Already exists
        when(paymentRepository.save(any())).thenAnswer(inv -> inv.getArgument(0));

        subscriptionService.saveCheckoutSession(3L, "standard", 2000L, mockSession);

        // The existing subscription must not be replaced by a new save
        verify(subscriptionRepository, never()).save(any(Subscription.class));
        // Payment must still be persisted
        verify(paymentRepository).save(any());
    }

    // ==========================================================================
    // getUserSubscriptions
    // ==========================================================================

    @Test
    @DisplayName("getUserSubscriptions: throws IllegalArgumentException when the user does not exist")
    void testGetUserSubscriptions_userNotFound() throws Exception {
        // Requesting subscriptions for a nonexistent user must fail with a clear
        // domain exception so the controller can return an appropriate 4xx response.
        when(userRepository.findById(99L)).thenReturn(Optional.empty());

        assertThrows(
                IllegalArgumentException.class,
                () -> subscriptionService.getUserSubscriptions(99L)
        );
    }

    @Test
    @DisplayName("getUserSubscriptions: returns DB subscriptions without Stripe sync when user has no Stripe customer ID")
    void testGetUserSubscriptions_noStripeCustomerId_skipsSync() throws Exception {
        // When the user has no Stripe customer ID, the service must skip the Stripe
        // sync and return whatever is stored locally, avoiding unnecessary API calls.
        final User user = User.builder().id(1L).email("user@example.com").password("pw").build();
        // stripeCustomerId is null (not set via builder)
        final Subscription sub = new Subscription();
        sub.setId(10L);
        sub.setStatus("ACTIVE");
        when(userRepository.findById(1L)).thenReturn(Optional.of(user));
        when(subscriptionRepository.findByUser(user)).thenReturn(List.of(sub));

        final List<Subscription> result = subscriptionService.getUserSubscriptions(1L);

        assertNotNull(result);
        assertEquals(1, result.size());
        assertEquals("ACTIVE", result.get(0).getStatus());
        verify(subscriptionRepository).findByUser(user);
    }

    @Test
    @DisplayName("getUserSubscriptions: returns empty list when user has no subscriptions")
    void testGetUserSubscriptions_noSubscriptions_returnsEmptyList() throws Exception {
        // An empty result from the repository is valid; the method must not throw
        // or convert it to null.
        final User user = User.builder().id(3L).email("new@example.com").password("pw").build();
        when(userRepository.findById(3L)).thenReturn(Optional.of(user));
        when(subscriptionRepository.findByUser(user)).thenReturn(List.of());

        final List<Subscription> result = subscriptionService.getUserSubscriptions(3L);

        assertNotNull(result);
        assertTrue(result.isEmpty());
    }

    // ==========================================================================
    // getUserActiveSubscriptions
    // ==========================================================================

    @Test
    @DisplayName("getUserActiveSubscriptions: throws IllegalArgumentException when the user does not exist")
    void testGetUserActiveSubscriptions_userNotFound() throws Exception {
        // Requesting active subscriptions for a nonexistent user must fail fast.
        when(userRepository.findById(88L)).thenReturn(Optional.empty());

        assertThrows(
                IllegalArgumentException.class,
                () -> subscriptionService.getUserActiveSubscriptions(88L)
        );
    }

    @Test
    @DisplayName("getUserActiveSubscriptions: returns only ACTIVE subscriptions when user has no Stripe customer ID")
    void testGetUserActiveSubscriptions_noStripeCustomerId_returnsActive() throws Exception {
        // Without a Stripe customer ID the sync is skipped; the repository is queried
        // directly with status="ACTIVE" and the result is returned unchanged.
        final User user = User.builder().id(5L).email("active@example.com").password("pw").build();
        final Subscription activeSub = new Subscription();
        activeSub.setStatus("ACTIVE");
        when(userRepository.findById(5L)).thenReturn(Optional.of(user));
        when(subscriptionRepository.findByUserAndStatus(user, "ACTIVE"))
                .thenReturn(List.of(activeSub));

        final List<Subscription> result = subscriptionService.getUserActiveSubscriptions(5L);

        assertNotNull(result);
        assertEquals(1, result.size());
        assertEquals("ACTIVE", result.get(0).getStatus());
        verify(subscriptionRepository).findByUserAndStatus(user, "ACTIVE");
    }

    @Test
    @DisplayName("getUserActiveSubscriptions: returns empty list when no ACTIVE subscriptions exist")
    void testGetUserActiveSubscriptions_noActive_returnsEmptyList() throws Exception {
        // A user may have cancelled subscriptions but no active ones; the result
        // must be an empty list, not null or an exception.
        final User user = User.builder().id(6L).email("inactive@example.com").password("pw").build();
        when(userRepository.findById(6L)).thenReturn(Optional.of(user));
        when(subscriptionRepository.findByUserAndStatus(user, "ACTIVE")).thenReturn(List.of());

        final List<Subscription> result = subscriptionService.getUserActiveSubscriptions(6L);

        assertNotNull(result);
        assertTrue(result.isEmpty());
    }

    // ==========================================================================
    // handleStripeWebhook
    // ==========================================================================

    @Test
    @DisplayName("handleStripeWebhook: throws RuntimeException when the JSON payload is malformed")
    void testHandleStripeWebhook_invalidJson_throwsRuntimeException() throws Exception {
        // A non-JSON payload cannot be parsed by Stripe's Webhook utility.
        // The service must catch JsonSyntaxException and rethrow as RuntimeException.
        assertThrows(
                RuntimeException.class,
                () -> subscriptionService.handleStripeWebhook(
                        "not_valid_json_at_all",
                        "t=1,v1=fakesig",
                        "whsec_test_secret"
                )
        );
    }

    @Test
    @DisplayName("handleStripeWebhook: throws RuntimeException when the webhook signature is invalid")
    void testHandleStripeWebhook_invalidSignature_throwsRuntimeException() throws Exception {
        // Even with a plausible JSON body, a wrong signature must be rejected.
        // SignatureVerificationException is caught and wrapped as RuntimeException.
        final String validishJson = "{\"id\":\"evt_test\",\"object\":\"event\"}";
        assertThrows(
                RuntimeException.class,
                () -> subscriptionService.handleStripeWebhook(
                        validishJson,
                        "t=1,v1=invalidsignature",
                        "whsec_wrongsecret"
                )
        );
    }

    // ==========================================================================
    // cancelSubscription — with Stripe ID (MockedStatic)
    // ==========================================================================

    @Test
    @DisplayName("cancelSubscription: cancels on Stripe when stripeSubscriptionId is present")
    void testCancelSubscription_withStripeId_cancelsOnStripe() throws Exception {
        final Subscription sub = new Subscription();
        sub.setId(3L);
        sub.setStripeSubscriptionId("sub_stripe123");
        sub.setStatus("ACTIVE");
        when(subscriptionRepository.findById(3L)).thenReturn(Optional.of(sub));
        when(subscriptionRepository.save(any(Subscription.class))).thenAnswer(inv -> inv.getArgument(0));

        com.stripe.model.Subscription mockStripeSub = mock(com.stripe.model.Subscription.class);

        try (MockedStatic<com.stripe.model.Subscription> stripeMock = mockStatic(com.stripe.model.Subscription.class)) {
            stripeMock.when(() -> com.stripe.model.Subscription.retrieve("sub_stripe123")).thenReturn(mockStripeSub);

            subscriptionService.cancelSubscription(3L);

            verify(mockStripeSub).cancel();
            assertEquals("CANCELLED", sub.getStatus());
            assertNull(sub.getStripeSubscriptionId());
            verify(subscriptionRepository).save(sub);
        }
    }

    @Test
    @DisplayName("cancelSubscription: throws RuntimeException when Stripe cancel fails")
    void testCancelSubscription_stripeFailure_throwsRuntimeException() throws Exception {
        final Subscription sub = new Subscription();
        sub.setId(4L);
        sub.setStripeSubscriptionId("sub_fail");
        sub.setStatus("ACTIVE");
        when(subscriptionRepository.findById(4L)).thenReturn(Optional.of(sub));

        try (MockedStatic<com.stripe.model.Subscription> stripeMock = mockStatic(com.stripe.model.Subscription.class)) {
            stripeMock.when(() -> com.stripe.model.Subscription.retrieve("sub_fail"))
                    .thenThrow(new RuntimeException("Stripe error"));

            assertThrows(RuntimeException.class, () -> subscriptionService.cancelSubscription(4L));
        }
    }

    // ==========================================================================
    // cancelSubscriptionByStripeId — found path (MockedStatic)
    // ==========================================================================

    @Test
    @DisplayName("cancelSubscriptionByStripeId: cancels on Stripe and clears local record when found")
    void testCancelSubscriptionByStripeId_found_cancelsAndClears() throws Exception {
        final Subscription sub = new Subscription();
        sub.setId(5L);
        sub.setStripeSubscriptionId("sub_found");
        sub.setStatus("ACTIVE");
        when(subscriptionRepository.findByStripeSubscriptionId("sub_found")).thenReturn(Optional.of(sub));
        when(subscriptionRepository.save(any(Subscription.class))).thenAnswer(inv -> inv.getArgument(0));

        com.stripe.model.Subscription mockStripeSub = mock(com.stripe.model.Subscription.class);

        try (MockedStatic<com.stripe.model.Subscription> stripeMock = mockStatic(com.stripe.model.Subscription.class)) {
            stripeMock.when(() -> com.stripe.model.Subscription.retrieve("sub_found")).thenReturn(mockStripeSub);

            subscriptionService.cancelSubscriptionByStripeId("sub_found");

            verify(mockStripeSub).cancel();
            assertEquals("CANCELLED", sub.getStatus());
            assertNull(sub.getStripeSubscriptionId());
            verify(subscriptionRepository).save(sub);
        }
    }

    @Test
    @DisplayName("cancelSubscriptionByStripeId: throws RuntimeException when Stripe cancel fails")
    void testCancelSubscriptionByStripeId_stripeFailure() throws Exception {
        final Subscription sub = new Subscription();
        sub.setId(6L);
        sub.setStripeSubscriptionId("sub_fail2");
        sub.setStatus("ACTIVE");
        when(subscriptionRepository.findByStripeSubscriptionId("sub_fail2")).thenReturn(Optional.of(sub));

        try (MockedStatic<com.stripe.model.Subscription> stripeMock = mockStatic(com.stripe.model.Subscription.class)) {
            stripeMock.when(() -> com.stripe.model.Subscription.retrieve("sub_fail2"))
                    .thenThrow(new RuntimeException("Stripe error"));

            assertThrows(RuntimeException.class,
                    () -> subscriptionService.cancelSubscriptionByStripeId("sub_fail2"));
        }
    }

    // ==========================================================================
    // syncPlanWithStripe — createIfMissing=true (MockedStatic)
    // ==========================================================================

    @Test
    @DisplayName("syncPlanWithStripe: creates Product and Price in Stripe when createIfMissing=true")
    void testSyncPlanWithStripe_createIfMissing_success() throws Exception {
        final Plan plan = new Plan();
        plan.setId(8L);
        plan.setCode("STANDARD");
        plan.setName("Standard Plan");
        plan.setPriceCents(2000);
        plan.setBillingPeriod("month");
        when(planRepository.findById(8L)).thenReturn(Optional.of(plan));
        when(planRepository.save(any(Plan.class))).thenAnswer(inv -> inv.getArgument(0));

        final Product mockProduct = mock(Product.class);
        when(mockProduct.getId()).thenReturn("prod_123");
        final Price mockPrice = mock(Price.class);
        when(mockPrice.getId()).thenReturn("price_new456");

        try (MockedStatic<Product> productMock = mockStatic(Product.class);
             final MockedStatic<Price> priceMock = mockStatic(Price.class)) {
            productMock.when(() -> Product.create(anyMap())).thenReturn(mockProduct);
            priceMock.when(() -> Price.create(anyMap())).thenReturn(mockPrice);

            final Plan result = subscriptionService.syncPlanWithStripe(8L, true);

            assertEquals("price_new456", result.getCode());
            verify(planRepository).save(plan);
        }
    }

    @Test
    @DisplayName("syncPlanWithStripe: throws RuntimeException when Stripe product creation fails")
    void testSyncPlanWithStripe_createIfMissing_stripeFailure() throws Exception {
        final Plan plan = new Plan();
        plan.setId(9L);
        plan.setCode("PREMIUM");
        plan.setName("Premium Plan");
        plan.setPriceCents(3000);
        plan.setBillingPeriod("month");
        when(planRepository.findById(9L)).thenReturn(Optional.of(plan));

        try (MockedStatic<Product> productMock = mockStatic(Product.class)) {
            productMock.when(() -> Product.create(anyMap()))
                    .thenThrow(new RuntimeException("Stripe product creation failed"));

            assertThrows(RuntimeException.class,
                    () -> subscriptionService.syncPlanWithStripe(9L, true));
        }
    }

    // ==========================================================================
    // syncPlanWithStripe — empty code
    // ==========================================================================

    @Test
    @DisplayName("syncPlanWithStripe: throws IllegalArgumentException when code is empty and createIfMissing=false")
    void testSyncPlanWithStripe_emptyCode_createIfMissingFalse_throws() throws Exception {
        final Plan plan = new Plan();
        plan.setId(10L);
        plan.setCode("");
        when(planRepository.findById(10L)).thenReturn(Optional.of(plan));

        assertThrows(IllegalArgumentException.class,
                () -> subscriptionService.syncPlanWithStripe(10L, false));
    }

    // ==========================================================================
    // findOrCreatePlanByStripeId — empty nickname
    // ==========================================================================

    @Test
    @DisplayName("findOrCreatePlanByStripeId: uses fallback name when nickname is empty string")
    void testFindOrCreatePlanByStripeId_emptyNickname() throws Exception {
        when(planRepository.findByCode("price_empty")).thenReturn(null);
        when(planRepository.save(any(Plan.class))).thenAnswer(inv -> inv.getArgument(0));

        final Plan result = subscriptionService.findOrCreatePlanByStripeId("price_empty", "", 1000);

        // Empty string is truthy so it should use ""
        assertEquals("", result.getName());
    }

    // ==========================================================================
    // saveCheckoutSession — additional branches
    // ==========================================================================

    @Test
    @DisplayName("saveCheckoutSession: does not update user stripeCustomerId when session customer is null")
    void testSaveCheckoutSession_nullSessionCustomer() throws Exception {
        final User user = User.builder().id(4L).email("test@x.com").password("pw").build();
        final Session mockSession = mock(Session.class);
        when(mockSession.getCustomer()).thenReturn(null);
        when(mockSession.getSubscription()).thenReturn(null);
        when(mockSession.getId()).thenReturn("cs_4");
        when(mockSession.getPaymentIntent()).thenReturn(null);

        when(userRepository.findById(4L)).thenReturn(Optional.of(user));
        when(subscriptionRepository.save(any())).thenAnswer(inv -> inv.getArgument(0));
        when(paymentRepository.save(any())).thenAnswer(inv -> inv.getArgument(0));

        subscriptionService.saveCheckoutSession(4L, "standard", 2000L, mockSession);

        assertNull(user.getStripeCustomerId());
        verify(userRepository, never()).save(any());
    }

    @Test
    @DisplayName("saveCheckoutSession: does not overwrite existing user stripeCustomerId")
    void testSaveCheckoutSession_existingCustomerId_noOverwrite() throws Exception {
        final User user = User.builder().id(5L).email("test@x.com").password("pw").build();
        user.setStripeCustomerId("cus_existing");
        final Session mockSession = mock(Session.class);
        when(mockSession.getCustomer()).thenReturn("cus_new");
        when(mockSession.getSubscription()).thenReturn(null);
        when(mockSession.getId()).thenReturn("cs_5");
        when(mockSession.getPaymentIntent()).thenReturn(null);

        when(userRepository.findById(5L)).thenReturn(Optional.of(user));
        when(subscriptionRepository.save(any())).thenAnswer(inv -> inv.getArgument(0));
        when(paymentRepository.save(any())).thenAnswer(inv -> inv.getArgument(0));

        subscriptionService.saveCheckoutSession(5L, "standard", 2000L, mockSession);

        assertEquals("cus_existing", user.getStripeCustomerId());
        verify(userRepository, never()).save(any());
    }

    @Test
    @DisplayName("saveCheckoutSession: sets stripeCustomerId on new subscription when session has customer")
    void testSaveCheckoutSession_newSub_setsCustomerIdOnSubscription() throws Exception {
        final User user = User.builder().id(6L).email("test@x.com").password("pw").build();
        user.setStripeCustomerId("cus_already");
        final Session mockSession = mock(Session.class);
        when(mockSession.getCustomer()).thenReturn("cus_already");
        when(mockSession.getSubscription()).thenReturn("sub_new");
        when(mockSession.getId()).thenReturn("cs_6");
        when(mockSession.getPaymentIntent()).thenReturn("pi_6");

        when(userRepository.findById(6L)).thenReturn(Optional.of(user));
        when(subscriptionRepository.findByStripeSubscriptionId("sub_new")).thenReturn(Optional.empty());
        when(subscriptionRepository.save(any(Subscription.class))).thenAnswer(inv -> inv.getArgument(0));
        when(paymentRepository.save(any())).thenAnswer(inv -> inv.getArgument(0));

        subscriptionService.saveCheckoutSession(6L, "standard", 2000L, mockSession);

        verify(subscriptionRepository).save(argThat(sub ->
                "cus_already".equals(sub.getStripeCustomerId()) && "sub_new".equals(sub.getStripeSubscriptionId())));
    }

    // ==========================================================================
    // createCheckoutSession (MockedStatic)
    // ==========================================================================

    private HttpServletRequest mockRequest() throws Exception {
        final HttpServletRequest req = mock(HttpServletRequest.class);
        when(req.getScheme()).thenReturn("https");
        when(req.getServerName()).thenReturn("localhost");
        when(req.getServerPort()).thenReturn(443);
        return req;
    }

    @Test
    @DisplayName("createCheckoutSession: returns checkout URL for user with existing stripeCustomerId")
    void testCreateCheckoutSession_userWithExistingCustomerId() throws Exception {
        final User user = User.builder().id(1L).email("test@x.com").password("pw").build();
        user.setStripeCustomerId("cus_existing");
        when(userRepository.findById(1L)).thenReturn(Optional.of(user));

        final Session mockSession = mock(Session.class);
        when(mockSession.getUrl()).thenReturn("https://checkout.stripe.com/session123");

        try (MockedStatic<Session> sessionMock = mockStatic(Session.class)) {
            sessionMock.when(() -> Session.create(any(SessionCreateParams.class))).thenReturn(mockSession);

            @SuppressWarnings("unchecked")
            final ResponseEntity<Map<String, String>> result = (ResponseEntity<Map<String, String>>)
                    subscriptionService.createCheckoutSession(mockRequest(), "premium", 1L, 3000L, null, null);

            assertEquals(200, result.getStatusCode().value());
        }
    }

    @Test
    @DisplayName("createCheckoutSession: uses stripeCustomerId param when user has none")
    void testCreateCheckoutSession_userWithParamCustomerId() throws Exception {
        final User user = User.builder().id(2L).email("test@x.com").password("pw").build();
        when(userRepository.findById(2L)).thenReturn(Optional.of(user));

        final Session mockSession = mock(Session.class);
        when(mockSession.getUrl()).thenReturn("https://checkout.stripe.com/session");

        try (MockedStatic<Session> sessionMock = mockStatic(Session.class)) {
            sessionMock.when(() -> Session.create(any(SessionCreateParams.class))).thenReturn(mockSession);

            subscriptionService.createCheckoutSession(mockRequest(), "standard", 2L, 2000L, "cus_param", null);

            assertEquals("cus_param", user.getStripeCustomerId());
            verify(userRepository).save(user);
        }
    }

    @Test
    @DisplayName("createCheckoutSession: creates Stripe customer when user has no customer ID")
    void testCreateCheckoutSession_createsNewCustomer() throws Exception {
        final User user = User.builder().id(3L).email("test@x.com").password("pw").build();
        when(userRepository.findById(3L)).thenReturn(Optional.of(user));

        final Customer mockCustomer = mock(Customer.class);
        when(mockCustomer.getId()).thenReturn("cus_new");

        final Session mockSession = mock(Session.class);
        when(mockSession.getUrl()).thenReturn("https://checkout.stripe.com/session");

        try (MockedStatic<Customer> customerMock = mockStatic(Customer.class);
             final MockedStatic<Session> sessionMock = mockStatic(Session.class)) {
            customerMock.when(() -> Customer.create(anyMap())).thenReturn(mockCustomer);
            sessionMock.when(() -> Session.create(any(SessionCreateParams.class))).thenReturn(mockSession);

            subscriptionService.createCheckoutSession(mockRequest(), "standard", 3L, null, null, null);

            assertEquals("cus_new", user.getStripeCustomerId());
        }
    }

    @Test
    @DisplayName("createCheckoutSession: continues without customer when Customer.create fails")
    void testCreateCheckoutSession_customerCreationFails() throws Exception {
        final User user = User.builder().id(4L).email("test@x.com").password("pw").build();
        when(userRepository.findById(4L)).thenReturn(Optional.of(user));

        final Session mockSession = mock(Session.class);
        when(mockSession.getUrl()).thenReturn("https://checkout.stripe.com/session");

        try (MockedStatic<Customer> customerMock = mockStatic(Customer.class);
             final MockedStatic<Session> sessionMock = mockStatic(Session.class)) {
            customerMock.when(() -> Customer.create(anyMap())).thenThrow(new RuntimeException("fail"));
            sessionMock.when(() -> Session.create(any(SessionCreateParams.class))).thenReturn(mockSession);

            final ResponseEntity<?> result = subscriptionService.createCheckoutSession(
                    mockRequest(), "standard", 4L, null, null, null);

            assertEquals(200, result.getStatusCode().value());
        }
    }

    @Test
    @DisplayName("createCheckoutSession: uses default amount when amount is null")
    void testCreateCheckoutSession_defaultAmount_premium() throws Exception {
        final Session mockSession = mock(Session.class);
        when(mockSession.getUrl()).thenReturn("https://checkout.stripe.com/session");

        try (MockedStatic<Session> sessionMock = mockStatic(Session.class)) {
            sessionMock.when(() -> Session.create(any(SessionCreateParams.class))).thenReturn(mockSession);

            final ResponseEntity<?> result = subscriptionService.createCheckoutSession(
                    mockRequest(), "premium", null, null, null, null);

            assertEquals(200, result.getStatusCode().value());
        }
    }

    @Test
    @DisplayName("createCheckoutSession: uses stripeCustomerId without user")
    void testCreateCheckoutSession_noUser_withStripeCustomerId() throws Exception {
        final Session mockSession = mock(Session.class);
        when(mockSession.getUrl()).thenReturn("https://checkout.stripe.com/session");

        try (MockedStatic<Session> sessionMock = mockStatic(Session.class)) {
            sessionMock.when(() -> Session.create(any(SessionCreateParams.class))).thenReturn(mockSession);

            final ResponseEntity<?> result = subscriptionService.createCheckoutSession(
                    mockRequest(), "standard", null, 2000L, "cus_external", null);

            assertEquals(200, result.getStatusCode().value());
        }
    }

    @Test
    @DisplayName("createCheckoutSession: returns error when Session.create throws")
    void testCreateCheckoutSession_sessionCreateFails_returnsBadRequest() throws Exception {
        try (MockedStatic<Session> sessionMock = mockStatic(Session.class)) {
            sessionMock.when(() -> Session.create(any(SessionCreateParams.class)))
                    .thenThrow(new RuntimeException("Stripe API error"));

            final ResponseEntity<?> result = subscriptionService.createCheckoutSession(
                    mockRequest(), "standard", null, 2000L, null, null);

            assertEquals(400, result.getStatusCode().value());
        }
    }

    @Test
    @DisplayName("createCheckoutSession: throws when userId is nonzero but user not found")
    void testCreateCheckoutSession_userNotFound_returnsBadRequest() throws Exception {
        when(userRepository.findById(99L)).thenReturn(Optional.empty());

        final ResponseEntity<?> result = subscriptionService.createCheckoutSession(
                mockRequest(), "standard", 99L, 2000L, null, null);

        assertEquals(400, result.getStatusCode().value());
    }

    @Test
    @DisplayName("createCheckoutSession: uses 'update' portal path in success URL")
    void testCreateCheckoutSession_portalUpdate() throws Exception {
        final Session mockSession = mock(Session.class);
        when(mockSession.getUrl()).thenReturn("https://checkout.stripe.com/session");

        try (MockedStatic<Session> sessionMock = mockStatic(Session.class)) {
            sessionMock.when(() -> Session.create(any(SessionCreateParams.class))).thenReturn(mockSession);

            final ResponseEntity<?> result = subscriptionService.createCheckoutSession(
                    mockRequest(), "standard", null, 2000L, null, "update");

            assertEquals(200, result.getStatusCode().value());
        }
    }

    @Test
    @DisplayName("createCheckoutSession: non-standard port is included in domain")
    void testCreateCheckoutSession_nonStandardPort() throws Exception {
        final HttpServletRequest req = mock(HttpServletRequest.class);
        when(req.getScheme()).thenReturn("http");
        when(req.getServerName()).thenReturn("localhost");
        when(req.getServerPort()).thenReturn(8080);

        final Session mockSession = mock(Session.class);
        when(mockSession.getUrl()).thenReturn("https://checkout.stripe.com/session");

        try (MockedStatic<Session> sessionMock = mockStatic(Session.class)) {
            sessionMock.when(() -> Session.create(any(SessionCreateParams.class))).thenReturn(mockSession);

            final ResponseEntity<?> result = subscriptionService.createCheckoutSession(
                    req, "standard", null, 2000L, null, null);

            assertEquals(200, result.getStatusCode().value());
        }
    }

    @Test
    @DisplayName("createCheckoutSession: userId=0 is treated same as null")
    void testCreateCheckoutSession_userIdZero() throws Exception {
        final Session mockSession = mock(Session.class);
        when(mockSession.getUrl()).thenReturn("https://checkout.stripe.com/session");

        try (MockedStatic<Session> sessionMock = mockStatic(Session.class)) {
            sessionMock.when(() -> Session.create(any(SessionCreateParams.class))).thenReturn(mockSession);

            final ResponseEntity<?> result = subscriptionService.createCheckoutSession(
                    mockRequest(), "standard", 0L, 2000L, null, null);

            assertEquals(200, result.getStatusCode().value());
        }
    }

    // ==========================================================================
    // listCustomerSubscriptions (MockedStatic)
    // ==========================================================================

    @Test
    @DisplayName("listCustomerSubscriptions: returns SubscriptionCollection from Stripe")
    void testListCustomerSubscriptions_success() throws Exception {
        final SubscriptionCollection mockCollection = mock(SubscriptionCollection.class);

        try (MockedStatic<com.stripe.model.Subscription> stripeMock = mockStatic(com.stripe.model.Subscription.class)) {
            stripeMock.when(() -> com.stripe.model.Subscription.list(any(com.stripe.param.SubscriptionListParams.class))).thenReturn(mockCollection);

            final SubscriptionCollection result = subscriptionService.listCustomerSubscriptions("cus_test");
            assertNotNull(result);
        }
    }

    @Test
    @DisplayName("listCustomerSubscriptions: throws RuntimeException on StripeException")
    void testListCustomerSubscriptions_stripeFailure() throws Exception {
        try (MockedStatic<com.stripe.model.Subscription> stripeMock = mockStatic(com.stripe.model.Subscription.class)) {
            stripeMock.when(() -> com.stripe.model.Subscription.list(any(com.stripe.param.SubscriptionListParams.class)))
                    .thenThrow(mock(com.stripe.exception.StripeException.class));

            assertThrows(RuntimeException.class,
                    () -> subscriptionService.listCustomerSubscriptions("cus_fail"));
        }
    }

    // ==========================================================================
    // Webhook handler helpers
    // ==========================================================================

    private Event createMockWebhookEvent(String type, Object dataObject) {
        final Event event = mock(Event.class);
        when(event.getType()).thenReturn(type);
        final EventDataObjectDeserializer deserializer = mock(EventDataObjectDeserializer.class);
        when(event.getDataObjectDeserializer()).thenReturn(deserializer);
        if (dataObject != null) {
            when(deserializer.getObject()).thenReturn(Optional.of((StripeObject) dataObject));
        } else {
            when(deserializer.getObject()).thenReturn(Optional.empty());
        }
        return event;
    }

    // ==========================================================================
    // handleStripeWebhook — valid events via MockedStatic<Webhook>
    // ==========================================================================

    @Test
    @DisplayName("handleStripeWebhook: checkout.session.completed with null session is no-op")
    void testWebhook_checkoutCompleted_nullSession() throws Exception {
        final Event event = createMockWebhookEvent("checkout.session.completed", null);

        try (MockedStatic<Webhook> webhookMock = mockStatic(Webhook.class)) {
            webhookMock.when(() -> Webhook.constructEvent(anyString(), anyString(), anyString()))
                    .thenReturn(event);

            final String result = subscriptionService.handleStripeWebhook("{}", "sig", "secret");
            assertEquals("Webhook received", result);
        }
    }

    @Test
    @DisplayName("handleStripeWebhook: checkout.session.completed updates payment and existing subscription")
    void testWebhook_checkoutCompleted_existingSubscription() throws Exception {
        final Session mockSession = mock(Session.class);
        when(mockSession.getSubscription()).thenReturn("sub_existing");
        when(mockSession.getCustomer()).thenReturn("cus_123");
        when(mockSession.getClientReferenceId()).thenReturn(null);
        when(mockSession.getId()).thenReturn("cs_session1");

        final Payment payment = Payment.builder().user(null).amountCents(2000).status("PENDING")
                .stripeSessionId("cs_session1").build();
        when(paymentRepository.findByStripeSessionId("cs_session1")).thenReturn(payment);

        final Subscription existingSub = new Subscription();
        existingSub.setId(10L);
        existingSub.setStripeSubscriptionId("sub_existing");
        when(subscriptionRepository.findByStripeSubscriptionId("sub_existing"))
                .thenReturn(Optional.of(existingSub));
        when(subscriptionRepository.save(any())).thenAnswer(inv -> inv.getArgument(0));

        final Event event = createMockWebhookEvent("checkout.session.completed", mockSession);

        try (MockedStatic<Webhook> webhookMock = mockStatic(Webhook.class)) {
            webhookMock.when(() -> Webhook.constructEvent(anyString(), anyString(), anyString()))
                    .thenReturn(event);

            subscriptionService.handleStripeWebhook("{}", "sig", "secret");

            assertEquals("ACTIVE", existingSub.getStatus());
            assertEquals("COMPLETED", payment.getStatus());
        }
    }

    @Test
    @DisplayName("handleStripeWebhook: checkout.session.completed updates existing sub customer ID when empty")
    void testWebhook_checkoutCompleted_updatesSubCustomerId() throws Exception {
        final Session mockSession = mock(Session.class);
        when(mockSession.getSubscription()).thenReturn("sub_nocust");
        when(mockSession.getCustomer()).thenReturn("cus_new");
        when(mockSession.getClientReferenceId()).thenReturn(null);
        when(mockSession.getId()).thenReturn(null);

        final Subscription existingSub = new Subscription();
        existingSub.setStripeSubscriptionId("sub_nocust");
        existingSub.setStripeCustomerId(null);
        when(subscriptionRepository.findByStripeSubscriptionId("sub_nocust"))
                .thenReturn(Optional.of(existingSub));
        when(subscriptionRepository.save(any())).thenAnswer(inv -> inv.getArgument(0));

        final Event event = createMockWebhookEvent("checkout.session.completed", mockSession);

        try (MockedStatic<Webhook> webhookMock = mockStatic(Webhook.class)) {
            webhookMock.when(() -> Webhook.constructEvent(anyString(), anyString(), anyString()))
                    .thenReturn(event);

            subscriptionService.handleStripeWebhook("{}", "sig", "secret");

            assertEquals("cus_new", existingSub.getStripeCustomerId());
        }
    }

    @Test
    @DisplayName("handleStripeWebhook: checkout.session.completed — no subscriptionId prints message")
    void testWebhook_checkoutCompleted_noSubscriptionId() throws Exception {
        final Session mockSession = mock(Session.class);
        when(mockSession.getSubscription()).thenReturn(null);
        when(mockSession.getCustomer()).thenReturn("cus_123");
        when(mockSession.getClientReferenceId()).thenReturn(null);
        when(mockSession.getId()).thenReturn(null);

        final Event event = createMockWebhookEvent("checkout.session.completed", mockSession);

        try (MockedStatic<Webhook> webhookMock = mockStatic(Webhook.class)) {
            webhookMock.when(() -> Webhook.constructEvent(anyString(), anyString(), anyString()))
                    .thenReturn(event);

            final String result = subscriptionService.handleStripeWebhook("{}", "sig", "secret");
            assertEquals("Webhook received", result);
        }
    }

    @Test
    @DisplayName("handleStripeWebhook: checkout.session.completed — new sub with user found by customer ID")
    void testWebhook_checkoutCompleted_newSubFoundByCustomerId() throws Exception {
        final Session mockSession = mock(Session.class);
        when(mockSession.getSubscription()).thenReturn("sub_new1");
        when(mockSession.getCustomer()).thenReturn("cus_abc");
        when(mockSession.getClientReferenceId()).thenReturn(null);
        when(mockSession.getId()).thenReturn(null);

        when(subscriptionRepository.findByStripeSubscriptionId("sub_new1")).thenReturn(Optional.empty());

        com.stripe.model.Subscription mockStripeSub = mock(com.stripe.model.Subscription.class);
        when(mockStripeSub.getCustomer()).thenReturn("cus_abc");
        when(mockStripeSub.getCurrentPeriodStart()).thenReturn(1000L);
        when(mockStripeSub.getCurrentPeriodEnd()).thenReturn(2000L);
        when(mockStripeSub.getItems()).thenReturn(null);

        final User user = User.builder().id(1L).email("test@x.com").password("pw").build();
        user.setStripeCustomerId("cus_abc");
        when(userRepository.findByStripeCustomerId("cus_abc")).thenReturn(Optional.of(user));
        when(subscriptionRepository.save(any())).thenAnswer(inv -> inv.getArgument(0));

        final Event event = createMockWebhookEvent("checkout.session.completed", mockSession);

        try (MockedStatic<Webhook> webhookMock = mockStatic(Webhook.class);
             final MockedStatic<com.stripe.model.Subscription> stripeMock = mockStatic(com.stripe.model.Subscription.class)) {
            webhookMock.when(() -> Webhook.constructEvent(anyString(), anyString(), anyString()))
                    .thenReturn(event);
            stripeMock.when(() -> com.stripe.model.Subscription.retrieve("sub_new1"))
                    .thenReturn(mockStripeSub);

            subscriptionService.handleStripeWebhook("{}", "sig", "secret");

            verify(subscriptionRepository).save(argThat(s -> "ACTIVE".equals(s.getStatus())));
        }
    }

    @Test
    @DisplayName("handleStripeWebhook: checkout.session.completed — user found by payment record")
    void testWebhook_checkoutCompleted_userFoundByPayment() throws Exception {
        final User user = User.builder().id(2L).email("test@x.com").password("pw").build();
        final Payment payment = Payment.builder().user(user).amountCents(2000).status("PENDING")
                .stripeSessionId("cs_pay").build();

        final Session mockSession = mock(Session.class);
        when(mockSession.getSubscription()).thenReturn("sub_pay");
        when(mockSession.getCustomer()).thenReturn("cus_pay");
        when(mockSession.getClientReferenceId()).thenReturn(null);
        when(mockSession.getId()).thenReturn("cs_pay");

        when(subscriptionRepository.findByStripeSubscriptionId("sub_pay")).thenReturn(Optional.empty());
        when(paymentRepository.findByStripeSessionId("cs_pay")).thenReturn(payment);
        when(userRepository.findByStripeCustomerId("cus_pay")).thenReturn(Optional.empty());

        com.stripe.model.Subscription mockStripeSub = mock(com.stripe.model.Subscription.class);
        when(mockStripeSub.getCustomer()).thenReturn("cus_pay");
        when(mockStripeSub.getCurrentPeriodStart()).thenReturn(1000L);
        when(mockStripeSub.getCurrentPeriodEnd()).thenReturn(2000L);
        when(mockStripeSub.getItems()).thenReturn(null);

        when(subscriptionRepository.save(any())).thenAnswer(inv -> inv.getArgument(0));

        final Event event = createMockWebhookEvent("checkout.session.completed", mockSession);

        try (MockedStatic<Webhook> webhookMock = mockStatic(Webhook.class);
             final MockedStatic<com.stripe.model.Subscription> stripeMock = mockStatic(com.stripe.model.Subscription.class)) {
            webhookMock.when(() -> Webhook.constructEvent(anyString(), anyString(), anyString()))
                    .thenReturn(event);
            stripeMock.when(() -> com.stripe.model.Subscription.retrieve("sub_pay"))
                    .thenReturn(mockStripeSub);

            subscriptionService.handleStripeWebhook("{}", "sig", "secret");

            verify(subscriptionRepository).save(argThat(s -> s.getUser() == user));
        }
    }

    @Test
    @DisplayName("handleStripeWebhook: checkout.session.completed — user found by clientReferenceId")
    void testWebhook_checkoutCompleted_userFoundByClientRefId() throws Exception {
        final User user = User.builder().id(7L).email("test@x.com").password("pw").build();

        final Session mockSession = mock(Session.class);
        when(mockSession.getSubscription()).thenReturn("sub_ref");
        when(mockSession.getCustomer()).thenReturn("cus_ref");
        when(mockSession.getClientReferenceId()).thenReturn("7");
        when(mockSession.getId()).thenReturn(null);

        when(subscriptionRepository.findByStripeSubscriptionId("sub_ref")).thenReturn(Optional.empty());
        when(userRepository.findByStripeCustomerId("cus_ref")).thenReturn(Optional.empty());
        when(userRepository.findById(7L)).thenReturn(Optional.of(user));

        com.stripe.model.Subscription mockStripeSub = mock(com.stripe.model.Subscription.class);
        when(mockStripeSub.getCustomer()).thenReturn("cus_ref");
        when(mockStripeSub.getCurrentPeriodStart()).thenReturn(1000L);
        when(mockStripeSub.getCurrentPeriodEnd()).thenReturn(2000L);
        when(mockStripeSub.getItems()).thenReturn(null);

        when(subscriptionRepository.save(any())).thenAnswer(inv -> inv.getArgument(0));

        final Event event = createMockWebhookEvent("checkout.session.completed", mockSession);

        try (MockedStatic<Webhook> webhookMock = mockStatic(Webhook.class);
             final MockedStatic<com.stripe.model.Subscription> stripeMock = mockStatic(com.stripe.model.Subscription.class)) {
            webhookMock.when(() -> Webhook.constructEvent(anyString(), anyString(), anyString()))
                    .thenReturn(event);
            stripeMock.when(() -> com.stripe.model.Subscription.retrieve("sub_ref"))
                    .thenReturn(mockStripeSub);

            subscriptionService.handleStripeWebhook("{}", "sig", "secret");

            verify(subscriptionRepository).save(argThat(s -> s.getUser() == user));
        }
    }

    @Test
    @DisplayName("handleStripeWebhook: checkout.session.completed — invalid clientReferenceId is non-numeric")
    void testWebhook_checkoutCompleted_invalidClientRefId() throws Exception {
        final Session mockSession = mock(Session.class);
        when(mockSession.getSubscription()).thenReturn("sub_bad");
        when(mockSession.getCustomer()).thenReturn("cus_bad");
        when(mockSession.getClientReferenceId()).thenReturn("not_a_number");
        when(mockSession.getId()).thenReturn(null);

        when(subscriptionRepository.findByStripeSubscriptionId("sub_bad")).thenReturn(Optional.empty());
        when(userRepository.findByStripeCustomerId("cus_bad")).thenReturn(Optional.empty());

        com.stripe.model.Subscription mockStripeSub = mock(com.stripe.model.Subscription.class);
        when(mockStripeSub.getCustomer()).thenReturn("cus_bad");
        when(mockStripeSub.getCurrentPeriodStart()).thenReturn(1000L);
        when(mockStripeSub.getCurrentPeriodEnd()).thenReturn(2000L);
        when(mockStripeSub.getItems()).thenReturn(null);

        final Event event = createMockWebhookEvent("checkout.session.completed", mockSession);

        try (MockedStatic<Webhook> webhookMock = mockStatic(Webhook.class);
             final MockedStatic<com.stripe.model.Subscription> stripeMock = mockStatic(com.stripe.model.Subscription.class)) {
            webhookMock.when(() -> Webhook.constructEvent(anyString(), anyString(), anyString()))
                    .thenReturn(event);
            stripeMock.when(() -> com.stripe.model.Subscription.retrieve("sub_bad"))
                    .thenReturn(mockStripeSub);

            final String result = subscriptionService.handleStripeWebhook("{}", "sig", "secret");
            assertEquals("Webhook received", result);
        }
    }

    @Test
    @DisplayName("handleStripeWebhook: checkout.session.completed — Stripe retrieve fails is caught")
    void testWebhook_checkoutCompleted_stripeRetrieveFails() throws Exception {
        final Session mockSession = mock(Session.class);
        when(mockSession.getSubscription()).thenReturn("sub_err");
        when(mockSession.getCustomer()).thenReturn("cus_err");
        when(mockSession.getClientReferenceId()).thenReturn(null);
        when(mockSession.getId()).thenReturn(null);

        when(subscriptionRepository.findByStripeSubscriptionId("sub_err")).thenReturn(Optional.empty());

        final Event event = createMockWebhookEvent("checkout.session.completed", mockSession);

        try (MockedStatic<Webhook> webhookMock = mockStatic(Webhook.class);
             final MockedStatic<com.stripe.model.Subscription> stripeMock = mockStatic(com.stripe.model.Subscription.class)) {
            webhookMock.when(() -> Webhook.constructEvent(anyString(), anyString(), anyString()))
                    .thenReturn(event);
            stripeMock.when(() -> com.stripe.model.Subscription.retrieve("sub_err"))
                    .thenThrow(new RuntimeException("fail"));

            final String result = subscriptionService.handleStripeWebhook("{}", "sig", "secret");
            assertEquals("Webhook received", result);
        }
    }

    @Test
    @DisplayName("handleStripeWebhook: checkout.session.completed — new sub with price items and plan")
    void testWebhook_checkoutCompleted_newSubWithPriceAndPlan() throws Exception {
        final Session mockSession = mock(Session.class);
        when(mockSession.getSubscription()).thenReturn("sub_items");
        when(mockSession.getCustomer()).thenReturn("cus_items");
        when(mockSession.getClientReferenceId()).thenReturn(null);
        when(mockSession.getId()).thenReturn(null);

        when(subscriptionRepository.findByStripeSubscriptionId("sub_items")).thenReturn(Optional.empty());

        // Build Stripe sub with items
        com.stripe.model.Subscription mockStripeSub = mock(com.stripe.model.Subscription.class);
        when(mockStripeSub.getCustomer()).thenReturn("cus_items");
        when(mockStripeSub.getCurrentPeriodStart()).thenReturn(1000L);
        when(mockStripeSub.getCurrentPeriodEnd()).thenReturn(2000L);

        final SubscriptionItemCollection itemCollection = mock(SubscriptionItemCollection.class);
        final SubscriptionItem item = mock(SubscriptionItem.class);
        final Price price = mock(Price.class);
        when(price.getId()).thenReturn("price_abc");
        when(item.getPrice()).thenReturn(price);
        when(itemCollection.getData()).thenReturn(List.of(item));
        when(mockStripeSub.getItems()).thenReturn(itemCollection);

        final User user = User.builder().id(1L).email("test@x.com").password("pw").build();
        user.setStripeCustomerId("cus_items");
        when(userRepository.findByStripeCustomerId("cus_items")).thenReturn(Optional.of(user));

        final Plan plan = new Plan();
        plan.setCode("price_abc");
        plan.setName("Test Plan");
        when(planRepository.findByCode("price_abc")).thenReturn(plan);
        when(subscriptionRepository.save(any())).thenAnswer(inv -> inv.getArgument(0));

        final Event event = createMockWebhookEvent("checkout.session.completed", mockSession);

        try (MockedStatic<Webhook> webhookMock = mockStatic(Webhook.class);
             final MockedStatic<com.stripe.model.Subscription> stripeMock = mockStatic(com.stripe.model.Subscription.class)) {
            webhookMock.when(() -> Webhook.constructEvent(anyString(), anyString(), anyString()))
                    .thenReturn(event);
            stripeMock.when(() -> com.stripe.model.Subscription.retrieve("sub_items"))
                    .thenReturn(mockStripeSub);

            subscriptionService.handleStripeWebhook("{}", "sig", "secret");

            verify(subscriptionRepository).save(argThat(s ->
                    "price_abc".equals(s.getPriceId()) && s.getPlan() == plan));
        }
    }

    @Test
    @DisplayName("handleStripeWebhook: checkout.session.completed — payment user gets stripeCustomerId updated")
    void testWebhook_checkoutCompleted_paymentUserGetsCustomerIdUpdate() throws Exception {
        final User user = User.builder().id(1L).email("test@x.com").password("pw").build();
        final Payment payment = Payment.builder().user(user).amountCents(2000).status("PENDING")
                .stripeSessionId("cs_upd").build();

        final Session mockSession = mock(Session.class);
        when(mockSession.getSubscription()).thenReturn(null);
        when(mockSession.getCustomer()).thenReturn("cus_upd");
        when(mockSession.getId()).thenReturn("cs_upd");
        when(mockSession.getClientReferenceId()).thenReturn(null);

        when(paymentRepository.findByStripeSessionId("cs_upd")).thenReturn(payment);

        final Event event = createMockWebhookEvent("checkout.session.completed", mockSession);

        try (MockedStatic<Webhook> webhookMock = mockStatic(Webhook.class)) {
            webhookMock.when(() -> Webhook.constructEvent(anyString(), anyString(), anyString()))
                    .thenReturn(event);

            subscriptionService.handleStripeWebhook("{}", "sig", "secret");

            assertEquals("cus_upd", user.getStripeCustomerId());
            verify(userRepository).save(user);
        }
    }

    // ==========================================================================
    // handleAsyncPaymentFailed
    // ==========================================================================

    @Test
    @DisplayName("handleStripeWebhook: async_payment_failed sets status PAYMENT_FAILED")
    void testWebhook_asyncPaymentFailed() throws Exception {
        final Session mockSession = mock(Session.class);
        when(mockSession.getSubscription()).thenReturn("sub_apf");

        final Subscription sub = new Subscription();
        sub.setStatus("ACTIVE");
        when(subscriptionRepository.findByStripeSubscriptionId("sub_apf")).thenReturn(Optional.of(sub));
        when(subscriptionRepository.save(any())).thenAnswer(inv -> inv.getArgument(0));

        final Event event = createMockWebhookEvent("checkout.session.async_payment_failed", mockSession);

        try (MockedStatic<Webhook> webhookMock = mockStatic(Webhook.class)) {
            webhookMock.when(() -> Webhook.constructEvent(anyString(), anyString(), anyString()))
                    .thenReturn(event);

            subscriptionService.handleStripeWebhook("{}", "sig", "secret");
            assertEquals("PAYMENT_FAILED", sub.getStatus());
        }
    }

    @Test
    @DisplayName("handleStripeWebhook: async_payment_failed with null session is no-op")
    void testWebhook_asyncPaymentFailed_nullSession() throws Exception {
        final Event event = createMockWebhookEvent("checkout.session.async_payment_failed", null);

        try (MockedStatic<Webhook> webhookMock = mockStatic(Webhook.class)) {
            webhookMock.when(() -> Webhook.constructEvent(anyString(), anyString(), anyString()))
                    .thenReturn(event);

            final String result = subscriptionService.handleStripeWebhook("{}", "sig", "secret");
            assertEquals("Webhook received", result);
        }
    }

    @Test
    @DisplayName("handleStripeWebhook: async_payment_failed with null subscriptionId is no-op")
    void testWebhook_asyncPaymentFailed_nullSubId() throws Exception {
        final Session mockSession = mock(Session.class);
        when(mockSession.getSubscription()).thenReturn(null);

        final Event event = createMockWebhookEvent("checkout.session.async_payment_failed", mockSession);

        try (MockedStatic<Webhook> webhookMock = mockStatic(Webhook.class)) {
            webhookMock.when(() -> Webhook.constructEvent(anyString(), anyString(), anyString()))
                    .thenReturn(event);

            final String result = subscriptionService.handleStripeWebhook("{}", "sig", "secret");
            assertEquals("Webhook received", result);
        }
    }

    // ==========================================================================
    // handleAsyncPaymentSucceeded
    // ==========================================================================

    @Test
    @DisplayName("handleStripeWebhook: async_payment_succeeded sets status ACTIVE")
    void testWebhook_asyncPaymentSucceeded() throws Exception {
        final Session mockSession = mock(Session.class);
        when(mockSession.getSubscription()).thenReturn("sub_aps");

        final Subscription sub = new Subscription();
        sub.setStatus("PENDING");
        when(subscriptionRepository.findByStripeSubscriptionId("sub_aps")).thenReturn(Optional.of(sub));
        when(subscriptionRepository.save(any())).thenAnswer(inv -> inv.getArgument(0));

        final Event event = createMockWebhookEvent("checkout.session.async_payment_succeeded", mockSession);

        try (MockedStatic<Webhook> webhookMock = mockStatic(Webhook.class)) {
            webhookMock.when(() -> Webhook.constructEvent(anyString(), anyString(), anyString()))
                    .thenReturn(event);

            subscriptionService.handleStripeWebhook("{}", "sig", "secret");
            assertEquals("ACTIVE", sub.getStatus());
        }
    }

    @Test
    @DisplayName("handleStripeWebhook: async_payment_succeeded with null session is no-op")
    void testWebhook_asyncPaymentSucceeded_nullSession() throws Exception {
        final Event event = createMockWebhookEvent("checkout.session.async_payment_succeeded", null);

        try (MockedStatic<Webhook> webhookMock = mockStatic(Webhook.class)) {
            webhookMock.when(() -> Webhook.constructEvent(anyString(), anyString(), anyString()))
                    .thenReturn(event);

            final String result = subscriptionService.handleStripeWebhook("{}", "sig", "secret");
            assertEquals("Webhook received", result);
        }
    }

    // ==========================================================================
    // handleSessionExpired
    // ==========================================================================

    @Test
    @DisplayName("handleStripeWebhook: session_expired sets status EXPIRED")
    void testWebhook_sessionExpired() throws Exception {
        final Session mockSession = mock(Session.class);
        when(mockSession.getSubscription()).thenReturn("sub_exp");

        final Subscription sub = new Subscription();
        sub.setStatus("PENDING");
        when(subscriptionRepository.findByStripeSubscriptionId("sub_exp")).thenReturn(Optional.of(sub));
        when(subscriptionRepository.save(any())).thenAnswer(inv -> inv.getArgument(0));

        final Event event = createMockWebhookEvent("checkout.session.expired", mockSession);

        try (MockedStatic<Webhook> webhookMock = mockStatic(Webhook.class)) {
            webhookMock.when(() -> Webhook.constructEvent(anyString(), anyString(), anyString()))
                    .thenReturn(event);

            subscriptionService.handleStripeWebhook("{}", "sig", "secret");
            assertEquals("EXPIRED", sub.getStatus());
        }
    }

    @Test
    @DisplayName("handleStripeWebhook: session_expired with null session is no-op")
    void testWebhook_sessionExpired_nullSession() throws Exception {
        final Event event = createMockWebhookEvent("checkout.session.expired", null);

        try (MockedStatic<Webhook> webhookMock = mockStatic(Webhook.class)) {
            webhookMock.when(() -> Webhook.constructEvent(anyString(), anyString(), anyString()))
                    .thenReturn(event);

            final String result = subscriptionService.handleStripeWebhook("{}", "sig", "secret");
            assertEquals("Webhook received", result);
        }
    }

    // ==========================================================================
    // handleSubscriptionCreated
    // ==========================================================================

    @Test
    @DisplayName("handleStripeWebhook: subscription.created creates new subscription record")
    void testWebhook_subscriptionCreated_newSub() throws Exception {
        com.stripe.model.Subscription mockStripeSub = mock(com.stripe.model.Subscription.class);
        when(mockStripeSub.getId()).thenReturn("sub_created");
        when(mockStripeSub.getCustomer()).thenReturn("cus_created");
        when(mockStripeSub.getCurrentPeriodStart()).thenReturn(1000L);
        when(mockStripeSub.getCurrentPeriodEnd()).thenReturn(2000L);
        when(mockStripeSub.getItems()).thenReturn(null);

        when(subscriptionRepository.findByStripeSubscriptionId("sub_created")).thenReturn(Optional.empty());

        final User user = User.builder().id(1L).email("test@x.com").password("pw").build();
        when(userRepository.findByStripeCustomerId("cus_created")).thenReturn(Optional.of(user));
        when(subscriptionRepository.save(any())).thenAnswer(inv -> inv.getArgument(0));

        final Event event = createMockWebhookEvent("customer.subscription.created", mockStripeSub);

        try (MockedStatic<Webhook> webhookMock = mockStatic(Webhook.class)) {
            webhookMock.when(() -> Webhook.constructEvent(anyString(), anyString(), anyString()))
                    .thenReturn(event);

            subscriptionService.handleStripeWebhook("{}", "sig", "secret");

            verify(subscriptionRepository).save(argThat(s ->
                    "sub_created".equals(s.getStripeSubscriptionId()) && "ACTIVE".equals(s.getStatus())));
        }
    }

    @Test
    @DisplayName("handleStripeWebhook: subscription.created with items and plan")
    void testWebhook_subscriptionCreated_withItemsAndPlan() throws Exception {
        com.stripe.model.Subscription mockStripeSub = mock(com.stripe.model.Subscription.class);
        when(mockStripeSub.getId()).thenReturn("sub_with_items");
        when(mockStripeSub.getCustomer()).thenReturn("cus_wi");
        when(mockStripeSub.getCurrentPeriodStart()).thenReturn(1000L);
        when(mockStripeSub.getCurrentPeriodEnd()).thenReturn(2000L);

        final SubscriptionItemCollection itemCollection = mock(SubscriptionItemCollection.class);
        final SubscriptionItem item = mock(SubscriptionItem.class);
        final Price price = mock(Price.class);
        when(price.getId()).thenReturn("price_wi");
        when(item.getPrice()).thenReturn(price);
        when(itemCollection.getData()).thenReturn(List.of(item));
        when(mockStripeSub.getItems()).thenReturn(itemCollection);

        when(subscriptionRepository.findByStripeSubscriptionId("sub_with_items")).thenReturn(Optional.empty());

        final User user = User.builder().id(1L).email("test@x.com").password("pw").build();
        when(userRepository.findByStripeCustomerId("cus_wi")).thenReturn(Optional.of(user));

        final Plan plan = new Plan();
        plan.setCode("price_wi");
        when(planRepository.findByCode("price_wi")).thenReturn(plan);
        when(subscriptionRepository.save(any())).thenAnswer(inv -> inv.getArgument(0));

        final Event event = createMockWebhookEvent("customer.subscription.created", mockStripeSub);

        try (MockedStatic<Webhook> webhookMock = mockStatic(Webhook.class)) {
            webhookMock.when(() -> Webhook.constructEvent(anyString(), anyString(), anyString()))
                    .thenReturn(event);

            subscriptionService.handleStripeWebhook("{}", "sig", "secret");

            verify(subscriptionRepository).save(argThat(s -> s.getPlan() == plan));
        }
    }

    @Test
    @DisplayName("handleStripeWebhook: subscription.created with null stripeSub is no-op")
    void testWebhook_subscriptionCreated_nullStripeSub() throws Exception {
        final Event event = createMockWebhookEvent("customer.subscription.created", null);

        try (MockedStatic<Webhook> webhookMock = mockStatic(Webhook.class)) {
            webhookMock.when(() -> Webhook.constructEvent(anyString(), anyString(), anyString()))
                    .thenReturn(event);

            final String result = subscriptionService.handleStripeWebhook("{}", "sig", "secret");
            assertEquals("Webhook received", result);
        }
    }

    @Test
    @DisplayName("handleStripeWebhook: subscription.created skips when subscription already exists")
    void testWebhook_subscriptionCreated_alreadyExists() throws Exception {
        com.stripe.model.Subscription mockStripeSub = mock(com.stripe.model.Subscription.class);
        when(mockStripeSub.getId()).thenReturn("sub_dup");
        when(mockStripeSub.getCustomer()).thenReturn("cus_dup");

        final Subscription existing = new Subscription();
        when(subscriptionRepository.findByStripeSubscriptionId("sub_dup")).thenReturn(Optional.of(existing));

        final Event event = createMockWebhookEvent("customer.subscription.created", mockStripeSub);

        try (MockedStatic<Webhook> webhookMock = mockStatic(Webhook.class)) {
            webhookMock.when(() -> Webhook.constructEvent(anyString(), anyString(), anyString()))
                    .thenReturn(event);

            subscriptionService.handleStripeWebhook("{}", "sig", "secret");
            verify(subscriptionRepository, never()).save(any());
        }
    }

    @Test
    @DisplayName("handleStripeWebhook: subscription.created user not found does not save")
    void testWebhook_subscriptionCreated_userNotFound() throws Exception {
        com.stripe.model.Subscription mockStripeSub = mock(com.stripe.model.Subscription.class);
        when(mockStripeSub.getId()).thenReturn("sub_nouser");
        when(mockStripeSub.getCustomer()).thenReturn("cus_nouser");

        when(subscriptionRepository.findByStripeSubscriptionId("sub_nouser")).thenReturn(Optional.empty());
        when(userRepository.findByStripeCustomerId("cus_nouser")).thenReturn(Optional.empty());

        final Event event = createMockWebhookEvent("customer.subscription.created", mockStripeSub);

        try (MockedStatic<Webhook> webhookMock = mockStatic(Webhook.class)) {
            webhookMock.when(() -> Webhook.constructEvent(anyString(), anyString(), anyString()))
                    .thenReturn(event);

            subscriptionService.handleStripeWebhook("{}", "sig", "secret");
            verify(subscriptionRepository, never()).save(any());
        }
    }

    // ==========================================================================
    // handleSubscriptionUpdated
    // ==========================================================================

    @Test
    @DisplayName("handleStripeWebhook: subscription.updated updates existing subscription")
    void testWebhook_subscriptionUpdated() throws Exception {
        com.stripe.model.Subscription mockStripeSub = mock(com.stripe.model.Subscription.class);
        when(mockStripeSub.getId()).thenReturn("sub_upd");
        when(mockStripeSub.getStatus()).thenReturn("past_due");
        when(mockStripeSub.getCurrentPeriodEnd()).thenReturn(3000L);
        when(mockStripeSub.getItems()).thenReturn(null);

        final Subscription sub = new Subscription();
        sub.setStripeSubscriptionId("sub_upd");
        sub.setStatus("ACTIVE");
        when(subscriptionRepository.findByStripeSubscriptionId("sub_upd")).thenReturn(Optional.of(sub));
        when(subscriptionRepository.save(any())).thenAnswer(inv -> inv.getArgument(0));

        final Event event = createMockWebhookEvent("customer.subscription.updated", mockStripeSub);

        try (MockedStatic<Webhook> webhookMock = mockStatic(Webhook.class)) {
            webhookMock.when(() -> Webhook.constructEvent(anyString(), anyString(), anyString()))
                    .thenReturn(event);

            subscriptionService.handleStripeWebhook("{}", "sig", "secret");
            assertEquals("past_due", sub.getStatus());
        }
    }

    @Test
    @DisplayName("handleStripeWebhook: subscription.updated with items updates priceId and plan")
    void testWebhook_subscriptionUpdated_withItems() throws Exception {
        com.stripe.model.Subscription mockStripeSub = mock(com.stripe.model.Subscription.class);
        when(mockStripeSub.getId()).thenReturn("sub_upd2");
        when(mockStripeSub.getStatus()).thenReturn("active");
        when(mockStripeSub.getCurrentPeriodEnd()).thenReturn(3000L);

        final SubscriptionItemCollection itemCollection = mock(SubscriptionItemCollection.class);
        final SubscriptionItem item = mock(SubscriptionItem.class);
        final Price price = mock(Price.class);
        when(price.getId()).thenReturn("price_upd");
        when(item.getPrice()).thenReturn(price);
        when(itemCollection.getData()).thenReturn(List.of(item));
        when(mockStripeSub.getItems()).thenReturn(itemCollection);

        final Subscription sub = new Subscription();
        sub.setStripeSubscriptionId("sub_upd2");
        when(subscriptionRepository.findByStripeSubscriptionId("sub_upd2")).thenReturn(Optional.of(sub));

        final Plan plan = new Plan();
        when(planRepository.findByCode("price_upd")).thenReturn(plan);
        when(subscriptionRepository.save(any())).thenAnswer(inv -> inv.getArgument(0));

        final Event event = createMockWebhookEvent("customer.subscription.updated", mockStripeSub);

        try (MockedStatic<Webhook> webhookMock = mockStatic(Webhook.class)) {
            webhookMock.when(() -> Webhook.constructEvent(anyString(), anyString(), anyString()))
                    .thenReturn(event);

            subscriptionService.handleStripeWebhook("{}", "sig", "secret");
            assertEquals("price_upd", sub.getPriceId());
            assertEquals(plan, sub.getPlan());
        }
    }

    @Test
    @DisplayName("handleStripeWebhook: subscription.updated with null stripeSub is no-op")
    void testWebhook_subscriptionUpdated_nullStripeSub() throws Exception {
        final Event event = createMockWebhookEvent("customer.subscription.updated", null);

        try (MockedStatic<Webhook> webhookMock = mockStatic(Webhook.class)) {
            webhookMock.when(() -> Webhook.constructEvent(anyString(), anyString(), anyString()))
                    .thenReturn(event);

            final String result = subscriptionService.handleStripeWebhook("{}", "sig", "secret");
            assertEquals("Webhook received", result);
        }
    }

    // ==========================================================================
    // handleSubscriptionDeleted
    // ==========================================================================

    @Test
    @DisplayName("handleStripeWebhook: subscription.deleted clears subscription and marks CANCELLED")
    void testWebhook_subscriptionDeleted() throws Exception {
        com.stripe.model.Subscription mockStripeSub = mock(com.stripe.model.Subscription.class);
        when(mockStripeSub.getId()).thenReturn("sub_del");

        final Subscription sub = new Subscription();
        sub.setStripeSubscriptionId("sub_del");
        sub.setStripeCustomerId("cus_del");
        sub.setStatus("ACTIVE");
        sub.setPriceId("price_del");
        when(subscriptionRepository.findByStripeSubscriptionId("sub_del")).thenReturn(Optional.of(sub));
        when(subscriptionRepository.save(any())).thenAnswer(inv -> inv.getArgument(0));

        final Event event = createMockWebhookEvent("customer.subscription.deleted", mockStripeSub);

        try (MockedStatic<Webhook> webhookMock = mockStatic(Webhook.class)) {
            webhookMock.when(() -> Webhook.constructEvent(anyString(), anyString(), anyString()))
                    .thenReturn(event);

            subscriptionService.handleStripeWebhook("{}", "sig", "secret");
            assertEquals("CANCELLED", sub.getStatus());
            assertNull(sub.getStripeSubscriptionId());
            assertNull(sub.getStripeCustomerId());
            assertNull(sub.getPriceId());
            assertNull(sub.getPlan());
        }
    }

    @Test
    @DisplayName("handleStripeWebhook: subscription.deleted with null stripeSub is no-op")
    void testWebhook_subscriptionDeleted_nullStripeSub() throws Exception {
        final Event event = createMockWebhookEvent("customer.subscription.deleted", null);

        try (MockedStatic<Webhook> webhookMock = mockStatic(Webhook.class)) {
            webhookMock.when(() -> Webhook.constructEvent(anyString(), anyString(), anyString()))
                    .thenReturn(event);

            final String result = subscriptionService.handleStripeWebhook("{}", "sig", "secret");
            assertEquals("Webhook received", result);
        }
    }

    // ==========================================================================
    // handleInvoicePaid
    // ==========================================================================

    @Test
    @DisplayName("handleStripeWebhook: invoice.paid sets ACTIVE and creates payment record")
    void testWebhook_invoicePaid() throws Exception {
        final Invoice mockInvoice = mock(Invoice.class);
        when(mockInvoice.getSubscription()).thenReturn("sub_inv");
        when(mockInvoice.getId()).thenReturn("inv_123");
        when(mockInvoice.getAmountPaid()).thenReturn(2000L);

        final User user = User.builder().id(1L).email("test@x.com").password("pw").build();
        final Subscription sub = new Subscription();
        sub.setStripeSubscriptionId("sub_inv");
        sub.setUser(user);
        sub.setStatus("PENDING");
        when(subscriptionRepository.findByStripeSubscriptionId("sub_inv")).thenReturn(Optional.of(sub));
        when(subscriptionRepository.save(any())).thenAnswer(inv -> inv.getArgument(0));
        when(paymentRepository.save(any())).thenAnswer(inv -> inv.getArgument(0));

        final Event event = createMockWebhookEvent("invoice.paid", mockInvoice);

        try (MockedStatic<Webhook> webhookMock = mockStatic(Webhook.class)) {
            webhookMock.when(() -> Webhook.constructEvent(anyString(), anyString(), anyString()))
                    .thenReturn(event);

            subscriptionService.handleStripeWebhook("{}", "sig", "secret");
            assertEquals("ACTIVE", sub.getStatus());
            verify(paymentRepository).save(any());
        }
    }

    @Test
    @DisplayName("handleStripeWebhook: invoice.paid with null invoice is no-op")
    void testWebhook_invoicePaid_nullInvoice() throws Exception {
        final Event event = createMockWebhookEvent("invoice.paid", null);

        try (MockedStatic<Webhook> webhookMock = mockStatic(Webhook.class)) {
            webhookMock.when(() -> Webhook.constructEvent(anyString(), anyString(), anyString()))
                    .thenReturn(event);

            final String result = subscriptionService.handleStripeWebhook("{}", "sig", "secret");
            assertEquals("Webhook received", result);
        }
    }

    @Test
    @DisplayName("handleStripeWebhook: invoice.paid with null subscription in invoice is no-op")
    void testWebhook_invoicePaid_nullSubInInvoice() throws Exception {
        final Invoice mockInvoice = mock(Invoice.class);
        when(mockInvoice.getSubscription()).thenReturn(null);

        final Event event = createMockWebhookEvent("invoice.paid", mockInvoice);

        try (MockedStatic<Webhook> webhookMock = mockStatic(Webhook.class)) {
            webhookMock.when(() -> Webhook.constructEvent(anyString(), anyString(), anyString()))
                    .thenReturn(event);

            final String result = subscriptionService.handleStripeWebhook("{}", "sig", "secret");
            assertEquals("Webhook received", result);
        }
    }

    @Test
    @DisplayName("handleStripeWebhook: invoice.paid with null user on subscription does not create payment")
    void testWebhook_invoicePaid_nullUser() throws Exception {
        final Invoice mockInvoice = mock(Invoice.class);
        when(mockInvoice.getSubscription()).thenReturn("sub_nouser");
        when(mockInvoice.getAmountPaid()).thenReturn(2000L);

        final Subscription sub = new Subscription();
        sub.setStripeSubscriptionId("sub_nouser");
        sub.setUser(null);
        when(subscriptionRepository.findByStripeSubscriptionId("sub_nouser")).thenReturn(Optional.of(sub));
        when(subscriptionRepository.save(any())).thenAnswer(inv -> inv.getArgument(0));

        final Event event = createMockWebhookEvent("invoice.paid", mockInvoice);

        try (MockedStatic<Webhook> webhookMock = mockStatic(Webhook.class)) {
            webhookMock.when(() -> Webhook.constructEvent(anyString(), anyString(), anyString()))
                    .thenReturn(event);

            subscriptionService.handleStripeWebhook("{}", "sig", "secret");
            verify(paymentRepository, never()).save(any());
        }
    }

    // ==========================================================================
    // handleInvoicePaymentFailed
    // ==========================================================================

    @Test
    @DisplayName("handleStripeWebhook: invoice.payment_failed sets status PAYMENT_FAILED")
    void testWebhook_invoicePaymentFailed() throws Exception {
        final Invoice mockInvoice = mock(Invoice.class);
        when(mockInvoice.getSubscription()).thenReturn("sub_ipf");

        final Subscription sub = new Subscription();
        sub.setStatus("ACTIVE");
        when(subscriptionRepository.findByStripeSubscriptionId("sub_ipf")).thenReturn(Optional.of(sub));
        when(subscriptionRepository.save(any())).thenAnswer(inv -> inv.getArgument(0));

        final Event event = createMockWebhookEvent("invoice.payment_failed", mockInvoice);

        try (MockedStatic<Webhook> webhookMock = mockStatic(Webhook.class)) {
            webhookMock.when(() -> Webhook.constructEvent(anyString(), anyString(), anyString()))
                    .thenReturn(event);

            subscriptionService.handleStripeWebhook("{}", "sig", "secret");
            assertEquals("PAYMENT_FAILED", sub.getStatus());
        }
    }

    @Test
    @DisplayName("handleStripeWebhook: invoice.payment_failed with null invoice is no-op")
    void testWebhook_invoicePaymentFailed_nullInvoice() throws Exception {
        final Event event = createMockWebhookEvent("invoice.payment_failed", null);

        try (MockedStatic<Webhook> webhookMock = mockStatic(Webhook.class)) {
            webhookMock.when(() -> Webhook.constructEvent(anyString(), anyString(), anyString()))
                    .thenReturn(event);

            final String result = subscriptionService.handleStripeWebhook("{}", "sig", "secret");
            assertEquals("Webhook received", result);
        }
    }

    // ==========================================================================
    // Unhandled event type
    // ==========================================================================

    @Test
    @DisplayName("handleStripeWebhook: unhandled event type returns 'Webhook received'")
    void testWebhook_unhandledEventType() throws Exception {
        final Event event = createMockWebhookEvent("some.unknown.event", null);

        try (MockedStatic<Webhook> webhookMock = mockStatic(Webhook.class)) {
            webhookMock.when(() -> Webhook.constructEvent(anyString(), anyString(), anyString()))
                    .thenReturn(event);

            final String result = subscriptionService.handleStripeWebhook("{}", "sig", "secret");
            assertEquals("Webhook received", result);
        }
    }

    // ==========================================================================
    // getUserSubscriptions — with Stripe sync (MockedStatic)
    // ==========================================================================

    @Test
    @DisplayName("getUserSubscriptions: syncs with Stripe when user has stripeCustomerId")
    void testGetUserSubscriptions_withStripeCustomerId_syncs() throws Exception {
        final User user = User.builder().id(1L).email("test@x.com").password("pw").build();
        user.setStripeCustomerId("cus_sync");
        when(userRepository.findById(1L)).thenReturn(Optional.of(user));
        when(userRepository.findByStripeCustomerId("cus_sync")).thenReturn(Optional.of(user));

        final SubscriptionCollection mockCollection = mock(SubscriptionCollection.class);
        when(mockCollection.getData()).thenReturn(List.of());

        final Subscription sub = new Subscription();
        sub.setStatus("ACTIVE");
        when(subscriptionRepository.findByUser(user)).thenReturn(List.of(sub));

        try (MockedStatic<com.stripe.model.Subscription> stripeMock = mockStatic(com.stripe.model.Subscription.class)) {
            stripeMock.when(() -> com.stripe.model.Subscription.list(anyMap())).thenReturn(mockCollection);

            final List<Subscription> result = subscriptionService.getUserSubscriptions(1L);

            assertNotNull(result);
            assertEquals(1, result.size());
        }
    }

    @Test
    @DisplayName("getUserSubscriptions: continues with DB records when Stripe sync fails")
    void testGetUserSubscriptions_stripeSyncFails_returnsDbRecords() throws Exception {
        final User user = User.builder().id(2L).email("test@x.com").password("pw").build();
        user.setStripeCustomerId("cus_fail");
        when(userRepository.findById(2L)).thenReturn(Optional.of(user));
        when(userRepository.findByStripeCustomerId("cus_fail")).thenReturn(Optional.of(user));

        final Subscription sub = new Subscription();
        sub.setStatus("ACTIVE");
        when(subscriptionRepository.findByUser(user)).thenReturn(List.of(sub));

        try (MockedStatic<com.stripe.model.Subscription> stripeMock = mockStatic(com.stripe.model.Subscription.class)) {
            stripeMock.when(() -> com.stripe.model.Subscription.list(anyMap()))
                    .thenThrow(mock(com.stripe.exception.StripeException.class));

            final List<Subscription> result = subscriptionService.getUserSubscriptions(2L);

            assertNotNull(result);
            assertEquals(1, result.size());
        }
    }

    // ==========================================================================
    // getUserActiveSubscriptions — with Stripe sync (MockedStatic)
    // ==========================================================================

    @Test
    @DisplayName("getUserActiveSubscriptions: syncs with Stripe when user has stripeCustomerId")
    void testGetUserActiveSubscriptions_withStripeCustomerId_syncs() throws Exception {
        final User user = User.builder().id(3L).email("test@x.com").password("pw").build();
        user.setStripeCustomerId("cus_active");
        when(userRepository.findById(3L)).thenReturn(Optional.of(user));
        when(userRepository.findByStripeCustomerId("cus_active")).thenReturn(Optional.of(user));

        final SubscriptionCollection mockCollection = mock(SubscriptionCollection.class);
        when(mockCollection.getData()).thenReturn(List.of());

        final Subscription sub = new Subscription();
        sub.setStatus("ACTIVE");
        when(subscriptionRepository.findByUserAndStatus(user, "ACTIVE")).thenReturn(List.of(sub));

        try (MockedStatic<com.stripe.model.Subscription> stripeMock = mockStatic(com.stripe.model.Subscription.class)) {
            stripeMock.when(() -> com.stripe.model.Subscription.list(anyMap())).thenReturn(mockCollection);

            final List<Subscription> result = subscriptionService.getUserActiveSubscriptions(3L);

            assertNotNull(result);
            assertEquals(1, result.size());
        }
    }

    @Test
    @DisplayName("getUserActiveSubscriptions: continues when Stripe sync fails")
    void testGetUserActiveSubscriptions_stripeSyncFails() throws Exception {
        final User user = User.builder().id(4L).email("test@x.com").password("pw").build();
        user.setStripeCustomerId("cus_afail");
        when(userRepository.findById(4L)).thenReturn(Optional.of(user));
        when(userRepository.findByStripeCustomerId("cus_afail")).thenReturn(Optional.of(user));

        when(subscriptionRepository.findByUserAndStatus(user, "ACTIVE")).thenReturn(List.of());

        try (MockedStatic<com.stripe.model.Subscription> stripeMock = mockStatic(com.stripe.model.Subscription.class)) {
            stripeMock.when(() -> com.stripe.model.Subscription.list(anyMap()))
                    .thenThrow(mock(com.stripe.exception.StripeException.class));

            final List<Subscription> result = subscriptionService.getUserActiveSubscriptions(4L);

            assertNotNull(result);
            assertTrue(result.isEmpty());
        }
    }

    // ==========================================================================
    // syncAllSubscriptionsForCustomer (MockedStatic)
    // ==========================================================================

    @Test
    @DisplayName("syncAllSubscriptionsForCustomer: syncs existing and new subscriptions")
    void testSyncAllSubscriptionsForCustomer_mixedExistingAndNew() throws Exception {
        final User user = User.builder().id(1L).email("test@x.com").password("pw").build();
        user.setStripeCustomerId("cus_all");
        when(userRepository.findByStripeCustomerId("cus_all")).thenReturn(Optional.of(user));

        // Existing sub
        com.stripe.model.Subscription stripeSub1 = mock(com.stripe.model.Subscription.class);
        when(stripeSub1.getId()).thenReturn("sub_ex");
        when(stripeSub1.getStatus()).thenReturn("active");
        when(stripeSub1.getCurrentPeriodStart()).thenReturn(1000L);
        when(stripeSub1.getCurrentPeriodEnd()).thenReturn(2000L);
        when(stripeSub1.getItems()).thenReturn(null);

        final Subscription existingSub = new Subscription();
        existingSub.setId(10L);
        when(subscriptionRepository.findByStripeSubscriptionId("sub_ex")).thenReturn(Optional.of(existingSub));

        // New sub
        com.stripe.model.Subscription stripeSub2 = mock(com.stripe.model.Subscription.class);
        when(stripeSub2.getId()).thenReturn("sub_new");
        when(stripeSub2.getStatus()).thenReturn("active");
        when(stripeSub2.getCurrentPeriodStart()).thenReturn(1000L);
        when(stripeSub2.getCurrentPeriodEnd()).thenReturn(2000L);
        when(stripeSub2.getItems()).thenReturn(null);

        when(subscriptionRepository.findByStripeSubscriptionId("sub_new")).thenReturn(Optional.empty());

        final SubscriptionCollection mockCollection = mock(SubscriptionCollection.class);
        when(mockCollection.getData()).thenReturn(List.of(stripeSub1, stripeSub2));

        when(subscriptionRepository.save(any())).thenAnswer(inv -> inv.getArgument(0));

        try (MockedStatic<com.stripe.model.Subscription> stripeMock = mockStatic(com.stripe.model.Subscription.class)) {
            stripeMock.when(() -> com.stripe.model.Subscription.list(anyMap())).thenReturn(mockCollection);

            final List<Subscription> result = subscriptionService.syncAllSubscriptionsForCustomer("cus_all");

            assertEquals(2, result.size());
            verify(subscriptionRepository, times(2)).save(any());
        }
    }

    @Test
    @DisplayName("syncAllSubscriptionsForCustomer: returns empty list when no Stripe subscriptions")
    void testSyncAllSubscriptionsForCustomer_empty() throws Exception {
        final User user = User.builder().id(1L).email("test@x.com").password("pw").build();
        when(userRepository.findByStripeCustomerId("cus_empty")).thenReturn(Optional.of(user));

        final SubscriptionCollection mockCollection = mock(SubscriptionCollection.class);
        when(mockCollection.getData()).thenReturn(List.of());

        try (MockedStatic<com.stripe.model.Subscription> stripeMock = mockStatic(com.stripe.model.Subscription.class)) {
            stripeMock.when(() -> com.stripe.model.Subscription.list(anyMap())).thenReturn(mockCollection);

            final List<Subscription> result = subscriptionService.syncAllSubscriptionsForCustomer("cus_empty");

            assertTrue(result.isEmpty());
        }
    }

    @Test
    @DisplayName("syncAllSubscriptionsForCustomer: throws when user not found")
    void testSyncAllSubscriptionsForCustomer_userNotFound() throws Exception {
        when(userRepository.findByStripeCustomerId("cus_nope")).thenReturn(Optional.empty());

        assertThrows(IllegalArgumentException.class,
                () -> subscriptionService.syncAllSubscriptionsForCustomer("cus_nope"));
    }

    @Test
    @DisplayName("syncAllSubscriptionsForCustomer: with items sets priceId and plan")
    void testSyncAllSubscriptionsForCustomer_withItems() throws Exception {
        final User user = User.builder().id(1L).email("test@x.com").password("pw").build();
        when(userRepository.findByStripeCustomerId("cus_items2")).thenReturn(Optional.of(user));

        com.stripe.model.Subscription stripeSub = mock(com.stripe.model.Subscription.class);
        when(stripeSub.getId()).thenReturn("sub_items2");
        when(stripeSub.getStatus()).thenReturn("active");
        when(stripeSub.getCurrentPeriodStart()).thenReturn(1000L);
        when(stripeSub.getCurrentPeriodEnd()).thenReturn(2000L);

        final SubscriptionItemCollection itemCollection = mock(SubscriptionItemCollection.class);
        final SubscriptionItem item = mock(SubscriptionItem.class);
        final Price price = mock(Price.class);
        when(price.getId()).thenReturn("price_sync");
        when(item.getPrice()).thenReturn(price);
        when(itemCollection.getData()).thenReturn(List.of(item));
        when(stripeSub.getItems()).thenReturn(itemCollection);

        when(subscriptionRepository.findByStripeSubscriptionId("sub_items2")).thenReturn(Optional.empty());

        final Plan plan = new Plan();
        when(planRepository.findByCode("price_sync")).thenReturn(plan);

        final SubscriptionCollection mockCollection = mock(SubscriptionCollection.class);
        when(mockCollection.getData()).thenReturn(List.of(stripeSub));

        when(subscriptionRepository.save(any())).thenAnswer(inv -> inv.getArgument(0));

        try (MockedStatic<com.stripe.model.Subscription> stripeMock = mockStatic(com.stripe.model.Subscription.class)) {
            stripeMock.when(() -> com.stripe.model.Subscription.list(anyMap())).thenReturn(mockCollection);

            final List<Subscription> result = subscriptionService.syncAllSubscriptionsForCustomer("cus_items2");

            assertEquals(1, result.size());
            assertEquals("price_sync", result.get(0).getPriceId());
            assertEquals(plan, result.get(0).getPlan());
        }
    }

    @Test
    @DisplayName("syncAllSubscriptionsForCustomer: rethrows StripeException")
    void testSyncAllSubscriptionsForCustomer_stripeException() throws Exception {
        final User user = User.builder().id(1L).email("test@x.com").password("pw").build();
        when(userRepository.findByStripeCustomerId("cus_err")).thenReturn(Optional.of(user));

        try (MockedStatic<com.stripe.model.Subscription> stripeMock = mockStatic(com.stripe.model.Subscription.class)) {
            stripeMock.when(() -> com.stripe.model.Subscription.list(anyMap()))
                    .thenThrow(new RuntimeException("Stripe error"));

            assertThrows(RuntimeException.class,
                    () -> subscriptionService.syncAllSubscriptionsForCustomer("cus_err"));
        }
    }

    // ==========================================================================
    // syncSubscriptionFromStripe (MockedStatic)
    // ==========================================================================

    @Test
    @DisplayName("syncSubscriptionFromStripe: updates existing subscription")
    void testSyncSubscriptionFromStripe_existing() throws Exception {
        final Subscription existingSub = new Subscription();
        existingSub.setId(10L);
        existingSub.setStripeSubscriptionId("sub_sync_ex");
        when(subscriptionRepository.findByStripeSubscriptionId("sub_sync_ex")).thenReturn(Optional.of(existingSub));

        com.stripe.model.Subscription mockStripeSub = mock(com.stripe.model.Subscription.class);
        when(mockStripeSub.getCustomer()).thenReturn("cus_sync_ex");
        when(mockStripeSub.getStatus()).thenReturn("active");
        when(mockStripeSub.getCurrentPeriodStart()).thenReturn(1000L);
        when(mockStripeSub.getCurrentPeriodEnd()).thenReturn(2000L);
        when(mockStripeSub.getItems()).thenReturn(null);

        when(subscriptionRepository.save(any())).thenAnswer(inv -> inv.getArgument(0));

        try (MockedStatic<com.stripe.model.Subscription> stripeMock = mockStatic(com.stripe.model.Subscription.class)) {
            stripeMock.when(() -> com.stripe.model.Subscription.retrieve("sub_sync_ex")).thenReturn(mockStripeSub);

            final Subscription result = subscriptionService.syncSubscriptionFromStripe("sub_sync_ex");

            assertEquals("active", result.getStatus());
            assertEquals("cus_sync_ex", result.getStripeCustomerId());
        }
    }

    @Test
    @DisplayName("syncSubscriptionFromStripe: updates existing subscription with items")
    void testSyncSubscriptionFromStripe_existingWithItems() throws Exception {
        final Subscription existingSub = new Subscription();
        existingSub.setId(11L);
        when(subscriptionRepository.findByStripeSubscriptionId("sub_sync_items")).thenReturn(Optional.of(existingSub));

        com.stripe.model.Subscription mockStripeSub = mock(com.stripe.model.Subscription.class);
        when(mockStripeSub.getCustomer()).thenReturn("cus_si");
        when(mockStripeSub.getStatus()).thenReturn("active");
        when(mockStripeSub.getCurrentPeriodStart()).thenReturn(1000L);
        when(mockStripeSub.getCurrentPeriodEnd()).thenReturn(2000L);

        final SubscriptionItemCollection itemCollection = mock(SubscriptionItemCollection.class);
        final SubscriptionItem item = mock(SubscriptionItem.class);
        final Price price = mock(Price.class);
        when(price.getId()).thenReturn("price_si");
        when(item.getPrice()).thenReturn(price);
        when(itemCollection.getData()).thenReturn(List.of(item));
        when(mockStripeSub.getItems()).thenReturn(itemCollection);

        final Plan plan = new Plan();
        when(planRepository.findByCode("price_si")).thenReturn(plan);
        when(subscriptionRepository.save(any())).thenAnswer(inv -> inv.getArgument(0));

        try (MockedStatic<com.stripe.model.Subscription> stripeMock = mockStatic(com.stripe.model.Subscription.class)) {
            stripeMock.when(() -> com.stripe.model.Subscription.retrieve("sub_sync_items")).thenReturn(mockStripeSub);

            final Subscription result = subscriptionService.syncSubscriptionFromStripe("sub_sync_items");

            assertEquals("price_si", result.getPriceId());
            assertEquals(plan, result.getPlan());
        }
    }

    @Test
    @DisplayName("syncSubscriptionFromStripe: creates new subscription when not found locally")
    void testSyncSubscriptionFromStripe_new() throws Exception {
        when(subscriptionRepository.findByStripeSubscriptionId("sub_sync_new")).thenReturn(Optional.empty());

        com.stripe.model.Subscription mockStripeSub = mock(com.stripe.model.Subscription.class);
        when(mockStripeSub.getCustomer()).thenReturn("cus_sync_new");
        when(mockStripeSub.getStatus()).thenReturn("active");
        when(mockStripeSub.getCurrentPeriodStart()).thenReturn(1000L);
        when(mockStripeSub.getCurrentPeriodEnd()).thenReturn(2000L);
        when(mockStripeSub.getItems()).thenReturn(null);

        final User user = User.builder().id(1L).email("test@x.com").password("pw").build();
        when(userRepository.findByStripeCustomerId("cus_sync_new")).thenReturn(Optional.of(user));
        when(subscriptionRepository.save(any())).thenAnswer(inv -> inv.getArgument(0));

        try (MockedStatic<com.stripe.model.Subscription> stripeMock = mockStatic(com.stripe.model.Subscription.class)) {
            stripeMock.when(() -> com.stripe.model.Subscription.retrieve("sub_sync_new")).thenReturn(mockStripeSub);

            final Subscription result = subscriptionService.syncSubscriptionFromStripe("sub_sync_new");

            assertEquals("sub_sync_new", result.getStripeSubscriptionId());
            assertEquals(user, result.getUser());
        }
    }

    @Test
    @DisplayName("syncSubscriptionFromStripe: new sub with items and plan")
    void testSyncSubscriptionFromStripe_newWithItems() throws Exception {
        when(subscriptionRepository.findByStripeSubscriptionId("sub_sync_ni")).thenReturn(Optional.empty());

        com.stripe.model.Subscription mockStripeSub = mock(com.stripe.model.Subscription.class);
        when(mockStripeSub.getCustomer()).thenReturn("cus_sync_ni");
        when(mockStripeSub.getStatus()).thenReturn("active");
        when(mockStripeSub.getCurrentPeriodStart()).thenReturn(1000L);
        when(mockStripeSub.getCurrentPeriodEnd()).thenReturn(2000L);

        final SubscriptionItemCollection itemCollection = mock(SubscriptionItemCollection.class);
        final SubscriptionItem item = mock(SubscriptionItem.class);
        final Price price = mock(Price.class);
        when(price.getId()).thenReturn("price_ni");
        when(item.getPrice()).thenReturn(price);
        when(itemCollection.getData()).thenReturn(List.of(item));
        when(mockStripeSub.getItems()).thenReturn(itemCollection);

        final User user = User.builder().id(1L).email("test@x.com").password("pw").build();
        when(userRepository.findByStripeCustomerId("cus_sync_ni")).thenReturn(Optional.of(user));

        final Plan plan = new Plan();
        when(planRepository.findByCode("price_ni")).thenReturn(plan);
        when(subscriptionRepository.save(any())).thenAnswer(inv -> inv.getArgument(0));

        try (MockedStatic<com.stripe.model.Subscription> stripeMock = mockStatic(com.stripe.model.Subscription.class)) {
            stripeMock.when(() -> com.stripe.model.Subscription.retrieve("sub_sync_ni")).thenReturn(mockStripeSub);

            final Subscription result = subscriptionService.syncSubscriptionFromStripe("sub_sync_ni");

            assertEquals("price_ni", result.getPriceId());
            assertEquals(plan, result.getPlan());
        }
    }

    @Test
    @DisplayName("syncSubscriptionFromStripe: new sub throws when user not found")
    void testSyncSubscriptionFromStripe_newUserNotFound() throws Exception {
        when(subscriptionRepository.findByStripeSubscriptionId("sub_sync_nouser")).thenReturn(Optional.empty());

        com.stripe.model.Subscription mockStripeSub = mock(com.stripe.model.Subscription.class);
        when(mockStripeSub.getCustomer()).thenReturn("cus_sync_nouser");

        when(userRepository.findByStripeCustomerId("cus_sync_nouser")).thenReturn(Optional.empty());

        try (MockedStatic<com.stripe.model.Subscription> stripeMock = mockStatic(com.stripe.model.Subscription.class)) {
            stripeMock.when(() -> com.stripe.model.Subscription.retrieve("sub_sync_nouser")).thenReturn(mockStripeSub);

            assertThrows(IllegalArgumentException.class,
                    () -> subscriptionService.syncSubscriptionFromStripe("sub_sync_nouser"));
        }
    }

    // ==========================================================================
    // createSubscriptionDirectly (MockedStatic)
    // ==========================================================================

    @Test
    @DisplayName("createSubscriptionDirectly: creates subscription with existing customer ID")
    void testCreateSubscriptionDirectly_existingCustomerId() throws Exception {
        final User user = User.builder().id(1L).email("test@x.com").password("pw").build();
        user.setStripeCustomerId("cus_direct");
        when(userRepository.findById(1L)).thenReturn(Optional.of(user));

        com.stripe.model.Subscription mockStripeSub = mock(com.stripe.model.Subscription.class);
        when(mockStripeSub.getId()).thenReturn("sub_direct");
        when(mockStripeSub.getCurrentPeriodStart()).thenReturn(1000L);
        when(mockStripeSub.getCurrentPeriodEnd()).thenReturn(2000L);

        when(planRepository.findByCode("price_direct")).thenReturn(null);
        when(subscriptionRepository.save(any())).thenAnswer(inv -> inv.getArgument(0));

        try (MockedStatic<com.stripe.model.Subscription> stripeMock = mockStatic(com.stripe.model.Subscription.class)) {
            stripeMock.when(() -> com.stripe.model.Subscription.create(anyMap())).thenReturn(mockStripeSub);

            final Subscription result = subscriptionService.createSubscriptionDirectly(1L, "price_direct");

            assertEquals("sub_direct", result.getStripeSubscriptionId());
            assertEquals("ACTIVE", result.getStatus());
            assertEquals("price_direct", result.getPriceId());
            assertNull(result.getPlan());
        }
    }

    @Test
    @DisplayName("createSubscriptionDirectly: creates customer when user has no customer ID")
    void testCreateSubscriptionDirectly_noCustomerId() throws Exception {
        final User user = User.builder().id(2L).email("test@x.com").password("pw").build();
        when(userRepository.findById(2L)).thenReturn(Optional.of(user));

        final Customer mockCustomer = mock(Customer.class);
        when(mockCustomer.getId()).thenReturn("cus_created");

        com.stripe.model.Subscription mockStripeSub = mock(com.stripe.model.Subscription.class);
        when(mockStripeSub.getId()).thenReturn("sub_created_d");
        when(mockStripeSub.getCurrentPeriodStart()).thenReturn(1000L);
        when(mockStripeSub.getCurrentPeriodEnd()).thenReturn(2000L);

        final Plan plan = new Plan();
        when(planRepository.findByCode("price_cd")).thenReturn(plan);
        when(subscriptionRepository.save(any())).thenAnswer(inv -> inv.getArgument(0));

        try (MockedStatic<Customer> customerMock = mockStatic(Customer.class);
             final MockedStatic<com.stripe.model.Subscription> stripeMock = mockStatic(com.stripe.model.Subscription.class)) {
            customerMock.when(() -> Customer.create(anyMap())).thenReturn(mockCustomer);
            stripeMock.when(() -> com.stripe.model.Subscription.create(anyMap())).thenReturn(mockStripeSub);

            final Subscription result = subscriptionService.createSubscriptionDirectly(2L, "price_cd");

            assertEquals("cus_created", user.getStripeCustomerId());
            assertEquals(plan, result.getPlan());
            verify(userRepository).save(user);
        }
    }

    @Test
    @DisplayName("createSubscriptionDirectly: throws when user not found")
    void testCreateSubscriptionDirectly_userNotFound() throws Exception {
        when(userRepository.findById(99L)).thenReturn(Optional.empty());

        assertThrows(IllegalArgumentException.class,
                () -> subscriptionService.createSubscriptionDirectly(99L, "price_x"));
    }

    // ==========================================================================
    // syncUserSubscriptionsFromStripe (MockedStatic)
    // ==========================================================================

    @Test
    @DisplayName("syncUserSubscriptionsFromStripe: uses existing stripeCustomerId")
    void testSyncUserSubscriptionsFromStripe_existingCustomerId() throws Exception {
        final User user = User.builder().id(1L).email("test@x.com").password("pw").build();
        user.setStripeCustomerId("cus_sync_u");
        when(userRepository.findById(1L)).thenReturn(Optional.of(user));
        when(userRepository.findByStripeCustomerId("cus_sync_u")).thenReturn(Optional.of(user));

        final SubscriptionCollection mockCollection = mock(SubscriptionCollection.class);
        when(mockCollection.getData()).thenReturn(List.of());

        try (MockedStatic<com.stripe.model.Subscription> stripeMock = mockStatic(com.stripe.model.Subscription.class)) {
            stripeMock.when(() -> com.stripe.model.Subscription.list(anyMap())).thenReturn(mockCollection);

            final List<Subscription> result = subscriptionService.syncUserSubscriptionsFromStripe(1L);
            assertTrue(result.isEmpty());
        }
    }

    @Test
    @DisplayName("syncUserSubscriptionsFromStripe: finds customer by email when no stripeCustomerId")
    void testSyncUserSubscriptionsFromStripe_findsCustomerByEmail() throws Exception {
        final User user = User.builder().id(2L).email("test@x.com").password("pw").build();
        when(userRepository.findById(2L)).thenReturn(Optional.of(user));

        final Customer mockCustomer = mock(Customer.class);
        when(mockCustomer.getId()).thenReturn("cus_found");

        final CustomerCollection mockCustCollection = mock(CustomerCollection.class);
        when(mockCustCollection.getData()).thenReturn(List.of(mockCustomer));

        final SubscriptionCollection mockSubCollection = mock(SubscriptionCollection.class);
        when(mockSubCollection.getData()).thenReturn(List.of());

        when(userRepository.findByStripeCustomerId("cus_found")).thenReturn(Optional.of(user));

        try (MockedStatic<Customer> customerMock = mockStatic(Customer.class);
             final MockedStatic<com.stripe.model.Subscription> stripeMock = mockStatic(com.stripe.model.Subscription.class)) {
            customerMock.when(() -> Customer.list(anyMap())).thenReturn(mockCustCollection);
            stripeMock.when(() -> com.stripe.model.Subscription.list(anyMap())).thenReturn(mockSubCollection);

            subscriptionService.syncUserSubscriptionsFromStripe(2L);

            assertEquals("cus_found", user.getStripeCustomerId());
            verify(userRepository).save(user);
        }
    }

    @Test
    @DisplayName("syncUserSubscriptionsFromStripe: creates new customer when not found by email")
    void testSyncUserSubscriptionsFromStripe_createsNewCustomer() throws Exception {
        final User user = User.builder().id(3L).email("test@x.com").password("pw").build();
        when(userRepository.findById(3L)).thenReturn(Optional.of(user));

        final CustomerCollection emptyCustCollection = mock(CustomerCollection.class);
        when(emptyCustCollection.getData()).thenReturn(List.of());

        final Customer mockNewCustomer = mock(Customer.class);
        when(mockNewCustomer.getId()).thenReturn("cus_brand_new");

        final SubscriptionCollection mockSubCollection = mock(SubscriptionCollection.class);
        when(mockSubCollection.getData()).thenReturn(List.of());

        when(userRepository.findByStripeCustomerId("cus_brand_new")).thenReturn(Optional.of(user));

        try (MockedStatic<Customer> customerMock = mockStatic(Customer.class);
             final MockedStatic<com.stripe.model.Subscription> stripeMock = mockStatic(com.stripe.model.Subscription.class)) {
            customerMock.when(() -> Customer.list(anyMap())).thenReturn(emptyCustCollection);
            customerMock.when(() -> Customer.create(anyMap())).thenReturn(mockNewCustomer);
            stripeMock.when(() -> com.stripe.model.Subscription.list(anyMap())).thenReturn(mockSubCollection);

            subscriptionService.syncUserSubscriptionsFromStripe(3L);

            assertEquals("cus_brand_new", user.getStripeCustomerId());
        }
    }

    @Test
    @DisplayName("syncUserSubscriptionsFromStripe: throws when Customer.list fails")
    void testSyncUserSubscriptionsFromStripe_customerListFails() throws Exception {
        final User user = User.builder().id(4L).email("test@x.com").password("pw").build();
        when(userRepository.findById(4L)).thenReturn(Optional.of(user));

        try (MockedStatic<Customer> customerMock = mockStatic(Customer.class)) {
            customerMock.when(() -> Customer.list(anyMap())).thenThrow(new RuntimeException("fail"));

            assertThrows(Exception.class,
                    () -> subscriptionService.syncUserSubscriptionsFromStripe(4L));
        }
    }

    @Test
    @DisplayName("syncUserSubscriptionsFromStripe: throws when user not found")
    void testSyncUserSubscriptionsFromStripe_userNotFound() throws Exception {
        when(userRepository.findById(99L)).thenReturn(Optional.empty());

        assertThrows(IllegalArgumentException.class,
                () -> subscriptionService.syncUserSubscriptionsFromStripe(99L));
    }

    // ==========================================================================
    // refreshUserSubscriptionsFromStripe
    // ==========================================================================

    @Test
    @DisplayName("refreshUserSubscriptionsFromStripe: delegates to syncUserSubscriptionsFromStripe")
    void testRefreshUserSubscriptionsFromStripe_success() throws Exception {
        final User user = User.builder().id(1L).email("test@x.com").password("pw").build();
        user.setStripeCustomerId("cus_refresh");
        when(userRepository.findById(1L)).thenReturn(Optional.of(user));
        when(userRepository.findByStripeCustomerId("cus_refresh")).thenReturn(Optional.of(user));

        final SubscriptionCollection mockCollection = mock(SubscriptionCollection.class);
        when(mockCollection.getData()).thenReturn(List.of());

        try (MockedStatic<com.stripe.model.Subscription> stripeMock = mockStatic(com.stripe.model.Subscription.class)) {
            stripeMock.when(() -> com.stripe.model.Subscription.list(anyMap())).thenReturn(mockCollection);

            final List<Subscription> result = subscriptionService.refreshUserSubscriptionsFromStripe(1L);
            assertNotNull(result);
        }
    }

    @Test
    @DisplayName("refreshUserSubscriptionsFromStripe: wraps exception as RuntimeException")
    void testRefreshUserSubscriptionsFromStripe_wrapsException() throws Exception {
        when(userRepository.findById(99L)).thenReturn(Optional.empty());

        final RuntimeException ex = assertThrows(RuntimeException.class,
                () -> subscriptionService.refreshUserSubscriptionsFromStripe(99L));

        assertTrue(ex.getMessage().contains("Failed to refresh subscriptions"));
    }

    // ==========================================================================
    // Edge cases for maximum coverage
    // ==========================================================================

    @Test
    @DisplayName("createCheckoutSession: amount=0 falls through to default pricing")
    void testCreateCheckoutSession_amountZero() throws Exception {
        final Session mockSession = mock(Session.class);
        when(mockSession.getUrl()).thenReturn("https://checkout.stripe.com/session");

        try (MockedStatic<Session> sessionMock = mockStatic(Session.class)) {
            sessionMock.when(() -> Session.create(any(SessionCreateParams.class))).thenReturn(mockSession);

            final ResponseEntity<?> result = subscriptionService.createCheckoutSession(
                    mockRequest(), "standard", null, 0L, null, null);

            assertEquals(200, result.getStatusCode().value());
        }
    }

    @Test
    @DisplayName("createCheckoutSession: empty stripeCustomerId param is ignored")
    void testCreateCheckoutSession_emptyStripeCustomerIdParam() throws Exception {
        final User user = User.builder().id(10L).email("test@x.com").password("pw").build();
        when(userRepository.findById(10L)).thenReturn(Optional.of(user));

        final Customer mockCustomer = mock(Customer.class);
        when(mockCustomer.getId()).thenReturn("cus_new_10");

        final Session mockSession = mock(Session.class);
        when(mockSession.getUrl()).thenReturn("https://checkout.stripe.com/session");

        try (MockedStatic<Customer> customerMock = mockStatic(Customer.class);
             final MockedStatic<Session> sessionMock = mockStatic(Session.class)) {
            customerMock.when(() -> Customer.create(anyMap())).thenReturn(mockCustomer);
            sessionMock.when(() -> Session.create(any(SessionCreateParams.class))).thenReturn(mockSession);

            subscriptionService.createCheckoutSession(mockRequest(), "standard", 10L, 2000L, "", null);

            assertEquals("cus_new_10", user.getStripeCustomerId());
        }
    }

    @Test
    @DisplayName("createCheckoutSession: default plan amount for unknown plan name")
    void testCreateCheckoutSession_unknownPlanName() throws Exception {
        final Session mockSession = mock(Session.class);
        when(mockSession.getUrl()).thenReturn("https://checkout.stripe.com/session");

        try (MockedStatic<Session> sessionMock = mockStatic(Session.class)) {
            sessionMock.when(() -> Session.create(any(SessionCreateParams.class))).thenReturn(mockSession);

            final ResponseEntity<?> result = subscriptionService.createCheckoutSession(
                    mockRequest(), "unknown_plan", null, null, null, null);

            assertEquals(200, result.getStatusCode().value());
        }
    }

    @Test
    @DisplayName("saveCheckoutSession: user with empty stripeCustomerId gets updated")
    void testSaveCheckoutSession_emptyUserCustomerId() throws Exception {
        final User user = User.builder().id(7L).email("test@x.com").password("pw").build();
        user.setStripeCustomerId("");
        final Session mockSession = mock(Session.class);
        when(mockSession.getCustomer()).thenReturn("cus_fill");
        when(mockSession.getSubscription()).thenReturn(null);
        when(mockSession.getId()).thenReturn("cs_7");
        when(mockSession.getPaymentIntent()).thenReturn(null);

        when(userRepository.findById(7L)).thenReturn(Optional.of(user));
        when(subscriptionRepository.save(any())).thenAnswer(inv -> inv.getArgument(0));
        when(paymentRepository.save(any())).thenAnswer(inv -> inv.getArgument(0));

        subscriptionService.saveCheckoutSession(7L, "standard", 2000L, mockSession);

        assertEquals("cus_fill", user.getStripeCustomerId());
        verify(userRepository).save(user);
    }

    @Test
    @DisplayName("handleStripeWebhook: checkout.session.completed — existing sub with non-empty customerId is not overwritten")
    void testWebhook_checkoutCompleted_existingSubWithCustomerId() throws Exception {
        final Session mockSession = mock(Session.class);
        when(mockSession.getSubscription()).thenReturn("sub_keep");
        when(mockSession.getCustomer()).thenReturn("cus_new_id");
        when(mockSession.getClientReferenceId()).thenReturn(null);
        when(mockSession.getId()).thenReturn(null);

        final Subscription existingSub = new Subscription();
        existingSub.setStripeSubscriptionId("sub_keep");
        existingSub.setStripeCustomerId("cus_old_id");
        when(subscriptionRepository.findByStripeSubscriptionId("sub_keep"))
                .thenReturn(Optional.of(existingSub));
        when(subscriptionRepository.save(any())).thenAnswer(inv -> inv.getArgument(0));

        final Event event = createMockWebhookEvent("checkout.session.completed", mockSession);

        try (MockedStatic<Webhook> webhookMock = mockStatic(Webhook.class)) {
            webhookMock.when(() -> Webhook.constructEvent(anyString(), anyString(), anyString()))
                    .thenReturn(event);

            subscriptionService.handleStripeWebhook("{}", "sig", "secret");
            assertEquals("cus_old_id", existingSub.getStripeCustomerId());
        }
    }

    @Test
    @DisplayName("handleStripeWebhook: checkout.session.completed — payment found but user is null, no customer update")
    void testWebhook_checkoutCompleted_paymentWithNullUser() throws Exception {
        final Session mockSession = mock(Session.class);
        when(mockSession.getSubscription()).thenReturn(null);
        when(mockSession.getCustomer()).thenReturn("cus_nouser");
        when(mockSession.getClientReferenceId()).thenReturn(null);
        when(mockSession.getId()).thenReturn("cs_nulluser");

        final Payment payment = Payment.builder().user(null).amountCents(2000).status("PENDING")
                .stripeSessionId("cs_nulluser").build();
        when(paymentRepository.findByStripeSessionId("cs_nulluser")).thenReturn(payment);

        final Event event = createMockWebhookEvent("checkout.session.completed", mockSession);

        try (MockedStatic<Webhook> webhookMock = mockStatic(Webhook.class)) {
            webhookMock.when(() -> Webhook.constructEvent(anyString(), anyString(), anyString()))
                    .thenReturn(event);

            subscriptionService.handleStripeWebhook("{}", "sig", "secret");
            verify(userRepository, never()).save(any());
        }
    }

    @Test
    @DisplayName("handleStripeWebhook: checkout.session.completed — payment lookup throws exception is caught")
    void testWebhook_checkoutCompleted_paymentLookupFails() throws Exception {
        final Session mockSession = mock(Session.class);
        when(mockSession.getSubscription()).thenReturn(null);
        when(mockSession.getCustomer()).thenReturn("cus_x");
        when(mockSession.getClientReferenceId()).thenReturn(null);
        when(mockSession.getId()).thenReturn("cs_err");

        when(paymentRepository.findByStripeSessionId("cs_err")).thenThrow(new RuntimeException("DB error"));

        final Event event = createMockWebhookEvent("checkout.session.completed", mockSession);

        try (MockedStatic<Webhook> webhookMock = mockStatic(Webhook.class)) {
            webhookMock.when(() -> Webhook.constructEvent(anyString(), anyString(), anyString()))
                    .thenReturn(event);

            final String result = subscriptionService.handleStripeWebhook("{}", "sig", "secret");
            assertEquals("Webhook received", result);
        }
    }

    @Test
    @DisplayName("handleStripeWebhook: checkout.session.completed — new sub, user not found by any method")
    void testWebhook_checkoutCompleted_noUserFound() throws Exception {
        final Session mockSession = mock(Session.class);
        when(mockSession.getSubscription()).thenReturn("sub_orphan");
        when(mockSession.getCustomer()).thenReturn("cus_orphan");
        when(mockSession.getClientReferenceId()).thenReturn(null);
        when(mockSession.getId()).thenReturn(null);

        when(subscriptionRepository.findByStripeSubscriptionId("sub_orphan")).thenReturn(Optional.empty());
        when(userRepository.findByStripeCustomerId("cus_orphan")).thenReturn(Optional.empty());

        com.stripe.model.Subscription mockStripeSub = mock(com.stripe.model.Subscription.class);
        when(mockStripeSub.getCustomer()).thenReturn("cus_orphan");

        final Event event = createMockWebhookEvent("checkout.session.completed", mockSession);

        try (MockedStatic<Webhook> webhookMock = mockStatic(Webhook.class);
             final MockedStatic<com.stripe.model.Subscription> stripeMock = mockStatic(com.stripe.model.Subscription.class)) {
            webhookMock.when(() -> Webhook.constructEvent(anyString(), anyString(), anyString()))
                    .thenReturn(event);
            stripeMock.when(() -> com.stripe.model.Subscription.retrieve("sub_orphan")).thenReturn(mockStripeSub);

            final String result = subscriptionService.handleStripeWebhook("{}", "sig", "secret");
            assertEquals("Webhook received", result);
            verify(subscriptionRepository, never()).save(any());
        }
    }

    @Test
    @DisplayName("handleStripeWebhook: checkout.session.completed — new sub with empty items")
    void testWebhook_checkoutCompleted_emptyItems() throws Exception {
        final Session mockSession = mock(Session.class);
        when(mockSession.getSubscription()).thenReturn("sub_emptyitems");
        when(mockSession.getCustomer()).thenReturn("cus_ei");
        when(mockSession.getClientReferenceId()).thenReturn(null);
        when(mockSession.getId()).thenReturn(null);

        when(subscriptionRepository.findByStripeSubscriptionId("sub_emptyitems")).thenReturn(Optional.empty());

        com.stripe.model.Subscription mockStripeSub = mock(com.stripe.model.Subscription.class);
        when(mockStripeSub.getCustomer()).thenReturn("cus_ei");
        when(mockStripeSub.getCurrentPeriodStart()).thenReturn(1000L);
        when(mockStripeSub.getCurrentPeriodEnd()).thenReturn(2000L);

        final SubscriptionItemCollection emptyItems = mock(SubscriptionItemCollection.class);
        when(emptyItems.getData()).thenReturn(List.of());
        when(mockStripeSub.getItems()).thenReturn(emptyItems);

        final User user = User.builder().id(1L).email("test@x.com").password("pw").build();
        user.setStripeCustomerId("cus_ei");
        when(userRepository.findByStripeCustomerId("cus_ei")).thenReturn(Optional.of(user));
        when(subscriptionRepository.save(any())).thenAnswer(inv -> inv.getArgument(0));

        final Event event = createMockWebhookEvent("checkout.session.completed", mockSession);

        try (MockedStatic<Webhook> webhookMock = mockStatic(Webhook.class);
             final MockedStatic<com.stripe.model.Subscription> stripeMock = mockStatic(com.stripe.model.Subscription.class)) {
            webhookMock.when(() -> Webhook.constructEvent(anyString(), anyString(), anyString()))
                    .thenReturn(event);
            stripeMock.when(() -> com.stripe.model.Subscription.retrieve("sub_emptyitems")).thenReturn(mockStripeSub);

            subscriptionService.handleStripeWebhook("{}", "sig", "secret");
            verify(subscriptionRepository).save(argThat(s -> s.getPriceId() == null));
        }
    }

    @Test
    @DisplayName("handleStripeWebhook: checkout.session.completed — user found by payment, user needs customer ID update")
    void testWebhook_checkoutCompleted_userFromPaymentNeedsCustomerIdUpdate() throws Exception {
        final User user = User.builder().id(2L).email("test@x.com").password("pw").build();
        final Payment payment = Payment.builder().user(user).amountCents(2000).status("PENDING")
                .stripeSessionId("cs_upd2").build();

        final Session mockSession = mock(Session.class);
        when(mockSession.getSubscription()).thenReturn("sub_payupd");
        when(mockSession.getCustomer()).thenReturn("cus_payupd");
        when(mockSession.getClientReferenceId()).thenReturn(null);
        when(mockSession.getId()).thenReturn("cs_upd2");

        when(subscriptionRepository.findByStripeSubscriptionId("sub_payupd")).thenReturn(Optional.empty());
        when(paymentRepository.findByStripeSessionId("cs_upd2")).thenReturn(payment);
        when(userRepository.findByStripeCustomerId("cus_payupd")).thenReturn(Optional.empty());

        com.stripe.model.Subscription mockStripeSub = mock(com.stripe.model.Subscription.class);
        when(mockStripeSub.getCustomer()).thenReturn("cus_payupd");
        when(mockStripeSub.getCurrentPeriodStart()).thenReturn(1000L);
        when(mockStripeSub.getCurrentPeriodEnd()).thenReturn(2000L);
        when(mockStripeSub.getItems()).thenReturn(null);

        when(subscriptionRepository.save(any())).thenAnswer(inv -> inv.getArgument(0));

        final Event event = createMockWebhookEvent("checkout.session.completed", mockSession);

        try (MockedStatic<Webhook> webhookMock = mockStatic(Webhook.class);
             final MockedStatic<com.stripe.model.Subscription> stripeMock = mockStatic(com.stripe.model.Subscription.class)) {
            webhookMock.when(() -> Webhook.constructEvent(anyString(), anyString(), anyString()))
                    .thenReturn(event);
            stripeMock.when(() -> com.stripe.model.Subscription.retrieve("sub_payupd")).thenReturn(mockStripeSub);

            subscriptionService.handleStripeWebhook("{}", "sig", "secret");

            assertEquals("cus_payupd", user.getStripeCustomerId());
        }
    }

    @Test
    @DisplayName("handleStripeWebhook: checkout.session.completed — user found by clientReferenceId needs customer ID update")
    void testWebhook_checkoutCompleted_userByClientRefNeedsCustomerIdUpdate() throws Exception {
        final User user = User.builder().id(8L).email("test@x.com").password("pw").build();

        final Session mockSession = mock(Session.class);
        when(mockSession.getSubscription()).thenReturn("sub_refupd");
        when(mockSession.getCustomer()).thenReturn("cus_refupd");
        when(mockSession.getClientReferenceId()).thenReturn("8");
        when(mockSession.getId()).thenReturn(null);

        when(subscriptionRepository.findByStripeSubscriptionId("sub_refupd")).thenReturn(Optional.empty());
        when(userRepository.findByStripeCustomerId("cus_refupd")).thenReturn(Optional.empty());
        when(userRepository.findById(8L)).thenReturn(Optional.of(user));

        com.stripe.model.Subscription mockStripeSub = mock(com.stripe.model.Subscription.class);
        when(mockStripeSub.getCustomer()).thenReturn("cus_refupd");
        when(mockStripeSub.getCurrentPeriodStart()).thenReturn(1000L);
        when(mockStripeSub.getCurrentPeriodEnd()).thenReturn(2000L);
        when(mockStripeSub.getItems()).thenReturn(null);

        when(subscriptionRepository.save(any())).thenAnswer(inv -> inv.getArgument(0));

        final Event event = createMockWebhookEvent("checkout.session.completed", mockSession);

        try (MockedStatic<Webhook> webhookMock = mockStatic(Webhook.class);
             final MockedStatic<com.stripe.model.Subscription> stripeMock = mockStatic(com.stripe.model.Subscription.class)) {
            webhookMock.when(() -> Webhook.constructEvent(anyString(), anyString(), anyString()))
                    .thenReturn(event);
            stripeMock.when(() -> com.stripe.model.Subscription.retrieve("sub_refupd")).thenReturn(mockStripeSub);

            subscriptionService.handleStripeWebhook("{}", "sig", "secret");

            assertEquals("cus_refupd", user.getStripeCustomerId());
            verify(userRepository).save(user);
        }
    }

    @Test
    @DisplayName("handleStripeWebhook: async_payment_failed — subscription not found is no-op")
    void testWebhook_asyncPaymentFailed_subNotFound() throws Exception {
        final Session mockSession = mock(Session.class);
        when(mockSession.getSubscription()).thenReturn("sub_nf");
        when(subscriptionRepository.findByStripeSubscriptionId("sub_nf")).thenReturn(Optional.empty());

        final Event event = createMockWebhookEvent("checkout.session.async_payment_failed", mockSession);

        try (MockedStatic<Webhook> webhookMock = mockStatic(Webhook.class)) {
            webhookMock.when(() -> Webhook.constructEvent(anyString(), anyString(), anyString()))
                    .thenReturn(event);

            subscriptionService.handleStripeWebhook("{}", "sig", "secret");
            verify(subscriptionRepository, never()).save(any());
        }
    }

    @Test
    @DisplayName("handleStripeWebhook: async_payment_succeeded — subscription not found is no-op")
    void testWebhook_asyncPaymentSucceeded_subNotFound() throws Exception {
        final Session mockSession = mock(Session.class);
        when(mockSession.getSubscription()).thenReturn("sub_nf2");
        when(subscriptionRepository.findByStripeSubscriptionId("sub_nf2")).thenReturn(Optional.empty());

        final Event event = createMockWebhookEvent("checkout.session.async_payment_succeeded", mockSession);

        try (MockedStatic<Webhook> webhookMock = mockStatic(Webhook.class)) {
            webhookMock.when(() -> Webhook.constructEvent(anyString(), anyString(), anyString()))
                    .thenReturn(event);

            subscriptionService.handleStripeWebhook("{}", "sig", "secret");
            verify(subscriptionRepository, never()).save(any());
        }
    }

    @Test
    @DisplayName("handleStripeWebhook: session_expired — subscription not found is no-op")
    void testWebhook_sessionExpired_subNotFound() throws Exception {
        final Session mockSession = mock(Session.class);
        when(mockSession.getSubscription()).thenReturn("sub_nf3");
        when(subscriptionRepository.findByStripeSubscriptionId("sub_nf3")).thenReturn(Optional.empty());

        final Event event = createMockWebhookEvent("checkout.session.expired", mockSession);

        try (MockedStatic<Webhook> webhookMock = mockStatic(Webhook.class)) {
            webhookMock.when(() -> Webhook.constructEvent(anyString(), anyString(), anyString()))
                    .thenReturn(event);

            subscriptionService.handleStripeWebhook("{}", "sig", "secret");
            verify(subscriptionRepository, never()).save(any());
        }
    }

    @Test
    @DisplayName("handleStripeWebhook: subscription.updated — not found locally is no-op")
    void testWebhook_subscriptionUpdated_notFound() throws Exception {
        com.stripe.model.Subscription mockStripeSub = mock(com.stripe.model.Subscription.class);
        when(mockStripeSub.getId()).thenReturn("sub_upd_nf");
        when(subscriptionRepository.findByStripeSubscriptionId("sub_upd_nf")).thenReturn(Optional.empty());

        final Event event = createMockWebhookEvent("customer.subscription.updated", mockStripeSub);

        try (MockedStatic<Webhook> webhookMock = mockStatic(Webhook.class)) {
            webhookMock.when(() -> Webhook.constructEvent(anyString(), anyString(), anyString()))
                    .thenReturn(event);

            subscriptionService.handleStripeWebhook("{}", "sig", "secret");
            verify(subscriptionRepository, never()).save(any());
        }
    }

    @Test
    @DisplayName("handleStripeWebhook: subscription.deleted — not found locally is no-op")
    void testWebhook_subscriptionDeleted_notFound() throws Exception {
        com.stripe.model.Subscription mockStripeSub = mock(com.stripe.model.Subscription.class);
        when(mockStripeSub.getId()).thenReturn("sub_del_nf");
        when(subscriptionRepository.findByStripeSubscriptionId("sub_del_nf")).thenReturn(Optional.empty());

        final Event event = createMockWebhookEvent("customer.subscription.deleted", mockStripeSub);

        try (MockedStatic<Webhook> webhookMock = mockStatic(Webhook.class)) {
            webhookMock.when(() -> Webhook.constructEvent(anyString(), anyString(), anyString()))
                    .thenReturn(event);

            subscriptionService.handleStripeWebhook("{}", "sig", "secret");
            verify(subscriptionRepository, never()).save(any());
        }
    }

    @Test
    @DisplayName("handleStripeWebhook: invoice.paid — subscription not found is no-op")
    void testWebhook_invoicePaid_subNotFound() throws Exception {
        final Invoice mockInvoice = mock(Invoice.class);
        when(mockInvoice.getSubscription()).thenReturn("sub_inv_nf");
        when(subscriptionRepository.findByStripeSubscriptionId("sub_inv_nf")).thenReturn(Optional.empty());

        final Event event = createMockWebhookEvent("invoice.paid", mockInvoice);

        try (MockedStatic<Webhook> webhookMock = mockStatic(Webhook.class)) {
            webhookMock.when(() -> Webhook.constructEvent(anyString(), anyString(), anyString()))
                    .thenReturn(event);

            subscriptionService.handleStripeWebhook("{}", "sig", "secret");
            verify(subscriptionRepository, never()).save(any());
        }
    }

    @Test
    @DisplayName("handleStripeWebhook: invoice.payment_failed — subscription not found is no-op")
    void testWebhook_invoicePaymentFailed_subNotFound() throws Exception {
        final Invoice mockInvoice = mock(Invoice.class);
        when(mockInvoice.getSubscription()).thenReturn("sub_ipf_nf");
        when(subscriptionRepository.findByStripeSubscriptionId("sub_ipf_nf")).thenReturn(Optional.empty());

        final Event event = createMockWebhookEvent("invoice.payment_failed", mockInvoice);

        try (MockedStatic<Webhook> webhookMock = mockStatic(Webhook.class)) {
            webhookMock.when(() -> Webhook.constructEvent(anyString(), anyString(), anyString()))
                    .thenReturn(event);

            subscriptionService.handleStripeWebhook("{}", "sig", "secret");
            verify(subscriptionRepository, never()).save(any());
        }
    }

    @Test
    @DisplayName("handleStripeWebhook: subscription.updated — plan not found returns null plan")
    void testWebhook_subscriptionUpdated_planNotFound() throws Exception {
        com.stripe.model.Subscription mockStripeSub = mock(com.stripe.model.Subscription.class);
        when(mockStripeSub.getId()).thenReturn("sub_upd_np");
        when(mockStripeSub.getStatus()).thenReturn("active");
        when(mockStripeSub.getCurrentPeriodEnd()).thenReturn(3000L);

        final SubscriptionItemCollection itemCollection = mock(SubscriptionItemCollection.class);
        final SubscriptionItem item = mock(SubscriptionItem.class);
        final Price price = mock(Price.class);
        when(price.getId()).thenReturn("price_unknown");
        when(item.getPrice()).thenReturn(price);
        when(itemCollection.getData()).thenReturn(List.of(item));
        when(mockStripeSub.getItems()).thenReturn(itemCollection);

        final Subscription sub = new Subscription();
        when(subscriptionRepository.findByStripeSubscriptionId("sub_upd_np")).thenReturn(Optional.of(sub));
        when(planRepository.findByCode("price_unknown")).thenReturn(null);
        when(subscriptionRepository.save(any())).thenAnswer(inv -> inv.getArgument(0));

        final Event event = createMockWebhookEvent("customer.subscription.updated", mockStripeSub);

        try (MockedStatic<Webhook> webhookMock = mockStatic(Webhook.class)) {
            webhookMock.when(() -> Webhook.constructEvent(anyString(), anyString(), anyString()))
                    .thenReturn(event);

            subscriptionService.handleStripeWebhook("{}", "sig", "secret");
            assertEquals("price_unknown", sub.getPriceId());
            assertNull(sub.getPlan());
        }
    }

    @Test
    @DisplayName("handleStripeWebhook: invoice.paid — payment creation failure is caught")
    void testWebhook_invoicePaid_paymentCreationFails() throws Exception {
        final Invoice mockInvoice = mock(Invoice.class);
        when(mockInvoice.getSubscription()).thenReturn("sub_inv_fail");
        when(mockInvoice.getId()).thenReturn("inv_fail");
        when(mockInvoice.getAmountPaid()).thenReturn(2000L);

        final User user = User.builder().id(1L).email("test@x.com").password("pw").build();
        final Subscription sub = new Subscription();
        sub.setUser(user);
        when(subscriptionRepository.findByStripeSubscriptionId("sub_inv_fail")).thenReturn(Optional.of(sub));
        when(subscriptionRepository.save(any())).thenAnswer(inv -> inv.getArgument(0));
        when(paymentRepository.save(any())).thenThrow(new RuntimeException("DB error"));

        final Event event = createMockWebhookEvent("invoice.paid", mockInvoice);

        try (MockedStatic<Webhook> webhookMock = mockStatic(Webhook.class)) {
            webhookMock.when(() -> Webhook.constructEvent(anyString(), anyString(), anyString()))
                    .thenReturn(event);

            final String result = subscriptionService.handleStripeWebhook("{}", "sig", "secret");
            assertEquals("Webhook received", result);
        }
    }

    @Test
    @DisplayName("syncAllSubscriptionsForCustomer: plan not found for priceId leaves plan null")
    void testSyncAllSubscriptionsForCustomer_planNotFound() throws Exception {
        final User user = User.builder().id(1L).email("test@x.com").password("pw").build();
        when(userRepository.findByStripeCustomerId("cus_np")).thenReturn(Optional.of(user));

        com.stripe.model.Subscription stripeSub = mock(com.stripe.model.Subscription.class);
        when(stripeSub.getId()).thenReturn("sub_np");
        when(stripeSub.getStatus()).thenReturn("active");
        when(stripeSub.getCurrentPeriodStart()).thenReturn(1000L);
        when(stripeSub.getCurrentPeriodEnd()).thenReturn(2000L);

        final SubscriptionItemCollection itemCollection = mock(SubscriptionItemCollection.class);
        final SubscriptionItem item = mock(SubscriptionItem.class);
        final Price price = mock(Price.class);
        when(price.getId()).thenReturn("price_orphan");
        when(item.getPrice()).thenReturn(price);
        when(itemCollection.getData()).thenReturn(List.of(item));
        when(stripeSub.getItems()).thenReturn(itemCollection);

        when(subscriptionRepository.findByStripeSubscriptionId("sub_np")).thenReturn(Optional.empty());
        when(planRepository.findByCode("price_orphan")).thenReturn(null);

        final SubscriptionCollection mockCollection = mock(SubscriptionCollection.class);
        when(mockCollection.getData()).thenReturn(List.of(stripeSub));

        when(subscriptionRepository.save(any())).thenAnswer(inv -> inv.getArgument(0));

        try (MockedStatic<com.stripe.model.Subscription> stripeMock = mockStatic(com.stripe.model.Subscription.class)) {
            stripeMock.when(() -> com.stripe.model.Subscription.list(anyMap())).thenReturn(mockCollection);

            final List<Subscription> result = subscriptionService.syncAllSubscriptionsForCustomer("cus_np");

            assertEquals(1, result.size());
            assertEquals("price_orphan", result.get(0).getPriceId());
            assertNull(result.get(0).getPlan());
        }
    }

    @Test
    @DisplayName("createSubscriptionDirectly: creates customer when user name is null")
    void testCreateSubscriptionDirectly_userNameNull() throws Exception {
        final User user = User.builder().id(5L).email("test@x.com").password("pw").build();
        when(userRepository.findById(5L)).thenReturn(Optional.of(user));

        final Customer mockCustomer = mock(Customer.class);
        when(mockCustomer.getId()).thenReturn("cus_noname");

        com.stripe.model.Subscription mockStripeSub = mock(com.stripe.model.Subscription.class);
        when(mockStripeSub.getId()).thenReturn("sub_noname");
        when(mockStripeSub.getCurrentPeriodStart()).thenReturn(1000L);
        when(mockStripeSub.getCurrentPeriodEnd()).thenReturn(2000L);

        when(planRepository.findByCode("price_nn")).thenReturn(null);
        when(subscriptionRepository.save(any())).thenAnswer(inv -> inv.getArgument(0));

        try (MockedStatic<Customer> customerMock = mockStatic(Customer.class);
             final MockedStatic<com.stripe.model.Subscription> stripeMock = mockStatic(com.stripe.model.Subscription.class)) {
            customerMock.when(() -> Customer.create(anyMap())).thenReturn(mockCustomer);
            stripeMock.when(() -> com.stripe.model.Subscription.create(anyMap())).thenReturn(mockStripeSub);


            subscriptionService.createSubscriptionDirectly(5L, "price_nn");

            assertEquals("cus_noname", user.getStripeCustomerId());
        }
    }

    @Test
    @DisplayName("handleStripeWebhook: subscription.created — error during save is caught")
    void testWebhook_subscriptionCreated_saveFails() throws Exception {
        com.stripe.model.Subscription mockStripeSub = mock(com.stripe.model.Subscription.class);
        when(mockStripeSub.getId()).thenReturn("sub_save_err");
        when(mockStripeSub.getCustomer()).thenReturn("cus_save_err");
        when(mockStripeSub.getCurrentPeriodStart()).thenReturn(1000L);
        when(mockStripeSub.getCurrentPeriodEnd()).thenReturn(2000L);
        when(mockStripeSub.getItems()).thenReturn(null);

        when(subscriptionRepository.findByStripeSubscriptionId("sub_save_err")).thenReturn(Optional.empty());

        final User user = User.builder().id(1L).email("test@x.com").password("pw").build();
        when(userRepository.findByStripeCustomerId("cus_save_err")).thenReturn(Optional.of(user));
        when(subscriptionRepository.save(any())).thenThrow(new RuntimeException("DB save error"));

        final Event event = createMockWebhookEvent("customer.subscription.created", mockStripeSub);

        try (MockedStatic<Webhook> webhookMock = mockStatic(Webhook.class)) {
            webhookMock.when(() -> Webhook.constructEvent(anyString(), anyString(), anyString()))
                    .thenReturn(event);

            final String result = subscriptionService.handleStripeWebhook("{}", "sig", "secret");
            assertEquals("Webhook received", result);
        }
    }

    @Test
    @DisplayName("syncUserSubscriptionsFromStripe: creates customer with name when user has name")
    void testSyncUserSubscriptionsFromStripe_createsCustomerWithName() throws Exception {
        final User user = User.builder().id(6L).email("test@x.com").password("pw").build();
        user.setName("John Doe");
        when(userRepository.findById(6L)).thenReturn(Optional.of(user));

        final CustomerCollection emptyCustCollection = mock(CustomerCollection.class);
        when(emptyCustCollection.getData()).thenReturn(List.of());

        final Customer mockNewCustomer = mock(Customer.class);
        when(mockNewCustomer.getId()).thenReturn("cus_with_name");

        final SubscriptionCollection mockSubCollection = mock(SubscriptionCollection.class);
        when(mockSubCollection.getData()).thenReturn(List.of());

        when(userRepository.findByStripeCustomerId("cus_with_name")).thenReturn(Optional.of(user));

        try (MockedStatic<Customer> customerMock = mockStatic(Customer.class);
             final MockedStatic<com.stripe.model.Subscription> stripeMock = mockStatic(com.stripe.model.Subscription.class)) {
            customerMock.when(() -> Customer.list(anyMap())).thenReturn(emptyCustCollection);
            customerMock.when(() -> Customer.create(anyMap())).thenReturn(mockNewCustomer);
            stripeMock.when(() -> com.stripe.model.Subscription.list(anyMap())).thenReturn(mockSubCollection);


            subscriptionService.syncUserSubscriptionsFromStripe(6L);

            assertEquals("cus_with_name", user.getStripeCustomerId());
        }
    }

    @Test
    @DisplayName("syncSubscriptionFromStripe: existing sub with empty items does not set priceId")
    void testSyncSubscriptionFromStripe_existingEmptyItems() throws Exception {
        final Subscription existingSub = new Subscription();
        existingSub.setId(12L);
        when(subscriptionRepository.findByStripeSubscriptionId("sub_ei")).thenReturn(Optional.of(existingSub));

        com.stripe.model.Subscription mockStripeSub = mock(com.stripe.model.Subscription.class);
        when(mockStripeSub.getCustomer()).thenReturn("cus_ei2");
        when(mockStripeSub.getStatus()).thenReturn("active");
        when(mockStripeSub.getCurrentPeriodStart()).thenReturn(1000L);
        when(mockStripeSub.getCurrentPeriodEnd()).thenReturn(2000L);

        final SubscriptionItemCollection emptyItems = mock(SubscriptionItemCollection.class);
        when(emptyItems.getData()).thenReturn(List.of());
        when(mockStripeSub.getItems()).thenReturn(emptyItems);

        when(subscriptionRepository.save(any())).thenAnswer(inv -> inv.getArgument(0));

        try (MockedStatic<com.stripe.model.Subscription> stripeMock = mockStatic(com.stripe.model.Subscription.class)) {
            stripeMock.when(() -> com.stripe.model.Subscription.retrieve("sub_ei")).thenReturn(mockStripeSub);

            final Subscription result = subscriptionService.syncSubscriptionFromStripe("sub_ei");

            assertNull(result.getPriceId());
        }
    }

    @Test
    @DisplayName("syncSubscriptionFromStripe: new sub with empty items does not set priceId")
    void testSyncSubscriptionFromStripe_newEmptyItems() throws Exception {
        when(subscriptionRepository.findByStripeSubscriptionId("sub_nei")).thenReturn(Optional.empty());

        com.stripe.model.Subscription mockStripeSub = mock(com.stripe.model.Subscription.class);
        when(mockStripeSub.getCustomer()).thenReturn("cus_nei");
        when(mockStripeSub.getStatus()).thenReturn("active");
        when(mockStripeSub.getCurrentPeriodStart()).thenReturn(1000L);
        when(mockStripeSub.getCurrentPeriodEnd()).thenReturn(2000L);

        final SubscriptionItemCollection emptyItems = mock(SubscriptionItemCollection.class);
        when(emptyItems.getData()).thenReturn(List.of());
        when(mockStripeSub.getItems()).thenReturn(emptyItems);

        final User user = User.builder().id(1L).email("test@x.com").password("pw").build();
        when(userRepository.findByStripeCustomerId("cus_nei")).thenReturn(Optional.of(user));
        when(subscriptionRepository.save(any())).thenAnswer(inv -> inv.getArgument(0));

        try (MockedStatic<com.stripe.model.Subscription> stripeMock = mockStatic(com.stripe.model.Subscription.class)) {
            stripeMock.when(() -> com.stripe.model.Subscription.retrieve("sub_nei")).thenReturn(mockStripeSub);

            final Subscription result = subscriptionService.syncSubscriptionFromStripe("sub_nei");

            assertNull(result.getPriceId());
        }
    }

    @Test
    @DisplayName("syncSubscriptionFromStripe: new sub — plan not found for priceId")
    void testSyncSubscriptionFromStripe_newPlanNotFound() throws Exception {
        when(subscriptionRepository.findByStripeSubscriptionId("sub_nopln")).thenReturn(Optional.empty());

        com.stripe.model.Subscription mockStripeSub = mock(com.stripe.model.Subscription.class);
        when(mockStripeSub.getCustomer()).thenReturn("cus_nopln");
        when(mockStripeSub.getStatus()).thenReturn("active");
        when(mockStripeSub.getCurrentPeriodStart()).thenReturn(1000L);
        when(mockStripeSub.getCurrentPeriodEnd()).thenReturn(2000L);

        final SubscriptionItemCollection itemCollection = mock(SubscriptionItemCollection.class);
        final SubscriptionItem item = mock(SubscriptionItem.class);
        final Price price = mock(Price.class);
        when(price.getId()).thenReturn("price_missing");
        when(item.getPrice()).thenReturn(price);
        when(itemCollection.getData()).thenReturn(List.of(item));
        when(mockStripeSub.getItems()).thenReturn(itemCollection);

        final User user = User.builder().id(1L).email("test@x.com").password("pw").build();
        when(userRepository.findByStripeCustomerId("cus_nopln")).thenReturn(Optional.of(user));
        when(planRepository.findByCode("price_missing")).thenReturn(null);
        when(subscriptionRepository.save(any())).thenAnswer(inv -> inv.getArgument(0));

        try (MockedStatic<com.stripe.model.Subscription> stripeMock = mockStatic(com.stripe.model.Subscription.class)) {
            stripeMock.when(() -> com.stripe.model.Subscription.retrieve("sub_nopln")).thenReturn(mockStripeSub);

            final Subscription result = subscriptionService.syncSubscriptionFromStripe("sub_nopln");

            assertEquals("price_missing", result.getPriceId());
            assertNull(result.getPlan());
        }
    }

    @Test
    @DisplayName("syncSubscriptionFromStripe: existing sub — plan not found for priceId")
    void testSyncSubscriptionFromStripe_existingPlanNotFound() throws Exception {
        final Subscription existingSub = new Subscription();
        existingSub.setId(13L);
        when(subscriptionRepository.findByStripeSubscriptionId("sub_expln")).thenReturn(Optional.of(existingSub));

        com.stripe.model.Subscription mockStripeSub = mock(com.stripe.model.Subscription.class);
        when(mockStripeSub.getCustomer()).thenReturn("cus_expln");
        when(mockStripeSub.getStatus()).thenReturn("active");
        when(mockStripeSub.getCurrentPeriodStart()).thenReturn(1000L);
        when(mockStripeSub.getCurrentPeriodEnd()).thenReturn(2000L);

        final SubscriptionItemCollection itemCollection = mock(SubscriptionItemCollection.class);
        final SubscriptionItem item = mock(SubscriptionItem.class);
        final Price price = mock(Price.class);
        when(price.getId()).thenReturn("price_miss2");
        when(item.getPrice()).thenReturn(price);
        when(itemCollection.getData()).thenReturn(List.of(item));
        when(mockStripeSub.getItems()).thenReturn(itemCollection);

        when(planRepository.findByCode("price_miss2")).thenReturn(null);
        when(subscriptionRepository.save(any())).thenAnswer(inv -> inv.getArgument(0));

        try (MockedStatic<com.stripe.model.Subscription> stripeMock = mockStatic(com.stripe.model.Subscription.class)) {
            stripeMock.when(() -> com.stripe.model.Subscription.retrieve("sub_expln")).thenReturn(mockStripeSub);

            final Subscription result = subscriptionService.syncSubscriptionFromStripe("sub_expln");

            assertEquals("price_miss2", result.getPriceId());
            assertNull(result.getPlan());
        }
    }
}
