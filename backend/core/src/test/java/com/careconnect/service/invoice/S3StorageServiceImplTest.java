package com.careconnect.service.invoice;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.ArgumentCaptor;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import software.amazon.awssdk.core.sync.RequestBody;
import software.amazon.awssdk.services.s3.S3Client;
import software.amazon.awssdk.services.s3.model.PutObjectRequest;
import software.amazon.awssdk.services.s3.model.PutObjectResponse;

import java.lang.reflect.Field;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
class S3StorageServiceImplTest {

    @Mock
    private S3Client s3Client;

    @InjectMocks
    private S3StorageServiceImpl s3StorageServiceImpl;

    @BeforeEach
    void setUp() throws Exception {
        // @Value field is not injected by MockitoExtension — set via reflection
        final Field bucketField = S3StorageServiceImpl.class.getDeclaredField("bucket");
        bucketField.setAccessible(true);
        bucketField.set(s3StorageServiceImpl, "test-bucket");

        when(s3Client.putObject(any(PutObjectRequest.class), any(RequestBody.class)))
                .thenReturn(PutObjectResponse.builder().build());
    }

    @Test
    void upload_twoArgs_delegatesToThreeArgVersion() throws Exception {
        final byte[] data = "pdf-data".getBytes();

        s3StorageServiceImpl.upload(data, "invoices/test.pdf");

        verify(s3Client).putObject(any(PutObjectRequest.class), any(RequestBody.class));
    }

    @Test
    void upload_threeArgs_withContentType_setsContentType() throws Exception {
        final byte[] data = "pdf-data".getBytes();
        final ArgumentCaptor<PutObjectRequest> captor = ArgumentCaptor.forClass(PutObjectRequest.class);

        s3StorageServiceImpl.upload(data, "invoices/test.pdf", "application/pdf");

        verify(s3Client).putObject(captor.capture(), any(RequestBody.class));
        assertThat(captor.getValue().contentType()).isEqualTo("application/pdf");
        assertThat(captor.getValue().bucket()).isEqualTo("test-bucket");
        assertThat(captor.getValue().key()).isEqualTo("invoices/test.pdf");
    }

    @Test
    void upload_threeArgs_nullContentType_doesNotSetContentType() throws Exception {
        final byte[] data = "pdf-data".getBytes();
        final ArgumentCaptor<PutObjectRequest> captor = ArgumentCaptor.forClass(PutObjectRequest.class);

        s3StorageServiceImpl.upload(data, "invoices/test.pdf", null);

        verify(s3Client).putObject(captor.capture(), any(RequestBody.class));
        assertThat(captor.getValue().contentType()).isNull();
    }

    @Test
    void upload_threeArgs_blankContentType_doesNotSetContentType() throws Exception {
        final byte[] data = "pdf-data".getBytes();
        final ArgumentCaptor<PutObjectRequest> captor = ArgumentCaptor.forClass(PutObjectRequest.class);

        s3StorageServiceImpl.upload(data, "invoices/test.pdf", "   ");

        verify(s3Client).putObject(captor.capture(), any(RequestBody.class));
        assertThat(captor.getValue().contentType()).isNull();
    }

    @Test
    void upload_setsAcl() throws Exception {
        final byte[] data = "data".getBytes();
        final ArgumentCaptor<PutObjectRequest> captor = ArgumentCaptor.forClass(PutObjectRequest.class);

        s3StorageServiceImpl.upload(data, "invoices/file.pdf", "application/pdf");

        verify(s3Client).putObject(captor.capture(), any(RequestBody.class));
        assertThat(captor.getValue().acl().toString()).isEqualTo("bucket-owner-full-control");
    }
}
