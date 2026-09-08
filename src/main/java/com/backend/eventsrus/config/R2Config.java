package com.backend.eventsrus.config;

import java.net.URI;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import software.amazon.awssdk.auth.credentials.AwsBasicCredentials;
import software.amazon.awssdk.auth.credentials.StaticCredentialsProvider;
import software.amazon.awssdk.regions.Region;
import software.amazon.awssdk.services.s3.S3Client;
import software.amazon.awssdk.services.s3.S3Configuration;
import software.amazon.awssdk.services.s3.presigner.S3Presigner;

/**
 * Cloudflare R2 - replaces what used to be real AWS S3 here (same AWS SDK,
 * since R2's API is S3-compatible; only the endpoint/credentials/region
 * differ - see S3UploadService, otherwise unchanged). Two differences from
 * plain AWS S3 that matter for this config:
 *   - R2 has no real AWS region; "auto" is Cloudflare's documented
 *     placeholder value, only used for SigV4 request signing.
 *   - R2's endpoint doesn't support virtual-hosted-style addressing
 *     (bucket-as-subdomain) the way AWS's default S3 endpoints do, so
 *     path-style access must be forced explicitly.
 */
@Configuration
public class R2Config {

    @Bean
    public S3Client s3Client(
            @Value("${r2.endpoint}") String endpoint,
            @Value("${r2.access-key-id}") String accessKeyId,
            @Value("${r2.secret-access-key}") String secretAccessKey) {
        return S3Client.builder()
                .region(Region.of("auto"))
                .endpointOverride(URI.create(endpoint))
                .serviceConfiguration(S3Configuration.builder().pathStyleAccessEnabled(true).build())
                .credentialsProvider(StaticCredentialsProvider.create(
                        AwsBasicCredentials.create(accessKeyId, secretAccessKey)))
                .build();
    }

    @Bean
    public S3Presigner s3Presigner(
            @Value("${r2.endpoint}") String endpoint,
            @Value("${r2.access-key-id}") String accessKeyId,
            @Value("${r2.secret-access-key}") String secretAccessKey) {
        return S3Presigner.builder()
                .region(Region.of("auto"))
                .endpointOverride(URI.create(endpoint))
                .serviceConfiguration(S3Configuration.builder().pathStyleAccessEnabled(true).build())
                .credentialsProvider(StaticCredentialsProvider.create(
                        AwsBasicCredentials.create(accessKeyId, secretAccessKey)))
                .build();
    }
}
