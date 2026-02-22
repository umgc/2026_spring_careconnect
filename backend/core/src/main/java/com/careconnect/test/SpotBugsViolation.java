package com.careconnect.test;
import java.io.FileInputStream;
import java.io.IOException;
/**
 * Demonstrates SpotBugs violations for CI/CD pipeline testing.
 */
public class SpotBugsViolation {
    /**
     * Triggers null dereference and resource leak.
     * @param input input string
     */
    public void riskyMethod(final String input) {
        String value = null;
        if (input.equals("trigger")) { value = "set"; }
        System.out.println(value.length());
        try {
            FileInputStream fis = new FileInputStream("test.txt");
            int data = fis.read();
            System.out.println(data);
        } catch (IOException e) {
            throw new RuntimeException("Failed", e);
        }
    }
    /**
     * Triggers ignored return value.
     * @param str the string
     */
    public void ignoredReturn(final String str) {
        str.replace("old", "new");
    }
}
