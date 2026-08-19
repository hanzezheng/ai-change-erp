# 09_DEVELOPMENT_PLAN.md

版本：3.0

# 开发计划

## 1. 阶段划分

### Phase 1A：ERPNext 主数据读取

- Customer
- Item / Variant
- UOM
- Price
- Inventory

状态：已完成（只读链路）。

Phase 1B：已用官方 `frappe_docker` 标准 ERPNext v16 做真实 Smoke Test；
路径空格编码缺陷已修复。未进入 PostgreSQL / 写链路 / Flutter / AI。

### Phase 2：SaaS 基础

- PostgreSQL
- Flyway
- Spring Security
- User
- Tenant
- Membership
- Authentication
- Authorization
- Audit
- ERP Connection 持久配置

Phase 1A 的临时 Token → Tenant Filter 已在本阶段替换为正式认证与授权。

状态：已完成（见分支 `cursor/saas-infrastructure-b267`）。

### Phase 3：ERPNext 业务写链路

- Sales Order
- Sales Order Item[]
- Draft
- Submit
- Update
- Payment Entry
- Payment accumulation

Draft 边界见 `docs/04_DOMAIN_MODEL.md` 第 8 节与 `docs/06_API_DATA_DESIGN.md` 第 24 节。

### Phase 4：Flutter 无 AI 业务闭环

- 首页
- 订单列表
- 多商品订单编辑
- 订单详情
- 客户
- 收款

本阶段结束时，不用 AI 必须能完整经营。

### Phase 5：AI / ASR / Resolver

- 短按快速处理
- 长按直接录音
- ASR
- 多商品订单解析
- Customer Resolver
- Product Resolver

### Phase 6：Identity Learning / Knowledge 增强

- Customer Identity
- Product Identity
- 用户纠错积累

------

## 2. 黄金路径

黄金路径内容不变，但可验收的时点跟随上面的阶段顺序：
写链路相关路径在 Phase 3 之后才可能跑通，AI 相关路径在 Phase 5 之后才可能跑通。

### 2.1 第一条：手工订单（Phase 3 + Phase 4）

没有 AI，手工建立多商品订单：

订单
→ 新增
→ 选择韩兆亮
→ 添加苹果80果 / 箱 / 20
→ 添加香蕉粉蕉 / 件 / 30
→ 保存草稿（创建 ERPNext Draft Sales Order）
→ 提交
→ ERPNext

再次「保存修改」必须更新同一张 Sales Order，不新建单。

### 2.2 第二条：AI 创建订单（Phase 5）

> 老韩要20箱八零、30件粉蕉，还是以前价格。

必须跑通：

长按语音
→ ASR
→ 客户解析
→ 两个商品解析
→ 历史价格查询
→ 订单编辑页 Draft State
→ 用户修改
→ 保存草稿 / 提交
→ ERPNext

AI 解析完成不自动创建 ERPNext Sales Order。

### 2.3 第三条：局部歧义（Phase 5）

故意测试 ASR 错误：

> 韩照亮苹果20箱

系统找到两个可能客户。

用户只选择客户。

后续商品、数量保持原识别结果。

### 2.4 第四条：手工与 AI 结果一致（Phase 5）

手工建立与 AI 建立同样一张订单，
最终 ERPNext 结构一致。

### 2.5 第五条：累计收款（Phase 3）

订单 2,320，先收 1,000 → 部分收款，再收 1,320 → 已收款。

Order Status 不因付款自动完成。

------

## 3. MVP 必须验收

- 多商品订单
- 手工新增商品
- 删除商品
- 修改商品数量 / 价格
- 保存草稿创建 ERPNext Draft
- 再次保存修改同一张 Sales Order
- 存在无效商品行时保存草稿被阻止且不静默删除
- AI 追加商品
- 客户姓名 ASR 错误
- 商品简称
- 长按录音
- AI 不可用时手动流程完整

------

## 4. 暂不做

- 多 Agent
- 自动采购
- 自动经营
- 自研 ERP
- 大规模知识图谱
- 独立向量数据库集群
