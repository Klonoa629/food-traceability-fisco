USE foodtrace;

-- 一次性引导：将已注册的 regulator 账户提升为监管账户，
-- 链上身份绑定部署 Foodtrace 合约的 WeBASE-Sign 用户 regulator_001（即链上 regulator）。

UPDATE sys_user
SET is_regulator  = 1,
    status        = 1,
    role          = 0,
    sign_user_id  = 'regulator_001',
    chain_address = '0x4fd7874046652c942ee686b695ad77e8a5f0cccd'
WHERE username = 'regulator';
