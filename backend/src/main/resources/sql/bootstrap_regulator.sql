USE foodtrace;

-- 一次性引导：将已注册的 regulator 账户提升为监管账户，
-- 必须绑定实际部署 Foodtrace 合约的 WeBASE-Sign 用户，即链上 regulator。
-- 在同一会话填写以下值；占位地址不满足校验时不会修改账户。
SET @bootstrap_username = 'regulator';
SET @bootstrap_sign_user_id = 'REPLACE_WITH_SIGN_USER_ID';
SET @bootstrap_chain_address = 'REPLACE_WITH_DEPLOYER_ADDRESS';

UPDATE sys_user
SET is_regulator  = 1,
    status        = 1,
    role          = 0,
    sign_user_id  = @bootstrap_sign_user_id,
    chain_address = @bootstrap_chain_address
WHERE username = @bootstrap_username
  AND status = 0
  AND is_regulator = 0
  AND @bootstrap_chain_address REGEXP '^0x[0-9a-fA-F]{40}$'
  AND @bootstrap_sign_user_id <> 'REPLACE_WITH_SIGN_USER_ID'
  AND CHAR_LENGTH(@bootstrap_sign_user_id) > 0;

-- 首次正确引导应为 1；为 0 时检查参数和账户状态，不能据此认为引导成功。
SELECT ROW_COUNT() AS promoted_accounts;
