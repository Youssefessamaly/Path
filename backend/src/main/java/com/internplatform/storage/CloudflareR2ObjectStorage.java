package com.internplatform.storage;

import jakarta.annotation.PreDestroy;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.context.annotation.Profile;
import org.springframework.stereotype.Component;
import software.amazon.awssdk.auth.credentials.AwsBasicCredentials;
import software.amazon.awssdk.auth.credentials.StaticCredentialsProvider;
import software.amazon.awssdk.core.sync.RequestBody;
import software.amazon.awssdk.core.checksums.RequestChecksumCalculation;
import software.amazon.awssdk.core.checksums.ResponseChecksumValidation;
import software.amazon.awssdk.regions.Region;
import software.amazon.awssdk.services.s3.S3Client;
import software.amazon.awssdk.services.s3.S3Configuration;
import software.amazon.awssdk.services.s3.model.DeleteObjectRequest;
import software.amazon.awssdk.services.s3.model.GetObjectRequest;
import software.amazon.awssdk.services.s3.model.ListObjectsV2Request;
import software.amazon.awssdk.services.s3.model.ListObjectsV2Response;
import software.amazon.awssdk.services.s3.model.PutObjectRequest;
import software.amazon.awssdk.services.s3.model.S3Exception;
import software.amazon.awssdk.services.s3.model.S3Object;

import java.net.URI;
import java.time.Duration;

/**
 * Cloudflare R2 S3-compatible private object storage. No local upload directories, public
 * bucket URLs, or AWS default credential chain. Do not log credentials or file contents.
 */
@Component
@Profile("!test")
public class CloudflareR2ObjectStorage implements ObjectStorage {
    private final S3Client s3;
    private final String bucket;

    public CloudflareR2ObjectStorage(
            @Value("${STORAGE_PROVIDER:r2}") String provider,
            @Value("${R2_BUCKET:}") String bucket,
            @Value("${R2_ENDPOINT:}") String endpoint,
            @Value("${R2_REGION:auto}") String region,
            @Value("${R2_ACCESS_KEY_ID:}") String accessKey,
            @Value("${R2_SECRET_ACCESS_KEY:}") String secretKey) {
        if (!"r2".equalsIgnoreCase(provider) || bucket.isBlank() || endpoint.isBlank()
                || accessKey.isBlank() || secretKey.isBlank()) {
            throw new IllegalStateException("R2 object storage configuration is incomplete: check "
                    + "STORAGE_PROVIDER, R2_BUCKET, R2_ENDPOINT, R2_ACCESS_KEY_ID, R2_SECRET_ACCESS_KEY");
        }
        URI uri = URI.create(endpoint);
        if (!"https".equalsIgnoreCase(uri.getScheme()) || uri.getHost() == null
                || uri.getUserInfo() != null || uri.getQuery() != null || uri.getFragment() != null) {
            throw new IllegalStateException("R2_ENDPOINT must be the HTTPS S3 API endpoint");
        }
        this.bucket = bucket;
        this.s3 = S3Client.builder()
                .endpointOverride(uri)
                .region(Region.of(region))
                .credentialsProvider(StaticCredentialsProvider.create(
                        AwsBasicCredentials.create(accessKey, secretKey)))
                // Avoid automatic CRC32 headers unsupported by some R2 S3 integrations.
                .requestChecksumCalculation(RequestChecksumCalculation.WHEN_REQUIRED)
                .responseChecksumValidation(ResponseChecksumValidation.WHEN_REQUIRED)
                .serviceConfiguration(S3Configuration.builder()
                        .pathStyleAccessEnabled(true)
                        .chunkedEncodingEnabled(false)
                        .build())
                .overrideConfiguration(builder -> builder
                        .apiCallAttemptTimeout(Duration.ofSeconds(20))
                        .apiCallTimeout(Duration.ofSeconds(60)))
                .build();
    }

    @Override
    public void put(String key, byte[] bytes, String contentType) {
        s3.putObject(PutObjectRequest.builder().bucket(bucket).key(key)
                .contentLength((long) bytes.length).contentType(contentType).build(),
                RequestBody.fromBytes(bytes));
    }

    @Override
    public byte[] get(String key) {
        return s3.getObjectAsBytes(GetObjectRequest.builder().bucket(bucket).key(key).build())
                .asByteArray();
    }

    @Override
    public void delete(String key) {
        s3.deleteObject(DeleteObjectRequest.builder().bucket(bucket).key(key).build());
    }

    @Override
    public void deletePrefix(String prefix) {
        if (prefix == null || prefix.isBlank() || !prefix.endsWith("/")) {
            throw new IllegalArgumentException("Refusing to delete an incomplete object prefix");
        }
        for (ListObjectsV2Response page : s3.listObjectsV2Paginator(
                ListObjectsV2Request.builder().bucket(bucket).prefix(prefix).build())) {
            for (S3Object object : page.contents()) {
                if (!object.key().startsWith(prefix)) {
                    throw new IllegalStateException("R2 returned a key outside the deletion prefix");
                }
                delete(object.key());
            }
        }
    }

    @PreDestroy
    public void close() {
        s3.close();
    }
}
