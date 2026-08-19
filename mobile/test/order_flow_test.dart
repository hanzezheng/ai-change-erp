import 'package:assistant/core/api/api_client.dart';
import 'package:assistant/core/auth/secure_token_store.dart';
import 'package:assistant/features/customers/data/customer_models.dart';
import 'package:assistant/features/orders/data/order_models.dart';
import 'package:assistant/features/orders/data/order_repository.dart';
import 'package:assistant/features/orders/presentation/order_edit_controller.dart';
import 'package:assistant/features/products/data/product_models.dart';
import 'package:decimal/decimal.dart';
import 'package:dio/dio.dart';
import 'package:flutter_test/flutter_test.dart';

import 'helpers/scripted_adapter.dart';

ApiClient client(ScriptedAdapter adapter, MemoryTokenStore store) {
  final options = BaseOptions(baseUrl: 'http://api.test');
  return ApiClient(
    baseUrl: 'http://api.test',
    tokenStore: store,
    dio: Dio(options)..httpClientAdapter = adapter,
    refreshDio: Dio(options)..httpClientAdapter = adapter,
  );
}

Map<String, dynamic> orderJson({
  String id = 'SAL-ORD-001',
  String status = 'DRAFT',
  String qty = '20',
  String? itemId,
}) {
  return {
    'orderId': id,
    'customerId': 'CUST-HAN',
    'customerName': '韩兆亮',
    'transactionDate': '2026-08-19',
    'items': [
      {
        'orderItemId': itemId ?? 'row-1',
        'productId': 'APPLE',
        'itemCode': 'APPLE-80',
        'productName': '苹果80果',
        'spec': '80果',
        'qty': qty,
        'uom': '箱',
        'rate': '68',
        'amount': '1360',
      },
    ],
    'orderStatus': status,
    'orderStatusLabel': status == 'DRAFT' ? '草稿' : '已提交',
    'paymentStatus': 'UNPAID',
    'paymentStatusLabel': '未收款',
    'totalAmount': '1360',
    'confirmedPaid': '0',
    'remainingToCollect': '1360',
    'currency': 'CNY',
    'createdAt': '2026-08-19T02:00:00Z',
    'updatedAt': '2026-08-19T02:00:00Z',
  };
}

void main() {
  late MemoryTokenStore store;

  setUp(() {
    store = MemoryTokenStore();
  });

  test('new order starts local only and does not POST', () async {
    var posted = false;
    final adapter = ScriptedAdapter((options) async {
      posted = true;
      return ScriptedHttp(500, {'code': 'INTERNAL_ERROR', 'message': 'no'});
    });
    final controller = OrderEditController(
      orders: OrderRepository(client(adapter, store)),
      loadLastDeal:
          ({required customerId, required itemCode, required uom}) async =>
              null,
    );
    controller.startNew();
    expect(controller.state.isNew, isTrue);
    expect(posted, isFalse);
  });

  test('select customer binds customerId', () {
    final controller = OrderEditController(
      orders: OrderRepository(
        client(ScriptedAdapter((_) async => ScriptedHttp(200, {})), store),
      ),
      loadLastDeal:
          ({required customerId, required itemCode, required uom}) async =>
              null,
    );
    controller.startNew();
    controller.selectCustomer(
      const CustomerSummary(customerId: 'CUST-HAN', customerName: '韩兆亮'),
    );
    expect(controller.state.customerId, 'CUST-HAN');
    expect(controller.state.customerName, '韩兆亮');
  });

  test('add product binds itemCode and single UOM stays the given uom', () {
    final controller = OrderEditController(
      orders: OrderRepository(
        client(ScriptedAdapter((_) async => ScriptedHttp(200, {})), store),
      ),
      loadLastDeal:
          ({required customerId, required itemCode, required uom}) async =>
              null,
    );
    controller.startNew();
    controller.addOrReplaceItem(
      LocalOrderItem(
        productId: 'APPLE',
        itemCode: 'APPLE-80',
        productName: '苹果80果',
        spec: '80果',
        qty: Decimal.parse('20'),
        uom: '箱',
        rate: Decimal.parse('68'),
        allowedUoms: const [AllowedUom(uom: '箱')],
      ),
    );
    expect(controller.state.items.single.itemCode, 'APPLE-80');
    expect(controller.state.items.single.uom, '箱');
  });

  test('invalid line prevents save and is not dropped', () async {
    final controller = OrderEditController(
      orders: OrderRepository(
        client(
          ScriptedAdapter((_) async => ScriptedHttp(200, orderJson())),
          store,
        ),
      ),
      loadLastDeal:
          ({required customerId, required itemCode, required uom}) async =>
              null,
    );
    controller.startNew();
    controller.selectCustomer(
      const CustomerSummary(customerId: 'CUST-HAN', customerName: '韩兆亮'),
    );
    controller.addOrReplaceItem(
      LocalOrderItem(
        productId: 'APPLE',
        itemCode: 'APPLE-80',
        productName: '苹果80果',
        uom: '箱',
      ),
    );
    final saved = await controller.saveDraft();
    expect(saved, isNull);
    expect(controller.state.items, hasLength(1));
    expect(controller.state.items.single.lineError, isNotNull);
  });

  test(
    'create draft sends Idempotency-Key and retries keep the same key',
    () async {
      final keys = <String>[];
      final adapter = ScriptedAdapter((options) async {
        keys.add(options.headers['Idempotency-Key']?.toString() ?? '');
        return ScriptedHttp(200, orderJson());
      });
      final controller = OrderEditController(
        orders: OrderRepository(client(adapter, store)),
        loadLastDeal:
            ({required customerId, required itemCode, required uom}) async =>
                null,
        keyFactory: () => 'fixed-key',
      );
      controller.startNew();
      controller.selectCustomer(
        const CustomerSummary(customerId: 'CUST-HAN', customerName: '韩兆亮'),
      );
      controller.addOrReplaceItem(
        LocalOrderItem(
          productId: 'APPLE',
          itemCode: 'APPLE-80',
          productName: '苹果80果',
          qty: Decimal.parse('20'),
          uom: '箱',
          rate: Decimal.parse('68'),
        ),
      );
      await controller.saveDraft();
      expect(controller.state.orderId, 'SAL-ORD-001');
      expect(controller.state.isNew, isFalse);
      controller.state.dirty = true;
      await controller.saveDraft();
      expect(keys.first, 'fixed-key');
    },
  );

  test('update sends expectedModifiedAt', () async {
    DateTime? sent;
    final adapter = ScriptedAdapter((options) async {
      if (options.method == 'PUT') {
        sent = DateTime.parse(
          (options.data as Map)['expectedModifiedAt'] as String,
        );
        return ScriptedHttp(200, orderJson(qty: '30'));
      }
      return ScriptedHttp(200, orderJson());
    });
    final controller = OrderEditController(
      orders: OrderRepository(client(adapter, store)),
      loadLastDeal:
          ({required customerId, required itemCode, required uom}) async =>
              null,
    );
    controller.loadExisting(Order.fromJson(orderJson()));
    controller.state.dirty = true;
    controller.state.items.first.qty = Decimal.parse('30');
    await controller.saveDraft();
    expect(sent, isNotNull);
  });

  test('ORDER_CONFLICT is surfaced without overwrite', () async {
    final adapter = ScriptedAdapter((options) async {
      return ScriptedHttp(409, {
        'code': 'ORDER_CONFLICT',
        'message': '订单已被其他人修改，请刷新后重试',
        'traceId': 'c1',
        'details': {},
      });
    });
    final controller = OrderEditController(
      orders: OrderRepository(client(adapter, store)),
      loadLastDeal:
          ({required customerId, required itemCode, required uom}) async =>
              null,
    );
    controller.loadExisting(Order.fromJson(orderJson()));
    controller.state.dirty = true;
    await controller.saveDraft();
    expect(controller.state.conflict, isTrue);
    expect(controller.state.items.first.qty, Decimal.parse('20'));
  });

  test('new-order submit creates draft then submits same orderId', () async {
    final paths = <String>[];
    final adapter = ScriptedAdapter((options) async {
      paths.add('${options.method} ${options.path}');
      if (options.path.endsWith('/submit')) {
        return ScriptedHttp(200, orderJson(status: 'SUBMITTED'));
      }
      return ScriptedHttp(200, orderJson());
    });
    final controller = OrderEditController(
      orders: OrderRepository(client(adapter, store)),
      loadLastDeal:
          ({required customerId, required itemCode, required uom}) async =>
              null,
    );
    controller.startNew();
    controller.selectCustomer(
      const CustomerSummary(customerId: 'CUST-HAN', customerName: '韩兆亮'),
    );
    controller.addOrReplaceItem(
      LocalOrderItem(
        productId: 'APPLE',
        itemCode: 'APPLE-80',
        productName: '苹果80果',
        qty: Decimal.parse('20'),
        uom: '箱',
        rate: Decimal.parse('68'),
      ),
    );
    final submitted = await controller.submit();
    expect(submitted?.orderStatus, OrderStatus.submitted);
    expect(paths, [
      'POST /api/v1/orders',
      'POST /api/v1/orders/SAL-ORD-001/submit',
    ]);
  });

  test('submit second step failure keeps created draft', () async {
    final adapter = ScriptedAdapter((options) async {
      if (options.path.endsWith('/submit')) {
        return ScriptedHttp(409, {
          'code': 'ORDER_STATUS_INVALID',
          'message': '当前订单状态不允许该操作',
          'traceId': 's1',
          'details': {},
        });
      }
      return ScriptedHttp(200, orderJson());
    });
    final controller = OrderEditController(
      orders: OrderRepository(client(adapter, store)),
      loadLastDeal:
          ({required customerId, required itemCode, required uom}) async =>
              null,
    );
    controller.startNew();
    controller.selectCustomer(
      const CustomerSummary(customerId: 'CUST-HAN', customerName: '韩兆亮'),
    );
    controller.addOrReplaceItem(
      LocalOrderItem(
        productId: 'APPLE',
        itemCode: 'APPLE-80',
        productName: '苹果80果',
        qty: Decimal.parse('20'),
        uom: '箱',
        rate: Decimal.parse('68'),
      ),
    );
    final submitted = await controller.submit();
    expect(submitted, isNull);
    expect(controller.state.orderId, 'SAL-ORD-001');
    expect(controller.state.submitFailedAfterCreate, isTrue);
  });

  test('UOM switch changes last-deal context', () async {
    String? queriedUom;
    final controller = OrderEditController(
      orders: OrderRepository(
        client(ScriptedAdapter((_) async => ScriptedHttp(200, {})), store),
      ),
      loadLastDeal:
          ({required customerId, required itemCode, required uom}) async {
            queriedUom = uom;
            return LastDealPrice(price: Decimal.parse('3.80'), uom: uom);
          },
    );
    controller.startNew();
    controller.selectCustomer(
      const CustomerSummary(customerId: 'CUST-HAN', customerName: '韩兆亮'),
    );
    final item = LocalOrderItem(
      productId: 'APPLE',
      itemCode: 'APPLE-80',
      productName: '苹果80果',
      uom: '箱',
      rate: Decimal.parse('68'),
      allowedUoms: [
        AllowedUom(uom: '箱', referencePrice: Decimal.parse('68')),
        AllowedUom(uom: '斤', referencePrice: Decimal.parse('3.8')),
      ],
    );
    controller.addOrReplaceItem(item);
    await controller.applyUom(item, '斤');
    expect(item.uom, '斤');
    expect(item.referencePrice, Decimal.parse('3.8'));
    expect(item.lastDealPrice, Decimal.parse('3.80'));
    expect(queriedUom, '斤');
  });

  test(
    'UOM switch clears an old rate when the new UOM has no reference price',
    () async {
      final controller = OrderEditController(
        orders: OrderRepository(
          client(ScriptedAdapter((_) async => ScriptedHttp(200, {})), store),
        ),
        loadLastDeal:
            ({required customerId, required itemCode, required uom}) async =>
                LastDealPrice(price: Decimal.parse('3.80'), uom: uom),
      );
      controller.startNew();
      controller.selectCustomer(
        const CustomerSummary(customerId: 'CUST-HAN', customerName: '韩兆亮'),
      );
      final item = LocalOrderItem(
        productId: 'APPLE',
        itemCode: 'APPLE-80',
        productName: '苹果80果',
        uom: '箱',
        rate: Decimal.parse('68'),
        allowedUoms: [
          AllowedUom(uom: '箱', referencePrice: Decimal.parse('68')),
          AllowedUom(uom: '斤'),
        ],
      );

      await controller.applyUom(item, '斤');

      expect(item.referencePrice, isNull);
      expect(item.rate, isNull);
      expect(item.lastDealPrice, Decimal.parse('3.80'));
    },
  );

  test('list search uses server q', () async {
    String? q;
    final adapter = ScriptedAdapter((options) async {
      q = options.queryParameters['q']?.toString();
      return ScriptedHttp(200, {
        'content': [],
        'page': 1,
        'pageSize': 20,
        'hasMore': false,
      });
    });
    await OrderRepository(client(adapter, store)).list(q: '韩兆亮');
    expect(q, '韩兆亮');
  });

  test('idempotency unknown does not auto retry', () async {
    var posts = 0;
    final adapter = ScriptedAdapter((options) async {
      posts += 1;
      return ScriptedHttp(409, {
        'code': 'IDEMPOTENCY_OUTCOME_UNKNOWN',
        'message': '上次写入结果未知，禁止自动重试',
        'traceId': 'u1',
        'details': {},
      });
    });
    final controller = OrderEditController(
      orders: OrderRepository(client(adapter, store)),
      loadLastDeal:
          ({required customerId, required itemCode, required uom}) async =>
              null,
    );
    controller.startNew();
    controller.selectCustomer(
      const CustomerSummary(customerId: 'CUST-HAN', customerName: '韩兆亮'),
    );
    controller.addOrReplaceItem(
      LocalOrderItem(
        productId: 'APPLE',
        itemCode: 'APPLE-80',
        productName: '苹果80果',
        qty: Decimal.parse('20'),
        uom: '箱',
        rate: Decimal.parse('68'),
      ),
    );
    await controller.saveDraft();
    await controller.saveDraft();
    expect(posts, 1);
    expect(controller.state.unknownOutcome, isTrue);
  });

  test('submitted order is read-only and will not save', () async {
    var put = false;
    final adapter = ScriptedAdapter((options) async {
      if (options.method == 'PUT' || options.path.endsWith('/submit')) {
        put = true;
      }
      return ScriptedHttp(200, orderJson(status: 'SUBMITTED'));
    });
    final controller = OrderEditController(
      orders: OrderRepository(client(adapter, store)),
      loadLastDeal:
          ({required customerId, required itemCode, required uom}) async =>
              null,
    );
    controller.loadExisting(Order.fromJson(orderJson(status: 'SUBMITTED')));
    expect(controller.state.readOnly, isTrue);
    expect(await controller.saveDraft(), isNull);
    expect(await controller.submit(), isNull);
    expect(put, isFalse);
  });

  test('create then submit failure does not POST a second order', () async {
    var createCount = 0;
    final adapter = ScriptedAdapter((options) async {
      if (options.path.endsWith('/submit')) {
        return ScriptedHttp(503, {
          'code': 'ERP_UNAVAILABLE',
          'message': '经营系统暂时不可用',
          'traceId': 's2',
          'details': {},
        });
      }
      if (options.method == 'POST' && options.path == '/api/v1/orders') {
        createCount += 1;
        return ScriptedHttp(200, orderJson());
      }
      return ScriptedHttp(200, orderJson());
    });
    final controller = OrderEditController(
      orders: OrderRepository(client(adapter, store)),
      loadLastDeal:
          ({required customerId, required itemCode, required uom}) async =>
              null,
    );
    controller.startNew();
    controller.selectCustomer(
      const CustomerSummary(customerId: 'CUST-HAN', customerName: '韩兆亮'),
    );
    controller.addOrReplaceItem(
      LocalOrderItem(
        productId: 'APPLE',
        itemCode: 'APPLE-80',
        productName: '苹果80果',
        qty: Decimal.parse('20'),
        uom: '箱',
        rate: Decimal.parse('68'),
      ),
    );
    await controller.submit();
    await controller.submit();
    expect(createCount, 1);
    expect(controller.state.orderId, 'SAL-ORD-001');
    expect(controller.state.submitFailedAfterCreate, isTrue);
  });
}
