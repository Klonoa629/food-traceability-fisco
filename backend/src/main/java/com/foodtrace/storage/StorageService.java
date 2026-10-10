package com.foodtrace.storage;

/**
 * 存证对象存储服务
 *
 * <p>对象按内容 SHA-256 寻址：链上的 data_hash 即存储键，
 * 无需额外维护哈希与键的映射。
 *
 * @author Microft0629
 * @since 2026-10-10
 */
public interface StorageService {

    /**
     * 上传存证文件，键为内容的 SHA-256（同内容幂等）
     *
     * @param data     文件字节
     * @param filename 原始文件名
     * @return 存证信息
     */
    EvidenceInfo put(byte[] data, String filename);

    /**
     * 按哈希取回存证文件
     *
     * @param sha256 内容哈希
     * @return 文件内容与元数据，不存在返回 null
     */
    EvidenceFile get(String sha256);

    /**
     * 存储是否可用（桶存在）
     *
     * @return 可用返回 true
     */
    boolean healthy();

    /**
     * 存证信息
     *
     * @param sha256   内容哈希（即存储键）
     * @param filename 原始文件名
     * @param size     字节数
     */
    record EvidenceInfo(String sha256, String filename, long size) {
    }

    /**
     * 存证文件内容
     *
     * @param filename 原始文件名
     * @param data     文件字节
     */
    record EvidenceFile(String filename, byte[] data) {
    }
}
