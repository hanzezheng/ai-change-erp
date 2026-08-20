import 'package:decimal/decimal.dart';

import '../../../core/utils/datetime_fmt.dart';
import '../../../core/utils/decimal_json.dart';

enum OrderStatus { draft, submitted, completed, cancelled }

OrderStatus parseOrderStatus(Object? value) {
  switch (value?.toString()) {
    case 'SUBMITTED':
      return OrderStatus.submitted;
    case 'COMPLETED':
      return OrderStatus.completed;
    case 'CANCELLED':
      return OrderStatus.cancelled;
    default:
      return OrderStatus.draft;
  }
}

String orderStatusApi(OrderStatus status) {
  switch (status) {
    case OrderStatus.draft:
      return 'DRAFT';
    case OrderStatus.submitted:
      return 'SUBMITTED';
    case OrderStatus.completed:
      return 'COMPLETED';
    case OrderStatus.cancelled:
      return 'CANCELLED';
  }
}

String orderStatusLabelOf(OrderStatus status, [String? backendLabel]) {
  if (backendLabel != null && backendLabel.isNotEmpty) {
    return backendLabel;
  }
  switch (status) {
    case OrderStatus.draft:
      return '草稿';
    case OrderStatus.submitted:
      return '已提交';
    case OrderStatus.completed:
      return '已完成';
    case OrderStatus.cancelled:
      return '已取消';
  }
}

enum PaymentCollectionStatus { unpaid, partial, paid }

PaymentCollectionStatus parsePaymentCollection(Object? value) {
  switch (value?.toString()) {
    case 'PARTIAL':
      return PaymentCollectionStatus.partial;
    case 'PAID':
      return PaymentCollectionStatus.paid;
    default:
      return PaymentCollectionStatus.unpaid;
  }
}

String paymentCollectionLabel(PaymentCollectionStatus status, [String? backendLabel]) {
  if (backendLabel != null && backendLabel.isNotEmpty) {
    return backendLabel;
  }
  switch (status) {
    case PaymentCollectionStatus.unpaid:
      return '未收款';
    case PaymentCollectionStatus.partial:
      return '部分收款';
    case PaymentCollectionStatus.paid:
      return '已收款';
  }
}

class OrderSummary {
  const OrderSummary({
    required this.orderId,
    required this.customerId,
    required this.customerName,
    required this.itemSummary,
    required this.itemCount,
    required this.totalAmount,
    required this.orderStatus,
    required this.paymentStatus,
    required this.transactionTime,
  });

  final String orderId;
  final String customerId;
  final String customerName;
  final String itemSummary;
  final int itemCount;
  final Decimal totalAmount;
  final OrderStatus orderStatus;
  final PaymentCollectionStatus paymentStatus;
  final DateTime transactionTime;

  factory OrderSummary.fromJson(Map<String, dynamic> json) {
    return OrderSummary(
      orderId: json['orderId']?.toString() ?? '',
      customerId: json['customerId']?.toString() ?? '',
      customerName: json['customerName']?.toString() ?? '',
      itemSummary: json['itemSummary']?.toString() ?? '',
      itemCount: (json['itemCount'] as num?)?.toInt() ?? 0,
      totalAmount: decimalFromJsonRequired(json['totalAmount']),
      orderStatus: parseOrderStatus(json['orderStatus']),
      paymentStatus: parsePaymentCollection(json['paymentStatus']),
      transactionTime: BusinessTime.parseInstant(json['transactionTime']),
    );
  }
}

class OrderItem {
  const OrderItem({
    this.orderItemId,
    required this.productId,
    required this.itemCode,
    required this.productName,
    this.spec,
    required this.qty,
    required this.uom,
    required this.rate,
    required this.amount,
  });

  final String? orderItemId;
  final String productId;
  final String itemCode;
  final String productName;
  final String? spec;
  final Decimal qty;
  final String uom;
  final Decimal rate;
  final Decimal amount;

  factory OrderItem.fromJson(Map<String, dynamic> json) {
    return OrderItem(
      orderItemId: json['orderItemId']?.toString(),
      productId: json['productId']?.toString() ?? '',
      itemCode: json['itemCode']?.toString() ?? '',
      productName: json['productName']?.toString() ?? '',
      spec: json['spec']?.toString(),
      qty: decimalFromJsonRequired(json['qty']),
      uom: json['uom']?.toString() ?? '',
      rate: decimalFromJsonRequired(json['rate']),
      amount: decimalFromJsonRequired(json['amount']),
    );
  }
}

class Order {
  const Order({
    required this.orderId,
    required this.customerId,
    required this.customerName,
    required this.transactionDate,
    required this.items,
    required this.orderStatus,
    required this.orderStatusLabel,
    required this.paymentStatus,
    required this.paymentStatusLabel,
    required this.totalAmount,
    required this.confirmedPaid,
    required this.remainingToCollect,
    this.currency,
    this.createdAt,
    this.updatedAt,
  });

  final String orderId;
  final String customerId;
  final String customerName;
  final DateTime transactionDate;
  final List<OrderItem> items;
  final OrderStatus orderStatus;
  final String orderStatusLabel;
  final PaymentCollectionStatus paymentStatus;
  final String paymentStatusLabel;
  final Decimal totalAmount;
  final Decimal confirmedPaid;
  final Decimal remainingToCollect;
  final String? currency;
  final DateTime? createdAt;
  final DateTime? updatedAt;

  bool get isDraft => orderStatus == OrderStatus.draft;
  bool get isReadOnly => orderStatus != OrderStatus.draft;

  factory Order.fromJson(Map<String, dynamic> json) {
    return Order(
      orderId: json['orderId']?.toString() ?? '',
      customerId: json['customerId']?.toString() ?? '',
      customerName: json['customerName']?.toString() ?? '',
      transactionDate: DateTime.parse(json['transactionDate'].toString()),
      items: (json['items'] as List?)
              ?.whereType<Map>()
              .map((item) => OrderItem.fromJson(Map<String, dynamic>.from(item)))
              .toList() ??
          const [],
      orderStatus: parseOrderStatus(json['orderStatus']),
      orderStatusLabel: json['orderStatusLabel']?.toString() ?? '',
      paymentStatus: parsePaymentCollection(json['paymentStatus']),
      paymentStatusLabel: json['paymentStatusLabel']?.toString() ?? '',
      totalAmount: decimalFromJsonRequired(json['totalAmount']),
      confirmedPaid: decimalFromJsonRequired(json['confirmedPaid']),
      remainingToCollect: decimalFromJsonRequired(json['remainingToCollect']),
      currency: json['currency']?.toString(),
      createdAt: BusinessTime.tryParseInstant(json['createdAt']),
      updatedAt: BusinessTime.tryParseInstant(json['updatedAt']),
    );
  }
}

class OrderPaymentSummary {
  const OrderPaymentSummary({
    required this.orderTotal,
    required this.confirmedPaid,
    required this.remainingToCollect,
    required this.paymentStatus,
  });

  final Decimal orderTotal;
  final Decimal confirmedPaid;
  final Decimal remainingToCollect;
  final PaymentCollectionStatus paymentStatus;

  factory OrderPaymentSummary.fromJson(Map<String, dynamic> json) {
    return OrderPaymentSummary(
      orderTotal: decimalFromJsonRequired(json['orderTotal']),
      confirmedPaid: decimalFromJsonRequired(json['confirmedPaid']),
      remainingToCollect: decimalFromJsonRequired(json['remainingToCollect']),
      paymentStatus: parsePaymentCollection(json['paymentStatus']),
    );
  }
}
