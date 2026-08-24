import 'package:assistant/app/app.dart';
import 'package:assistant/core/api/api_client.dart';
import 'package:assistant/core/auth/auth_providers.dart';
import 'package:assistant/core/auth/auth_session.dart';
import 'package:assistant/core/auth/secure_token_store.dart';
import 'package:assistant/features/auth/presentation/login_page.dart';
import 'package:dio/dio.dart';
import 'package:flutter/material.dart';
import 'package:flutter_riverpod/flutter_riverpod.dart';
import 'package:flutter_test/flutter_test.dart';

import 'helpers/scripted_adapter.dart';

ApiClient buildClient(ScriptedAdapter adapter, MemoryTokenStore store) {
  final options = BaseOptions(baseUrl: 'http://api.test');
  return ApiClient(
    baseUrl: 'http://api.test',
    tokenStore: store,
    dio: Dio(options)..httpClientAdapter = adapter,
    refreshDio: Dio(options)..httpClientAdapter = adapter,
  );
}

Future<void> pumpAsync(WidgetTester tester) async {
  await tester.pump();
  await tester.pump(const Duration(milliseconds: 50));
  await tester.pump();
}

void main() {
  testWidgets('登录失败展示后端错误文案而不是 DioException', (tester) async {
    final store = MemoryTokenStore();
    final adapter = ScriptedAdapter((options) async {
      return ScriptedHttp(401, {
        'code': 'AUTHENTICATION_FAILED',
        'message': '登录名或密码不正确',
        'traceId': 't-login',
        'details': {},
      });
    });
    final client = buildClient(adapter, store);
    await tester.pumpWidget(
      ProviderScope(
        overrides: [
          apiBaseUrlProvider.overrideWithValue('http://api.test'),
          tokenStoreProvider.overrideWithValue(store),
          apiClientProvider.overrideWithValue(client),
        ],
        child: const MaterialApp(home: LoginPage()),
      ),
    );
    await tester.enterText(find.byType(TextField).at(0), 'user');
    await tester.enterText(find.byType(TextField).at(1), 'pass');
    await tester.tap(find.text('登录'));
    await pumpAsync(tester);
    expect(find.text('登录名或密码错误'), findsOneWidget);
    expect(find.textContaining('DioException'), findsNothing);
  });

  testWidgets('启动后短按麦克风打开快捷操作（文字入口）', (tester) async {
    final store = MemoryTokenStore();
    await store.saveSession(AuthSession.fromTokenResponse(tokenJson(access: 'old-a', refresh: 'old-r')));
    var refreshCount = 0;
    final adapter = ScriptedAdapter((options) async {
      if (options.path.contains('/auth/refresh')) {
        refreshCount += 1;
        return ScriptedHttp(200, tokenJson(access: 'new-a', refresh: 'new-r'));
      }
      return ScriptedHttp(200, {
        'content': [],
        'page': 1,
        'pageSize': 20,
        'hasMore': false,
      });
    });
    final client = buildClient(adapter, store);
    await tester.pumpWidget(
      ProviderScope(
        overrides: [
          apiBaseUrlProvider.overrideWithValue('http://api.test'),
          tokenStoreProvider.overrideWithValue(store),
          apiClientProvider.overrideWithValue(client),
        ],
        child: const NongpiApp(),
      ),
    );
    await pumpAsync(tester);
    await tester.pump(const Duration(milliseconds: 50));

    expect(refreshCount, 1);
    expect(find.text('首页'), findsWidgets);
    expect(find.byKey(const ValueKey('primary-nav-voice')), findsOneWidget);

    await tester.tap(find.byKey(const ValueKey('primary-nav-voice')));
    await pumpAsync(tester);
    await tester.pump(const Duration(milliseconds: 100));

    expect(find.text('快捷操作'), findsOneWidget);
    expect(find.text('执行'), findsOneWidget);
    expect(find.textContaining('AI'), findsNothing);
  });
}
