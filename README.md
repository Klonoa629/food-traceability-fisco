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
wsl.exe bash -c "mysql -uroot -p123456 foodtrace < /mnt/e/Study/Project/food-traceability-fisco/backend/src/main/resources/sql/init.sql"
```

监管账户不走审批流：先通过注册接口建一个普通账户，再执行
`bootstrap_regulator.sql` 把它提升为监管并绑定链上 regulator（脚本内地址
已按本机 WeBASE-Sign 实例填好）。

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
│       └── sql/                     # 建表与监管账户引导脚本
└── frontend        # Vue 3 前端
```

Foodtrace 合约部署在 `0x2af6160e266f763652f433a80b94fc13f4065303`，
地址配置在 `backend/src/main/resources/application.yaml`。重新部署合约后
需要更新该地址，并由监管重新给各机构发放链上角色。

## 测试

```bash
cd blockchain && npm test     # 合约 47 个用例
cd backend && ./gradlew test  # 后端 19 个用例（-Pintegration 需链环境）
cd frontend && npm run build  # 类型检查 + 构建
```

接口字段与前端对接约定见 [FRONTEND_BRIEF.md](FRONTEND_BRIEF.md)。
