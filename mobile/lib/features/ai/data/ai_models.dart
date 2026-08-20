import 'package:decimal/decimal.dart';

import '../../../core/utils/decimal_json.dart';

class AiActionRequest {
  const AiActionRequest({
    required this.inputType,
    this.text,
    this.asrText,
    this.context = const AiActionContext(),
  });

  final String inputType;
  final String? text;
  final String? asrText;
  final AiActionContext context;

  Map<String, dynamic> toJson() => {
        'inputType': inputType,
        if (text != null) 'text': text,
        if (asrText != null) 'asrText': asrText,
        'context': context.toJson(),
      };
}

class AiActionContext {
  const AiActionContext({
    this.currentPage,
    this.currentOrderId,
    this.currentCustomerId,
    this.currentCustomerName,
    this.currentItems = const [],
  });

  final String? currentPage;
  final String? currentOrderId;
  final String? currentCustomerId;
  final String? currentCustomerName;
  final List<AiContextItem> currentItems;

  Map<String, dynamic> toJson() => {
        if (currentPage != null) 'currentPage': currentPage,
        if (currentOrderId != null) 'currentOrderId': currentOrderId,
        if (currentCustomerId != null) 'currentCustomerId': currentCustomerId,
        if (currentCustomerName != null) 'currentCustomerName': currentCustomerName,
        'currentItems': currentItems.map((e) => e.toJson()).toList(),
      };
}

class AiContextItem {
  const AiContextItem({
    this.itemCode,
    this.productId,
    this.productName,
    this.spec,
    this.qty,
    this.uom,
    this.rate,
  });

  final String? itemCode;
  final String? productId;
  final String? productName;
  final String? spec;
  final Decimal? qty;
  final String? uom;
  final Decimal? rate;

  Map<String, dynamic> toJson() => {
        if (itemCode != null) 'itemCode': itemCode,
        if (productId != null) 'productId': productId,
        if (productName != null) 'productName': productName,
        if (spec != null) 'spec': spec,
        if (qty != null) 'qty': qty.toString(),
        if (uom != null) 'uom': uom,
        if (rate != null) 'rate': rate.toString(),
      };
}

class AiActionResponse {
  const AiActionResponse({
    required this.actionId,
    this.actionType,
    required this.status,
    this.targetPage,
    this.ambiguities = const [],
    this.payload = const {},
    this.message,
    this.provider,
    this.model,
  });

  final String actionId;
  final String? actionType;
  final String status;
  final String? targetPage;
  final List<AiAmbiguity> ambiguities;
  final Map<String, dynamic> payload;
  final String? message;
  final String? provider;
  final String? model;

  factory AiActionResponse.fromJson(Map<String, dynamic> json) {
    final ambiguitiesRaw = json['ambiguities'];
    return AiActionResponse(
      actionId: json['actionId']?.toString() ?? '',
      actionType: json['actionType']?.toString(),
      status: json['status']?.toString() ?? 'FAILED',
      targetPage: json['targetPage']?.toString(),
      ambiguities: ambiguitiesRaw is List
          ? ambiguitiesRaw
              .whereType<Map>()
              .map((e) => AiAmbiguity.fromJson(Map<String, dynamic>.from(e)))
              .toList()
          : const [],
      payload: json['payload'] is Map
          ? Map<String, dynamic>.from(json['payload'] as Map)
          : const {},
      message: json['message']?.toString(),
      provider: json['provider']?.toString(),
      model: json['model']?.toString(),
    );
  }
}

class AiAmbiguity {
  const AiAmbiguity({
    required this.field,
    required this.expression,
    this.candidates = const [],
  });

  final String field;
  final String expression;
  final List<Map<String, dynamic>> candidates;

  factory AiAmbiguity.fromJson(Map<String, dynamic> json) {
    final candidatesRaw = json['candidates'];
    return AiAmbiguity(
      field: json['field']?.toString() ?? '',
      expression: json['expression']?.toString() ?? '',
      candidates: candidatesRaw is List
          ? candidatesRaw
              .whereType<Map>()
              .map((e) => Map<String, dynamic>.from(e))
              .toList()
          : const [],
    );
  }
}

Decimal? aiDecimal(Object? value) => decimalFromJson(value);
