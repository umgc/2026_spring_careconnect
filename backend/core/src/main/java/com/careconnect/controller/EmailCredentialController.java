package com.careconnect.controller;

import com.careconnect.security.Permission;
import com.careconnect.security.RequirePermission;

import com.careconnect.model.User;
import com.careconnect.repository.EmailCredentialRepository;
import com.careconnect.model.EmailCredential;
import com.careconnect.security.AuthorizationService;
import com.careconnect.security.UnauthorizedException;
import com.careconnect.util.SecurityUtil;
import lombok.RequiredArgsConstructor;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;

@RestController
@RequestMapping("v1/api/email-credentials/status")
@RequiredArgsConstructor
public class EmailCredentialController {

    private final SecurityUtil securityUtil;
    private final AuthorizationService authorizationService;
    private final EmailCredentialRepository credRepo;

    @RequirePermission(Permission.VIEW_ASSIGNED_PATIENTS)


    @GetMapping("/email-credentials/status")
    public ResponseEntity<Boolean> getConnectionStatus(@RequestParam String userId) throws UnauthorizedException {
        User currentUser = securityUtil.resolveCurrentUser();
        authorizationService.requireAdminOrCaregiver(currentUser);

        boolean hasValidCredentials = credRepo
                .findFirstByUserIdAndProviderOrderByIdDesc(userId, EmailCredential.Provider.GMAIL)
                .filter(cred -> cred.getAccessTokenEnc() != null && !cred.getAccessTokenEnc().isEmpty())
                .isPresent();

        return ResponseEntity.ok(hasValidCredentials);
    }
}