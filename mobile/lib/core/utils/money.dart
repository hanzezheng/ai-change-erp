import 'package:decimal/decimal.dart';
import 'package:intl/intl.dart';

class MoneyFormat {
  MoneyFormat._();

  static final NumberFormat _pattern = NumberFormat.currency(
    locale: 'zh_CN',
    symbol: '¥',
    decimalDigits: 2,
  );

  static String cny(Decimal? amount) {
    if (amount == null) {
      return '';
    }
    final asDouble = double.parse(amount.toString());
    final formatted = _pattern.format(asDouble);
    if (amount.scale <= 0 || _isWhole(amount)) {
      return formatted.replaceAll(RegExp(r'\.00$'), '');
    }
    return formatted;
  }

  static String cnyWithUom(Decimal? amount, String? uom) {
    final money = cny(amount);
    if (money.isEmpty) {
      return '';
    }
    if (uom == null || uom.isEmpty) {
      return money;
    }
    return '$money/$uom';
  }

  static bool _isWhole(Decimal amount) {
    return amount == Decimal.fromBigInt(amount.toBigInt());
  }
}
