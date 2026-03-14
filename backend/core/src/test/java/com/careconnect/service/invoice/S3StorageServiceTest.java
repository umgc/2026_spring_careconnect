package com.careconnect.service.invoice;

import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import static org.assertj.core.api.Assertions.assertThatCode;
import static org.mockito.Mockito.doNothing;
import static org.mockito.Mockito.verify;

@ExtendWith(MockitoExtension.class)
class S3StorageServiceTest {

    @Mock
    private S3StorageService s3StorageService;

    @Test
    void upload_twoArgs_canBeMocked() throws Exception {
        final byte[] data = new byte[]{1, 2, 3};
        doNothing().when(s3StorageService).upload(data, "key");

        assertThatCode(() -> s3StorageService.upload(data, "key")).doesNotThrowAnyException();
        verify(s3StorageService).upload(data, "key");
    }

    @Test
    void upload_threeArgs_canBeMocked() throws Exception {
        final byte[] data = new byte[]{1, 2, 3};
        doNothing().when(s3StorageService).upload(data, "key", "application/pdf");

        assertThatCode(() -> s3StorageService.upload(data, "key", "application/pdf"))
                .doesNotThrowAnyException();
        verify(s3StorageService).upload(data, "key", "application/pdf");
    }
}
