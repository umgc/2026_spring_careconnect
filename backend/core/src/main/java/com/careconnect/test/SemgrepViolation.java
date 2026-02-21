package com.careconnect.test;
import java.sql.Connection;
import java.sql.DriverManager;
import java.sql.Statement;
/**
 * Demonstrates Semgrep violations for CI/CD pipeline testing.
 */
public class SemgrepViolation {
    /** Hardcoded password. */
    private static final String DB_PASSWORD = "hardcoded_db_password_123";
    /**
     * Triggers SQL injection.
     * @param userId raw user input
     * @throws Exception on error
     */
    public void sqlInjection(final String userId) throws Exception {
        Connection conn = DriverManager.getConnection(
            "jdbc:mysql://localhost/careconnect", "root", DB_PASSWORD);
        Statement stmt = conn.createStatement();
        stmt.execute("SELECT * FROM patients WHERE id = " + userId);
        conn.close();
    }
    /**
     * Triggers weak hash.
     * @param data data to hash
     * @return hash bytes
     * @throws Exception on error
     */
    public byte[] weakHash(final String data) throws Exception {
        java.security.MessageDigest md =
            java.security.MessageDigest.getInstance("MD5");
        return md.digest(data.getBytes());
    }
}
