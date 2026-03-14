package com.careconnect.service.evv;

import com.careconnect.model.evv.EvvRecord;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.http.HttpEntity;
import org.springframework.http.ResponseEntity;
import org.springframework.web.client.RestTemplate;

import java.lang.reflect.Field;
import java.time.OffsetDateTime;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
class DcSandataAltEvvClientTest {

    @InjectMocks
    private DcSandataAltEvvClient client;

    @Mock
    private RestTemplate mockRestTemplate;

    @BeforeEach
    void setUp() throws Exception {
        final Field f = DcSandataAltEvvClient.class.getDeclaredField("restTemplate");
        f.setAccessible(true);
        f.set(client, mockRestTemplate);
    }

    @Test
    void destination_returnsDcSandata() throws Exception {
        assertThat(client.destination()).isEqualTo("dc-sandata");
    }

    @Test
    void submit_successfulResponse_doesNotThrow() throws Exception {
        final EvvRecord record = mock(EvvRecord.class);
        when(record.getTimeIn()).thenReturn(OffsetDateTime.parse("2025-01-15T08:00:00Z"));
        when(record.getLocationLat()).thenReturn(38.9072);
        when(record.getLocationLng()).thenReturn(-77.0369);
        when(record.getId()).thenReturn(42L);

        when(mockRestTemplate.postForEntity(
                eq("https://api.sandata.dc.gov/altevv/Visits"),
                any(HttpEntity.class),
                eq(String.class)))
                .thenReturn(ResponseEntity.ok("OK"));

        // Should not throw
        client.submit(record);
    }

    @Test
    void submit_nonSuccessResponse_throwsRuntimeException() throws Exception {
        final EvvRecord record = mock(EvvRecord.class);
        when(record.getTimeIn()).thenReturn(OffsetDateTime.parse("2025-01-15T08:00:00Z"));
        when(record.getLocationLat()).thenReturn(38.9072);
        when(record.getLocationLng()).thenReturn(-77.0369);

        when(mockRestTemplate.postForEntity(
                eq("https://api.sandata.dc.gov/altevv/Visits"),
                any(HttpEntity.class),
                eq(String.class)))
                .thenReturn(ResponseEntity.status(500).<String>build());

        assertThatThrownBy(() -> client.submit(record))
                .isInstanceOf(RuntimeException.class)
                .hasMessageContaining("Sandata submission failed");
    }
}
