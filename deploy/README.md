# IntellRecipe Cloud Deployment

This directory contains a first-stage deployment flow for one Linux cloud server.

## Server Ports

Open these ports in the cloud security group while debugging:

- `22`: SSH
- `80`: Nginx HTTP entry
- `10010`: Gateway debugging, close after Nginx works
- `8848`: Nacos console debugging, close after deployment
- `15672`: RabbitMQ console debugging, close after deployment

For a safer public setup, keep only `22`, `80`, and later `443` open. Restrict `22` to your own IP if your cloud provider supports it.

## First Deployment

Run commands from the project root on the cloud server.

```bash
bash deploy/01-install-prereqs.sh
cp deploy/docker.env.example docker/.env
cp deploy/env.example deploy/env.sh
```

Edit `docker/.env` and `deploy/env.sh` before production use. Keep real passwords and SMS keys out of Git.

Start infrastructure:

```bash
bash deploy/02-start-infra.sh
```

On **4GB RAM** servers, Elasticsearch is **not** started by default (it saves memory). Item search features that need ES will not work until you upgrade RAM or start ES:

```bash
START_ELASTICSEARCH=1 bash deploy/02-start-infra.sh
```

With ES off, `item-service` still starts: set `ITEM_ELASTICSEARCH_ENABLED=false` in `deploy/env.sh` (see `deploy/env.example`). That excludes ES auto-config and sets `spring.data.elasticsearch.repositories.enabled=false`; keyword search uses MySQL.

Build Spring Boot jars:

```bash
bash deploy/03-build-services.sh
```

Start application services:

```bash
bash deploy/04-start-services.sh
```

Verify the deployment:

```bash
bash deploy/06-verify.sh
```

### After a server reboot (or OOM recovery)

From the project root, one script runs the full sequence: start Docker, fix legacy Compose `version` if missing, start middleware **without** Elasticsearch, align the RabbitMQ user with `deploy/env.sh`, clear stale Spring Boot pid files, start jars, then verify:

```bash
bash deploy/07-post-reboot.sh
```

If Docker needs root on your machine, run `sudo systemctl start docker` once, then re-run the script. Keep `ITEM_ELASTICSEARCH_ENABLED=false` and modest `JAVA_OPTS` on 4GB RAM (see `deploy/env.example`).

**Do not type that every reboot.** Either:

- **Automatic (recommended):** one-time on the server, after `deploy/07-post-reboot.sh` exists in the tree:

  ```bash
  sudo bash deploy/08-install-autostart.sh
  ```

  Then reboots run the same flow via systemd (`intellrecipe-after-boot.service`). Logs: `sudo journalctl -u intellrecipe-after-boot.service -b --no-pager`. Remove: `sudo bash deploy/08-install-autostart.sh uninstall`.

- **Manual but one line:** if you skip systemd, you only ever run `bash deploy/07-post-reboot.sh` from the project root—no long command block.

Stop application services:

```bash
bash deploy/05-stop-services.sh
```

Stop middleware if needed:

```bash
cd docker
docker compose down
```

## Runtime Layout

- Middleware runs from `docker/docker-compose.yml`.
- Nginx serves files from `docker/nginx/html` and proxies `/api/` to Gateway on `10010`.
- Spring Boot service logs are written to `logs/`.
- Service pids are tracked under `deploy/pids/`.

## Configuration

The application YAML files now read runtime dependencies from environment variables:

- `MYSQL_HOST`, `MYSQL_PORT`, `MYSQL_DATABASE`, `MYSQL_USERNAME`, `MYSQL_PASSWORD`
- `REDIS_HOST`, `REDIS_PORT`, `REDIS_PASSWORD`, `REDIS_DATABASE`
- `NACOS_SERVER_ADDR`
- `RABBITMQ_HOST`, `RABBITMQ_PORT`, `RABBITMQ_USERNAME`, `RABBITMQ_PASSWORD`, `RABBITMQ_VIRTUAL_HOST`
- `ELASTICSEARCH_URIS`
- `ALIYUN_SMS_ACCESS_KEY_ID`, `ALIYUN_SMS_ACCESS_KEY_SECRET`, `ALIYUN_SMS_SIGN_NAME`, `ALIYUN_SMS_TEMPLATE_CODE`

When the Spring Boot jars run directly on the host, use `127.0.0.1` and the exposed Docker ports. If you later containerize the services into the same Docker Compose network, switch these hosts to service names such as `mysql`, `redis`, `nacos`, `rabbitmq`, and `elasticsearch`.

## Troubleshooting

### `voucher-service`: RabbitMQ `Authentication failure` / `ACCESS_REFUSED`

`deploy/env.sh` must use the **same** username and password as `docker/.env` (`RABBITMQ_DEFAULT_USER` / `RABBITMQ_DEFAULT_PASS`). RabbitMQ only applies default users on **first** data directory init; if you changed `.env` later, old credentials may still be in `docker/rabbitmq/data`.

To reset the broker user (destroys RabbitMQ queues and definitions):

```bash
cd ~/IntellRecipe/docker
docker-compose stop rabbitmq
sudo rm -rf ./rabbitmq/data/*
docker-compose up -d rabbitmq
```

Then restart `voucher-service`.

### `item-service`: `elasticsearchTemplate` could not be found

Ensure `ITEM_ELASTICSEARCH_ENABLED=false` is exported in `deploy/env.sh` and you are running a build that includes `spring.data.elasticsearch.repositories.enabled` in `item-service` `application.yml` (see latest `main`). Rebuild with `bash deploy/03-build-services.sh` and restart.
