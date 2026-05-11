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

With ES off, `item-service` still starts: set `ITEM_ELASTICSEARCH_ENABLED=false` in `deploy/env.sh` (see `deploy/env.example`). That excludes ES auto-config; keyword search uses MySQL.

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
