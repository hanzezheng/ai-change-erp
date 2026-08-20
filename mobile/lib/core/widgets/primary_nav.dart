import 'package:flutter/material.dart';

import '../../app/theme/app_colors.dart';
import '../../app/theme/app_spacing.dart';
import '../../app/theme/app_text_styles.dart';

class PrimaryNavBar extends StatelessWidget {
  const PrimaryNavBar({
    super.key,
    required this.currentIndex,
    required this.onSelect,
    this.onMicTap,
  });

  /// 0 home, 1 orders, 2 customers, 3 more. Mic is a reserved slot, not an index.
  final int currentIndex;
  final ValueChanged<int> onSelect;
  final VoidCallback? onMicTap;

  @override
  Widget build(BuildContext context) {
    return Container(
      height: AppSpacing.navHeight + MediaQuery.paddingOf(context).bottom,
      padding: EdgeInsets.only(bottom: MediaQuery.paddingOf(context).bottom),
      decoration: const BoxDecoration(
        color: AppColors.surface,
        border: Border(top: BorderSide(color: AppColors.border)),
      ),
      child: Row(
        children: [
          _NavItem(
            icon: Icons.home_outlined,
            selectedIcon: Icons.home,
            label: '首页',
            selected: currentIndex == 0,
            onTap: () => onSelect(0),
          ),
          _NavItem(
            icon: Icons.receipt_long_outlined,
            selectedIcon: Icons.receipt_long,
            label: '订单',
            selected: currentIndex == 1,
            onTap: () => onSelect(1),
          ),
          _MicSlot(onTap: onMicTap),
          _NavItem(
            icon: Icons.person_outline,
            selectedIcon: Icons.person,
            label: '客户',
            selected: currentIndex == 2,
            onTap: () => onSelect(2),
          ),
          _NavItem(
            icon: Icons.more_horiz,
            selectedIcon: Icons.more_horiz,
            label: '更多',
            selected: currentIndex == 3,
            onTap: () => onSelect(3),
          ),
        ],
      ),
    );
  }
}

class _NavItem extends StatelessWidget {
  const _NavItem({
    required this.icon,
    required this.selectedIcon,
    required this.label,
    required this.selected,
    required this.onTap,
  });

  final IconData icon;
  final IconData selectedIcon;
  final String label;
  final bool selected;
  final VoidCallback onTap;

  @override
  Widget build(BuildContext context) {
    final color = selected ? AppColors.primary : AppColors.textTertiary;
    return Expanded(
      child: InkWell(
        onTap: onTap,
        child: Column(
          mainAxisAlignment: MainAxisAlignment.center,
          children: [
            Icon(selected ? selectedIcon : icon, size: 22, color: color),
            const SizedBox(height: 3),
            Text(
              label,
              style: AppTextStyles.navLabel.copyWith(
                color: color,
                fontWeight: selected ? FontWeight.w600 : FontWeight.w400,
              ),
            ),
          ],
        ),
      ),
    );
  }
}

class _MicSlot extends StatelessWidget {
  const _MicSlot({this.onTap});

  final VoidCallback? onTap;

  @override
  Widget build(BuildContext context) {
    return Expanded(
      child: Center(
        child: Padding(
          padding: const EdgeInsets.only(bottom: 4),
          child: Material(
            color: AppColors.primary,
            shape: const CircleBorder(),
            child: InkWell(
              key: const ValueKey('primary-nav-voice'),
              customBorder: const CircleBorder(),
              onTap: onTap,
              child: const SizedBox(
                width: 50,
                height: 50,
                child: Icon(Icons.mic, size: 22, color: Colors.white),
              ),
            ),
          ),
        ),
      ),
    );
  }
}
