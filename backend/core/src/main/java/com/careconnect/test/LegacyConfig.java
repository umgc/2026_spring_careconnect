package com.careconnect.test;
/**
 * Legacy config with fake credentials for TruffleHog testing.
 * DO NOT use patterns like this in real code.
 */
public class LegacyConfig {
    private static final String AWS_KEY = "AKIAIOSFODNN7EXAMPLE";
    private static final String AWS_SECRET = "wJalrXUtnFEMI/K7MDENG/bPxRfiCYEXAMPLEKEY";
    private static final String STRIPE_KEY = "sk_test_REPLACEME_NOT_A_REAL_KEY";
    /**
     * Returns the AWS key.
     * @return aws key string
     */
    public String getAwsKey() { return AWS_KEY; }
}
