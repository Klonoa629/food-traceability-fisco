# 基于 FISCO BCOS 的食品溯源平台

农产品从基地注册上链开始，沿加工、质检、运输、仓储、销售流转，每个环节
的操作记录连同数据哈希写入 FISCO BCOS 联盟链，质检不合格或监管介入即进入
召回终态。机构私钥统一托管在 WeBASE-Sign，后端只发起哈希签名，全程不接触
私钥。

## 技术栈

- 合约：Solidity 0.8.11 + Hardhat（`blockchain/`）
- 后端：Spring Boot 3.5 / Java 17 / MyBatis-Plus / MySQL（`backend/`）
- 前端：Vue 3 / Vite / TypeScript / Element Plus（`frontend/`）

## 快速开始

环境要求：JDK 17、Node.js 18+，WSL（Ubuntu-24.04）内已部署 WeBASE 3.1.1
全套——FISCO 节点（:20200）、WeBASE-Sign（:5004）、MySQL（:3306，root/123456）。

### 1. 启动链环境

```bash
wsl.exe bash -c "cd ~/fisco/webase-deploy && python3 deploy.py startAll"
```

停止用 `stopAll`。节点、Sign、MySQL 会一起拉起。

### 2. 初始化数据库（首次）

```bash
wsl.exe bash -c "mysql -uroot -p123456 -e 'CREATE DATABASE IF NOT EXISTS foodtrace DEFAULT CHARSET utf8mb4'"
```

只需建库：表结构由后端启动时经 Flyway 自动创建与升级（脚本在
`backend/src/main/resources/db/migration/`）。监管账户不走审批流：先通过
注册接口建一个普通账户，再执行 `sql/bootstrap_regulator.sql` 把它提升为
监管并绑定链上 regulator（脚本内地址已按本机 WeBASE-Sign 实例填好）。

### 3. 启动后端

```bash
cd backend
FOODTRACE_JWT_SECRET=<至少32字节的随机串> FOODTRACE_DB_PASSWORD=123456 ./gradlew bootRun
```

两个环境变量的说明见 `backend/.env.example`。从 Windows 访问后端用
`http://localhost:8081`，不要用 127.0.0.1——后端跑在 WSL 内时端口转发
可能只绑 IPv6。

### 4. 启动前端

```bash
cd frontend
npm install
npm run dev
```

浏览器打开 `http://localhost:5173`，`/api` 已由 Vite 代理到后端，无需
处理跨域。

## 容器化部署

前后端可打包为容器一键起（nginx 统一入口，TLS 可选），链环境仍在
WSL 不进容器，见 [deploy/README.md](deploy/README.md)：

```bash
cd deploy && cp .env.example .env && docker compose up -d --build
```

## 演示账号

| 用户名 | 密码 | 身份 |
|---|---|---|
| regulator | admin123456 | 监管机构 |
| farm_a | pass123456 | 基地 |
| inspector_b | pass123456 | 质检机构 |
| transporter_c | pass123456 | 物流 |
| retailer_d | pass123456 | 零售商 |

链上已有产品「阳光草莓」（批次 B20260918-A1）走完种植到召回的完整
生命周期，可直接用于演示。

## 目录结构

```
├── blockchain      # Foodtrace 溯源合约与测试
├── backend
│   └── src/main/resources
│       ├── abi/Foodtrace.abi        # 合约 ABI
│       ├── db/migration/            # Flyway 建表与升级脚本
│       └── sql/                     # 监管账户引导脚本
└── frontend        # Vue 3 前端
```

Foodtrace 合约部署在 `0x2af6160e266f763652f433a80b94fc13f4065303`，
地址配置在 `backend/src/main/resources/application.yaml`。重新部署合约后
需要更新该地址，并由监管重新给各机构发放链上角色。

## 测试

```bash
cd blockchain && npm run compile && npm test  # 合约与锚定回归
cd backend && ./gradlew test  # 后端回归（-Pintegration 需链环境）
cd frontend && npm test && npm run lint && npm run build
```

## 写链幂等与故障恢复

所有 HTTP 写链接口需带 `Idempotency-Key` 请求头（16–80 位字母、数字、
下划线或短横线）。前端会自动生成，并在当前标签页保留未确认操作；网络中断后
保持原内容重试即可。成功或明确的业务拒绝后才释放标识。同一标识不得更换内容。

后端先持久化交易哈希和已签名交易，再广播。超时返回业务码 503，表示结果待确认；
重试先按原哈希查询，必要时重发同一份签名交易，不重新签名生成新交易。
链上明确拒绝使用业务码 422。原交易超过有效区块范围且始终无回执时，
不会自动创建替代交易，需要监管核对后再决定下一步。

`GET /api/operations` 返回当前账户最近 50 次写链状态：
`PENDING`（待确认）、`CONFIRMED`（成功）、`REJECTED`（链上拒绝），
不返回签名交易原文。后台每分钟检查待确认记录，自动重发最多 10 次，
之后继续查询回执并保留错误信息。该状态描述交易是否上链；
产品回读失败时仍应重试原请求以恢复页面结果。

审批先保存开户身份及审批角色；链上授权成功而本地未生效时，
角色对账会在确认两者一致后补齐账户状态。一般角色冲突仍只告警，不自动改链。
审计通过事件回放补录，带相同交易哈希和动作的业务审计不会重复插入。

当前实现按单后端实例部署；多实例审计接链与审批协调需要额外的数据库并发控制。

## 文件存证与质检报告

上传 `/api/storage/upload` 返回文件 SHA-256。业务请求用 `file:sha256:<64位哈希>`
引用该文件；后端在上链前确认原文存在并重算摘要。质检 `reportHash` 必须使用
这种文件引用，不能再留空或只填写摘要。普通环节没有附件时，后端生成
`sha256:<64位哈希>` 业务摘要。旧记录保留原值，页面标明“旧版摘要（未标注类型）”。

公开溯源页与机构详情页只为明确的文件存证提供下载和核验入口。
下载时后端检查原文完整性；“核验原文”还会在浏览器中重新计算 SHA-256
与链上值比对（需 HTTPS 或 localhost）。文件缺失、内容被替换都会明确提示。
单文件上限 20 MB。上传的证据可被匿名下载，请仅上传适合公开的报告。

哈希核验只能说明当前文件与链上记录一致，无法证明原始检测或填报内容真实；
真实性仍需提交机构、检测流程与线下责任制度保证。此格式升级不需要重部署合约。

浏览器回归由 CI 自动运行：构建页面后，使用固定 API 夹具检查桌面/手机展示、
文件核验成功/缺失/内容不符，以及质检上传到提交的流程，失败时保留截图。
这属于前端集成测试，不代表真实 FISCO、MySQL、MinIO 联调已经通过。
本机有 Playwright Core 时可运行 `npm run test:browser`；依赖在其他目录时设置
`PLAYWRIGHT_MODULE` 为模块绝对路径，使用已有浏览器可设置 `E2E_BROWSER_PATH`。
该脚本启动并关闭独立的 5174 端口预览服务，截图默认保存在系统临时目录。
## 产品检索

`GET /api/products` 支持 `keyword`（名称/批次号，不区分大小写）、`stage`（0–6）、
`holder`（当前持有机构链地址）和 `recalled`（true/false），条件组合取交集。
先筛选全部产品，再按 id 倒序分页；`total` 是匹配总数，搜索结果可以继续翻页。
无筛选条件时只读取当前页。筛选目前遍历产品读缓存，冷缓存可能需要多次链读取，
适合小规模演示；大规模数据应增加事件驱动的数据库查询索引，不能据此宣称高吞吐检索。