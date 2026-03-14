package com.careconnect.service;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.ArgumentMatchers.*;
import static org.mockito.Mockito.*;

import com.careconnect.dto.SubscriptionResponseDTO;
import com.careconnect.model.Plan;
import com.careconnect.model.Subscription;
import com.careconnect.model.User;
import com.careconnect.repository.PlanRepository;
import com.careconnect.repository.SubscriptionRepository;
import com.careconnect.repository.UserRepository;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.mockito.Mock;
import org.mockito.MockitoAnnotations;
import org.springframework.test.util.ReflectionTestUtils;

import java.util.Collections;
import java.util.List;
import java.util.Optional;

/**
 * Unit tests for {@link SubscriptionEnrichmentService}.
 *
 * <p>All external dependencies (repositories and {@link StripeService}) are mocked with
 * Mockito, so no database or live Stripe API is needed. The service is instantiated
 * directly via its constructor, bypassing Spring's {@code @Value} injection by supplying
 * test-specific price IDs.</p>
 *
 * <p>Tests are grouped by the public method they exercise:</p>
 * <ul>
 *   <li>{@link SubscriptionEnrichmentService#getEnrichedUserSubscriptions}</li>
 *   <li>{@link SubscriptionEnrichmentService#createMissingPlanMappings}</li>
 *   <li>{@link SubscriptionEnrichmentService#getEnrichedActiveUserSubscriptions}</li>
 *   <li>{@link SubscriptionEnrichmentService#enrichSubscriptions}</li>
 * </ul>
 */
class SubscriptionEnrichmentServiceTest {

    // ─── Mocked dependencies ──────────────────────────────────────────────────

    @Mock
    private SubscriptionRepository subscriptionRepository;

    @Mock
    private UserRepository userRepository;

    @Mock
    private PlanRepository planRepository;

    @Mock
    private StripeService stripeService;

    // The service under test — constructed manually to supply @Value parameters
    private SubscriptionEnrichmentService service;

    // Stable test price IDs injected at construction time
    private static final String PREMIUM_PRICE_ID  = "price_premium_test";
    private static final String STANDARD_PRICE_ID = "price_standard_test";

    // ─── Setup ────────────────────────────────────────────────────────────────

    @BeforeEach
    void setUp() throws Exception {
        MockitoAnnotations.openMocks(this);

        // Build service with known price IDs instead of Spring @Value defaults
        service = new SubscriptionEnrichmentService(
                subscriptionRepository,
                userRepository,
                planRepository,
                PREMIUM_PRICE_ID,
                STANDARD_PRICE_ID);

        // Inject the optional StripeService (@Autowired(required=false)) via reflection
        ReflectionTestUtils.setField(service, "stripeService", stripeService);

        // ---- Safe lenient defaults ----
        // Prevents NullPointerExceptions in code paths not under test
        lenient().when(planRepository.findByCode(any())).thenReturn(null);
        lenient().when(planRepository.findByName(any())).thenReturn(Collections.emptyList());
        lenient().when(planRepository.findAll()).thenReturn(Collections.emptyList());
        lenient().when(subscriptionRepository.save(any(Subscription.class)))
                .thenAnswer(inv -> inv.getArgument(0));
    }

    // ─── Object-building helpers ──────────────────────────────────────────────

    /** Creates a User with the given ID and optional Stripe customer ID. */
    private User buildUser(Long id, String stripeCustomerId) {
        final User user = User.builder()
                .id(id)
                .email("user" + id + "@example.com")
                .password("pw")
                .build();
        user.setStripeCustomerId(stripeCustomerId);
        return user;
    }

    /** Creates a Subscription with the given fields. */
    private Subscription buildSubscription(
            Long id, User user, String stripeSubId, String status, String priceId) {
        final Subscription sub = new Subscription();
        sub.setId(id);
        sub.setUser(user);
        sub.setStripeSubscriptionId(stripeSubId);
        sub.setStatus(status);
        sub.setPriceId(priceId);
        return sub;
    }

    /** Creates a Plan with the given fields. */
    private Plan buildPlan(Long id, String code, String name, Integer priceCents) {
        final Plan plan = new Plan();
        plan.setId(id);
        plan.setCode(code);
        plan.setName(name);
        plan.setPriceCents(priceCents);
        plan.setBillingPeriod("MONTH");
        plan.setIsActive(true);
        return plan;
    }

    // ==========================================================================
    // getEnrichedUserSubscriptions
    // ==========================================================================

    @Test
    @DisplayName("getEnrichedUserSubscriptions: throws IllegalArgumentException when user does not exist")
    void testGetEnrichedUserSubscriptions_userNotFound() throws Exception {
        // A request for a nonexistent user must fail immediately so the caller can
        // return a 4xx response. No further processing should occur.
        when(userRepository.findById(99L)).thenReturn(Optional.empty());

        assertThrows(
                IllegalArgumentException.class,
                () -> service.getEnrichedUserSubscriptions(99L));

        verify(stripeService, never()).getCustomerActiveSubscriptions(any());
    }

    @Test
    @DisplayName("getEnrichedUserSubscriptions: skips Stripe when user has no Stripe customer ID")
    void testGetEnrichedUserSubscriptions_noStripeCustomerId_skipsStripe() throws Exception {
        // Without a Stripe customer ID there is nothing to look up in Stripe.
        // The service must enrich using only the local database records.
        final User user = buildUser(1L, null);
        final Subscription sub = buildSubscription(1L, user, null, "ACTIVE", null);

        when(userRepository.findById(1L)).thenReturn(Optional.of(user));
        when(subscriptionRepository.findByUser(user)).thenReturn(List.of(sub));

        final List<SubscriptionResponseDTO> result = service.getEnrichedUserSubscriptions(1L);

        assertNotNull(result);
        assertEquals(1, result.size());
        // Confirm no Stripe API call was attempted
        verify(stripeService, never()).getCustomerActiveSubscriptions(any());
    }

    @Test
    @DisplayName("getEnrichedUserSubscriptions: updates DB subscription to ACTIVE when Stripe returns a different active sub")
    void testGetEnrichedUserSubscriptions_inactiveDbSub_updatesFromStripe() throws Exception {
        // When Stripe reports an active subscription but the DB record is CANCELLED or
        // has a different Stripe ID, the service must update the local record and save it.
        final User user = buildUser(2L, "cus_test");
        final Subscription sub = buildSubscription(2L, user, "sub_old", "CANCELLED", PREMIUM_PRICE_ID);

        final String activeSubId = "sub_active_123";

        // Stripe's active-subscription list returns a different sub ID
        when(stripeService.getCustomerActiveSubscriptions("cus_test"))
                .thenReturn("{\"data\":[{\"id\":\"" + activeSubId + "\"}]}");

        // Details of the active Stripe subscription (used for the DB update and enrichment)
        final String stripeSubJson = "{\"status\":\"active\"," +
                "\"items\":{\"data\":[{\"price\":{\"id\":\"" + PREMIUM_PRICE_ID + "\"}}]}," +
                "\"current_period_start\":1700000000," +
                "\"current_period_end\":1702592000}";
        when(stripeService.getSubscription(activeSubId)).thenReturn(stripeSubJson);

        final Plan premiumPlan = buildPlan(1L, PREMIUM_PRICE_ID, "Premium Plan", 3000);
        when(userRepository.findById(2L)).thenReturn(Optional.of(user));
        when(subscriptionRepository.findByUser(user)).thenReturn(List.of(sub));
        when(planRepository.findByName("Premium Plan")).thenReturn(List.of(premiumPlan));
        when(planRepository.findByCode(PREMIUM_PRICE_ID)).thenReturn(premiumPlan);
        when(planRepository.findAll()).thenReturn(List.of(premiumPlan));

        service.getEnrichedUserSubscriptions(2L);

        // The subscription record must have been persisted with the updated status
        verify(subscriptionRepository, atLeastOnce()).save(any(Subscription.class));
        assertEquals("ACTIVE", sub.getStatus());
    }

    @Test
    @DisplayName("getEnrichedUserSubscriptions: does not update DB when subscription is already ACTIVE with matching Stripe ID")
    void testGetEnrichedUserSubscriptions_alreadyActive_noUpdate() throws Exception {
        // When the DB record is ACTIVE and its Stripe subscription ID matches the one
        // Stripe considers active, no write operation should be performed.
        final String activeSubId = "sub_already_active";
        final User user = buildUser(4L, "cus_active");
        final Subscription sub = buildSubscription(4L, user, activeSubId, "ACTIVE", PREMIUM_PRICE_ID);

        when(stripeService.getCustomerActiveSubscriptions("cus_active"))
                .thenReturn("{\"data\":[{\"id\":\"" + activeSubId + "\"}]}");
        // Stub the status-check call from enrichSubscriptions
        when(stripeService.getSubscription(activeSubId)).thenReturn("{\"status\":\"active\"}");

        final Plan premiumPlan = buildPlan(1L, PREMIUM_PRICE_ID, "Premium Plan", 3000);
        when(userRepository.findById(4L)).thenReturn(Optional.of(user));
        when(subscriptionRepository.findByUser(user)).thenReturn(List.of(sub));
        when(planRepository.findByCode(PREMIUM_PRICE_ID)).thenReturn(premiumPlan);
        when(planRepository.findAll()).thenReturn(List.of(premiumPlan));

        service.getEnrichedUserSubscriptions(4L);

        // No save should occur when the record already reflects the correct active state
        verify(subscriptionRepository, never()).save(any(Subscription.class));
    }

    @Test
    @DisplayName("getEnrichedUserSubscriptions: creates a new DB subscription when Stripe is active but DB has none")
    void testGetEnrichedUserSubscriptions_noDbSub_createsFromStripe() throws Exception {
        // When Stripe reports an active subscription but no subscription record exists
        // locally for the user, the service must create and persist a new record.
        final User user = buildUser(3L, "cus_create");
        final String activeSubId = "sub_new_create";

        when(stripeService.getCustomerActiveSubscriptions("cus_create"))
                .thenReturn("{\"data\":[{\"id\":\"" + activeSubId + "\"}]}");

        final String stripeSubJson = "{\"status\":\"active\"," +
                "\"items\":{\"data\":[{\"price\":{\"id\":\"" + PREMIUM_PRICE_ID + "\"}}]}," +
                "\"current_period_start\":1700000000," +
                "\"current_period_end\":1702592000}";
        when(stripeService.getSubscription(activeSubId)).thenReturn(stripeSubJson);

        // First call returns empty (no existing subs); second call returns the new record
        final Subscription created = buildSubscription(99L, user, activeSubId, "ACTIVE", PREMIUM_PRICE_ID);
        when(subscriptionRepository.findByUser(user))
                .thenReturn(Collections.emptyList())
                .thenReturn(List.of(created));
        when(userRepository.findById(3L)).thenReturn(Optional.of(user));
        when(planRepository.findAll()).thenReturn(Collections.emptyList());

        service.getEnrichedUserSubscriptions(3L);

        // At least one save must have been called to persist the new subscription
        verify(subscriptionRepository, atLeastOnce()).save(any(Subscription.class));
    }

    // ==========================================================================
    // createMissingPlanMappings
    // ==========================================================================

    @Test
    @DisplayName("createMissingPlanMappings: throws IllegalArgumentException when user does not exist")
    void testCreateMissingPlanMappings_userNotFound() throws Exception {
        // A nonexistent user means there are no subscriptions to map; fail fast.
        when(userRepository.findById(55L)).thenReturn(Optional.empty());

        assertThrows(
                IllegalArgumentException.class,
                () -> service.createMissingPlanMappings(55L));
    }

    @Test
    @DisplayName("createMissingPlanMappings: skips subscription that already has a plan linked")
    void testCreateMissingPlanMappings_skipSubscriptionWithPlan() throws Exception {
        // If the subscription already references a Plan entity, no new mapping is needed.
        final User user = buildUser(10L, null);
        final Plan existingPlan = buildPlan(1L, PREMIUM_PRICE_ID, "Premium Plan", 3000);
        final Subscription sub = buildSubscription(10L, user, "sub_x", "ACTIVE", PREMIUM_PRICE_ID);
        sub.setPlan(existingPlan); // plan is already set

        when(userRepository.findById(10L)).thenReturn(Optional.of(user));
        when(subscriptionRepository.findByUser(user)).thenReturn(List.of(sub));
        when(planRepository.findAll()).thenReturn(List.of(existingPlan));

        service.createMissingPlanMappings(10L);

        // No new plan should be created or saved
        verify(planRepository, never()).save(any());
    }

    @Test
    @DisplayName("createMissingPlanMappings: skips subscription with null priceId")
    void testCreateMissingPlanMappings_skipSubscriptionWithNullPriceId() throws Exception {
        // A subscription without a Stripe price ID cannot be mapped to any plan.
        final User user = buildUser(11L, null);
        final Subscription sub = buildSubscription(11L, user, "sub_y", "ACTIVE", null);

        when(userRepository.findById(11L)).thenReturn(Optional.of(user));
        when(subscriptionRepository.findByUser(user)).thenReturn(List.of(sub));
        when(planRepository.findAll()).thenReturn(Collections.emptyList());

        service.createMissingPlanMappings(11L);

        verify(planRepository, never()).save(any());
    }

    @Test
    @DisplayName("createMissingPlanMappings: creates a plan mapping for a known premium price ID")
    void testCreateMissingPlanMappings_createsMapping_premiumPriceId() throws Exception {
        // A subscription whose price ID is in the premium set and has no existing mapping
        // must result in a new Plan row being saved with code = the price ID.
        final User user = buildUser(12L, null);
        final Subscription sub = buildSubscription(12L, user, "sub_z", "ACTIVE", PREMIUM_PRICE_ID);
        final Plan premiumPlan = buildPlan(1L, "premium_code", "Premium Plan", 3000);

        when(userRepository.findById(12L)).thenReturn(Optional.of(user));
        when(subscriptionRepository.findByUser(user)).thenReturn(List.of(sub));
        when(planRepository.findAll()).thenReturn(List.of(premiumPlan));
        when(planRepository.findByCode(PREMIUM_PRICE_ID)).thenReturn(null); // no mapping yet
        when(planRepository.save(any(Plan.class))).thenAnswer(inv -> {
            final Plan p = inv.getArgument(0);
            p.setId(99L);
            return p;
        });

        service.createMissingPlanMappings(12L);

        // The saved plan must use the price ID as code and inherit the premium plan name
        verify(planRepository).save(argThat(p ->
                PREMIUM_PRICE_ID.equals(p.getCode()) && "Premium Plan".equals(p.getName())));
    }

    @Test
    @DisplayName("createMissingPlanMappings: creates a plan mapping for a known standard price ID")
    void testCreateMissingPlanMappings_createsMapping_standardPriceId() throws Exception {
        // Same as above but for the standard price ID set.
        final User user = buildUser(13L, null);
        final Subscription sub = buildSubscription(13L, user, "sub_std", "ACTIVE", STANDARD_PRICE_ID);
        final Plan standardPlan = buildPlan(2L, "standard_code", "Standard Plan", 2000);

        when(userRepository.findById(13L)).thenReturn(Optional.of(user));
        when(subscriptionRepository.findByUser(user)).thenReturn(List.of(sub));
        when(planRepository.findAll()).thenReturn(List.of(standardPlan));
        when(planRepository.findByCode(STANDARD_PRICE_ID)).thenReturn(null);
        when(planRepository.save(any(Plan.class))).thenAnswer(inv -> {
            final Plan p = inv.getArgument(0);
            p.setId(100L);
            return p;
        });

        service.createMissingPlanMappings(13L);

        verify(planRepository).save(argThat(p ->
                STANDARD_PRICE_ID.equals(p.getCode()) && "Standard Plan".equals(p.getName())));
    }

    @Test
    @DisplayName("createMissingPlanMappings: skips save when a mapping for the price ID already exists in the database")
    void testCreateMissingPlanMappings_skipsIfMappingAlreadyExists() throws Exception {
        // If planRepository.findByCode already returns a plan, no duplicate should be saved.
        final User user = buildUser(14L, null);
        final Subscription sub = buildSubscription(14L, user, "sub_map", "ACTIVE", PREMIUM_PRICE_ID);
        final Plan existingMapping = buildPlan(5L, PREMIUM_PRICE_ID, "Premium Plan", 3000);

        when(userRepository.findById(14L)).thenReturn(Optional.of(user));
        when(subscriptionRepository.findByUser(user)).thenReturn(List.of(sub));
        when(planRepository.findAll()).thenReturn(List.of(existingMapping));
        when(planRepository.findByCode(PREMIUM_PRICE_ID)).thenReturn(existingMapping);

        service.createMissingPlanMappings(14L);

        verify(planRepository, never()).save(any());
    }

    @Test
    @DisplayName("createMissingPlanMappings: defaults to premium plan mapping for an unknown price ID")
    void testCreateMissingPlanMappings_unknownPriceId_defaultsToPremium() throws Exception {
        // When a price ID doesn't match either the premium or standard set,
        // the service defaults to creating a mapping pointing at the Premium Plan.
        final String unknownPriceId = "price_unknown_xyz";
        final User user = buildUser(15L, null);
        final Subscription sub = buildSubscription(15L, user, "sub_unk", "ACTIVE", unknownPriceId);
        final Plan premiumPlan = buildPlan(1L, "premium_code", "Premium Plan", 3000);

        when(userRepository.findById(15L)).thenReturn(Optional.of(user));
        when(subscriptionRepository.findByUser(user)).thenReturn(List.of(sub));
        when(planRepository.findAll()).thenReturn(List.of(premiumPlan));
        when(planRepository.findByCode(unknownPriceId)).thenReturn(null);
        when(planRepository.save(any(Plan.class))).thenAnswer(inv -> {
            final Plan p = inv.getArgument(0);
            p.setId(101L);
            return p;
        });

        service.createMissingPlanMappings(15L);

        // The saved mapping should point to the "Premium Plan" by name
        verify(planRepository).save(argThat(p ->
                unknownPriceId.equals(p.getCode()) && "Premium Plan".equals(p.getName())));
    }

    // ==========================================================================
    // getEnrichedActiveUserSubscriptions
    // ==========================================================================

    @Test
    @DisplayName("getEnrichedActiveUserSubscriptions: throws IllegalArgumentException when user does not exist")
    void testGetEnrichedActiveUserSubscriptions_userNotFound() throws Exception {
        when(userRepository.findById(77L)).thenReturn(Optional.empty());

        assertThrows(
                IllegalArgumentException.class,
                () -> service.getEnrichedActiveUserSubscriptions(77L));
    }

    @Test
    @DisplayName("getEnrichedActiveUserSubscriptions: returns only locally ACTIVE subscriptions when user has no Stripe customer ID")
    void testGetEnrichedActiveUserSubscriptions_noStripeCustomer_returnsActiveOnly() throws Exception {
        // Without a Stripe customer ID the active list comes solely from local status.
        // CANCELLED subscriptions must not appear in the result.
        final User user = buildUser(20L, null);
        final Subscription activeSub    = buildSubscription(20L, user, null, "ACTIVE",    null);
        final Subscription cancelledSub = buildSubscription(21L, user, null, "CANCELLED", null);

        when(userRepository.findById(20L)).thenReturn(Optional.of(user));
        when(subscriptionRepository.findByUser(user)).thenReturn(List.of(activeSub, cancelledSub));

        final List<SubscriptionResponseDTO> result = service.getEnrichedActiveUserSubscriptions(20L);

        assertNotNull(result);
        assertEquals(1, result.size());
        assertEquals("ACTIVE", result.get(0).getStatus());
    }

    @Test
    @DisplayName("getEnrichedActiveUserSubscriptions: promotes a subscription to ACTIVE when Stripe says it is active")
    void testGetEnrichedActiveUserSubscriptions_stripeActive_updatesSub() throws Exception {
        // A locally CANCELLED subscription that appears in Stripe's active list must
        // be promoted to ACTIVE, saved, and included in the returned results.
        final String activeSubId = "sub_stripe_active";
        final User user = buildUser(21L, "cus_upgrade");
        final Subscription sub = buildSubscription(22L, user, activeSubId, "CANCELLED", null);

        when(stripeService.getCustomerActiveSubscriptions("cus_upgrade"))
                .thenReturn("{\"data\":[{\"id\":\"" + activeSubId + "\"}]}");
        // Stub the status-check call from enrichSubscriptions
        when(stripeService.getSubscription(activeSubId)).thenReturn("{\"status\":\"active\"}");

        when(userRepository.findById(21L)).thenReturn(Optional.of(user));
        when(subscriptionRepository.findByUser(user)).thenReturn(List.of(sub));

        final List<SubscriptionResponseDTO> result = service.getEnrichedActiveUserSubscriptions(21L);

        assertNotNull(result);
        assertEquals(1, result.size());
        // Status must have been updated and persisted
        assertEquals("ACTIVE", sub.getStatus());
        verify(subscriptionRepository).save(sub);
    }

    @Test
    @DisplayName("getEnrichedActiveUserSubscriptions: returns empty list when no active subscriptions exist and Stripe customer is absent")
    void testGetEnrichedActiveUserSubscriptions_noneActive_returnsEmpty() throws Exception {
        // A user with only CANCELLED subscriptions and no Stripe customer ID must
        // receive an empty result — not null, not an exception.
        final User user = buildUser(22L, null);
        final Subscription cancelledSub = buildSubscription(23L, user, null, "CANCELLED", null);

        when(userRepository.findById(22L)).thenReturn(Optional.of(user));
        when(subscriptionRepository.findByUser(user)).thenReturn(List.of(cancelledSub));

        final List<SubscriptionResponseDTO> result = service.getEnrichedActiveUserSubscriptions(22L);

        assertNotNull(result);
        assertTrue(result.isEmpty());
    }

    // ==========================================================================
    // enrichSubscriptions
    // ==========================================================================

    @Test
    @DisplayName("enrichSubscriptions: returns empty list when given an empty input list")
    void testEnrichSubscriptions_emptyList() throws Exception {
        // Feeding an empty list must produce an empty (not null) result list.
        final List<SubscriptionResponseDTO> result = service.enrichSubscriptions(Collections.emptyList());

        assertNotNull(result);
        assertTrue(result.isEmpty());
    }

    @Test
    @DisplayName("enrichSubscriptions: builds DTO from the plan already linked to the subscription")
    void testEnrichSubscriptions_subscriptionHasPlan() throws Exception {
        // When the subscription entity already holds a Plan reference, that plan's data
        // is used directly without any additional lookup by price ID.
        final Plan plan = buildPlan(1L, PREMIUM_PRICE_ID, "Premium Plan", 3000);
        final Subscription sub = new Subscription();
        sub.setId(30L);
        sub.setStatus("ACTIVE");
        sub.setPlan(plan);
        // No Stripe subscription ID → checkAndUpdateSubscriptionStatus returns early

        when(planRepository.findAll()).thenReturn(List.of(plan));

        final List<SubscriptionResponseDTO> result = service.enrichSubscriptions(List.of(sub));

        assertEquals(1, result.size());
        assertEquals("Premium Plan", result.get(0).getPlanName());
        assertEquals(PREMIUM_PRICE_ID, result.get(0).getPlanCode());
        assertEquals(3000, result.get(0).getPriceCents());
    }

    @Test
    @DisplayName("enrichSubscriptions: resolves plan from the plans-by-code map when priceId matches a plan code")
    void testEnrichSubscriptions_resolvesPlanByCode() throws Exception {
        // When no plan is linked but the subscription's priceId exactly matches a
        // Plan's code, that plan is used to populate the DTO.
        final Plan plan = buildPlan(2L, PREMIUM_PRICE_ID, "Premium Plan", 3000);
        final Subscription sub = new Subscription();
        sub.setId(31L);
        sub.setStatus("ACTIVE");
        sub.setPriceId(PREMIUM_PRICE_ID);
        // No Stripe subscription ID → checkAndUpdateSubscriptionStatus returns early

        when(planRepository.findAll()).thenReturn(List.of(plan));

        final List<SubscriptionResponseDTO> result = service.enrichSubscriptions(List.of(sub));

        assertEquals(1, result.size());
        assertEquals(2L, result.get(0).getPlanId());
        assertEquals("Premium Plan", result.get(0).getPlanName());
        assertEquals(3000, result.get(0).getPriceCents());
    }

    @Test
    @DisplayName("enrichSubscriptions: uses the DB Premium Plan when priceId is in the premium price ID set but has no direct code match")
    void testEnrichSubscriptions_premiumPriceId_usesPremiumPlanFromDb() throws Exception {
        // The priceId is in the configured premium set but no plan has that exact code.
        // The "Premium Plan" found by name is used to fill the DTO.
        final Plan premiumPlan = buildPlan(5L, "different_code", "Premium Plan", 3000);
        final Subscription sub = new Subscription();
        sub.setId(32L);
        sub.setStatus("ACTIVE");
        sub.setPriceId(PREMIUM_PRICE_ID);

        // findAll returns a plan with a different code, so plansByCode lookup misses
        when(planRepository.findAll()).thenReturn(List.of(premiumPlan));

        final List<SubscriptionResponseDTO> result = service.enrichSubscriptions(List.of(sub));

        assertEquals(1, result.size());
        assertEquals("Premium Plan", result.get(0).getPlanName());
        assertEquals(5L, result.get(0).getPlanId());
        assertEquals(3000, result.get(0).getPriceCents());
    }

    @Test
    @DisplayName("enrichSubscriptions: falls back to hardcoded premium defaults when priceId is premium but no DB plan exists")
    void testEnrichSubscriptions_premiumPriceId_noDbPlan_usesHardcodedDefaults() throws Exception {
        // When the priceId is in the premium set but no Premium Plan row exists in the
        // database, the DTO uses hardcoded defaults: name="Premium Plan", price=$30.00.
        final Subscription sub = new Subscription();
        sub.setId(33L);
        sub.setStatus("ACTIVE");
        sub.setPriceId(PREMIUM_PRICE_ID);

        when(planRepository.findAll()).thenReturn(Collections.emptyList());

        final List<SubscriptionResponseDTO> result = service.enrichSubscriptions(List.of(sub));

        assertEquals(1, result.size());
        assertEquals("Premium Plan", result.get(0).getPlanName());
        assertEquals(3000, result.get(0).getPriceCents());
        assertNull(result.get(0).getPlanId(), "No DB plan means planId should be null");
    }

    @Test
    @DisplayName("enrichSubscriptions: uses the DB Standard Plan when priceId is in the standard price ID set")
    void testEnrichSubscriptions_standardPriceId_usesStandardPlanFromDb() throws Exception {
        // The priceId is in the standard set and there is a matching "Standard Plan" by name.
        final Plan standardPlan = buildPlan(6L, "std_code", "Standard Plan", 2000);
        final Subscription sub = new Subscription();
        sub.setId(34L);
        sub.setStatus("ACTIVE");
        sub.setPriceId(STANDARD_PRICE_ID);

        when(planRepository.findAll()).thenReturn(List.of(standardPlan));

        final List<SubscriptionResponseDTO> result = service.enrichSubscriptions(List.of(sub));

        assertEquals(1, result.size());
        assertEquals("Standard Plan", result.get(0).getPlanName());
        assertEquals(6L, result.get(0).getPlanId());
        assertEquals(2000, result.get(0).getPriceCents());
    }

    @Test
    @DisplayName("enrichSubscriptions: falls back to hardcoded standard defaults when priceId is standard but no DB plan exists")
    void testEnrichSubscriptions_standardPriceId_noDbPlan_usesHardcodedDefaults() throws Exception {
        // Same as the premium fallback but for the standard tier:
        // hardcoded name="Standard Plan", price=$20.00.
        final Subscription sub = new Subscription();
        sub.setId(35L);
        sub.setStatus("ACTIVE");
        sub.setPriceId(STANDARD_PRICE_ID);

        when(planRepository.findAll()).thenReturn(Collections.emptyList());

        final List<SubscriptionResponseDTO> result = service.enrichSubscriptions(List.of(sub));

        assertEquals(1, result.size());
        assertEquals("Standard Plan", result.get(0).getPlanName());
        assertEquals(2000, result.get(0).getPriceCents());
        assertNull(result.get(0).getPlanId());
    }

    @Test
    @DisplayName("enrichSubscriptions: defaults to Premium Plan for a price ID that is neither premium nor standard")
    void testEnrichSubscriptions_unknownPriceId_defaultsToPremiumPlan() throws Exception {
        // An unrecognised price ID falls through to the final default branch, which
        // applies the Premium Plan details when one is present in the database.
        final Plan premiumPlan = buildPlan(7L, "premium_code", "Premium Plan", 3000);
        final Subscription sub = new Subscription();
        sub.setId(36L);
        sub.setStatus("ACTIVE");
        sub.setPriceId("price_unknown_xyz");

        when(planRepository.findAll()).thenReturn(List.of(premiumPlan));

        final List<SubscriptionResponseDTO> result = service.enrichSubscriptions(List.of(sub));

        assertEquals(1, result.size());
        assertEquals("Premium Plan", result.get(0).getPlanName());
        assertEquals(7L, result.get(0).getPlanId());
    }

    @Test
    @DisplayName("enrichSubscriptions: sets subscription status to ACTIVE when Stripe reports 'active'")
    void testEnrichSubscriptions_stripeActive_statusBecomesActive() throws Exception {
        // checkAndUpdateSubscriptionStatus is called for each subscription that has a
        // Stripe ID. A 'active' Stripe status must set the subscription to ACTIVE.
        final Subscription sub = new Subscription();
        sub.setId(37L);
        sub.setStripeSubscriptionId("sub_checking");
        sub.setStatus("ACTIVE");

        when(stripeService.getSubscription("sub_checking"))
                .thenReturn("{\"status\":\"active\"}");

        service.enrichSubscriptions(List.of(sub));

        assertEquals("ACTIVE", sub.getStatus());
    }

    @Test
    @DisplayName("enrichSubscriptions: sets subscription status to ACTIVE when Stripe reports 'trialing'")
    void testEnrichSubscriptions_stripeTrialing_setsActive() throws Exception {
        // Stripe's 'trialing' status is treated as active — the subscription is in
        // good standing and should be accessible to the user.
        final Subscription sub = new Subscription();
        sub.setId(38L);
        sub.setStripeSubscriptionId("sub_trialing");
        sub.setStatus("TRIALING");

        when(stripeService.getSubscription("sub_trialing"))
                .thenReturn("{\"status\":\"trialing\"}");
        when(planRepository.findAll()).thenReturn(Collections.emptyList());

        final List<SubscriptionResponseDTO> result = service.enrichSubscriptions(List.of(sub));

        assertEquals(1, result.size());
        assertEquals("ACTIVE", sub.getStatus());
    }

    @Test
    @DisplayName("enrichSubscriptions: updates status to match Stripe when Stripe status differs from the local record")
    void testEnrichSubscriptions_stripeStatusMismatch_updatesStatus() throws Exception {
        // When Stripe reports 'past_due' but the DB has 'ACTIVE', the subscription
        // object must reflect the real billing state from Stripe.
        final Subscription sub = new Subscription();
        sub.setId(39L);
        sub.setStripeSubscriptionId("sub_past_due");
        sub.setStatus("ACTIVE");

        when(stripeService.getSubscription("sub_past_due"))
                .thenReturn("{\"status\":\"past_due\"}");
        when(planRepository.findAll()).thenReturn(Collections.emptyList());

        service.enrichSubscriptions(List.of(sub));

        assertEquals("PAST_DUE", sub.getStatus());
    }

    @Test
    @DisplayName("enrichSubscriptions: keeps existing status when Stripe returns no status field")
    void testEnrichSubscriptions_stripeNoStatusField_keepsLocalStatus() throws Exception {
        // If the Stripe response is missing the 'status' field the service must not
        // overwrite the local status — it just continues with what it has.
        final Subscription sub = new Subscription();
        sub.setId(40L);
        sub.setStripeSubscriptionId("sub_no_status");
        sub.setStatus("ACTIVE");

        when(stripeService.getSubscription("sub_no_status"))
                .thenReturn("{\"id\":\"sub_no_status\"}");  // no 'status' field
        when(planRepository.findAll()).thenReturn(Collections.emptyList());

        service.enrichSubscriptions(List.of(sub));

        // Status must remain unchanged
        assertEquals("ACTIVE", sub.getStatus());
    }

    @Test
    @DisplayName("enrichSubscriptions: skips Stripe status check when subscription has no Stripe subscription ID")
    void testEnrichSubscriptions_noStripeId_skipsStatusCheck() throws Exception {
        // If stripeSubscriptionId is null, the service has nothing to query Stripe with
        // and must return early from the status check without making any API calls.
        final Subscription sub = new Subscription();
        sub.setId(41L);
        sub.setStripeSubscriptionId(null);
        sub.setStatus("ACTIVE");

        when(planRepository.findAll()).thenReturn(Collections.emptyList());

        service.enrichSubscriptions(List.of(sub));

        verify(stripeService, never()).getSubscription(any());
        assertEquals("ACTIVE", sub.getStatus());
    }

    @Test
    @DisplayName("enrichSubscriptions: gracefully handles an exception from the Stripe status check")
    void testEnrichSubscriptions_stripeException_keepsLocalStatus() throws Exception {
        // If the Stripe API call throws an exception, the service must catch it and
        // continue with the existing local status rather than propagating the error.
        final Subscription sub = new Subscription();
        sub.setId(42L);
        sub.setStripeSubscriptionId("sub_error");
        sub.setStatus("ACTIVE");

        when(stripeService.getSubscription("sub_error"))
                .thenThrow(new RuntimeException("Stripe unavailable"));
        when(planRepository.findAll()).thenReturn(Collections.emptyList());

        // Must not throw
        assertDoesNotThrow(() -> service.enrichSubscriptions(List.of(sub)));
        assertEquals("ACTIVE", sub.getStatus());
    }
}
