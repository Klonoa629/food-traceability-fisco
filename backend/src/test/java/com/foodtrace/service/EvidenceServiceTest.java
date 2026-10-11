package com.foodtrace.service;

import com.foodtrace.common.BizException;
import com.foodtrace.storage.StorageService;
import org.junit.jupiter.api.Test;

import java.nio.charset.StandardCharsets;

import static org.assertj.core.api.Assertions.*;
import static org.mockito.Mockito.*;

/** 文件引用不得绕过原文存在性与完整性检查。 */
class EvidenceServiceTest {
    private final StorageService storage = mock(StorageService.class);
    private final EvidenceService service = new EvidenceService(storage);
    private final byte[] report = "report".getBytes(StandardCharsets.UTF_8);
    private final String hash = EvidenceService.sha256(report);

    @Test
    void missingHashShouldProduceBusinessDigestOnly() {
        assertThat(service.normalize(null, "report")).isEqualTo("sha256:" + hash);
        verifyNoInteractions(storage);
    }

    @Test
    void legacyDigestShouldNotBeAssumedToBeAFile() {
        assertThat(service.normalize(hash, "report")).isEqualTo(hash);
        verifyNoInteractions(storage);
    }

    @Test
    void inspectorMustSupplyFileReference() {
        for (String invalid : new String[] {"", hash, "sha256:" + hash, "file:sha256:invalid"}) {
            assertThatThrownBy(() -> service.requireFile(invalid)).isInstanceOf(BizException.class);
        }
        assertThatThrownBy(() -> service.requireFile(null)).isInstanceOf(BizException.class);
        verifyNoInteractions(storage);
    }

    @Test
    void missingFileMustNotGoOnChain() {
        assertThatThrownBy(() -> service.requireFile("file:sha256:" + hash))
                .isInstanceOf(BizException.class).hasMessageContaining("不存在");
    }

    @Test
    void overwrittenFileMustNotGoOnChain() {
        when(storage.get(hash)).thenReturn(new StorageService.EvidenceFile("report", new byte[] {1}));
        assertThatThrownBy(() -> service.normalize("file:sha256:" + hash, "report"))
                .isInstanceOf(BizException.class).hasMessageContaining("不符");
    }

    @Test
    void matchingFileShouldReturnNormalizedReference() {
        when(storage.get(hash)).thenReturn(new StorageService.EvidenceFile("report", report));
        assertThat(service.requireFile("file:sha256:" + hash.toUpperCase()))
                .isEqualTo("file:sha256:" + hash);
    }
}
