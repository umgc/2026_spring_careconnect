package com.careconnect.test;
/** Clean service for CI testing. */
public class CleanService {
    /** Name. */
    private final String name;
    /** Constructor. @param name service name */
    public CleanService(final String name) { this.name = name; }
    /** Greet. @return greeting */
    public String greet() { return "Hello from " + name; }
}
