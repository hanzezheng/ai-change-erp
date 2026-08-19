import 'package:decimal/decimal.dart';

import '../../../core/utils/decimal_json.dart';

class InventoryItem {
  const InventoryItem({
    required this.productId,
    required this.itemCode,
    required this.productName,
    this.spec,
    required this.quantity,
    required this.stockUom,
    this.warehouse,
    this.alertQty,
    this.lowStock,
  });

  final String productId;
  final String itemCode;
  final String productName;
  final String? spec;
  final Decimal quantity;
  final String stockUom;
  final String? warehouse;
  final Decimal? alertQty;
  final bool? lowStock;

  factory InventoryItem.fromJson(Map<String, dynamic> json) {
    return InventoryItem(
      productId: json['productId']?.toString() ?? '',
      itemCode: json['itemCode']?.toString() ?? '',
      productName: json['productName']?.toString() ?? '',
      spec: json['spec']?.toString(),
      quantity: decimalFromJsonRequired(json['quantity']),
      stockUom: json['stockUom']?.toString() ?? '',
      warehouse: json['warehouse']?.toString(),
      alertQty: decimalFromJson(json['alertQty']),
      lowStock: json['lowStock'] as bool?,
    );
  }
}
