-- 按群组与合约保存事件回放位置；重启时包含最后一个块，防止同块事件遗漏
CREATE TABLE chain_event_cursor (
    stream_key VARCHAR(160) PRIMARY KEY,
    block_number BIGINT NOT NULL,
    updated_at DATETIME NOT NULL DEFAULT CURRENT_TIMESTAMP ON UPDATE CURRENT_TIMESTAMP
) ENGINE = InnoDB DEFAULT CHARSET = utf8mb4 COMMENT = '合约事件处理进度';
CREATE INDEX idx_chain_tx_hash ON operate_log (chain_tx_hash);
