import 'package:flutter_riverpod/flutter_riverpod.dart';

import '../../app/config/app_config.dart';
import '../api/api_client.dart';
import 'auth_controller.dart';
import 'auth_repository.dart';
import 'secure_token_store.dart';
import 'token_store.dart';

final apiBaseUrlProvider = Provider<String>((ref) {
  return AppConfig.requireApiBaseUrl();
});

final tokenStoreProvider = Provider<TokenStore>((ref) {
  return SecureTokenStore();
});

final authControllerProvider = ChangeNotifierProvider<AuthController>((ref) {
  return AuthController();
});

final apiClientProvider = Provider<ApiClient>((ref) {
  final client = ApiClient(
    baseUrl: ref.watch(apiBaseUrlProvider),
    tokenStore: ref.watch(tokenStoreProvider),
  );
  client.onSessionCleared = () {
    ref.read(authControllerProvider).clearSession();
  };
  return client;
});

final authRepositoryProvider = Provider<AuthRepository>((ref) {
  return AuthRepository(
    api: ref.watch(apiClientProvider),
    tokenStore: ref.watch(tokenStoreProvider),
  );
});
