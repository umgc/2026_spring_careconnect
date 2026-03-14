package com.careconnect.service;

import com.careconnect.dto.S3Props;
import com.careconnect.dto.UserFileDTO;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.mockito.ArgumentCaptor;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.MockitoAnnotations;
import org.springframework.web.multipart.MultipartFile;
import software.amazon.awssdk.core.ResponseBytes;
import software.amazon.awssdk.core.sync.RequestBody;
import software.amazon.awssdk.core.sync.ResponseTransformer;
import software.amazon.awssdk.services.s3.S3Client;
import software.amazon.awssdk.services.s3.model.*;

import java.io.ByteArrayInputStream;
import java.io.IOException;
import java.time.Instant;
import java.util.Collections;
import java.util.List;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.*;

class S3StorageServiceTest {

    @Mock
    private S3Client s3;

    @Mock
    private S3Props props;

    @InjectMocks
    private S3StorageService service;

    @BeforeEach
    void setUp() throws Exception {
        MockitoAnnotations.openMocks(this);
        when(props.getBucket()).thenReturn("test-bucket");
        when(props.getRegion()).thenReturn("us-east-1");
        when(props.getBaseUrl()).thenReturn("https://s3.amazonaws.com/test-bucket");
        when(props.getAccessKey()).thenReturn("AKIAIOSFODNN7EXAMPLE");
    }

    // ========== upload(String path, byte[] content, String mimeType) ==========

    @Test
    @DisplayName("upload_validContent_returnsUrlAndCallsS3")
    void upload_validContent_returnsUrlAndCallsS3() throws Exception {
        final PutObjectResponse response = PutObjectResponse.builder().eTag("etag123").build();
        when(s3.putObject(any(PutObjectRequest.class), any(RequestBody.class))).thenReturn(response);

        final String result = service.upload("docs/test.pdf", "hello".getBytes(), "application/pdf");

        assertEquals("https://s3.amazonaws.com/test-bucket/docs/test.pdf", result);

        final ArgumentCaptor<PutObjectRequest> captor = ArgumentCaptor.forClass(PutObjectRequest.class);
        verify(s3).putObject(captor.capture(), any(RequestBody.class));
        final PutObjectRequest captured = captor.getValue();
        assertEquals("test-bucket", captured.bucket());
        assertEquals("docs/test.pdf", captured.key());
        assertEquals("application/pdf", captured.contentType());
        assertEquals(ServerSideEncryption.AWS_KMS, captured.serverSideEncryption());
    }

    @Test
    @DisplayName("upload_s3ThrowsException_throwsRuntimeException")
    void upload_s3ThrowsException_throwsRuntimeException() throws Exception {
        when(s3.putObject(any(PutObjectRequest.class), any(RequestBody.class)))
                .thenThrow(S3Exception.builder().message("S3 error").build());

        final RuntimeException ex = assertThrows(RuntimeException.class,
                () -> service.upload("path/file.txt", "data".getBytes(), "text/plain"));
        assertEquals("Failed to upload file to S3", ex.getMessage());
        assertNotNull(ex.getCause());
    }

    // ========== uploadFile(MultipartFile, Long, String, String) ==========

    @Test
    @DisplayName("uploadFile_validFile_returnsFullPath")
    void uploadFile_validFile_returnsFullPath() throws IOException {
        final MultipartFile file = mock(MultipartFile.class);
        when(file.getOriginalFilename()).thenReturn("report.pdf");
        when(file.getSize()).thenReturn(1024L);
        when(file.getContentType()).thenReturn("application/pdf");
        when(file.getInputStream()).thenReturn(new ByteArrayInputStream("data".getBytes()));

        final PutObjectResponse response = PutObjectResponse.builder().eTag("etag").versionId("v1").build();
        when(s3.putObject(any(PutObjectRequest.class), any(RequestBody.class))).thenReturn(response);

        final String result = service.uploadFile(file, 42L, "Patient", "medical");

        assertEquals("patient_42/medical/report.pdf", result);
        verify(s3).putObject(any(PutObjectRequest.class), any(RequestBody.class));
    }

    @Test
    @DisplayName("uploadFile_ioException_throwsRuntimeException")
    void uploadFile_ioException_throwsRuntimeException() throws IOException {
        final MultipartFile file = mock(MultipartFile.class);
        when(file.getOriginalFilename()).thenReturn("file.txt");
        when(file.getSize()).thenReturn(100L);
        when(file.getContentType()).thenReturn("text/plain");
        when(file.getInputStream()).thenThrow(new IOException("IO failure"));

        final RuntimeException ex = assertThrows(RuntimeException.class,
                () -> service.uploadFile(file, 1L, "Provider", "notes"));
        assertEquals("Failed to upload file - IO Error", ex.getMessage());
        assertInstanceOf(IOException.class, ex.getCause());
    }

    @Test
    @DisplayName("uploadFile_genericException_throwsRuntimeExceptionWithMessage")
    void uploadFile_genericException_throwsRuntimeExceptionWithMessage() throws IOException {
        final MultipartFile file = mock(MultipartFile.class);
        when(file.getOriginalFilename()).thenReturn("file.txt");
        when(file.getSize()).thenReturn(100L);
        when(file.getContentType()).thenReturn("text/plain");
        when(file.getInputStream()).thenReturn(new ByteArrayInputStream("data".getBytes()));

        when(s3.putObject(any(PutObjectRequest.class), any(RequestBody.class)))
                .thenThrow(new RuntimeException("aws connection failed"));

        final RuntimeException ex = assertThrows(RuntimeException.class,
                () -> service.uploadFile(file, 2L, "Caregiver", "documents"));
        assertTrue(ex.getMessage().contains("Failed to upload file:"));
        assertTrue(ex.getMessage().contains("aws connection failed"));
    }

    // ========== download(String path) ==========

    @SuppressWarnings("unchecked")
    @Test
    @DisplayName("download_fileExists_returnsByteArray")
    void download_fileExists_returnsByteArray() throws Exception {
        final byte[] expected = "file content".getBytes();
        final GetObjectResponse getObjectResponse = GetObjectResponse.builder().build();
        final ResponseBytes<GetObjectResponse> responseBytes = ResponseBytes.fromByteArray(getObjectResponse, expected);

        when(s3.getObject(any(GetObjectRequest.class), any(ResponseTransformer.class)))
                .thenReturn(responseBytes);

        final byte[] result = service.download("docs/test.pdf");

        assertArrayEquals(expected, result);
    }

    @SuppressWarnings("unchecked")
    @Test
    @DisplayName("download_noSuchKeyException_throwsRuntimeException")
    void download_noSuchKeyException_throwsRuntimeException() throws Exception {
        when(s3.getObject(any(GetObjectRequest.class), any(ResponseTransformer.class)))
                .thenThrow(NoSuchKeyException.builder().message("not found").build());

        final RuntimeException ex = assertThrows(RuntimeException.class,
                () -> service.download("missing/file.txt"));
        assertTrue(ex.getMessage().contains("File not found: missing/file.txt"));
        assertInstanceOf(NoSuchKeyException.class, ex.getCause());
    }

    @SuppressWarnings("unchecked")
    @Test
    @DisplayName("download_genericException_throwsRuntimeException")
    void download_genericException_throwsRuntimeException() throws Exception {
        when(s3.getObject(any(GetObjectRequest.class), any(ResponseTransformer.class)))
                .thenThrow(new RuntimeException("network error"));

        final RuntimeException ex = assertThrows(RuntimeException.class,
                () -> service.download("some/path.txt"));
        assertEquals("Failed to download file", ex.getMessage());
    }

    // ========== getFileUrl(String path) ==========

    @Test
    @DisplayName("getFileUrl_validPath_returnsBaseUrlPlusPath")
    void getFileUrl_validPath_returnsBaseUrlPlusPath() throws Exception {
        final String result = service.getFileUrl("user_1/docs/file.pdf");
        assertEquals("https://s3.amazonaws.com/test-bucket/user_1/docs/file.pdf", result);
    }

    // ========== deleteFile(String path) ==========

    @Test
    @DisplayName("deleteFile_validPath_deletesFromS3")
    void deleteFile_validPath_deletesFromS3() throws Exception {
        final DeleteObjectResponse response = DeleteObjectResponse.builder().build();
        when(s3.deleteObject(any(DeleteObjectRequest.class))).thenReturn(response);

        assertDoesNotThrow(() -> service.deleteFile("user_1/docs/old.pdf"));

        final ArgumentCaptor<DeleteObjectRequest> captor = ArgumentCaptor.forClass(DeleteObjectRequest.class);
        verify(s3).deleteObject(captor.capture());
        assertEquals("test-bucket", captor.getValue().bucket());
        assertEquals("user_1/docs/old.pdf", captor.getValue().key());
    }

    @Test
    @DisplayName("deleteFile_s3ThrowsException_throwsRuntimeException")
    void deleteFile_s3ThrowsException_throwsRuntimeException() throws Exception {
        when(s3.deleteObject(any(DeleteObjectRequest.class)))
                .thenThrow(new RuntimeException("delete error"));

        final RuntimeException ex = assertThrows(RuntimeException.class,
                () -> service.deleteFile("path/file.txt"));
        assertEquals("Failed to delete file", ex.getMessage());
    }

    // ========== listUserFiles(Long userId, String userType) ==========

    @Test
    @DisplayName("listUserFiles_filesExist_returnsKeyList")
    void listUserFiles_filesExist_returnsKeyList() throws Exception {
        final S3Object obj1 = S3Object.builder().key("patient_1/docs/a.pdf").build();
        final S3Object obj2 = S3Object.builder().key("patient_1/docs/b.pdf").build();
        final ListObjectsV2Response response = ListObjectsV2Response.builder()
                .contents(obj1, obj2).build();

        when(s3.listObjectsV2(any(ListObjectsV2Request.class))).thenReturn(response);

        final List<String> result = service.listUserFiles(1L, "Patient");

        assertEquals(2, result.size());
        assertTrue(result.contains("patient_1/docs/a.pdf"));
        assertTrue(result.contains("patient_1/docs/b.pdf"));

        final ArgumentCaptor<ListObjectsV2Request> captor = ArgumentCaptor.forClass(ListObjectsV2Request.class);
        verify(s3).listObjectsV2(captor.capture());
        assertEquals("test-bucket", captor.getValue().bucket());
        assertEquals("patient_1/", captor.getValue().prefix());
    }

    @Test
    @DisplayName("listUserFiles_noFiles_returnsEmptyList")
    void listUserFiles_noFiles_returnsEmptyList() throws Exception {
        final ListObjectsV2Response response = ListObjectsV2Response.builder()
                .contents(Collections.emptyList()).build();

        when(s3.listObjectsV2(any(ListObjectsV2Request.class))).thenReturn(response);

        final List<String> result = service.listUserFiles(99L, "Caregiver");

        assertTrue(result.isEmpty());
    }

    @Test
    @DisplayName("listUserFiles_s3ThrowsException_throwsRuntimeException")
    void listUserFiles_s3ThrowsException_throwsRuntimeException() throws Exception {
        when(s3.listObjectsV2(any(ListObjectsV2Request.class)))
                .thenThrow(new RuntimeException("list error"));

        final RuntimeException ex = assertThrows(RuntimeException.class,
                () -> service.listUserFiles(1L, "Patient"));
        assertEquals("Failed to list user files", ex.getMessage());
    }

    // ========== listUserFilesDto(Long userId, String userType) ==========

    @Test
    @DisplayName("listUserFilesDto_filesExist_returnsDtoList")
    void listUserFilesDto_filesExist_returnsDtoList() throws Exception {
        final Instant now = Instant.now();
        final S3Object obj = S3Object.builder()
                .key("patient_5/medical/report.pdf")
                .size(2048L)
                .lastModified(now)
                .build();

        final ListObjectsV2Response response = ListObjectsV2Response.builder()
                .contents(obj).build();

        when(s3.listObjectsV2(any(ListObjectsV2Request.class))).thenReturn(response);

        final List<UserFileDTO> result = service.listUserFilesDto(5L, "Patient");

        assertEquals(1, result.size());
        final UserFileDTO dto = result.get(0);
        assertEquals("patient_5/medical/report.pdf", dto.getS3FullKey());
        assertEquals("report.pdf", dto.getFilename());
        assertEquals("medical", dto.getFileCategory());
        assertEquals("https://s3.amazonaws.com/test-bucket/patient_5/medical/report.pdf", dto.getFileUrl());
        assertEquals(2048L, dto.getFileSize());
        assertNotNull(dto.getUpdatedAt());
    }

    @Test
    @DisplayName("listUserFilesDto_noFiles_returnsEmptyList")
    void listUserFilesDto_noFiles_returnsEmptyList() throws Exception {
        final ListObjectsV2Response response = ListObjectsV2Response.builder()
                .contents(Collections.emptyList()).build();

        when(s3.listObjectsV2(any(ListObjectsV2Request.class))).thenReturn(response);

        final List<UserFileDTO> result = service.listUserFilesDto(10L, "Provider");

        assertTrue(result.isEmpty());
    }

    @Test
    @DisplayName("listUserFilesDto_s3ThrowsException_throwsRuntimeException")
    void listUserFilesDto_s3ThrowsException_throwsRuntimeException() throws Exception {
        when(s3.listObjectsV2(any(ListObjectsV2Request.class)))
                .thenThrow(new RuntimeException("dto list error"));

        final RuntimeException ex = assertThrows(RuntimeException.class,
                () -> service.listUserFilesDto(1L, "Patient"));
        assertEquals("Failed to list user files", ex.getMessage());
    }
}
