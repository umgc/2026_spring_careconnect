package com.careconnect.service;

import com.careconnect.dto.UploadedFileDTO;
import org.apache.pdfbox.pdmodel.PDDocument;
import org.apache.pdfbox.pdmodel.PDPage;
import org.apache.pdfbox.pdmodel.PDPageContentStream;
import org.apache.pdfbox.pdmodel.font.PDType1Font;
import org.apache.pdfbox.pdmodel.font.Standard14Fonts;
import org.apache.poi.xwpf.usermodel.XWPFDocument;
import org.apache.poi.xwpf.usermodel.XWPFParagraph;
import org.apache.poi.xwpf.usermodel.XWPFRun;
import org.apache.tika.Tika;
import org.apache.tika.exception.TikaException;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.test.util.ReflectionTestUtils;

import java.io.ByteArrayOutputStream;
import java.io.IOException;
import java.io.InputStream;
import java.util.Base64;

import static org.assertj.core.api.Assertions.assertThat;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

class DocumentProcessingServiceTest {

    private DocumentProcessingService service;

    @BeforeEach
    void setUp() throws Exception {
        service = new DocumentProcessingService();
    }

    // ── Helpers ─────────────────────────────────────────────────────────────

    private UploadedFileDTO file(String filename, String contentType, String content) {
        return UploadedFileDTO.builder()
                .filename(filename)
                .contentType(contentType)
                .content(content)
                .build();
    }

    private String base64(String raw) {
        return Base64.getEncoder().encodeToString(raw.getBytes());
    }

    private String longPlain(int minLength) {
        final StringBuilder sb = new StringBuilder();
        while (sb.length() < minLength + 500) {
            sb.append("Lorem ipsum dolor sit amet, consectetur adipiscing. ");
        }
        return sb.toString();
    }

    /**
     * Creates a valid PDF with the given text content and returns its base64 encoding.
     */
    private String createValidPdfBase64(String text) throws IOException {
        try (PDDocument doc = new PDDocument()) {
            final PDPage page = new PDPage();
            doc.addPage(page);
            try (PDPageContentStream cs = new PDPageContentStream(doc, page)) {
                cs.beginText();
                cs.setFont(new PDType1Font(Standard14Fonts.FontName.HELVETICA), 12);
                cs.newLineAtOffset(50, 700);
                cs.showText(text);
                cs.endText();
            }
            final ByteArrayOutputStream baos = new ByteArrayOutputStream();
            doc.save(baos);
            return Base64.getEncoder().encodeToString(baos.toByteArray());
        }
    }

    /**
     * Creates a valid PDF with no text content (just an empty page) and returns base64.
     */
    private String createEmptyPdfBase64() throws IOException {
        try (PDDocument doc = new PDDocument()) {
            doc.addPage(new PDPage());
            final ByteArrayOutputStream baos = new ByteArrayOutputStream();
            doc.save(baos);
            return Base64.getEncoder().encodeToString(baos.toByteArray());
        }
    }

    /**
     * Creates a valid PDF with long text exceeding the max content length.
     */
    private String createLongPdfBase64() throws IOException {
        try (PDDocument doc = new PDDocument()) {
            final PDPage page = new PDPage();
            doc.addPage(page);
            try (PDPageContentStream cs = new PDPageContentStream(doc, page)) {
                cs.beginText();
                cs.setFont(new PDType1Font(Standard14Fonts.FontName.HELVETICA), 6);
                cs.setLeading(8f);
                cs.newLineAtOffset(25, 750);
                // Write many lines of text to exceed 15000 chars
                for (int i = 0; i < 300; i++) {
                    cs.showText("Line " + i + " This is a long text line for testing truncation of PDF documents extracted. ");
                    cs.newLine();
                }
                cs.endText();
            }
            final ByteArrayOutputStream baos = new ByteArrayOutputStream();
            doc.save(baos);
            return Base64.getEncoder().encodeToString(baos.toByteArray());
        }
    }

    /**
     * Creates a valid DOCX with the given text and returns base64.
     */
    private String createValidDocxBase64(String text) throws IOException {
        try (XWPFDocument doc = new XWPFDocument()) {
            final XWPFParagraph para = doc.createParagraph();
            final XWPFRun run = para.createRun();
            run.setText(text);
            final ByteArrayOutputStream baos = new ByteArrayOutputStream();
            doc.write(baos);
            return Base64.getEncoder().encodeToString(baos.toByteArray());
        }
    }

    /**
     * Creates a valid DOCX with no text (empty paragraphs only) and returns base64.
     */
    private String createEmptyDocxBase64() throws IOException {
        try (XWPFDocument doc = new XWPFDocument()) {
            // Create a paragraph with no text
            doc.createParagraph();
            final ByteArrayOutputStream baos = new ByteArrayOutputStream();
            doc.write(baos);
            return Base64.getEncoder().encodeToString(baos.toByteArray());
        }
    }

    /**
     * Creates a valid DOCX with long text exceeding the max document content length.
     */
    private String createLongDocxBase64() throws IOException {
        try (XWPFDocument doc = new XWPFDocument()) {
            // Write enough text to exceed 15000 chars
            for (int i = 0; i < 300; i++) {
                final XWPFParagraph para = doc.createParagraph();
                final XWPFRun run = para.createRun();
                run.setText("Paragraph " + i + " This is a long text line for testing truncation of DOCX documents. ");
            }
            final ByteArrayOutputStream baos = new ByteArrayOutputStream();
            doc.write(baos);
            return Base64.getEncoder().encodeToString(baos.toByteArray());
        }
    }

    // ── getFileType: filename present ───────────────────────────────────────

    @Test
    @DisplayName("extractTextContent_pdfExtension_routesToPdfHandler")
    void extractTextContent_pdfExtension_routesToPdfHandler() throws Exception {
        final UploadedFileDTO dto = file("report.pdf", "application/pdf", "");
        final String result = service.extractTextContent(dto);
        assertThat(result).isEqualTo("[Empty PDF file: report.pdf]");
    }

    @Test
    @DisplayName("extractTextContent_docExtension_routesToDocHandler")
    void extractTextContent_docExtension_routesToDocHandler() throws Exception {
        final UploadedFileDTO dto = file("report.doc", "application/msword", "");
        final String result = service.extractTextContent(dto);
        assertThat(result).isEqualTo("[Empty DOC file: report.doc]");
    }

    @Test
    @DisplayName("extractTextContent_docxExtension_routesToDocxHandler")
    void extractTextContent_docxExtension_routesToDocxHandler() throws Exception {
        final UploadedFileDTO dto = file("report.docx", "application/vnd.openxmlformats", "");
        final String result = service.extractTextContent(dto);
        assertThat(result).isEqualTo("[Empty DOCX file: report.docx]");
    }

    @Test
    @DisplayName("extractTextContent_txtExtension_routesToTextHandler")
    void extractTextContent_txtExtension_routesToTextHandler() throws Exception {
        final UploadedFileDTO dto = file("notes.txt", "text/plain", "");
        final String result = service.extractTextContent(dto);
        assertThat(result).isEqualTo("[Empty text file]");
    }

    @Test
    @DisplayName("extractTextContent_mdExtension_routesToTextHandler")
    void extractTextContent_mdExtension_routesToTextHandler() throws Exception {
        final UploadedFileDTO dto = file("readme.md", "text/markdown", "");
        final String result = service.extractTextContent(dto);
        assertThat(result).isEqualTo("[Empty text file]");
    }

    @Test
    @DisplayName("extractTextContent_textExtension_routesToTextHandler")
    void extractTextContent_textExtension_routesToTextHandler() throws Exception {
        final UploadedFileDTO dto = file("readme.text", "text/plain", "Hello");
        final String result = service.extractTextContent(dto);
        assertThat(result).isEqualTo("Hello");
    }

    @Test
    @DisplayName("extractTextContent_jsonExtension_routesToJsonHandler")
    void extractTextContent_jsonExtension_routesToJsonHandler() throws Exception {
        final UploadedFileDTO dto = file("data.json", "application/json", "");
        final String result = service.extractTextContent(dto);
        assertThat(result).isEqualTo("[Empty JSON file]");
    }

    @Test
    @DisplayName("extractTextContent_csvExtension_routesToCsvHandler")
    void extractTextContent_csvExtension_routesToCsvHandler() throws Exception {
        final UploadedFileDTO dto = file("data.csv", "text/csv", "");
        final String result = service.extractTextContent(dto);
        assertThat(result).isEqualTo("[Empty CSV file]");
    }

    @Test
    @DisplayName("extractTextContent_unknownExtension_routesToGenericHandler")
    void extractTextContent_unknownExtension_routesToGenericHandler() throws Exception {
        final UploadedFileDTO dto = file("archive.xyz", "application/octet", "");
        final String result = service.extractTextContent(dto);
        assertThat(result).isEqualTo("[Empty file: archive.xyz]");
    }

    // ── getFileType: filename null, contentType present ─────────────────────

    @Test
    @DisplayName("extractTextContent_nullFilenameTextContentType_routesToTextHandler")
    void extractTextContent_nullFilenameTextContentType_routesToTextHandler() throws Exception {
        final UploadedFileDTO dto = file(null, "text/plain", "Hello world");
        final String result = service.extractTextContent(dto);
        assertThat(result).isEqualTo("Hello world");
    }

    @Test
    @DisplayName("extractTextContent_nullFilenameImageContentType_routesToGeneric")
    void extractTextContent_nullFilenameImageContentType_routesToGeneric() throws Exception {
        final UploadedFileDTO dto = file(null, "image/png", "");
        final String result = service.extractTextContent(dto);
        assertThat(result).startsWith("[Empty file: null]");
    }

    @Test
    @DisplayName("extractTextContent_nullFilenamePdfContentType_routesToPdf")
    void extractTextContent_nullFilenamePdfContentType_routesToPdf() throws Exception {
        final UploadedFileDTO dto = file(null, "application/pdf", null);
        final String result = service.extractTextContent(dto);
        assertThat(result).isEqualTo("[Empty PDF file: null]");
    }

    @Test
    @DisplayName("extractTextContent_nullFilenameJsonContentType_routesToJson")
    void extractTextContent_nullFilenameJsonContentType_routesToJson() throws Exception {
        final UploadedFileDTO dto = file(null, "application/json", "");
        final String result = service.extractTextContent(dto);
        assertThat(result).isEqualTo("[Empty JSON file]");
    }

    @Test
    @DisplayName("extractTextContent_nullFilenameCsvContentType_routesToTextHandler")
    void extractTextContent_nullFilenameCsvContentType_routesToCsv() throws Exception {
        // "text/csv" starts with "text/", so getFileType returns "text" before
        // reaching the "text/csv" check, routing to the text handler
        final UploadedFileDTO dto = file(null, "text/csv", "");
        final String result = service.extractTextContent(dto);
        assertThat(result).isEqualTo("[Empty text file]");
    }

    @Test
    @DisplayName("extractTextContent_nullFilenameDocxContentType_routesToDocx")
    void extractTextContent_nullFilenameDocxContentType_routesToDocx() throws Exception {
        final UploadedFileDTO dto = file(null,
                "application/vnd.openxmlformats-officedocument.wordprocessingml.document", "");
        final String result = service.extractTextContent(dto);
        assertThat(result).isEqualTo("[Empty DOCX file: null]");
    }

    @Test
    @DisplayName("extractTextContent_nullFilenameMswordContentType_routesToDoc")
    void extractTextContent_nullFilenameMswordContentType_routesToDoc() throws Exception {
        final UploadedFileDTO dto = file(null, "application/msword", null);
        final String result = service.extractTextContent(dto);
        assertThat(result).isEqualTo("[Empty DOC file: null]");
    }

    @Test
    @DisplayName("extractTextContent_nullFilenameNullContentType_routesToGeneric")
    void extractTextContent_nullFilenameNullContentType_routesToGeneric() throws Exception {
        final UploadedFileDTO dto = file(null, null, "");
        final String result = service.extractTextContent(dto);
        assertThat(result).isEqualTo("[Empty file: null]");
    }

    // ── extractPdfContent ───────────────────────────────────────────────────

    @Test
    @DisplayName("extractPdfContent_nullContent_returnsEmptyMessage")
    void extractPdfContent_nullContent_returnsEmptyMessage() throws Exception {
        final UploadedFileDTO dto = file("doc.pdf", "application/pdf", null);
        final String result = service.extractTextContent(dto);
        assertThat(result).isEqualTo("[Empty PDF file: doc.pdf]");
    }

    @Test
    @DisplayName("extractPdfContent_blankContent_returnsEmptyMessage")
    void extractPdfContent_blankContent_returnsEmptyMessage() throws Exception {
        final UploadedFileDTO dto = file("doc.pdf", "application/pdf", "   ");
        final String result = service.extractTextContent(dto);
        assertThat(result).isEqualTo("[Empty PDF file: doc.pdf]");
    }

    @Test
    @DisplayName("extractPdfContent_invalidBase64_returnsErrorMessage")
    void extractPdfContent_invalidBase64_returnsErrorMessage() throws Exception {
        final UploadedFileDTO dto = file("doc.pdf", "application/pdf", "NOT_VALID_B64!!!");
        final String result = service.extractTextContent(dto);
        assertThat(result).startsWith("[Error extracting PDF content:");
    }

    @Test
    @DisplayName("extractPdfContent_validPdfWithText_returnsExtractedText")
    void extractPdfContent_validPdfWithText_returnsExtractedText() throws IOException {
        final String pdfBase64 = createValidPdfBase64("Hello from PDF document");
        final UploadedFileDTO dto = file("doc.pdf", "application/pdf", pdfBase64);
        final String result = service.extractTextContent(dto);
        assertThat(result).contains("Hello from PDF document");
    }

    @Test
    @DisplayName("extractPdfContent_validPdfNoText_returnsNoExtractableTextMessage")
    void extractPdfContent_validPdfNoText_returnsNoExtractableTextMessage() throws IOException {
        final String pdfBase64 = createEmptyPdfBase64();
        final UploadedFileDTO dto = file("empty.pdf", "application/pdf", pdfBase64);
        final String result = service.extractTextContent(dto);
        assertThat(result).isEqualTo("[PDF file contains no extractable text: empty.pdf]");
    }

    @Test
    @DisplayName("extractPdfContent_longPdf_truncated")
    void extractPdfContent_longPdf_truncated() throws IOException {
        final String pdfBase64 = createLongPdfBase64();
        final UploadedFileDTO dto = file("long.pdf", "application/pdf", pdfBase64);
        final String result = service.extractTextContent(dto);
        assertThat(result).contains("... [PDF content truncated for processing]");
    }

    // ── extractDocContent ───────────────────────────────────────────────────

    @Test
    @DisplayName("extractDocContent_nullContent_returnsEmptyMessage")
    void extractDocContent_nullContent_returnsEmptyMessage() throws Exception {
        final UploadedFileDTO dto = file("doc.doc", "application/msword", null);
        final String result = service.extractTextContent(dto);
        assertThat(result).isEqualTo("[Empty DOC file: doc.doc]");
    }

    @Test
    @DisplayName("extractDocContent_blankContent_returnsEmptyMessage")
    void extractDocContent_blankContent_returnsEmptyMessage() throws Exception {
        final UploadedFileDTO dto = file("doc.doc", "application/msword", "  ");
        final String result = service.extractTextContent(dto);
        assertThat(result).isEqualTo("[Empty DOC file: doc.doc]");
    }

    @Test
    @DisplayName("extractDocContent_invalidBinary_returnsError")
    void extractDocContent_invalidBinary_returnsError() throws Exception {
        final String bogus = Base64.getEncoder().encodeToString("not a doc file".getBytes());
        final UploadedFileDTO dto = file("doc.doc", "application/msword", bogus);
        final String result = service.extractTextContent(dto);
        assertThat(result).startsWith("[Error extracting DOC content:");
    }

    // ── extractDocxContent ──────────────────────────────────────────────────

    @Test
    @DisplayName("extractDocxContent_nullContent_returnsEmptyMessage")
    void extractDocxContent_nullContent_returnsEmptyMessage() throws Exception {
        final UploadedFileDTO dto = file("doc.docx", "application/vnd.openxmlformats", null);
        final String result = service.extractTextContent(dto);
        assertThat(result).isEqualTo("[Empty DOCX file: doc.docx]");
    }

    @Test
    @DisplayName("extractDocxContent_blankContent_returnsEmptyMessage")
    void extractDocxContent_blankContent_returnsEmptyMessage() throws Exception {
        final UploadedFileDTO dto = file("doc.docx", "application/vnd.openxmlformats", "  ");
        final String result = service.extractTextContent(dto);
        assertThat(result).isEqualTo("[Empty DOCX file: doc.docx]");
    }

    @Test
    @DisplayName("extractDocxContent_invalidBinary_returnsError")
    void extractDocxContent_invalidBinary_returnsError() throws Exception {
        final String bogus = Base64.getEncoder().encodeToString("not a docx file".getBytes());
        final UploadedFileDTO dto = file("doc.docx", "application/vnd.openxmlformats", bogus);
        final String result = service.extractTextContent(dto);
        assertThat(result).startsWith("[Error extracting DOCX content:");
    }

    @Test
    @DisplayName("extractDocxContent_validDocxWithText_returnsExtractedText")
    void extractDocxContent_validDocxWithText_returnsExtractedText() throws IOException {
        final String docxBase64 = createValidDocxBase64("Hello from DOCX document");
        final UploadedFileDTO dto = file("doc.docx", "application/vnd.openxmlformats", docxBase64);
        final String result = service.extractTextContent(dto);
        assertThat(result).contains("Hello from DOCX document");
    }

    @Test
    @DisplayName("extractDocxContent_validDocxNoText_returnsNoExtractableTextMessage")
    void extractDocxContent_validDocxNoText_returnsNoExtractableTextMessage() throws IOException {
        final String docxBase64 = createEmptyDocxBase64();
        final UploadedFileDTO dto = file("empty.docx", "application/vnd.openxmlformats", docxBase64);
        final String result = service.extractTextContent(dto);
        assertThat(result).isEqualTo("[DOCX file contains no extractable text: empty.docx]");
    }

    @Test
    @DisplayName("extractDocxContent_longDocx_truncated")
    void extractDocxContent_longDocx_truncated() throws IOException {
        final String docxBase64 = createLongDocxBase64();
        final UploadedFileDTO dto = file("long.docx", "application/vnd.openxmlformats", docxBase64);
        final String result = service.extractTextContent(dto);
        assertThat(result).contains("... [DOCX content truncated for processing]");
    }

    // ── extractTextFileContent ──────────────────────────────────────────────

    @Test
    @DisplayName("extractTextFileContent_nullContent_returnsEmptyMessage")
    void extractTextFileContent_nullContent_returnsEmptyMessage() throws Exception {
        final UploadedFileDTO dto = file("notes.txt", "text/plain", null);
        final String result = service.extractTextContent(dto);
        assertThat(result).isEqualTo("[Empty text file]");
    }

    @Test
    @DisplayName("extractTextFileContent_blankContent_returnsEmptyMessage")
    void extractTextFileContent_blankContent_returnsEmptyMessage() throws Exception {
        final UploadedFileDTO dto = file("notes.txt", "text/plain", "  ");
        final String result = service.extractTextContent(dto);
        assertThat(result).isEqualTo("[Empty text file]");
    }

    @Test
    @DisplayName("extractTextFileContent_plainText_returnedAsIs")
    void extractTextFileContent_plainText_returnedAsIs() throws Exception {
        final UploadedFileDTO dto = file("notes.txt", "text/plain", "Hello CareConnect");
        final String result = service.extractTextContent(dto);
        assertThat(result).isEqualTo("Hello CareConnect");
    }

    @Test
    @DisplayName("extractTextFileContent_base64Content_decoded")
    void extractTextFileContent_base64Content_decoded() throws Exception {
        final StringBuilder raw = new StringBuilder();
        while (raw.length() < 200) {
            raw.append("CareConnect patient notes are important. ");
        }
        final String encoded = base64(raw.toString());
        final UploadedFileDTO dto = file("notes.txt", "text/plain", encoded);
        final String result = service.extractTextContent(dto);
        assertThat(result).contains("CareConnect patient notes");
    }

    @Test
    @DisplayName("extractTextFileContent_longContent_truncated")
    void extractTextFileContent_longContent_truncated() throws Exception {
        final String longText = longPlain(10000);
        final UploadedFileDTO dto = file("notes.txt", "text/plain", longText);
        final String result = service.extractTextContent(dto);
        assertThat(result).endsWith("... [Text content truncated for processing]");
        assertThat(result.length()).isGreaterThan(10000);
    }

    @Test
    @DisplayName("extractTextFileContent_shortString_notBase64")
    void extractTextFileContent_shortString_notBase64() throws Exception {
        final UploadedFileDTO dto = file("notes.txt", "text/plain", "Short text");
        final String result = service.extractTextContent(dto);
        assertThat(result).isEqualTo("Short text");
    }

    @Test
    @DisplayName("extractTextFileContent_nonBase64Pattern_notDecoded")
    void extractTextFileContent_nonBase64Pattern_notDecoded() throws Exception {
        final StringBuilder sb = new StringBuilder();
        while (sb.length() < 150) {
            sb.append("This has spaces and punctuation! ");
        }
        final UploadedFileDTO dto = file("notes.txt", "text/plain", sb.toString());
        final String result = service.extractTextContent(dto);
        assertThat(result).contains("This has spaces");
    }

    // ── extractJsonContent ──────────────────────────────────────────────────

    @Test
    @DisplayName("extractJsonContent_nullContent_returnsEmptyMessage")
    void extractJsonContent_nullContent_returnsEmptyMessage() throws Exception {
        final UploadedFileDTO dto = file("data.json", "application/json", null);
        final String result = service.extractTextContent(dto);
        assertThat(result).isEqualTo("[Empty JSON file]");
    }

    @Test
    @DisplayName("extractJsonContent_blankContent_returnsEmptyMessage")
    void extractJsonContent_blankContent_returnsEmptyMessage() throws Exception {
        final UploadedFileDTO dto = file("data.json", "application/json", "  ");
        final String result = service.extractTextContent(dto);
        assertThat(result).isEqualTo("[Empty JSON file]");
    }

    @Test
    @DisplayName("extractJsonContent_plainJson_formatted")
    void extractJsonContent_plainJson_formatted() throws Exception {
        final String json = "{\"name\":\"test\"}";
        final UploadedFileDTO dto = file("data.json", "application/json", json);
        final String result = service.extractTextContent(dto);
        assertThat(result).contains("name");
        assertThat(result).contains("test");
    }

    @Test
    @DisplayName("extractJsonContent_base64Json_decodedAndFormatted")
    void extractJsonContent_base64Json_decodedAndFormatted() throws Exception {
        final StringBuilder raw = new StringBuilder();
        raw.append("{\"key\":\"");
        while (raw.length() < 200) {
            raw.append("value");
        }
        raw.append("\"}");
        final String encoded = base64(raw.toString());
        final UploadedFileDTO dto = file("data.json", "application/json", encoded);
        final String result = service.extractTextContent(dto);
        assertThat(result).contains("key");
    }

    @Test
    @DisplayName("extractJsonContent_longJson_truncated")
    void extractJsonContent_longJson_truncated() throws Exception {
        final String longJson = longPlain(10000);
        final UploadedFileDTO dto = file("data.json", "application/json", longJson);
        final String result = service.extractTextContent(dto);
        assertThat(result).contains("... [JSON content truncated for processing]");
    }

    // ── extractCsvContent ───────────────────────────────────────────────────

    @Test
    @DisplayName("extractCsvContent_nullContent_returnsEmptyMessage")
    void extractCsvContent_nullContent_returnsEmptyMessage() throws Exception {
        final UploadedFileDTO dto = file("data.csv", "text/csv", null);
        final String result = service.extractTextContent(dto);
        assertThat(result).isEqualTo("[Empty CSV file]");
    }

    @Test
    @DisplayName("extractCsvContent_blankContent_returnsEmptyMessage")
    void extractCsvContent_blankContent_returnsEmptyMessage() throws Exception {
        final UploadedFileDTO dto = file("data.csv", "text/csv", " ");
        final String result = service.extractTextContent(dto);
        assertThat(result).isEqualTo("[Empty CSV file]");
    }

    @Test
    @DisplayName("extractCsvContent_plainCsv_returned")
    void extractCsvContent_plainCsv_returned() throws Exception {
        final String csv = "name,age\nAlice,30\nBob,25";
        final UploadedFileDTO dto = file("data.csv", "text/csv", csv);
        final String result = service.extractTextContent(dto);
        assertThat(result).isEqualTo(csv);
    }

    @Test
    @DisplayName("extractCsvContent_base64Csv_decoded")
    void extractCsvContent_base64Csv_decoded() throws Exception {
        final StringBuilder raw = new StringBuilder();
        raw.append("col1,col2\n");
        while (raw.length() < 200) {
            raw.append("val1,val2\n");
        }
        final String encoded = base64(raw.toString());
        final UploadedFileDTO dto = file("data.csv", "text/csv", encoded);
        final String result = service.extractTextContent(dto);
        assertThat(result).contains("col1");
    }

    @Test
    @DisplayName("extractCsvContent_longCsv_truncated")
    void extractCsvContent_longCsv_truncated() throws Exception {
        final String longCsv = longPlain(10000);
        final UploadedFileDTO dto = file("data.csv", "text/csv", longCsv);
        final String result = service.extractTextContent(dto);
        assertThat(result).contains("... [CSV content truncated for processing]");
    }

    // ── extractGenericContent ───────────────────────────────────────────────

    @Test
    @DisplayName("extractGenericContent_nullContent_returnsEmptyMessage")
    void extractGenericContent_nullContent_returnsEmptyMessage() throws Exception {
        final UploadedFileDTO dto = file("file.xyz", "application/octet", null);
        final String result = service.extractTextContent(dto);
        assertThat(result).isEqualTo("[Empty file: file.xyz]");
    }

    @Test
    @DisplayName("extractGenericContent_blankContent_returnsEmptyMessage")
    void extractGenericContent_blankContent_returnsEmptyMessage() throws Exception {
        final UploadedFileDTO dto = file("file.xyz", "application/octet", "   ");
        final String result = service.extractTextContent(dto);
        assertThat(result).isEqualTo("[Empty file: file.xyz]");
    }

    @Test
    @DisplayName("extractGenericContent_plainText_extractedViaTika")
    void extractGenericContent_plainText_extractedViaTika() throws Exception {
        final UploadedFileDTO dto = file("file.xyz", "application/octet", "Some readable text");
        final String result = service.extractTextContent(dto);
        assertThat(result).contains("Some readable text");
    }

    @Test
    @DisplayName("extractGenericContent_longContent_truncated")
    void extractGenericContent_longContent_truncated() throws Exception {
        final String longText = longPlain(10000);
        final UploadedFileDTO dto = file("file.xyz", "application/octet", longText);
        final String result = service.extractTextContent(dto);
        assertThat(result).contains("... [Content truncated for processing]");
    }

    @Test
    @DisplayName("extractGenericContent_tikaReturnsEmpty_fallbackMsg")
    void extractGenericContent_tikaReturnsEmpty_fallbackMsg() throws Exception {
        final Tika mockTika = mock(Tika.class);
        when(mockTika.parseToString(any(InputStream.class))).thenReturn("");
        ReflectionTestUtils.setField(service, "tika", mockTika);

        final UploadedFileDTO dto = file("file.xyz", "application/octet", "some content");
        final String result = service.extractTextContent(dto);
        assertThat(result).isEqualTo("[File contains no extractable text: file.xyz]");
    }

    @Test
    @DisplayName("extractGenericContent_tikaReturnsNull_fallbackMsg")
    void extractGenericContent_tikaReturnsNull_fallbackMsg() throws Exception {
        final Tika mockTika = mock(Tika.class);
        when(mockTika.parseToString(any(InputStream.class))).thenReturn(null);
        ReflectionTestUtils.setField(service, "tika", mockTika);

        final UploadedFileDTO dto = file("file.xyz", "application/octet", "some content");
        final String result = service.extractTextContent(dto);
        assertThat(result).isEqualTo("[File contains no extractable text: file.xyz]");
    }

    @Test
    @DisplayName("extractGenericContent_tikaException_returnsUnableMsg")
    void extractGenericContent_tikaException_returnsUnableMsg() throws Exception {
        final Tika mockTika = mock(Tika.class);
        when(mockTika.parseToString(any(InputStream.class)))
                .thenThrow(new TikaException("parse failed"));
        ReflectionTestUtils.setField(service, "tika", mockTika);

        final UploadedFileDTO dto = file("file.xyz", "application/octet", "some content");
        final String result = service.extractTextContent(dto);
        assertThat(result).isEqualTo("[Unable to extract text from: file.xyz]");
    }

    @Test
    @DisplayName("extractGenericContent_ioException_returnsUnableMsg")
    void extractGenericContent_ioException_returnsUnableMsg() throws Exception {
        final Tika mockTika = mock(Tika.class);
        when(mockTika.parseToString(any(InputStream.class)))
                .thenThrow(new IOException("io failed"));
        ReflectionTestUtils.setField(service, "tika", mockTika);

        final UploadedFileDTO dto = file("file.xyz", "application/octet", "some content");
        final String result = service.extractTextContent(dto);
        assertThat(result).isEqualTo("[Unable to extract text from: file.xyz]");
    }

    @Test
    @DisplayName("extractGenericContent_base64Content_decodedBeforeTika")
    void extractGenericContent_base64Content_decodedBeforeTika() throws Exception {
        final StringBuilder raw = new StringBuilder();
        while (raw.length() < 200) {
            raw.append("Readable generic text content here. ");
        }
        final String encoded = base64(raw.toString());
        final UploadedFileDTO dto = file("file.xyz", "application/octet", encoded);
        final String result = service.extractTextContent(dto);
        assertThat(result).contains("Readable generic text");
    }

    // ── extractGenericContent - base64 decode failure branch ────────────────

    @Test
    @DisplayName("extractGenericContent_base64DecodeFails_returnsBinaryFileMessage")
    void extractGenericContent_base64DecodeFails_returnsBinaryFileMessage() throws Exception {
        // Build a string > 100 chars that matches the base64 regex in the sample
        // but will fail full decode. We use a valid base64 string and then make
        // isBase64Encoded return true, but force the inner decode to fail.
        // The simplest approach: create content that passes isBase64Encoded
        // (sample-based check passes) but the full content fails decode.
        // A valid base64 string of > 1000 chars where the first 1000 chars are
        // valid base64 but the remaining chars are invalid.
        final StringBuilder validPart = new StringBuilder();
        // Build valid base64 chars (a-zA-Z0-9+/) for > 1000 chars
        while (validPart.length() < 1100) {
            validPart.append("QUFBQUFBQUFB"); // "AAAAAAAAA" in base64
        }
        // Append invalid base64 chars at the end that will cause decode to fail
        // The % character is not valid base64
        final String content = validPart.toString() + "!!!INVALID!!!";
        final UploadedFileDTO dto = file("file.xyz", "application/octet", content);
        final String result = service.extractTextContent(dto);
        assertThat(result).isEqualTo("[Binary file: file.xyz - Content not readable as text]");
    }

    // ── isBase64Encoded edge cases ──────────────────────────────────────────

    @Test
    @DisplayName("isBase64Encoded_nullContent_returnsFalse")
    void isBase64Encoded_nullContent_returnsFalse() throws Exception {
        // Text handler with null content triggers the null check in isBase64Encoded
        // indirectly - but content null is caught earlier. Test via CSV with short non-b64.
        final UploadedFileDTO dto = file("data.csv", "text/csv", "short");
        final String result = service.extractTextContent(dto);
        assertThat(result).isEqualTo("short");
    }

    @Test
    @DisplayName("isBase64Encoded_emptyContent_returnsFalse")
    void isBase64Encoded_emptyContent_returnsFalse() throws Exception {
        // The empty check happens before isBase64Encoded, but we verify the flow
        final UploadedFileDTO dto = file("notes.txt", "text/plain", "");
        final String result = service.extractTextContent(dto);
        assertThat(result).isEqualTo("[Empty text file]");
    }

    @Test
    @DisplayName("isBase64Encoded_longSampleOver1000Chars_checksSubstring")
    void isBase64Encoded_longSampleOver1000Chars_checksSubstring() throws Exception {
        // Create valid base64 content longer than 1000 chars to exercise the
        // substring(0, 1000) branch in isBase64Encoded
        final StringBuilder raw = new StringBuilder();
        while (raw.length() < 2000) {
            raw.append("Long content that will produce base64 over 1000 chars. ");
        }
        final String encoded = base64(raw.toString());
        assertThat(encoded.length()).isGreaterThan(1000);
        final UploadedFileDTO dto = file("notes.txt", "text/plain", encoded);
        final String result = service.extractTextContent(dto);
        assertThat(result).contains("Long content");
    }

    @Test
    @DisplayName("isBase64Encoded_validBase64CharsButDecodeThrows_returnsFalse")
    void isBase64Encoded_validBase64CharsButDecodeThrows_returnsFalse() throws Exception {
        // Create a string > 100 chars with only valid base64 chars that passes
        // the regex but fails the actual decode attempt at line 376.
        // A string of 'A' repeated 101 times is valid base64 chars but length
        // 101 is not a valid base64 length (not multiple of 4 and no padding).
        // However the regex removes trailing = before decode. Let's try a string
        // that passes the regex but where the sample decode fails.
        // Actually, Base64.getDecoder().decode is lenient with padding in some cases.
        // We need a subtler approach. Use a length that is 1 mod 4 (invalid).
        // 101 chars of 'A' -> after removing trailing '=' -> still 101 'A's -> invalid length
        final StringBuilder sb = new StringBuilder();
        for (int i = 0; i < 101; i++) {
            sb.append("A");
        }
        // This should pass the regex (all valid base64 chars, no =)
        // But fail decode because 101 % 4 == 1 which is invalid
        final String content = sb.toString();
        // Use this as CSV content; isBase64Encoded should return false
        // because decode fails, so the content is returned as-is
        final UploadedFileDTO dto = file("data.csv", "text/csv", content);
        final String result = service.extractTextContent(dto);
        assertThat(result).isEqualTo(content);
    }

    // ── cleanExtractedText ──────────────────────────────────────────────────

    @Test
    @DisplayName("extractGenericContent_excessiveWhitespace_cleaned")
    void extractGenericContent_excessiveWhitespace_cleaned() throws Exception {
        final UploadedFileDTO dto = file("file.xyz", "application/octet",
                "Hello    world   test    content");
        final String result = service.extractTextContent(dto);
        assertThat(result).doesNotContain("    ");
    }

    // ── outer catch in extractTextContent ───────────────────────────────────

    @Test
    @DisplayName("extractTextContent_exceptionDuringProcessing_returnsErrorMessage")
    void extractTextContent_exceptionDuringProcessing_returnsErrorMessage() throws Exception {
        // Create a mock where getFilename() returns a value (so the catch block's
        // log statement works) but getContentType() throws to trigger the catch
        final UploadedFileDTO dto = mock(UploadedFileDTO.class);
        when(dto.getFilename()).thenReturn("test.txt");
        when(dto.getContentType()).thenReturn("text/plain");
        when(dto.getContent()).thenThrow(new RuntimeException("content error"));
        final String result = service.extractTextContent(dto);
        assertThat(result).isEqualTo("[Error processing file: content error]");
    }

    @Test
    @DisplayName("extractTextContent_exceptionInProcessing_andGetFilenameThrows_rethrows")
    void extractTextContent_exceptionInProcessing_andGetFilenameThrows_rethrows() throws Exception {
        // When getFilename() always throws, the catch block at line 71 calls
        // file.getFilename() again for logging, which re-throws the exception
        final UploadedFileDTO dto = mock(UploadedFileDTO.class);
        when(dto.getFilename()).thenThrow(new RuntimeException("boom"));
        assertThat(assertThrows(RuntimeException.class,
                () -> service.extractTextContent(dto)).getMessage()).isEqualTo("boom");
    }

    // ── getFileType with image contentType (routes to default switch case) ──

    @Test
    @DisplayName("extractTextContent_imageFileType_routesToDefaultGenericHandler")
    void extractTextContent_imageFileType_routesToDefaultGenericHandler() throws Exception {
        // When filename is null and contentType is "image/png", getFileType returns "image"
        // which doesn't match any switch case, falling through to default (generic handler)
        final UploadedFileDTO dto = file(null, "image/png", "image data here");
        final String result = service.extractTextContent(dto);
        // "image" type goes to default -> extractGenericContent
        // "image data here" is short text, not base64, so it goes through Tika
        assertThat(result).contains("image data here");
    }
}
