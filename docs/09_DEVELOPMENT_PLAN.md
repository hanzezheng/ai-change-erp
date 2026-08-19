# 09_DEVELOPMENT_PLAN.md

版本：2.2

# 开发计划

## 1. MVP主线

开发顺序不变，但核心验收增加：

### Phase 1：ERPNext

跑通：

- Customer
- Item
- Sales Order + Sales Order Items
- Payment Entry
- Stock查询

### Phase 2：Spring Boot

完成：

- 用户
- Tenant
- 权限
- ERPNext Adapter
- API

### Phase 3：Flutter

先做到无AI可用：

- 首页
- 订单列表
- 多商品订单编辑
- 订单详情
- 客户
- 收款

### Phase 4：AI

完成：

- 短按快速处理
- 长按直接录音
- ASR
- 多商品订单解析
- 客户Resolver
- 商品Resolver

### Phase 5：知识

完成：

- Customer Identity
- Product Identity
- 用户纠错积累

------

## 2. 第一条黄金路径

> 老韩要20箱八零、30件粉蕉，还是以前价格。

必须跑通：

长按语音
→ ASR
→ 客户解析
→ 两个商品解析
→ 历史价格查询
→ ERPNext Draft Sales Order
→ 订单编辑
→ 用户修改/提交

------

## 3. 第二条黄金路径

故意测试错误：

ASR：

> 韩照亮苹果20箱

系统找到两个可能客户。

用户只选择客户。

后续商品、数量保持原识别结果。

------

## 4. 第三条黄金路径

没有AI：

手工建立同样订单。

最终 ERPNext 结构与 AI 创建一致。

------

## 5. MVP必须验收

- 多商品订单
- 手工新增商品
- 删除商品
- 修改商品数量/价格
- AI追加商品
- 客户姓名ASR错误
- 商品简称
- 长按录音
- AI不可用时手动流程完整

------

## 6. 暂不做

- 多Agent
- 自动采购
- 自动经营
- 自研ERP
- 大规模知识图谱
- 独立向量数据库集群