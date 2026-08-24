from app.asr.transcriber import transcribe_audio


def test_stub_empty_by_default(monkeypatch):
    monkeypatch.setattr("app.asr.transcriber.settings.asr_dev_fixed_text", "")
    monkeypatch.setattr("app.asr.transcriber.settings.asr_provider", "stub")
    resp = transcribe_audio()
    assert resp.text == ""
    assert resp.provider == "stub"


def test_dev_fixed_text(monkeypatch):
    monkeypatch.setattr("app.asr.transcriber.settings.asr_dev_fixed_text", "老韩80果20箱")
    monkeypatch.setattr("app.asr.transcriber.settings.asr_provider", "stub")
    resp = transcribe_audio(filename="x.webm")
    assert resp.text == "老韩80果20箱"
    assert "dev_fixed" in resp.provider
