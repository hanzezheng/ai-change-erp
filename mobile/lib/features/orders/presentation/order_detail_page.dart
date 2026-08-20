import 'dart:async';

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
  bool _summaryLoading = false;
  String? _summaryError;
  String? _summaryTraceId;
  bool _historyLoading = false;
  bool _historyLoaded = false;
  String? _historyError;
  String? _historyTraceId;
  int _loadGeneration = 0;

  @override
  void initState() {
    super.initState();
    _load();
  }

  Future<void> _load() async {
    final generation = ++_loadGeneration;
    setState(() {
      _loading = true;
      _error = null;
      _traceId = null;
      _summary = null;
      _summaryLoading = true;
      _summaryError = null;
      _summaryTraceId = null;
      _payments = const [];
      _historyLoading = true;
      _historyLoaded = false;
      _historyError = null;
      _historyTraceId = null;
    });
    try {
      final orders = ref.read(orderRepositoryProvider);
      final order = await orders.getById(widget.orderId);
      if (!mounted || generation != _loadGeneration) {
        return;
      }
      setState(() {
        _order = order;
        _loading = false;
      });
      // The order is still useful if either payment endpoint is unavailable.
      // Keep both payment requests independent so one failure cannot turn the
      // other section into a misleading empty state.
      unawaited(_loadSummary(generation));
      unawaited(_loadHistory(generation));
    } on ApiException catch (error) {
      if (!mounted || generation != _loadGeneration) {
        return;
      }
      setState(() {
        _error = error.userMessage;
        _traceId = error.traceId;
        _loading = false;
        _summaryLoading = false;
        _historyLoading = false;
      });
    }
  }

  Future<void> _loadSummary(int generation) async {
    try {
      final summary = await ref
          .read(orderRepositoryProvider)
          .paymentSummary(widget.orderId);
      if (!mounted || generation != _loadGeneration) {
        return;
      }
      setState(() {
        _summary = summary;
        _summaryLoading = false;
        _summaryError = null;
        _summaryTraceId = null;
      });
    } on ApiException catch (error) {
      if (!mounted || generation != _loadGeneration) {
        return;
      }
      setState(() {
        _summaryLoading = false;
        _summaryError = error.userMessage;
        _summaryTraceId = error.traceId;
      });
    } catch (_) {
      if (!mounted || generation != _loadGeneration) {
        return;
      }
      setState(() {
        _summaryLoading = false;
        _summaryError = '收款汇总加载失败';
        _summaryTraceId = null;
      });
    }
  }

  Future<void> _loadHistory(int generation) async {
    try {
      final history = (await ref
              .read(paymentRepositoryProvider)
              .list(relatedOrderId: widget.orderId))
          .content;
      if (!mounted || generation != _loadGeneration) {
        return;
      }
      setState(() {
        _payments = history;
        _historyLoading = false;
        _historyLoaded = true;
        _historyError = null;
        _historyTraceId = null;
      });
    } on ApiException catch (error) {
      if (!mounted || generation != _loadGeneration) {
        return;
      }
      setState(() {
        _historyLoading = false;
        _historyLoaded = false;
        _historyError = error.userMessage;
        _historyTraceId = error.traceId;
      });
    } catch (_) {
      if (!mounted || generation != _loadGeneration) {
        return;
      }
      setState(() {
        _historyLoading = false;
        _historyLoaded = false;
        _historyError = '收款记录加载失败';
        _historyTraceId = null;
      });
    }
  }

  Future<void> _retrySummary() {
    if (mounted) {
      setState(() {
        _summaryLoading = true;
        _summaryError = null;
        _summaryTraceId = null;
      });
    }
    return _loadSummary(_loadGeneration);
  }

  Future<void> _retryHistory() {
    if (mounted) {
      setState(() {
        _historyLoading = true;
        _historyError = null;
        _historyTraceId = null;
      });
    }
    return _loadHistory(_loadGeneration);
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
      ScaffoldMessenger.of(
        context,
      ).showSnackBar(SnackBar(content: Text(error.userMessage)));
    } finally {
      if (mounted) {
        setState(() => _confirmingId = null);
      }
    }
  }

  Widget _paymentSummarySection(Order order) {
    // Order already carries an authoritative payment snapshot.  It is safe to
    // show that snapshot while the richer summary endpoint is loading or if it
    // fails; never replace it with invented zeroes.
    final orderTotal = _summary?.orderTotal ?? order.totalAmount;
    final confirmedPaid = _summary?.confirmedPaid ?? order.confirmedPaid;
    final remaining = _summary?.remainingToCollect ?? order.remainingToCollect;
    return ColoredBox(
      color: AppColors.surface,
      child: Padding(
        padding: const EdgeInsets.fromLTRB(16, 12, 16, 12),
        child: Column(
          children: [
            _kv('订单金额', MoneyFormat.cny(orderTotal)),
            _kv('已收', MoneyFormat.cny(confirmedPaid)),
            _kv('未收', MoneyFormat.cny(remaining)),
            if (_summaryLoading)
              const Align(
                alignment: Alignment.centerLeft,
                child: Padding(
                  padding: EdgeInsets.only(top: 6),
                  child: Text('正在刷新收款汇总…', style: AppTextStyles.tertiary),
                ),
              ),
            if (_summaryError != null)
              Align(
                alignment: Alignment.centerLeft,
                child: Padding(
                  padding: const EdgeInsets.only(top: 6),
                  child: Row(
                    children: [
                      Expanded(
                        child: Text(
                          '收款汇总加载失败，已显示订单记录${_summaryError!.isEmpty ? '' : '：$_summaryError'}',
                          style: AppTextStyles.tertiary.copyWith(
                            color: AppColors.danger,
                          ),
                        ),
                      ),
                      TextButton(
                        onPressed: _retrySummary,
                        child: const Text('刷新'),
                      ),
                    ],
                  ),
                ),
              ),
            if (_summaryTraceId != null)
              Align(
                alignment: Alignment.centerLeft,
                child: Text(
                  '错误编号 $_summaryTraceId',
                  style: AppTextStyles.tertiary,
                ),
              ),
          ],
        ),
      ),
    );
  }

  Widget _paymentHistorySection() {
    return Column(
      crossAxisAlignment: CrossAxisAlignment.start,
      children: [
        const Padding(
          padding: EdgeInsets.fromLTRB(16, 16, 16, 6),
          child: Text('收款记录', style: AppTextStyles.section),
        ),
        ColoredBox(
          color: AppColors.surface,
          child: _historyLoading
              ? const Padding(
                  padding: EdgeInsets.all(20),
                  child: Row(
                    children: [
                      SizedBox(
                        width: 16,
                        height: 16,
                        child: CircularProgressIndicator(strokeWidth: 2),
                      ),
                      SizedBox(width: 10),
                      Text('收款记录加载中…', style: AppTextStyles.secondary),
                    ],
                  ),
                )
              : _historyError != null
                  ? Padding(
                      padding: const EdgeInsets.fromLTRB(16, 16, 16, 12),
                      child: Column(
                        crossAxisAlignment: CrossAxisAlignment.start,
                        children: [
                          Text(
                            '收款记录加载失败',
                            style: AppTextStyles.secondary.copyWith(
                              color: AppColors.danger,
                            ),
                          ),
                          if (_historyError!.isNotEmpty) ...[
                            const SizedBox(height: 4),
                            Text(_historyError!, style: AppTextStyles.tertiary),
                          ],
                          if (_historyTraceId != null) ...[
                            const SizedBox(height: 4),
                            Text(
                              '错误编号 $_historyTraceId',
                              style: AppTextStyles.tertiary,
                            ),
                          ],
                          const SizedBox(height: 8),
                          SecondaryButton(
                            label: '重试',
                            onPressed: _retryHistory,
                            expanded: false,
                          ),
                        ],
                      ),
                    )
                  : !_historyLoaded
                      ? const SizedBox.shrink()
                      : _payments.isEmpty
                          ? const Padding(
                              padding: EdgeInsets.all(20),
                              child: Text('暂无收款记录',
                                  style: AppTextStyles.secondary),
                            )
                          : Column(
                              children: [
                                for (final payment in _payments)
                                  _paymentHistoryRow(payment),
                              ],
                            ),
        ),
      ],
    );
  }

  Widget _paymentHistoryRow(Payment payment) {
    return ListTile(
      title: Text(
        MoneyFormat.cny(payment.amount),
        style: AppTextStyles.bodyStrong,
      ),
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
                onPressed: _confirmingId == payment.paymentId
                    ? null
                    : () => _confirm(payment),
                child: _confirmingId == payment.paymentId
                    ? const SizedBox(
                        width: 12,
                        height: 12,
                        child: CircularProgressIndicator(strokeWidth: 2),
                      )
                    : const Text('确认到账'),
              ),
            ),
          ],
        ],
      ),
    );
  }

  @override
  Widget build(BuildContext context) {
    if (_loading) {
      return const AppScaffold(title: '订单详情', body: LoadingState());
    }
    if (_error != null || _order == null) {
      return AppScaffold(
        title: '订单详情',
        body: ErrorState(
          message: _error ?? '订单不存在',
          traceId: _traceId,
          onRetry: _load,
        ),
      );
    }
    final order = _order!;
    final summary = _summary;
    final remaining = summary?.remainingToCollect ?? order.remainingToCollect;
    final canCollect =
        order.orderStatus == OrderStatus.submitted && remaining > Decimal.zero;

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
                        StatusBadge(
                          label: order.orderStatusLabel,
                          kind: orderStatusKind(order.orderStatusLabel),
                        ),
                        const SizedBox(width: 6),
                        StatusBadge(
                          label: order.paymentStatusLabel,
                          kind: paymentStatusKind(order.paymentStatusLabel),
                        ),
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
            _paymentSummarySection(order),
            _paymentHistorySection(),
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
            onPressed: () => context.push(
              '/orders/${Uri.encodeComponent(order.orderId)}/edit',
            ),
          ),
        ],
      );
    }
    if (!canCollect) {
      return null;
    }
    final paidSomething =
        (_summary?.confirmedPaid ?? order.confirmedPaid) > Decimal.zero;
    return BusinessActionBar(
      children: [
        PrimaryButton(
          label: paidSomething ? '补收尾款' : '记录收款',
          onPressed: () => context.push(
            '/orders/${Uri.encodeComponent(order.orderId)}/payment',
          ),
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
