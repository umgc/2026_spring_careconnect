package com.careconnect.model;

import org.junit.jupiter.api.Test;

import static org.assertj.core.api.Assertions.assertThat;

class MetricTypeTest {

    @Test
    void values_containsAllExpected() throws Exception {
        assertThat(MetricType.values()).containsExactly(
                MetricType.HEART_RATE,
                MetricType.SPO2,
                MetricType.BLOOD_PRESSURE_SYS,
                MetricType.BLOOD_PRESSURE_DIA,
                MetricType.WEIGHT
        );
    }

    @Test
    void valueOf_returnsCorrectConstant() throws Exception {
        assertThat(MetricType.valueOf("HEART_RATE")).isEqualTo(MetricType.HEART_RATE);
        assertThat(MetricType.valueOf("SPO2")).isEqualTo(MetricType.SPO2);
        assertThat(MetricType.valueOf("BLOOD_PRESSURE_SYS")).isEqualTo(MetricType.BLOOD_PRESSURE_SYS);
        assertThat(MetricType.valueOf("BLOOD_PRESSURE_DIA")).isEqualTo(MetricType.BLOOD_PRESSURE_DIA);
        assertThat(MetricType.valueOf("WEIGHT")).isEqualTo(MetricType.WEIGHT);
    }
}
