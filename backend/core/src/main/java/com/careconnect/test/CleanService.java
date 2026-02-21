package com.careconnect.test;
/**
 * Clean service class for CI/CD pipeline testing.
 */
public class CleanService {
    /** Service name. */
    private final String name;
    /**
     * Constructs a CleanService.
     * @param name the service name
     */
    public CleanService(final String name) { this.name = name; }
    /**
     * Returns a greeting.
     * @return greeting string
     */
    public String greet() { return "Hello from " + name; }
}
