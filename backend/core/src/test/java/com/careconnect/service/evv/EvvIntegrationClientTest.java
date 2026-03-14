package com.careconnect.service.evv;

import com.careconnect.model.evv.EvvRecord;
import org.junit.jupiter.api.Test;
import org.mockito.Mockito;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

/**
 * Tests for the {@link EvvIntegrationClient} interface via mock verification.
 * Concrete implementations (DcSandataAltEvvClient, MarylandInfoOnlyClient,
 * VirginiaMcoClient) are tested in their own test classes.
 */
class EvvIntegrationClientTest {

    @Test
    void destination_mockReturnsExpectedValue() throws Exception {
        final EvvIntegrationClient client = mock(EvvIntegrationClient.class);
        when(client.destination()).thenReturn("test-destination");

        assertThat(client.destination()).isEqualTo("test-destination");
    }

    @Test
    void submit_mockCanBeInvoked() throws Exception {
        final EvvIntegrationClient client = mock(EvvIntegrationClient.class);
        final EvvRecord record = Mockito.mock(EvvRecord.class);

        client.submit(record);

        Mockito.verify(client).submit(record);
    }
}
