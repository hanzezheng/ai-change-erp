import 'dart:async';
import 'dart:convert';
import 'dart:typed_data';

import 'package:dio/dio.dart';

class ScriptedHttp {
  ScriptedHttp(this.status, this.body, {this.delay = Duration.zero});

  final int status;
  final Object? body;
  final Duration delay;
}

class ScriptedAdapter implements HttpClientAdapter {
  ScriptedAdapter(this.onFetch);

  final Future<ScriptedHttp> Function(RequestOptions options) onFetch;
  final requests = <RequestOptions>[];

  @override
  Future<ResponseBody> fetch(
    RequestOptions options,
    Stream<Uint8List>? requestStream,
    Future<void>? cancelFuture,
  ) async {
    requests.add(options);
    final scripted = await onFetch(options);
    if (scripted.delay > Duration.zero) {
      await Future<void>.delayed(scripted.delay);
    }
    return ResponseBody.fromString(
      scripted.body == null ? '' : jsonEncode(scripted.body),
      scripted.status,
      headers: {
        Headers.contentTypeHeader: [Headers.jsonContentType],
      },
    );
  }

  @override
  void close({bool force = false}) {}
}

Map<String, dynamic> tokenJson({
  String access = 'access-1',
  String refresh = 'refresh-1',
}) {
  return {
    'accessToken': access,
    'refreshToken': refresh,
    'tokenType': 'Bearer',
    'expiresIn': 900,
    'userId': '11111111-1111-1111-1111-111111111111',
    'tenantId': '22222222-2222-2222-2222-222222222222',
    'tenantName': '鲜果档口',
    'membershipId': '33333333-3333-3333-3333-333333333333',
    'role': 'OWNER',
    'displayName': '陈老板',
  };
}
