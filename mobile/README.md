# 农批经营助手 · Flutter 客户端

包名：`com.nongpi.assistant`

本目录是 Phase 4 的移动经营客户端。没有 AI 时必须完整可用：登录、手工开单、草稿保存/提交、订单详情、按订单收款、客户浏览、库存查询。

## 环境

- Flutter 3.x stable（开发时记录：Flutter 3.47.0 / Dart 3.13.0）
- 只请求 Spring Boot `/api/v1/**`，禁止直连 ERPNext / PostgreSQL

```bash
cd mobile
flutter pub get
flutter run \
--dart-define=API_BASE_URL=http://10.0.2.2:8080
```

`API_BASE_URL` 只填 Spring Boot 源站（例如 `http://192.168.1.8:8080`），不要再拼 `/api/v1`，客户端请求路径已包含该前缀。

## 端口与本地栈

推荐拓扑：**Spring Boot `8080`**，**ERPNext `8000`**（见 `backend/README.md`）。
若本机 ERPNext 已占用 `8080`，可临时把 Spring 起在其它端口（例如 `18082`），
Emulator 用对应端口：

```bash
flutter run -d emulator-5554 \
  --dart-define=API_BASE_URL=http://10.0.2.2:18082
```

## Android 本地联调

Android Emulator 访问宿主机 Spring Boot 时使用 `10.0.2.2`（不要使用
`localhost`）：

```bash
cd mobile
flutter run -d emulator-5554 \
  --dart-define=API_BASE_URL=http://10.0.2.2:8080
```

Cloud / CI 无图形界面的 Emulator 需要 KVM 权限。Linux 上若提示无法使用
`/dev/kvm`，将用户加入 `kvm` 组后用 `sg kvm -c 'emulator ...'` 启动。
仅允许**一个** AVD 实例；多实例会导致 adb 长期 `offline`。

真实设备与电脑在同一局域网时，使用电脑的局域网地址，并确保 Spring Boot
监听该网卡且防火墙放行端口：

```bash
cd mobile
flutter run \
  --dart-define=API_BASE_URL=http://192.168.1.8:8080
```

`http://` 明文请求只在 Android debug manifest overlay 中开启；main/release
manifest 不允许全局 cleartext。生产构建必须使用 `https://` API 地址和有效的
TLS 证书，不要把本地 HTTP 地址写进生产配置。

## Release 签名

本阶段只验证 debug APK，不发布应用商店。Production Release Signing 在发布前
必须单独配置正式 keystore、密钥保管和 CI secrets；不要把 Android debug keystore
当作正式签名方案。完成正式签名配置后，才可使用以下发布构建示例：

```bash
cd mobile
flutter build appbundle --release \
  --dart-define=API_BASE_URL=https://api.example.com
```

## 真实黄金路径

在 Android Emulator 或真实设备上运行上述 debug 命令，并先启动可访问的
Spring Boot、PostgreSQL 与 ERPNext v16。使用真实账号完成以下闭环（不要使用
Fake Dio 测试数据）：

1. 登录并选择租户（如账号有多个租户），确认首页、客户和库存都能加载。
2. 新建订单，选择客户“韩兆亮”、`APPLE-80`（规格 `80果`），确认默认成交价来自当前 UOM 的 `referencePrice`；输入 `20` 箱。
3. 添加 `BANANA-FEN`，输入 `30` 件，保存草稿；在 ERPNext 检查同一张 Sales Order 及其行 ID。
4. 在 App 回读订单后把苹果从 20 改为 30，保存并确认 ERPNext 仍是同一张 Sales Order，再提交订单。
5. 进入记录收款，选择 ERPNext 返回的付款方式，收款 `1000` 并选择“已到账”；创建 Draft 后确认同一个 payment ID，检查订单为部分收款。
6. 使用“补收尾款”完成剩余金额，确认订单变为已收款但不会因付款自动变为已完成；检查两笔 Payment Entry 的历史。
7. 打开库存、客户详情确认数据来自后端，最后退出登录。

Emulator 运行时可用 `flutter devices` 查看设备 ID；真实设备需开启 USB
调试并允许 App 安装。若 API 不可达，先检查设备到电脑的路由、防火墙和
Spring Boot 监听地址，再重试，不要在客户端拼接额外的 `/api/v1`。

## 当前能力

- 登录 / 多租户选择 / Token 旋转刷新 / 退出
- 一级导航：首页 | 订单 | 麦克风（本阶段 disabled） | 客户 | 更多
- 订单列表搜索与状态筛选
- 新订单 Local Edit：客户选择、商品选择、UOM、数量单价
- 保存草稿 `POST /orders`（一次 Idempotency-Key）
- 修改草稿 `PUT`（`transactionDate` + `expectedModifiedAt`）
- 提交订单（新单：先建草稿再 submit；已有草稿直接 submit）
- 已提交订单只读详情、收款摘要、补收尾款
- 动态收款方式、待确认 / 确认到账
- 客户列表/详情（只读字段）、库存、商品浏览、账号信息

## 已知限制

- 麦克风与语音 / AI / ASR 未启用（Phase 5）
- 无客户/商品 CRUD
- 客户页不显示应收、累计交易、常买商品（后端无这些字段）
- 首页不显示今日订单/待收款指标（分页无 totalCount）
- 无全局收款流水、无报表、无催款
- `ProductVariant` 无库存字段；库存只在库存页
- 二级页不渲染麦克风按钮
- 本机新订单未保存离开即丢失，不落 Hive/SQLite

## 测试

```bash
cd mobile
flutter analyze
flutter test
flutter build apk --debug --dart-define=API_BASE_URL=http://10.0.2.2:8080
```

测试使用 Fake Dio，不连接真实 Spring Boot / ERPNext。

## API 黄金路径（无 UI）

在 Spring Boot + PostgreSQL + ERPNext 已启动且种子数据就绪时：

```bash
export SPRING_BASE=http://127.0.0.1:8080   # 或实际 Spring 端口
export NONGPI_ENV_FILE=/path/to/local.env  # 含 APP_BOOTSTRAP_LOGIN/PASSWORD
python3 mobile/scripts/phase41_api_golden_path.py
```

报告写入 `mobile/artifacts/phase41-golden-path-report.json`（18 步全绿即通过）。

## Emulator UI 黄金路径与截图

```bash
chmod +x mobile/scripts/emulator_golden_path.sh
SPRING_PORT=8080 mobile/scripts/emulator_golden_path.sh
```

按脚本提示完成 `mobile/README.md`「真实黄金路径」手工步骤，每屏执行
`shot <name>` 保存到 `mobile/artifacts/screenshots/`。
