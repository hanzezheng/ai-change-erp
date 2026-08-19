import 'package:assistant/core/api/api_client.dart';
import 'package:assistant/core/auth/auth_providers.dart';
import 'package:assistant/core/auth/secure_token_store.dart';
import 'package:assistant/features/payments/presentation/payment_page.dart';
import 'package:assistant/app/theme/app_theme.dart';
import 'package:dio/dio.dart';
import 'package:flutter/material.dart';
import 'package:flutter_riverpod/flutter_riverpod.dart';
import 'package:flutter_test/flutter_test.dart';

import 'helpers/scripted_adapter.dart';

ApiClient _client(ScriptedAdapter adapter, MemoryTokenStore store) {
  final options = BaseOptions(baseUrl: 'http://api.test');
  return ApiClient(
    baseUrl: 'http://api.test',
    tokenStore: store,
    dio: Dio(options)..httpClientAdapter = adapter,
    refreshDio: Dio(options)..httpClientAdapter = adapter,
  );
}

Map<String, dynamic> _orderJson() {
  return {
    'orderId': 'SO-1',
    'customerId': 'CUST-1',
    'customerName': '韩兆亮',
    'transactionDate': '2026-08-20',
    'items': [
      {
        'orderItemId': 'row-1',
        'productId': 'APPLE',
        'itemCode': 'APPLE-80',
        'productName': '苹果80果',
        'spec': '80果',
        'qty': '20',
        'uom': '箱',
        'rate': '68',
        'amount': '1360',
      },
    ],
    'orderStatus': 'SUBMITTED',
    'orderStatusLabel': '已提交',
    'paymentStatus': 'UNPAID',
    'paymentStatusLabel': '未收款',
    'totalAmount': '1360',
    'confirmedPaid': '0',
    'remainingToCollect': '1000',
    'currency': 'CNY',
    'createdAt': '2026-08-20T00:00:00Z',
    'updatedAt': '2026-08-20T00:00:00Z',
  };
}

Map<String, dynamic> _paymentJson({String status = 'PENDING_CONFIRMATION'}) {
  return {
    'paymentId': 'PE-1',
    'customerId': 'CUST-1',
    'customerName': '韩兆亮',
    'relatedOrderId': 'SO-1',
    'amount': '1000',
    'paymentMethodId': 'Cash',
    'paymentMethodName': '现金（正式）',
    'paymentStatus': status,
    'paymentStatusLabel': status == 'CONFIRMED' ? '已到账' : '待确认',
  };
}

void main() {
  testWidgets(
      'payment draft freezes DTO and retries confirm without creating again',
      (tester) async {
    var createCount = 0;
    var confirmCount = 0;
    final store = MemoryTokenStore();
    final adapter = ScriptedAdapter((options) async {
      if (options.path == '/api/v1/orders/SO-1') {
        return ScriptedHttp(200, _orderJson());
      }
      if (options.path == '/api/v1/orders/SO-1/payment-summary') {
        return ScriptedHttp(200, {
          'orderTotal': '1360',
          'confirmedPaid': '0',
          'remainingToCollect': '1000',
          'paymentStatus': 'UNPAID',
          'paymentStatusLabel': '未收款',
        });
      }
      if (options.path == '/api/v1/payment-methods') {
        return ScriptedHttp(200, [
          {'paymentMethodId': 'Cash', 'paymentMethodName': '现金（配置）'},
        ]);
      }
      if (options.path == '/api/v1/payments') {
        createCount += 1;
        return ScriptedHttp(200, _paymentJson());
      }
      if (options.path == '/api/v1/payments/PE-1/confirm') {
        confirmCount += 1;
        return ScriptedHttp(409, {
          'code': 'PAYMENT_INVALID',
          'message': '该收款仍不可确认',
          'traceId': 'pay-confirm-fail',
          'details': {},
        });
      }
      return ScriptedHttp(
          500, {'code': 'INTERNAL_ERROR', 'message': 'unexpected'});
    });
    final client = _client(adapter, store);

    await tester.pumpWidget(
      ProviderScope(
        overrides: [
          tokenStoreProvider.overrideWithValue(store),
          apiClientProvider.overrideWithValue(client),
        ],
        child: MaterialApp(
            theme: AppTheme.light(), home: const PaymentPage(orderId: 'SO-1')),
      ),
    );
    await tester.pumpAndSettle();

    expect(find.byType(TextField), findsOneWidget);
    await tester.tap(find.text('保存收款'));
    await tester.pumpAndSettle();

    expect(createCount, 1);
    expect(confirmCount, 1);
    expect(find.byType(TextField), findsNothing);
    expect(find.text('现金（正式）'), findsOneWidget);
    expect(find.text('¥1,000'), findsOneWidget);
    expect(find.text('PAYMENT_INVALID'), findsNothing);
    expect(find.text('该收款仍不可确认'), findsOneWidget);
    expect(find.text('确认到账'), findsOneWidget);

    await tester.tap(find.text('确认到账'));
    await tester.pumpAndSettle();
    expect(createCount, 1);
    expect(confirmCount, 2);
  });
}
