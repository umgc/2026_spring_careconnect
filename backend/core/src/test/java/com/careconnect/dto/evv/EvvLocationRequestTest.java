package com.careconnect.dto.evv;

import com.careconnect.model.evv.EvvLocationRole;
import com.careconnect.model.evv.EvvLocationType;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.junit.jupiter.MockitoExtension;

import java.math.BigDecimal;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

@ExtendWith(MockitoExtension.class)
class EvvLocationRequestTest {

    // ─── No-arg constructor ───────────────────────────────────────────────────

    @Test
    void noArgConstructor_createsInstance() throws Exception {
        final EvvLocationRequest request = new EvvLocationRequest();

        assertThat(request).isNotNull();
        assertThat(request.getEvvRecordId()).isNull();
        assertThat(request.getRole()).isNull();
        assertThat(request.getType()).isNull();
        assertThat(request.getCoords()).isNull();
    }

    // ─── All-args constructor ─────────────────────────────────────────────────

    @Test
    void allArgsConstructor_setsAllFields() throws Exception {
        final EvvLocationRequest.CoordinatesDto coords = new EvvLocationRequest.CoordinatesDto(
                new BigDecimal("38.8951"), new BigDecimal("-77.0364"), new BigDecimal("5.0"));

        final EvvLocationRequest request = new EvvLocationRequest(
                1L, EvvLocationRole.CHECK_IN, EvvLocationType.GPS, coords, null, null);

        assertThat(request.getEvvRecordId()).isEqualTo(1L);
        assertThat(request.getRole()).isEqualTo(EvvLocationRole.CHECK_IN);
        assertThat(request.getType()).isEqualTo(EvvLocationType.GPS);
        assertThat(request.getCoords()).isEqualTo(coords);
    }

    // ─── Builder ──────────────────────────────────────────────────────────────

    @Test
    void builder_allFields_setsCorrectly() throws Exception {
        final EvvLocationRequest.CoordinatesDto coords = EvvLocationRequest.CoordinatesDto.builder()
                .lat(new BigDecimal("39.0"))
                .lng(new BigDecimal("-76.0"))
                .accuracyM(new BigDecimal("3.5"))
                .build();

        final EvvLocationRequest request = EvvLocationRequest.builder()
                .evvRecordId(2L)
                .role(EvvLocationRole.CHECK_OUT)
                .type(EvvLocationType.GPS)
                .coords(coords)
                .build();

        assertThat(request.getEvvRecordId()).isEqualTo(2L);
        assertThat(request.getRole()).isEqualTo(EvvLocationRole.CHECK_OUT);
        assertThat(request.getType()).isEqualTo(EvvLocationType.GPS);
        assertThat(request.getCoords()).isEqualTo(coords);
    }

    // ─── Setters ──────────────────────────────────────────────────────────────

    @Test
    void setters_updateFields() throws Exception {
        final EvvLocationRequest request = new EvvLocationRequest();
        final EvvLocationRequest.CoordinatesDto coords = new EvvLocationRequest.CoordinatesDto();

        request.setEvvRecordId(5L);
        request.setRole(EvvLocationRole.CHECK_IN);
        request.setType(EvvLocationType.PATIENT_ADDRESS);
        request.setCoords(coords);

        assertThat(request.getEvvRecordId()).isEqualTo(5L);
        assertThat(request.getRole()).isEqualTo(EvvLocationRole.CHECK_IN);
        assertThat(request.getType()).isEqualTo(EvvLocationType.PATIENT_ADDRESS);
        assertThat(request.getCoords()).isEqualTo(coords);
    }

    // ─── CoordinatesDto ───────────────────────────────────────────────────────

    @Test
    void coordinatesDto_noArgConstructor_createsInstance() throws Exception {
        final EvvLocationRequest.CoordinatesDto coords = new EvvLocationRequest.CoordinatesDto();

        assertThat(coords).isNotNull();
        assertThat(coords.getLat()).isNull();
        assertThat(coords.getLng()).isNull();
        assertThat(coords.getAccuracyM()).isNull();
    }

    @Test
    void coordinatesDto_allArgsConstructor_setsFields() throws Exception {
        final BigDecimal lat = new BigDecimal("40.7128");
        final BigDecimal lng = new BigDecimal("-74.0060");
        final BigDecimal accuracy = new BigDecimal("2.5");

        final EvvLocationRequest.CoordinatesDto coords = new EvvLocationRequest.CoordinatesDto(lat, lng, accuracy);

        assertThat(coords.getLat()).isEqualTo(lat);
        assertThat(coords.getLng()).isEqualTo(lng);
        assertThat(coords.getAccuracyM()).isEqualTo(accuracy);
    }

    @Test
    void coordinatesDto_setters_updateFields() throws Exception {
        final EvvLocationRequest.CoordinatesDto coords = new EvvLocationRequest.CoordinatesDto();

        coords.setLat(new BigDecimal("51.5074"));
        coords.setLng(new BigDecimal("-0.1278"));
        coords.setAccuracyM(new BigDecimal("10.0"));

        assertThat(coords.getLat()).isEqualByComparingTo("51.5074");
        assertThat(coords.getLng()).isEqualByComparingTo("-0.1278");
        assertThat(coords.getAccuracyM()).isEqualByComparingTo("10.0");
    }

    // ─── validate(): GPS with valid coords ────────────────────────────────────

    @Test
    void validate_gpsWithValidCoords_doesNotThrow() throws Exception {
        final EvvLocationRequest.CoordinatesDto coords = new EvvLocationRequest.CoordinatesDto(
                new BigDecimal("38.0"), new BigDecimal("-77.0"), null);

        final EvvLocationRequest request = EvvLocationRequest.builder()
                .evvRecordId(1L)
                .role(EvvLocationRole.CHECK_IN)
                .type(EvvLocationType.GPS)
                .coords(coords)
                .build();

        // Should not throw
        request.validate();
    }

    // ─── validate(): GPS with null coords ────────────────────────────────────

    @Test
    void validate_gpsWithNullCoords_throwsIllegalArgumentException() throws Exception {
        final EvvLocationRequest request = EvvLocationRequest.builder()
                .evvRecordId(1L)
                .role(EvvLocationRole.CHECK_IN)
                .type(EvvLocationType.GPS)
                .coords(null)
                .build();

        assertThatThrownBy(request::validate)
                .isInstanceOf(IllegalArgumentException.class)
                .hasMessage("GPS location requires coordinates");
    }

    // ─── validate(): GPS with null lat ───────────────────────────────────────

    @Test
    void validate_gpsWithNullLat_throwsIllegalArgumentException() throws Exception {
        final EvvLocationRequest.CoordinatesDto coords = new EvvLocationRequest.CoordinatesDto(
                null, new BigDecimal("-77.0"), null);

        final EvvLocationRequest request = EvvLocationRequest.builder()
                .evvRecordId(1L)
                .role(EvvLocationRole.CHECK_IN)
                .type(EvvLocationType.GPS)
                .coords(coords)
                .build();

        assertThatThrownBy(request::validate)
                .isInstanceOf(IllegalArgumentException.class)
                .hasMessage("GPS location requires coordinates");
    }

    // ─── validate(): GPS with null lng ───────────────────────────────────────

    @Test
    void validate_gpsWithNullLng_throwsIllegalArgumentException() throws Exception {
        final EvvLocationRequest.CoordinatesDto coords = new EvvLocationRequest.CoordinatesDto(
                new BigDecimal("38.0"), null, null);

        final EvvLocationRequest request = EvvLocationRequest.builder()
                .evvRecordId(1L)
                .role(EvvLocationRole.CHECK_IN)
                .type(EvvLocationType.GPS)
                .coords(coords)
                .build();

        assertThatThrownBy(request::validate)
                .isInstanceOf(IllegalArgumentException.class)
                .hasMessage("GPS location requires coordinates");
    }

    // ─── validate(): PATIENT_ADDRESS does not need coords ────────────────────

    @Test
    void validate_patientAddress_doesNotThrow() throws Exception {
        final EvvLocationRequest request = EvvLocationRequest.builder()
                .evvRecordId(1L)
                .role(EvvLocationRole.CHECK_OUT)
                .type(EvvLocationType.PATIENT_ADDRESS)
                .coords(null)
                .build();

        // Should not throw
        request.validate();
    }
}
