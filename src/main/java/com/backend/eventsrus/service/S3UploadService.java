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
    private final String publicBaseUrl;

    public S3UploadService(
            S3Client s3Client,
            S3Presigner s3Presigner,
            @Value("${r2.public-bucket}") String publicBucket,
            @Value("${r2.private-bucket}") String privateBucket,
            @Value("${r2.public-base-url}") String publicBaseUrl) {
        this.s3Client = s3Client;
        this.s3Presigner = s3Presigner;
        this.publicBucket = publicBucket;
        this.privateBucket = privateBucket;
        this.publicBaseUrl = publicBaseUrl;
    }

    private static final int MAX_UPLOAD_ATTEMPTS = 2;

    public UploadResult upload(MultipartFile file, String keyPrefix, Visibility visibility) {
        String extension = extractExtension(file.getOriginalFilename());
        String key = "%s-%s%s".formatted(keyPrefix, UUID.randomUUID(), extension);
        String bucket = bucketFor(visibility);

        // One retry on a transient network/DNS blip reaching R2 - the same
        // class of intermittent failure already seen and fixed for
        // reCAPTCHA (RecaptchaVerificationService) this project. Unlike
        // that fail-open case, an upload that still fails after the retry
        // is a real failure that has to surface - there's no safe default
        // to fall back to when the file genuinely never made it to R2.
        RuntimeException lastFailure = null;
        for (int attempt = 1; attempt <= MAX_UPLOAD_ATTEMPTS; attempt++) {
            try (InputStream in = file.getInputStream()) {
                s3Client.putObject(
                        PutObjectRequest.builder()
                                .bucket(bucket)
                                .key(key)
                                .contentType(file.getContentType())
                                .build(),
                        RequestBody.fromInputStream(in, file.getSize()));
                lastFailure = null;
                break;
            } catch (IOException e) {
                throw new FileUploadException("Failed to read uploaded file", e);
            } catch (RuntimeException e) {
                lastFailure = e;
            }
        }
        if (lastFailure != null) {
            throw new FileUploadException("Failed to upload file to storage after retrying", lastFailure);
        }

        // A private-bucket URL would just 403 if ever used directly — only
        // public uploads get a usable plain URL. Private files are read back
        // exclusively via presignedUrl(...). publicBaseUrl is the CDN-fronted
        // custom domain connected to the public R2 bucket (or its plain
        // r2.dev URL in dev, where no custom domain is connected) - not a
        // raw R2 endpoint URL, so this stays a simple concatenation.
        String url = visibility == Visibility.PUBLIC
                ? publicBaseUrl + "/" + key
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
