package com.backend.eventsrus.service;

import com.backend.eventsrus.exception.FileUploadException;
import java.io.IOException;
import java.io.InputStream;
import java.time.Duration;
import java.util.UUID;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Service;
import org.springframework.web.multipart.MultipartFile;
import software.amazon.awssdk.core.sync.RequestBody;
import software.amazon.awssdk.services.s3.S3Client;
import software.amazon.awssdk.services.s3.model.GetObjectRequest;
import software.amazon.awssdk.services.s3.model.PutObjectRequest;
import software.amazon.awssdk.services.s3.presigner.S3Presigner;
import software.amazon.awssdk.services.s3.presigner.model.GetObjectPresignRequest;

@Service
public class S3UploadService {

    private final S3Client s3Client;
    private final S3Presigner s3Presigner;
    private final String publicBucket;
    private final String privateBucket;
    private final String region;

    public S3UploadService(
            S3Client s3Client,
            S3Presigner s3Presigner,
            @Value("${aws.s3.public-bucket}") String publicBucket,
            @Value("${aws.s3.private-bucket}") String privateBucket,
            @Value("${aws.s3.region}") String region) {
        this.s3Client = s3Client;
        this.s3Presigner = s3Presigner;
        this.publicBucket = publicBucket;
        this.privateBucket = privateBucket;
        this.region = region;
    }

    public UploadResult upload(MultipartFile file, String keyPrefix, Visibility visibility) {
        String extension = extractExtension(file.getOriginalFilename());
        String key = "%s-%s%s".formatted(keyPrefix, UUID.randomUUID(), extension);
        String bucket = bucketFor(visibility);

        try (InputStream in = file.getInputStream()) {
            s3Client.putObject(
                    PutObjectRequest.builder()
                            .bucket(bucket)
                            .key(key)
                            .contentType(file.getContentType())
                            .build(),
                    RequestBody.fromInputStream(in, file.getSize()));
        } catch (IOException e) {
            throw new FileUploadException("Failed to read uploaded file", e);
        }

        // A private-bucket URL would just 403 if ever used directly — only
        // public uploads get a usable plain URL. Private files are read back
        // exclusively via presignedUrl(...).
        String url = visibility == Visibility.PUBLIC
                ? "https://%s.s3.%s.amazonaws.com/%s".formatted(bucket, region, key)
                : null;
        return new UploadResult(key, url);
    }

    /**
     * Generates a short-lived signed GET URL for a private-bucket object.
     * Returns null if {@code key} is null (i.e. that file was never uploaded).
     */
    public String presignedUrl(String key, Duration ttl) {
        if (key == null) {
            return null;
        }

        GetObjectPresignRequest presignRequest = GetObjectPresignRequest.builder()
                .signatureDuration(ttl)
                .getObjectRequest(GetObjectRequest.builder()
                        .bucket(privateBucket)
                        .key(key)
                        .build())
                .build();

        return s3Presigner.presignGetObject(presignRequest).url().toString();
    }

    private String bucketFor(Visibility visibility) {
        return visibility == Visibility.PUBLIC ? publicBucket : privateBucket;
    }

    private String extractExtension(String originalFilename) {
        if (originalFilename == null) {
            return "";
        }
        int dotIndex = originalFilename.lastIndexOf('.');
        return dotIndex >= 0 ? originalFilename.substring(dotIndex) : "";
    }

    public enum Visibility {
        PUBLIC, PRIVATE
    }

    public record UploadResult(String key, String url) {
    }
}
