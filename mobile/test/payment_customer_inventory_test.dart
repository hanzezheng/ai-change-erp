import 'package:assistant/core/api/api_client.dart';
import 'package:assistant/core/api/api_exception.dart';
import 'package:assistant/core/auth/secure_token_store.dart';
import 'package:assistant/features/customers/data/customer_models.dart';
import 'package:assistant/features/customers/data/customer_repository.dart';
import 'package:assistant/features/inventory/data/inventory_models.dart';
import 'package:assistant/features/inventory/data/inventory_repository.dart';
import 'package:assistant/features/payments/data/payment_repository.dart';
import 'package:decimal/decimal.dart';
import 'package:dio/dio.dart';
import 'package:flutter_test/flutter_test.dart';

import 'helpers/scripted_adapter.dart';

ApiClient client(ScriptedAdapter adapter) {
  final store = MemoryTokenStore();
  final options = BaseOptions(baseUrl: 'http://api.test');
  return ApiClient(
    baseUrl: 'http://api.test',
    tokenStore: store,
    dio: Dio(options)..httpClientAdapter = adapter,
    refreshDio: Dio(options)..httpClientAdapter = adapter,
  );
}

void main() {
  test('payment methods are loaded dynamically', () async {
    final repo = PaymentRepository(client(ScriptedAdapter((options) async {
      expect(options.path, '/api/v1/payment-methods');
      return ScriptedHttp(200, [
        {'paymentMethodId': 'Cash', 'paymentMethodName': 'Cash'},
      ]);
    })));
    final methods = await repo.methods();
    expect(methods.single.paymentMethodName, 'Cash');
  });

  test('payment methods are not reused across tenant/session changes',
      () async {
    var requestCount = 0;
    final repo = PaymentRepository(client(ScriptedAdapter((options) async {
      expect(options.path, '/api/v1/payment-methods');
      requestCount += 1;
      return ScriptedHttp(200, [
        {
          'paymentMethodId': requestCount == 1 ? 'Cash-A' : 'Cash-B',
          'paymentMethodName': requestCount == 1 ? 'A现金' : 'B现金',
        },
      ]);
    })));

    final tenantAMethods = await repo.methods();
    // The provider/repository may survive logout and a subsequent tenant
    // login.  A second call must hit the backend instead of returning A.
    final tenantBMethods = await repo.methods();
    expect(requestCount, 2);
    expect(tenantAMethods.single.paymentMethodId, 'Cash-A');
    expect(tenantBMethods.single.paymentMethodId, 'Cash-B');
  });

  test('create pending payment posts once with idempotency key', () async {
    String? key;
    final repo = PaymentRepository(client(ScriptedAdapter((options) async {
      key = options.headers['Idempotency-Key']?.toString();
      expect(options.path, '/api/v1/payments');
      return ScriptedHttp(200, {
        'paymentId': 'PE-1',
        'customerId': 'CUST-HAN',
        'customerName': '韩兆亮',
        'relatedOrderId': 'SO-1',
        'amount': '1000',
        'paymentMethodId': 'Cash',
        'paymentMethodName': 'Cash',
        'paymentStatus': 'PENDING_CONFIRMATION',
        'paymentStatusLabel': '待确认',
      });
    })));
    final payment = await repo.createDraft(
      customerId: 'CUST-HAN',
      relatedOrderId: 'SO-1',
      amount: Decimal.parse('1000'),
      paymentMethodId: 'Cash',
      idempotencyKey: 'pay-1',
    );
    expect(payment.paymentId, 'PE-1');
    expect(payment.isPending, isTrue);
    expect(key, 'pay-1');
  });

  test('create then confirm uses same payment id', () async {
    final paths = <String>[];
    final repo = PaymentRepository(client(ScriptedAdapter((options) async {
      paths.add(options.path);
      if (options.path.endsWith('/confirm')) {
        return ScriptedHttp(200, {
          'paymentId': 'PE-1',
          'customerId': 'CUST-HAN',
          'customerName': '韩兆亮',
          'relatedOrderId': 'SO-1',
          'amount': '1000',
          'paymentMethodId': 'Cash',
          'paymentMethodName': 'Cash',
          'paymentStatus': 'CONFIRMED',
          'paymentStatusLabel': '已到账',
        });
      }
      return ScriptedHttp(200, {
        'paymentId': 'PE-1',
        'customerId': 'CUST-HAN',
        'customerName': '韩兆亮',
        'relatedOrderId': 'SO-1',
        'amount': '1000',
        'paymentMethodId': 'Cash',
        'paymentMethodName': 'Cash',
        'paymentStatus': 'PENDING_CONFIRMATION',
        'paymentStatusLabel': '待确认',
      });
    })));
    final created = await repo.createDraft(
      customerId: 'CUST-HAN',
      relatedOrderId: 'SO-1',
      amount: Decimal.parse('1000'),
      paymentMethodId: 'Cash',
      idempotencyKey: 'pay-2',
    );
    final confirmed = await repo.confirm(created.paymentId);
    expect(confirmed.paymentId, created.paymentId);
    expect(paths, ['/api/v1/payments', '/api/v1/payments/PE-1/confirm']);
  });

  test('confirm failure keeps draft payment id', () async {
    final repo = PaymentRepository(client(ScriptedAdapter((options) async {
      if (options.path.endsWith('/confirm')) {
        return ScriptedHttp(409, {
          'code': 'ERP_UNAVAILABLE',
          'message': 'ERP 系统暂时不可用',
          'traceId': 'p1',
          'details': {},
        });
      }
      return ScriptedHttp(200, {
        'paymentId': 'PE-9',
        'customerId': 'CUST-HAN',
        'customerName': '韩兆亮',
        'relatedOrderId': 'SO-1',
        'amount': '1000',
        'paymentMethodId': 'Cash',
        'paymentMethodName': 'Cash',
        'paymentStatus': 'PENDING_CONFIRMATION',
        'paymentStatusLabel': '待确认',
      });
    })));
    final created = await repo.createDraft(
      customerId: 'CUST-HAN',
      relatedOrderId: 'SO-1',
      amount: Decimal.parse('1000'),
      paymentMethodId: 'Cash',
      idempotencyKey: 'pay-3',
    );
    expect(created.paymentId, 'PE-9');
    await expectLater(
        repo.confirm(created.paymentId), throwsA(isA<ApiException>()));
  });

  test('payment history requires relatedOrderId', () async {
    String? related;
    final repo = PaymentRepository(client(ScriptedAdapter((options) async {
      related = options.queryParameters['relatedOrderId']?.toString();
      return ScriptedHttp(
          200, {'content': [], 'page': 1, 'pageSize': 20, 'hasMore': false});
    })));
    await repo.list(relatedOrderId: 'SO-1');
    expect(related, 'SO-1');
  });

  test('customer list and selector parse real fields only', () async {
    final json = {
      'customerId': 'CUST-HAN',
      'customerName': '韩兆亮',
      'aliases': ['老韩'],
      'phone': '13800000000',
      'address': '新发地',
    };
    final customer = CustomerSummary.fromJson(json);
    expect(customer.customerId, 'CUST-HAN');
    expect(() => (json as dynamic).receivableAmount, throwsA(anything));

    final repo = CustomerRepository(client(ScriptedAdapter((options) async {
      if (options.path.endsWith('/selector')) {
        return ScriptedHttp(200, {
          'recent': [json],
          'results': [json],
        });
      }
      return ScriptedHttp(200, {
        'content': [json],
        'page': 1,
        'pageSize': 20,
        'hasMore': false,
      });
    })));
    final list = await repo.list(q: '韩');
    expect(list.content.single.customerName, '韩兆亮');
    final selector = await repo.selector(q: '韩');
    expect(selector.recent.single.customerId, 'CUST-HAN');
  });

  test('inventory search is server-side and null lowStock is preserved',
      () async {
    bool? low;
    String? q;
    final repo = InventoryRepository(client(ScriptedAdapter((options) async {
      q = options.queryParameters['q']?.toString();
      low = options.queryParameters['lowStock'] == true ||
          options.queryParameters['lowStock']?.toString() == 'true';
      return ScriptedHttp(200, {
        'content': [
          {
            'productId': 'APPLE',
            'itemCode': 'APPLE-80',
            'productName': '苹果80果',
            'spec': '80果',
            'quantity': '12',
            'stockUom': '箱',
            'warehouse': 'Stores',
            'alertQty': null,
            'lowStock': null,
          }
        ],
        'page': 1,
        'pageSize': 20,
        'hasMore': false,
      });
    })));
    final page = await repo.list(q: '苹果', lowStock: true);
    expect(q, '苹果');
    expect(low, isTrue);
    expect(page.content.single.lowStock, isNull);
  });

  test('payment idempotency unknown does not POST again', () async {
    var posts = 0;
    final repo = PaymentRepository(client(ScriptedAdapter((options) async {
      posts += 1;
      return ScriptedHttp(409, {
        'code': 'IDEMPOTENCY_OUTCOME_UNKNOWN',
        'message': '上次写入结果未知，禁止自动重试',
        'traceId': 'u2',
        'details': {},
      });
    })));
    await expectLater(
      repo.createDraft(
        customerId: 'CUST-HAN',
        relatedOrderId: 'SO-1',
        amount: Decimal.parse('1000'),
        paymentMethodId: 'Cash',
        idempotencyKey: 'pay-unknown',
      ),
      throwsA(isA<ApiException>()
          .having((e) => e.code, 'code', 'IDEMPOTENCY_OUTCOME_UNKNOWN')),
    );
    expect(posts, 1);
  });

  test('inventory null lowStock is not treated as 库存正常', () {
    final item = InventoryItem.fromJson({
      'productId': 'APPLE',
      'itemCode': 'APPLE-80',
      'productName': '苹果80果',
      'quantity': '12',
      'stockUom': '箱',
      'lowStock': null,
    });
    expect(item.lowStock, isNull);
    expect(item.lowStock == true, isFalse);
  });
}
