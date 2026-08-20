import 'package:assistant/core/api/api_client.dart';
import 'package:assistant/core/api/api_exception.dart';
import 'package:assistant/core/auth/auth_repository.dart';
import 'package:assistant/core/auth/auth_session.dart';
import 'package:assistant/core/auth/secure_token_store.dart';
import 'package:dio/dio.dart';
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

void main() {
  test('login success saves token pair', () async {
    final store = MemoryTokenStore();
    final adapter = ScriptedAdapter((options) async {
      expect(options.path, '/api/v1/auth/login');
      return ScriptedHttp(200, tokenJson());
    });
    final repo =
        AuthRepository(api: buildClient(adapter, store), tokenStore: store);
    final session = await repo.login(login: 'chen', password: 'secret');
    expect(session.accessToken, 'access-1');
    expect(await store.readRefreshToken(), 'refresh-1');
    expect(store.accessToken, 'access-1');
  });

  test('login failure becomes AUTHENTICATION_FAILED', () async {
    final store = MemoryTokenStore();
    final adapter = ScriptedAdapter((options) async {
      return ScriptedHttp(401, {
        'code': 'AUTHENTICATION_FAILED',
        'message': '登录名或密码不正确',
        'traceId': 't1',
        'details': {},
      });
    });
    final repo =
        AuthRepository(api: buildClient(adapter, store), tokenStore: store);
    await expectLater(
      repo.login(login: 'chen', password: 'bad'),
      throwsA(isA<ApiException>()
          .having((e) => e.code, 'code', 'AUTHENTICATION_FAILED')),
    );
  });

  test('TENANT_SELECTION_REQUIRED exposes tenant list', () async {
    final store = MemoryTokenStore();
    final adapter = ScriptedAdapter((options) async {
      return ScriptedHttp(409, {
        'code': 'TENANT_SELECTION_REQUIRED',
        'message': '请选择要进入的企业',
        'traceId': 't2',
        'details': {
          'tenants': [
            {'tenantId': 'aa', 'tenantName': '档口A', 'role': 'OWNER'},
            {'tenantId': 'bb', 'tenantName': '档口B', 'role': 'STAFF'},
          ],
        },
      });
    });
    final repo =
        AuthRepository(api: buildClient(adapter, store), tokenStore: store);
    try {
      await repo.login(login: 'chen', password: 'secret');
      fail('expected exception');
    } on ApiException catch (error) {
      expect(error.isTenantSelection, isTrue);
      expect(error.tenantOptions, hasLength(2));
      expect(error.tenantOptions.first.tenantName, '档口A');
    }
  });

  test('bootstrap refresh success replaces session', () async {
    final store = MemoryTokenStore();
    await store.saveSession(AuthSession.fromTokenResponse(
        tokenJson(access: 'old-a', refresh: 'old-r')));
    final adapter = ScriptedAdapter((options) async {
      expect(options.path, '/api/v1/auth/refresh');
      expect((options.data as Map)['refreshToken'], 'old-r');
      return ScriptedHttp(200, tokenJson(access: 'new-a', refresh: 'new-r'));
    });
    final repo =
        AuthRepository(api: buildClient(adapter, store), tokenStore: store);
    final session = await repo.refresh();
    expect(session.accessToken, 'new-a');
    expect(await store.readRefreshToken(), 'new-r');
  });

  test('refresh failure clears session', () async {
    final store = MemoryTokenStore();
    await store.saveSession(AuthSession.fromTokenResponse(tokenJson()));
    final adapter = ScriptedAdapter((options) async {
      return ScriptedHttp(401, {
        'code': 'REFRESH_TOKEN_INVALID',
        'message': '刷新令牌无效或已失效',
        'traceId': 't3',
        'details': {},
      });
    });
    final repo =
        AuthRepository(api: buildClient(adapter, store), tokenStore: store);
    await expectLater(repo.refresh(), throwsA(isA<ApiException>()));
    expect(await store.readSession(), isNull);
    expect(store.accessToken, isNull);
  });

  test('concurrent 401 only causes one refresh and rotates tokens', () async {
    final store = MemoryTokenStore();
    await store.saveSession(AuthSession.fromTokenResponse(
        tokenJson(access: 'old-a', refresh: 'old-r')));
    var refreshCount = 0;
    final adapter = ScriptedAdapter((options) async {
      if (options.path.contains('/auth/refresh')) {
        refreshCount += 1;
        expect((options.data as Map)['refreshToken'], 'old-r');
        await Future<void>.delayed(const Duration(milliseconds: 40));
        return ScriptedHttp(200, tokenJson(access: 'new-a', refresh: 'new-r'));
      }
      if ((options.headers['Authorization'] ??
              options.headers['authorization']) ==
          'Bearer old-a') {
        return ScriptedHttp(401, {
          'code': 'TOKEN_EXPIRED',
          'message': '访问令牌已过期',
          'traceId': 't4',
          'details': {},
        });
      }
      return ScriptedHttp(200, {
        'content': [],
        'page': 1,
        'pageSize': 20,
        'hasMore': false,
      });
    });
    final client = buildClient(adapter, store);
    await Future.wait([
      client.get('/api/v1/orders'),
      client.get('/api/v1/customers'),
      client.get('/api/v1/inventory'),
    ]);
    expect(refreshCount, 1);
    expect(store.accessToken, 'new-a');
    expect(await store.readRefreshToken(), 'new-r');
  });

  test('a retried 401 refreshes at most once and clears the session', () async {
    final store = MemoryTokenStore();
    await store.saveSession(AuthSession.fromTokenResponse(
        tokenJson(access: 'old-a', refresh: 'old-r')));
    var refreshCount = 0;
    final adapter = ScriptedAdapter((options) async {
      if (options.path.endsWith('/auth/refresh')) {
        refreshCount += 1;
        return ScriptedHttp(200, tokenJson(access: 'new-a', refresh: 'new-r'));
      }
      return ScriptedHttp(401, {
        'code': 'TOKEN_EXPIRED',
        'message': '访问令牌已过期',
        'traceId': 'retry-401',
        'details': {},
      });
    });
    final client = buildClient(adapter, store);

    await expectLater(
        client.get('/api/v1/orders'), throwsA(isA<ApiException>()));
    expect(refreshCount, 1);
    expect(await store.readSession(), isNull);
  });

  test('logout retry uses the rotated refresh token and clears local session',
      () async {
    final store = MemoryTokenStore();
    await store.saveSession(AuthSession.fromTokenResponse(
        tokenJson(access: 'old-a', refresh: 'old-r')));
    final logoutBodies = <String>[];
    final adapter = ScriptedAdapter((options) async {
      if (options.path.endsWith('/auth/logout')) {
        logoutBodies.add((options.data as Map)['refreshToken'].toString());
        if (logoutBodies.length == 1) {
          return ScriptedHttp(401, {
            'code': 'TOKEN_EXPIRED',
            'message': '访问令牌已过期',
            'traceId': 'logout-expired',
            'details': {},
          });
        }
        return ScriptedHttp(200, {});
      }
      if (options.path.endsWith('/auth/refresh')) {
        expect((options.data as Map)['refreshToken'], 'old-r');
        return ScriptedHttp(200, tokenJson(access: 'new-a', refresh: 'new-r'));
      }
      return ScriptedHttp(
          500, {'code': 'INTERNAL_ERROR', 'message': 'unexpected'});
    });
    final repo =
        AuthRepository(api: buildClient(adapter, store), tokenStore: store);

    await repo.logout();
    expect(logoutBodies, ['old-r', 'new-r']);
    expect(await store.readSession(), isNull);
  });

  test('logout sends the refresh token currently in the store', () async {
    final store = MemoryTokenStore();
    await store.saveSession(AuthSession.fromTokenResponse(
        tokenJson(access: 'access-a', refresh: 'refresh-a')));
    String? sent;
    final adapter = ScriptedAdapter((options) async {
      expect(options.path, '/api/v1/auth/logout');
      sent = (options.data as Map)['refreshToken']?.toString();
      return ScriptedHttp(200, {});
    });
    final repo =
        AuthRepository(api: buildClient(adapter, store), tokenStore: store);

    await repo.logout();
    expect(sent, 'refresh-a');
    expect(await store.readSession(), isNull);
  });

  test('logout network failure keeps session until local clear', () async {
    final store = MemoryTokenStore();
    await store.saveSession(AuthSession.fromTokenResponse(tokenJson()));
    final adapter = ScriptedAdapter((options) async {
      expect(options.path, '/api/v1/auth/logout');
      return ScriptedHttp(503, {
        'code': 'NETWORK_ERROR',
        'message': '服务暂时不可用',
        'traceId': 't5',
        'details': {},
      });
    });
    final repo =
        AuthRepository(api: buildClient(adapter, store), tokenStore: store);
    await expectLater(repo.logout(), throwsA(isA<ApiException>()));
    expect(await store.readRefreshToken(), 'refresh-1');
    await repo.clearLocal();
    expect(await store.readSession(), isNull);
  });
}
