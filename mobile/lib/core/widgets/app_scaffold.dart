import 'package:flutter/material.dart';

import '../../app/theme/app_colors.dart';
import '../../app/theme/app_spacing.dart';
import '../../features/ai/presentation/quick_action_sheet.dart';
import 'primary_nav.dart';

class AppScaffold extends StatelessWidget {
  const AppScaffold({
    super.key,
    required this.body,
    this.title,
    this.leading,
    this.actions,
    this.bottomNavigationBar,
    this.bottomAction,
    this.backgroundColor,
  });

  final Widget body;
  final String? title;
  final Widget? leading;
  final List<Widget>? actions;
  final Widget? bottomNavigationBar;
  final Widget? bottomAction;
  final Color? backgroundColor;

  @override
  Widget build(BuildContext context) {
    return Scaffold(
      backgroundColor: backgroundColor ?? AppColors.background,
      appBar: title == null
          ? null
          : AppBar(
              title: Text(title!),
              leading: leading,
              actions: actions,
              bottom: const PreferredSize(
                preferredSize: Size.fromHeight(1),
                child: Divider(height: 1, color: AppColors.border),
              ),
            ),
      body: body,
      bottomNavigationBar: bottomAction ?? bottomNavigationBar,
    );
  }
}

class BusinessActionBar extends StatelessWidget {
  const BusinessActionBar({super.key, required this.children});

  final List<Widget> children;

  @override
  Widget build(BuildContext context) {
    return Material(
      color: AppColors.surface,
      child: Padding(
        padding: EdgeInsets.fromLTRB(
          AppSpacing.lg,
          10,
          AppSpacing.lg,
          10 + MediaQuery.paddingOf(context).bottom,
        ),
        child: Row(
          children: [
            for (var i = 0; i < children.length; i++) ...[
              if (i > 0) const SizedBox(width: 10),
              Expanded(child: children[i]),
            ],
          ],
        ),
      ),
    );
  }
}

class ShellScaffold extends StatelessWidget {
  const ShellScaffold({
    super.key,
    required this.currentIndex,
    required this.onSelect,
    required this.child,
  });

  final int currentIndex;
  final ValueChanged<int> onSelect;
  final Widget child;

  @override
  Widget build(BuildContext context) {
    return Scaffold(
      backgroundColor: AppColors.background,
      body: child,
      bottomNavigationBar: PrimaryNavBar(
        currentIndex: currentIndex,
        onSelect: onSelect,
        onMicTap: () => showQuickActionSheet(context),
      ),
    );
  }
}
