package com.careconnect.test;
/**
 * Legacy config with fake credentials for TruffleHog testing.
 * DO NOT use in real code.
 */
public class LegacyConfig {
    private static final String AWS_KEY = "AKIAIOSFODNN7EXAMPLE";
    private static final String AWS_SECRET = "wJalrXUtnFEMI/K7MDENG/bPxRfiCYEXAMPLEKEY";
    /**
     * Returns the AWS key.
     * @return aws key string
     */
    public String getAwsKey() { return AWS_KEY; }
}
