package com.foodtrace.storage;

import com.foodtrace.config.StorageProperties;
import io.minio.BucketExistsArgs;
import io.minio.GetObjectArgs;
import io.minio.GetObjectResponse;
import io.minio.MakeBucketArgs;
import io.minio.MinioClient;
import io.minio.PutObjectArgs;
import io.minio.StatObjectArgs;
import io.minio.StatObjectResponse;
import io.minio.errors.ErrorResponseException;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;

import java.io.ByteArrayInputStream;
import java.net.URLDecoder;
import java.net.URLEncoder;
import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;
import java.security.NoSuchAlgorithmException;
import java.util.Map;
import java.util.concurrent.atomic.AtomicBoolean;

/**
 * MinIO 存证存储实现
 *
 * <p>对象键为内容 SHA-256，文件名存于对象元数据；桶在首次访问时创建。
 *
 * @author Microft0629
 * @since 2026-10-10
 */
@Slf4j
@Service
public class MinioStorageService implements StorageService {
    /** 元数据键：原始文件名 */
    private static final String META_FILENAME = "filename";

    /** MinIO 客户端 */
    private final MinioClient client;
    /** 桶名 */
    private final String bucket;
    /** 桶就绪标记（首次访问时创建） */
    private final AtomicBoolean bucketReady = new AtomicBoolean(false);

    /**
     * 以配置构造 MinIO 客户端
     *
     * @param properties 存储配置
     */
    public MinioStorageService(StorageProperties properties) {
        this.client = MinioClient.builder()
                .endpoint(properties.getEndpoint())
                .credentials(properties.getAccessKey(), properties.getSecretKey())
                .build();
        this.bucket = properties.getBucket();
    }

    /**
     * 上传存证文件
     *
     * @param data     文件字节
     * @param filename 原始文件名
     * @return 存证信息
     */
    @Override
    public EvidenceInfo put(byte[] data, String filename) {
        ensureBucket();
        String sha256 = sha256Hex(data);
        try {
            client.putObject(PutObjectArgs.builder()
                    .bucket(bucket).object(sha256)
                    .stream(new ByteArrayInputStream(data), data.length, -1)
                    .contentType("application/octet-stream")
                    .userMetadata(Map.of(META_FILENAME,
                            URLEncoder.encode(filename, StandardCharsets.UTF_8)))
                    .build());
        } catch (Exception e) {
            throw new IllegalStateException("存证上传失败：" + e.getMessage(), e);
        }
        return new EvidenceInfo(sha256, filename, data.length);
    }

    /**
     * 按哈希取回存证文件
     *
     * @param sha256 内容哈希
     * @return 文件内容与元数据，不存在返回 null
     */
    @Override
    public EvidenceFile get(String sha256) {
        ensureBucket();
        try {
            StatObjectResponse stat = client.statObject(
                    StatObjectArgs.builder().bucket(bucket).object(sha256).build());
            String encoded = stat.headers().get("x-amz-meta-" + META_FILENAME);
            String filename = encoded == null ? sha256
                    : URLDecoder.decode(encoded, StandardCharsets.UTF_8);
            try (GetObjectResponse object = client.getObject(
                    GetObjectArgs.builder().bucket(bucket).object(sha256).build())) {
                return new EvidenceFile(filename, object.readAllBytes());
            }
        } catch (ErrorResponseException notFound) {
            return null;
        } catch (Exception e) {
            throw new IllegalStateException("存证读取失败：" + e.getMessage(), e);
        }
    }

    /**
     * 存储健康检查：桶缺失时自动创建，DOWN 仅表示存储不可达
     *
     * @return 桶可访问返回 true
     */
    @Override
    public boolean healthy() {
        try {
            ensureBucket();
            return client.bucketExists(BucketExistsArgs.builder().bucket(bucket).build());
        } catch (Exception e) {
            log.warn("存证存储健康检查失败：{}", e.getMessage());
            return false;
        }
    }

    /**
     * 首次访问时确保桶存在
     */
    private void ensureBucket() {
        if (bucketReady.get()) {
            return;
        }
        synchronized (bucketReady) {
            if (bucketReady.get()) {
                return;
            }
            try {
                if (!client.bucketExists(BucketExistsArgs.builder().bucket(bucket).build())) {
                    client.makeBucket(MakeBucketArgs.builder().bucket(bucket).build());
                    log.info("存证桶已创建：{}", bucket);
                }
                bucketReady.set(true);
            } catch (Exception e) {
                throw new IllegalStateException("存证桶不可用：" + e.getMessage(), e);
            }
        }
    }

    /**
     * 计算内容 SHA-256
     */
    private static String sha256Hex(byte[] data) {
        try {
            byte[] digest = MessageDigest.getInstance("SHA-256").digest(data);
            StringBuilder hex = new StringBuilder(digest.length * 2);
            for (byte b : digest) {
                hex.append(String.format("%02x", b));
            }
            return hex.toString();
        } catch (NoSuchAlgorithmException e) {
            throw new IllegalStateException("SHA-256 不可用", e);
        }
    }
}
