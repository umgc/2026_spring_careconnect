package com.careconnect.config;

import lombok.extern.slf4j.Slf4j;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.core.env.Environment;

/**
 * WebSocket Mode Configuration
 *
 * Automatically determines whether to use Local or AWS WebSocket mode based on environment.
 * Priority:
 * 1. If AWS_WEBSOCKET_API_GATEWAY_ENDPOINT env var is set -> AWS mode
 * 2. If AWS_WEBSOCKET_API_ENDPOINT env var is set -> AWS mode (legacy fallback)
 * 3. Otherwise -> Local mode (default)
 */
@Slf4j
@Configuration
public class WebSocketModeConfig {

    @Bean
    public String websocketMode(Environment env) {
        String awsEndpoint = env.getProperty("AWS_WEBSOCKET_API_GATEWAY_ENDPOINT");
        if (awsEndpoint == null || awsEndpoint.isBlank()) {
            awsEndpoint = env.getProperty("AWS_WEBSOCKET_API_ENDPOINT");
        }

        if (awsEndpoint != null && !awsEndpoint.isBlank()) {
            log.info("AWS WebSocket endpoint detected: Using AWS WebSocket mode");
            log.info("AWS WebSocket endpoint: {}", awsEndpoint);
            return "aws";
        } else {
            log.info("AWS WebSocket endpoint not set: Using Local WebSocket mode");
            return "local";
        }
    }
}
