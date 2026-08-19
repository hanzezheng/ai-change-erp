# AI农批经营助手技术选型规范

------

# 1. 技术选型目标

本项目目标：

打造长期商业 SaaS 产品。

因此技术选择必须满足：

- 稳定性
- 可维护性
- 可扩展性
- 支持多租户
- 支持 AI 能力持续演进
- 支持团队协作开发

------

# 2. 总体技术架构

最终技术架构：

```text
                 Flutter App

                      |

                    Nginx

                      |

              Spring Boot Backend

                      |

        --------------------------------

        |                              |

 ERPNext Adapter              Python AI Service


        |                              |

        ↓                              ↓


     ERPNext                  LLM / ASR / RAG
```

------

# 3. 技术分层原则

系统分为：

------

## 业务平台层

技术：

Spring Boot

------

负责：

- 用户
- 企业
- 权限
- API
- ERPNext集成
- 业务流程控制

------

## AI能力层

技术：

Python FastAPI

------

负责：

- LLM调用
- Prompt
- 语音处理
- 知识检索
- 客户身份解析

------

## 企业业务层

技术：

ERPNext

------

负责：

- Customer
- Item
- Sales Order
- Payment Entry
- Stock
- Accounting

------

# 4. 移动端技术

## Flutter

版本：

Flutter 3.x

语言：

Dart

------

选择原因：

- Android/iOS统一
- 开发效率高
- 适合企业移动App
- 方便快速迭代

------

Flutter负责：

- UI
- 页面导航
- 用户输入
- 数据展示

------

Flutter不负责：

- AI逻辑
- 权限
- ERP业务规则

------

# 5. 业务后端技术

## Spring Boot

版本：

- Java 21
- Spring Boot 3.x

------

选择原因：

长期 SaaS 需要：

- 稳定
- 成熟生态
- 企业级权限
- 事务控制

------

Spring Boot负责：

------

## 5.1 API Gateway

统一入口。

例如：

```text
Flutter

↓

Spring Boot
```

------

## 5.2 用户系统

包括：

- 注册
- 登录
- 用户管理

------

## 5.3 多租户管理

例如：

企业A：

```text
徐州水果档口
```

企业B：

```text
广州批发档口
```

------

## 5.4 权限管理

例如：

角色：

老板。

员工。

------

## 5.5 ERPNext Adapter

负责：

ERPNext通信。

------

# 6. AI服务技术

## Python FastAPI

------

选择原因：

AI生态成熟。

------

负责：

```text
AI Service

├── LLM Gateway

├── Prompt Management

├── Intent Recognition

├── Entity Extraction

├── Customer Resolver

├── Product Resolver

├── Knowledge Retrieval

├── Speech Processing

└── AI Evaluation
```

------

# 7. LLM模型架构

原则：

## 不绑定单一模型。

------

采用：

OpenAI Compatible API。

------

结构：

```text
Python AI Service

↓

Model Gateway

↓

LLM Provider
```

------

支持：

未来切换：

- OpenAI
- Claude
- Qwen
- DeepSeek
- 私有模型

------

# 8. LLM使用原则

LLM负责：

- 理解语言
- 提取信息
- 生成结构化结果

------

LLM不负责：

- 保存业务数据
- 判断权限
- 修改ERPNext

------

例如：

LLM输出：

```json
{
"customer_reference":"老韩",

"product_reference":"苹果",

"quantity":20
}
```

------

后续由业务系统处理。

------

# 9. 语音技术

## Speech Pipeline

```text
语音

↓

ASR

↓

文本

↓

AI理解

↓

业务操作
```

------

# ASR选择

抽象：

Speech Service。

------

支持：

## 云服务

例如：

- Whisper API
- 阿里云语音
- 腾讯云语音

------

## 私有部署

Whisper。

------

# 10. ASR设计原则

ASR不是最终业务识别。

------

例如：

ASR：

```text
韩照亮
```

------

AI Identity Resolver：

判断：

```text
韩兆亮
```

------

原因：

农批场景：

- 人名复杂
- 方言
- 环境噪音
- 商品简称

------

# 11. 数据库技术

采用：

双数据库架构。

------

# 11.1 ERPNext数据库

负责：

业务事实。

------

包括：

- Customer
- Item
- Sales Order
- Payment Entry
- Stock

------

# 11.2 PostgreSQL

负责：

SaaS和AI增强数据。

------

保存：

- Tenant
- User
- AI配置
- Customer Identity
- Product Identity
- Knowledge
- AI日志

------

原因：

- 稳定
- JSON支持
- pgvector扩展

------

# 12. Redis

用途：

缓存和临时状态。

------

包括：

- Session
- AI上下文
- 热点数据缓存
- 限流

------

不保存：

订单事实。

------

# 13. 消息队列

## RabbitMQ

------

用途：

异步任务。

例如：

- ERP同步
- 通知
- 后台AI任务

------

为什么不是Kafka：

MVP和早期 SaaS 不需要复杂流处理。

------

# 14. 向量检索

选择：

PostgreSQL + pgvector。

------

用途：

企业知识搜索。

例如：

查询：

“老韩是谁？”

------

不单独引入：

- Milvus
- Qdrant
- Weaviate

------

原因：

降低运维复杂度。

------

# 15. 文件存储

采用：

对象存储。

------

支持：

S3协议。

------

例如：

- MinIO
- 阿里云OSS
- 腾讯COS

------

保存：

- 语音
- 图片
- OCR文件
- 附件

------

# 16. 部署技术

## 开发环境

Docker Compose。

------

服务：

```text
Nginx

Spring Boot

Python AI Service

PostgreSQL

Redis

RabbitMQ
```

------

ERPNext：

独立部署。

------

# 17. 生产环境

初期：

Docker。

------

规模扩大：

Kubernetes。

------

目标：

支持：

- 多实例
- 自动扩容
- 高可用

------

# 18. 不采用技术

明确：

------

## 不使用复杂Agent框架

原因：

业务流程优先。

------

## 不使用Kubernetes起步

原因：

增加运维成本。

------

## 不使用Kafka

原因：

当前业务不需要。

------

## 不自研大模型

原因：

成本和价值不匹配。

------

# 19. 技术冻结表

| 领域     | 技术                  |
| -------- | --------------------- |
| 移动端   | Flutter               |
| 业务后端 | Spring Boot           |
| AI服务   | Python FastAPI        |
| 入口代理 | Nginx                 |
| ERP      | ERPNext               |
| 业务数据 | ERPNext MariaDB       |
| AI数据   | PostgreSQL            |
| 缓存     | Redis                 |
| 消息     | RabbitMQ              |
| 向量     | pgvector              |
| 模型接口 | OpenAI Compatible API |
| 语音     | Whisper/云ASR         |
| 部署     | Docker/Kubernetes     |

------

# 20. 最终技术原则

> Spring Boot 保证 SaaS 业务稳定，Python 保证 AI 快速迭代，ERPNext 保证企业数据可靠。