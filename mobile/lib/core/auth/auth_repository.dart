import '../api/api_client.dart';
import '../api/api_exception.dart';
import 'auth_session.dart';
import 'token_store.dart';

class AuthRepository {
  AuthRepository({
    required ApiClient api,
    required TokenStore tokenStore,
  })  : _api = api,
        _tokenStore = tokenStore;

  final ApiClient _api;
  final TokenStore _tokenStore;

  Future<AuthSession?> restore() {
    return _tokenStore.readSession();
  }

  Future<AuthSession> login({
    required String login,
    required String password,
    String? tenantId,
  }) async {
    final body = <String, dynamic>{
      'login': login,
      'password': password,
    };
    if (tenantId != null && tenantId.isNotEmpty) {
      body['tenantId'] = tenantId;
    }
    final response = await _api.postUnauthenticated('/api/v1/auth/login', data: body);
    final data = response.data;
    if (data is! Map) {
      throw ApiException(code: 'INTERNAL_ERROR', message: '登录响应无效');
    }
    final session = AuthSession.fromTokenResponse(Map<String, dynamic>.from(data));
    await _tokenStore.saveSession(session);
    return session;
  }

  Future<AuthSession> refresh() async {
    final refreshToken = await _tokenStore.readRefreshToken();
    if (refreshToken == null || refreshToken.isEmpty) {
      await _tokenStore.clear();
      throw ApiException(code: 'REFRESH_TOKEN_INVALID', message: '刷新令牌无效或已失效');
    }
    try {
      final response = await _api.postUnauthenticated(
        '/api/v1/auth/refresh',
        data: {'refreshToken': refreshToken},
      );
      final data = response.data;
      if (data is! Map) {
        await _tokenStore.clear();
        throw ApiException(code: 'REFRESH_TOKEN_INVALID', message: '刷新令牌无效或已失效');
      }
      final session = AuthSession.fromTokenResponse(Map<String, dynamic>.from(data));
      await _tokenStore.saveSession(session);
      return session;
    } on ApiException catch (error) {
      if (error.shouldClearSession || error.isAuthFailure) {
        await _tokenStore.clear();
      }
      rethrow;
    }
  }

  Future<void> logout({bool forceLocal = false}) async {
    final refreshToken = await _tokenStore.readRefreshToken();
    if (!forceLocal && refreshToken != null && refreshToken.isNotEmpty) {
      try {
        await _api.post(
          '/api/v1/auth/logout',
          data: {'refreshToken': refreshToken},
        );
      } on ApiException {
        rethrow;
      }
    }
    await _tokenStore.clear();
  }

  Future<void> clearLocal() => _tokenStore.clear();
}
