package com.foodtrace.service;

import com.foodtrace.common.BizException;
import com.foodtrace.common.ErrorCode;
import com.foodtrace.storage.StorageService;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;

import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;
import java.security.NoSuchAlgorithmException;
import java.util.HexFormat;
import java.util.Locale;

/**
 * 区分文件存证与业务摘要，上链前核对文件内容。
 *
 * @author Microft0629
 */
@Service
@RequiredArgsConstructor
public class EvidenceService {
    private static final String FILE_PREFIX = "file:sha256:";
    private final StorageService storageService;

    /** 缺省时计算业务摘要；自填旧格式保留原含义，不冒充文件存证。 */
    public String normalize(String provided, String content) {
        if (provided == null || provided.isBlank()) {
            return "sha256:" + sha256(content.getBytes(StandardCharsets.UTF_8));
        }
        String value = provided.trim();
        if (value.startsWith("file:")) {
            return requireFile(value);
        }
        return value;
    }

    /** 质检必须提交真实文件引用，文件缺失或内容不符时禁止上链。 */
    public String requireFile(String reference) {
        if (reference == null || !reference.matches("file:sha256:[0-9a-fA-F]{64}")) {
            throw new BizException(ErrorCode.PARAM_ERROR, "请先上传检测报告文件");
        }
        String hash = reference.substring(FILE_PREFIX.length()).toLowerCase(Locale.ROOT);
        StorageService.EvidenceFile file = storageService.get(hash);
        if (file == null) {
            throw new BizException(ErrorCode.PARAM_ERROR, "存证文件不存在，请重新上传");
        }
        verify(hash, file.data());
        return FILE_PREFIX + hash;
    }

    /** 下载与上链共用内容检查，避免对象被替换后继续作为原文返回。 */
    public static void verify(String expected, byte[] data) {
        if (!sha256(data).equalsIgnoreCase(expected)) {
            throw new BizException(ErrorCode.INVALID_STATE, "存证文件内容与哈希不符，请联系管理员核查");
        }
    }

    public static String sha256(byte[] data) {
        try {
            return HexFormat.of().formatHex(MessageDigest.getInstance("SHA-256").digest(data));
        } catch (NoSuchAlgorithmException e) {
            throw new IllegalStateException("SHA-256 不可用", e);
        }
    }
}
