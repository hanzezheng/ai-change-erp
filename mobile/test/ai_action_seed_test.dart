import 'package:assistant/core/api/api_client.dart';
import 'package:assistant/core/auth/secure_token_store.dart';
import 'package:assistant/features/ai/data/ai_models.dart';
import 'package:assistant/features/orders/data/order_repository.dart';
import 'package:assistant/features/orders/presentation/order_edit_controller.dart';
import 'package:assistant/features/orders/presentation/order_edit_page.dart';
import 'package:decimal/decimal.dart';
import 'package:dio/dio.dart';
import 'package:flutter_test/flutter_test.dart';

import 'helpers/scripted_adapter.dart';

void main() {
  test('OrderEditSeed.fromAiPayload 解析客户与商品行', () {
    final seed = OrderEditSeed.fromAiPayload({
      'customer': {'customerId': 'C1', 'customerName': '韩兆亮'},
      'items': [
        {
          'itemCode': 'APPLE-80',
          'productId': 'APPLE',
          'productName': '苹果80果',
          'spec': '80果',
          'qty': 20,
          'uom': '箱',
          'rate': 68,
        },
      ],
    });
    expect(seed.customerId, 'C1');
    expect(seed.customerName, '韩兆亮');
    expect(seed.items, hasLength(1));
    expect(seed.items.first.itemCode, 'APPLE-80');
    expect(seed.items.first.qty, Decimal.parse('20'));
  });

  test('startNew 可预填 AI 商品行', () {
    final store = MemoryTokenStore();
    final options = BaseOptions(baseUrl: 'http://api.test');
    final adapter = ScriptedAdapter((_) async => ScriptedHttp(200, {}));
    final client = ApiClient(
      baseUrl: 'http://api.test',
      tokenStore: store,
      dio: Dio(options)..httpClientAdapter = adapter,
      refreshDio: Dio(options)..httpClientAdapter = adapter,
    );
    final controller = OrderEditController(
      orders: OrderRepository(client),
      loadLastDeal: ({required customerId, required itemCode, required uom}) async => null,
      keyFactory: () => 'k1',
    );
    controller.startNew(
      customerId: 'C1',
      customerName: '韩兆亮',
      items: [
        LocalOrderItem(
          productId: 'APPLE',
          itemCode: 'APPLE-80',
          productName: '苹果80果',
          qty: Decimal.parse('20'),
          uom: '箱',
          rate: Decimal.parse('68'),
        ),
      ],
    );
    expect(controller.state.customerId, 'C1');
    expect(controller.state.items.single.itemCode, 'APPLE-80');
    expect(controller.state.dirty, isTrue);
  });

  test('AiActionResponse 解析 NEED_USER_INPUT', () {
    final resp = AiActionResponse.fromJson({
      'actionId': 'a1',
      'actionType': 'CREATE_ORDER',
      'status': 'NEED_USER_INPUT',
      'ambiguities': [
        {
          'field': 'customer',
          'expression': '老韩',
          'candidates': [
            {'customerId': 'C1', 'name': '韩兆亮'},
            {'customerId': 'C2', 'name': '韩兆良'},
          ],
        },
      ],
      'payload': {},
    });
    expect(resp.status, 'NEED_USER_INPUT');
    expect(resp.ambiguities.single.candidates, hasLength(2));
  });
}
