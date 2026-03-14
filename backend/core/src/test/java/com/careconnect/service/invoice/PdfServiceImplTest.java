package com.careconnect.service.invoice;

import org.apache.pdfbox.pdmodel.PDDocument;
import org.apache.pdfbox.pdmodel.PDPage;
import org.junit.jupiter.api.Test;
import org.springframework.web.multipart.MultipartFile;

import javax.imageio.ImageIO;
import java.awt.image.BufferedImage;
import java.io.ByteArrayOutputStream;
import java.io.IOException;
import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

class PdfServiceImplTest {

    private final PdfServiceImpl pdfService = new PdfServiceImpl();

    private static byte[] createValidPdfBytes() throws IOException {
        try (PDDocument doc = new PDDocument();
             final ByteArrayOutputStream baos = new ByteArrayOutputStream()) {
            doc.addPage(new PDPage());
            doc.save(baos);
            return baos.toByteArray();
        }
    }

    private static byte[] createValidPngBytes() throws IOException {
        final BufferedImage img = new BufferedImage(1, 1, BufferedImage.TYPE_INT_RGB);
        final ByteArrayOutputStream baos = new ByteArrayOutputStream();
        ImageIO.write(img, "png", baos);
        return baos.toByteArray();
    }

    @Test
    void combineToPdf_null_throwsIllegalArgument() throws Exception {
        assertThatThrownBy(() -> pdfService.combineToPdf(null))
                .isInstanceOf(IllegalArgumentException.class)
                .hasMessage("File list cannot be null or empty.");
    }

    @Test
    void combineToPdf_emptyList_throwsIllegalArgument() throws Exception {
        assertThatThrownBy(() -> pdfService.combineToPdf(List.of()))
                .isInstanceOf(IllegalArgumentException.class)
                .hasMessage("File list cannot be null or empty.");
    }

    @Test
    void combineToPdf_unsupportedContentType_skipsAndReturnsEmptyPdf() throws IOException {
        final MultipartFile file = mock(MultipartFile.class);
        when(file.getContentType()).thenReturn("application/octet-stream");
        when(file.getOriginalFilename()).thenReturn("test.bin");

        final byte[] result = pdfService.combineToPdf(List.of(file));

        assertThat(result).isNotNull().isNotEmpty();
        assertThat(new String(result, 0, 4)).isEqualTo("%PDF");
    }

    @Test
    void combineToPdf_nullContentType_skipsFile() throws IOException {
        final MultipartFile file = mock(MultipartFile.class);
        when(file.getContentType()).thenReturn(null);
        when(file.getOriginalFilename()).thenReturn("noext");

        final byte[] result = pdfService.combineToPdf(List.of(file));

        assertThat(result).isNotNull().isNotEmpty();
    }

    @Test
    void combineToPdf_pdfContentType_mergesPages() throws IOException {
        final byte[] pdfBytes = createValidPdfBytes();
        final MultipartFile file = mock(MultipartFile.class);
        when(file.getContentType()).thenReturn("application/pdf");
        when(file.getOriginalFilename()).thenReturn("test.pdf");
        when(file.getBytes()).thenReturn(pdfBytes);

        final byte[] result = pdfService.combineToPdf(List.of(file));

        assertThat(result).isNotNull().isNotEmpty();
        assertThat(new String(result, 0, 4)).isEqualTo("%PDF");
    }

    @Test
    void combineToPdf_pdfContentType_caseInsensitive() throws IOException {
        final byte[] pdfBytes = createValidPdfBytes();
        final MultipartFile file = mock(MultipartFile.class);
        when(file.getContentType()).thenReturn("APPLICATION/PDF");
        when(file.getOriginalFilename()).thenReturn("test.PDF");
        when(file.getBytes()).thenReturn(pdfBytes);

        final byte[] result = pdfService.combineToPdf(List.of(file));

        assertThat(result).isNotNull().isNotEmpty();
    }

    @Test
    void combineToPdf_imageContentType_addsPageWithImage() throws IOException {
        final byte[] pngBytes = createValidPngBytes();
        final MultipartFile file = mock(MultipartFile.class);
        when(file.getContentType()).thenReturn("image/png");
        when(file.getOriginalFilename()).thenReturn("test.png");
        when(file.getBytes()).thenReturn(pngBytes);

        final byte[] result = pdfService.combineToPdf(List.of(file));

        assertThat(result).isNotNull().isNotEmpty();
        assertThat(new String(result, 0, 4)).isEqualTo("%PDF");
    }

    @Test
    void combineToPdf_imageWithNullFilename_usesDefaultName() throws IOException {
        final byte[] pngBytes = createValidPngBytes();
        final MultipartFile file = mock(MultipartFile.class);
        when(file.getContentType()).thenReturn("image/jpeg");
        when(file.getOriginalFilename()).thenReturn(null);
        when(file.getBytes()).thenReturn(pngBytes);

        final byte[] result = pdfService.combineToPdf(List.of(file));

        assertThat(result).isNotNull().isNotEmpty();
    }

    @Test
    void combineToPdf_multipleFiles_mixedTypes() throws IOException {
        final byte[] pdfBytes = createValidPdfBytes();

        final MultipartFile pdfFile = mock(MultipartFile.class);
        when(pdfFile.getContentType()).thenReturn("application/pdf");
        when(pdfFile.getOriginalFilename()).thenReturn("doc.pdf");
        when(pdfFile.getBytes()).thenReturn(pdfBytes);

        final MultipartFile unsupported = mock(MultipartFile.class);
        when(unsupported.getContentType()).thenReturn("text/plain");
        when(unsupported.getOriginalFilename()).thenReturn("notes.txt");

        final byte[] result = pdfService.combineToPdf(List.of(pdfFile, unsupported));

        assertThat(result).isNotNull().isNotEmpty();
    }
}
