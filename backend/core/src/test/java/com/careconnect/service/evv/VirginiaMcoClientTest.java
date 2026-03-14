package com.careconnect.service.evv;

import com.careconnect.model.evv.EvvRecord;
import org.junit.jupiter.api.Test;
import org.mockito.Mockito;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatCode;

class VirginiaMcoClientTest {

    private final VirginiaMcoClient client = new VirginiaMcoClient();

    @Test
    void destination_returnsVirginiaMco() throws Exception {
        assertThat(client.destination()).isEqualTo("virginia-mco");
    }

    @Test
    void submit_doesNotThrow() throws Exception {
        final EvvRecord record = Mockito.mock(EvvRecord.class);
        Mockito.when(record.getId()).thenReturn(2L);

        assertThatCode(() -> client.submit(record)).doesNotThrowAnyException();
    }
}
