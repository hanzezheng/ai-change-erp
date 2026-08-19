import 'package:assistant/app/theme/app_theme.dart';
import 'package:assistant/core/api/api_client.dart';
import 'package:assistant/core/auth/auth_providers.dart';
import 'package:assistant/core/auth/secure_token_store.dart';
import 'package:assistant/features/orders/presentation/order_detail_page.dart';
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

Map<String, dynamic> _order() {
  return {
    'orderId': 'SO-DETAIL-1',
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
    'paymentStatus': 'PARTIAL',
    'paymentStatusLabel': '部分收款',
    'totalAmount': '1360',
    'confirmedPaid': '1000',
    'remainingToCollect': '360',
    'currency': 'CNY',
    'createdAt': '2026-08-20T00:00:00Z',
    'updatedAt': '2026-08-20T00:00:00Z',
  };
}

Future<void> _pumpDetail(WidgetTester tester, ScriptedAdapter adapter) async {
  final store = MemoryTokenStore();
  await tester.pumpWidget(
    ProviderScope(
      overrides: [
        tokenStoreProvider.overrideWithValue(store),
        apiClientProvider.overrideWithValue(_client(adapter, store)),
      ],
      child: MaterialApp(
        theme: AppTheme.light(),
        home: const OrderDetailPage(orderId: 'SO-DETAIL-1'),
      ),
    ),
  );
  await tester.pumpAndSettle();
}

void main() {
  testWidgets('payment history request error is distinct from empty history',
      (tester) async {
    final adapter = ScriptedAdapter((options) async {
      if (options.path == '/api/v1/orders/SO-DETAIL-1') {
        return ScriptedHttp(200, _order());
      }
      if (options.path == '/api/v1/orders/SO-DETAIL-1/payment-summary') {
        return ScriptedHttp(200, {
          'orderTotal': '1360',
          'confirmedPaid': '1000',
          'remainingToCollect': '360',
          'paymentStatus': 'PARTIAL',
        });
      }
      if (options.path == '/api/v1/payments') {
        return ScriptedHttp(503, {
          'code': 'ERP_UNAVAILABLE',
          'message': '收款记录服务暂时不可用',
          'traceId': 'history-error',
          'details': {},
        });
      }
      return ScriptedHttp(
          500, {'code': 'INTERNAL_ERROR', 'message': 'unexpected'});
    });

    await _pumpDetail(tester, adapter);
    expect(find.text('收款记录加载失败'), findsOneWidget);
    expect(find.text('暂无收款记录'), findsNothing);
    expect(find.text('已收'), findsOneWidget);
    expect(find.text('¥1,000'), findsOneWidget);
  });

  testWidgets('payment history empty is shown only after a successful request',
      (tester) async {
    final adapter = ScriptedAdapter((options) async {
      if (options.path == '/api/v1/orders/SO-DETAIL-1') {
        return ScriptedHttp(200, _order());
      }
      if (options.path == '/api/v1/orders/SO-DETAIL-1/payment-summary') {
        return ScriptedHttp(500, {
          'code': 'ERP_UNAVAILABLE',
          'message': '汇总暂时不可用',
          'traceId': 'summary-error',
          'details': {},
        });
      }
      if (options.path == '/api/v1/payments') {
        return ScriptedHttp(200, {
          'content': [],
          'page': 1,
          'pageSize': 20,
          'hasMore': false,
        });
      }
      return ScriptedHttp(
          500, {'code': 'INTERNAL_ERROR', 'message': 'unexpected'});
    });

    await _pumpDetail(tester, adapter);
    expect(find.text('暂无收款记录'), findsOneWidget);
    expect(find.text('收款记录加载失败'), findsNothing);
    // The order's own payment snapshot remains visible when summary fails.
    expect(find.textContaining('收款汇总加载失败'), findsOneWidget);
    expect(find.text('¥1,000'), findsOneWidget);
  });
}
