# 10_CURRENT_STATUS.md

版本：1.0  
更新日期：2026-08-20  
用途：**任何人接手项目时先读本文**，再按需读 `AGENTS.md` 与其它 docs。

---

## 1. 一句话现状

后端 Phase 1–3 已进 `main`；Flutter 无 AI 经营闭环（Phase 4）**代码与单测已完成**，在 Draft [PR #4](https://github.com/hanzezheng/ai-change-erp/pull/4)，**尚未合并**。  
负责人已在 Chrome 真栈冒烟通过；API 黄金路径 18/18。  
**当前闸门：把 PR #4 未提交修复（CORS / init-dev）提交 → Ready → Merge。在此之前禁止开始 Phase 5（AI）。**

---

## 2. 阶段进度总表

| 阶段 | 内容 | 状态 | 代码位置 |
|------|------|------|----------|
| Phase 1 | ERPNext 主数据只读 | **已合并 main** | `backend/` Adapter |
| Phase 2 | SaaS / JWT / Tenant / Flyway | **已合并 main** | PR #2 |
| Phase 3 | 订单 Draft/Submit、收款累计 | **已合并 main** | PR #3 |
| Phase 4 | Flutter 无 AI 经营闭环 | **Draft PR #4** | 分支 `cursor/flutter-manual-business-closure-b267`，目录 `mobile/` |
| Phase 4.1 | 可靠性收口 + 本地脚本 | **含在 PR #4** | 分页、Payment draft、WSL/ERP 初始化脚本 |
| Phase 5 | AI / ASR / Resolver | **未开始** | — |
| Phase 6 | Identity / Knowledge | **未开始** | — |

长期阶段规划仍以 [`09_DEVELOPMENT_PLAN.md`](09_DEVELOPMENT_PLAN.md) 为准；**以本文「闸门」覆盖文档里写「Phase 4 已完成」但 PR 未合的歧义**。

---

## 3. 已交付能力（可依赖）

### 后端（main）

- 认证：login / refresh 轮换 / logout / 多租户选择
- ERP 连接：DB 加密存储；一个 Tenant ↔ 一个 ERPNext Site
- 读：客户、商品选择器、库存、历史成交价
- 写：多商品 Draft 订单、同单更新、Submit、收款 Draft/Confirm、payment-summary
- CI：`.github/workflows/backend-test.yml`（Testcontainers，默认不连真 ERP）

### Flutter（PR #4 分支）

- 登录、五槽导航（麦克风 **disabled**）
- 订单列表 / Local Edit / Customer·Product·UOM Selector
- Draft 保存与提交、订单详情、分次收款与补收尾款
- 客户 / 库存 / 首页 / 更多
- 单测约 51、`flutter analyze` 干净；Mobile CI 含 debug APK
- **不直连 ERPNext**，只打 Spring `/api/v1`

### 本地工具（PR #4 分支）

| 脚本 | 作用 |
|------|------|
| `scripts/erpnext/init-dev.sh` | ERPNext Setup Wizard + 黄金路径种子 + API Key（自动识别 backend 容器名） |
| `scripts/wsl-dev.sh` | WSL 一键：种子 → env → Spring → API 18 步 |
| `mobile/scripts/phase41_api_golden_path.py` | 无 UI 的 API 黄金路径验收 |

---

## 4. 未完成 / 阻塞（接手必看）

| 项 | 说明 |
|----|------|
| PR #4 未合 | Chrome 冒烟已过；待提交 CORS + init-dev 修复后 Ready / Merge |
| Android Emulator | 多数 Windows 机尚未建 AVD；可用 Chrome/Windows 桌面先验收 |
| 生产部署 | 无正式 Nginx/Compose/发布签名；仅本地开发栈 |
| Phase 5 | **明确禁止提前开做** |

Windows + WSL 常见坑（详见根目录 `README.md`）：

- `mvnw` CRLF → `sed -i 's/\r$//' mvnw`；需 `JAVA_HOME`（OpenJDK 21）
- `APP_JWT_SECRET` 报不够 32 字 → 多半没 `source` env，不是真不够长
- WSL 调 `/mnt/c/.../flutter` → CRLF；Flutter 用 Windows 装或 WSL 内单独装
- Spring 在 WSL、模拟器在 Windows → **不要用 `10.0.2.2`**，用 `wsl hostname -I` 的 IP

---

## 5. 下一步（按优先级，禁止跳步）

### P0 — 立即（关闭 Phase 4）

1. ~~真实栈 + Chrome 冒烟~~（已完成；API 18/18 亦通过）
2. **提交并推送**：Spring CORS（Flutter Web）、`init-dev.sh` CRLF/console 修复、本文验收更新
3. PR #4 转 **Ready** → CI 绿 → **Merge**
4. Merge 后：本文第 2 节 Phase 4 改为「已合并 main」；从 `main` 起干净拓扑（Spring 8080 / ERP 8000）

### P1 — Phase 4 合并之后

1. 可选：Android Studio + AVD，补真机/模拟器截图（非合并硬门槛）。
2. 清理多余本地 Spring 多实例习惯；env 模板 `.env.example`（无真实密钥）。
3. 再开 **Phase 5** 分支：ASR → Intent → Customer/Product Resolver → `create_order` / `update_current_order`（严格按 `AGENTS.md` + `08_AI_ENGINE_DESIGN.md`）。

### P2 — 明确不做（除非产品书面改决策）

- 催款工作流、多 Agent、自研第二套 ERP、K8s MVP、向量库集群
- 未合 Phase 4 前任何 AI 演示优先于手工经营

---

## 6. 接手 30 分钟清单

```text
□ 读 AGENTS.md 第 1、8、11–13、58、108 节
□ 读本文全文 + README.md「端口约定」与「Windows/WSL」
□ git checkout cursor/flutter-manual-business-closure-b267 && git pull
□ docker ps 确认 ERPNext（frontend 映射 8000 或 8080）与 postgres
□ bash scripts/erpnext/init-dev.sh（或 wsl-dev.sh）
□ source ~/nongpi-local.env && 启动 Spring，curl /actuator/health
□ python3 mobile/scripts/phase41_api_golden_path.py  → 期望 18/18
□ Windows: flutter run -d chrome --dart-define=API_BASE_URL=http://<WSL_IP>:8080
□ 走完第 5 节 P0 手工路径，把结果记回本文「验收记录」
```

---

## 7. 验收记录（负责人填写）

| 日期 | 人 | 环境 | 结果 | 备注 |
|------|----|------|------|------|
| 2026-08-20 | Cloud Agent | 真 API 栈 | API 黄金路径 18/18 | Emulator 截图未完成 |
| 2026-08-20 | 本地接手 | Windows WSL + ERP `:8000` + Spring `:8080` | 种子 / API 18/18 / Spring UP | 补 CORS 后 Chrome 可连 |
| 2026-08-20 | 负责人 | Flutter Chrome → WSL IP `:8080` | 冒烟通过（点测无明显问题） | 完整 UI 黄金路径未逐项书面勾选 |

**合并判定签字：**

- [x] 手工冒烟通过（Chrome）  
- [ ] PR #4 Ready + CI 绿  
- [ ] 已 merge 入 main  
- [ ] 本文第 2 节 Phase 4 改为「已合并 main」

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
