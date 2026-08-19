import '../../../core/api/api_client.dart';
import 'product_models.dart';

class ProductRepository {
  ProductRepository(this._api);

  final ApiClient _api;

  Future<ProductSelectorResult> selector({String? q, String? customerId}) {
    return _api.getJson(
      '/api/v1/products/selector',
      queryParameters: {'q': q, 'customerId': customerId},
      parse: (json) => ProductSelectorResult.fromJson(Map<String, dynamic>.from(json as Map)),
    );
  }

  Future<LastDealPrice?> lastDeal({
    required String customerId,
    required String itemCode,
    required String uom,
  }) async {
    try {
      return await _api.getJson(
        '/api/v1/pricing/last-deal',
        queryParameters: {
          'customerId': customerId,
          'itemCode': itemCode,
          'uom': uom,
        },
        parse: (json) {
          if (json == null || json is! Map) {
            return null;
          }
          final map = Map<String, dynamic>.from(json);
          if (map['price'] == null) {
            return null;
          }
          return LastDealPrice.fromJson(map);
        },
      );
    } catch (_) {
      return null;
    }
  }
}
