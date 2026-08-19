class AuthSession {
  const AuthSession({
    required this.accessToken,
    required this.refreshToken,
    required this.userId,
    required this.tenantId,
    required this.tenantName,
    required this.membershipId,
    required this.role,
    required this.displayName,
  });

  final String accessToken;
  final String refreshToken;
  final String userId;
  final String tenantId;
  final String tenantName;
  final String membershipId;
  final String role;
  final String displayName;

  factory AuthSession.fromTokenResponse(Map<String, dynamic> json) {
    return AuthSession(
      accessToken: json['accessToken'] as String,
      refreshToken: json['refreshToken'] as String,
      userId: json['userId']?.toString() ?? '',
      tenantId: json['tenantId']?.toString() ?? '',
      tenantName: json['tenantName']?.toString() ?? '',
      membershipId: json['membershipId']?.toString() ?? '',
      role: json['role']?.toString() ?? '',
      displayName: json['displayName']?.toString() ?? '',
    );
  }

  Map<String, String> toStorage() {
    return {
      'accessToken': accessToken,
      'refreshToken': refreshToken,
      'userId': userId,
      'tenantId': tenantId,
      'tenantName': tenantName,
      'membershipId': membershipId,
      'role': role,
      'displayName': displayName,
    };
  }

  factory AuthSession.fromStorage(Map<String, String> values) {
    return AuthSession(
      accessToken: values['accessToken'] ?? '',
      refreshToken: values['refreshToken'] ?? '',
      userId: values['userId'] ?? '',
      tenantId: values['tenantId'] ?? '',
      tenantName: values['tenantName'] ?? '',
      membershipId: values['membershipId'] ?? '',
      role: values['role'] ?? '',
      displayName: values['displayName'] ?? '',
    );
  }

  AuthSession copyWith({
    String? accessToken,
    String? refreshToken,
    String? userId,
    String? tenantId,
    String? tenantName,
    String? membershipId,
    String? role,
    String? displayName,
  }) {
    return AuthSession(
      accessToken: accessToken ?? this.accessToken,
      refreshToken: refreshToken ?? this.refreshToken,
      userId: userId ?? this.userId,
      tenantId: tenantId ?? this.tenantId,
      tenantName: tenantName ?? this.tenantName,
      membershipId: membershipId ?? this.membershipId,
      role: role ?? this.role,
      displayName: displayName ?? this.displayName,
    );
  }

  String get roleLabel {
    switch (role) {
      case 'OWNER':
        return '所有者';
      case 'ADMIN':
        return '管理员';
      case 'STAFF':
        return '员工';
      default:
        return role;
    }
  }
}
