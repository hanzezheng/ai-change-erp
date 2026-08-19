import 'package:decimal/decimal.dart';
import 'package:flutter/material.dart';

import '../../app/theme/app_text_styles.dart';
import '../utils/money.dart';

class MoneyText extends StatelessWidget {
  const MoneyText(
    this.amount, {
    super.key,
    this.style,
    this.uom,
  });

  final Decimal? amount;
  final TextStyle? style;
  final String? uom;

  @override
  Widget build(BuildContext context) {
    final text = uom == null ? MoneyFormat.cny(amount) : MoneyFormat.cnyWithUom(amount, uom);
    if (text.isEmpty) {
      return const SizedBox.shrink();
    }
    return Text(text, style: style ?? AppTextStyles.money);
  }
}
