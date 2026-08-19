# 10_CURSOR_RULES.md

版本：2.1（长期 SaaS 架构版）

> 这个文件建议直接改名为项目根目录的 `AGENTS.md`，作为 Cursor/Codex 的最高优先级开发规则。

------

# AGENTS.md

# AI农批经营助手开发规则

------

# 1. 项目定位

你正在开发：

# AI农批经营助手

------

产品定位：

> 基于 ERPNext 的 AI 原生农批经营 SaaS 平台。

------

系统不是：

❌ 新 ERP 系统
❌ ERPNext 替代品
❌ AI 聊天机器人
❌ 简单语音录单工具

------

系统目标：

```text
ERPNext

+

SaaS业务平台

+

AI智能交互

+

农批行业理解

=

AI农批经营助手
```

------

# 2. 开发前必须阅读

开始任何开发任务前，必须阅读：

```text
docs/

01_PRODUCT_VISION.md

02_ARCHITECTURE.md

03_PRODUCT_FLOW.md

04_DOMAIN_MAPPING.md

05_UI_SPEC.md

06_API_DATA_DESIGN.md

07_TECH_STACK_DECISION.md

08_AI_ENGINE_DESIGN.md

09_DEVELOPMENT_PLAN.md
```

------

这些文档定义：

- 产品方向
- 系统架构
- 数据边界
- 技术选型
- 开发顺序

------

如果代码实现和文档冲突：

必须先提出问题。

不要自行改变架构。

------

# 3. 核心架构规则

系统架构：

```text
Flutter

↓

Nginx

↓

Spring Boot Business Platform

↓

----------------------

ERPNext Adapter

Python AI Service

----------------------

↓

ERPNext
```

------

每一层职责必须保持清晰。

------

# 4. Flutter开发规则

Flutter负责：

- 页面
- 用户交互
- 数据展示
- 页面跳转

------

Flutter不负责：

❌ AI判断

❌ ERPNext调用

❌ 权限控制

❌ 业务规则

------

例如：

用户输入：

“老韩苹果20箱”

Flutter：

发送：

```json
{
"text":"老韩苹果20箱"
}
```

------

不要在Flutter里面判断：

“老韩是谁”。

------

# 5. Spring Boot开发规则

Spring Boot 是商业 SaaS 核心。

负责：

------

## 用户体系

包括：

- 登录
- 用户
- 企业

------

## 多租户

所有 SaaS 数据必须隔离。

------

## 权限

所有业务操作必须经过权限检查。

------

## API入口

Flutter只能访问：

Spring Boot。

------

## ERPNext Adapter

所有 ERPNext 操作必须经过 Adapter。

------

禁止：

业务代码直接调用 ERPNext。

------

错误：

```java
OrderService

↓

ERPNext API
```

------

正确：

```text
OrderService

↓

ERPNext Adapter

↓

ERPNext
```

------

# 6. Python AI Service开发规则

Python AI Service负责：

AI能力。

------

包括：

- LLM调用
- Prompt
- ASR
- Knowledge
- Identity Resolver
- Product Resolver

------

AI Service不负责：

❌ 保存订单

❌ 保存库存

❌ 保存收款

❌ 修改ERPNext数据

------

AI输出：

必须是：

结构化结果。

------

例如：

正确：

```json
{
"intent":"create_order",

"customer_reference":"老韩",

"product_reference":"苹果",

"quantity":20
}
```

------

错误：

```json
{
"order_created":true
}
```

------

# 7. ERPNext规则

ERPNext是：

## System of Record

------

保存：

- Customer
- Item
- Sales Order
- Payment Entry
- Stock
- Accounting

------

禁止：

创建第二套业务事实。

------

禁止：

自己建立：

```text
customers表

orders表

inventory表
```

作为主数据。

------

# 8. 数据规则

必须区分：

------

## ERPNext数据

企业事实。

例如：

订单：

韩兆亮

苹果80果

20箱

------

## AI增强数据

帮助理解。

例如：

```text
老韩

↓

韩兆亮
```

------

允许AI保存：

✅ 客户叫法

✅ 商品简称

✅ 企业语言

✅ AI日志

------

禁止AI保存：

❌ 订单事实

❌ 库存事实

❌ 收款事实

------

# 9. AI设计规则

AI负责：

理解。

------

AI不负责：

决定。

------

例如：

老板：

> 老王苹果20箱

------

AI可以：

找到候选。

------

AI不能：

没有确认直接创建错误客户订单。

------

------

# 10. 客户识别规则

禁止：

简单字符串匹配。

------

错误：

```java
name.equals(input)
```

------

原因：

农批真实环境：

老板说：

- 老韩
- 亮哥
- 韩老板

------

必须通过：

Customer Identity Resolver。

------

流程：

```text
老板表达

↓

Identity Resolver

↓

客户候选

↓

ERPNext Customer
```

------

# 11. AI入口规则

AI入口：

只存在：

底部导航中间按钮。

------

禁止：

- 每个页面添加AI按钮
- AI悬浮窗
- AI聊天首页

------

正确：

```text
首页

订单

🎤

客户

更多
```

------

AI结果：

必须回归业务页面。

------

例如：

AI创建订单：

```text
AI输入

↓

订单草稿

↓

订单编辑页

↓

提交ERPNext
```

------

# 12. 技术规则

固定技术：

------

## 前端

Flutter。

------

## 业务后端

Spring Boot。

------

## AI服务

Python FastAPI。

------

## 数据库

ERPNext：

MariaDB。

SaaS/AI：

PostgreSQL。

------

## 缓存

Redis。

------

## 消息

RabbitMQ（需要时）。

------

## 部署

Docker。

------

# 13. 不允许过度设计

未经确认禁止引入：

❌ 多Agent框架

❌ 自动经营系统

❌ 自研大模型

❌ 复杂工作流引擎

❌ 微服务无限拆分

------

优先完成：

真实业务闭环。

------

# 14. 新功能判断标准

增加任何功能前：

必须回答：

------

## 问题1

ERPNext是否已经支持？

如果支持：

优先使用。

------

## 问题2

是否降低用户操作成本？

如果不是：

不要做。

------

## 问题3

是否破坏数据唯一性？

如果是：

重新设计。

------

# 15. 开发流程

收到任务：

必须：

## 第一步

说明：

理解。

------

## 第二步

指出：

涉及文档。

------

## 第三步

提出：

实现方案。

------

## 第四步

编码。

------

## 第五步

测试。

------

## 第六步

总结。

------

禁止：

直接大范围修改。

------

# 16. 测试要求

所有核心功能：

必须测试两条路径。

------

## 手动路径

例如：

手动创建订单。

------

## AI路径

例如：

语音创建订单。

------

最终：

ERPNext结果一致。

------

# 17. MVP开发目标

优先：

```text
ERPNext连接

↓

Spring Boot平台

↓

Flutter业务页面

↓

AI入口

↓

AI订单

↓

客户身份学习
```

------

# 18. 最终原则

不要开发：

一个会聊天的软件。

------

开发：

一个：

```text
可靠经营系统

+

自然语言操作

+

行业理解能力
```

------

# 最重要一句话：

> ERPNext负责记录企业发生了什么，Spring Boot负责管理商业系统，Python AI负责理解老板想做什么，Cursor负责把确定的设计实现出来。