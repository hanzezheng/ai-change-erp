import 'package:dio/dio.dart';

import '../../../core/api/api_client.dart';
import 'ai_models.dart';

class AiRepository {
  AiRepository(this._api);

  final ApiClient _api;

  Future<AiActionResponse> createAction(AiActionRequest request) async {
    final response = await _api.post('/api/v1/ai/actions', data: request.toJson());
    return AiActionResponse.fromJson(Map<String, dynamic>.from(response.data as Map));
  }

  /// 服务端 ASR（文件上传）。Chrome 长按当前仍优先设备 ASR；本接口供后续录音上传。
  Future<({String text, String? provider})> transcribe(
    List<int> bytes, {
    String filename = 'audio.webm',
  }) async {
    final form = FormData.fromMap({
      'file': MultipartFile.fromBytes(bytes, filename: filename),
    });
    final response = await _api.post(
      '/api/v1/ai/speech/transcribe',
      data: form,
    );
    final map = Map<String, dynamic>.from(response.data as Map);
    return (
      text: map['text']?.toString() ?? '',
      provider: map['provider']?.toString(),
    );
  }
}
