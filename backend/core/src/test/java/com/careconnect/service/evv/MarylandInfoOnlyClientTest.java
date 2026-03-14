package com.careconnect.service.evv;

import com.careconnect.model.evv.EvvRecord;
import org.junit.jupiter.api.Test;
import org.mockito.Mockito;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatCode;

class MarylandInfoOnlyClientTest {

    private final MarylandInfoOnlyClient client = new MarylandInfoOnlyClient();

    @Test
    void destination_returnsMarylandOnlyInfo() throws Exception {
        assertThat(client.destination()).isEqualTo("maryland-only-info");
    }

    @Test
    void submit_doesNotThrow() throws Exception {
        final EvvRecord record = Mockito.mock(EvvRecord.class);
        Mockito.when(record.getId()).thenReturn(1L);

        assertThatCode(() -> client.submit(record)).doesNotThrowAnyException();
    }
}
