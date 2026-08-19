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

真机请把 `10.0.2.2` 换成电脑局域网 IP。`API_BASE_URL` 只填 Spring Boot 源站（例如 `http://192.168.1.8:8080`），不要再拼 `/api/v1`，客户端请求路径已包含该前缀。

Android debug 已允许 cleartext HTTP。

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
```

测试使用 Fake Dio，不连接真实 Spring Boot / ERPNext。
