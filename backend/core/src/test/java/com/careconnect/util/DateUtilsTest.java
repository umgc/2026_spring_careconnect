package com.careconnect.util;

import org.junit.jupiter.api.Test;

import static org.assertj.core.api.Assertions.assertThat;

class DateUtilsTest {

    @Test
    void instantiation_succeeds() throws Exception {
        assertThat(new DateUtils()).isNotNull();
    }
}
