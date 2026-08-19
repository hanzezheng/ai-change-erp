import 'dart:async';
import 'dart:convert';
import 'dart:io';

import 'package:dio/dio.dart';

import '../auth/auth_session.dart';
import '../auth/token_store.dart';
import 'api_exception.dart';

typedef OnSessionCleared = void Function();

class ApiClient {
  ApiClient({
    required String baseUrl,
    required TokenStore tokenStore,
    Dio? dio,
    Dio? refreshDio,
    Duration timeout = const Duration(seconds: 20),
    this.onSessionCleared,
  })  : _tokenStore = tokenStore,
        _baseUrl = _normalizeBase(baseUrl) {
    _dio = dio ??
        Dio(
          BaseOptions(
            baseUrl: _baseUrl,
            connectTimeout: timeout,
            receiveTimeout: timeout,
            sendTimeout: timeout,
            headers: const {
              HttpHeaders.acceptHeader: 'application/json',
              HttpHeaders.contentTypeHeader: 'application/json',
            },
          ),
        );
    if (_dio.options.baseUrl.isEmpty) {
      _dio.options.baseUrl = _baseUrl;
    }
    _refreshDio = refreshDio ??
        Dio(
          BaseOptions(
            baseUrl: _baseUrl,
            connectTimeout: timeout,
            receiveTimeout: timeout,
            sendTimeout: timeout,
            headers: const {
              HttpHeaders.acceptHeader: 'application/json',
              HttpHeaders.contentTypeHeader: 'application/json',
            },
          ),
        );
    if (_refreshDio.options.baseUrl.isEmpty) {
      _refreshDio.options.baseUrl = _baseUrl;
    }
    _dio.interceptors.add(
        InterceptorsWrapper(
          onRequest: (options, handler) {
            final token = _tokenStore.accessToken;
            if (token != null && token.isNotEmpty) {
              options.headers[HttpHeaders.authorizationHeader] = 'Bearer $token';
            }
            handler.next(options);
          },
          onError: (error, handler) async {
            if (!_shouldRefresh(error)) {
              handler.next(error);
              return;
            }
            try {
              final refreshed = await _refreshSingleFlight();
              if (!refreshed) {
                handler.next(error);
                return;
              }
              final retry = await _retry(error.requestOptions);
              handler.resolve(retry);
            } catch (retryError) {
              if (retryError is DioException) {
                handler.next(retryError);
              } else {
                handler.next(error);
              }
            }
          },
        ),
      );
  }

  final TokenStore _tokenStore;
  final String _baseUrl;
  late final Dio _dio;
  late final Dio _refreshDio;
  OnSessionCleared? onSessionCleared;

  Completer<bool>? _refreshCompleter;
  bool _clearing = false;

  Dio get dio => _dio;

  static String _normalizeBase(String value) {
    final trimmed = value.trim();
    if (trimmed.endsWith('/')) {
      return trimmed.substring(0, trimmed.length - 1);
    }
    return trimmed;
  }

  Future<Response<dynamic>> get(
    String path, {
    Map<String, dynamic>? queryParameters,
    CancelToken? cancelToken,
  }) {
    return _guard(() => _dio.get<dynamic>(
          path,
          queryParameters: _compact(queryParameters),
          cancelToken: cancelToken,
        ));
  }

  Future<Response<dynamic>> post(
    String path, {
    Object? data,
    Map<String, dynamic>? queryParameters,
    Map<String, dynamic>? headers,
    CancelToken? cancelToken,
  }) {
    return _guard(() => _dio.post<dynamic>(
          path,
          data: data,
          queryParameters: _compact(queryParameters),
          cancelToken: cancelToken,
          options: Options(headers: headers),
        ));
  }

  Future<Response<dynamic>> postUnauthenticated(
    String path, {
    Object? data,
  }) {
    return _guard(() => _refreshDio.post<dynamic>(path, data: data));
  }

  Future<Response<dynamic>> put(
    String path, {
    Object? data,
    Map<String, dynamic>? headers,
    CancelToken? cancelToken,
  }) {
    return _guard(() => _dio.put<dynamic>(
          path,
          data: data,
          cancelToken: cancelToken,
          options: Options(headers: headers),
        ));
  }

  Future<T> getJson<T>(
    String path, {
    Map<String, dynamic>? queryParameters,
    CancelToken? cancelToken,
    required T Function(dynamic json) parse,
  }) async {
    final response = await get(
      path,
      queryParameters: queryParameters,
      cancelToken: cancelToken,
    );
    return parse(response.data);
  }

  bool _shouldRefresh(DioException error) {
    final status = error.response?.statusCode;
    if (status != 401) {
      return false;
    }
    final path = error.requestOptions.path;
    if (path.contains('/auth/login') || path.contains('/auth/refresh')) {
      return false;
    }
    final code = _tryParse(error.response).code;
    return code == 'TOKEN_EXPIRED' ||
        code == 'TOKEN_INVALID' ||
        code == 'AUTHENTICATION_FAILED' ||
        code.isEmpty;
  }

  Future<bool> _refreshSingleFlight() {
    final existing = _refreshCompleter;
    if (existing != null) {
      return existing.future;
    }
    final completer = Completer<bool>();
    _refreshCompleter = completer;
    () async {
      try {
        final refreshToken = await _tokenStore.readRefreshToken();
        if (refreshToken == null || refreshToken.isEmpty) {
          await _clearSession();
          completer.complete(false);
          return;
        }
        final response = await _refreshDio.post<dynamic>(
          '/api/v1/auth/refresh',
          data: {'refreshToken': refreshToken},
        );
        final data = response.data;
        if (data is! Map) {
          await _clearSession();
          completer.complete(false);
          return;
        }
        final sessionJson = Map<String, dynamic>.from(data);
        await _tokenStore.saveSession(AuthSession.fromTokenResponse(sessionJson));
        completer.complete(true);
      } on DioException catch (error) {
        final parsed = _tryParse(error.response);
        if (parsed.shouldClearSession || parsed.httpStatus == 401 || parsed.httpStatus == 403) {
          await _clearSession();
        }
        completer.complete(false);
      } catch (_) {
        await _clearSession();
        completer.complete(false);
      } finally {
        _refreshCompleter = null;
      }
    }();
    return completer.future;
  }

  Future<Response<dynamic>> _retry(RequestOptions requestOptions) {
    final token = _tokenStore.accessToken;
    final headers = Map<String, dynamic>.from(requestOptions.headers);
    if (token != null && token.isNotEmpty) {
      headers[HttpHeaders.authorizationHeader] = 'Bearer $token';
    }
    return _dio.request<dynamic>(
      requestOptions.path,
      data: requestOptions.data,
      queryParameters: requestOptions.queryParameters,
      cancelToken: requestOptions.cancelToken,
      options: Options(
        method: requestOptions.method,
        headers: headers,
        responseType: requestOptions.responseType,
        contentType: requestOptions.contentType,
        extra: requestOptions.extra,
      ),
    );
  }

  Future<Response<dynamic>> _guard(Future<Response<dynamic>> Function() run) async {
    try {
      return await run();
    } on DioException catch (error) {
      throw mapDioException(error);
    }
  }

  Future<void> _clearSession() async {
    if (_clearing) {
      return;
    }
    _clearing = true;
    try {
      await _tokenStore.clear();
      onSessionCleared?.call();
    } finally {
      _clearing = false;
    }
  }

  static ApiException mapDioException(DioException error) {
    if (error.type == DioExceptionType.cancel) {
      return ApiException(code: 'CANCELLED', message: '请求已取消');
    }
    if (error.type == DioExceptionType.connectionTimeout ||
        error.type == DioExceptionType.sendTimeout ||
        error.type == DioExceptionType.receiveTimeout) {
      return ApiException(
        code: 'NETWORK_TIMEOUT',
        message: '网络超时，请稍后重试',
        httpStatus: error.response?.statusCode,
      );
    }
    if (error.type == DioExceptionType.connectionError) {
      return ApiException(
        code: 'NETWORK_ERROR',
        message: '网络不可用，请检查连接后重试',
      );
    }
    return _tryParse(error.response);
  }

  static ApiException _tryParse(Response<dynamic>? response) {
    final status = response?.statusCode;
    final data = response?.data;
    if (data is Map) {
      final map = Map<String, dynamic>.from(data);
      final detailsRaw = map['details'];
      return ApiException(
        code: map['code']?.toString() ?? 'INTERNAL_ERROR',
        message: map['message']?.toString() ?? '服务暂时不可用',
        traceId: map['traceId']?.toString(),
        details: detailsRaw is Map ? Map<String, dynamic>.from(detailsRaw) : const {},
        httpStatus: status,
      );
    }
    if (data is String && data.trim().startsWith('{')) {
      try {
        final map = jsonDecode(data) as Map<String, dynamic>;
        return ApiException(
          code: map['code']?.toString() ?? 'INTERNAL_ERROR',
          message: map['message']?.toString() ?? '服务暂时不可用',
          traceId: map['traceId']?.toString(),
          details: map['details'] is Map ? Map<String, dynamic>.from(map['details'] as Map) : const {},
          httpStatus: status,
        );
      } catch (_) {}
    }
    return ApiException(
      code: status == 401 ? 'TOKEN_INVALID' : 'INTERNAL_ERROR',
      message: '服务暂时不可用',
      httpStatus: status,
    );
  }

  Map<String, dynamic>? _compact(Map<String, dynamic>? query) {
    if (query == null) {
      return null;
    }
    final result = <String, dynamic>{};
    query.forEach((key, value) {
      if (value != null) {
        result[key] = value;
      }
    });
    return result;
  }
}
