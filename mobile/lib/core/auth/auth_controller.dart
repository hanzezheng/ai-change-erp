import 'package:flutter/foundation.dart';

import 'auth_session.dart';

enum AuthStatus { unknown, unauthenticated, authenticated }

class AuthState {
  const AuthState({
    required this.status,
    this.session,
    this.bootstrapping = false,
  });

  final AuthStatus status;
  final AuthSession? session;
  final bool bootstrapping;

  factory AuthState.unknown() =>
      const AuthState(status: AuthStatus.unknown, bootstrapping: true);

  factory AuthState.unauthenticated() =>
      const AuthState(status: AuthStatus.unauthenticated);

  factory AuthState.authenticated(AuthSession session) =>
      AuthState(status: AuthStatus.authenticated, session: session);

  bool get isLoggedIn => status == AuthStatus.authenticated && session != null;
}

class AuthController extends ChangeNotifier {
  AuthState _state = AuthState.unknown();

  AuthState get state => _state;

  void setBootstrapping() {
    _state = AuthState.unknown();
    notifyListeners();
  }

  void setSession(AuthSession session) {
    _state = AuthState.authenticated(session);
    notifyListeners();
  }

  void clearSession() {
    _state = AuthState.unauthenticated();
    notifyListeners();
  }
}
