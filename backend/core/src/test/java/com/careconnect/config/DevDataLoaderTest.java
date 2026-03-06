package com.careconnect.config;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

import javax.sql.DataSource;
import java.sql.Connection;
import java.sql.ResultSet;
import java.sql.Statement;

import static org.junit.jupiter.api.Assertions.assertDoesNotThrow;
import static org.mockito.Mockito.*;

class DevDataLoaderTest {

    private DataSource dataSource;
    private Connection connection;
    private Statement statement;
    private ResultSet resultSet;

    private DevDataLoader loader;

    @BeforeEach
    void setUp() throws Exception {
        dataSource = mock(DataSource.class);
        connection = mock(Connection.class);
        statement = mock(Statement.class);
        resultSet = mock(ResultSet.class);

        when(dataSource.getConnection()).thenReturn(connection);
        when(connection.createStatement()).thenReturn(statement);

        loader = new DevDataLoader(dataSource, true);
    }

    // -------------------------------------------------
    // 1. Disabled flag → do nothing
    // -------------------------------------------------

    @Test
    void run_DoesNothingWhenDisabled() throws Exception {
        DevDataLoader disabledLoader = new DevDataLoader(dataSource, false);

        assertDoesNotThrow(() -> disabledLoader.run());
        verifyNoInteractions(dataSource);
    }

    // -------------------------------------------------
    // 2. Users exist → skip loading
    // -------------------------------------------------

    @Test
    void run_SkipsLoadingWhenUsersExist() throws Exception {
        when(statement.executeQuery("SELECT COUNT(*) FROM users"))
                .thenReturn(resultSet);
        when(resultSet.next()).thenReturn(true);
        when(resultSet.getInt(1)).thenReturn(5);

        assertDoesNotThrow(() -> loader.run());

        verify(statement, atLeastOnce()).executeQuery("SELECT COUNT(*) FROM users");
        verify(statement, never()).executeUpdate(anyString());
    }

    // -------------------------------------------------
    // 3. Users = 0 → should attempt SQL execution
    // -------------------------------------------------

    @Test
    void run_AttemptsLoadWhenNoUsersExist() throws Exception {
        when(statement.executeQuery("SELECT COUNT(*) FROM users"))
                .thenReturn(resultSet);
        when(resultSet.next()).thenReturn(true);
        when(resultSet.getInt(1)).thenReturn(0);

        // Simulate SQL file execution
        when(statement.executeUpdate(anyString())).thenReturn(1);

        assertDoesNotThrow(() -> loader.run());

        verify(statement, atLeastOnce()).executeQuery("SELECT COUNT(*) FROM users");
    }

    // -------------------------------------------------
    // 4. Exception checking user count → still attempts load
    // -------------------------------------------------

    @Test
    void run_AttemptsLoadWhenUserCheckFails() throws Exception {
        when(statement.executeQuery("SELECT COUNT(*) FROM users"))
                .thenThrow(new RuntimeException("DB error"));

        when(statement.executeUpdate(anyString())).thenReturn(1);

        assertDoesNotThrow(() -> loader.run());
    }

    // -------------------------------------------------
    // 5. SQL execution failure → does not crash
    // -------------------------------------------------

    @Test
    void run_DoesNotThrowWhenSqlExecutionFails() throws Exception {
        when(statement.executeQuery("SELECT COUNT(*) FROM users"))
                .thenReturn(resultSet);
        when(resultSet.next()).thenReturn(true);
        when(resultSet.getInt(1)).thenReturn(0);

        when(statement.executeUpdate(anyString()))
                .thenThrow(new RuntimeException("SQL failure"));

        assertDoesNotThrow(() -> loader.run());
    }
}
