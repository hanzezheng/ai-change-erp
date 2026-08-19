# 08_AI_ENGINE_DESIGN.md

版本：2.2

# AI Engine设计

## 1. AI职责

Python AI Service只负责概率性理解：

- 意图
- 实体
- 客户表达
- 商品表达
- 数量/单位
- 价格表达
- 时间/备注
- 上下文理解

Spring Boot负责确定性业务。

------

## 2. 多商品解析

订单Intent输出必须天然支持 items[]。

例如：

> 老韩苹果20箱，香蕉30件，葡萄10箱。

提取：

customer_reference = 老韩

items:

1. 苹果 / 20 / 箱
2. 香蕉 / 30 / 件
3. 葡萄 / 10 / 箱

不能只提取第一个商品。

------

## 3. 部分解析

每条 item 独立拥有解析状态。

例如：

苹果80果 → resolved
香蕉 → resolved
葡萄 → ambiguous

系统只追问葡萄。

------

## 4. Customer Resolver

综合：

- 精确名称
- confirmed alias
- ASR variant
- 发音/拼音相似
- 最近上下文
- 历史纠错

模型不直接把相似姓名当成事实。

------

## 5. Product Resolver

类似：

用户表达
→ Product Identity
→ ERPNext Item候选

支持农批简称：

- 八零
- 大果
- 粉蕉

企业确认知识优先于通用模型猜测。

------

## 6. ASR

ASR负责声音转文本，不负责最终人名、商品身份判断。

例如：

ASR：韩照亮

Resolver仍可匹配：

韩兆亮。

应保留原始 ASR 文本用于纠错和评估。

------

## 7. Prompt

Prompt输出结构化JSON。

Prompt负责：

- 用户说了什么
- 用户可能想做什么
- 哪些字段存在

Prompt不负责：

- 是否有权限
- 是否允许提交
- ERPNext写入

------

## 8. Knowledge

优先保存结构化身份知识。

V1不需要把所有内容都做成向量RAG。

结构化别名：

SQL精确/模糊检索优先。

pgvector主要预留给：

- 非结构化企业知识
- 长文本业务规则
- 文档语义检索

------

## 9. Risk

风险不是简单由LLM confidence决定。

高风险示例：

- 多个合理客户
- 高金额异常
- 修改已提交业务
- 关键数据缺失

处理中风险时，尽量生成可编辑草稿，而不是强制确认全部字段。

------

## 10. 状态机

一次AI请求：

RECEIVED
→ ASR（语音时）
→ UNDERSTANDING
→ ENTITY_EXTRACTION
→ RESOLUTION
→ BUSINESS_CONTEXT
→ RISK_CHECK
→ ACTION_GENERATION
→ RETURN_TO_BUSINESS

异常：

NEED_USER_INPUT
FAILED
CANCELLED

AI Service本身不进入“ORDER_COMPLETED”，因为订单完成属于业务系统。

------

## 11. Feedback

记录：

- 原始输入
- ASR文本
- AI提取
- Resolver候选
- 用户最终选择
- 用户修改字段
- 最终ERPNext结果

这些数据将比“模型自我评分”更有价值。

------

## 12. 最终原则

> AI应该尽可能完成已经确定的部分，只把真正不确定的部分交给老板。
