import 'package:flutter/material.dart';

import '../../app/theme/app_colors.dart';
import '../../app/theme/app_radius.dart';
import '../../app/theme/app_text_styles.dart';

class StatusBadge extends StatelessWidget {
  const StatusBadge({super.key, required this.label, required this.kind});

  final String label;
  final BadgeKind kind;

  @override
  Widget build(BuildContext context) {
    final colors = switch (kind) {
      BadgeKind.neutral => (AppColors.background, AppColors.textSecondary),
      BadgeKind.success => (AppColors.successLight, AppColors.success),
      BadgeKind.warning => (AppColors.warningLight, AppColors.warning),
      BadgeKind.danger => (AppColors.dangerLight, AppColors.danger),
      BadgeKind.info => (const Color(0xFFEEF2FF), const Color(0xFF4B5FBF)),
    };
    return Container(
      padding: const EdgeInsets.symmetric(horizontal: 7, vertical: 2),
      decoration: BoxDecoration(
        color: colors.$1,
        borderRadius: BorderRadius.circular(AppRadius.xs),
      ),
      child: Text(label, style: AppTextStyles.badge.copyWith(color: colors.$2)),
    );
  }
}

enum BadgeKind { neutral, success, warning, danger, info }

BadgeKind orderStatusKind(String status) {
  switch (status) {
    case 'DRAFT':
    case '草稿':
      return BadgeKind.neutral;
    case 'SUBMITTED':
    case '已提交':
      return BadgeKind.info;
    case 'COMPLETED':
    case '已完成':
      return BadgeKind.success;
    case 'CANCELLED':
    case '已取消':
      return BadgeKind.neutral;
    default:
      return BadgeKind.neutral;
  }
}

BadgeKind paymentStatusKind(String status) {
  switch (status) {
    case 'UNPAID':
    case '未收款':
      return BadgeKind.danger;
    case 'PARTIAL':
    case '部分收款':
      return BadgeKind.warning;
    case 'PAID':
    case '已收款':
      return BadgeKind.success;
    case 'PENDING_CONFIRMATION':
    case '待确认':
      return BadgeKind.warning;
    case 'CONFIRMED':
    case '已到账':
      return BadgeKind.success;
    default:
      return BadgeKind.neutral;
  }
}
