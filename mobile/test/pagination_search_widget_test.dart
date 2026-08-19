import 'package:assistant/core/api/api_client.dart';
import 'package:assistant/core/auth/auth_providers.dart';
import 'package:assistant/core/auth/secure_token_store.dart';
import 'package:assistant/features/customers/presentation/customer_selector_sheet.dart';
import 'package:assistant/features/customers/presentation/customers_page.dart';
import 'package:assistant/features/inventory/presentation/inventory_page.dart';
import 'package:assistant/features/orders/presentation/orders_page.dart';
import 'package:assistant/features/products/presentation/product_selector_sheet.dart';
import 'package:assistant/features/products/presentation/products_page.dart';
import 'package:dio/dio.dart';
import 'package:flutter/material.dart';
import 'package:flutter_riverpod/flutter_riverpod.dart';
import 'package:flutter_test/flutter_test.dart';

import 'helpers/scripted_adapter.dart';

ApiClient _client(ScriptedAdapter adapter) {
  final store = MemoryTokenStore();
  final options = BaseOptions(baseUrl: 'http://api.test');
  return ApiClient(
    baseUrl: 'http://api.test',
    tokenStore: store,
    dio: Dio(options)..httpClientAdapter = adapter,
    refreshDio: Dio(options)..httpClientAdapter = adapter,
  );
}

Future<void> _pumpPage(
  WidgetTester tester,
  ApiClient client,
  Widget child,
) async {
  await tester.pumpWidget(
    ProviderScope(
      overrides: [apiClientProvider.overrideWithValue(client)],
      // Some production pages are embedded in ShellScaffold and therefore
      // intentionally return their body without another Scaffold.  Give the
      // isolated widget tests the same Material/bounded-body environment.
      child: MaterialApp(home: Scaffold(body: child)),
    ),
  );
  await tester.pump();
  await tester.pump(const Duration(milliseconds: 20));
  await tester.pumpAndSettle();
}

Map<String, dynamic> _page(
  List<Map<String, dynamic>> content, {
  required int page,
  required bool hasMore,
}) {
  return {'content': content, 'page': page, 'pageSize': 20, 'hasMore': hasMore};
}

Map<String, dynamic> _order(String id, {String paymentStatus = 'UNPAID'}) {
  return {
    'orderId': id,
    'customerId': 'C-$id',
    'customerName': id,
    'itemSummary': '苹果80果',
    'itemCount': 1,
    'totalAmount': '100',
    'orderStatus': 'SUBMITTED',
    'paymentStatus': paymentStatus,
    'transactionTime': '2026-08-19T02:00:00Z',
  };
}

Map<String, dynamic> _inventory(String id) {
  return {
    'productId': id,
    'itemCode': id,
    'productName': id,
    'quantity': '20',
    'stockUom': '箱',
    'warehouse': 'Stores',
  };
}

Map<String, dynamic> _customer(String id) {
  return {'customerId': id, 'customerName': id, 'aliases': <String>[]};
}

Map<String, dynamic> _product(String id) {
  return {
    'productId': id,
    'itemCode': id,
    'productName': id,
    'defaultUom': '箱',
    'allowedUoms': [
      {'uom': '箱', 'referencePrice': '68'},
    ],
    'referencePrice': '68',
    'priceUom': '箱',
  };
}

void main() {
  testWidgets(
    'Orders load-more failure keeps cursor and retries the same page',
    (tester) async {
      final pages = <int>[];
      var page2Attempts = 0;
      final adapter = ScriptedAdapter((options) async {
        if (options.path != '/api/v1/orders') {
          return ScriptedHttp(200, _page(const [], page: 1, hasMore: false));
        }
        final page = int.parse(options.queryParameters['page'].toString());
        pages.add(page);
        if (page == 1) {
          return ScriptedHttp(
            200,
            _page(
              List.generate(30, (index) => _order('ORDER-$index')),
              page: 1,
              hasMore: true,
            ),
          );
        }
        page2Attempts++;
        if (page2Attempts == 1) {
          return ScriptedHttp(503, {
            'code': 'ERP_UNAVAILABLE',
            'message': 'page failed',
          });
        }
        return ScriptedHttp(
          200,
          _page([_order('ORDER-31')], page: 2, hasMore: false),
        );
      });
      await _pumpPage(tester, _client(adapter), const OrdersPage());

      final list = find.byType(ListView).last;
      await tester.drag(list, const Offset(0, -3000));
      await tester.pump();
      await tester.pump(const Duration(milliseconds: 50));
      await tester.pumpAndSettle();
      // A retry button is rendered at the bottom after the first failure.  Tap
      // it when it is present; a near-bottom notification may also retry it.
      final retry = find.text('更多订单加载失败');
      if (retry.evaluate().isNotEmpty) {
        await tester.tap(retry);
        await tester.pump();
        await tester.pump(const Duration(milliseconds: 50));
        await tester.pumpAndSettle();
      }

      expect(pages, contains(1));
      expect(pages.where((page) => page == 2).length, greaterThanOrEqualTo(2));
      expect(pages, isNot(contains(3)));
      expect(find.text('ORDER-31'), findsOneWidget);
    },
  );

  testWidgets(
    'collect-mode skips paid backend pages until an unpaid order exists',
    (tester) async {
      final pages = <int>[];
      final adapter = ScriptedAdapter((options) async {
        final page = int.parse(options.queryParameters['page'].toString());
        pages.add(page);
        if (page == 1) {
          return ScriptedHttp(
            200,
            _page(
              [_order('PAID-1', paymentStatus: 'PAID')],
              page: 1,
              hasMore: true,
            ),
          );
        }
        return ScriptedHttp(
          200,
          _page([_order('UNPAID-2')], page: 2, hasMore: false),
        );
      });
      await _pumpPage(
        tester,
        _client(adapter),
        const OrdersPage(collectMode: true),
      );

      expect(pages, [1, 2]);
      expect(find.text('UNPAID-2'), findsOneWidget);
      expect(find.text('暂无待收款订单'), findsNothing);
    },
  );

  testWidgets(
    'collect-mode shows empty only after every backend page is paid',
    (tester) async {
      final pages = <int>[];
      final adapter = ScriptedAdapter((options) async {
        final page = int.parse(options.queryParameters['page'].toString());
        pages.add(page);
        return ScriptedHttp(
          200,
          _page(
            [_order('PAID-$page', paymentStatus: 'PAID')],
            page: page,
            hasMore: page == 1,
          ),
        );
      });
      await _pumpPage(
        tester,
        _client(adapter),
        const OrdersPage(collectMode: true),
      );

      expect(pages, [1, 2]);
      expect(find.text('暂无待收款订单'), findsOneWidget);
    },
  );

  testWidgets(
    'Inventory loads the second page and does not skip it after failure',
    (tester) async {
      final pages = <int>[];
      var page2Attempts = 0;
      final adapter = ScriptedAdapter((options) async {
        final page = int.parse(options.queryParameters['page'].toString());
        pages.add(page);
        if (page == 1) {
          return ScriptedHttp(
            200,
            _page(
              List.generate(30, (index) => _inventory('ITEM-$index')),
              page: 1,
              hasMore: true,
            ),
          );
        }
        page2Attempts++;
        if (page2Attempts == 1) {
          return ScriptedHttp(503, {
            'code': 'ERP_UNAVAILABLE',
            'message': 'page failed',
          });
        }
        return ScriptedHttp(
          200,
          _page([_inventory('ITEM-31')], page: 2, hasMore: false),
        );
      });
      await _pumpPage(tester, _client(adapter), const InventoryPage());

      final list = find.byType(ListView).last;
      await tester.drag(list, const Offset(0, -3000));
      await tester.pump();
      await tester.pump(const Duration(milliseconds: 50));
      await tester.pumpAndSettle();
      final retry = find.text('更多库存加载失败');
      if (retry.evaluate().isNotEmpty) {
        await tester.tap(retry);
        await tester.pump();
        await tester.pump(const Duration(milliseconds: 50));
        await tester.pumpAndSettle();
      }

      expect(pages.where((page) => page == 2).length, greaterThanOrEqualTo(2));
      expect(pages, isNot(contains(3)));
      expect(find.text('ITEM-31'), findsOneWidget);
    },
  );

  testWidgets('Customer search ignores a late response for an older query', (
    tester,
  ) async {
    final adapter = ScriptedAdapter((options) async {
      final query = options.queryParameters['q']?.toString() ?? '';
      if (query == 'A') {
        return ScriptedHttp(
          200,
          _page([_customer('A客户')], page: 1, hasMore: false),
          delay: const Duration(milliseconds: 500),
        );
      }
      if (query == 'B') {
        return ScriptedHttp(
          200,
          _page([_customer('B客户')], page: 1, hasMore: false),
          delay: const Duration(milliseconds: 10),
        );
      }
      return ScriptedHttp(200, _page(const [], page: 1, hasMore: false));
    });
    await _pumpPage(tester, _client(adapter), const CustomersPage());
    final search = find.byType(TextField);
    await tester.enterText(search, 'A');
    await tester.pump(const Duration(milliseconds: 450));
    await tester.enterText(search, 'B');
    await tester.pump(const Duration(milliseconds: 450));
    await tester.pump(const Duration(milliseconds: 700));

    expect(find.text('B客户'), findsOneWidget);
    expect(find.text('A客户'), findsNothing);
  });

  testWidgets('Customer selector ignores a late response for an older query', (
    tester,
  ) async {
    final adapter = ScriptedAdapter((options) async {
      final query = options.queryParameters['q']?.toString() ?? '';
      final id = query == 'A'
          ? 'A客户'
          : query == 'B'
          ? 'B客户'
          : '';
      final delay = query == 'A'
          ? const Duration(milliseconds: 500)
          : const Duration(milliseconds: 10);
      return ScriptedHttp(200, {
        'recent': const [],
        'results': id.isEmpty ? const [] : [_customer(id)],
      }, delay: delay);
    });
    await _pumpPage(tester, _client(adapter), const CustomerSelectorSheet());
    final search = find.byType(TextField);
    await tester.enterText(search, 'A');
    await tester.pump(const Duration(milliseconds: 450));
    await tester.enterText(search, 'B');
    await tester.pump(const Duration(milliseconds: 450));
    await tester.pump(const Duration(milliseconds: 700));

    expect(find.text('B客户'), findsOneWidget);
    expect(find.text('A客户'), findsNothing);
  });

  testWidgets('Product selector ignores a late response for an older query', (
    tester,
  ) async {
    final adapter = ScriptedAdapter((options) async {
      final query = options.queryParameters['q']?.toString() ?? '';
      final id = query == 'A'
          ? 'A商品'
          : query == 'B'
          ? 'B商品'
          : '';
      final delay = query == 'A'
          ? const Duration(milliseconds: 500)
          : const Duration(milliseconds: 10);
      return ScriptedHttp(200, {
        'frequentItems': const [],
        'results': id.isEmpty ? const [] : [_product(id)],
      }, delay: delay);
    });
    await _pumpPage(tester, _client(adapter), const ProductSelectorSheet());
    final search = find.byType(TextField);
    await tester.enterText(search, 'A');
    await tester.pump(const Duration(milliseconds: 450));
    await tester.enterText(search, 'B');
    await tester.pump(const Duration(milliseconds: 450));
    await tester.pump(const Duration(milliseconds: 700));

    expect(find.text('B商品'), findsOneWidget);
    expect(find.text('A商品'), findsNothing);
  });

  testWidgets('Product read-only search is debounced', (tester) async {
    final queries = <String>[];
    final adapter = ScriptedAdapter((options) async {
      queries.add(options.queryParameters['q']?.toString() ?? '');
      return ScriptedHttp(200, {
        'frequentItems': const [],
        'results': const [],
      });
    });
    await _pumpPage(tester, _client(adapter), const ProductsPage());
    final search = find.byType(TextField);
    await tester.enterText(search, 'a');
    await tester.pump(const Duration(milliseconds: 100));
    await tester.enterText(search, 'ab');
    await tester.pump(const Duration(milliseconds: 200));
    expect(queries.where((query) => query == 'ab'), isEmpty);
    await tester.pump(const Duration(milliseconds: 250));
    expect(queries.where((query) => query == 'ab'), hasLength(1));
  });
}
