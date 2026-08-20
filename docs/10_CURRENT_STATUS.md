# 10_CURRENT_STATUS.md

版本：1.1  
更新日期：2026-08-20  
用途：**任何人接手项目时先读本文**，再按需读 `AGENTS.md` 与其它 docs。

---

## 1. 一句话现状

后端 Phase 1–3 与 Flutter Phase 4（无 AI 经营闭环）**均已合并 `main`**（含 [PR #4](https://github.com/hanzezheng/ai-change-erp/pull/4) 内容；本地 merge 提交 `5b2cab5`）。  
Chrome 冒烟 + API 黄金路径 18/18 已通过；Spring 已支持本机 Flutter Web CORS。  
**当前闸门已解除：可以开 Phase 5（AI / ASR / Resolver）分支。**

---

## 2. 阶段进度总表

| 阶段 | 内容 | 状态 | 代码位置 |
|------|------|------|----------|
| Phase 1 | ERPNext 主数据只读 | **已合并 main** | `backend/` Adapter |
| Phase 2 | SaaS / JWT / Tenant / Flyway | **已合并 main** | PR #2 |
| Phase 3 | 订单 Draft/Submit、收款累计 | **已合并 main** | PR #3 |
| Phase 4 | Flutter 无 AI 经营闭环 | **已合并 main** | `mobile/`（原 PR #4） |
| Phase 4.1 | 可靠性收口 + 本地脚本 | **已合并 main** | 分页、Payment draft、WSL/ERP 初始化、CORS |
| Phase 5 | AI / ASR / Resolver | **未开始（可开）** | — |
| Phase 6 | Identity / Knowledge | **未开始** | — |

长期阶段规划仍以 [`09_DEVELOPMENT_PLAN.md`](09_DEVELOPMENT_PLAN.md) 为准；进度与闸门以本文为准。

---

## 3. 已交付能力（可依赖）

### 后端（main）

- 认证：login / refresh 轮换 / logout / 多租户选择
- ERP 连接：DB 加密存储；一个 Tenant ↔ 一个 ERPNext Site
- 读：客户、商品选择器、库存、历史成交价
- 写：多商品 Draft 订单、同单更新、Submit、收款 Draft/Confirm、payment-summary
- CI：`.github/workflows/backend-test.yml`（Testcontainers，默认不连真 ERP）

### Flutter（main）

- 登录、五槽导航（麦克风 **disabled**，留给 Phase 5）
- 订单列表 / Local Edit / Customer·Product·UOM Selector
- Draft 保存与提交、订单详情、分次收款与补收尾款
- 客户 / 库存 / 首页 / 更多
- 单测约 51、`flutter analyze` 干净；Mobile CI 含 debug APK
- **不直连 ERPNext**，只打 Spring `/api/v1`
- 本机开发 CORS：允许 `http://localhost:*` / `http://127.0.0.1:*`

### 本地工具（main）

| 脚本 | 作用 |
|------|------|
| `scripts/erpnext/init-dev.sh` | ERPNext Setup Wizard + 黄金路径种子 + API Key（自动识别 backend 容器名） |
| `scripts/wsl-dev.sh` | WSL 一键：种子 → env → Spring → API 18 步 |
| `mobile/scripts/phase41_api_golden_path.py` | 无 UI 的 API 黄金路径验收 |

---

## 4. 未完成 / 阻塞（接手必看）

| 项 | 说明 |
|----|------|
| Android Emulator | 多数 Windows 机尚未建 AVD；Chrome 已可验收 |
| 生产部署 | 无正式 Nginx/Compose/发布签名；仅本地开发栈 |
| Phase 5 | 可开分支；严格按 `AGENTS.md` + `08_AI_ENGINE_DESIGN.md` |

Windows + WSL 常见坑（详见根目录 `README.md`）：

- `mvnw` CRLF → `sed -i 's/\r$//' mvnw`；需 `JAVA_HOME`（OpenJDK 21）
- `APP_JWT_SECRET` 报不够 32 字 → 多半没 `source` env，不是真不够长
- WSL 调 `/mnt/c/.../flutter` → CRLF；Flutter 用 Windows 装或 WSL 内单独装
- Spring 在 WSL、模拟器在 Windows → **不要用 `10.0.2.2`**，用 `wsl hostname -I` 的 IP

---

## 5. 下一步（按优先级，禁止跳步）

### P0 — 下一步开发（Phase 5）

从 `main` 开分支，按序：

1. Python FastAPI AI Service 骨架 + Model Gateway  
2. ASR（Voice → Text）接入  
3. Customer / Product Resolver  
4. Intent：`create_order` / `update_current_order`（先于其它 Intent）  
5. Flutter：全局麦克风短按 Quick Action / 长按直录（仍走 Spring，不直连 AI）

### P1 — 收尾与加固

1. 可选：Android Studio + AVD 截图  
2. env 模板 `.env.example`（无真实密钥）  
3. 若 GitHub 上 PR #4 仍显示 Draft/Open：在网页关闭或标记已合并（内容已进 `main`）

### P2 — 明确不做（除非产品书面改决策）

- 催款工作流、多 Agent、自研第二套 ERP、K8s MVP、向量库集群

---

## 6. 接手 30 分钟清单

```text
□ 读 AGENTS.md 第 1、8、11–13、58、108 节
□ 读本文全文 + README.md「端口约定」与「Windows/WSL」
□ git checkout main && git pull
□ docker ps 确认 ERPNext（frontend 映射 8000）与 postgres
□ bash scripts/erpnext/init-dev.sh（或 wsl-dev.sh）
□ source ~/nongpi-local.env && 启动 Spring，curl /actuator/health
□ python3 mobile/scripts/phase41_api_golden_path.py  → 期望 18/18
□ Windows: flutter run -d chrome --dart-define=API_BASE_URL=http://<WSL_IP>:8080
```

---

## 7. 验收记录（负责人填写）

| 日期 | 人 | 环境 | 结果 | 备注 |
|------|----|------|------|------|
| 2026-08-20 | Cloud Agent | 真 API 栈 | API 黄金路径 18/18 | Emulator 截图未完成 |
| 2026-08-20 | 本地接手 | Windows WSL + ERP `:8000` + Spring `:8080` | 种子 / API 18/18 / Spring UP | 补 CORS 后 Chrome 可连 |
| 2026-08-20 | 负责人 | Flutter Chrome → WSL IP `:8080` | 冒烟通过（点测无明显问题） | 完整 UI 黄金路径未逐项书面勾选 |
| 2026-08-20 | — | git | Phase 4 已合入 `main`（`5b2cab5`） | 含 CORS / init-dev |

**合并判定签字：**

- [x] 手工冒烟通过（Chrome）  
- [x] 修复已推送并合入 main（本地 merge 推送；若网页 PR 仍 Open 请手动关闭）  
- [x] 已 merge 入 main  
- [x] 本文第 2 节 Phase 4 改为「已合并 main」

---

## 8. 关键链接

| 资源 | 路径 |
|------|------|
| 总规则 | [`AGENTS.md`](../AGENTS.md) |
| 运行/测试/部署 | [`README.md`](../README.md) |
| 阶段规划 | [`09_DEVELOPMENT_PLAN.md`](09_DEVELOPMENT_PLAN.md) |
| PR #4 | https://github.com/hanzezheng/ai-change-erp/pull/4 |
| 后端说明 | [`backend/README.md`](../backend/README.md) |
| 客户端说明 | [`mobile/README.md`](../mobile/README.md) |

---

## 9. 文档维护约定

- **状态变化只改本文**（合并、闸门、阻塞），不要散落在聊天记录。
- `09_DEVELOPMENT_PLAN.md` 只改阶段范围与冻结规则；进度以本文为准。
- 禁止为「显得完整」再堆无关长文；交接信息优先写进第 4、5、7 节。
