package com.careconnect.security;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.*;

public class UserPrincipalTest {

    @Test
    @DisplayName("Should instantiate UserPrincipal")
    void shouldInstantiateUserPrincipal() {
        UserPrincipal principal = new UserPrincipal();
        assertNotNull(principal);
    }
}
