# 基于 FISCO BCOS 的食品溯源平台

农产品从基地注册上链，沿加工、质检、运输、仓储、销售流转，质检不合格或
监管介入进入召回终态。机构签名密钥托管在 WeBASE-Sign；链上保存责任方、
流转记录与内容摘要，文件原文保存在 MinIO。

## 技术栈

- 合约：Solidity 0.8.11、Hardhat 3、TypeScript / node:test
- 后端：Spring Boot 3.5、Java 17、MyBatis-Plus、MySQL、Flyway
- 联盟链：FISCO BCOS v3、WeBASE-Sign
- 前端：Vue 3、Vite、TypeScript、Element Plus
- 部署与观测：Docker Compose、nginx、MinIO、Prometheus、Grafana

## 快速开始

需要 JDK 17、Node.js 22.13+，以及已经准备好的 FISCO / WeBASE-Sign / MySQL / MinIO。
本仓库不自动部署联盟链，也不自带可跨环境通用的合约地址、监管身份或演示密码。
完整初始化顺序见 [从空业务库到演示](docs/demo.md)。

1. 部署合约，准备 SDK 证书，设置实际合约地址和数据库、存储、JWT 环境变量。
   参考 [后端变量示例](backend/.env.example) 与 [容器变量示例](deploy/.env.example)。
2. 创建空的 `foodtrace` 库，由后端启动时执行 Flyway 迁移。
   已有旧库的接管见 [部署说明](deploy/README.md)，不能直接假定其结构与 V1 相同。
3. 启动后端（以下在 WSL / Linux，环境变量在当前终端设置）：

   ```bash
   cd backend
   ./gradlew bootRun
   ```

4. 前端首次由维护者安装依赖后启动：

   ```bash
   cd frontend
   npm ci
   npm run dev
   ```

开发入口为 `http://localhost:5173`，Vite 将 `/api` 代理至后端 8081。
容器入口默认 `http://localhost:8080`，打包、TLS 与备份步骤见 [部署说明](deploy/README.md)。
首次监管账户需要绑定真实合约部署者；后续机构通过监管审批开户授权。

## 演示与设计说明

- [五分钟演示与数据初始化](docs/demo.md)：六类机构、正常销售及两种召回场景。
- [架构与一致性边界](docs/architecture.md)：数据权威来源、写链恢复与状态图。
- [读取性能测量](docs/performance.md)：测量脚本、统计口径与结果记录要求。

```powershell
# 默认只打印计划；实际写入演示数据需按文档配置凭据并显式加 --apply
python -X utf8 -B scripts/demo.py --run-id showcase01
```

## 目录结构

```text
├── blockchain      # Foodtrace / AuditAnchor 合约与测试
├── backend         # API、链适配、迁移、审计与恢复任务
├── frontend        # 机构工作台与免登录溯源查询
├── deploy          # 容器、nginx、监控与备份
├── scripts         # 演示数据、读取测量及离线回归
└── docs            # 架构、演示、性能说明
```

## 测试

在对应目录分别执行：

```bash
# blockchain
npm run compile && npm test
# backend
./gradlew test
# frontend
npm test && npm run lint && npm run build
# 仓库根目录
python -B -m unittest discover -s scripts -p 'test_*.py'
```

CI 包含合约、后端、脚本、前端检查、浏览器回归及镜像构建。
单元测试和带 API 夹具的浏览器测试无需真实链；它们不能替代实际 FISCO、
MySQL、MinIO 的部署联调。真实联调验收清单见演示文档。

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