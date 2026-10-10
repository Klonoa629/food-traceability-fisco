USE foodtrace;

-- 改密后旧令牌失效：版本号递增，JWT 携带签发时版本，过滤器比对
ALTER TABLE sys_user
    ADD COLUMN pwd_version INT NOT NULL DEFAULT 0 AFTER password_hash;
