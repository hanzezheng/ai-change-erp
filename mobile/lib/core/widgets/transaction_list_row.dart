import 'package:decimal/decimal.dart';
import 'package:flutter/material.dart';

import '../../app/theme/app_colors.dart';
import '../../app/theme/app_text_styles.dart';
import '../utils/money.dart';
import 'status_badge.dart';

class TransactionListRow extends StatelessWidget {
  const TransactionListRow({
    super.key,
    required this.title,
    required this.subtitle,
    this.amount,
    this.trailing,
    this.orderStatus,
    this.paymentStatus,
    this.lastDeal,
    this.showChevron = false,
    this.onTap,
  });

  final String title;
  final String subtitle;
  final Decimal? amount;
  final Widget? trailing;
  final String? orderStatus;
  final String? paymentStatus;
  final String? lastDeal;
  final bool showChevron;
  final VoidCallback? onTap;

  @override
  Widget build(BuildContext context) {
    return InkWell(
      onTap: onTap,
      child: Padding(
        padding: const EdgeInsets.symmetric(horizontal: 16, vertical: 12),
        child: Column(
          children: [
            Row(
              crossAxisAlignment: CrossAxisAlignment.start,
              children: [
                Expanded(
                  child: Text(
                    title,
                    style: AppTextStyles.rowTitle,
                    maxLines: 2,
                    overflow: TextOverflow.ellipsis,
                  ),
                ),
                const SizedBox(width: 8),
                if (amount != null)
                  Text(MoneyFormat.cny(amount), style: AppTextStyles.money),
                if (showChevron) ...[
                  const SizedBox(width: 2),
                  const Icon(Icons.chevron_right, size: 16, color: AppColors.textMuted),
                ],
              ],
            ),
            const SizedBox(height: 4),
            Row(
              children: [
                Expanded(
                  child: Text(
                    subtitle,
                    style: AppTextStyles.caption,
                    maxLines: 1,
                    overflow: TextOverflow.ellipsis,
                  ),
                ),
                if (lastDeal != null && lastDeal!.isNotEmpty)
                  Text(lastDeal!, style: AppTextStyles.tertiary),
                if (orderStatus != null || paymentStatus != null)
                  Row(
                    children: [
                      if (orderStatus != null) StatusBadge(label: orderStatus!, kind: orderStatusKind(orderStatus!)),
                      if (orderStatus != null && paymentStatus != null) const SizedBox(width: 5),
                      if (paymentStatus != null)
                        StatusBadge(label: paymentStatus!, kind: paymentStatusKind(paymentStatus!)),
                    ],
                  ),
                if (trailing != null) trailing!,
              ],
            ),
          ],
        ),
      ),
    );
  }
}
