import 'package:flutter_secure_storage/flutter_secure_storage.dart';

import 'auth_session.dart';
import 'token_store.dart';

class SecureTokenStore implements TokenStore {
  SecureTokenStore({FlutterSecureStorage? storage})
      : _storage = storage ?? const FlutterSecureStorage();

  static const _keys = [
    'accessToken',
    'refreshToken',
    'userId',
    'tenantId',
    'tenantName',
    'membershipId',
    'role',
    'displayName',
  ];

  final FlutterSecureStorage _storage;
  String? _accessToken;

  @override
  String? get accessToken => _accessToken;

  @override
  Future<String?> readRefreshToken() {
    return _storage.read(key: 'refreshToken');
  }

  @override
  Future<AuthSession?> readSession() async {
    final values = <String, String>{};
    for (final key in _keys) {
      final value = await _storage.read(key: key);
      if (value != null) {
        values[key] = value;
      }
    }
    final access = values['accessToken'];
    final refresh = values['refreshToken'];
    if (access == null || access.isEmpty || refresh == null || refresh.isEmpty) {
      _accessToken = null;
      return null;
    }
    final session = AuthSession.fromStorage(values);
    _accessToken = session.accessToken;
    return session;
  }

  @override
  Future<void> saveSession(AuthSession session) async {
    _accessToken = session.accessToken;
    final map = session.toStorage();
    for (final entry in map.entries) {
      await _storage.write(key: entry.key, value: entry.value);
    }
  }

  @override
  Future<void> clear() async {
    _accessToken = null;
    for (final key in _keys) {
      await _storage.delete(key: key);
    }
  }
}

class MemoryTokenStore implements TokenStore {
  AuthSession? _session;

  @override
  String? get accessToken => _session?.accessToken;

  @override
  Future<String?> readRefreshToken() async => _session?.refreshToken;

  @override
  Future<AuthSession?> readSession() async => _session;

  @override
  Future<void> saveSession(AuthSession session) async {
    _session = session;
  }

  @override
  Future<void> clear() async {
    _session = null;
  }
}
