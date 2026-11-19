# Deploying to a free server

The whole stack runs on one Oracle Cloud "Always Free" ARM VM (4 OCPU, 24 GB RAM) with Docker Compose. Only Caddy (ports 80/443) is public; Postgres, Kafka and the services stay on the internal Docker network.

## 1. Create the VM

1. Sign up at cloud.oracle.com (card is for verification only).
2. Compute → Instances → Create instance:
   - Image: Ubuntu 24.04
   - Shape: `VM.Standard.A1.Flex`, 4 OCPU, 24 GB RAM
   - Save the SSH private key.
   - "Out of capacity": try another availability domain or region, or retry later.
3. Networking → the VCN's default security list → add ingress rules for TCP **80** and **443** from `0.0.0.0/0`.

## 2. Domain

Create a free subdomain at duckdns.org (e.g. `chubb-claims.duckdns.org`) and set it to the VM's public IP.

## 3. Prepare the server

```bash
ssh -i <key> ubuntu@<public-ip>

curl -fsSL https://get.docker.com | sudo sh
sudo usermod -aG docker $USER && newgrp docker

# Ubuntu images on OCI also block ports in iptables
sudo iptables -I INPUT 6 -p tcp --dport 80 -j ACCEPT
sudo iptables -I INPUT 6 -p tcp --dport 443 -j ACCEPT
sudo netfilter-persistent save
```

## 4. Deploy

```bash
sudo mkdir -p /opt && sudo chown $USER /opt
git clone <repo-url> /opt/chubb-claims-platform
cd /opt/chubb-claims-platform

cp .env.example .env
nano .env        # set DOMAIN and strong passwords (e.g. openssl rand -base64 24)

docker compose -f docker-compose.yml -f docker-compose.prod.yml up -d --build
```

The first build takes several minutes (Maven and npm). Caddy gets a Let's Encrypt certificate automatically once DNS resolves. Then open `https://<DOMAIN>`.

Check status: `docker compose -f docker-compose.yml -f docker-compose.prod.yml ps`

## 5. Backups

```bash
chmod +x infra/backup/pg-backup.sh
crontab -e
# 0 2 * * * cd /opt/chubb-claims-platform && ./infra/backup/pg-backup.sh >> $HOME/pg-backup.log 2>&1
```

Dumps go to `~/backups`, kept 14 days. Copy them off the VM periodically. Restore one with:

```bash
docker compose -f docker-compose.yml -f docker-compose.prod.yml exec -T postgres \
  pg_restore -U postgres -d claims --clean < ~/backups/claims-<stamp>.dump
```

## Updating

```bash
git pull
docker compose -f docker-compose.yml -f docker-compose.prod.yml up -d --build
```

Never run `down -v` on the server: it deletes the database volume.

## Kafka UI

Not started in production. To inspect topics temporarily, add `--profile debug` and use an SSH tunnel; it is not exposed publicly.
