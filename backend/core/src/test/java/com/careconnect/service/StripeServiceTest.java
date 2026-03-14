package com.careconnect.service;

import com.careconnect.dto.PlanDTO;
import com.careconnect.exception.AppException;
import com.careconnect.repository.SubscriptionRepository;
import com.careconnect.repository.UserRepository;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.mockito.*;
import org.springframework.http.*;
import org.springframework.test.util.ReflectionTestUtils;
import org.springframework.web.client.HttpClientErrorException;
import org.springframework.web.client.RestTemplate;

import java.nio.charset.StandardCharsets;
import java.util.*;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.ArgumentMatchers.*;
import static org.mockito.Mockito.*;

class StripeServiceTest {

    @Mock
    private UserRepository userRepository;

    @Mock
    private SubscriptionRepository subscriptionRepository;

    @InjectMocks
    private StripeService stripeService;


    @BeforeEach
    void setUp() throws Exception {
        MockitoAnnotations.openMocks(this);
        ReflectionTestUtils.setField(stripeService, "stripeSecretKey", "sk_test_secret");
        ReflectionTestUtils.setField(stripeService, "BASE_URL", "https://api.stripe.com/v1");
    }

    // ========== createCustomer ==========

    @Test
    @DisplayName("createCustomer_success_shouldReturnCustomerMap")
    void createCustomer_success_shouldReturnCustomerMap() throws Exception {
        final String responseBody = "{\"id\":\"cus_123\",\"email\":\"test@example.com\"}";

        try (MockedConstruction<RestTemplate> mocked = mockConstruction(RestTemplate.class, (mock, ctx) -> {
            when(mock.exchange(
                    eq("https://api.stripe.com/v1/customers"),
                    eq(HttpMethod.POST),
                    any(HttpEntity.class),
                    eq(String.class)
            )).thenReturn(new ResponseEntity<>(responseBody, HttpStatus.OK));
        })) {
            final Map<String, Object> result = stripeService.createCustomer("Test User", "test@example.com");

            assertNotNull(result);
            assertEquals("cus_123", result.get("id"));
            assertEquals("test@example.com", result.get("email"));
        }
    }

    @Test
    @DisplayName("createCustomer_parseFailure_shouldThrowAppException")
    void createCustomer_parseFailure_shouldThrowAppException() throws Exception {
        try (MockedConstruction<RestTemplate> mocked = mockConstruction(RestTemplate.class, (mock, ctx) -> {
            when(mock.exchange(
                    eq("https://api.stripe.com/v1/customers"),
                    eq(HttpMethod.POST),
                    any(HttpEntity.class),
                    eq(String.class)
            )).thenReturn(new ResponseEntity<>("not valid json{{{", HttpStatus.OK));
        })) {
            final AppException ex = assertThrows(AppException.class, () ->
                    stripeService.createCustomer("Test", "test@example.com"));
            assertTrue(ex.getMessage().contains("Failed to parse Stripe customer response"));
        }
    }

    // ========== createSubscription ==========

    @Test
    @DisplayName("createSubscription_withValidPriceId_shouldReturnSubscriptionMap")
    void createSubscription_withValidPriceId_shouldReturnSubscriptionMap() throws Exception {
        final String responseBody = "{\"id\":\"sub_123\",\"status\":\"active\"}";

        try (MockedConstruction<RestTemplate> mocked = mockConstruction(RestTemplate.class, (mock, ctx) -> {
            when(mock.exchange(
                    eq("https://api.stripe.com/v1/subscriptions"),
                    eq(HttpMethod.POST),
                    any(HttpEntity.class),
                    eq(String.class)
            )).thenReturn(new ResponseEntity<>(responseBody, HttpStatus.OK));
        })) {
            final Map<String, Object> result = stripeService.createSubscription("cus_123", "price_abc");

            assertNotNull(result);
            assertEquals("sub_123", result.get("id"));
        }
    }

    @Test
    @DisplayName("createSubscription_withPlanId_defaultPriceFound_shouldResolveAndCreate")
    void createSubscription_withPlanId_defaultPriceFound_shouldResolveAndCreate() throws Exception {
        final String planResponse = "{\"id\":\"plan_xyz\",\"default_price\":\"price_resolved\"}";
        final String subscriptionResponse = "{\"id\":\"sub_456\",\"status\":\"active\"}";

        try (MockedConstruction<RestTemplate> mocked = mockConstruction(RestTemplate.class, (mock, ctx) -> {
            // GET plan details
            when(mock.exchange(
                    eq("https://api.stripe.com/v1/products/plan_xyz"),
                    eq(HttpMethod.GET),
                    any(HttpEntity.class),
                    eq(String.class)
            )).thenReturn(new ResponseEntity<>(planResponse, HttpStatus.OK));

            // POST subscription
            when(mock.exchange(
                    eq("https://api.stripe.com/v1/subscriptions"),
                    eq(HttpMethod.POST),
                    any(HttpEntity.class),
                    eq(String.class)
            )).thenReturn(new ResponseEntity<>(subscriptionResponse, HttpStatus.OK));
        })) {
            final Map<String, Object> result = stripeService.createSubscription("cus_123", "plan_xyz");

            assertNotNull(result);
            assertEquals("sub_456", result.get("id"));
        }
    }

    @Test
    @DisplayName("createSubscription_withPlanId_noDefaultPrice_shouldLookupPrices")
    void createSubscription_withPlanId_noDefaultPrice_shouldLookupPrices() throws Exception {
        final String planResponse = "{\"id\":\"plan_abc\"}";
        final String pricesResponse = "{\"data\":[{\"id\":\"price_from_list\"}]}";
        final String subscriptionResponse = "{\"id\":\"sub_789\",\"status\":\"active\"}";

        try (MockedConstruction<RestTemplate> mocked = mockConstruction(RestTemplate.class, (mock, ctx) -> {
            when(mock.exchange(
                    eq("https://api.stripe.com/v1/products/plan_abc"),
                    eq(HttpMethod.GET),
                    any(HttpEntity.class),
                    eq(String.class)
            )).thenReturn(new ResponseEntity<>(planResponse, HttpStatus.OK));

            when(mock.exchange(
                    eq("https://api.stripe.com/v1/prices?product=plan_abc"),
                    eq(HttpMethod.GET),
                    any(HttpEntity.class),
                    eq(String.class)
            )).thenReturn(new ResponseEntity<>(pricesResponse, HttpStatus.OK));

            when(mock.exchange(
                    eq("https://api.stripe.com/v1/subscriptions"),
                    eq(HttpMethod.POST),
                    any(HttpEntity.class),
                    eq(String.class)
            )).thenReturn(new ResponseEntity<>(subscriptionResponse, HttpStatus.OK));
        })) {
            final Map<String, Object> result = stripeService.createSubscription("cus_123", "plan_abc");

            assertNotNull(result);
            assertEquals("sub_789", result.get("id"));
        }
    }

    @Test
    @DisplayName("createSubscription_withPlanId_noDefaultPriceAndEmptyPrices_shouldThrowAppException")
    void createSubscription_withPlanId_noDefaultPriceAndEmptyPrices_shouldThrowAppException() throws Exception {
        final String planResponse = "{\"id\":\"plan_empty\"}";
        final String pricesResponse = "{\"data\":[]}";

        try (MockedConstruction<RestTemplate> mocked = mockConstruction(RestTemplate.class, (mock, ctx) -> {
            when(mock.exchange(
                    eq("https://api.stripe.com/v1/products/plan_empty"),
                    eq(HttpMethod.GET),
                    any(HttpEntity.class),
                    eq(String.class)
            )).thenReturn(new ResponseEntity<>(planResponse, HttpStatus.OK));

            when(mock.exchange(
                    eq("https://api.stripe.com/v1/prices?product=plan_empty"),
                    eq(HttpMethod.GET),
                    any(HttpEntity.class),
                    eq(String.class)
            )).thenReturn(new ResponseEntity<>(pricesResponse, HttpStatus.OK));
        })) {
            final AppException ex = assertThrows(AppException.class, () ->
                    stripeService.createSubscription("cus_123", "plan_empty"));
            assertTrue(ex.getMessage().contains("Failed to find price for plan"));
        }
    }

    @Test
    @DisplayName("createSubscription_withPlanId_noDefaultPriceAndNullPrices_shouldThrowAppException")
    void createSubscription_withPlanId_noDefaultPriceAndNullPrices_shouldThrowAppException() throws Exception {
        final String planResponse = "{\"id\":\"plan_null\"}";
        final String pricesResponse = "{\"data\":null}";

        try (MockedConstruction<RestTemplate> mocked = mockConstruction(RestTemplate.class, (mock, ctx) -> {
            when(mock.exchange(
                    eq("https://api.stripe.com/v1/products/plan_null"),
                    eq(HttpMethod.GET),
                    any(HttpEntity.class),
                    eq(String.class)
            )).thenReturn(new ResponseEntity<>(planResponse, HttpStatus.OK));

            when(mock.exchange(
                    eq("https://api.stripe.com/v1/prices?product=plan_null"),
                    eq(HttpMethod.GET),
                    any(HttpEntity.class),
                    eq(String.class)
            )).thenReturn(new ResponseEntity<>(pricesResponse, HttpStatus.OK));
        })) {
            final AppException ex = assertThrows(AppException.class, () ->
                    stripeService.createSubscription("cus_123", "plan_null"));
            assertTrue(ex.getMessage().contains("Failed to find price for plan"));
        }
    }

    @Test
    @DisplayName("createSubscription_withPlanId_lookupThrowsException_shouldThrowAppException")
    void createSubscription_withPlanId_lookupThrowsException_shouldThrowAppException() throws Exception {
        try (MockedConstruction<RestTemplate> mocked = mockConstruction(RestTemplate.class, (mock, ctx) -> {
            when(mock.exchange(
                    eq("https://api.stripe.com/v1/products/plan_fail"),
                    eq(HttpMethod.GET),
                    any(HttpEntity.class),
                    eq(String.class)
            )).thenThrow(new RuntimeException("Network error"));
        })) {
            final AppException ex = assertThrows(AppException.class, () ->
                    stripeService.createSubscription("cus_123", "plan_fail"));
            assertTrue(ex.getMessage().contains("Failed to find price for plan"));
        }
    }

    @Test
    @DisplayName("createSubscription_httpClientError_withStripeErrorDetails_shouldThrowAppException")
    void createSubscription_httpClientError_withStripeErrorDetails_shouldThrowAppException() throws Exception {
        // When the inner try parses the error and throws AppException,
        // that AppException is caught by the inner catch (Exception ex) block which swallows it.
        // So the outer "throw new AppException(... Failed to create Stripe subscription ...)" is always reached.
        final String errorBody = "{\"error\":{\"message\":\"No such customer\",\"code\":\"resource_missing\",\"param\":\"customer\"}}";

        try (MockedConstruction<RestTemplate> mocked = mockConstruction(RestTemplate.class, (mock, ctx) -> {
            final HttpClientErrorException ex = HttpClientErrorException.create(
                    HttpStatus.BAD_REQUEST,
                    "Bad Request",
                    HttpHeaders.EMPTY,
                    errorBody.getBytes(StandardCharsets.UTF_8),
                    StandardCharsets.UTF_8
            );
            when(mock.exchange(
                    eq("https://api.stripe.com/v1/subscriptions"),
                    eq(HttpMethod.POST),
                    any(HttpEntity.class),
                    eq(String.class)
            )).thenThrow(ex);
        })) {
            final AppException thrown = assertThrows(AppException.class, () ->
                    stripeService.createSubscription("cus_invalid", "price_abc"));
            assertTrue(thrown.getMessage().contains("Failed to create Stripe subscription"));
        }
    }

    @Test
    @DisplayName("createSubscription_httpClientError_withUnparsableError_shouldThrowGenericAppException")
    void createSubscription_httpClientError_withUnparsableError_shouldThrowGenericAppException() throws Exception {
        final String errorBody = "not valid json";

        try (MockedConstruction<RestTemplate> mocked = mockConstruction(RestTemplate.class, (mock, ctx) -> {
            final HttpClientErrorException ex = HttpClientErrorException.create(
                    HttpStatus.BAD_REQUEST,
                    "Bad Request",
                    HttpHeaders.EMPTY,
                    errorBody.getBytes(StandardCharsets.UTF_8),
                    StandardCharsets.UTF_8
            );
            when(mock.exchange(
                    eq("https://api.stripe.com/v1/subscriptions"),
                    eq(HttpMethod.POST),
                    any(HttpEntity.class),
                    eq(String.class)
            )).thenThrow(ex);
        })) {
            final AppException thrown = assertThrows(AppException.class, () ->
                    stripeService.createSubscription("cus_invalid", "price_abc"));
            assertTrue(thrown.getMessage().contains("Failed to create Stripe subscription"));
        }
    }

    @Test
    @DisplayName("createSubscription_httpClientError_withNoErrorKey_shouldThrowGenericAppException")
    void createSubscription_httpClientError_withNoErrorKey_shouldThrowGenericAppException() throws Exception {
        final String errorBody = "{\"something\":\"else\"}";

        try (MockedConstruction<RestTemplate> mocked = mockConstruction(RestTemplate.class, (mock, ctx) -> {
            final HttpClientErrorException ex = HttpClientErrorException.create(
                    HttpStatus.BAD_REQUEST,
                    "Bad Request",
                    HttpHeaders.EMPTY,
                    errorBody.getBytes(StandardCharsets.UTF_8),
                    StandardCharsets.UTF_8
            );
            when(mock.exchange(
                    eq("https://api.stripe.com/v1/subscriptions"),
                    eq(HttpMethod.POST),
                    any(HttpEntity.class),
                    eq(String.class)
            )).thenThrow(ex);
        })) {
            final AppException thrown = assertThrows(AppException.class, () ->
                    stripeService.createSubscription("cus_invalid", "price_abc"));
            assertTrue(thrown.getMessage().contains("Failed to create Stripe subscription"));
        }
    }

    @Test
    @DisplayName("createSubscription_genericException_shouldThrowInternalServerError")
    void createSubscription_genericException_shouldThrowInternalServerError() throws Exception {
        try (MockedConstruction<RestTemplate> mocked = mockConstruction(RestTemplate.class, (mock, ctx) -> {
            when(mock.exchange(
                    eq("https://api.stripe.com/v1/subscriptions"),
                    eq(HttpMethod.POST),
                    any(HttpEntity.class),
                    eq(String.class)
            )).thenThrow(new RuntimeException("Unexpected error"));
        })) {
            final AppException thrown = assertThrows(AppException.class, () ->
                    stripeService.createSubscription("cus_123", "price_abc"));
            assertEquals(HttpStatus.INTERNAL_SERVER_ERROR, thrown.getStatus());
            assertTrue(thrown.getMessage().contains("Failed to create Stripe subscription"));
        }
    }

    // ========== updateSubscription ==========

    @Test
    @DisplayName("updateSubscription_success_shouldReturnUpdatedSubscriptionMap")
    void updateSubscription_success_shouldReturnUpdatedSubscriptionMap() throws Exception {
        final String getResponse = "{\"items\":{\"data\":[{\"id\":\"si_item1\"}]}}";
        final String updateResponse = "{\"id\":\"sub_123\",\"status\":\"active\"}";

        try (MockedConstruction<RestTemplate> mocked = mockConstruction(RestTemplate.class, (mock, ctx) -> {
            when(mock.exchange(
                    eq("https://api.stripe.com/v1/subscriptions/sub_123"),
                    eq(HttpMethod.GET),
                    any(HttpEntity.class),
                    eq(String.class)
            )).thenReturn(new ResponseEntity<>(getResponse, HttpStatus.OK));

            when(mock.exchange(
                    eq("https://api.stripe.com/v1/subscriptions/sub_123"),
                    eq(HttpMethod.POST),
                    any(HttpEntity.class),
                    eq(String.class)
            )).thenReturn(new ResponseEntity<>(updateResponse, HttpStatus.OK));
        })) {
            final Map<String, Object> result = stripeService.updateSubscription("sub_123", "price_new");

            assertNotNull(result);
            assertEquals("sub_123", result.get("id"));
        }
    }

    @Test
    @DisplayName("updateSubscription_failToGetItemId_shouldThrowAppException")
    void updateSubscription_failToGetItemId_shouldThrowAppException() throws Exception {
        final String getResponse = "invalid json";

        try (MockedConstruction<RestTemplate> mocked = mockConstruction(RestTemplate.class, (mock, ctx) -> {
            when(mock.exchange(
                    eq("https://api.stripe.com/v1/subscriptions/sub_123"),
                    eq(HttpMethod.GET),
                    any(HttpEntity.class),
                    eq(String.class)
            )).thenReturn(new ResponseEntity<>(getResponse, HttpStatus.OK));
        })) {
            final AppException ex = assertThrows(AppException.class, () ->
                    stripeService.updateSubscription("sub_123", "price_new"));
            assertTrue(ex.getMessage().contains("Failed to get subscription item ID"));
        }
    }

    @Test
    @DisplayName("updateSubscription_failToParseUpdateResponse_shouldThrowAppException")
    void updateSubscription_failToParseUpdateResponse_shouldThrowAppException() throws Exception {
        final String getResponse = "{\"items\":{\"data\":[{\"id\":\"si_item1\"}]}}";
        final String updateResponse = "not valid json{{{";

        try (MockedConstruction<RestTemplate> mocked = mockConstruction(RestTemplate.class, (mock, ctx) -> {
            when(mock.exchange(
                    eq("https://api.stripe.com/v1/subscriptions/sub_123"),
                    eq(HttpMethod.GET),
                    any(HttpEntity.class),
                    eq(String.class)
            )).thenReturn(new ResponseEntity<>(getResponse, HttpStatus.OK));

            when(mock.exchange(
                    eq("https://api.stripe.com/v1/subscriptions/sub_123"),
                    eq(HttpMethod.POST),
                    any(HttpEntity.class),
                    eq(String.class)
            )).thenReturn(new ResponseEntity<>(updateResponse, HttpStatus.OK));
        })) {
            final AppException ex = assertThrows(AppException.class, () ->
                    stripeService.updateSubscription("sub_123", "price_new"));
            assertTrue(ex.getMessage().contains("Failed to parse Stripe subscription update response"));
        }
    }

    // ========== cancelSubscription ==========

    @Test
    @DisplayName("cancelSubscription_success_shouldReturnCancelledSubscriptionMap")
    void cancelSubscription_success_shouldReturnCancelledSubscriptionMap() throws Exception {
        final String responseBody = "{\"id\":\"sub_123\",\"status\":\"canceled\"}";

        try (MockedConstruction<RestTemplate> mocked = mockConstruction(RestTemplate.class, (mock, ctx) -> {
            when(mock.exchange(
                    eq("https://api.stripe.com/v1/subscriptions/sub_123"),
                    eq(HttpMethod.DELETE),
                    any(HttpEntity.class),
                    eq(String.class)
            )).thenReturn(new ResponseEntity<>(responseBody, HttpStatus.OK));
        })) {
            final Map<String, Object> result = stripeService.cancelSubscription("sub_123");

            assertNotNull(result);
            assertEquals("canceled", result.get("status"));
        }
    }

    @Test
    @DisplayName("cancelSubscription_parseFailure_shouldThrowAppException")
    void cancelSubscription_parseFailure_shouldThrowAppException() throws Exception {
        try (MockedConstruction<RestTemplate> mocked = mockConstruction(RestTemplate.class, (mock, ctx) -> {
            when(mock.exchange(
                    eq("https://api.stripe.com/v1/subscriptions/sub_123"),
                    eq(HttpMethod.DELETE),
                    any(HttpEntity.class),
                    eq(String.class)
            )).thenReturn(new ResponseEntity<>("bad json", HttpStatus.OK));
        })) {
            final AppException ex = assertThrows(AppException.class, () ->
                    stripeService.cancelSubscription("sub_123"));
            assertTrue(ex.getMessage().contains("Failed to parse Stripe subscription cancel response"));
        }
    }

    // ========== getCustomer ==========

    @Test
    @DisplayName("getCustomer_success_shouldReturnCustomerMap")
    void getCustomer_success_shouldReturnCustomerMap() throws Exception {
        final String responseBody = "{\"id\":\"cus_456\",\"name\":\"Test Customer\"}";

        try (MockedConstruction<RestTemplate> mocked = mockConstruction(RestTemplate.class, (mock, ctx) -> {
            when(mock.exchange(
                    eq("https://api.stripe.com/v1/customers/cus_456"),
                    eq(HttpMethod.GET),
                    any(HttpEntity.class),
                    eq(String.class)
            )).thenReturn(new ResponseEntity<>(responseBody, HttpStatus.OK));
        })) {
            final Map<String, Object> result = stripeService.getCustomer("cus_456");

            assertNotNull(result);
            assertEquals("cus_456", result.get("id"));
            assertEquals("Test Customer", result.get("name"));
        }
    }

    @Test
    @DisplayName("getCustomer_parseFailure_shouldThrowAppException")
    void getCustomer_parseFailure_shouldThrowAppException() throws Exception {
        try (MockedConstruction<RestTemplate> mocked = mockConstruction(RestTemplate.class, (mock, ctx) -> {
            when(mock.exchange(
                    eq("https://api.stripe.com/v1/customers/cus_456"),
                    eq(HttpMethod.GET),
                    any(HttpEntity.class),
                    eq(String.class)
            )).thenReturn(new ResponseEntity<>("bad json", HttpStatus.OK));
        })) {
            final AppException ex = assertThrows(AppException.class, () ->
                    stripeService.getCustomer("cus_456"));
            assertTrue(ex.getMessage().contains("Failed to parse Stripe customer response"));
        }
    }

    // ========== listPlans ==========

    @Test
    @DisplayName("listPlans_success_shouldReturnPlanDTOList")
    void listPlans_success_shouldReturnPlanDTOList() throws Exception {
        final String responseBody = "{\"data\":[{\"id\":\"plan_1\",\"active\":true,\"amount\":999,\"currency\":\"usd\",\"interval\":\"month\",\"interval_count\":1,\"product\":\"prod_1\",\"nickname\":\"Basic\"}]}";

        try (MockedConstruction<RestTemplate> mocked = mockConstruction(RestTemplate.class, (mock, ctx) -> {
            when(mock.exchange(
                    eq("https://api.stripe.com/v1/plans"),
                    eq(HttpMethod.GET),
                    any(HttpEntity.class),
                    eq(String.class)
            )).thenReturn(new ResponseEntity<>(responseBody, HttpStatus.OK));
        })) {
            final List<PlanDTO> result = stripeService.listPlans();

            assertNotNull(result);
            assertEquals(1, result.size());
            assertEquals("plan_1", result.get(0).id());
            assertTrue(result.get(0).active());
            assertEquals(999, result.get(0).amount());
            assertEquals("usd", result.get(0).currency());
            assertEquals("month", result.get(0).interval());
            assertEquals(1, result.get(0).intervalCount());
            assertEquals("prod_1", result.get(0).product());
            assertEquals("Basic", result.get(0).nickname());
        }
    }

    @Test
    @DisplayName("listPlans_withNullNickname_shouldReturnNullNickname")
    void listPlans_withNullNickname_shouldReturnNullNickname() throws Exception {
        final String responseBody = "{\"data\":[{\"id\":\"plan_2\",\"active\":false,\"amount\":0,\"currency\":\"eur\",\"interval\":\"year\",\"interval_count\":1,\"product\":\"prod_2\",\"nickname\":null}]}";

        try (MockedConstruction<RestTemplate> mocked = mockConstruction(RestTemplate.class, (mock, ctx) -> {
            when(mock.exchange(
                    eq("https://api.stripe.com/v1/plans"),
                    eq(HttpMethod.GET),
                    any(HttpEntity.class),
                    eq(String.class)
            )).thenReturn(new ResponseEntity<>(responseBody, HttpStatus.OK));
        })) {
            final List<PlanDTO> result = stripeService.listPlans();

            assertNotNull(result);
            assertEquals(1, result.size());
            assertNull(result.get(0).nickname());
        }
    }

    @Test
    @DisplayName("listPlans_emptyDataArray_shouldReturnEmptyList")
    void listPlans_emptyDataArray_shouldReturnEmptyList() throws Exception {
        final String responseBody = "{\"data\":[]}";

        try (MockedConstruction<RestTemplate> mocked = mockConstruction(RestTemplate.class, (mock, ctx) -> {
            when(mock.exchange(
                    eq("https://api.stripe.com/v1/plans"),
                    eq(HttpMethod.GET),
                    any(HttpEntity.class),
                    eq(String.class)
            )).thenReturn(new ResponseEntity<>(responseBody, HttpStatus.OK));
        })) {
            final List<PlanDTO> result = stripeService.listPlans();

            assertNotNull(result);
            assertTrue(result.isEmpty());
        }
    }

    @Test
    @DisplayName("listPlans_nullDataNode_shouldReturnEmptyList")
    void listPlans_nullDataNode_shouldReturnEmptyList() throws Exception {
        final String responseBody = "{\"other\":\"field\"}";

        try (MockedConstruction<RestTemplate> mocked = mockConstruction(RestTemplate.class, (mock, ctx) -> {
            when(mock.exchange(
                    eq("https://api.stripe.com/v1/plans"),
                    eq(HttpMethod.GET),
                    any(HttpEntity.class),
                    eq(String.class)
            )).thenReturn(new ResponseEntity<>(responseBody, HttpStatus.OK));
        })) {
            final List<PlanDTO> result = stripeService.listPlans();

            assertNotNull(result);
            assertTrue(result.isEmpty());
        }
    }

    @Test
    @DisplayName("listPlans_parseFailure_shouldThrowRuntimeException")
    void listPlans_parseFailure_shouldThrowRuntimeException() throws Exception {
        try (MockedConstruction<RestTemplate> mocked = mockConstruction(RestTemplate.class, (mock, ctx) -> {
            when(mock.exchange(
                    eq("https://api.stripe.com/v1/plans"),
                    eq(HttpMethod.GET),
                    any(HttpEntity.class),
                    eq(String.class)
            )).thenReturn(new ResponseEntity<>("bad json", HttpStatus.OK));
        })) {
            final RuntimeException ex = assertThrows(RuntimeException.class, () ->
                    stripeService.listPlans());
            assertTrue(ex.getMessage().contains("Failed to parse plans"));
        }
    }

    // ========== listProducts ==========

    @Test
    @DisplayName("listProducts_success_shouldReturnResponseBody")
    void listProducts_success_shouldReturnResponseBody() throws Exception {
        final String responseBody = "{\"data\":[{\"id\":\"prod_1\"}]}";

        try (MockedConstruction<RestTemplate> mocked = mockConstruction(RestTemplate.class, (mock, ctx) -> {
            when(mock.exchange(
                    eq("https://api.stripe.com/v1/products"),
                    eq(HttpMethod.GET),
                    any(HttpEntity.class),
                    eq(String.class)
            )).thenReturn(new ResponseEntity<>(responseBody, HttpStatus.OK));
        })) {
            final String result = stripeService.listProducts();

            assertEquals(responseBody, result);
        }
    }

    // ========== listSubscriptions ==========

    @Test
    @DisplayName("listSubscriptions_withCustomerId_shouldAppendCustomerParam")
    void listSubscriptions_withCustomerId_shouldAppendCustomerParam() throws Exception {
        final String responseBody = "{\"data\":[]}";

        try (MockedConstruction<RestTemplate> mocked = mockConstruction(RestTemplate.class, (mock, ctx) -> {
            when(mock.exchange(
                    eq("https://api.stripe.com/v1/subscriptions?customer=cus_123"),
                    eq(HttpMethod.GET),
                    any(HttpEntity.class),
                    eq(String.class)
            )).thenReturn(new ResponseEntity<>(responseBody, HttpStatus.OK));
        })) {
            final String result = stripeService.listSubscriptions("cus_123");

            assertEquals(responseBody, result);
        }
    }

    @Test
    @DisplayName("listSubscriptions_withNullCustomerId_shouldNotAppendParam")
    void listSubscriptions_withNullCustomerId_shouldNotAppendParam() throws Exception {
        final String responseBody = "{\"data\":[]}";

        try (MockedConstruction<RestTemplate> mocked = mockConstruction(RestTemplate.class, (mock, ctx) -> {
            when(mock.exchange(
                    eq("https://api.stripe.com/v1/subscriptions"),
                    eq(HttpMethod.GET),
                    any(HttpEntity.class),
                    eq(String.class)
            )).thenReturn(new ResponseEntity<>(responseBody, HttpStatus.OK));
        })) {
            final String result = stripeService.listSubscriptions(null);

            assertEquals(responseBody, result);
        }
    }

    @Test
    @DisplayName("listSubscriptions_withEmptyCustomerId_shouldNotAppendParam")
    void listSubscriptions_withEmptyCustomerId_shouldNotAppendParam() throws Exception {
        final String responseBody = "{\"data\":[]}";

        try (MockedConstruction<RestTemplate> mocked = mockConstruction(RestTemplate.class, (mock, ctx) -> {
            when(mock.exchange(
                    eq("https://api.stripe.com/v1/subscriptions"),
                    eq(HttpMethod.GET),
                    any(HttpEntity.class),
                    eq(String.class)
            )).thenReturn(new ResponseEntity<>(responseBody, HttpStatus.OK));
        })) {
            final String result = stripeService.listSubscriptions("");

            assertEquals(responseBody, result);
        }
    }

    // ========== getSubscription ==========

    @Test
    @DisplayName("getSubscription_success_shouldReturnResponseBody")
    void getSubscription_success_shouldReturnResponseBody() throws Exception {
        final String responseBody = "{\"id\":\"sub_123\",\"status\":\"active\"}";

        try (MockedConstruction<RestTemplate> mocked = mockConstruction(RestTemplate.class, (mock, ctx) -> {
            when(mock.exchange(
                    eq("https://api.stripe.com/v1/subscriptions/sub_123"),
                    eq(HttpMethod.GET),
                    any(HttpEntity.class),
                    eq(String.class)
            )).thenReturn(new ResponseEntity<>(responseBody, HttpStatus.OK));
        })) {
            final String result = stripeService.getSubscription("sub_123");

            assertEquals(responseBody, result);
        }
    }

    // ========== getCustomerActiveSubscriptions ==========

    @Test
    @DisplayName("getCustomerActiveSubscriptions_success_shouldReturnResponseBody")
    void getCustomerActiveSubscriptions_success_shouldReturnResponseBody() throws Exception {
        final String responseBody = "{\"data\":[{\"id\":\"sub_active\"}]}";

        try (MockedConstruction<RestTemplate> mocked = mockConstruction(RestTemplate.class, (mock, ctx) -> {
            when(mock.exchange(
                    eq("https://api.stripe.com/v1/subscriptions?customer=cus_789&status=active"),
                    eq(HttpMethod.GET),
                    any(HttpEntity.class),
                    eq(String.class)
            )).thenReturn(new ResponseEntity<>(responseBody, HttpStatus.OK));
        })) {
            final String result = stripeService.getCustomerActiveSubscriptions("cus_789");

            assertEquals(responseBody, result);
        }
    }

    // ========== searchSubscriptions ==========

    @Test
    @DisplayName("searchSubscriptions_success_shouldReturnResponseBody")
    void searchSubscriptions_success_shouldReturnResponseBody() throws Exception {
        final String responseBody = "{\"data\":[]}";

        try (MockedConstruction<RestTemplate> mocked = mockConstruction(RestTemplate.class, (mock, ctx) -> {
            when(mock.exchange(
                    eq("https://api.stripe.com/v1/subscriptions/search"),
                    eq(HttpMethod.GET),
                    any(HttpEntity.class),
                    eq(String.class)
            )).thenReturn(new ResponseEntity<>(responseBody, HttpStatus.OK));
        })) {
            final String result = stripeService.searchSubscriptions("status:'active'");

            assertEquals(responseBody, result);
        }
    }

    // ========== upgradeOrDowngradeSubscription ==========

    @Test
    @DisplayName("upgradeOrDowngradeSubscription_customerAsString_shouldCancelAndCreateNew")
    void upgradeOrDowngradeSubscription_customerAsString_shouldCancelAndCreateNew() throws Exception {
        final String oldSubJson = "{\"id\":\"sub_old\",\"customer\":\"cus_upgrade\",\"status\":\"active\"}";
        final String cancelResponse = "{\"id\":\"sub_old\",\"status\":\"canceled\"}";
        final String newSubResponse = "{\"id\":\"sub_new\",\"status\":\"active\"}";

        try (MockedConstruction<RestTemplate> mocked = mockConstruction(RestTemplate.class, (mock, ctx) -> {
            // getSubscription (GET)
            when(mock.exchange(
                    eq("https://api.stripe.com/v1/subscriptions/sub_old"),
                    eq(HttpMethod.GET),
                    any(HttpEntity.class),
                    eq(String.class)
            )).thenReturn(new ResponseEntity<>(oldSubJson, HttpStatus.OK));

            // cancelSubscription (DELETE)
            when(mock.exchange(
                    eq("https://api.stripe.com/v1/subscriptions/sub_old"),
                    eq(HttpMethod.DELETE),
                    any(HttpEntity.class),
                    eq(String.class)
            )).thenReturn(new ResponseEntity<>(cancelResponse, HttpStatus.OK));

            // createSubscription (POST)
            when(mock.exchange(
                    eq("https://api.stripe.com/v1/subscriptions"),
                    eq(HttpMethod.POST),
                    any(HttpEntity.class),
                    eq(String.class)
            )).thenReturn(new ResponseEntity<>(newSubResponse, HttpStatus.OK));
        })) {
            final Map<String, Object> result = stripeService.upgradeOrDowngradeSubscription("sub_old", "price_new");

            assertNotNull(result);
            assertEquals("sub_new", result.get("id"));
        }
    }

    @Test
    @DisplayName("upgradeOrDowngradeSubscription_customerAsMap_shouldExtractIdAndProceed")
    void upgradeOrDowngradeSubscription_customerAsMap_shouldExtractIdAndProceed() throws Exception {
        final String oldSubJson = "{\"id\":\"sub_old2\",\"customer\":{\"id\":\"cus_map_id\"},\"status\":\"active\"}";
        final String cancelResponse = "{\"id\":\"sub_old2\",\"status\":\"canceled\"}";
        final String newSubResponse = "{\"id\":\"sub_new2\",\"status\":\"active\"}";

        try (MockedConstruction<RestTemplate> mocked = mockConstruction(RestTemplate.class, (mock, ctx) -> {
            when(mock.exchange(
                    eq("https://api.stripe.com/v1/subscriptions/sub_old2"),
                    eq(HttpMethod.GET),
                    any(HttpEntity.class),
                    eq(String.class)
            )).thenReturn(new ResponseEntity<>(oldSubJson, HttpStatus.OK));

            when(mock.exchange(
                    eq("https://api.stripe.com/v1/subscriptions/sub_old2"),
                    eq(HttpMethod.DELETE),
                    any(HttpEntity.class),
                    eq(String.class)
            )).thenReturn(new ResponseEntity<>(cancelResponse, HttpStatus.OK));

            when(mock.exchange(
                    eq("https://api.stripe.com/v1/subscriptions"),
                    eq(HttpMethod.POST),
                    any(HttpEntity.class),
                    eq(String.class)
            )).thenReturn(new ResponseEntity<>(newSubResponse, HttpStatus.OK));
        })) {
            final Map<String, Object> result = stripeService.upgradeOrDowngradeSubscription("sub_old2", "price_new");

            assertNotNull(result);
            assertEquals("sub_new2", result.get("id"));
        }
    }

    @Test
    @DisplayName("upgradeOrDowngradeSubscription_customerUnexpectedFormat_shouldThrowAppException")
    void upgradeOrDowngradeSubscription_customerUnexpectedFormat_shouldThrowAppException() throws Exception {
        final String oldSubJson = "{\"id\":\"sub_bad\",\"customer\":12345}";

        try (MockedConstruction<RestTemplate> mocked = mockConstruction(RestTemplate.class, (mock, ctx) -> {
            when(mock.exchange(
                    eq("https://api.stripe.com/v1/subscriptions/sub_bad"),
                    eq(HttpMethod.GET),
                    any(HttpEntity.class),
                    eq(String.class)
            )).thenReturn(new ResponseEntity<>(oldSubJson, HttpStatus.OK));
        })) {
            final AppException ex = assertThrows(AppException.class, () ->
                    stripeService.upgradeOrDowngradeSubscription("sub_bad", "price_new"));
            assertTrue(ex.getMessage().contains("Unexpected customer format"));
        }
    }

    @Test
    @DisplayName("upgradeOrDowngradeSubscription_invalidJson_shouldThrowAppException")
    void upgradeOrDowngradeSubscription_invalidJson_shouldThrowAppException() throws Exception {
        try (MockedConstruction<RestTemplate> mocked = mockConstruction(RestTemplate.class, (mock, ctx) -> {
            when(mock.exchange(
                    eq("https://api.stripe.com/v1/subscriptions/sub_invalid"),
                    eq(HttpMethod.GET),
                    any(HttpEntity.class),
                    eq(String.class)
            )).thenReturn(new ResponseEntity<>("bad json", HttpStatus.OK));
        })) {
            final AppException ex = assertThrows(AppException.class, () ->
                    stripeService.upgradeOrDowngradeSubscription("sub_invalid", "price_new"));
            assertTrue(ex.getMessage().contains("Failed to parse subscription JSON"));
        }
    }

    // ========== listPlans - missing nickname field ==========

    @Test
    @DisplayName("listPlans_withMissingNicknameField_shouldReturnNullNickname")
    void listPlans_withMissingNicknameField_shouldReturnNullNickname() throws Exception {
        final String responseBody = "{\"data\":[{\"id\":\"plan_3\",\"active\":true,\"amount\":500,\"currency\":\"usd\",\"interval\":\"month\",\"interval_count\":1,\"product\":\"prod_3\"}]}";

        try (MockedConstruction<RestTemplate> mocked = mockConstruction(RestTemplate.class, (mock, ctx) -> {
            when(mock.exchange(
                    eq("https://api.stripe.com/v1/plans"),
                    eq(HttpMethod.GET),
                    any(HttpEntity.class),
                    eq(String.class)
            )).thenReturn(new ResponseEntity<>(responseBody, HttpStatus.OK));
        })) {
            final List<PlanDTO> result = stripeService.listPlans();

            assertNotNull(result);
            assertEquals(1, result.size());
            assertNull(result.get(0).nickname());
        }
    }
}
