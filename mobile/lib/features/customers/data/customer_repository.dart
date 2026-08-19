import '../../../core/api/api_client.dart';
import '../../../core/api/page_result.dart';
import 'customer_models.dart';

class CustomerRepository {
  CustomerRepository(this._api);

  final ApiClient _api;

  Future<PageResult<CustomerSummary>> list({
    String? q,
    int page = 1,
    int pageSize = 20,
  }) {
    return _api.getJson(
      '/api/v1/customers',
      queryParameters: {'q': q, 'page': page, 'pageSize': pageSize},
      parse: (json) => PageResult.fromJson(
        Map<String, dynamic>.from(json as Map),
        CustomerSummary.fromJson,
      ),
    );
  }

  Future<CustomerSelectorResult> selector({String? q}) {
    return _api.getJson(
      '/api/v1/customers/selector',
      queryParameters: {'q': q},
      parse: (json) => CustomerSelectorResult.fromJson(Map<String, dynamic>.from(json as Map)),
    );
  }

  Future<CustomerSummary> getById(String customerId) {
    return _api.getJson(
      '/api/v1/customers/${Uri.encodeComponent(customerId)}',
      parse: (json) => CustomerSummary.fromJson(Map<String, dynamic>.from(json as Map)),
    );
  }
}
