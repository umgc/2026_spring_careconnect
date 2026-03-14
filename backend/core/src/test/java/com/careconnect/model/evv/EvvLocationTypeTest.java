package com.careconnect.model.evv;

import org.junit.jupiter.api.Test;

import static org.assertj.core.api.Assertions.assertThat;

class EvvLocationTypeTest {

    @Test
    void values_containsAllExpected() throws Exception {
        assertThat(EvvLocationType.values()).containsExactly(
                EvvLocationType.GPS,
                EvvLocationType.PATIENT_ADDRESS
        );
    }

    @Test
    void valueOf_returnsCorrectConstant() throws Exception {
        assertThat(EvvLocationType.valueOf("GPS")).isEqualTo(EvvLocationType.GPS);
        assertThat(EvvLocationType.valueOf("PATIENT_ADDRESS")).isEqualTo(EvvLocationType.PATIENT_ADDRESS);
    }
}
