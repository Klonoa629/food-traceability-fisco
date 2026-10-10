#!/bin/bash
# foodtrace 库备份：mysqldump --single-transaction 热备，gzip 落盘并附 SHA-256，保留 14 天
set -euo pipefail

BACKUP_DIR="${FOODTRACE_BACKUP_DIR:-$HOME/backups/foodtrace}"
KEEP_DAYS=14
MYSQL_CNF="$HOME/opt/mysql-backup.cnf"
DB=foodtrace

mkdir -p "$BACKUP_DIR"
stamp=$(date +%Y%m%d-%H%M%S)
out="$BACKUP_DIR/foodtrace-$stamp.sql.gz"

mysqldump --defaults-extra-file="$MYSQL_CNF" --single-transaction --quick --routines "$DB" | gzip > "$out"
sha256sum "$out" > "$out.sha256"

find "$BACKUP_DIR" -name 'foodtrace-*.sql.gz' -mtime +"$KEEP_DAYS" -delete
find "$BACKUP_DIR" -name 'foodtrace-*.sql.gz.sha256' -mtime +"$KEEP_DAYS" -delete

echo "backup ok: $out ($(du -h "$out" | cut -f1))"
