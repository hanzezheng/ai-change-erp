import 'package:decimal/decimal.dart';
import 'package:dio/dio.dart';

import '../../../core/api/api_client.dart';
import '../../../core/api/page_result.dart';
import '../../../core/utils/datetime_fmt.dart';
import '../../../core/utils/decimal_json.dart';
import 'order_models.dart';

class OrderRepository {
  OrderRepository(this._api);

  final ApiClient _api;

  Future<PageResult<OrderSummary>> list({
    String? q,
    OrderStatus? status,
    DateTime? from,
    DateTime? to,
    int page = 1,
    int pageSize = 20,
    CancelToken? cancelToken,
  }) {
    return _api.getJson(
      '/api/v1/orders',
      queryParameters: {
        'q': q,
        'status': status == null ? null : orderStatusApi(status),
        'from': from == null ? null : BusinessTime.formatDate(from),
        'to': to == null ? null : BusinessTime.formatDate(to),
        'page': page,
        'pageSize': pageSize,
      },
      cancelToken: cancelToken,
      parse: (json) => PageResult.fromJson(
        Map<String, dynamic>.from(json as Map),
        OrderSummary.fromJson,
      ),
    );
  }

  Future<Order> getById(String orderId) {
    return _api.getJson(
      '/api/v1/orders/${Uri.encodeComponent(orderId)}',
      parse: (json) => Order.fromJson(Map<String, dynamic>.from(json as Map)),
    );
  }

  Future<Order> createDraft({
    required String customerId,
    DateTime? transactionDate,
    required List<Map<String, dynamic>> items,
    required String idempotencyKey,
  }) async {
    final body = <String, dynamic>{
      'customerId': customerId,
      'items': items,
    };
    if (transactionDate != null) {
      body['transactionDate'] = BusinessTime.formatDate(transactionDate);
    }
    final response = await _api.post(
      '/api/v1/orders',
      data: body,
      headers: {'Idempotency-Key': idempotencyKey},
    );
    return Order.fromJson(Map<String, dynamic>.from(response.data as Map));
  }

  Future<Order> updateDraft({
    required String orderId,
    required String customerId,
    required DateTime transactionDate,
    required DateTime expectedModifiedAt,
    required List<Map<String, dynamic>> items,
  }) async {
    final response = await _api.put(
      '/api/v1/orders/${Uri.encodeComponent(orderId)}',
      data: {
        'customerId': customerId,
        'transactionDate': BusinessTime.formatDate(transactionDate),
        'expectedModifiedAt': expectedModifiedAt.toUtc().toIso8601String(),
        'items': items,
      },
    );
    return Order.fromJson(Map<String, dynamic>.from(response.data as Map));
  }

  Future<Order> submit(String orderId) async {
    final response = await _api.post('/api/v1/orders/${Uri.encodeComponent(orderId)}/submit');
    return Order.fromJson(Map<String, dynamic>.from(response.data as Map));
  }

  Future<OrderPaymentSummary> paymentSummary(String orderId) {
    return _api.getJson(
      '/api/v1/orders/${Uri.encodeComponent(orderId)}/payment-summary',
      parse: (json) => OrderPaymentSummary.fromJson(Map<String, dynamic>.from(json as Map)),
    );
  }
}

Map<String, dynamic> orderItemPayload({
  String? orderItemId,
  required String itemCode,
  required Decimal qty,
  required String uom,
  required Decimal rate,
}) {
  return {
    if (orderItemId != null && orderItemId.isNotEmpty) 'orderItemId': orderItemId,
    'itemCode': itemCode,
    'qty': decimalToJson(qty),
    'uom': uom,
    'rate': decimalToJson(rate),
  };
}
