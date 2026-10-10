# 容器化部署

前端静态资源与 nginx 统一入口（/api 反代到后端），后端打包为镜像运行；
链环境（FISCO 节点、WeBASE-Sign、MySQL）不进容器，仍部署在 WSL，
容器经 host.docker.internal 访问它们（Docker Desktop 的网关可直接
到达宿主机与 WSL 的端口转发，无需额外配置）。

Prometheus 抓取后端指标、Grafana 面板展示、告警规则见 monitoring/ 目录。

## 使用

    cd deploy
    cp .env.example .env    # 填写 FOODTRACE_JWT_SECRET 与数据库口令
    docker compose up -d --build

浏览器打开 http://localhost:8080（WSL 里 WeBASE 的 nginx 已占用 80，
故默认 8080，可用 .env 的 WEB_PORT 调整）。

Grafana 面板：http://localhost:3000（默认 admin/admin，可用 .env 的
GRAFANA_PASSWORD 调整）。数据源自动配置为 Prometheus，指标来自
后端 /actuator/prometheus。

## 告警规则

monitoring/alerts.yml 定义了三组告警：

- **critical**：外部链上交易（chain_event_external）、审计链断裂
  （audit_chain_broken）、后端/节点/Sign 不可达（app_health / up）
- **warning**：角色不一致（reconcile_mismatch）、存储不可达
- **performance**：链上查询 P99 延迟超 2 秒、限流拦截量异常

Prometheus 不对外发布端口（9090 仅容器网络内可达，指标经 Grafana 展示），
需要查看告警列表时可临时加回 ports 映射。接通知渠道（邮件/钉钉/飞书）
需在 Prometheus 侧配置 Alertmanager。

## 启用 TLS

    mkdir certs
    openssl req -x509 -newkey rsa:2048 -nodes -days 365 \
      -keyout certs/server.key -out certs/server.crt -subj "/CN=localhost"

放开 nginx.conf 里注释的三行 ssl 配置，并把 compose 的端口映射改为
`443:443`（可同时保留 80），`docker compose up -d` 重建生效。
自签证书仅供演示，正式环境换机构签发证书。

## 数据库与存证备份

备份由 WSL 内的 systemd timer 每日 03:30 触发（错过自动补跑），两段独立：
MySQL 热备（`--single-transaction`，带 SHA-256 校验文件，保留 14 天）与
存证对象备份（`mc mirror` 镜像 MinIO bucket 内容，内容寻址只增不删，
**不按时间清理**——旧对象对应链上历史哈希，须永久保留）。任一段失败 systemd 单元显示失败。

备份前置：Docker Desktop 与 MinIO 容器在运行。安装：

    mkdir -p ~/opt/bin
    cp deploy/backup/backup-foodtrace.sh ~/opt/bin/ && chmod +x ~/opt/bin/backup-foodtrace.sh
    printf '[client]\nhost=127.0.0.1\nuser=root\npassword=***\n' > ~/opt/mysql-backup.cnf
    chmod 600 ~/opt/mysql-backup.cnf
    sudo cp deploy/backup/foodtrace-backup.{service,timer} /etc/systemd/system/
    sudo systemctl daemon-reload && sudo systemctl enable --now foodtrace-backup.timer

手动备份与恢复：

    ~/opt/bin/backup-foodtrace.sh
    # MySQL
    cd ~/backups/foodtrace && sha256sum -c <备份文件>.sha256
    mysql -uroot -p -e "CREATE DATABASE foodtrace DEFAULT CHARSET utf8mb4"
    zcat <备份文件>.sql.gz | mysql -uroot -p foodtrace
    # 存证（对象名即内容哈希，恢复后逐文件 sha256sum 比对文件名即完成校验）
    docker run --rm --network foodtrace-net --entrypoint sh -v <备份目录>:/backup \
      minio/mc -c 'mc alias set r http://<目标minio>:9000 <AK> <SK> && \
                   mc mb --ignore-existing r/foodtrace-evidence && \
                   mc mirror --overwrite /backup r/foodtrace-evidence'

恢复后启动后端，调用 GET /api/admin/logs/verify-chain 确认审计哈希链完整。
2026-10-10 双段演练：MySQL 删库恢复行数一致、审计链完整续接、链上校验
36/36；存证恢复至影子实例后逐文件哈希与对象名全部一致。

## 存证对象存储

MinIO 随 compose 一并启动（仅容器网络内可达，管理台 9001 未对外）。
对象按内容 SHA-256 寻址，链上 data_hash 即下载键：
`GET /api/storage/{hash}` 匿名可下载（受 IP 限流），上传需登录。
本地开发单独起一个：

    docker run -d --name foodtrace-minio -p 9000:9000 -p 9001:9001 \
      minio/minio server /data --console-address :9001

默认凭据 minioadmin/minioadmin，环境变量 FOODTRACE_STORAGE_* 覆盖
地址与凭据。

## 单实例约束

当前设计假定后端以单实例运行，以下组件依赖此前提：

- **审计哈希链接链**（`OperateLogService.record`）：`synchronized`
  仅在单 JVM 内有效，多实例并发写审计会打断链条
- **限流**（`RateLimitService`）：计数器存于内存，多实例各算各的
- **产品缓存**（`ProductCache`）：各实例独立，事件订阅的失效
  消息每个实例都会收到，缓存一致性可接受
- **事件订阅**（`ChainEventSubscriber`）：多实例各自订阅，去重
  靠审计表按交易哈希判断，天然幂等

多实例部署需改造：审计接链改用数据库行锁取链头，限流迁至
Redis，缓存换分布式缓存或消息广播失效。

## 优雅停机

后端已启用 Spring 优雅停机（`server.shutdown=graceful`，
超时 30 秒），Docker 的 `stop_grace_period` 设为 35 秒——
正在上链的交易（1-3 秒）在容器停止前可正常完成，不会腰斩。

## 加密范围

只加密对外的入口段，节点通信的加密由 FISCO 自带：

- 浏览器到 nginx：TLS，按上节方法启用
- nginx 到后端、后端到 MySQL 与 WeBASE-Sign：明文，流量不出宿主机
  （Docker 与 WSL 内部网络）
- 后端到 FISCO 节点：SDK 证书双向认证加密（backend/conf 下的
  ca.crt、sdk.crt、sdk.key，不入库，运行时挂载进容器 /app/conf），
  联盟链原有机制

多机部署时再逐段补齐：后端启用 HTTPS 并将 nginx 反代改为 https，
JDBC 连接加 useSSL，WeBASE-Sign 换其自带的 HTTPS 端口。
