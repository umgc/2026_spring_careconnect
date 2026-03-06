package com.careconnect.config;

import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import software.amazon.awssdk.auth.credentials.DefaultCredentialsProvider;
import software.amazon.awssdk.regions.Region;
import software.amazon.awssdk.services.s3.S3Client;
import software.amazon.awssdk.services.ssm.SsmClient;
import software.amazon.awssdk.services.textract.TextractClient;

import static org.junit.jupiter.api.Assertions.*;

class AwsAccessConfigTest {

    private AwsAccessConfig config;

    @BeforeEach
    void setUp() {
        // Ensure region is set so DefaultAwsRegionProviderChain doesn't fail
        System.setProperty("aws.region", "us-east-1");
        config = new AwsAccessConfig();
    }

    @AfterEach
    void tearDown() {
        System.clearProperty("aws.region");
    }

    @Test
    void defaultAwsRegion_ReturnsRegion() {
        Region region = config.defaultAwsRegion();
        assertNotNull(region);
        assertEquals("us-east-1", region.id());
    }

    @Test
    void awsCredentialsProvider_IsCreated() {
        DefaultCredentialsProvider provider = config.awsCredentialsProvider();
        assertNotNull(provider);
    }

    @Test
    void s3Client_IsCreatedSuccessfully() {
        S3Client s3Client = config.s3Client();
        assertNotNull(s3Client);
    }

    @Test
    void ssmClient_IsCreatedSuccessfully() {
        DefaultCredentialsProvider provider = config.awsCredentialsProvider();
        SsmClient ssmClient = config.ssmClient(provider);
        assertNotNull(ssmClient);
    }

    @Test
    void textractClient_IsCreatedSuccessfully() {
        TextractClient textractClient = config.textractClient();
        assertNotNull(textractClient);
    }

    @Test
    void allBeansCanBeCreatedTogether() {
        Region region = config.defaultAwsRegion();
        DefaultCredentialsProvider provider = config.awsCredentialsProvider();
        S3Client s3 = config.s3Client();
        SsmClient ssm = config.ssmClient(provider);
        TextractClient textract = config.textractClient();

        assertNotNull(region);
        assertNotNull(provider);
        assertNotNull(s3);
        assertNotNull(ssm);
        assertNotNull(textract);
    }
}
