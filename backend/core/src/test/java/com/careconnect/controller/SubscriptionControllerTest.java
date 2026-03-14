package com.careconnect.controller;

import com.careconnect.dto.PlanDTO;
import com.careconnect.dto.SubscriptionResponseDTO;
import com.careconnect.model.Plan;
import com.careconnect.model.Subscription;
import com.careconnect.model.User;
import com.careconnect.repository.PlanRepository;
import com.careconnect.repository.SubscriptionRepository;
import com.careconnect.repository.UserRepository;
import com.careconnect.service.StripeService;
import com.careconnect.service.SubscriptionEnrichmentService;
import com.careconnect.security.AuthorizationService;
import com.careconnect.service.SubscriptionService;
import com.careconnect.util.SecurityUtil;
import jakarta.servlet.http.HttpServletRequest;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.test.util.ReflectionTestUtils;

import java.io.BufferedReader;
import java.io.StringReader;
import java.util.List;
import java.util.Map;
import java.util.Optional;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.Mockito.*;

@ExtendWith(MockitoExtension.class)
class SubscriptionControllerTest {

    @Mock private SubscriptionService subscriptionService;
    @Mock private StripeService stripeService;
    @Mock private SubscriptionEnrichmentService subscriptionEnrichmentService;
    @Mock private UserRepository userRepository;
    @Mock private PlanRepository planRepository;
    @Mock private SubscriptionRepository subscriptionRepository;

    @Mock private SecurityUtil securityUtil;
    @Mock private AuthorizationService authorizationService;

    @InjectMocks
    private SubscriptionController controller;

    private static final Long USER_ID = 1L;
    private static final Long SUB_ID  = 42L;

    @BeforeEach
    void injectValues() throws Exception {
        ReflectionTestUtils.setField(controller, "stripeWebhookSecret", "whsec_test");
        ReflectionTestUtils.setField(controller, "frontendBaseUrl", "http://localhost:3000");
    }

    // ─── listProducts ─────────────────────────────────────────────────────────

    @Test
    void listProducts_returnsOkWithProductsString() throws Exception {
        when(stripeService.listProducts()).thenReturn("[{\"id\":\"prod_1\"}]");

        final ResponseEntity<String> response = controller.listProducts();

        assertThat(response.getStatusCode()).isEqualTo(HttpStatus.OK);
        assertThat(response.getBody()).contains("prod_1");
    }

    // ─── listPlans ────────────────────────────────────────────────────────────

    @Test
    void listPlans_returnsOkWithPlanList() throws Exception {
        final List<PlanDTO> plans = List.of(new PlanDTO("price_1", true, 999, "usd", "month", 1, "prod_1", "Pro"));
        when(stripeService.listPlans()).thenReturn(plans);

        final ResponseEntity<List<PlanDTO>> response = controller.listPlans();

        assertThat(response.getStatusCode()).isEqualTo(HttpStatus.OK);
        assertThat(response.getBody()).isEqualTo(plans);
    }

    // ─── createPlan ───────────────────────────────────────────────────────────

    @Test
    void createPlan_returnsOkWithPlan() throws Exception {
        final Plan plan = mock(Plan.class);
        when(subscriptionService.createPlan("PRO", "Pro Plan", 999, "monthly", true)).thenReturn(plan);

        final ResponseEntity<?> response = controller.createPlan("PRO", "Pro Plan", 999, "monthly", true);

        assertThat(response.getStatusCode()).isEqualTo(HttpStatus.OK);
        assertThat(response.getBody()).isEqualTo(plan);
    }

    // ─── getPlan ──────────────────────────────────────────────────────────────

    @Test
    void getPlan_returnsOkWithPlan() throws Exception {
        final Plan plan = mock(Plan.class);
        when(subscriptionService.getPlan(5L)).thenReturn(plan);

        final ResponseEntity<?> response = controller.getPlan("5");

        assertThat(response.getStatusCode()).isEqualTo(HttpStatus.OK);
        assertThat(response.getBody()).isEqualTo(plan);
    }

    // ─── syncPlanWithStripe ───────────────────────────────────────────────────

    @Test
    void syncPlanWithStripe_success_returnsOk() throws Exception {
        final Plan plan = mock(Plan.class);
        when(subscriptionService.syncPlanWithStripe(5L, true)).thenReturn(plan);

        final ResponseEntity<?> response = controller.syncPlanWithStripe("5", true);

        assertThat(response.getStatusCode()).isEqualTo(HttpStatus.OK);
    }

    @Test
    void syncPlanWithStripe_exception_returnsBadRequest() throws Exception {
        when(subscriptionService.syncPlanWithStripe(5L, true)).thenThrow(new RuntimeException("Stripe error"));

        final ResponseEntity<?> response = controller.syncPlanWithStripe("5", true);

        assertThat(response.getStatusCode()).isEqualTo(HttpStatus.BAD_REQUEST);
    }

    // ─── syncSubscriptionFromStripe ───────────────────────────────────────────

    @Test
    void syncSubscriptionFromStripe_success_returnsOk() throws Exception {
        final Subscription sub = new Subscription();
        when(subscriptionService.syncSubscriptionFromStripe("sub_abc")).thenReturn(sub);

        final ResponseEntity<?> response = controller.syncSubscriptionFromStripe("sub_abc");

        assertThat(response.getStatusCode()).isEqualTo(HttpStatus.OK);
    }

    @Test
    void syncSubscriptionFromStripe_exception_returnsBadRequest() throws Exception {
        when(subscriptionService.syncSubscriptionFromStripe("sub_bad")).thenThrow(new RuntimeException("not found"));

        final ResponseEntity<?> response = controller.syncSubscriptionFromStripe("sub_bad");

        assertThat(response.getStatusCode()).isEqualTo(HttpStatus.BAD_REQUEST);
    }

    // ─── getStripeCustomerSubscriptions ───────────────────────────────────────

    @Test
    void getStripeCustomerSubscriptions_returnsOk() throws Exception {
        when(stripeService.listSubscriptions("cus_1")).thenReturn("[]");

        final ResponseEntity<String> response = controller.getStripeCustomerSubscriptions("cus_1");

        assertThat(response.getStatusCode()).isEqualTo(HttpStatus.OK);
    }

    // ─── syncAllCustomerSubscriptions ─────────────────────────────────────────

    @Test
    void syncAllCustomerSubscriptions_success_returnsOk() throws Exception {
        when(subscriptionService.syncAllSubscriptionsForCustomer("cus_1")).thenReturn(List.of());

        final ResponseEntity<?> response = controller.syncAllCustomerSubscriptions("cus_1");

        assertThat(response.getStatusCode()).isEqualTo(HttpStatus.OK);
    }

    @Test
    void syncAllCustomerSubscriptions_exception_returnsBadRequest() throws Exception {
        when(subscriptionService.syncAllSubscriptionsForCustomer("cus_bad")).thenThrow(new RuntimeException("fail"));

        final ResponseEntity<?> response = controller.syncAllCustomerSubscriptions("cus_bad");

        assertThat(response.getStatusCode()).isEqualTo(HttpStatus.BAD_REQUEST);
    }

    // ─── getSubscription ──────────────────────────────────────────────────────

    @Test
    void getSubscription_returnsOk() throws Exception {
        when(stripeService.getSubscription("sub_1")).thenReturn("{\"id\":\"sub_1\"}");

        final ResponseEntity<String> response = controller.getSubscription("sub_1");

        assertThat(response.getStatusCode()).isEqualTo(HttpStatus.OK);
    }

    // ─── searchSubscriptions ──────────────────────────────────────────────────

    @Test
    void searchSubscriptions_returnsOk() throws Exception {
        when(stripeService.searchSubscriptions("query")).thenReturn("[]");

        final ResponseEntity<String> response = controller.searchSubscriptions("query");

        assertThat(response.getStatusCode()).isEqualTo(HttpStatus.OK);
    }

    // ─── createCheckoutSession ────────────────────────────────────────────────

    @Test
    void createCheckoutSession_delegatesToService() throws Exception {
        final HttpServletRequest request = mock(HttpServletRequest.class);
        final ResponseEntity<String> serviceResponse = ResponseEntity.ok("url");
        doReturn(serviceResponse).when(subscriptionService)
                .createCheckoutSession(request, "pro", USER_ID, null, null, null);

        final ResponseEntity<?> response = controller.createCheckoutSession(request, "pro", USER_ID, null, null, null);

        assertThat(response.getStatusCode()).isEqualTo(HttpStatus.OK);
    }

    // ─── cancelSubscription ───────────────────────────────────────────────────

    @Test
    void cancelSubscription_stripeId_cancelsByStripeIdAndReturnsOk() throws Exception {
        doNothing().when(subscriptionService).cancelSubscriptionByStripeId("sub_abc");

        final ResponseEntity<?> response = controller.cancelSubscription("sub_abc");

        assertThat(response.getStatusCode()).isEqualTo(HttpStatus.OK);
        verify(subscriptionService).cancelSubscriptionByStripeId("sub_abc");
    }

    @Test
    void cancelSubscription_numericId_cancelsByDatabaseIdAndReturnsOk() throws Exception {
        doNothing().when(subscriptionService).cancelSubscription(42L);

        final ResponseEntity<?> response = controller.cancelSubscription("42");

        assertThat(response.getStatusCode()).isEqualTo(HttpStatus.OK);
        verify(subscriptionService).cancelSubscription(42L);
    }

    @Test
    void cancelSubscription_invalidFormat_returnsBadRequest() throws Exception {
        final ResponseEntity<?> response = controller.cancelSubscription("not-a-number");

        assertThat(response.getStatusCode()).isEqualTo(HttpStatus.BAD_REQUEST);
    }

    @Test
    void cancelSubscription_serviceThrows_returnsInternalServerError() throws Exception {
        doThrow(new RuntimeException("Stripe down")).when(subscriptionService).cancelSubscriptionByStripeId("sub_x");

        final ResponseEntity<?> response = controller.cancelSubscription("sub_x");

        assertThat(response.getStatusCode()).isEqualTo(HttpStatus.INTERNAL_SERVER_ERROR);
    }

    // ─── cancelSubscriptionById ───────────────────────────────────────────────

    @Test
    void cancelSubscriptionById_success_returnsOk() throws Exception {
        doNothing().when(subscriptionService).cancelSubscription(SUB_ID);

        final ResponseEntity<?> response = controller.cancelSubscriptionById(SUB_ID);

        assertThat(response.getStatusCode()).isEqualTo(HttpStatus.OK);
    }

    @Test
    void cancelSubscriptionById_exception_returnsInternalServerError() throws Exception {
        doThrow(new RuntimeException("fail")).when(subscriptionService).cancelSubscription(SUB_ID);

        final ResponseEntity<?> response = controller.cancelSubscriptionById(SUB_ID);

        assertThat(response.getStatusCode()).isEqualTo(HttpStatus.INTERNAL_SERVER_ERROR);
    }

    // ─── cancelSubscriptionByStripeId ─────────────────────────────────────────

    @Test
    void cancelSubscriptionByStripeId_success_returnsOk() throws Exception {
        doNothing().when(subscriptionService).cancelSubscriptionByStripeId("sub_abc");

        final ResponseEntity<?> response = controller.cancelSubscriptionByStripeId("sub_abc");

        assertThat(response.getStatusCode()).isEqualTo(HttpStatus.OK);
    }

    @Test
    void cancelSubscriptionByStripeId_exception_returnsInternalServerError() throws Exception {
        doThrow(new RuntimeException("fail")).when(subscriptionService).cancelSubscriptionByStripeId("sub_bad");

        final ResponseEntity<?> response = controller.cancelSubscriptionByStripeId("sub_bad");

        assertThat(response.getStatusCode()).isEqualTo(HttpStatus.INTERNAL_SERVER_ERROR);
    }

    // ─── createSubscriptionDirect ─────────────────────────────────────────────

    @Test
    void createSubscriptionDirect_bothParamsProvided_returnsOk() throws Exception {
        final Map<String, Object> result = Map.of("id", "sub_new");
        when(stripeService.createSubscription("cus_1", "price_1")).thenReturn(result);

        final ResponseEntity<?> response = controller.createSubscriptionDirect("cus_1", "price_1", null);

        assertThat(response.getStatusCode()).isEqualTo(HttpStatus.OK);
    }

    @Test
    void createSubscriptionDirect_paramsFromBody_returnsOk() throws Exception {
        final Map<String, Object> result = Map.of("id", "sub_new");
        when(stripeService.createSubscription("cus_1", "price_1")).thenReturn(result);
        final Map<String, String> body = Map.of("customerId", "cus_1", "priceId", "price_1");

        final ResponseEntity<?> response = controller.createSubscriptionDirect(null, null, body);

        assertThat(response.getStatusCode()).isEqualTo(HttpStatus.OK);
    }

    @Test
    void createSubscriptionDirect_customerIdMissing_returnsBadRequest() throws Exception {
        final ResponseEntity<?> response = controller.createSubscriptionDirect(null, "price_1", null);

        assertThat(response.getStatusCode()).isEqualTo(HttpStatus.BAD_REQUEST);
        @SuppressWarnings("unchecked")
        final Map<String, String> body = (Map<String, String>) response.getBody();
        assertThat(body).containsEntry("error", "Customer ID is required");
    }

    @Test
    void createSubscriptionDirect_customerIdEmpty_returnsBadRequest() throws Exception {
        final ResponseEntity<?> response = controller.createSubscriptionDirect("", "price_1", null);

        assertThat(response.getStatusCode()).isEqualTo(HttpStatus.BAD_REQUEST);
    }

    @Test
    void createSubscriptionDirect_priceIdMissing_returnsBadRequest() throws Exception {
        final ResponseEntity<?> response = controller.createSubscriptionDirect("cus_1", null, null);

        assertThat(response.getStatusCode()).isEqualTo(HttpStatus.BAD_REQUEST);
        @SuppressWarnings("unchecked")
        final Map<String, String> body = (Map<String, String>) response.getBody();
        assertThat(body).containsEntry("error", "Price ID is required");
    }

    @Test
    void createSubscriptionDirect_priceIdEmpty_returnsBadRequest() throws Exception {
        final ResponseEntity<?> response = controller.createSubscriptionDirect("cus_1", "", null);

        assertThat(response.getStatusCode()).isEqualTo(HttpStatus.BAD_REQUEST);
    }

    @Test
    void createSubscriptionDirect_stripeException_returnsBadRequest() throws Exception {
        when(stripeService.createSubscription("cus_1", "price_1")).thenThrow(new RuntimeException("Stripe error"));

        final ResponseEntity<?> response = controller.createSubscriptionDirect("cus_1", "price_1", null);

        assertThat(response.getStatusCode()).isEqualTo(HttpStatus.BAD_REQUEST);
    }

    // ─── createSubscriptionDirectForUser ──────────────────────────────────────

    @Test
    void createSubscriptionDirectForUser_success_returnsOk() throws Exception {
        final Subscription sub = new Subscription();
        when(subscriptionService.createSubscriptionDirectly(USER_ID, "price_1")).thenReturn(sub);

        final ResponseEntity<?> response = controller.createSubscriptionDirectForUser(USER_ID, "price_1");

        assertThat(response.getStatusCode()).isEqualTo(HttpStatus.OK);
    }

    @Test
    void createSubscriptionDirectForUser_exception_returnsBadRequest() throws Exception {
        when(subscriptionService.createSubscriptionDirectly(USER_ID, "price_1")).thenThrow(new RuntimeException("fail"));

        final ResponseEntity<?> response = controller.createSubscriptionDirectForUser(USER_ID, "price_1");

        assertThat(response.getStatusCode()).isEqualTo(HttpStatus.BAD_REQUEST);
    }

    // ─── handleStripeWebhook ──────────────────────────────────────────────────

    @Test
    void handleStripeWebhook_success_returnsOk() throws Exception {
        final HttpServletRequest request = mock(HttpServletRequest.class);
        when(request.getReader()).thenReturn(new BufferedReader(new StringReader("payload")));
        when(request.getHeader("Stripe-Signature")).thenReturn("sig");
        when(subscriptionService.handleStripeWebhook("payload", "sig", "whsec_test")).thenReturn("ok");

        final ResponseEntity<String> response = controller.handleStripeWebhook(request);

        assertThat(response.getStatusCode()).isEqualTo(HttpStatus.OK);
    }

    @Test
    void handleStripeWebhook_exception_returnsBadRequest() throws Exception {
        final HttpServletRequest request = mock(HttpServletRequest.class);
        when(request.getReader()).thenThrow(new RuntimeException("IO error"));

        final ResponseEntity<String> response = controller.handleStripeWebhook(request);

        assertThat(response.getStatusCode()).isEqualTo(HttpStatus.BAD_REQUEST);
    }

    // ─── getUserSubscriptions ─────────────────────────────────────────────────

    @Test
    void getUserSubscriptions_success_returnsOk() throws Exception {
        final List<SubscriptionResponseDTO> dtos = List.of(new SubscriptionResponseDTO());
        when(subscriptionEnrichmentService.getEnrichedUserSubscriptions(USER_ID)).thenReturn(dtos);

        final ResponseEntity<?> response = controller.getUserSubscriptions(USER_ID);

        assertThat(response.getStatusCode()).isEqualTo(HttpStatus.OK);
    }

    @Test
    void getUserSubscriptions_exception_returnsBadRequest() throws Exception {
        when(subscriptionEnrichmentService.getEnrichedUserSubscriptions(USER_ID)).thenThrow(new RuntimeException("fail"));

        final ResponseEntity<?> response = controller.getUserSubscriptions(USER_ID);

        assertThat(response.getStatusCode()).isEqualTo(HttpStatus.BAD_REQUEST);
    }

    // ─── refreshAndGetUserSubscriptions ───────────────────────────────────────

    @Test
    void refreshAndGetUserSubscriptions_success_returnsOk() throws Exception {
        final List<SubscriptionResponseDTO> dtos = List.of();
        when(subscriptionService.refreshUserSubscriptionsFromStripe(USER_ID)).thenReturn(List.of());
        when(subscriptionEnrichmentService.getEnrichedUserSubscriptions(USER_ID)).thenReturn(dtos);

        final ResponseEntity<?> response = controller.refreshAndGetUserSubscriptions(USER_ID);

        assertThat(response.getStatusCode()).isEqualTo(HttpStatus.OK);
    }

    @Test
    void refreshAndGetUserSubscriptions_exception_returnsBadRequest() throws Exception {
        when(subscriptionService.refreshUserSubscriptionsFromStripe(USER_ID)).thenThrow(new RuntimeException("fail"));

        final ResponseEntity<?> response = controller.refreshAndGetUserSubscriptions(USER_ID);

        assertThat(response.getStatusCode()).isEqualTo(HttpStatus.BAD_REQUEST);
    }

    // ─── forceImportSubscription ──────────────────────────────────────────────

    @Test
    void forceImportSubscription_userNotFound_returnsBadRequest() throws Exception {
        when(userRepository.findById(USER_ID)).thenReturn(Optional.empty());

        final ResponseEntity<?> response = controller.forceImportSubscription(USER_ID, "sub_xyz");

        assertThat(response.getStatusCode()).isEqualTo(HttpStatus.BAD_REQUEST);
    }

    @Test
    void forceImportSubscription_subscriptionAlreadyExists_returnsOk() throws Exception {
        final User user = mock(User.class);
        final Subscription existing = new Subscription();
        when(userRepository.findById(USER_ID)).thenReturn(Optional.of(user));
        when(subscriptionRepository.findByStripeSubscriptionId("sub_xyz")).thenReturn(Optional.of(existing));

        final ResponseEntity<?> response = controller.forceImportSubscription(USER_ID, "sub_xyz");

        assertThat(response.getStatusCode()).isEqualTo(HttpStatus.OK);
        @SuppressWarnings("unchecked")
        final Map<String, Object> body = (Map<String, Object>) response.getBody();
        assertThat(body.get("message")).isEqualTo("Subscription already exists");
    }

    @Test
    void forceImportSubscription_stripeDataEmpty_returnsBadRequest() throws Exception {
        final User user = mock(User.class);
        when(userRepository.findById(USER_ID)).thenReturn(Optional.of(user));
        when(subscriptionRepository.findByStripeSubscriptionId("sub_xyz")).thenReturn(Optional.empty());
        when(stripeService.getSubscription("sub_xyz")).thenReturn("");

        final ResponseEntity<?> response = controller.forceImportSubscription(USER_ID, "sub_xyz");

        assertThat(response.getStatusCode()).isEqualTo(HttpStatus.BAD_REQUEST);
    }

    @Test
    void forceImportSubscription_fullJson_planFound_returnsOk() throws Exception {
        final User user = mock(User.class);
        when(user.getStripeCustomerId()).thenReturn("cus_1");
        final Subscription savedSub = new Subscription();
        final Plan plan = mock(Plan.class);
        when(userRepository.findById(USER_ID)).thenReturn(Optional.of(user));
        when(subscriptionRepository.findByStripeSubscriptionId("sub_xyz")).thenReturn(Optional.empty());
        when(stripeService.getSubscription("sub_xyz")).thenReturn(
                "{\"status\":\"active\","
                + "\"items\":{\"data\":[{\"price\":{\"id\":\"price_1\"}}]},"
                + "\"current_period_start\":1700000000,"
                + "\"current_period_end\":1702678400}");
        when(planRepository.findByName("Premium Plan")).thenReturn(List.of(plan));
        when(subscriptionRepository.save(any())).thenReturn(savedSub);

        final ResponseEntity<?> response = controller.forceImportSubscription(USER_ID, "sub_xyz");

        assertThat(response.getStatusCode()).isEqualTo(HttpStatus.OK);
        @SuppressWarnings("unchecked")
        final Map<String, Object> body = (Map<String, Object>) response.getBody();
        assertThat(body.get("message")).isEqualTo("Subscription imported successfully");
    }

    @Test
    void forceImportSubscription_minimalJson_noPlan_returnsOk() throws Exception {
        final User user = mock(User.class);
        when(user.getStripeCustomerId()).thenReturn("cus_1");
        final Subscription savedSub = new Subscription();
        when(userRepository.findById(USER_ID)).thenReturn(Optional.of(user));
        when(subscriptionRepository.findByStripeSubscriptionId("sub_xyz")).thenReturn(Optional.empty());
        // No status, no items, no period dates
        when(stripeService.getSubscription("sub_xyz")).thenReturn("{}");
        when(planRepository.findByName("Premium Plan")).thenReturn(List.of());
        when(subscriptionRepository.save(any())).thenReturn(savedSub);

        final ResponseEntity<?> response = controller.forceImportSubscription(USER_ID, "sub_xyz");

        assertThat(response.getStatusCode()).isEqualTo(HttpStatus.OK);
    }

    @Test
    void forceImportSubscription_itemsWithNoPrice_returnsOk() throws Exception {
        final User user = mock(User.class);
        when(user.getStripeCustomerId()).thenReturn("cus_1");
        final Subscription savedSub = new Subscription();
        when(userRepository.findById(USER_ID)).thenReturn(Optional.of(user));
        when(subscriptionRepository.findByStripeSubscriptionId("sub_xyz")).thenReturn(Optional.empty());
        // Items present but no price.id
        when(stripeService.getSubscription("sub_xyz")).thenReturn(
                "{\"items\":{\"data\":[{\"other\":\"field\"}]}}");
        when(planRepository.findByName("Premium Plan")).thenReturn(List.of());
        when(subscriptionRepository.save(any())).thenReturn(savedSub);

        final ResponseEntity<?> response = controller.forceImportSubscription(USER_ID, "sub_xyz");

        assertThat(response.getStatusCode()).isEqualTo(HttpStatus.OK);
    }

    @Test
    void forceImportSubscription_emptyItemsData_returnsOk() throws Exception {
        final User user = mock(User.class);
        when(user.getStripeCustomerId()).thenReturn("cus_1");
        final Subscription savedSub = new Subscription();
        when(userRepository.findById(USER_ID)).thenReturn(Optional.of(user));
        when(subscriptionRepository.findByStripeSubscriptionId("sub_xyz")).thenReturn(Optional.empty());
        // Items present but data array is empty
        when(stripeService.getSubscription("sub_xyz")).thenReturn(
                "{\"items\":{\"data\":[]}}");
        when(planRepository.findByName("Premium Plan")).thenReturn(List.of());
        when(subscriptionRepository.save(any())).thenReturn(savedSub);

        final ResponseEntity<?> response = controller.forceImportSubscription(USER_ID, "sub_xyz");

        assertThat(response.getStatusCode()).isEqualTo(HttpStatus.OK);
    }

    // ─── refreshUserSubscriptionsWithStripe ───────────────────────────────────

    @Test
    void refreshUserSubscriptionsWithStripe_success_returnsOk() throws Exception {
        final List<Subscription> subs = List.of();
        final List<SubscriptionResponseDTO> dtos = List.of();
        when(subscriptionService.refreshUserSubscriptionsFromStripe(USER_ID)).thenReturn(subs);
        when(subscriptionEnrichmentService.enrichSubscriptions(subs)).thenReturn(dtos);

        final ResponseEntity<?> response = controller.refreshUserSubscriptionsWithStripe(USER_ID);

        assertThat(response.getStatusCode()).isEqualTo(HttpStatus.OK);
    }

    @Test
    void refreshUserSubscriptionsWithStripe_exception_returnsBadRequest() throws Exception {
        when(subscriptionService.refreshUserSubscriptionsFromStripe(USER_ID)).thenThrow(new RuntimeException("fail"));

        final ResponseEntity<?> response = controller.refreshUserSubscriptionsWithStripe(USER_ID);

        assertThat(response.getStatusCode()).isEqualTo(HttpStatus.BAD_REQUEST);
    }

    // ─── getUserActiveSubscriptions ───────────────────────────────────────────

    @Test
    void getUserActiveSubscriptions_success_returnsOk() throws Exception {
        when(subscriptionEnrichmentService.getEnrichedActiveUserSubscriptions(USER_ID)).thenReturn(List.of());

        final ResponseEntity<?> response = controller.getUserActiveSubscriptions(USER_ID);

        assertThat(response.getStatusCode()).isEqualTo(HttpStatus.OK);
    }

    @Test
    void getUserActiveSubscriptions_exception_returnsBadRequest() throws Exception {
        when(subscriptionEnrichmentService.getEnrichedActiveUserSubscriptions(USER_ID))
                .thenThrow(new RuntimeException("fail"));

        final ResponseEntity<?> response = controller.getUserActiveSubscriptions(USER_ID);

        assertThat(response.getStatusCode()).isEqualTo(HttpStatus.BAD_REQUEST);
    }

    // ─── syncUserSubscriptionsFromStripe ──────────────────────────────────────

    @Test
    void syncUserSubscriptionsFromStripe_success_returnsOk() throws Exception {
        final List<Subscription> subs = List.of();
        final List<SubscriptionResponseDTO> dtos = List.of();
        when(subscriptionService.refreshUserSubscriptionsFromStripe(USER_ID)).thenReturn(subs);
        when(subscriptionEnrichmentService.enrichSubscriptions(subs)).thenReturn(dtos);

        final ResponseEntity<?> response = controller.syncUserSubscriptionsFromStripe(USER_ID);

        assertThat(response.getStatusCode()).isEqualTo(HttpStatus.OK);
    }

    @Test
    void syncUserSubscriptionsFromStripe_exception_returnsBadRequest() throws Exception {
        when(subscriptionService.refreshUserSubscriptionsFromStripe(USER_ID)).thenThrow(new RuntimeException("fail"));

        final ResponseEntity<?> response = controller.syncUserSubscriptionsFromStripe(USER_ID);

        assertThat(response.getStatusCode()).isEqualTo(HttpStatus.BAD_REQUEST);
    }

    // ─── upgradeOrDowngradeSubscription ───────────────────────────────────────

    @Test
    void upgradeOrDowngradeSubscription_success_returnsOk() throws Exception {
        final Map<String, Object> result = Map.of("id", "sub_new");
        when(stripeService.upgradeOrDowngradeSubscription("sub_old", "price_new")).thenReturn(result);

        final ResponseEntity<?> response = controller.upgradeOrDowngradeSubscription("sub_old", "price_new");

        assertThat(response.getStatusCode()).isEqualTo(HttpStatus.OK);
    }

    @Test
    void upgradeOrDowngradeSubscription_exception_returnsBadRequest() throws Exception {
        when(stripeService.upgradeOrDowngradeSubscription("sub_old", "price_new"))
                .thenThrow(new RuntimeException("fail"));

        final ResponseEntity<?> response = controller.upgradeOrDowngradeSubscription("sub_old", "price_new");

        assertThat(response.getStatusCode()).isEqualTo(HttpStatus.BAD_REQUEST);
    }

    // ─── getPaymentRedirectUrl ────────────────────────────────────────────────

    @Test
    void getPaymentRedirectUrl_portalTrue_redirectsToSubscriptionPage() throws Exception {
        final ResponseEntity<?> response = controller.getPaymentRedirectUrl(true);

        assertThat(response.getStatusCode()).isEqualTo(HttpStatus.OK);
        @SuppressWarnings("unchecked")
        final Map<String, String> body = (Map<String, String>) response.getBody();
        assertThat(body.get("redirectUrl")).isEqualTo("http://localhost:3000/account/subscription");
    }

    @Test
    void getPaymentRedirectUrl_portalFalse_redirectsToDashboard() throws Exception {
        final ResponseEntity<?> response = controller.getPaymentRedirectUrl(false);

        assertThat(response.getStatusCode()).isEqualTo(HttpStatus.OK);
        @SuppressWarnings("unchecked")
        final Map<String, String> body = (Map<String, String>) response.getBody();
        assertThat(body.get("redirectUrl")).isEqualTo("http://localhost:3000/dashboard");
    }

    @Test
    void getPaymentRedirectUrl_portalNull_redirectsToDashboard() throws Exception {
        final ResponseEntity<?> response = controller.getPaymentRedirectUrl(null);

        assertThat(response.getStatusCode()).isEqualTo(HttpStatus.OK);
        @SuppressWarnings("unchecked")
        final Map<String, String> body = (Map<String, String>) response.getBody();
        assertThat(body.get("redirectUrl")).isEqualTo("http://localhost:3000/dashboard");
    }
}
