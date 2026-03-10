package com.careconnect.config;

import org.junit.jupiter.api.Test;
import org.springframework.context.annotation.AnnotationConfigApplicationContext;
import org.springframework.http.client.BufferingClientHttpRequestFactory;
import org.springframework.http.client.ClientHttpRequestFactory;
import org.springframework.web.client.RestTemplate;

import static org.junit.jupiter.api.Assertions.*;

class WebClientConfigTest {

    @Test
    void shouldCreateRestTemplateBean() {
        AnnotationConfigApplicationContext context =
                new AnnotationConfigApplicationContext(WebClientConfig.class);

        RestTemplate restTemplate = context.getBean(RestTemplate.class);

        assertNotNull(restTemplate);
        context.close();
    }

    @Test
    void shouldUseBufferingRequestFactory() {
        WebClientConfig config = new WebClientConfig();
        RestTemplate restTemplate = config.restTemplate();

        ClientHttpRequestFactory factory = restTemplate.getRequestFactory();

        assertTrue(factory instanceof BufferingClientHttpRequestFactory);
    }

    @Test
    void restTemplateShouldBeFunctional() {
        WebClientConfig config = new WebClientConfig();
        RestTemplate restTemplate = config.restTemplate();

        assertNotNull(restTemplate.getRequestFactory());
        assertNotNull(restTemplate.getMessageConverters());
        assertFalse(restTemplate.getMessageConverters().isEmpty());
    }

    @Test
    void shouldReturnNewInstanceEachCall() {
        WebClientConfig config = new WebClientConfig();

        RestTemplate r1 = config.restTemplate();
        RestTemplate r2 = config.restTemplate();

        assertNotSame(r1, r2);
    }
}
