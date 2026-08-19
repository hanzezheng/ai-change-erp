import 'package:decimal/decimal.dart';

import '../../../core/utils/decimal_json.dart';

class AllowedUom {
  const AllowedUom({
    required this.uom,
    this.conversionFactor,
    this.referencePrice,
    this.currency,
  });

  final String uom;
  final Decimal? conversionFactor;
  final Decimal? referencePrice;
  final String? currency;

  factory AllowedUom.fromJson(Map<String, dynamic> json) {
    return AllowedUom(
      uom: json['uom']?.toString() ?? '',
      conversionFactor: decimalFromJson(json['conversionFactor']),
      referencePrice: decimalFromJson(json['referencePrice']),
      currency: json['currency']?.toString(),
    );
  }
}

class ProductVariant {
  const ProductVariant({
    required this.productId,
    required this.itemCode,
    required this.productName,
    this.spec,
    this.aliases = const [],
    required this.defaultUom,
    this.allowedUoms = const [],
    this.referencePrice,
    this.priceUom,
    this.currency,
    this.lastDealPrice,
  });

  final String productId;
  final String itemCode;
  final String productName;
  final String? spec;
  final List<String> aliases;
  final String defaultUom;
  final List<AllowedUom> allowedUoms;
  final Decimal? referencePrice;
  final String? priceUom;
  final String? currency;
  final Decimal? lastDealPrice;

  factory ProductVariant.fromJson(Map<String, dynamic> json) {
    return ProductVariant(
      productId: json['productId']?.toString() ?? '',
      itemCode: json['itemCode']?.toString() ?? '',
      productName: json['productName']?.toString() ?? '',
      spec: json['spec']?.toString(),
      aliases: (json['aliases'] as List?)?.map((e) => e.toString()).toList() ?? const [],
      defaultUom: json['defaultUom']?.toString() ?? '',
      allowedUoms: (json['allowedUoms'] as List?)
              ?.whereType<Map>()
              .map((item) => AllowedUom.fromJson(Map<String, dynamic>.from(item)))
              .toList() ??
          const [],
      referencePrice: decimalFromJson(json['referencePrice']),
      priceUom: json['priceUom']?.toString(),
      currency: json['currency']?.toString(),
      lastDealPrice: decimalFromJson(json['lastDealPrice']),
    );
  }

  AllowedUom? uomInfo(String uom) {
    for (final item in allowedUoms) {
      if (item.uom == uom) {
        return item;
      }
    }
    return null;
  }
}

class ProductSelectorResult {
  const ProductSelectorResult({
    required this.frequentItems,
    required this.results,
  });

  final List<ProductVariant> frequentItems;
  final List<ProductVariant> results;

  factory ProductSelectorResult.fromJson(Map<String, dynamic> json) {
    List<ProductVariant> parse(String key) {
      final raw = json[key];
      if (raw is! List) {
        return const [];
      }
      return raw
          .whereType<Map>()
          .map((item) => ProductVariant.fromJson(Map<String, dynamic>.from(item)))
          .toList();
    }

    return ProductSelectorResult(
      frequentItems: parse('frequentItems'),
      results: parse('results'),
    );
  }
}

class LastDealPrice {
  const LastDealPrice({
    required this.price,
    required this.uom,
    this.sourceOrderId,
    this.transactionTime,
  });

  final Decimal price;
  final String uom;
  final String? sourceOrderId;
  final DateTime? transactionTime;

  factory LastDealPrice.fromJson(Map<String, dynamic> json) {
    return LastDealPrice(
      price: decimalFromJsonRequired(json['price']),
      uom: json['uom']?.toString() ?? '',
      sourceOrderId: json['sourceOrderId']?.toString(),
      transactionTime: json['transactionTime'] == null
          ? null
          : DateTime.tryParse(json['transactionTime'].toString()),
    );
  }
}
