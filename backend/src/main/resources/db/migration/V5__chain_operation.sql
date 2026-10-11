-- 广播前持久化签名交易；同一幂等键始终重发同一份交易，不重新签名
CREATE TABLE chain_operation (
    operation_key CHAR(64) PRIMARY KEY,
    request_id VARCHAR(80) NOT NULL,
    sign_user_id VARCHAR(64) NOT NULL,
    method VARCHAR(64) NOT NULL,
    payload_hash CHAR(64) NOT NULL,
    tx_hash VARCHAR(70) NOT NULL,
    signed_tx MEDIUMTEXT NOT NULL,
    status VARCHAR(16) NOT NULL DEFAULT 'PENDING',
    attempts INT NOT NULL DEFAULT 0,
    last_error VARCHAR(512) NULL,
    created_at DATETIME NOT NULL DEFAULT CURRENT_TIMESTAMP,
    updated_at DATETIME NOT NULL DEFAULT CURRENT_TIMESTAMP ON UPDATE CURRENT_TIMESTAMP,
    KEY idx_tx_hash (tx_hash),
    KEY idx_status (status, updated_at),
    KEY idx_sign_user (sign_user_id, created_at)
) ENGINE = InnoDB DEFAULT CHARSET = utf8mb4 COMMENT = '写链操作与恢复记录';
