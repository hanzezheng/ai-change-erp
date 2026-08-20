# AI Service（Phase 5）

Python FastAPI 内部服务。只负责理解与结构化 Action，**不写 ERPNext**。

对外仅由 Spring Boot 调用：

- `POST /internal/ai/parse-action`
- `POST /internal/ai/speech/transcribe`
- `GET /health`

公开入口仍是 Flutter → `POST /api/v1/ai/actions`（Spring）。

## 本地启动

```bash
cd ai-service
python3 -m venv .venv
source .venv/bin/activate   # Windows: .venv\Scripts\activate
pip install -r requirements.txt
uvicorn app.main:app --host 0.0.0.0 --port 8090 --reload
```

Spring 侧配置：

```bash
export AI_SERVICE_BASE_URL=http://127.0.0.1:8090
```

## 当前能力（V0）

- Model Gateway 抽象 + StubProvider（无真实 LLM Key 也可跑）
- 农批黄金句启发式解析：`create_order` / `update_current_order`
- ASR transcribe Stub（返回占位说明；后续接真实 Provider）

正式 LLM / ASR Provider 通过 Gateway 接入，业务代码不绑定单一厂商。
