# 从空业务库到五分钟演示

## 1. 准备基础环境

本仓库不自动安装或重置 FISCO、WeBASE、MySQL。先准备 JDK 17、Node.js 22.13+、
Python 3.12，以及可用的 FISCO BCOS v3 群组、WeBASE-Sign、MySQL 与 MinIO。
安装和启动步骤由环境维护者执行。容器入口见 [部署说明](../deploy/README.md)。

1. 使用实际监管签名账户部署 `blockchain/contracts/Foodtrace.sol`。
   如需审计锚定，用同一监管账户部署 `AuditAnchor.sol`。Hardhat 本地测试地址不能直接用于 FISCO。
2. 记录群组、节点地址、SDK 证书目录、监管 Sign 用户标识与链地址、两个合约地址。
   后端 `conf/` 中使用实际 SDK 证书；不要把证书与私钥提交到仓库。
3. 创建空的 `foodtrace` 数据库。配置 `FOODTRACE_DB_PASSWORD`、`FOODTRACE_JWT_SECRET`
   （至少 32 字节随机值）、`FOODTRACE_CONTRACT_ADDRESS`、MinIO 连接和凭据。
   群组与证书目录见 `application.yaml`；实际地址必须覆盖仓库保留的本机默认值。
4. 有锚定合约时设置 `FOODTRACE_ANCHOR_ADDRESS`。空业务库直接由 Flyway 执行 V1–V5；
   已有旧库须按部署文档确认结构后接管，不能盲目启用 baseline。
5. 启动后端和前端或 Compose。确认健康探针可达，登录页正常显示；
   MinIO 必须可用，否则新的质检报告不能提交。

## 2. 首次监管引导

先通过注册页创建名为 `regulator` 的普通待审批账户，密码自行设置。
在 [引导 SQL](../backend/src/main/resources/sql/bootstrap_regulator.sql) 中填写实际的
Sign 用户标识与部署者链地址，由维护者在数据库中执行一次。
`promoted_accounts` 应为 1；为 0 时检查参数和账户状态，不要认为引导已成功。

这里是系统的首次信任引导，SQL 不会创建链上监管权。填入的地址必须已经是合约
`regulator()` 返回的地址，且 Sign 用户确实对应这把签名密钥。
后续普通机构走“注册 → 监管审批”流程，不再用 SQL 分配角色。

## 3. 生成独立演示数据

在仓库根目录使用 PowerShell 7。先查看计划，默认不连接服务：

```powershell
python -X utf8 -B scripts/demo.py --run-id showcase01
```

确认目标为演示环境后，在当前终端设置凭据并显式执行：

```powershell
$env:DEMO_REGULATOR_USERNAME = 'regulator'
$env:DEMO_REGULATOR_PASSWORD = Read-Host '监管密码' -MaskInput
$env:DEMO_PASSWORD = Read-Host '演示机构密码（8–64位，含字母数字）' -MaskInput
python -X utf8 -B scripts/demo.py --base-url http://localhost:8080 --run-id showcase01 --apply
```

开发模式直连后端时将端口改为 8081。脚本仅通过正式 API 操作，创建以下数据：

| 内容 | 标识 | 结果 |
|---|---|---|
| 六类机构 | `demo_showcase01_1` 至 `_6` | 依次为基地、加工、质检、物流、仓库、零售 |
| 正常批次 | `DEMO-showcase01-SALE` | 完整流转到销售 |
| 不合格批次 | `DEMO-showcase01-FAILED` | 基地送检，判不合格并召回 |
| 监管召回批次 | `DEMO-showcase01-RECALL` | 基地注册后由监管召回 |

报告内容明确标为模拟数据，不能用作真实检测证明。脚本不清空库、不删除账号、不重部署合约。
断线或 503 后保持同一 `run-id`、密码和脚本版本重跑；每一步使用固定请求标识。
如果账号被吊销、角色被人工更改，或已签名交易过期而无回执，先人工核对，不能换标识盲目补写。
连续多次运行可能触发每 IP 每分钟 20 次认证请求限制；遇到 429 等窗口恢复再用原参数重试。

## 4. 五分钟讲解顺序

| 时间 | 操作 | 要说明的机制 |
|---|---|---|
| 0:00–1:00 | 机构登录，跨页搜索 SALE 批次 | 后端全量筛选后分页；当前责任方和角色控制操作 |
| 1:00–2:00 | 展开完整时间线与质检报告 | 链上存摘要，原文在 MinIO；业务摘要与文件存证区分 |
| 2:00–3:00 | 无痕窗口打开 `/trace`，查询同一批次并核验原文 | 无需登录，可独立计算摘要；哈希一致不保证内容真实 |
| 3:00–4:00 | 查看 FAILED 与 RECALL 批次 | 两条召回路径，召回为终态，禁止后续写入 |
| 4:00–5:00 | 监管台查看审计与校验，说明交易恢复机制 | MySQL 哈希链、历史锚、事件回放、原交易重试 |

真实操作演示可换一个 `run-id` 建立新数据。破坏文件、改数据库、停节点等故障注入，
只在另外准备的可恢复测试环境进行；本轮没有在现有环境执行这些操作。

## 5. 验收记录

记录 Git commit、合约地址、群组、数据量、运行时间、浏览器，以及脚本输出的三个产品 id。
核对六个账户角色正确、销售与两个召回终态正确、公开下载摘要匹配、重跑不重复建产品。
如果启用了审计锚定，等到至少一次成功锚定后再演示前缀校验。
链真实联调未执行时写“未验证”，不能用脚本离线测试或浏览器夹具测试替代它。
