#!/bin/bash
# foodtrace 备份：MySQL 热备（mysqldump，校验和+保留 14 天）
#               + 存证对象备份（mc mirror，内容寻址只增不删，不按时间清理）
# 任一段失败以非零退出，systemd 单元显示失败
set -uo pipefail

BACKUP_DIR="${FOODTRACE_BACKUP_DIR:-$HOME/backups/foodtrace}"
EVIDENCE_DIR="$HOME/backups/foodtrace-evidence"
KEEP_DAYS=14
MYSQL_CNF="$HOME/opt/mysql-backup.cnf"
DB=foodtrace
EVIDENCE_ENDPOINT="${FOODTRACE_STORAGE_ENDPOINT:-http://foodtrace-minio:9000}"
EVIDENCE_USER="${FOODTRACE_ACCESS_KEY:-minioadmin}"
EVIDENCE_PASS="${FOODTRACE_SECRET_KEY:-minioadmin}"
EVIDENCE_BUCKET="${FOODTRACE_BUCKET:-foodtrace-evidence}"

mkdir -p "$BACKUP_DIR" "$EVIDENCE_DIR"
rc=0

# ---- MySQL ----
stamp=$(date +%Y%m%d-%H%M%S)
out="$BACKUP_DIR/foodtrace-$stamp.sql.gz"
if mysqldump --defaults-extra-file="$MYSQL_CNF" --single-transaction --quick --routines "$DB" | gzip > "$out"; then
  sha256sum "$out" > "$out.sha256"
  echo "mysql backup ok: $out ($(du -h "$out" | cut -f1))"
else
  echo "mysql backup FAILED" >&2
  rc=1
fi
find "$BACKUP_DIR" -name 'foodtrace-*.sql.gz' -mtime +"$KEEP_DAYS" -delete
find "$BACKUP_DIR" -name 'foodtrace-*.sql.gz.sha256' -mtime +"$KEEP_DAYS" -delete

# ---- 存证对象（MinIO bucket 内容） ----
# 备份目录的 Windows 视路径（docker.exe 卷挂载用），如 \\wsl.localhost\Ubuntu-24.04\home\...
DISTRO="${WSL_DISTRO_NAME:-Ubuntu-24.04}"
WIN_HOME="\\\\wsl.localhost\\$DISTRO${HOME//\//\\}"

docker.exe network create foodtrace-net >/dev/null 2>&1 || true
docker.exe network connect foodtrace-net foodtrace-minio >/dev/null 2>&1 || true
if docker.exe run --rm --network foodtrace-net --entrypoint sh \
  -v "$WIN_HOME\\backups\\foodtrace-evidence:/backup" \
  minio/mc:latest -c "
    mc alias set b $EVIDENCE_ENDPOINT $EVIDENCE_USER $EVIDENCE_PASS >/dev/null &&
    mc mirror --overwrite b/$EVIDENCE_BUCKET /backup &&
    echo \"evidence backup ok: \$(mc ls --recursive b/$EVIDENCE_BUCKET | wc -l) objects\"
  "; then
  :
else
  echo "evidence backup FAILED" >&2
  rc=1
fi

exit $rc
