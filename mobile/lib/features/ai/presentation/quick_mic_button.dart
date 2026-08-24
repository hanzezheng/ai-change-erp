import 'package:flutter/material.dart';

import 'quick_action_sheet.dart';

/// 短按 / 长按均打开文字快捷操作。
/// 设备 ASR 不准、服务端农批 ASR 未就绪前不做直录。
class QuickMicButton extends StatelessWidget {
  const QuickMicButton({
    super.key,
    this.icon = Icons.mic_none,
    this.tooltip = '快捷操作',
  });

  final IconData icon;
  final String tooltip;

  @override
  Widget build(BuildContext context) {
    return IconButton(
      tooltip: tooltip,
      icon: Icon(icon),
      onPressed: () => showQuickActionSheet(context),
    );
  }
}
