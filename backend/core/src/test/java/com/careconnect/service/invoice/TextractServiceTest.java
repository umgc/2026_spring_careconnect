package com.careconnect.service.invoice;

import com.careconnect.dto.chat.AiRequest;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.Timeout;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.web.multipart.MultipartFile;
import software.amazon.awssdk.services.s3.S3Client;
import software.amazon.awssdk.services.textract.TextractClient;
import software.amazon.awssdk.services.textract.model.Block;
import software.amazon.awssdk.services.textract.model.BlockType;
import software.amazon.awssdk.services.textract.model.GetDocumentTextDetectionRequest;
import software.amazon.awssdk.services.textract.model.GetDocumentTextDetectionResponse;
import software.amazon.awssdk.services.textract.model.JobStatus;
import software.amazon.awssdk.services.textract.model.StartDocumentTextDetectionRequest;
import software.amazon.awssdk.services.textract.model.StartDocumentTextDetectionResponse;

import java.io.IOException;
import java.lang.reflect.Field;
import java.util.List;
import java.util.concurrent.TimeUnit;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
class TextractServiceTest {

    @Mock
    private TextractClient textractClient;

    @Mock
    private S3Client s3Client;

    @Mock
    private PdfService pdfService;

    @Mock
    private S3StorageService s3StorageService;

    @InjectMocks
    private TextractService textractService;

    @BeforeEach
    void setUp() throws Exception {
        final Field bucketField = TextractService.class.getDeclaredField("s3BucketName");
        bucketField.setAccessible(true);
        bucketField.set(textractService, "test-bucket");
    }

    private MultipartFile nonEmptyFile(String name) {
        final MultipartFile f = mock(MultipartFile.class);
        when(f.isEmpty()).thenReturn(false);
        when(f.getOriginalFilename()).thenReturn(name);
        return f;
    }

    // ----- Input validation (fast — no Thread.sleep) -----

    @Test
    void analyzeAndGetResult_null_throwsIllegalArgument() throws Exception {
        assertThatThrownBy(() -> textractService.analyzeAndGetResult(null))
                .isInstanceOf(IllegalArgumentException.class)
                .hasMessage("File list cannot be null or empty.");
    }

    @Test
    void analyzeAndGetResult_emptyList_throwsIllegalArgument() throws Exception {
        assertThatThrownBy(() -> textractService.analyzeAndGetResult(List.of()))
                .isInstanceOf(IllegalArgumentException.class)
                .hasMessage("File list cannot be null or empty.");
    }

    @Test
    void analyzeAndGetResult_allEmptyFiles_throwsIllegalArgument() throws Exception {
        final MultipartFile emptyFile = mock(MultipartFile.class);
        when(emptyFile.isEmpty()).thenReturn(true);
        assertThatThrownBy(() -> textractService.analyzeAndGetResult(List.of(emptyFile)))
                .isInstanceOf(IllegalArgumentException.class)
                .hasMessage("File list cannot be null or empty.");
    }

    @Test
    void analyzeAndGetResult_nullFileName_throwsIllegalArgument() throws IOException {
        final MultipartFile file = mock(MultipartFile.class);
        when(file.isEmpty()).thenReturn(false);
        when(file.getOriginalFilename()).thenReturn(null);
        when(pdfService.combineToPdf(any())).thenReturn(new byte[]{1, 2, 3});

        assertThatThrownBy(() -> textractService.analyzeAndGetResult(List.of(file)))
                .isInstanceOf(IllegalArgumentException.class)
                .hasMessage("Invalid file name");
    }

    @Test
    void analyzeAndGetResult_blankFileName_throwsIllegalArgument() throws IOException {
        final MultipartFile file = mock(MultipartFile.class);
        when(file.isEmpty()).thenReturn(false);
        when(file.getOriginalFilename()).thenReturn("   ");
        when(pdfService.combineToPdf(any())).thenReturn(new byte[]{1, 2, 3});

        assertThatThrownBy(() -> textractService.analyzeAndGetResult(List.of(file)))
                .isInstanceOf(IllegalArgumentException.class)
                .hasMessage("Invalid file name");
    }

    @Test
    void getBlocksFromFiles_null_throwsIllegalArgument() throws Exception {
        assertThatThrownBy(() -> textractService.getBlocksFromFiles(null))
                .isInstanceOf(IllegalArgumentException.class);
    }

    // ----- Polling tests (each sleeps ~2s due to POLL_INTERVAL_MS) -----

    @Test
    @Timeout(value = 15, unit = TimeUnit.SECONDS)
    void analyzeAndGetResult_success_returnsRawText() throws IOException, InterruptedException {
        final MultipartFile file = nonEmptyFile("invoice.pdf");
        when(pdfService.combineToPdf(any())).thenReturn(new byte[]{1, 2, 3});

        when(textractClient.startDocumentTextDetection(any(StartDocumentTextDetectionRequest.class)))
                .thenReturn(StartDocumentTextDetectionResponse.builder().jobId("job-123").build());

        final Block line = Block.builder().blockType(BlockType.LINE).text("Invoice Total: $100.00").build();
        final Block page = Block.builder().blockType(BlockType.PAGE).build();
        when(textractClient.getDocumentTextDetection(any(GetDocumentTextDetectionRequest.class)))
                .thenReturn(GetDocumentTextDetectionResponse.builder()
                        .jobStatus(JobStatus.SUCCEEDED)
                        .blocks(List.of(line, page))
                        .nextToken(null)
                        .build());

        final AiRequest.AnalysisResult result = textractService.analyzeAndGetResult(List.of(file));

        assertThat(result.rawText).isEqualTo("Invoice Total: $100.00");
        assertThat(result.s3Key).startsWith("invoices/");
    }

    @Test
    @Timeout(value = 15, unit = TimeUnit.SECONDS)
    void analyzeAndGetResult_noBlocks_throwsRuntime() throws IOException, InterruptedException {
        final MultipartFile file = nonEmptyFile("invoice.pdf");
        when(pdfService.combineToPdf(any())).thenReturn(new byte[]{1, 2, 3});

        when(textractClient.startDocumentTextDetection(any(StartDocumentTextDetectionRequest.class)))
                .thenReturn(StartDocumentTextDetectionResponse.builder().jobId("job-empty").build());
        when(textractClient.getDocumentTextDetection(any(GetDocumentTextDetectionRequest.class)))
                .thenReturn(GetDocumentTextDetectionResponse.builder()
                        .jobStatus(JobStatus.SUCCEEDED)
                        .blocks(List.of())
                        .nextToken(null)
                        .build());

        assertThatThrownBy(() -> textractService.analyzeAndGetResult(List.of(file)))
                .isInstanceOf(RuntimeException.class)
                .hasMessage("No text blocks returned.");
    }

    @Test
    @Timeout(value = 15, unit = TimeUnit.SECONDS)
    void analyzeAndGetResult_jobFailed_throwsRuntime() throws IOException, InterruptedException {
        final MultipartFile file = nonEmptyFile("invoice.pdf");
        when(pdfService.combineToPdf(any())).thenReturn(new byte[]{1, 2, 3});

        when(textractClient.startDocumentTextDetection(any(StartDocumentTextDetectionRequest.class)))
                .thenReturn(StartDocumentTextDetectionResponse.builder().jobId("job-fail").build());
        when(textractClient.getDocumentTextDetection(any(GetDocumentTextDetectionRequest.class)))
                .thenReturn(GetDocumentTextDetectionResponse.builder()
                        .jobStatus(JobStatus.FAILED)
                        .blocks(List.of())
                        .nextToken(null)
                        .build());

        assertThatThrownBy(() -> textractService.analyzeAndGetResult(List.of(file)))
                .isInstanceOf(RuntimeException.class)
                .hasMessageContaining("failed with status: FAILED");
    }

    @Test
    @Timeout(value = 15, unit = TimeUnit.SECONDS)
    void analyzeAndGetResult_withPagination_collectsAllBlocks()
            throws IOException, InterruptedException {
        final MultipartFile file = nonEmptyFile("invoice.pdf");
        when(pdfService.combineToPdf(any())).thenReturn(new byte[]{1, 2, 3});

        when(textractClient.startDocumentTextDetection(any(StartDocumentTextDetectionRequest.class)))
                .thenReturn(StartDocumentTextDetectionResponse.builder().jobId("job-paged").build());

        final Block line1 = Block.builder().blockType(BlockType.LINE).text("Page 1 text").build();
        final Block line2 = Block.builder().blockType(BlockType.LINE).text("Page 2 text").build();

        when(textractClient.getDocumentTextDetection(any(GetDocumentTextDetectionRequest.class)))
                .thenReturn(GetDocumentTextDetectionResponse.builder()
                        .jobStatus(JobStatus.SUCCEEDED)
                        .blocks(List.of(line1))
                        .nextToken("token-page2")
                        .build())
                .thenReturn(GetDocumentTextDetectionResponse.builder()
                        .jobStatus(JobStatus.SUCCEEDED)
                        .blocks(List.of(line2))
                        .nextToken(null)
                        .build());

        final AiRequest.AnalysisResult result = textractService.analyzeAndGetResult(List.of(file));

        assertThat(result.rawText).contains("Page 1 text").contains("Page 2 text");
    }

    @Test
    @Timeout(value = 15, unit = TimeUnit.SECONDS)
    void getBlocksFromFiles_success_returnsRawText() throws IOException, InterruptedException {
        final MultipartFile file = nonEmptyFile("doc.pdf");
        when(pdfService.combineToPdf(any())).thenReturn(new byte[]{1, 2, 3});

        when(textractClient.startDocumentTextDetection(any(StartDocumentTextDetectionRequest.class)))
                .thenReturn(StartDocumentTextDetectionResponse.builder().jobId("job-txt").build());

        final Block line = Block.builder().blockType(BlockType.LINE).text("extracted text").build();
        when(textractClient.getDocumentTextDetection(any(GetDocumentTextDetectionRequest.class)))
                .thenReturn(GetDocumentTextDetectionResponse.builder()
                        .jobStatus(JobStatus.SUCCEEDED)
                        .blocks(List.of(line))
                        .nextToken(null)
                        .build());

        final String rawText = textractService.getBlocksFromFiles(List.of(file));

        assertThat(rawText).isEqualTo("extracted text");
    }
}
