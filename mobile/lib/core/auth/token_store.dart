import 'auth_session.dart';

abstract class TokenStore {
  String? get accessToken;

  Future<String?> readRefreshToken();

  Future<AuthSession?> readSession();

  Future<void> saveSession(AuthSession session);

  Future<void> clear();
}
