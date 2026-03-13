package com.careconnect.config;

import com.stripe.Stripe;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.Test;

import java.lang.reflect.Field;

import static org.junit.jupiter.api.Assertions.*;

class StripeConfigTest {

    @AfterEach
    void cleanup() {
        // Reset static field after each test
        Stripe.apiKey = null;
    }

    // ==========================================
    // Should NOT set Stripe key if blank
    // ==========================================
    @Test
    void shouldNotSetStripeApiKeyIfSecretKeyBlank() throws Exception {

        StripeConfig config = new StripeConfig();

        // Inject blank secretKey using reflection
        Field field = StripeConfig.class.getDeclaredField("secretKey");
        field.setAccessible(true);
        field.set(config, "");

        config.init();

        assertNull(Stripe.apiKey);
    }

    // ==========================================
    // Should set Stripe key when provided
    // ==========================================
    @Test
    void shouldSetStripeApiKeyWhenSecretKeyPresent() throws Exception {

        StripeConfig config = new StripeConfig();

        Field field = StripeConfig.class.getDeclaredField("secretKey");
        field.setAccessible(true);
        field.set(config, "sk_test_123");

        config.init();

        assertEquals("sk_test_123", Stripe.apiKey);
    }
}
