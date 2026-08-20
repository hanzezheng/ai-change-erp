import 'package:decimal/decimal.dart';

import '../../../core/api/api_client.dart';
import '../../../core/api/page_result.dart';
import '../../../core/utils/decimal_json.dart';
import 'payment_models.dart';

class PaymentRepository {
  PaymentRepository(this._api);

  final ApiClient _api;

  /// Payment methods are tenant-scoped ERP data.  Do not cache them in the
  /// process-wide repository: the provider lives for the whole app session and
  /// can outlive a logout/login or tenant switch.
  Future<List<PaymentMethod>> methods({bool force = false}) async {
    final result = await _api.getJson(
      '/api/v1/payment-methods',
      parse: (json) {
        if (json is! List) {
          return <PaymentMethod>[];
        }
        return json
            .whereType<Map>()
            .map(
              (item) => PaymentMethod.fromJson(Map<String, dynamic>.from(item)),
            )
            .toList();
      },
    );
    return result;
  }

  Future<PageResult<Payment>> list({
    required String relatedOrderId,
    int page = 1,
    int pageSize = 20,
  }) {
    return _api.getJson(
      '/api/v1/payments',
      queryParameters: {
        'relatedOrderId': relatedOrderId,
        'page': page,
        'pageSize': pageSize,
      },
      parse: (json) => PageResult.fromJson(
        Map<String, dynamic>.from(json as Map),
        Payment.fromJson,
      ),
    );
  }

  Future<Payment> createDraft({
    required String customerId,
    required String relatedOrderId,
    required Decimal amount,
    required String paymentMethodId,
    required String idempotencyKey,
  }) async {
    final response = await _api.post(
      '/api/v1/payments',
      data: {
        'customerId': customerId,
        'relatedOrderId': relatedOrderId,
        'amount': decimalToJson(amount),
        'paymentMethodId': paymentMethodId,
      },
      headers: {'Idempotency-Key': idempotencyKey},
    );
    return Payment.fromJson(Map<String, dynamic>.from(response.data as Map));
  }

  Future<Payment> confirm(String paymentId) async {
    final response = await _api.post(
      '/api/v1/payments/${Uri.encodeComponent(paymentId)}/confirm',
    );
    return Payment.fromJson(Map<String, dynamic>.from(response.data as Map));
  }
}
