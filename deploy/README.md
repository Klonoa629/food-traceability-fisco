# 容器化部署

前端静态资源与 nginx 统一入口（/api 反代到后端），后端打包为镜像运行；
链环境（FISCO 节点、WeBASE-Sign、MySQL）不进容器，仍部署在 WSL，
容器经 host.docker.internal 访问它们（Docker Desktop 的网关可直接
到达宿主机与 WSL 的端口转发，无需额外配置）。

## 使用

    cd deploy
    cp .env.example .env    # 填写 FOODTRACE_JWT_SECRET 与数据库口令
    docker compose up -d --build

浏览器打开 http://localhost:8080（WSL 里 WeBASE 的 nginx 已占用 80，
故默认 8080，可用 .env 的 WEB_PORT 调整）。

## 启用 TLS

    mkdir certs
    openssl req -x509 -newkey rsa:2048 -nodes -days 365 \
      -keyout certs/server.key -out certs/server.crt -subj "/CN=localhost"

放开 nginx.conf 里注释的三行 ssl 配置，并把 compose 的端口映射改为
`443:443`（可同时保留 80），`docker compose up -d` 重建生效。
自签证书仅供演示，正式环境换机构签发证书。

## 加密范围

只加密对外的入口段，节点通信的加密由 FISCO 自带：

- 浏览器到 nginx：TLS，按上节方法启用
- nginx 到后端、后端到 MySQL 与 WeBASE-Sign：明文，流量不出宿主机
  （Docker 与 WSL 内部网络）
- 后端到 FISCO 节点：SDK 证书双向认证加密（backend/conf 下的
  ca.crt、sdk.crt、sdk.key），联盟链原有机制

多机部署时再逐段补齐：后端启用 HTTPS 并将 nginx 反代改为 https，
JDBC 连接加 useSSL，WeBASE-Sign 换其自带的 HTTPS 端口。
