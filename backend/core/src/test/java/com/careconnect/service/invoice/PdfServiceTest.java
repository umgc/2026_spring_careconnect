package com.careconnect.service.invoice;

import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import java.io.IOException;
import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.anyList;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
class PdfServiceTest {

    @Mock
    private PdfService pdfService;

    @Test
    void pdfService_canBeMocked() throws IOException {
        when(pdfService.combineToPdf(anyList())).thenReturn(new byte[]{1, 2, 3});

        final byte[] result = pdfService.combineToPdf(List.of());

        assertThat(result).containsExactly(1, 2, 3);
    }

    @Test
    void pdfService_throwsIoException_whenMockedTo() throws IOException {
        when(pdfService.combineToPdf(anyList())).thenThrow(new IOException("read error"));

        try {
            pdfService.combineToPdf(List.of());
        } catch (IOException e) {
            assertThat(e.getMessage()).isEqualTo("read error");
        }
    }
}
