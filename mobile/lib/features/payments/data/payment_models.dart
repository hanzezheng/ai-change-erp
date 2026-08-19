import 'package:decimal/decimal.dart';

import '../../../core/utils/datetime_fmt.dart';
import '../../../core/utils/decimal_json.dart';

enum PaymentConfirmationStatus { pendingConfirmation, confirmed, cancelled }

PaymentConfirmationStatus parsePaymentConfirmation(Object? value) {
  switch (value?.toString()) {
    case 'CONFIRMED':
      return PaymentConfirmationStatus.confirmed;
    case 'CANCELLED':
      return PaymentConfirmationStatus.cancelled;
    default:
      return PaymentConfirmationStatus.pendingConfirmation;
  }
}

String paymentConfirmationLabel(PaymentConfirmationStatus status, [String? backend]) {
  if (backend != null && backend.isNotEmpty) {
    return backend;
  }
  switch (status) {
    case PaymentConfirmationStatus.pendingConfirmation:
      return '待确认';
    case PaymentConfirmationStatus.confirmed:
      return '已到账';
    case PaymentConfirmationStatus.cancelled:
      return '已取消';
  }
}

class PaymentMethod {
  const PaymentMethod({
    required this.paymentMethodId,
    required this.paymentMethodName,
  });

  final String paymentMethodId;
  final String paymentMethodName;

  factory PaymentMethod.fromJson(Map<String, dynamic> json) {
    return PaymentMethod(
      paymentMethodId: json['paymentMethodId']?.toString() ?? '',
      paymentMethodName: json['paymentMethodName']?.toString() ?? '',
    );
  }
}

class Payment {
  const Payment({
    required this.paymentId,
    required this.customerId,
    required this.customerName,
    required this.relatedOrderId,
    required this.amount,
    required this.paymentMethodId,
    required this.paymentMethodName,
    required this.paymentStatus,
    required this.paymentStatusLabel,
    this.referenceNo,
    this.referenceDate,
    this.createdAt,
    this.updatedAt,
  });

  final String paymentId;
  final String customerId;
  final String customerName;
  final String relatedOrderId;
  final Decimal amount;
  final String paymentMethodId;
  final String paymentMethodName;
  final PaymentConfirmationStatus paymentStatus;
  final String paymentStatusLabel;
  final String? referenceNo;
  final DateTime? referenceDate;
  final DateTime? createdAt;
  final DateTime? updatedAt;

  bool get isPending => paymentStatus == PaymentConfirmationStatus.pendingConfirmation;

  factory Payment.fromJson(Map<String, dynamic> json) {
    return Payment(
      paymentId: json['paymentId']?.toString() ?? '',
      customerId: json['customerId']?.toString() ?? '',
      customerName: json['customerName']?.toString() ?? '',
      relatedOrderId: json['relatedOrderId']?.toString() ?? '',
      amount: decimalFromJsonRequired(json['amount']),
      paymentMethodId: json['paymentMethodId']?.toString() ?? '',
      paymentMethodName: json['paymentMethodName']?.toString() ?? '',
      paymentStatus: parsePaymentConfirmation(json['paymentStatus']),
      paymentStatusLabel: json['paymentStatusLabel']?.toString() ?? '',
      referenceNo: json['referenceNo']?.toString(),
      referenceDate: json['referenceDate'] == null ? null : DateTime.tryParse(json['referenceDate'].toString()),
      createdAt: BusinessTime.tryParseInstant(json['createdAt']),
      updatedAt: BusinessTime.tryParseInstant(json['updatedAt']),
    );
  }
}
