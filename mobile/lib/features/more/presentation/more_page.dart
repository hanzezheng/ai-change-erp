import 'package:flutter/material.dart';
import 'package:flutter_riverpod/flutter_riverpod.dart';
import 'package:go_router/go_router.dart';

import '../../../app/theme/app_colors.dart';
import '../../../app/theme/app_text_styles.dart';
import '../../../core/api/api_exception.dart';
import '../../../core/auth/auth_providers.dart';
import '../../../core/widgets/buttons.dart';

class MorePage extends ConsumerWidget {
  const MorePage({super.key});

  @override
  Widget build(BuildContext context, WidgetRef ref) {
    final session = ref.watch(authControllerProvider).state.session;
    return ColoredBox(
      color: AppColors.background,
      child: ListView(
        children: [
          Container(
            color: AppColors.surface,
            padding: EdgeInsets.fromLTRB(16, MediaQuery.paddingOf(context).top + 18, 16, 16),
            child: Column(
              crossAxisAlignment: CrossAxisAlignment.start,
              children: [
                Text(session?.displayName ?? '', style: AppTextStyles.greeting),
                const SizedBox(height: 6),
                Text(session?.tenantName ?? '', style: AppTextStyles.secondary),
                const SizedBox(height: 4),
                Text(session?.roleLabel ?? '', style: AppTextStyles.tertiary),
              ],
            ),
          ),
          const SizedBox(height: 10),
          ColoredBox(
            color: AppColors.surface,
            child: Column(
              children: [
                ListTile(
                  leading: const Icon(Icons.sell_outlined, color: AppColors.textSecondary),
                  title: const Text('商品'),
                  trailing: const Icon(Icons.chevron_right, color: AppColors.textMuted),
                  onTap: () => context.push('/products'),
                ),
                const Divider(height: 1),
                ListTile(
                  leading: const Icon(Icons.inventory_2_outlined, color: AppColors.textSecondary),
                  title: const Text('库存'),
                  trailing: const Icon(Icons.chevron_right, color: AppColors.textMuted),
                  onTap: () => context.push('/inventory'),
                ),
              ],
            ),
          ),
          const SizedBox(height: 10),
          ColoredBox(
            color: AppColors.surface,
            child: Column(
              children: [
                ListTile(
                  title: const Text('账号'),
                  subtitle: Text(session?.displayName ?? '-', style: AppTextStyles.tertiary),
                ),
                const Divider(height: 1),
                ListTile(
                  title: const Text('企业'),
                  subtitle: Text(session?.tenantName ?? '-', style: AppTextStyles.tertiary),
                ),
                const Divider(height: 1),
                ListTile(
                  title: const Text('角色'),
                  subtitle: Text(session?.roleLabel ?? '-', style: AppTextStyles.tertiary),
                ),
              ],
            ),
          ),
          const SizedBox(height: 24),
          Padding(
            padding: const EdgeInsets.symmetric(horizontal: 16),
            child: SecondaryButton(
              label: '退出登录',
              onPressed: () => _logout(context, ref),
            ),
          ),
        ],
      ),
    );
  }

  Future<void> _logout(BuildContext context, WidgetRef ref) async {
    final repo = ref.read(authRepositoryProvider);
    final auth = ref.read(authControllerProvider);
    try {
      await repo.logout();
      auth.clearSession();
      if (context.mounted) {
        context.go('/login');
      }
    } on ApiException {
      if (!context.mounted) {
        return;
      }
      final force = await showDialog<bool>(
        context: context,
        builder: (context) => AlertDialog(
          title: const Text('退出失败'),
          content: const Text('无法连接服务器。是否仍清除本机登录状态？'),
          actions: [
            TextButton(onPressed: () => Navigator.pop(context, false), child: const Text('取消')),
            TextButton(onPressed: () => Navigator.pop(context, true), child: const Text('清除本机登录')),
          ],
        ),
      );
      if (force == true) {
        await repo.clearLocal();
        auth.clearSession();
        if (context.mounted) {
          context.go('/login');
        }
      }
    }
  }
}
