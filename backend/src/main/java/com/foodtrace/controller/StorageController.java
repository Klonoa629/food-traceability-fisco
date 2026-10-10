package com.foodtrace.controller;

import com.foodtrace.common.BizException;
import com.foodtrace.common.ErrorCode;
import com.foodtrace.common.Result;
import com.foodtrace.security.RateLimitService;
import com.foodtrace.storage.StorageService;
import jakarta.servlet.http.HttpServletRequest;
import lombok.RequiredArgsConstructor;
import org.springframework.core.io.ByteArrayResource;
import org.springframework.http.ContentDisposition;
import org.springframework.http.HttpHeaders;
import org.springframework.http.MediaType;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;
import org.springframework.web.multipart.MultipartFile;

import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.util.regex.Pattern;

/**
 * 存证文件接口：上传需登录，下载匿名开放（消费者扫码核验）
 *
 * @author Microft0629
 * @since 2026-10-10
 */
@RestController
@RequestMapping("/api/storage")
@RequiredArgsConstructor
public class StorageController {
    /** 64 位十六进制哈希 */
    private static final Pattern SHA256 = Pattern.compile("^[0-9a-fA-F]{64}$");

    /** 存证存储服务 */
    private final StorageService storageService;
    /** 限流服务 */
    private final RateLimitService rateLimitService;

    /**
     * 上传存证文件，返回内容哈希（可直接作为业务接口的 dataHash）
     *
     * @param file 上传的文件
     * @return 存证信息
     * @throws IOException 读取文件失败时抛出
     */
    @PostMapping("/upload")
    public Result<StorageService.EvidenceInfo> upload(@RequestParam("file") MultipartFile file)
            throws IOException {
        if (file.isEmpty()) {
            throw new BizException(ErrorCode.PARAM_ERROR, "文件不能为空");
        }
        String filename = file.getOriginalFilename() == null ? "evidence" : file.getOriginalFilename();
        return Result.ok(storageService.put(file.getBytes(), filename));
    }

    /**
     * 按内容哈希下载存证原文
     *
     * @param sha256 内容哈希（即链上 data_hash）
     * @param request 当前请求
     * @return 文件流，不存在返回 404
     */
    @GetMapping("/{sha256}")
    public ResponseEntity<ByteArrayResource> download(@PathVariable String sha256,
                                                      HttpServletRequest request) {
        rateLimitService.checkIpLimit(request, "public");
        if (!SHA256.matcher(sha256).matches()) {
            return ResponseEntity.notFound().build();
        }
        StorageService.EvidenceFile evidence = storageService.get(sha256);
        if (evidence == null) {
            return ResponseEntity.notFound().build();
        }
        HttpHeaders headers = new HttpHeaders();
        headers.setContentType(MediaType.APPLICATION_OCTET_STREAM);
        headers.setContentDisposition(ContentDisposition.attachment()
                .filename(evidence.filename(), StandardCharsets.UTF_8).build());
        return ResponseEntity.ok().headers(headers)
                .body(new ByteArrayResource(evidence.data()));
    }
}
