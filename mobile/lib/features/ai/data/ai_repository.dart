import '../../../core/api/api_client.dart';
import 'ai_models.dart';

class AiRepository {
  AiRepository(this._api);

  final ApiClient _api;

  Future<AiActionResponse> createAction(AiActionRequest request) async {
    final response = await _api.post('/api/v1/ai/actions', data: request.toJson());
    return AiActionResponse.fromJson(Map<String, dynamic>.from(response.data as Map));
  }
}
