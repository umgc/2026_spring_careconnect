package com.careconnect.config;

import org.junit.jupiter.api.Test;

import java.util.HashMap;
import java.util.Map;

import static org.junit.jupiter.api.Assertions.*;

class AiPropertiesTest {

    @Test
    void providerProps_DefaultValuesAreCorrect() {
        AiProperties.ProviderProps props = new AiProperties.ProviderProps();

        assertEquals(0.2, props.getTemperature());
        assertEquals(1500, props.getMaxTokens());
        assertNull(props.getApiKey());
        assertNull(props.getModel());
        assertNull(props.getBaseUrl());
    }

    @Test
    void providerProps_SettersAndGettersWorkCorrectly() {
        AiProperties.ProviderProps props = new AiProperties.ProviderProps();

        props.setApiKey("test-key");
        props.setModel("gpt-4");
        props.setBaseUrl("https://api.openai.com");
        props.setTemperature(0.7);
        props.setMaxTokens(2000);

        assertEquals("test-key", props.getApiKey());
        assertEquals("gpt-4", props.getModel());
        assertEquals("https://api.openai.com", props.getBaseUrl());
        assertEquals(0.7, props.getTemperature());
        assertEquals(2000, props.getMaxTokens());
    }

    @Test
    void providerProps_AllowsNullValuesForOptionalFields() {
        AiProperties.ProviderProps props = new AiProperties.ProviderProps();

        props.setApiKey(null);
        props.setModel(null);
        props.setBaseUrl(null);
        props.setTemperature(null);
        props.setMaxTokens(null);

        assertNull(props.getApiKey());
        assertNull(props.getModel());
        assertNull(props.getBaseUrl());
        assertNull(props.getTemperature());
        assertNull(props.getMaxTokens());
    }

    @Test
    void aiProperties_ProvidersMapCanBeSetAndRetrieved() {
        AiProperties properties = new AiProperties();

        AiProperties.ProviderProps openAiProps = new AiProperties.ProviderProps();
        openAiProps.setApiKey("openai-key");
        openAiProps.setModel("gpt-4");

        Map<String, AiProperties.ProviderProps> providers = new HashMap<>();
        providers.put("openai", openAiProps);

        properties.setProviders(providers);

        assertNotNull(properties.getProviders());
        assertEquals(1, properties.getProviders().size());
        assertEquals("openai-key",
                properties.getProviders().get("openai").getApiKey());
        assertEquals("gpt-4",
                properties.getProviders().get("openai").getModel());
    }

    @Test
    void aiProperties_ProvidersMapCanBeNull() {
        AiProperties properties = new AiProperties();

        properties.setProviders(null);

        assertNull(properties.getProviders());
    }

    @Test
    void aiProperties_SupportsMultipleProviders() {
        AiProperties properties = new AiProperties();

        AiProperties.ProviderProps openAi = new AiProperties.ProviderProps();
        openAi.setApiKey("openai-key");

        AiProperties.ProviderProps deepSeek = new AiProperties.ProviderProps();
        deepSeek.setApiKey("deepseek-key");

        Map<String, AiProperties.ProviderProps> providers = new HashMap<>();
        providers.put("openai", openAi);
        providers.put("deepseek", deepSeek);

        properties.setProviders(providers);

        assertEquals(2, properties.getProviders().size());
        assertEquals("openai-key",
                properties.getProviders().get("openai").getApiKey());
        assertEquals("deepseek-key",
                properties.getProviders().get("deepseek").getApiKey());
    }
}
