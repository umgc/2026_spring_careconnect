package com.careconnect.test;
public class CheckstyleViolation {
    public void BadMethodName() {
        int x = 12345678;
        System.out.println(x);
    }
    public void anotherBadMethodName() { int y = 99999999; System.out.println("This line is intentionally very long to trigger the line length checkstyle rule which is usually set to 100 or 120 characters"); }
}
