import 'package:flutter/material.dart';
import 'package:flutter_riverpod/flutter_riverpod.dart';
import 'package:speech_to_text/speech_to_text.dart';

import '../../../core/api/api_exception.dart';
import '../../feature_providers.dart';
import '../data/ai_models.dart';
import 'ai_action_handler.dart';
import 'quick_action_sheet.dart';

/// 全局语音会话：长按直录 → 设备 ASR 出文本 → 仍走 Spring `/ai/actions`。
/// 服务端文件 ASR 后续接入；当前不直连 Python AI Service。
class VoiceSession {
  VoiceSession();

  final SpeechToText _speech = SpeechToText();
  var _ready = false;
  var _listening = false;
  String _buffer = '';

  bool get isListening => _listening;

  Future<bool> ensureReady() async {
    if (_ready) {
      return true;
    }
    _ready = await _speech.initialize(
      onError: (_) {},
      onStatus: (_) {},
    );
    return _ready;
  }

  Future<void> beginHold(BuildContext context) async {
    _buffer = '';
    final ok = await ensureReady();
    if (!ok) {
      if (context.mounted) {
        ScaffoldMessenger.of(context).showSnackBar(
          const SnackBar(content: Text('无法使用麦克风，请短按用文字指令')),
        );
      }
      return;
    }
    _listening = true;
    if (context.mounted) {
      ScaffoldMessenger.of(context).showSnackBar(
        const SnackBar(
          content: Text('正在听…松开结束'),
          duration: Duration(seconds: 60),
        ),
      );
    }
    await _speech.listen(
      onResult: (result) {
        _buffer = result.recognizedWords;
      },
      listenMode: ListenMode.dictation,
      partialResults: true,
      localeId: 'zh_CN',
      cancelOnError: true,
    );
  }

  Future<void> endHold(BuildContext context, WidgetRef ref) async {
    if (!_listening) {
      return;
    }
    _listening = false;
    await _speech.stop();
    if (context.mounted) {
      ScaffoldMessenger.of(context).hideCurrentSnackBar();
    }
    final text = _buffer.trim();
    _buffer = '';
    if (!context.mounted) {
      return;
    }
    if (text.isEmpty) {
      ScaffoldMessenger.of(context).showSnackBar(
        const SnackBar(content: Text('未识别到内容，已打开文字快捷操作')),
      );
      await showQuickActionSheet(context);
      return;
    }

    final bridge = ref.read(aiDraftBridgeProvider);
    final draft = bridge.controller?.state;
    final aiContext = draft == null
        ? const AiActionContext(currentPage: 'HOME', currentItems: [])
        : AiActionContext(
            currentPage: 'ORDER_EDIT',
            currentOrderId: draft.orderId,
            currentCustomerId: draft.customerId,
            currentCustomerName: draft.customerName,
            currentItems: draft.items
                .map(
                  (e) => AiContextItem(
                    itemCode: e.itemCode,
                    productId: e.productId,
                    productName: e.productName,
                    spec: e.spec,
                    qty: e.qty,
                    uom: e.uom,
                    rate: e.rate,
                  ),
                )
                .toList(),
          );

    try {
      final response = await ref.read(aiRepositoryProvider).createAction(
            AiActionRequest(
              inputType: 'VOICE',
              text: text,
              asrText: text,
              context: aiContext,
            ),
          );
      if (!context.mounted) {
        return;
      }
      await handleAiActionResult(context, ref, response);
    } on ApiException catch (e) {
      if (context.mounted) {
        ScaffoldMessenger.of(context).showSnackBar(
          SnackBar(content: Text(e.userMessage)),
        );
      }
    } catch (_) {
      if (context.mounted) {
        ScaffoldMessenger.of(context).showSnackBar(
          const SnackBar(content: Text('语音处理失败，请改用文字')),
        );
      }
    }
  }
}

final voiceSessionProvider = Provider<VoiceSession>((ref) => VoiceSession());
