package com.careconnect.controller;

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

    public SubscriptionController(ObjectProvider<SubscriptionService> subscriptionServiceProvider,
                                  ObjectProvider<StripeService> stripeServiceProvider,
                                  ObjectProvider<SubscriptionEnrichmentService> subscriptionEnrichmentServiceProvider) {
        this.subscriptionService = subscriptionServiceProvider.getIfAvailable();
        this.stripeService = stripeServiceProvider.getIfAvailable();
        this.subscriptionEnrichmentService = subscriptionEnrichmentServiceProvider.getIfAvailable();
    }

    private ResponseEntity<Map<String, String>> unavailable(String feature) {
        return ResponseEntity.status(HttpStatus.SERVICE_UNAVAILABLE)
                .body(Map.of("error", feature + " is unavailable in this environment"));
    }

    @GetMapping("/plans")
    public ResponseEntity<List<PlanDTO>> listPlans() {
        if (stripeService == null) {
            return ResponseEntity.ok(List.of());
        }
        return ResponseEntity.ok(stripeService.listPlans());
    }

    @GetMapping("/user/{userId}")
    public ResponseEntity<?> getUserSubscriptions(@PathVariable Long userId) {
        if (subscriptionEnrichmentService == null) {
            return unavailable("Subscription enrichment");
        }
        try {
            List<SubscriptionResponseDTO> subscriptionDTOs = subscriptionEnrichmentService.getEnrichedUserSubscriptions(userId);
            return ResponseEntity.ok(subscriptionDTOs);
        } catch (Exception e) {
            return ResponseEntity.badRequest().body(Map.of("error", e.getMessage()));
        }
    }

    @PostMapping("/{id}/cancel")
    public ResponseEntity<?> cancelSubscription(@PathVariable String id) {
        if (subscriptionService == null) {
            return unavailable("Subscription management");
        }
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

    @PostMapping("/create-direct")
    public ResponseEntity<?> createSubscriptionDirect(
            @RequestParam(required = false) String customerId,
            @RequestParam(required = false) String priceId,
            @RequestBody(required = false) Map<String, String> requestBody) {
        if (stripeService == null) {
            return unavailable("Stripe subscriptions");
        }
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

    @PostMapping("/upgrade-or-downgrade")
    public ResponseEntity<?> upgradeOrDowngradeSubscription(
            @RequestParam String oldSubscriptionId,
            @RequestParam String newPriceId) {
        if (stripeService == null) {
            return unavailable("Stripe subscriptions");
        }
        try {
            Map<String, Object> result = stripeService.upgradeOrDowngradeSubscription(oldSubscriptionId, newPriceId);
            return ResponseEntity.ok(result);
        } catch (Exception e) {
            return ResponseEntity.badRequest().body(Map.of("error", e.getMessage()));
        }
    }

    @PostMapping("/create-direct-for-user")
    public ResponseEntity<?> createSubscriptionDirectForUser(
            @RequestParam Long userId,
            @RequestParam String priceId) {
        if (subscriptionService == null) {
            return unavailable("Subscription management");
        }
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
}
