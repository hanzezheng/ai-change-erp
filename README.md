# AI 农批经营助手

农批经营 SaaS + 移动客户端 + ERPNext 业务后端。  
产品规则见 [`AGENTS.md`](AGENTS.md)，阶段计划见 [`docs/09_DEVELOPMENT_PLAN.md`](docs/09_DEVELOPMENT_PLAN.md)。

**当前进度（2026-08）**

| 模块 | 状态 | 说明 |
|------|------|------|
| 后端 Phase 1–3 | 已合并 `main` | ERP 读 + SaaS + 订单/收款写 |
| Flutter Phase 4 | [Draft PR #4](https://github.com/hanzezheng/ai-change-erp/pull/4) | 无 AI 手工经营闭环 |
| AI / ASR | 未开始 | Phase 5，Phase 4 合并后再做 |

---

## Windows（WSL2 + Docker Desktop）

在 **WSL2 终端**里操作（不要用 PowerShell 直接跑 `.sh`）。

### 一次性准备

1. 安装 [Docker Desktop](https://www.docker.com/products/docker-desktop/)，Settings → Resources → **WSL Integration** 打开你的发行版。
2. 安装 WSL 内的 Java 21、Maven（或用仓库 `./mvnw`）、Flutter（跑 App 时需要）。
3. 把本仓库 clone 到 WSL 文件系统（例如 `~/projects/ai-change-erp`），不要放在 `/mnt/c/` 下跑 Docker 会慢。

### 启动 ERPNext

```bash
git clone https://github.com/frappe/frappe_docker.git ~/frappe_docker
cd ~/frappe_docker
# 推荐改 pwd.yml：frontend ports 为 "8000:8080"
docker compose -f pwd.yml up -d
# 等待数分钟直到 site 创建完成
curl -s -o /dev/null -w '%{http_code}\n' http://localhost:8000/
```

若未改端口、ERP 在 **8080**，后面 Spring 用 **18082**，env 里 `ERP_BASE_URL=http://localhost:8080`。

### 一键引导（推荐）

在仓库根目录：

```bash
bash scripts/wsl-dev.sh
```

脚本会：ERP 种子数据 → 写 API Key 到 `~/nongpi-local.env` → 起 Spring → 同步 ERP 连接 → 跑 API 黄金路径 18 步。

首次若缺少 `~/nongpi-local.env`，会生成模板，**改好 JWT/密码后重新执行**。

### Flutter（Windows 侧 Android Emulator）

Emulator 在 Windows 跑、Spring 在 WSL 跑时，`10.0.2.2` 指向 Windows 宿主机，**不一定能直达 WSL 里的 Spring**。任选其一：

| 方式 | 做法 |
|------|------|
| 全在 WSL | Android SDK + Emulator 也装在 WSL2（需 KVM，Windows 11 部分机型支持） |
| 端口转发 | WSL 里 `hostname -I` 取 WSL IP，Flutter 用 `http://<WSL_IP>:18082` |
| 最简单 | Spring 也监听 `0.0.0.0`，Windows 防火墙放行端口，真机/模拟器用 Windows 局域网 IP |

```bash
cd mobile
flutter run -d emulator-5554 \
  --dart-define=API_BASE_URL=http://10.0.2.2:18082
```

---

## 1. 仓库结构

```text
backend/     Spring Boot 3 · Java 21 · 业务 API / ERPNext Adapter
mobile/      Flutter 客户端（只调 Spring Boot，不直连 ERPNext）
docs/        产品与架构文档
.github/     CI（backend-test、mobile-test）
```

---

## 2. 前置依赖

| 工具 | 版本 | 用途 |
|------|------|------|
| Java | 21 | 后端 |
| Maven | 随 `./mvnw` | 后端构建 |
| Docker | 最新稳定版 | PostgreSQL、ERPNext、Testcontainers |
| Flutter | 3.x stable | 移动端 |
| Python | 3.10+ | API 黄金路径脚本（可选） |

---

## 3. 端口约定（先看这张表）

**标准本地拓扑**（推荐，Spring 与 ERP 不抢端口）：

| 服务 | 地址 | 说明 |
|------|------|------|
| Spring Boot API | `http://localhost:8080` | Flutter / curl 都连这里 |
| ERPNext 网页/API | `http://localhost:8000` | 官方 `frappe_docker` 把容器 8080 映射到宿主机 **8000** |
| PostgreSQL | `localhost:5432` | 库名 `nongpi` |

**若 ERPNext 已占 8080**（常见于默认 `pwd.yml` 的 `"8080:8080"`）：

| 服务 | 地址 |
|------|------|
| ERPNext | `http://localhost:8080` |
| Spring Boot | 改到其它端口，例如 `http://localhost:18082` |
| Flutter 模拟器 | `--dart-define=API_BASE_URL=http://10.0.2.2:18082` |

> 规则：**Flutter 的 `API_BASE_URL` 必须和 Spring 端口一致**；**Spring 的 `ERP_BASE_URL` 必须和 ERPNext 端口一致**。  
> 不要混用 8080 / 8000 / 18082 而不改配置。

---

## 4. 本地运行（完整栈）

按顺序执行。凭据**不要提交 Git**；用本机 env 文件即可。

### 4.1 PostgreSQL

```bash
docker run -d --name nongpi-pg \
  -e POSTGRES_DB=nongpi \
  -e POSTGRES_USER=nongpi \
  -e POSTGRES_PASSWORD=nongpi \
  -p 5432:5432 \
  postgres:16
```

检查：

```bash
docker exec nongpi-pg pg_isready -U nongpi
```

### 4.2 ERPNext v16

使用官方 [frappe_docker](https://github.com/frappe/frappe_docker)（已验证 v16.32.3）：

```bash
git clone https://github.com/frappe/frappe_docker.git
cd frappe_docker
```

**推荐**：编辑 `pwd.yml`，把 frontend 端口改成 `"8000:8080"`，然后：

```bash
docker compose -f pwd.yml up -d
# 首次需等待 create-site 完成（数分钟）
curl -s -o /dev/null -w '%{http_code}\n' http://localhost:8000/
```

**不用手工点 Setup Wizard。** 回到本仓库，一条命令灌入开发数据：

```bash
chmod +x scripts/erpnext/init-dev.sh
ERP_CONTAINER=erpnext-backend-1 ./scripts/erpnext/init-dev.sh
```

脚本会自动：

1. 跑 ERPNext 官方 Setup Wizard（公司「农批测试档口」、仓库、价目表）——仅首次
2. 写入黄金路径测试数据：`韩兆亮`、`APPLE-80`、`BANANA-FEN`、价格、库存
3. 生成 **API Key / Secret**（终端打印 `APIKEY=` / `APISECRET=`，复制到 env 文件）

可重复执行：已有数据会跳过，不会重复建 Company。

若暂时不改端口、ERP 在 **8080**，则 `ERP_BASE_URL=http://localhost:8080`，且 Spring 需换端口启动（见 4.3）。

### 4.3 Spring Boot

在仓库根目录创建本机 env 文件（路径自定，例如 `~/nongpi-local.env`）：

```bash
# 必填 — 签名与加密（各至少 32 字符，自行生成随机串）
export APP_JWT_SECRET='你的-jwt-密钥-至少32字符'
export APP_CREDENTIAL_ENCRYPTION_KEY='你的-凭据加密主密钥'

# 数据库（与 4.1 一致）
export DATABASE_URL=jdbc:postgresql://localhost:5432/nongpi
export DATABASE_USERNAME=nongpi
export DATABASE_PASSWORD=nongpi

# local 引导：首次启动自动建租户 + 管理员 + ERP 连接（仅尚无数据时）
export APP_BOOTSTRAP_LOGIN=boss
export APP_BOOTSTRAP_PASSWORD='你的登录密码'
export APP_BOOTSTRAP_TENANT_NAME=农批测试档口

# ERPNext（端口按你实际映射填写）
export ERP_BASE_URL=http://localhost:8000
export ERP_SITE_NAME=frontend
export ERP_API_KEY='你的-api-key'
export ERP_API_SECRET='你的-api-secret'
export ERP_DEFAULT_COMPANY=农批测试档口
export ERP_SELLING_PRICE_LIST=Standard Selling
export ERP_DEFAULT_WAREHOUSE='Stores - NPT'
```

启动（**标准拓扑**，Spring 在 8080）：

```bash
cd backend
source ~/nongpi-local.env
./mvnw -DskipTests package
java -jar target/nongpi-backend-0.1.0-SNAPSHOT.jar --spring.profiles.active=local
```

**ERP 已占 8080 时**，Spring 换端口：

```bash
java -jar target/nongpi-backend-0.1.0-SNAPSHOT.jar \
  --spring.profiles.active=local \
  --server.port=18082
```

检查：

```bash
curl -s http://127.0.0.1:8080/actuator/health    # 或 :18082
# 应返回 {"status":"UP"} 或类似 JSON
```

登录（把端口、账号换成你的）：

```bash
curl -s -X POST http://127.0.0.1:8080/api/v1/auth/login \
  -H 'Content-Type: application/json' \
  -d '{"login":"boss","password":"你的登录密码"}'
```

**常见错误**

| 现象 | 处理 |
|------|------|
| `ERP_WRITE_CONFIGURATION_INCOMPLETE` | 已有 `erp_connection` 但缺公司/仓库：用 OWNER 调 `PUT /api/v1/erp-connection` 补全（见 [`backend/README.md`](backend/README.md)） |
| 同时起了多个 Spring | 只保留一个实例，避免连错端口 |
| `APP_BOOTSTRAP_*` 未设置 | 不会自动建账号，需手动插库或补环境变量后重启 |

### 4.4 Flutter 客户端

```bash
cd mobile
flutter pub get

# Android 模拟器（Spring 在宿主机 8080）
flutter run -d emulator-5554 \
  --dart-define=API_BASE_URL=http://10.0.2.2:8080

# Spring 若在 18082
flutter run -d emulator-5554 \
  --dart-define=API_BASE_URL=http://10.0.2.2:18082

# 真机（同一 Wi‑Fi，换成电脑局域网 IP）
flutter run --dart-define=API_BASE_URL=http://192.168.1.8:8080
```

`API_BASE_URL` **只填 Spring 源站**，不要加 `/api/v1`。

手工验收路径见 [`mobile/README.md`](mobile/README.md)「真实黄金路径」。

---

## 5. 一键检查环境是否就绪

```bash
# PostgreSQL
docker exec nongpi-pg pg_isready -U nongpi

# ERPNext（按你的端口改 8000 或 8080）
curl -s -o /dev/null -w 'ERP %{http_code}\n' http://127.0.0.1:8000/

# Spring（按你的端口改）
curl -s http://127.0.0.1:8080/actuator/health

# API 黄金路径 18 步（需本机 env 文件含 APP_BOOTSTRAP_LOGIN/PASSWORD）
export SPRING_BASE=http://127.0.0.1:8080
export NONGPI_ENV_FILE=~/nongpi-local.env
python3 mobile/scripts/phase41_api_golden_path.py
# 期望输出 RESULT 18/18；报告 mobile/artifacts/phase41-golden-path-report.json
```

---

## 6. 测试

### 6.1 后端（不依赖 ERPNext）

```bash
cd backend
export APP_JWT_SECRET='ci-only-jwt-hmac-secret-key-32bytes'
export APP_CREDENTIAL_ENCRYPTION_KEY='ci-only-credential-master-key-32'
./mvnw test
```

集成测试用 **Testcontainers** 自动起 PostgreSQL，不需要本机 Docker 里已有 `nongpi-pg`。

真实 ERP 写探测（可选，需 live Site）：

```bash
export ERP_RUN_WRITE_PROBE=true
source /path/to/erp_env.sh   # 含 ERP 凭据
./mvnw test -Dsurefire.excludedGroups= -Dtest=com.nongpi.assistant.erp.client.ErpWriteProbeSmokeTest
```

### 6.2 Flutter（不依赖 Spring Boot）

```bash
cd mobile
flutter analyze
flutter test
```

### 6.3 CI（GitHub Actions）

| Workflow | 触发 | 内容 |
|----------|------|------|
| `backend-test.yml` | PR / push `main` | `./mvnw test` |
| `mobile-test.yml` | PR / push 改 `mobile/**` | analyze + test + debug APK 产物 |

PR 上应等两个 workflow 绿再合并。

---

## 7. 构建与部署

### 7.1 当前能构建什么

```bash
# 后端 JAR
cd backend && ./mvnw -DskipTests package
# 产物：backend/target/nongpi-backend-0.1.0-SNAPSHOT.jar

# Android debug APK
cd mobile && flutter build apk --debug \
  --dart-define=API_BASE_URL=https://你的生产-api-域名
# 产物：mobile/build/app/outputs/flutter-apk/app-debug.apk
```

### 7.2 生产部署（尚未在仓库内提供 compose）

架构目标（见 `AGENTS.md`）：**Flutter → Nginx → Spring Boot → ERPNext**，PostgreSQL / Redis 独立部署。  
本仓库**尚未包含**生产用 Docker Compose / K8s 清单；上线前需自行：

1. 配置 HTTPS 与 Nginx 反代 `/api/v1`
2. 注入生产环境变量（`APP_JWT_SECRET`、`APP_CREDENTIAL_ENCRYPTION_KEY`、数据库、ERP 连接）
3. Flutter Release 签名 + `API_BASE_URL=https://...`
4. 禁止 Android release 使用 HTTP cleartext

详细 API 与领域规则见 [`backend/README.md`](backend/README.md)、[`docs/06_API_DATA_DESIGN.md`](docs/06_API_DATA_DESIGN.md)。

---

## 8. 文档索引

| 文档 | 内容 |
|------|------|
| [`AGENTS.md`](AGENTS.md) | 开发硬约束（必读） |
| [`docs/01_PRODUCT_VISION.md`](docs/01_PRODUCT_VISION.md) | 产品定位 |
| [`docs/09_DEVELOPMENT_PLAN.md`](docs/09_DEVELOPMENT_PLAN.md) | 阶段与黄金路径 |
| [`backend/README.md`](backend/README.md) | API 列表、ERP 映射、Phase 验收 |
| [`mobile/README.md`](mobile/README.md) | Flutter 能力、限制、UI 黄金路径 |

---

## 9. 你现在最该做的一件事

Phase 4 合并前，**用 App 手工走通一条单**（不依赖 AI）：

登录 → 选韩兆亮 → 苹果80果 20 箱 + 粉蕉 30 件 → 存草稿 → 改 30 箱 → 提交 → 收 1000 → 补收尾款 → 看库存 → 退出。

能走通：PR #4 可转 Ready 并合并。走不通：把报错或界面现象发出来，只修阻塞项。
