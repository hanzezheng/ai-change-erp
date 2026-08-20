import 'package:flutter/material.dart';
import 'package:flutter_riverpod/flutter_riverpod.dart';
import 'package:go_router/go_router.dart';

import '../../../app/theme/app_colors.dart';
import '../../../app/theme/app_spacing.dart';
import '../../../app/theme/app_text_styles.dart';
import '../../../core/api/api_exception.dart';
import '../../../core/auth/auth_providers.dart';
import '../../../core/widgets/app_bottom_sheet.dart';
import '../../../core/widgets/buttons.dart';

class LoginPage extends ConsumerStatefulWidget {
  const LoginPage({super.key});

  @override
  ConsumerState<LoginPage> createState() => _LoginPageState();
}

class _LoginPageState extends ConsumerState<LoginPage> {
  final _login = TextEditingController();
  final _password = TextEditingController();
  bool _busy = false;
  String? _error;
  String? _traceId;

  @override
  void dispose() {
    _login.dispose();
    _password.dispose();
    super.dispose();
  }

  Future<void> _submit({String? tenantId}) async {
    setState(() {
      _busy = true;
      _error = null;
      _traceId = null;
    });
    try {
      final session = await ref.read(authRepositoryProvider).login(
            login: _login.text.trim(),
            password: _password.text,
            tenantId: tenantId,
          );
      ref.read(authControllerProvider).setSession(session);
      _password.clear();
      if (mounted) {
        context.go('/home');
      }
    } on ApiException catch (error) {
      if (error.isTenantSelection && tenantId == null) {
        final selected = await _pickTenant(error.tenantOptions);
        if (selected != null) {
          await _submit(tenantId: selected);
          return;
        }
      } else {
        setState(() {
          _error = error.userMessage;
          _traceId = error.traceId;
        });
      }
    } finally {
      if (mounted) {
        setState(() => _busy = false);
      }
    }
  }

  Future<String?> _pickTenant(List<TenantOption> tenants) {
    return showAppBottomSheet<String>(
      context: context,
      heightFactor: 0.5,
      builder: (context) {
        return ListView(
          children: [
            const Padding(
              padding: EdgeInsets.fromLTRB(16, 12, 16, 8),
              child: Text('选择企业', style: AppTextStyles.appBarTitle),
            ),
            for (final tenant in tenants)
              ListTile(
                title: Text(tenant.tenantName, style: AppTextStyles.bodyStrong),
                subtitle: Text(tenant.role, style: AppTextStyles.tertiary),
                onTap: () => Navigator.pop(context, tenant.tenantId),
              ),
          ],
        );
      },
    );
  }

  @override
  Widget build(BuildContext context) {
    return Scaffold(
      backgroundColor: AppColors.background,
      body: SafeArea(
        child: ListView(
          padding: const EdgeInsets.fromLTRB(24, 64, 24, 24),
          children: [
            const Text('农批经营助手', style: AppTextStyles.pageTitle),
            const SizedBox(height: 8),
            Text('登录后开始经营', style: AppTextStyles.secondary.copyWith(fontSize: 14)),
            const SizedBox(height: 36),
            const Text('登录名', style: AppTextStyles.secondary),
            const SizedBox(height: 6),
            TextField(
              controller: _login,
              autofillHints: const [AutofillHints.username],
              textInputAction: TextInputAction.next,
              decoration: const InputDecoration(hintText: '请输入登录名'),
            ),
            const SizedBox(height: 16),
            const Text('密码', style: AppTextStyles.secondary),
            const SizedBox(height: 6),
            TextField(
              controller: _password,
              obscureText: true,
              autofillHints: const [AutofillHints.password],
              onSubmitted: (_) => _busy ? null : _submit(),
              decoration: const InputDecoration(hintText: '请输入密码'),
            ),
            if (_error != null) ...[
              const SizedBox(height: 12),
              Text(_error!, style: AppTextStyles.caption.copyWith(color: AppColors.danger)),
              if (_traceId != null)
                Text('错误编号 $_traceId', style: AppTextStyles.tertiary),
            ],
            const SizedBox(height: 24),
            PrimaryButton(label: '登录', loading: _busy, onPressed: _busy ? null : _submit),
            const SizedBox(height: AppSpacing.xxl),
          ],
        ),
      ),
    );
  }
}

class SplashPage extends ConsumerStatefulWidget {
  const SplashPage({super.key});

  @override
  ConsumerState<SplashPage> createState() => _SplashPageState();
}

class _SplashPageState extends ConsumerState<SplashPage> {
  @override
  void initState() {
    super.initState();
    WidgetsBinding.instance.addPostFrameCallback((_) => _bootstrap());
  }

  Future<void> _bootstrap() async {
    final auth = ref.read(authControllerProvider);
    auth.setBootstrapping();
    final repo = ref.read(authRepositoryProvider);
    final local = await repo.restore();
    if (local == null || local.refreshToken.isEmpty) {
      auth.clearSession();
      if (mounted) {
        context.go('/login');
      }
      return;
    }
    try {
      final session = await repo.refresh();
      auth.setSession(session);
      if (mounted) {
        context.go('/home');
      }
    } catch (_) {
      await repo.clearLocal();
      auth.clearSession();
      if (mounted) {
        context.go('/login');
      }
    }
  }

  @override
  Widget build(BuildContext context) {
    return const Scaffold(
      backgroundColor: AppColors.background,
      body: Center(
        child: CircularProgressIndicator(color: AppColors.primary, strokeWidth: 2.4),
      ),
    );
  }
}
