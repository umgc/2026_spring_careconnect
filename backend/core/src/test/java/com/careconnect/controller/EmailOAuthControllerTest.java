package com.careconnect.controller;

import com.careconnect.repository.EmailCredentialRepository;
import com.careconnect.service.GoogleOAuthService;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;

import java.net.URI;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.Mockito.*;

@ExtendWith(MockitoExtension.class)
class EmailOAuthControllerTest {

    @Mock private GoogleOAuthService googleOAuthService;
    @Mock private EmailCredentialRepository credRepo;

    @InjectMocks
    private EmailOAuthController controller;

    // ── shared constants ──────────────────────────────────────────────────────

    private static final String USER_ID      = "user-123";
    private static final String CLIENT_ID    = "test-client-id";
    private static final String REDIRECT_URI = "http://localhost/callback";
    private static final String SCOPE        = "openid email";
    private static final String FRONTEND_URL = "http://localhost:3000";
    private static final String AUTH_CODE    = "auth-code-abc";

    // @Value fields have package-private access; set them directly from the same package
    @BeforeEach
    void setUp() throws Exception {
        controller.clientId       = CLIENT_ID;
        controller.redirectUri    = REDIRECT_URI;
        controller.scope          = SCOPE;
        controller.frontendBaseUrl = FRONTEND_URL;
    }

    // ── GET /oauth/google/start ───────────────────────────────────────────────

    @Nested
    class Start {

        @Test
        void returns302Found() throws Exception {
            final ResponseEntity<Void> response = controller.start(USER_ID, null);
            assertThat(response.getStatusCode()).isEqualTo(HttpStatus.FOUND);
        }

        @Test
        void locationHeaderIsPresent() throws Exception {
            final ResponseEntity<Void> response = controller.start(USER_ID, null);
            assertThat(response.getHeaders().getLocation()).isNotNull();
        }

        @Test
        void redirectsToGoogleAuthEndpoint() throws Exception {
            final URI location = controller.start(USER_ID, null).getHeaders().getLocation();
            assertThat(location.toString()).contains("accounts.google.com/o/oauth2/v2/auth");
        }

        @Test
        void includesResponseTypeCode() throws Exception {
            final URI location = controller.start(USER_ID, null).getHeaders().getLocation();
            assertThat(location.toString()).contains("response_type=code");
        }

        @Test
        void includesClientId() throws Exception {
            final URI location = controller.start(USER_ID, null).getHeaders().getLocation();
            assertThat(location.toString()).contains(CLIENT_ID);
        }

        @Test
        void includesAccessTypeOffline() throws Exception {
            final URI location = controller.start(USER_ID, null).getHeaders().getLocation();
            assertThat(location.toString()).contains("access_type=offline");
        }

        @Test
        void includesPromptConsent() throws Exception {
            final URI location = controller.start(USER_ID, null).getHeaders().getLocation();
            assertThat(location.toString()).contains("prompt=consent");
        }

        @Test
        void stateEncodeUserIdOnly_whenReturnUrlIsNull() throws Exception {
            final URI location = controller.start(USER_ID, null).getHeaders().getLocation();
            // state = "u:user-123" → UriUtils-encoded: "u%3Auser-123"
            assertThat(location.toString()).contains("u%3A" + USER_ID);
        }

        @Test
        void stateEncodeUserIdOnly_whenReturnUrlIsEmpty() throws Exception {
            final URI location = controller.start(USER_ID, "").getHeaders().getLocation();
            // empty returnUrl is treated like null; pipe separator never appended
            assertThat(location.toString()).doesNotContain("r%3A");
        }

        @Test
        void stateIncludesReturnUrl_whenReturnUrlProvided() throws Exception {
            final URI location = controller.start(USER_ID, "http://frontend/page").getHeaders().getLocation();
            // state = "u:user-123|r:http://frontend/page" → contains encoded "r:"
            assertThat(location.toString()).contains("r%3A");
        }

        @Test
        void doesNotInteractWithRepository() throws Exception {
            controller.start(USER_ID, null);
            verifyNoInteractions(credRepo);
        }

        @Test
        void doesNotInteractWithGoogleOAuthService() throws Exception {
            controller.start(USER_ID, null);
            verifyNoInteractions(googleOAuthService);
        }

        @Test
        void differentUserIdsProduceDifferentStateValues() throws Exception {
            final URI location1 = controller.start("user-aaa", null).getHeaders().getLocation();
            final URI location2 = controller.start("user-bbb", null).getHeaders().getLocation();
            assertThat(location1.toString()).isNotEqualTo(location2.toString());
        }
    }

    // ── GET /oauth/google/callback ────────────────────────────────────────────

    @Nested
    class Callback {

        // ── happy path: returnUrl provided ───────────────────────────────────

        @Test
        void returns302_whenSuccessful() throws Exception {
            final String state = "u:" + USER_ID + "|r:http://localhost:3000/page";
            final ResponseEntity<Void> response = controller.callback(AUTH_CODE, state);
            assertThat(response.getStatusCode()).isEqualTo(HttpStatus.FOUND);
        }

        @Test
        void redirectsToReturnUrl_whenReturnUrlPresentInState() throws Exception {
            final String returnUrl = "http://localhost:3000/settings";
            final String state = "u:" + USER_ID + "|r:" + returnUrl;

            final URI location = controller.callback(AUTH_CODE, state).getHeaders().getLocation();

            assertThat(location).isNotNull();
            assertThat(location.toString()).isEqualTo(returnUrl);
        }

        @Test
        void callsGoogleOAuthServiceExchange_withParsedUserIdAndCode() throws Exception {
            final String state = "u:" + USER_ID + "|r:http://localhost:3000/page";
            controller.callback(AUTH_CODE, state);
            verify(googleOAuthService).exchange(USER_ID, AUTH_CODE);
        }

        // ── happy path: no returnUrl in state ────────────────────────────────

        @Test
        void redirectsToFallbackUrl_whenNoReturnUrlInState() throws Exception {
            final String state = "u:" + USER_ID;
            final URI location = controller.callback(AUTH_CODE, state).getHeaders().getLocation();
            assertThat(location.toString()).isEqualTo(FRONTEND_URL + "/usps-test");
        }

        @Test
        void callsGoogleOAuthServiceExchange_whenNoReturnUrlInState() throws Exception {
            final String state = "u:" + USER_ID;
            controller.callback(AUTH_CODE, state);
            verify(googleOAuthService).exchange(USER_ID, AUTH_CODE);
        }

        // ── returnUrl is empty string in state ────────────────────────────────

        @Test
        void redirectsToFallbackUrl_whenReturnUrlIsEmptyStringInState() throws Exception {
            // state "u:user-123|r:" → returnUrl = "" → treated as missing
            final String state = "u:" + USER_ID + "|r:";
            final URI location = controller.callback(AUTH_CODE, state).getHeaders().getLocation();
            assertThat(location.toString()).isEqualTo(FRONTEND_URL + "/usps-test");
        }

        // ── URL validation fallback ───────────────────────────────────────────

        @Test
        void redirectsToFallbackUrl_whenReturnUrlIsMalformedUrl() throws Exception {
            // "not-a-valid-url" has no scheme → new java.net.URL() throws MalformedURLException
            final String state = "u:" + USER_ID + "|r:not-a-valid-url";
            final URI location = controller.callback(AUTH_CODE, state).getHeaders().getLocation();
            assertThat(location.toString()).isEqualTo(FRONTEND_URL + "/usps-test");
        }

        @Test
        void exchangeStillCalledBeforeUrlValidation_whenReturnUrlIsMalformed() throws Exception {
            final String state = "u:" + USER_ID + "|r:not-a-valid-url";
            controller.callback(AUTH_CODE, state);
            verify(googleOAuthService).exchange(USER_ID, AUTH_CODE);
        }

        // ── exchange() throws ─────────────────────────────────────────────────

        @Test
        void redirectsToSettingsErrorUrl_whenGoogleOAuthServiceThrows() throws Exception {
            final String state = "u:" + USER_ID;
            doThrow(new RuntimeException("token exchange failed"))
                    .when(googleOAuthService).exchange(USER_ID, AUTH_CODE);

            final URI location = controller.callback(AUTH_CODE, state).getHeaders().getLocation();

            assertThat(location.toString()).startsWith("/settings?error=");
        }

        @Test
        void errorUrlContainsEncodedExceptionMessage_whenGoogleOAuthServiceThrows() throws Exception {
            final String state = "u:" + USER_ID;
            doThrow(new RuntimeException("token exchange failed"))
                    .when(googleOAuthService).exchange(USER_ID, AUTH_CODE);

            final URI location = controller.callback(AUTH_CODE, state).getHeaders().getLocation();

            // URLEncoder encodes spaces as '+' and colons as '%3A'
            assertThat(location.toString()).contains("token+exchange+failed");
        }

        @Test
        void returns302_whenGoogleOAuthServiceThrows() throws Exception {
            final String state = "u:" + USER_ID;
            doThrow(new RuntimeException("oops")).when(googleOAuthService).exchange(USER_ID, AUTH_CODE);

            final ResponseEntity<Void> response = controller.callback(AUTH_CODE, state);

            assertThat(response.getStatusCode()).isEqualTo(HttpStatus.FOUND);
        }

        // ── state parsing failures ────────────────────────────────────────────

        @Test
        void redirectsToSettingsErrorUrl_whenStateMissingUserId() throws Exception {
            // state has r: but no u: → parseStateData throws IllegalArgumentException
            final String state = "r:http://somewhere.com";

            final URI location = controller.callback(AUTH_CODE, state).getHeaders().getLocation();

            assertThat(location.toString()).startsWith("/settings?error=");
        }

        @Test
        void doesNotCallExchange_whenStateMissingUserId() throws Exception {
            final String state = "r:http://somewhere.com";
            controller.callback(AUTH_CODE, state);
            verifyNoInteractions(googleOAuthService);
        }

        @Test
        void redirectsToSettingsErrorUrl_whenStateIsNull() throws Exception {
            // null state → parseStateData throws IllegalArgumentException("Invalid state: null")
            final URI location = controller.callback(AUTH_CODE, null).getHeaders().getLocation();
            assertThat(location.toString()).startsWith("/settings?error=");
        }

        @Test
        void doesNotCallExchange_whenStateIsNull() throws Exception {
            controller.callback(AUTH_CODE, null);
            verifyNoInteractions(googleOAuthService);
        }

        @Test
        void credRepoNeverCalledDirectly_inCallbackFlow() throws Exception {
            final String state = "u:" + USER_ID;
            controller.callback(AUTH_CODE, state);
            verifyNoInteractions(credRepo);
        }
    }
}
