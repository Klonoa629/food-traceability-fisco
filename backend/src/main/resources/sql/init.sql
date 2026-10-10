USE foodtrace;

CREATE TABLE sys_user (
    id            BIGINT       PRIMARY KEY AUTO_INCREMENT COMMENT '主键',
    username      VARCHAR(64)  NOT NULL COMMENT '登录名',
    password_hash VARCHAR(100) NOT NULL COMMENT '密码哈希',
    pwd_version   INT          NOT NULL DEFAULT 0 COMMENT '密码版本（改密递增，使旧令牌失效）',
    org_name      VARCHAR(128) NOT NULL COMMENT '机构名称',
    role          TINYINT      NOT NULL DEFAULT 0 COMMENT '合约角色 0-6',
    is_regulator  TINYINT      NOT NULL DEFAULT 0 COMMENT '0 = 非监管账户，1 = 监管账户',
    chain_address VARCHAR(64)  NULL COMMENT '链上账户地址（审批时回填）',
    sign_user_id  VARCHAR(64)  NULL COMMENT '签名标识',
    status        TINYINT      NOT NULL DEFAULT 0 COMMENT '0 = 待审批，1 = 生效，2 = 已吊销',
    created_at    DATETIME     NOT NULL DEFAULT CURRENT_TIMESTAMP,
    updated_at    DATETIME     NOT NULL DEFAULT CURRENT_TIMESTAMP ON UPDATE CURRENT_TIMESTAMP,
    UNIQUE KEY uk_username (username),
    UNIQUE KEY uk_sign_user_id (sign_user_id)
) ENGINE = InnoDB DEFAULT CHARSET = utf8mb4 COMMENT = '平台账户';

CREATE TABLE operate_log (
    id            BIGINT       PRIMARY KEY AUTO_INCREMENT COMMENT '主键',
    user_id       BIGINT       NOT NULL COMMENT '操作账户 id',
    username      VARCHAR(64)  NOT NULL COMMENT '操作账户名',
    action        VARCHAR(64)  NOT NULL COMMENT '具体操作',
    target_id     BIGINT       NULL COMMENT '被操作对象 id',
    chain_tx_hash VARCHAR(70)  NULL COMMENT '链上交易哈希',
    detail        VARCHAR(512) NULL COMMENT '补充说明',
    prev_hash     CHAR(64)     NULL COMMENT '前一行哈希（防删改链）',
    row_hash      CHAR(64)     NULL COMMENT '本行内容哈希（防篡改）',
    created_at    DATETIME     NOT NULL DEFAULT CURRENT_TIMESTAMP,
    KEY idx_user (user_id),
    KEY idx_action (action)
) ENGINE = InnoDB DEFAULT CHARSET = utf8mb4 COMMENT = '操作审计';