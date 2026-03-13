package com.careconnect.config;

import com.careconnect.service.ParameterStoreService;
import com.zaxxer.hikari.HikariDataSource;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.mockito.Mockito;
import org.springframework.boot.autoconfigure.jdbc.DataSourceProperties;
import org.springframework.mock.env.MockEnvironment;

import javax.sql.DataSource;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.Mockito.*;

class DatabaseConfigTest {

    private ParameterStoreService parameterStoreService;
    private DatabaseConfig databaseConfig;

    @BeforeEach
    void setUp() {
        parameterStoreService = mock(ParameterStoreService.class);
        databaseConfig = new DatabaseConfig(parameterStoreService);

        // Inject @Value fields manually
        org.springframework.test.util.ReflectionTestUtils.setField(databaseConfig, "jdbcUrl", "db-url-key");
        org.springframework.test.util.ReflectionTestUtils.setField(databaseConfig, "userParameter", "db-user-key");
        org.springframework.test.util.ReflectionTestUtils.setField(databaseConfig, "passwordParameter", "db-pass-key");
    }

    @Test
    void dataSourceProperties_ReturnsCorrectProperties() {
        when(parameterStoreService.getSecureParameter("db-url-key"))
                .thenReturn("jdbc:h2:mem:testdb");
        when(parameterStoreService.getSecureParameter("db-user-key"))
                .thenReturn("sa");
        when(parameterStoreService.getSecureParameter("db-pass-key"))
                .thenReturn("password");

        DataSourceProperties properties = databaseConfig.dataSourceProperties();

        assertEquals("jdbc:h2:mem:testdb", properties.getUrl());
        assertEquals("sa", properties.getUsername());
        assertEquals("password", properties.getPassword());

        verify(parameterStoreService).getSecureParameter("db-url-key");
        verify(parameterStoreService).getSecureParameter("db-user-key");
        verify(parameterStoreService).getSecureParameter("db-pass-key");
    }

    @Test
    void dataSource_BuildsHikariDataSource() {
        when(parameterStoreService.getSecureParameter("db-url-key"))
                .thenReturn("jdbc:h2:mem:testdb");
        when(parameterStoreService.getSecureParameter("db-user-key"))
                .thenReturn("sa");
        when(parameterStoreService.getSecureParameter("db-pass-key"))
                .thenReturn("password");

        DataSourceProperties properties = databaseConfig.dataSourceProperties();

        MockEnvironment env = new MockEnvironment();
        env.setProperty("spring.datasource.hikari.maximum-pool-size", "5");

        DataSource dataSource = databaseConfig.dataSource(properties, env);

        assertNotNull(dataSource);
        assertTrue(dataSource instanceof HikariDataSource);

        HikariDataSource hikari = (HikariDataSource) dataSource;
        assertEquals("jdbc:h2:mem:testdb", hikari.getJdbcUrl());
        assertEquals("sa", hikari.getUsername());
    }

    @Test
    void dataSourceProperties_HandlesNullParameterServiceGracefully() {
        DatabaseConfig configWithoutService = new DatabaseConfig(null);

        org.springframework.test.util.ReflectionTestUtils.setField(configWithoutService, "jdbcUrl", "key1");
        org.springframework.test.util.ReflectionTestUtils.setField(configWithoutService, "userParameter", "key2");
        org.springframework.test.util.ReflectionTestUtils.setField(configWithoutService, "passwordParameter", "key3");

        assertThrows(NullPointerException.class,
                configWithoutService::dataSourceProperties);
    }

    @Test
    void dataSource_BindsHikariPropertiesFromEnvironment() {
        when(parameterStoreService.getSecureParameter("db-url-key"))
                .thenReturn("jdbc:h2:mem:testdb");
        when(parameterStoreService.getSecureParameter("db-user-key"))
                .thenReturn("sa");
        when(parameterStoreService.getSecureParameter("db-pass-key"))
                .thenReturn("password");

        DataSourceProperties properties = databaseConfig.dataSourceProperties();

        MockEnvironment env = new MockEnvironment();
        env.setProperty("spring.datasource.hikari.maximum-pool-size", "7");

        DataSource dataSource = databaseConfig.dataSource(properties, env);

        HikariDataSource hikari = (HikariDataSource) dataSource;

        assertEquals(7, hikari.getMaximumPoolSize());
    }
}
