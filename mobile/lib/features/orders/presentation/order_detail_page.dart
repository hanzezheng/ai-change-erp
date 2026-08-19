import 'package:decimal/decimal.dart';
import 'package:flutter/material.dart';
import 'package:flutter_riverpod/flutter_riverpod.dart';
import 'package:go_router/go_router.dart';

import '../../../app/theme/app_colors.dart';
import '../../../app/theme/app_text_styles.dart';
import '../../../core/api/api_exception.dart';
import '../../../core/utils/datetime_fmt.dart';
import '../../../core/utils/money.dart';
import '../../../core/widgets/app_scaffold.dart';
import '../../../core/widgets/buttons.dart';
import '../../../core/widgets/feedback.dart';
import '../../../core/widgets/status_badge.dart';
import '../../../core/widgets/transaction_list_row.dart';
import '../../feature_providers.dart';
import '../../payments/data/payment_models.dart';
import '../data/order_models.dart';

class OrderDetailPage extends ConsumerStatefulWidget {
  const OrderDetailPage({super.key, required this.orderId});

  final String orderId;

  @override
  ConsumerState<OrderDetailPage> createState() => _OrderDetailPageState();
}

class _OrderDetailPageState extends ConsumerState<OrderDetailPage> {
  Order? _order;
  OrderPaymentSummary? _summary;
  List<Payment> _payments = const [];
  bool _loading = true;
  String? _error;
  String? _traceId;
  String? _confirmingId;

  @override
  void initState() {
    super.initState();
    _load();
  }

  Future<void> _load() async {
    setState(() {
      _loading = true;
      _error = null;
    });
    try {
      final orders = ref.read(orderRepositoryProvider);
      final payments = ref.read(paymentRepositoryProvider);
      final order = await orders.getById(widget.orderId);
      OrderPaymentSummary? summary;
      List<Payment> history = const [];
      try {
        summary = await orders.paymentSummary(widget.orderId);
      } catch (_) {}
      try {
        history = (await payments.list(relatedOrderId: widget.orderId)).content;
      } catch (_) {}
      if (!mounted) {
        return;
      }
      setState(() {
        _order = order;
        _summary = summary;
        _payments = history;
        _loading = false;
      });
    } on ApiException catch (error) {
      setState(() {
        _error = error.userMessage;
        _traceId = error.traceId;
        _loading = false;
      });
    }
  }

  Future<void> _confirm(Payment payment) async {
    setState(() => _confirmingId = payment.paymentId);
    try {
      await ref.read(paymentRepositoryProvider).confirm(payment.paymentId);
      await _load();
    } on ApiException catch (error) {
      if (!mounted) {
        return;
      }
      ScaffoldMessenger.of(context).showSnackBar(SnackBar(content: Text(error.userMessage)));
    } finally {
      if (mounted) {
        setState(() => _confirmingId = null);
      }
    }
  }

  @override
  Widget build(BuildContext context) {
    if (_loading) {
      return const AppScaffold(title: '订单详情', body: LoadingState());
    }
    if (_error != null || _order == null) {
      return AppScaffold(
        title: '订单详情',
        body: ErrorState(message: _error ?? '订单不存在', traceId: _traceId, onRetry: _load),
      );
    }
    final order = _order!;
    final summary = _summary;
    final remaining = summary?.remainingToCollect ?? order.remainingToCollect;
    final canCollect = order.orderStatus == OrderStatus.submitted && remaining > Decimal.zero;

    return AppScaffold(
      title: '订单详情',
      body: RefreshIndicator(
        color: AppColors.primary,
        onRefresh: _load,
        child: ListView(
          children: [
            ColoredBox(
              color: AppColors.surface,
              child: Padding(
                padding: const EdgeInsets.all(16),
                child: Column(
                  crossAxisAlignment: CrossAxisAlignment.start,
                  children: [
                    Text(order.customerName, style: AppTextStyles.greeting),
                    const SizedBox(height: 8),
                    Row(
                      children: [
                        StatusBadge(label: order.orderStatusLabel, kind: orderStatusKind(order.orderStatusLabel)),
                        const SizedBox(width: 6),
                        StatusBadge(label: order.paymentStatusLabel, kind: paymentStatusKind(order.paymentStatusLabel)),
                      ],
                    ),
                  ],
                ),
              ),
            ),
            const SizedBox(height: 8),
            ColoredBox(
              color: AppColors.surface,
              child: Column(
                children: [
                  for (var i = 0; i < order.items.length; i++) ...[
                    TransactionListRow(
                      title: _title(order.items[i]),
                      subtitle:
                          '${order.items[i].qty} ${order.items[i].uom} × ${MoneyFormat.cnyWithUom(order.items[i].rate, order.items[i].uom)}',
                      amount: order.items[i].amount,
                    ),
                    if (i != order.items.length - 1) const Divider(height: 1),
                  ],
                ],
              ),
            ),
            if (summary != null)
              ColoredBox(
                color: AppColors.surface,
                child: Padding(
                  padding: const EdgeInsets.fromLTRB(16, 12, 16, 12),
                  child: Column(
                    children: [
                      _kv('订单金额', MoneyFormat.cny(summary.orderTotal)),
                      _kv('已收', MoneyFormat.cny(summary.confirmedPaid)),
                      _kv('未收', MoneyFormat.cny(summary.remainingToCollect)),
                    ],
                  ),
                ),
              ),
            if (_payments.isNotEmpty) ...[
              const Padding(
                padding: EdgeInsets.fromLTRB(16, 16, 16, 6),
                child: Align(
                  alignment: Alignment.centerLeft,
                  child: Text('收款记录', style: AppTextStyles.section),
                ),
              ),
              ColoredBox(
                color: AppColors.surface,
                child: Column(
                  children: [
                    for (final payment in _payments)
                      ListTile(
                        title: Text(MoneyFormat.cny(payment.amount), style: AppTextStyles.bodyStrong),
                        subtitle: Text(
                          '${payment.paymentMethodName} · ${payment.createdAt == null ? '' : BusinessTime.formatDateTime(payment.createdAt!)}',
                          style: AppTextStyles.tertiary,
                        ),
                        trailing: Row(
                          mainAxisSize: MainAxisSize.min,
                          children: [
                            StatusBadge(
                              label: payment.paymentStatusLabel,
                              kind: paymentStatusKind(payment.paymentStatusLabel),
                            ),
                            if (payment.isPending) ...[
                              const SizedBox(width: 8),
                              SizedBox(
                                height: 32,
                                child: OutlinedButton(
                                  onPressed: _confirmingId == payment.paymentId ? null : () => _confirm(payment),
                                  child: _confirmingId == payment.paymentId
                                      ? const SizedBox(width: 12, height: 12, child: CircularProgressIndicator(strokeWidth: 2))
                                      : const Text('确认到账'),
                                ),
                              ),
                            ],
                          ],
                        ),
                      ),
                  ],
                ),
              ),
            ],
            const SizedBox(height: 80),
          ],
        ),
      ),
      bottomAction: _bottom(order, remaining, canCollect),
    );
  }

  Widget? _bottom(Order order, remaining, bool canCollect) {
    if (order.isDraft) {
      return BusinessActionBar(
        children: [
          PrimaryButton(
            label: '编辑草稿',
            onPressed: () => context.push('/orders/${Uri.encodeComponent(order.orderId)}/edit'),
          ),
        ],
      );
    }
    if (!canCollect) {
      return null;
    }
    final paidSomething = (_summary?.confirmedPaid ?? order.confirmedPaid) > Decimal.zero;
    return BusinessActionBar(
      children: [
        PrimaryButton(
          label: paidSomething ? '补收尾款' : '记录收款',
          onPressed: () => context.push('/orders/${Uri.encodeComponent(order.orderId)}/payment'),
        ),
      ],
    );
  }

  Widget _kv(String label, String value) {
    return Padding(
      padding: const EdgeInsets.symmetric(vertical: 4),
      child: Row(
        children: [
          Text(label, style: AppTextStyles.secondary),
          const Spacer(),
          Text(value, style: AppTextStyles.bodyStrong),
        ],
      ),
    );
  }

  String _title(OrderItem item) {
    if (item.spec == null || item.spec!.isEmpty) {
      return item.productName;
    }
    return '${item.productName} · ${item.spec}';
  }
}
