package com.careconnect.model;

import org.junit.jupiter.api.Test;

import static org.assertj.core.api.Assertions.assertThat;

class ResetTokenTest {

    @Test
    void defaultConstructor_createsInstance() throws Exception {
        final ResetToken token = new ResetToken();
        assertThat(token).isNotNull();
    }
}
