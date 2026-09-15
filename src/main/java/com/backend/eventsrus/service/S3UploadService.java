package com.backend.eventsrus.service;

import com.backend.eventsrus.exception.FileUploadException;
import java.awt.Graphics2D;
import java.awt.RenderingHints;
import java.awt.image.BufferedImage;
import java.io.ByteArrayOutputStream;
import java.io.IOException;
import java.io.InputStream;
import java.time.Duration;
import java.util.Iterator;
import java.util.UUID;
import javax.imageio.IIOImage;
import javax.imageio.ImageIO;
import javax.imageio.ImageWriteParam;
import javax.imageio.ImageWriter;
import javax.imageio.stream.ImageOutputStream;
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

    // A phone-camera photo routinely arrives at 20-60 megapixels (8-10 MB) -
    // fine for the photographer's own archive, disastrous for a storefront
    // that only ever displays it as a gallery thumbnail or a 56px package
    // icon. Every image upload (package photos, gallery, legal documents,
    // payment QR codes, logo) funnels through this one method, so resizing
    // here - rather than in each of those separate services - guarantees
    // nothing oversized reaches R2 no matter which feature uploaded it.
    // 1600px is generous for anything this app ever renders (the largest
    // on-page use is a full-width gallery photo in a modal, well under
    // 1600px on any real device) while still looking sharp on a Retina
    // display at the actual display sizes involved.
    private static final int MAX_IMAGE_DIMENSION = 1600;
    private static final float JPEG_QUALITY = 0.85f;

    public UploadResult upload(MultipartFile file, String keyPrefix, Visibility visibility) {
        byte[] bytes;
        String contentType;
        String extension;
        try {
            if (isResizableImage(file.getContentType())) {
                ResizedImage resized = resizeIfOversized(file);
                bytes = resized.bytes();
                contentType = resized.contentType();
                extension = resized.extension();
            } else {
                bytes = file.getBytes();
                contentType = file.getContentType();
                extension = extractExtension(file.getOriginalFilename());
            }
        } catch (IOException e) {
            throw new FileUploadException("Failed to read uploaded file", e);
        }

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
            try {
                s3Client.putObject(
                        PutObjectRequest.builder()
                                .bucket(bucket)
                                .key(key)
                                .contentType(contentType)
                                .build(),
                        RequestBody.fromBytes(bytes));
                lastFailure = null;
                break;
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

    // Matches the ALLOWED_IMAGE_TYPES check already enforced independently
    // by SupportTicketService/VendorPackageImageService/VendorPaymentMethodService
    // before a file ever reaches here - anything else (PDFs: legal
    // documents, quotations, invoices) is left completely untouched.
    private static boolean isResizableImage(String contentType) {
        return "image/jpeg".equals(contentType) || "image/png".equals(contentType);
    }

    /**
     * Downscales an image to fit within {@link #MAX_IMAGE_DIMENSION} on its
     * longest edge, re-encoding as JPEG (or PNG, if the source has an alpha
     * channel - e.g. a logo - since JPEG can't represent transparency).
     * Left completely alone if it's already small, or if it doesn't even
     * decode as a raster image (upload as-is and let it fail wherever a
     * genuinely broken file would already have failed).
     */
    private ResizedImage resizeIfOversized(MultipartFile file) throws IOException {
        BufferedImage original;
        try (InputStream in = file.getInputStream()) {
            original = ImageIO.read(in);
        }
        if (original == null) {
            return new ResizedImage(file.getBytes(), file.getContentType(), extractExtension(file.getOriginalFilename()));
        }

        int width = original.getWidth();
        int height = original.getHeight();
        if (Math.max(width, height) <= MAX_IMAGE_DIMENSION) {
            return new ResizedImage(file.getBytes(), file.getContentType(), extractExtension(file.getOriginalFilename()));
        }

        double scale = MAX_IMAGE_DIMENSION / (double) Math.max(width, height);
        int newWidth = Math.max(1, (int) Math.round(width * scale));
        int newHeight = Math.max(1, (int) Math.round(height * scale));

        boolean hasAlpha = original.getColorModel().hasAlpha();
        BufferedImage scaled = new BufferedImage(
                newWidth, newHeight, hasAlpha ? BufferedImage.TYPE_INT_ARGB : BufferedImage.TYPE_INT_RGB);
        Graphics2D g = scaled.createGraphics();
        g.setRenderingHint(RenderingHints.KEY_INTERPOLATION, RenderingHints.VALUE_INTERPOLATION_BILINEAR);
        g.setRenderingHint(RenderingHints.KEY_RENDERING, RenderingHints.VALUE_RENDER_QUALITY);
        g.setRenderingHint(RenderingHints.KEY_ANTIALIASING, RenderingHints.VALUE_ANTIALIAS_ON);
        g.drawImage(original, 0, 0, newWidth, newHeight, null);
        g.dispose();

        ByteArrayOutputStream out = new ByteArrayOutputStream();
        if (hasAlpha) {
            ImageIO.write(scaled, "png", out);
            return new ResizedImage(out.toByteArray(), "image/png", ".png");
        }
        writeJpeg(scaled, out);
        return new ResizedImage(out.toByteArray(), "image/jpeg", ".jpg");
    }

    private void writeJpeg(BufferedImage image, ByteArrayOutputStream out) throws IOException {
        Iterator<ImageWriter> writers = ImageIO.getImageWritersByFormatName("jpg");
        ImageWriter writer = writers.next();
        try {
            ImageWriteParam param = writer.getDefaultWriteParam();
            param.setCompressionMode(ImageWriteParam.MODE_EXPLICIT);
            param.setCompressionQuality(JPEG_QUALITY);
            try (ImageOutputStream ios = ImageIO.createImageOutputStream(out)) {
                writer.setOutput(ios);
                writer.write(null, new IIOImage(image, null, null), param);
            }
        } finally {
            writer.dispose();
        }
    }

    public enum Visibility {
        PUBLIC, PRIVATE
    }

    public record UploadResult(String key, String url) {
    }

    private record ResizedImage(byte[] bytes, String contentType, String extension) {
    }
}
