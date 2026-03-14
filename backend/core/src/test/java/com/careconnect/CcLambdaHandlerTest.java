package com.careconnect;

import com.amazonaws.serverless.proxy.model.AwsProxyRequest;
import com.amazonaws.serverless.proxy.model.AwsProxyResponse;
import com.amazonaws.serverless.proxy.spring.SpringBootLambdaContainerHandler;
import com.amazonaws.services.lambda.runtime.Context;
import com.amazonaws.services.lambda.runtime.RequestStreamHandler;
import org.junit.jupiter.api.AfterAll;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.MockedStatic;
import org.mockito.junit.jupiter.MockitoExtension;

import java.io.ByteArrayInputStream;
import java.io.ByteArrayOutputStream;
import java.io.IOException;
import java.io.InputStream;
import java.io.OutputStream;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.*;

/**
 * Test class for CcLambdaHandler
 * Tests AWS Lambda request handling and Spring Boot integration
 */
@ExtendWith(MockitoExtension.class)
class CcLambdaHandlerTest {

    private static SpringBootLambdaContainerHandler<AwsProxyRequest, AwsProxyResponse> mockStaticHandler;
    @SuppressWarnings("rawtypes")
    private static MockedStatic<SpringBootLambdaContainerHandler> staticMock;

    private CcLambdaHandler handler;

    @Mock
    private Context mockContext;

    @SuppressWarnings("unchecked")
    @BeforeAll
    static void initClass() {
        mockStaticHandler = mock(SpringBootLambdaContainerHandler.class);
        staticMock = mockStatic(SpringBootLambdaContainerHandler.class);
        staticMock.when(() -> SpringBootLambdaContainerHandler.getAwsProxyHandler(any()))
                .thenReturn(mockStaticHandler);
    }

    @AfterAll
    static void tearDownClass() {
        if (staticMock != null) {
            staticMock.close();
        }
    }

    @SuppressWarnings("unchecked")
    @BeforeEach
    void setUp() {
        clearInvocations(mockStaticHandler);
        handler = new CcLambdaHandler();
    }

    @Test
    void testHandlerIsNotNull() {
        assertThat(handler).isNotNull();
    }

    @Test
    void testHandleRequestProcessesInputStream() throws IOException {
        final String requestJson = "{\"httpMethod\":\"GET\",\"path\":\"/test\"}";
        final InputStream inputStream = new ByteArrayInputStream(requestJson.getBytes());
        final OutputStream outputStream = new ByteArrayOutputStream();

        handler.handleRequest(inputStream, outputStream, mockContext);

        verify(mockStaticHandler).proxyStream(inputStream, outputStream, mockContext);
    }

    @Test
    void testHandleRequestWithNullInputStream() throws IOException {
        final OutputStream outputStream = new ByteArrayOutputStream();

        handler.handleRequest(null, outputStream, mockContext);

        verify(mockStaticHandler).proxyStream(null, outputStream, mockContext);
    }

    @Test
    void testHandleRequestWithNullOutputStream() throws IOException {
        final String requestJson = "{\"httpMethod\":\"GET\",\"path\":\"/test\"}";
        final InputStream inputStream = new ByteArrayInputStream(requestJson.getBytes());

        handler.handleRequest(inputStream, null, mockContext);

        verify(mockStaticHandler).proxyStream(inputStream, null, mockContext);
    }

    @Test
    void testHandleRequestWithValidStreams() throws IOException {
        final String requestJson = "{\"httpMethod\":\"GET\",\"path\":\"/health\"}";
        final InputStream inputStream = new ByteArrayInputStream(requestJson.getBytes());
        final ByteArrayOutputStream outputStream = new ByteArrayOutputStream();

        handler.handleRequest(inputStream, outputStream, mockContext);

        verify(mockStaticHandler).proxyStream(inputStream, outputStream, mockContext);
        assertThat(outputStream).isNotNull();
    }

    @Test
    void testContextParameterIsUsed() throws IOException {
        final String requestJson = "{\"httpMethod\":\"POST\",\"path\":\"/api/test\"}";
        final InputStream inputStream = new ByteArrayInputStream(requestJson.getBytes());
        final OutputStream outputStream = new ByteArrayOutputStream();

        when(mockContext.getFunctionName()).thenReturn("test-function");

        handler.handleRequest(inputStream, outputStream, mockContext);

        verify(mockStaticHandler).proxyStream(inputStream, outputStream, mockContext);
        assertThat(mockContext.getFunctionName()).isEqualTo("test-function");
    }

    @Test
    void testImplementsRequestStreamHandler() {
        assertThat(handler).isInstanceOf(RequestStreamHandler.class);
    }
}
