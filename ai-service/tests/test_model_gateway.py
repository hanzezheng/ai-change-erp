import json

import httpx

from app.gateway.model_gateway import OpenAiCompatibleGateway, StubModelGateway


def test_stub_gateway():
    gw = StubModelGateway()
    assert gw.provider_name == "stub"
    assert gw.complete_json(system="s", user="u")["understood"] is False


def test_openai_compatible_parses_json(monkeypatch):
    def handler(request: httpx.Request) -> httpx.Response:
        assert request.url.path.endswith("/chat/completions")
        body = {
            "choices": [
                {
                    "message": {
                        "content": json.dumps({"ok": True, "intent": "create_order"})
                    }
                }
            ]
        }
        return httpx.Response(200, json=body)

    transport = httpx.MockTransport(handler)
    gw = OpenAiCompatibleGateway(
        base_url="https://example.test/v1",
        api_key="sk-test",
        model="demo-model",
    )

    original_client = httpx.Client

    def client_factory(*args, **kwargs):
        kwargs["transport"] = transport
        return original_client(*args, **kwargs)

    monkeypatch.setattr(httpx, "Client", client_factory)
    out = gw.complete_json(system="sys", user="user")
    assert out["ok"] is True
    assert out["intent"] == "create_order"
    assert gw.provider_name == "openai_compatible"
    assert gw.model_name == "demo-model"
