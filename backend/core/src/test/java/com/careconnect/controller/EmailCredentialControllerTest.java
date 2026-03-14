package com.careconnect.controller;

import com.careconnect.model.EmailCredential;
import com.careconnect.repository.EmailCredentialRepository;
import com.careconnect.security.AuthorizationService;
import com.careconnect.util.SecurityUtil;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;

import java.util.Optional;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.Mockito.*;

@ExtendWith(MockitoExtension.class)
class EmailCredentialControllerTest {

    @Mock
    private EmailCredentialRepository credRepo;
    @Mock
    private SecurityUtil securityUtil;
    @Mock
    private AuthorizationService authorizationService;

    @InjectMocks
    private EmailCredentialController controller;

    // ── shared constants ──────────────────────────────────────────────────────

    private static final String USER_ID = "user-123";

    // ── shared helpers ────────────────────────────────────────────────────────

    private EmailCredential credentialWithToken(String accessToken) {
        final EmailCredential cred = new EmailCredential();
        cred.setUserId(USER_ID);
        cred.setProvider(EmailCredential.Provider.GMAIL);
        cred.setAccessTokenEnc(accessToken);
        return cred;
    }

    // ── GET /email-credentials/status ─────────────────────────────────────────

    @Nested
    class GetConnectionStatus {

        @Test
        void returnsTrue_whenCredentialExistsWithValidAccessToken() throws Exception {
            final EmailCredential cred = credentialWithToken("valid-token");
            when(credRepo.findFirstByUserIdAndProviderOrderByIdDesc(USER_ID, EmailCredential.Provider.GMAIL))
                    .thenReturn(Optional.of(cred));

            final ResponseEntity<Boolean> response = controller.getConnectionStatus(USER_ID);

            assertThat(response.getStatusCode()).isEqualTo(HttpStatus.OK);
            assertThat(response.getBody()).isTrue();
        }

        @Test
        void returnsFalse_whenNoCredentialFound() throws Exception {
            when(credRepo.findFirstByUserIdAndProviderOrderByIdDesc(USER_ID, EmailCredential.Provider.GMAIL))
                    .thenReturn(Optional.empty());

            final ResponseEntity<Boolean> response = controller.getConnectionStatus(USER_ID);

            assertThat(response.getStatusCode()).isEqualTo(HttpStatus.OK);
            assertThat(response.getBody()).isFalse();
        }

        @Test
        void returnsFalse_whenAccessTokenIsNull() throws Exception {
            final EmailCredential cred = credentialWithToken(null);
            when(credRepo.findFirstByUserIdAndProviderOrderByIdDesc(USER_ID, EmailCredential.Provider.GMAIL))
                    .thenReturn(Optional.of(cred));

            final ResponseEntity<Boolean> response = controller.getConnectionStatus(USER_ID);

            assertThat(response.getStatusCode()).isEqualTo(HttpStatus.OK);
            assertThat(response.getBody()).isFalse();
        }

        @Test
        void returnsFalse_whenAccessTokenIsEmpty() throws Exception {
            final EmailCredential cred = credentialWithToken("");
            when(credRepo.findFirstByUserIdAndProviderOrderByIdDesc(USER_ID, EmailCredential.Provider.GMAIL))
                    .thenReturn(Optional.of(cred));

            final ResponseEntity<Boolean> response = controller.getConnectionStatus(USER_ID);

            assertThat(response.getStatusCode()).isEqualTo(HttpStatus.OK);
            assertThat(response.getBody()).isFalse();
        }

        @Test
        void alwaysQueriesGmailProvider() throws Exception {
            when(credRepo.findFirstByUserIdAndProviderOrderByIdDesc(USER_ID, EmailCredential.Provider.GMAIL))
                    .thenReturn(Optional.empty());

            controller.getConnectionStatus(USER_ID);

            verify(credRepo, times(1))
                    .findFirstByUserIdAndProviderOrderByIdDesc(USER_ID, EmailCredential.Provider.GMAIL);
            verify(credRepo, never())
                    .findFirstByUserIdAndProviderOrderByIdDesc(anyString(), eq(EmailCredential.Provider.OUTLOOK));
        }

        @Test
        void passesUserIdToRepository() throws Exception {
            final String specificUserId = "specific-user-456";
            when(credRepo.findFirstByUserIdAndProviderOrderByIdDesc(specificUserId, EmailCredential.Provider.GMAIL))
                    .thenReturn(Optional.empty());

            controller.getConnectionStatus(specificUserId);

            verify(credRepo).findFirstByUserIdAndProviderOrderByIdDesc(specificUserId, EmailCredential.Provider.GMAIL);
        }

        @Test
        void returnsTrue_whenAccessTokenIsWhitespace() throws Exception {
            // Whitespace is non-empty, so the filter passes and result is true
            final EmailCredential cred = credentialWithToken("   ");
            when(credRepo.findFirstByUserIdAndProviderOrderByIdDesc(USER_ID, EmailCredential.Provider.GMAIL))
                    .thenReturn(Optional.of(cred));

            final ResponseEntity<Boolean> response = controller.getConnectionStatus(USER_ID);

            assertThat(response.getStatusCode()).isEqualTo(HttpStatus.OK);
            assertThat(response.getBody()).isTrue();
        }

        @Test
        void responseBodyIsNeverNull() throws Exception {
            when(credRepo.findFirstByUserIdAndProviderOrderByIdDesc(USER_ID, EmailCredential.Provider.GMAIL))
                    .thenReturn(Optional.empty());

            final ResponseEntity<Boolean> response = controller.getConnectionStatus(USER_ID);

            assertThat(response.getBody()).isNotNull();
        }
    }
}
