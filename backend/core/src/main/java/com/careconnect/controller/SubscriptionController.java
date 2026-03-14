package com.careconnect.controller;

import com.careconnect.security.Permission;
import com.careconnect.security.RequirePermission;

import com.stripe.model.SubscriptionCollection;
import org.springframework.beans.factory.annotation.Value;
import java.util.Map;
import java.util.List;
import com.careconnect.model.Subscription;
import com.careconnect.model.Plan;
import com.careconnect.model.User;
import com.careconnect.repository.UserRepository;
import com.careconnect.repository.PlanRepository;
import com.careconnect.repository.SubscriptionRepository;
import com.careconnect.security.AuthorizationService;
import com.careconnect.security.UnauthorizedException;
import com.careconnect.util.SecurityUtil;
import com.stripe.exception.StripeException;
import com.stripe.model.checkout.Session;
import com.careconnect.service.StripeService;
import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;

import jakarta.servlet.http.HttpServletRequest;
import java.util.Optional;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.http.ResponseEntity;

import com.careconnect.dto.SubscriptionCancelRequestDTO;
import com.careconnect.dto.PlanDTO;
import com.careconnect.dto.SubscriptionResponseDTO;
import com.careconnect.model.Subscription;
import com.careconnect.service.StripeService;
import com.careconnect.service.SubscriptionEnrichmentService;
import com.careconnect.service.SubscriptionService;
import org.springframework.beans.factory.ObjectProvider;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;

import java.util.List;
import java.util.Map;

@RestController
@RequestMapping("/v3/api/subscriptions")
public class SubscriptionController {

    private final SubscriptionService subscriptionService;
    private final StripeService stripeService;
    private final SubscriptionEnrichmentService subscriptionEnrichmentService;
    private final UserRepository userRepository;
    private final PlanRepository planRepository;
    private final SubscriptionRepository subscriptionRepository;
    private final SecurityUtil securityUtil;
    private final AuthorizationService authorizationService;

    @Value("${stripe.webhook-secret}")
    private String stripeWebhookSecret;

    @Value("${frontend.base-url}")
    private String frontendBaseUrl;


    public SubscriptionController(
        SubscriptionService subscriptionService,
        StripeService stripeService,
        SubscriptionEnrichmentService subscriptionEnrichmentService,
        UserRepository userRepository,
        PlanRepository planRepository,
        SubscriptionRepository subscriptionRepository,
        SecurityUtil securityUtil,
        AuthorizationService authorizationService
        // @Value("${stripe.webhook-secret}") String stripeWebhookSecret
    ) {
        this.subscriptionService = subscriptionService;
        this.stripeService = stripeService;
        this.subscriptionEnrichmentService = subscriptionEnrichmentService;
        this.userRepository = userRepository;
        this.planRepository = planRepository;
        this.subscriptionRepository = subscriptionRepository;
        this.securityUtil = securityUtil;
        this.authorizationService = authorizationService;
        // this.stripeWebhookSecret = stripeWebhookSecret;
    }
	
    @RequirePermission(Permission.VIEW_ASSIGNED_PATIENTS)

	
    @GetMapping("/products")
    public ResponseEntity<String> listProducts() {
        String products = stripeService.listProducts();
        return ResponseEntity.ok(products);
    }
 
    @RequirePermission(Permission.VIEW_ASSIGNED_PATIENTS)

 
    @GetMapping("/plans")
    public ResponseEntity<List<PlanDTO>> listPlans() {
    List<PlanDTO> plans = stripeService.listPlans();
    return ResponseEntity.ok(plans);
    }
    
    @RequirePermission(Permission.CREATE_TASKS)

    
    @PostMapping("/plans")
    public ResponseEntity<?> createPlan(
            @RequestParam String code,
            @RequestParam String name,
            @RequestParam Integer priceCents,
            @RequestParam String billingPeriod,
            @RequestParam(required = false) Boolean isActive) throws UnauthorizedException {

        User currentUser = securityUtil.resolveCurrentUser();
        authorizationService.requireAdmin(currentUser);
        Plan plan = subscriptionService.createPlan(code, name, priceCents, billingPeriod, isActive);
        return ResponseEntity.ok(plan);
    }
    
    @RequirePermission(Permission.VIEW_ASSIGNED_PATIENTS)

    
    @GetMapping("/plans/{planId}")
    public ResponseEntity<?> getPlan(@PathVariable String planId) {
        Plan plan = subscriptionService.getPlan(Long.parseLong(planId));
        return ResponseEntity.ok(plan);
    }
    
    @RequirePermission(Permission.CREATE_TASKS)

    
    @PostMapping("/plans/{planId}/sync-with-stripe")
    public ResponseEntity<?> syncPlanWithStripe(
            @PathVariable String planId,
            @RequestParam(defaultValue = "true") boolean createIfMissing) throws UnauthorizedException {
        User currentUser = securityUtil.resolveCurrentUser();
        authorizationService.requireAdmin(currentUser);
        try {
            Plan plan = subscriptionService.syncPlanWithStripe(Long.parseLong(planId), createIfMissing);
            return ResponseEntity.ok(plan);
        } catch (Exception e) {
            return ResponseEntity.badRequest().body(Map.of("error", e.getMessage()));
        }
    }
    
    @RequirePermission(Permission.CREATE_TASKS)

    
    @PostMapping("/sync-from-stripe/{stripeSubscriptionId}")
    public ResponseEntity<?> syncSubscriptionFromStripe(@PathVariable String stripeSubscriptionId) throws UnauthorizedException {
        User currentUser = securityUtil.resolveCurrentUser();
        authorizationService.requireAdmin(currentUser);
        try {
            Subscription subscription = subscriptionService.syncSubscriptionFromStripe(stripeSubscriptionId);
            return ResponseEntity.ok(subscription);
        } catch (Exception e) {
            return ResponseEntity.badRequest().body(Map.of("error", e.getMessage()));
        }
    }
    
    @RequirePermission(Permission.VIEW_ASSIGNED_PATIENTS)

    
    @GetMapping("/stripe/{customerId}/subscriptions")
    public ResponseEntity<String> getStripeCustomerSubscriptions(@PathVariable String customerId) throws UnauthorizedException {
        User currentUser = securityUtil.resolveCurrentUser();
        authorizationService.requireAdmin(currentUser);
        String subs = stripeService.listSubscriptions(customerId);
        return ResponseEntity.ok(subs);
    }
    
    @RequirePermission(Permission.VIEW_ASSIGNED_PATIENTS)

    
    @GetMapping("/sync-all-from-stripe/{customerId}")
    public ResponseEntity<?> syncAllCustomerSubscriptions(@PathVariable String customerId) throws UnauthorizedException {
        User currentUser = securityUtil.resolveCurrentUser();
        authorizationService.requireAdmin(currentUser);
        try {
            Long userId = 1L;
            List<SubscriptionResponseDTO> subscriptionDTOs = subscriptionEnrichmentService.getEnrichedUserSubscriptions(userId);
            return ResponseEntity.ok(subscriptionDTOs);
        } catch (Exception e) {
            return ResponseEntity.badRequest().body(Map.of("error", e.getMessage()));
        }
    }

    @RequirePermission(Permission.VIEW_ASSIGNED_PATIENTS)


    @GetMapping("/stripe/subscription/{subscriptionId}")
    public ResponseEntity<String> getSubscription(@PathVariable String subscriptionId) throws UnauthorizedException {
        User currentUser = securityUtil.resolveCurrentUser();
        authorizationService.requireAdmin(currentUser);
        String sub = stripeService.getSubscription(subscriptionId);
        return ResponseEntity.ok(sub);
    }

    @RequirePermission(Permission.VIEW_ASSIGNED_PATIENTS)


    @GetMapping("/stripe/subscriptions/search")
    public ResponseEntity<String> searchSubscriptions(@RequestParam String query) throws UnauthorizedException {
        User currentUser = securityUtil.resolveCurrentUser();
        authorizationService.requireAdmin(currentUser);
        String result = stripeService.searchSubscriptions(query);
        return ResponseEntity.ok(result);
    }

    @RequirePermission(Permission.CREATE_TASKS)


    @PostMapping("/create")
    public ResponseEntity<?> createCheckoutSession(
            HttpServletRequest request,
            @RequestParam String plan,
            @RequestParam(required = false, defaultValue = "0") Long userId,
            @RequestParam(required = false) Long amount,
            @RequestParam(required = false) String stripeCustomerId,
            @RequestParam(required = false) String portal) {
        // RBAC: Ensure the caller is authenticated
        User currentUser = securityUtil.resolveCurrentUser();
        return subscriptionService.createCheckoutSession(request, plan, userId, amount, stripeCustomerId, portal);
    }

    // @RequirePermission(Permission.UPDATE_TASKS)
 @PutMapping("/{id}/payment-method")
    // public ResponseEntity<String> updatePayment(@PathVariable String id) { return ResponseEntity.ok("Payment updated: " + id); }
    @RequirePermission(Permission.CREATE_TASKS)

    @PostMapping("/{id}/cancel")
    public ResponseEntity<?> cancelSubscription(@PathVariable String id) throws UnauthorizedException {
        // RBAC: Only admins can cancel subscriptions by arbitrary ID
        User currentUser = securityUtil.resolveCurrentUser();
        authorizationService.requireAdmin(currentUser);
        try {
            if (id.startsWith("sub_")) {
                subscriptionService.cancelSubscriptionByStripeId(id);
            } else {
                Long subscriptionId = Long.parseLong(id);
                subscriptionService.cancelSubscription(subscriptionId);
            }
            return ResponseEntity.ok().body(Map.of(
                    "message", "Subscription cancelled and cleared successfully",
                    "subscriptionId", id
            ));
        } catch (NumberFormatException e) {
            return ResponseEntity.badRequest().body(Map.of("error", "Invalid subscription ID format"));
        } catch (Exception e) {
            return ResponseEntity.status(500).body(Map.of("error", "Failed to cancel subscription: " + e.getMessage()));
        }
    }

    /**
     * Cancel subscription by database subscription ID
     */
    @RequirePermission(Permission.CREATE_TASKS)

    @PostMapping("/database/{subscriptionId}/cancel")
    public ResponseEntity<?> cancelSubscriptionById(@PathVariable Long subscriptionId) throws UnauthorizedException {
        // RBAC: Only admins can cancel subscriptions by database ID
        User currentUser = securityUtil.resolveCurrentUser();
        authorizationService.requireAdmin(currentUser);
        try {
            subscriptionService.cancelSubscription(subscriptionId);
            return ResponseEntity.ok().body(Map.of(
                "message", "Subscription cancelled and cleared successfully",
                "subscriptionId", subscriptionId
            ));
        } catch (Exception e) {
            return ResponseEntity.status(500).body(Map.of("error", "Failed to cancel subscription: " + e.getMessage()));
        }
    }

    /**
     * Cancel subscription by Stripe subscription ID
     */
    @RequirePermission(Permission.CREATE_TASKS)

    @PostMapping("/stripe/{stripeSubscriptionId}/cancel")
    public ResponseEntity<?> cancelSubscriptionByStripeId(@PathVariable String stripeSubscriptionId) throws UnauthorizedException {
        // RBAC: Only admins can cancel subscriptions by Stripe ID
        User currentUser = securityUtil.resolveCurrentUser();
        authorizationService.requireAdmin(currentUser);
        try {
            subscriptionService.cancelSubscriptionByStripeId(stripeSubscriptionId);
            return ResponseEntity.ok().body(Map.of(
                "message", "Subscription cancelled and cleared successfully",
                "stripeSubscriptionId", stripeSubscriptionId
            ));
        } catch (Exception e) {
            return ResponseEntity.status(500).body(Map.of("error", "Failed to cancel subscription: " + e.getMessage()));
        }
    }
    
    /**
     * Creates a subscription directly with a Stripe customer ID and price ID
     * 
     * @param customerId The Stripe customer ID (starts with "cus_")
     * @param priceId Either a Stripe price ID (starts with "price_") or a Stripe plan ID (starts with "plan_")
     * @return The created subscription
     */
    @RequirePermission(Permission.CREATE_TASKS)

    @PostMapping("/create-direct")
    public ResponseEntity<?> createSubscriptionDirect(
            @RequestParam(required = false) String customerId,
            @RequestParam(required = false) String priceId,
            @RequestBody(required = false) Map<String, String> requestBody) throws UnauthorizedException {
        // RBAC: Only admins can create subscriptions directly via Stripe
        User currentUser = securityUtil.resolveCurrentUser();
        authorizationService.requireAdmin(currentUser);
        try {
            String finalCustomerId = customerId;
            String finalPriceId = priceId;

            if ((finalCustomerId == null || finalPriceId == null) && requestBody != null) {
                finalCustomerId = requestBody.getOrDefault("customerId", customerId);
                finalPriceId = requestBody.getOrDefault("priceId", priceId);
            }

            if (finalCustomerId == null || finalCustomerId.isEmpty()) {
                return ResponseEntity.badRequest().body(Map.of("error", "Customer ID is required"));
            }
            if (finalPriceId == null || finalPriceId.isEmpty()) {
                return ResponseEntity.badRequest().body(Map.of("error", "Price ID is required"));
            }

            Map<String, Object> result = stripeService.createSubscription(finalCustomerId, finalPriceId);
            return ResponseEntity.ok().body(result);
        } catch (Exception e) {
            return ResponseEntity.badRequest().body(Map.of("error", e.getMessage()));
        }
    }
    
    /**
     * Creates a subscription for a user by their user ID and a price ID
     * 
     * @param userId The CareConnect user ID
     * @param priceId Either a Stripe price ID (starts with "price_") or a Stripe plan ID (starts with "plan_")
     * @return The created subscription
     */
    @RequirePermission(Permission.CREATE_TASKS)

    @PostMapping("/create-direct-for-user")
    public ResponseEntity<?> createSubscriptionDirectForUser(
            @RequestParam Long userId,
            @RequestParam String priceId) throws UnauthorizedException {
        // RBAC: Only admins can create subscriptions for other users
        User currentUser = securityUtil.resolveCurrentUser();
        authorizationService.requireAdmin(currentUser);
        try {
            Subscription subscription = subscriptionService.createSubscriptionDirectly(userId, priceId);
            return ResponseEntity.ok().body(Map.of(
                "message", "Subscription created successfully",
                "subscription", subscription
            ));
        } catch (Exception e) {
            return ResponseEntity.badRequest().body(Map.of("error", e.getMessage()));
        }
    }

    @RequirePermission(Permission.CREATE_TASKS)


    @PostMapping("/webhook/stripe")
    public ResponseEntity<String> handleStripeWebhook(HttpServletRequest request) {
    try {
        String payload = request.getReader().lines().reduce("", (acc, line) -> acc + line);
        String sigHeader = request.getHeader("Stripe-Signature");
        String endpointSecret = stripeWebhookSecret; 

        String result = subscriptionService.handleStripeWebhook(payload, sigHeader, endpointSecret);
        return ResponseEntity.ok(result);
    } catch (Exception e) {
        return ResponseEntity.badRequest().body("Webhook error: " + e.getMessage());
    }
}

@RequirePermission(Permission.VIEW_ASSIGNED_PATIENTS)


@GetMapping("/user/{userId}")
public ResponseEntity<?> getUserSubscriptions(@PathVariable Long userId) throws UnauthorizedException {
    // RBAC: Users can view their own subscriptions; admins can view any
    User currentUser = securityUtil.resolveCurrentUser();
    authorizationService.requireSelfOrAdmin(currentUser, userId);
    try {
        List<SubscriptionResponseDTO> subscriptionDTOs = subscriptionEnrichmentService.getEnrichedUserSubscriptions(userId);
        return ResponseEntity.ok(subscriptionDTOs);
    } catch (Exception e) {
        return ResponseEntity.badRequest().body(Map.of("error", e.getMessage()));
    }
}

@RequirePermission(Permission.VIEW_ASSIGNED_PATIENTS)


@GetMapping("/user/{userId}/refresh")
public ResponseEntity<?> refreshAndGetUserSubscriptions(@PathVariable Long userId) throws UnauthorizedException {
    // RBAC: Users can refresh their own subscriptions; admins can refresh any
    User currentUser = securityUtil.resolveCurrentUser();
    authorizationService.requireSelfOrAdmin(currentUser, userId);
    try {
        // First sync with Stripe to ensure we have all subscriptions
        subscriptionService.refreshUserSubscriptionsFromStripe(userId);
        // Then get enriched data
        List<SubscriptionResponseDTO> subscriptionDTOs = subscriptionEnrichmentService.getEnrichedUserSubscriptions(userId);
        return ResponseEntity.ok(subscriptionDTOs);
    } catch (Exception e) {
        return ResponseEntity.badRequest().body(Map.of("error", e.getMessage()));
    }
}

@RequirePermission(Permission.VIEW_ASSIGNED_PATIENTS)


@GetMapping("/user/{userId}/force-import/{subscriptionId}")
public ResponseEntity<?> forceImportSubscription(
        @PathVariable Long userId,
        @PathVariable String subscriptionId) throws UnauthorizedException {
    // RBAC: Only admins can force-import subscriptions
    User currentUser = securityUtil.resolveCurrentUser();
    authorizationService.requireAdmin(currentUser);
    try {
        User user = userRepository.findById(userId)
            .orElseThrow(() -> new IllegalArgumentException("User not found with ID: " + userId));
            
        // Check if this subscription already exists
        Optional<Subscription> existingSub = subscriptionRepository.findByStripeSubscriptionId(subscriptionId);
        if (existingSub.isPresent()) {
            return ResponseEntity.ok(Map.of("message", "Subscription already exists", 
                                           "subscription", existingSub.get()));
        }
        
        // Fetch subscription from Stripe directly
        String stripeSubData = stripeService.getSubscription(subscriptionId);
        if (stripeSubData == null || stripeSubData.isEmpty()) {
            return ResponseEntity.badRequest().body(Map.of("error", "Subscription not found in Stripe"));
        }
            
        ObjectMapper mapper = new ObjectMapper();
        JsonNode stripeSubJson = mapper.readTree(stripeSubData);
        
        // Create a new subscription record
        Subscription newSub = new Subscription();
        newSub.setUser(user);
        newSub.setStripeSubscriptionId(subscriptionId);
        newSub.setStripeCustomerId(user.getStripeCustomerId());
        
        // Get status from Stripe
        if (stripeSubJson.has("status")) {
            newSub.setStatus(stripeSubJson.get("status").asText().toUpperCase());
        } else {
            newSub.setStatus("ACTIVE"); // Default to active if no status found
        }
        
        // Get price ID if available
        if (stripeSubJson.has("items") && stripeSubJson.get("items").has("data") && 
            stripeSubJson.get("items").get("data").size() > 0) {
            JsonNode item = stripeSubJson.get("items").get("data").get(0);
            if (item.has("price") && item.get("price").has("id")) {
                String priceId = item.get("price").get("id").asText();
                newSub.setPriceId(priceId);
            }
        }
        
        // Get dates
        if (stripeSubJson.has("current_period_start")) {
            newSub.setStartedAt(java.time.Instant.ofEpochSecond(
                stripeSubJson.get("current_period_start").asLong()));
        }
        if (stripeSubJson.has("current_period_end")) {
            newSub.setCurrentPeriodEnd(java.time.Instant.ofEpochSecond(
                stripeSubJson.get("current_period_end").asLong()));
        }
        
        // Find plan
        List<Plan> premiumPlans = planRepository.findByName("Premium Plan");
        if (!premiumPlans.isEmpty()) {
            newSub.setPlan(premiumPlans.get(0));
        }
        
        Subscription savedSub = subscriptionRepository.save(newSub);
        return ResponseEntity.ok(Map.of("message", "Subscription imported successfully", 
                                      "subscription", savedSub));
    } catch (Exception e) {
        return ResponseEntity.badRequest().body(Map.of("error", e.getMessage()));
    }
}

@RequirePermission(Permission.VIEW_ASSIGNED_PATIENTS)


@GetMapping("/user/{userId}/refresh-with-stripe")
public ResponseEntity<?> refreshUserSubscriptionsWithStripe(@PathVariable Long userId) throws UnauthorizedException {
    // RBAC: Users can refresh their own subscriptions; admins can refresh any
    User currentUser = securityUtil.resolveCurrentUser();
    authorizationService.requireSelfOrAdmin(currentUser, userId);
    try {
        List<Subscription> subscriptions = subscriptionService.refreshUserSubscriptionsFromStripe(userId);
        List<SubscriptionResponseDTO> subscriptionDTOs = subscriptionEnrichmentService.enrichSubscriptions(subscriptions);
        return ResponseEntity.ok(Map.of(
            "message", "Successfully refreshed subscriptions from Stripe",
            "subscriptions", subscriptionDTOs
        ));
    } catch (Exception e) {
        return ResponseEntity.badRequest().body(Map.of("error", e.getMessage()));
    }
}

@RequirePermission(Permission.VIEW_ASSIGNED_PATIENTS)


@GetMapping("/user/{userId}/active")
public ResponseEntity<?> getUserActiveSubscriptions(@PathVariable Long userId) throws UnauthorizedException {
    // RBAC: Users can view their own active subscriptions; admins can view any
    User currentUser = securityUtil.resolveCurrentUser();
    authorizationService.requireSelfOrAdmin(currentUser, userId);
    try {
        List<SubscriptionResponseDTO> subscriptionDTOs = subscriptionEnrichmentService.getEnrichedActiveUserSubscriptions(userId);
        return ResponseEntity.ok(subscriptionDTOs);
    } catch (Exception e) {
        return ResponseEntity.badRequest().body(Map.of("error", e.getMessage()));
    }
}

@RequirePermission(Permission.CREATE_TASKS)


@PostMapping("/user/{userId}/sync-from-stripe")
public ResponseEntity<?> syncUserSubscriptionsFromStripe(@PathVariable Long userId) throws UnauthorizedException {
    // RBAC: Only admins can trigger Stripe sync for users
    User currentUser = securityUtil.resolveCurrentUser();
    authorizationService.requireAdmin(currentUser);
    try {
        List<Subscription> subscriptions = subscriptionService.refreshUserSubscriptionsFromStripe(userId);
        // Use enrichment service to add plan details
        List<SubscriptionResponseDTO> subscriptionDTOs = subscriptionEnrichmentService.enrichSubscriptions(subscriptions);
        return ResponseEntity.ok(Map.of(
            "message", "Successfully synced subscriptions from Stripe",
            "count", subscriptions.size(),
            "subscriptions", subscriptionDTOs
        ));
    } catch (Exception e) {
        return ResponseEntity.badRequest().body(Map.of("error", e.getMessage()));
    }
}

    @RequirePermission(Permission.CREATE_TASKS)


    @PostMapping("/upgrade-or-downgrade")
    public ResponseEntity<?> upgradeOrDowngradeSubscription(
            @RequestParam String oldSubscriptionId,
            @RequestParam String newPriceId) throws UnauthorizedException {
        // RBAC: Only admins can upgrade/downgrade subscriptions
        User currentUser = securityUtil.resolveCurrentUser();
        authorizationService.requireAdmin(currentUser);
        try {
            Map<String, Object> result = stripeService.upgradeOrDowngradeSubscription(oldSubscriptionId, newPriceId);
            return ResponseEntity.ok(result);
        } catch (Exception e) {
            return ResponseEntity.badRequest().body(Map.of("error", e.getMessage()));
        }
    }
    
    /**
     * Endpoint to get the appropriate redirect URL after a successful payment
     * If portal=update is present, the user will be redirected to the subscription management page
     * Otherwise, they will be redirected to the dashboard
     */
    @RequirePermission(Permission.VIEW_ASSIGNED_PATIENTS)

    @GetMapping("/payment-redirect")
    public ResponseEntity<?> getPaymentRedirectUrl(
            @RequestParam(required = false) Boolean portal) {
        String redirectUrl;
        
        if (portal != null && portal) {
            redirectUrl = frontendBaseUrl + "/account/subscription";
        } else {
            redirectUrl = frontendBaseUrl + "/dashboard";
        }
        return ResponseEntity.ok(Map.of("redirectUrl", redirectUrl));
    }
}
