import 'package:decimal/decimal.dart';

Decimal? decimalFromJson(Object? value) {
  if (value == null) {
    return null;
  }
  if (value is Decimal) {
    return value;
  }
  final text = value.toString().trim();
  if (text.isEmpty || text == 'null') {
    return null;
  }
  return Decimal.parse(text);
}

Decimal decimalFromJsonRequired(Object? value) {
  return decimalFromJson(value) ?? Decimal.zero;
}

Object decimalToJson(Decimal value) {
  return value.toString();
}

bool decimalGreaterThan(Decimal a, Decimal b) => a > b;

bool decimalGreaterOrEqual(Decimal a, Decimal b) => a >= b;
