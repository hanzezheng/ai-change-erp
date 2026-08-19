class AppConfig {
  AppConfig._();

  static const String apiBaseUrl = String.fromEnvironment(
    'API_BASE_URL',
    defaultValue: '',
  );

  static String requireApiBaseUrl() {
    final value = apiBaseUrl.trim();
    if (value.isEmpty) {
      throw StateError(
        'API_BASE_URL 未配置。请使用 --dart-define=API_BASE_URL=https://host 启动。',
      );
    }
    return value.endsWith('/') ? value.substring(0, value.length - 1) : value;
  }
}
