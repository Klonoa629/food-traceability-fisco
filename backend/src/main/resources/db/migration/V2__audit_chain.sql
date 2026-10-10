-- 审计哈希链：加列后由应用启动时自动回填 prev_hash / row_hash
ALTER TABLE operate_log
    ADD COLUMN prev_hash CHAR(64) NULL COMMENT '前一行哈希（防删改链）' AFTER detail,
    ADD COLUMN row_hash  CHAR(64) NULL COMMENT '本行内容哈希（防篡改）' AFTER prev_hash;
