# 02_ARCHITECTURE.md

版本：2.2（长期 SaaS 生产架构版）

------

# AI农批经营助手系统架构设计

------

# 1. 架构目标

AI农批经营助手是一套：

> 基于 ERPNext 的 AI 原生 SaaS 经营平台。

------

目标：

让农批企业拥有：

- 稳定的业务系统
- 移动端经营体验
- 自然语言操作能力
- 企业专属 AI 能力

------

核心思想：

```text
ERPNext

负责：

企业发生了什么


AI

负责：

老板想表达什么


App

负责：

老板如何方便使用
```

------

# 2. 总体生产架构

最终架构：

```text
                 用户

                  |

                  |

              Flutter App


                  |

                  |

              HTTPS请求


                  |

                  |

                Nginx

        (SSL / 代理 / 限流)


                  |

                  |

          Spring Boot Backend


                  |

        ----------------------

        |                    |

        ↓                    ↓


 ERPNext Adapter       Python AI Service


        |                    |

        |                    |

        ↓                    ↓


    ERPNext             LLM / ASR / RAG
```

------

# 3. 架构分层

系统分为：

## 第一层：入口层

## 第二层：业务平台层

## 第三层：AI能力层

## 第四层：企业系统层

------

# 4. 第一层：入口层

## Nginx

技术：

Nginx。

------

职责：

------

## 4.1 HTTPS

负责：

- SSL证书
- HTTPS加密

------

用户：

```text
https://api.xxx.com
```

------

进入：

Nginx。

------

## 4.2 反向代理

例如：

请求：

```text
/api/orders
```

转发：

```text
Spring Boot
```

------

AI内部接口：

```text
/internal/ai/*
```

转发：

```text
Python AI Service
```

------

## 4.3 限流

SaaS环境：

需要保护系统。

例如：

限制：

- 单用户请求频率
- AI调用频率

------

## 4.4 静态资源

未来：

支持：

- Web管理后台
- 图片
- 文件

------

# 5. 第二层：Spring Boot Business Platform

## 定位

SaaS业务核心。

------

技术：

- Java 21
- Spring Boot 3.x

------

负责：

------

# 5.1 用户体系

包括：

- 用户注册
- 登录
- 账号管理

------

# 5.2 企业租户

支持：

多企业 SaaS。

------

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

数据隔离。

------

# 5.3 权限管理

例如：

角色：

老板：

全部权限。

员工：

部分权限。

------

AI必须遵守权限。

------

# 5.4 API入口

Flutter：

只访问：

Spring Boot。

------

禁止：

Flutter直接访问：

- Python AI
- ERPNext
- LLM

------

# 5.5 ERPNext Adapter

Spring Boot通过Adapter连接ERPNext。

------

职责：

- 数据转换
- API调用
- 错误处理
- 权限控制

------

# 6. 第三层：Python AI Service

## 定位

AI能力中心。

------

技术：

Python

FastAPI

------

负责：

------

# 6.1 LLM调用

包括：

- Prompt管理
- 模型调用
- 输出解析

------

# 6.2 意图识别

例如：

老板：

> 老韩苹果20箱

识别：

```text
create_order
```

------

# 6.3 信息提取

提取：

```text
客户表达：

老韩


商品表达：

苹果


数量：

20箱
```

------

# 6.4 Customer Resolver

解决：

老板语言。

------

例如：

ERPNext：

```text
韩兆亮
```

------

老板：

```text
老韩
```

------

AI：

建立：

```text
老韩

↓

韩兆亮
```

------

# 6.5 Product Resolver

例如：

ERPNext：

```text
苹果80果
```

------

老板：

```text
八零
```

------

AI：

解析。

------

# 6.6 Knowledge Retrieval

企业知识：

包括：

- 客户叫法
- 商品叫法
- 企业规则

------

# 7. 第四层：ERPNext

## 定位

System of Record。

------

保存：

企业真实数据。

------

包括：

------

## Customer

客户。

------

## Item

商品。

------

## Sales Order

订单。

------

## Payment Entry

收款。

------

## Stock

库存。

------

## Accounting

财务。

------

# 8. 数据流示例

## 创建订单

老板：

> 老韩苹果20箱

------

流程：

```text
Flutter

↓

Nginx

↓

Spring Boot

↓

Python AI Service

↓

客户解析

↓

商品解析

↓

Spring Boot业务校验

↓

ERPNext Adapter

↓

ERPNext Sales Order Draft

↓

返回App

↓

用户提交

↓

ERPNext保存
```

------

# 9. AI和业务边界

非常重要。

------

## AI负责：

概率性工作。

例如：

- 理解
- 匹配
- 推荐

------

## Spring Boot负责：

确定性业务。

例如：

- 权限
- 状态
- 校验
- 流程

------

## ERPNext负责：

事实。

例如：

- 订单
- 收款
- 库存

------

# 10. 数据存储

------

## ERPNext数据库

保存：

业务事实。

------

例如：

Sales Order。

------

## PostgreSQL

保存：

SaaS和AI增强数据。

包括：

- Tenant
- User
- AI配置
- Customer Identity
- Product Identity
- Knowledge
- AI日志

------

## Redis

保存：

临时数据。

包括：

- Session
- AI上下文
- 缓存

------

## RabbitMQ

保存：

异步任务。

例如：

- 通知
- 同步任务
- AI后台任务

------

# 11. 部署架构

MVP：

Docker。

------

结构：

```text
Nginx

↓

Spring Boot

↓

Python AI Service

↓

PostgreSQL

↓

Redis

↓

RabbitMQ


ERPNext独立部署
```

------

# 12. 后期扩展

规模扩大：

可以拆分：

- Tenant Service
- Notification Service
- AI Evaluation Service
- Integration Service

------

但是 MVP 不提前微服务化。

------

# 13. 架构禁止事项

禁止：

------

## 1. AI直接访问ERPNext数据库

必须经过：

ERPNext Adapter。

------

## 2. Flutter直接调用AI模型

必须经过：

Backend。

------

## 3. 创建第二套ERP

禁止：

自建：

- orders表作为事实
- inventory表作为事实

------

## 4. AI绕过权限

禁止。

------

# 14. 最终架构图

```text
                 Flutter App

                      |

                    Nginx

                      |

              Spring Boot Platform

                      |

        --------------------------------

        |                              |

 ERPNext Adapter               Python AI Service


        |                              |

        ↓                              ↓


     ERPNext                 LLM / ASR / RAG
```

------

# 15. 最终原则

> Nginx负责入口稳定，Spring Boot负责商业系统，Python负责AI智能，ERPNext负责企业事实。