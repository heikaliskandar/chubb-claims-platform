#!/usr/bin/env bash
# Nightly backup of both databases and the uploaded claim documents. Run from the project root, e.g. via cron:
#   0 2 * * * cd /opt/chubb-claims-platform && ./infra/backup/pg-backup.sh >> $HOME/backup.log 2>&1
set -euo pipefail

BACKUP_DIR="${BACKUP_DIR:-$HOME/backups}"
KEEP_DAYS="${KEEP_DAYS:-14}"
STAMP="$(date +%Y%m%d-%H%M%S)"
COMPOSE="docker compose -f docker-compose.yml -f docker-compose.prod.yml"

mkdir -p "$BACKUP_DIR"

for db in claims insights; do
  $COMPOSE exec -T postgres pg_dump -U postgres -Fc "$db" > "$BACKUP_DIR/$db-$STAMP.dump"
done

$COMPOSE exec -T claims-service tar -C /data -czf - documents > "$BACKUP_DIR/documents-$STAMP.tar.gz"

find "$BACKUP_DIR" \( -name '*.dump' -o -name 'documents-*.tar.gz' \) -mtime +"$KEEP_DAYS" -delete
echo "$(date -Is) backup ok: $BACKUP_DIR/*-$STAMP.*"
