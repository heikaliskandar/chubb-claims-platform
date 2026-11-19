#!/usr/bin/env bash
# One-command setup (and update) for a fresh Ubuntu server. Safe to run again.
#
#   ./infra/deploy/server-setup.sh my-claims.duckdns.org          first install
#   ./infra/deploy/server-setup.sh my-claims.duckdns.org --backups   also schedule nightly backups
#   git pull && ./infra/deploy/server-setup.sh                    update (reuses .env)
set -euo pipefail

cd "$(dirname "$0")/../.."
ROOT="$(pwd)"
COMPOSE=(sudo docker compose -f docker-compose.yml -f docker-compose.prod.yml)
DOMAIN_ARG=""
WITH_BACKUPS=false
for arg in "$@"; do
  case "$arg" in
    --backups) WITH_BACKUPS=true ;;
    -*) echo "Unknown option $arg" >&2; exit 1 ;;
    *) DOMAIN_ARG="$arg" ;;
  esac
done

step() { printf '\n\033[1;36m==> %s\033[0m\n' "$1"; }

step "Docker"
if ! command -v docker >/dev/null 2>&1; then
  curl -fsSL https://get.docker.com | sudo sh
  sudo usermod -aG docker "$USER"
fi
sudo docker compose version

step "Firewall (ports 80 and 443)"
for port in 80 443; do
  if sudo iptables -C INPUT -p tcp --dport "$port" -j ACCEPT 2>/dev/null; then
    continue
  fi
  reject_line="$(sudo iptables -L INPUT --line-numbers -n | awk '$2 == "REJECT" {print $1; exit}')"
  sudo iptables -I INPUT "${reject_line:-1}" -p tcp --dport "$port" -j ACCEPT
done
if command -v netfilter-persistent >/dev/null 2>&1; then
  sudo netfilter-persistent save >/dev/null
fi
if command -v ufw >/dev/null 2>&1 && sudo ufw status | grep -q "Status: active"; then
  sudo ufw allow 80/tcp >/dev/null && sudo ufw allow 443/tcp >/dev/null
fi
echo "open"

step "Memory"
total_mb="$(awk '/MemTotal/ {print int($2 / 1024)}' /proc/meminfo)"
if [ "$total_mb" -lt 6000 ] && ! swapon --show | grep -q .; then
  sudo fallocate -l 4G /swapfile && sudo chmod 600 /swapfile
  sudo mkswap /swapfile >/dev/null && sudo swapon /swapfile
  echo '/swapfile none swap sw 0 0' | sudo tee -a /etc/fstab >/dev/null
  echo "${total_mb} MB RAM: added 4 GB swap"
else
  echo "${total_mb} MB RAM"
fi

step "Configuration (.env)"
if [ ! -f .env ]; then
  if [ -z "$DOMAIN_ARG" ]; then
    echo "First run: pass your domain, e.g. ./infra/deploy/server-setup.sh my-claims.duckdns.org" >&2
    exit 1
  fi
  secret() { openssl rand -hex 24; }
  umask 077
  cat > .env <<EOF
DOMAIN=$DOMAIN_ARG
POSTGRES_PASSWORD=$(secret)
CLAIMS_DB_PASSWORD=$(secret)
INSIGHTS_DB_PASSWORD=$(secret)
DEMO_USER_PASSWORD=password
DEMO_DATA_ENABLED=true
API_DOCS_ENABLED=true
EOF
  echo "created .env with random database passwords (readable only by $USER)"
elif [ -n "$DOMAIN_ARG" ]; then
  sed -i "s/^DOMAIN=.*/DOMAIN=$DOMAIN_ARG/" .env
  echo "kept existing .env, DOMAIN=$DOMAIN_ARG"
else
  echo "kept existing .env"
fi
DOMAIN="$(grep '^DOMAIN=' .env | cut -d= -f2)"

step "DNS"
public_ip="$(curl -fsS --max-time 5 https://api.ipify.org || true)"
domain_ip="$(getent ahostsv4 "$DOMAIN" | awk 'NR == 1 {print $1}')"
if [ -n "$public_ip" ] && [ "$public_ip" = "$domain_ip" ]; then
  echo "$DOMAIN -> $domain_ip (this server)"
else
  echo "WARNING: $DOMAIN resolves to '${domain_ip:-nothing}', this server is '${public_ip:-unknown}'."
  echo "         HTTPS certificates are issued only once DNS points here. Continuing anyway."
fi

step "Build and start (first run takes several minutes)"
"${COMPOSE[@]}" up -d --build --remove-orphans

step "Waiting for the app"
for _ in $(seq 1 60); do
  if curl -fsS -o /dev/null --max-time 5 -H "Host: $DOMAIN" http://localhost/ 2>/dev/null \
      || curl -fsS -o /dev/null --max-time 5 "https://$DOMAIN/" 2>/dev/null; then
    break
  fi
  sleep 5
done
"${COMPOSE[@]}" ps --format 'table {{.Service}}\t{{.Status}}'

if [ "$WITH_BACKUPS" = true ]; then
  step "Nightly backups"
  chmod +x infra/backup/pg-backup.sh
  line="0 2 * * * cd $ROOT && ./infra/backup/pg-backup.sh >> \$HOME/backup.log 2>&1"
  ( crontab -l 2>/dev/null | grep -v 'infra/backup/pg-backup.sh' ; echo "$line" ) | crontab -
  echo "scheduled at 02:00; dumps and documents go to ~/backups (kept 14 days)"
fi

printf '\n\033[1;32mDone.\033[0m  App: https://%s   API docs: https://%s/api/docs\n' "$DOMAIN" "$DOMAIN"
printf 'Logs: sudo docker compose -f docker-compose.yml -f docker-compose.prod.yml logs -f claims-service\n'
