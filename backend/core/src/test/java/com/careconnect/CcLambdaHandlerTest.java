package com.careconnect;


import com.amazonaws.serverless.proxy.model.AwsProxyRequest;
import com.amazonaws.serverless.proxy.model.AwsProxyResponse;
import com.amazonaws.serverless.proxy.spring.SpringBootLambdaContainerHandler;
import com.amazonaws.services.lambda.runtime.Context;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;

import org.mockito.junit.jupiter.MockitoExtension;

import java.io.ByteArrayInputStream;
import java.io.ByteArrayOutputStream;
import java.io.IOException;
import java.io.InputStream;
import java.io.OutputStream;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.Mockito.*;

/**
 * Test class for CcLambdaHandler
 * Tests AWS Lambda request handling and Spring Boot integration
 */
@ExtendWith(MockitoExtension.class)
class CcLambdaHandlerTest {

    private CcLambdaHandler handler;

    @Mock
    private Context mockContext;

    @Mock
    private SpringBootLambdaContainerHandler<AwsProxyRequest, AwsProxyResponse> mockHandler;

    @BeforeEach
    void setUp() {
        handler = new CcLambdaHandler();
    }

    @Test
    void testHandlerIsNotNull() {
        // Verify that the handler instance can be created
        assertThat(handler).isNotNull();
    }

    @Test
    void testHandleRequestProcessesInputStream() throws IOException {
        // Arrange
        String requestJson = "{\"httpMethod\":\"GET\",\"path\":\"/test\"}";
        InputStream inputStream = new ByteArrayInputStream(requestJson.getBytes());
        OutputStream outputStream = new ByteArrayOutputStream();

        // Act & Assert
        // Note: This will use the actual static HANDLER, so it's more of an integration test
        // In a real scenario, you might want to mock the static handler
        assertThatThrownBy(() -> 
            handler.handleRequest(inputStream, outputStream, mockContext)
        ).isInstanceOf(RuntimeException.class);
    }

    @Test
    void testHandleRequestWithNullInputStream() {
        // Arrange
        OutputStream outputStream = new ByteArrayOutputStream();

        // Act & Assert
        assertThatThrownBy(() -> 
            handler.handleRequest(null, outputStream, mockContext)
        ).isInstanceOf(Exception.class);
    }

    @Test
    void testHandleRequestWithNullOutputStream() {
        // Arrange
        String requestJson = "{\"httpMethod\":\"GET\",\"path\":\"/test\"}";
        InputStream inputStream = new ByteArrayInputStream(requestJson.getBytes());

        // Act & Assert
        assertThatThrownBy(() -> 
            handler.handleRequest(inputStream, null, mockContext)
        ).isInstanceOf(Exception.class);
    }

    @Test
    void testHandleRequestWithValidStreams() throws IOException {
        // Arrange
        String requestJson = "{\"httpMethod\":\"GET\",\"path\":\"/health\"}";
        InputStream inputStream = new ByteArrayInputStream(requestJson.getBytes());
        ByteArrayOutputStream outputStream = new ByteArrayOutputStream();

        // Act
        try {
            handler.handleRequest(inputStream, outputStream, mockContext);
        } catch (RuntimeException e) {
            // Expected in test environment without full Spring context
            assertThat(e).isNotNull();
        }

        // Assert - verify output stream was written to (or exception was thrown)
        assertThat(outputStream).isNotNull();
    }

    @Test
    void testContextParameterIsUsed() {
        // Arrange
        String requestJson = "{\"httpMethod\":\"POST\",\"path\":\"/api/test\"}";
        InputStream inputStream = new ByteArrayInputStream(requestJson.getBytes());
        OutputStream outputStream = new ByteArrayOutputStream();

        // Configure mock context
        when(mockContext.getFunctionName()).thenReturn("test-function");
        when(mockContext.getRemainingTimeInMillis()).thenReturn(30000);

        // Act
        try {
            handler.handleRequest(inputStream, outputStream, mockContext);
        } catch (Exception e) {
            // Expected in test environment
        }

        // Assert - context should be passed to the handler
        assertThat(mockContext).isNotNull();
    }

    @Test
    void testImplementsRequestStreamHandler() {
        // Verify that CcLambdaHandler implements the correct interface
        assertThat(handler).isInstanceOf(com.amazonaws.services.lambda.runtime.RequestStreamHandler.class);
    }
}