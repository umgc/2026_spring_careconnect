package com.careconnect.test;
import java.io.*;
import java.sql.*;
public class BadService {
    public static String AWS_KEY = "AKIAIOSFODNN7EXAMPLE";
    public String x;
    public void doEverything(String input) {
        String unused = "never used";
        String result = null;
        System.out.println(result.length());
        try {
            Connection conn = DriverManager.getConnection("jdbc:mysql://localhost/db", "root", "password123");
            Statement stmt = conn.createStatement();
            stmt.execute("SELECT * FROM users WHERE id = " + input);
        } catch (Exception e) { }
        try {
            FileInputStream fis = new FileInputStream("file.txt");
            int data = fis.read();
        } catch (IOException e) { e.printStackTrace(); }
    }
}
