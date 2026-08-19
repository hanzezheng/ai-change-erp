import '../../../core/api/api_client.dart';
import '../../../core/api/page_result.dart';
import 'inventory_models.dart';

class InventoryRepository {
  InventoryRepository(this._api);

  final ApiClient _api;

  Future<PageResult<InventoryItem>> list({
    String? q,
    bool lowStock = false,
    String? warehouseId,
    int page = 1,
    int pageSize = 20,
  }) {
    return _api.getJson(
      '/api/v1/inventory',
      queryParameters: {
        'q': q,
        'lowStock': lowStock,
        'warehouseId': warehouseId,
        'page': page,
        'pageSize': pageSize,
      },
      parse: (json) => PageResult.fromJson(
        Map<String, dynamic>.from(json as Map),
        InventoryItem.fromJson,
      ),
    );
  }
}
