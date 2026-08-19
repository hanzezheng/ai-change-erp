class ApiException implements Exception {
  ApiException({
    required this.code,
    required this.message,
    this.traceId,
    this.details = const {},
    this.httpStatus,
  });

  final String code;
  final String message;
  final String? traceId;
  final Map<String, dynamic> details;
  final int? httpStatus;

  bool get isTenantSelection => code == 'TENANT_SELECTION_REQUIRED';

  bool get isAuthFailure =>
      code == 'AUTHENTICATION_FAILED' ||
      code == 'TOKEN_EXPIRED' ||
      code == 'TOKEN_INVALID' ||
      code == 'REFRESH_TOKEN_INVALID';

  bool get shouldClearSession =>
      code == 'REFRESH_TOKEN_INVALID' ||
      code == 'USER_DISABLED' ||
      code == 'TENANT_DISABLED' ||
      code == 'MEMBERSHIP_NOT_FOUND' ||
      code == 'TENANT_NOT_FOUND';

  List<TenantOption> get tenantOptions {
    final raw = details['tenants'];
    if (raw is! List) {
      return const [];
    }
    return raw
        .whereType<Map>()
        .map((item) => TenantOption.fromJson(Map<String, dynamic>.from(item)))
        .toList();
  }

  String get userMessage {
    switch (code) {
      case 'AUTHENTICATION_FAILED':
        return '登录名或密码错误';
      case 'PERMISSION_DENIED':
        return '没有权限执行此操作';
      case 'ERP_UNAVAILABLE':
      case 'ERP_CONNECTION_NOT_CONFIGURED':
        return '经营系统暂时不可用，可稍后重试';
      case 'ORDER_CONFLICT':
        return '订单已被其他人修改';
      case 'ORDER_STATUS_INVALID':
        return '当前订单状态不允许该操作';
      case 'IDEMPOTENCY_OUTCOME_UNKNOWN':
        return '写入结果暂时无法确认，请到业务列表刷新确认';
      case 'PAYMENT_NOT_SUPPORTED':
        return '当前版本不支持这类收款';
      case 'PAYMENT_METHOD_NOT_CONFIGURED':
        return '当前企业尚未配置可用收款方式';
      default:
        return message;
    }
  }

  @override
  String toString() => 'ApiException($code: $message, traceId: $traceId)';
}

class TenantOption {
  const TenantOption({
    required this.tenantId,
    required this.tenantName,
    required this.role,
  });

  final String tenantId;
  final String tenantName;
  final String role;

  factory TenantOption.fromJson(Map<String, dynamic> json) {
    return TenantOption(
      tenantId: json['tenantId']?.toString() ?? '',
      tenantName: json['tenantName']?.toString() ?? '',
      role: json['role']?.toString() ?? '',
    );
  }
}
