import 'package:assistant/core/utils/money.dart';
import 'package:assistant/features/customers/data/customer_models.dart';
import 'package:assistant/features/products/data/product_models.dart';
import 'package:decimal/decimal.dart';
import 'package:flutter_test/flutter_test.dart';

void main() {
  test('金额格式化为 ¥2,320 和 ¥3.80', () {
    expect(MoneyFormat.cny(Decimal.parse('2320')), '¥2,320');
    expect(MoneyFormat.cny(Decimal.parse('3.80')), '¥3.80');
    expect(MoneyFormat.cny(Decimal.parse('3.8')), '¥3.80');
    expect(MoneyFormat.cnyWithUom(Decimal.parse('68'), '箱'), '¥68/箱');
  });

  test('客户模型只保留正式字段', () {
    final customer = CustomerSummary.fromJson({
      'customerId': 'CUST-HAN',
      'customerName': '韩兆亮',
      'aliases': ['老韩'],
      'phone': '13800000000',
      'address': '新发地',
      'receivableAmount': 9999,
      'orderCount': 12,
    });
    expect(customer.customerId, 'CUST-HAN');
    expect(customer.aliases, ['老韩']);
  });

  test('商品身份使用 itemCode 且规格保持 ERP 原文', () {
    final variant = ProductVariant.fromJson({
      'productId': 'APPLE',
      'itemCode': 'APPLE-80',
      'productName': '苹果80果',
      'spec': '80果',
      'defaultUom': '箱',
      'allowedUoms': [
        {'uom': '箱', 'referencePrice': '68'},
        {'uom': '斤', 'referencePrice': '3.8'},
      ],
      'referencePrice': '68',
    });
    expect(variant.itemCode, 'APPLE-80');
    expect(variant.spec, '80果');
    expect(variant.allowedUoms.map((u) => u.uom), ['箱', '斤']);
  });
}
