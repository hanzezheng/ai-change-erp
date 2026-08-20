import 'package:decimal/decimal.dart';
import 'package:flutter/material.dart';
import 'package:flutter/services.dart';
import 'package:flutter_riverpod/flutter_riverpod.dart';
import 'package:go_router/go_router.dart';
import 'package:uuid/uuid.dart';

import '../../../app/theme/app_colors.dart';
import '../../../app/theme/app_radius.dart';
import '../../../app/theme/app_text_styles.dart';
import '../../../core/api/api_exception.dart';
import '../../../core/utils/decimal_json.dart';
import '../../../core/utils/money.dart';
import '../../../core/widgets/app_scaffold.dart';
import '../../../core/widgets/buttons.dart';
import '../../../core/widgets/feedback.dart';
import '../../../core/widgets/status_badge.dart';
import '../../feature_providers.dart';
import '../../orders/data/order_models.dart';
import '../data/payment_models.dart';

enum _PaymentFlowState {
  editingNewPayment,
  draftSavedAwaitingConfirmation,
  confirmed,
}

class PaymentPage extends ConsumerStatefulWidget {
  const PaymentPage({super.key, required this.orderId});

  final String orderId;

  @override
  ConsumerState<PaymentPage> createState() => _PaymentPageState();
}

class _PaymentPageState extends ConsumerState<PaymentPage> {
  Order? _order;
  OrderPaymentSummary? _summary;
  List<PaymentMethod> _methods = const [];
  final _amount = TextEditingController();
  String? _methodId;
  bool _confirmed = true;
  bool _loading = true;
  bool _busy = false;
  String? _error;
  String? _traceId;
  Payment? _createdPayment;
  late final String _idempotencyKey;
  bool _unknown = false;
  _PaymentFlowState _flowState = _PaymentFlowState.editingNewPayment;

  @override
  void initState() {
    super.initState();
    _idempotencyKey = const Uuid().v4();
    _load();
  }

  @override
  void dispose() {
    _amount.dispose();
    super.dispose();
  }

  Future<void> _load() async {
    setState(() {
      _loading = true;
      _error = null;
    });
    try {
      final order =
          await ref.read(orderRepositoryProvider).getById(widget.orderId);
      final summary = await ref
          .read(orderRepositoryProvider)
          .paymentSummary(widget.orderId);
      final methods = await ref.read(paymentRepositoryProvider).methods();
      if (!mounted) {
        return;
      }
      _order = order;
      _summary = summary;
      _methods = methods;
      if (_createdPayment == null) {
        _amount.text = summary.remainingToCollect.toString();
        _methodId = methods.isEmpty ? null : methods.first.paymentMethodId;
      }
      _loading = false;
      setState(() {});
    } on ApiException catch (error) {
      if (!mounted) {
        return;
      }
      setState(() {
        _error = error.userMessage;
        _traceId = error.traceId;
        _loading = false;
      });
    }
  }

  Future<void> _submit() async {
    final order = _order;

    // Once ERPNext has created the Payment Entry draft there is no update API
    // in this phase.  A retry must therefore confirm that same immutable DTO,
    // never create another payment from edited controllers.
    if (_flowState == _PaymentFlowState.draftSavedAwaitingConfirmation) {
      final payment = _createdPayment;
      if (payment == null || _busy) {
        return;
      }
      await _confirmExisting(payment);
      return;
    }
    if (_flowState == _PaymentFlowState.confirmed || _busy) {
      return;
    }

    final amount = decimalFromJson(_amount.text);
    if (order == null || amount == null || amount <= Decimal.zero) {
      setState(() => _error = '金额必须大于 0');
      return;
    }
    if (_methods.isEmpty || _methodId == null) {
      return;
    }
    if (_unknown) {
      return;
    }
    setState(() {
      _busy = true;
      _error = null;
    });
    final repo = ref.read(paymentRepositoryProvider);
    try {
      final created = await repo.createDraft(
        customerId: order.customerId,
        relatedOrderId: order.orderId,
        amount: amount,
        paymentMethodId: _methodId!,
        idempotencyKey: _idempotencyKey,
      );
      if (mounted) {
        setState(() {
          _createdPayment = created;
          _flowState = _PaymentFlowState.draftSavedAwaitingConfirmation;
        });
      } else {
        _createdPayment = created;
        _flowState = _PaymentFlowState.draftSavedAwaitingConfirmation;
      }
      if (_confirmed) {
        await _confirmExisting(created, keepBusy: true);
        return;
      }
      if (mounted) {
        ScaffoldMessenger.of(context).showSnackBar(
          SnackBar(content: Text(_confirmed ? '收款成功' : '已保存为待确认')),
        );
        context.go('/orders/${Uri.encodeComponent(order.orderId)}');
      }
    } on ApiException catch (error) {
      if (mounted) {
        setState(() {
          _unknown = error.code == 'IDEMPOTENCY_OUTCOME_UNKNOWN';
          _error = error.userMessage;
          _traceId = error.traceId;
        });
      }
    } finally {
      if (mounted) {
        setState(() => _busy = false);
      }
    }
  }

  Future<void> _confirmExisting(
    Payment payment, {
    bool keepBusy = false,
  }) async {
    if (!keepBusy) {
      setState(() {
        _busy = true;
        _error = null;
        _traceId = null;
      });
    }
    try {
      final confirmed =
          await ref.read(paymentRepositoryProvider).confirm(payment.paymentId);
      if (mounted) {
        setState(() {
          _createdPayment = confirmed;
          _flowState = _PaymentFlowState.confirmed;
        });
      } else {
        _createdPayment = confirmed;
        _flowState = _PaymentFlowState.confirmed;
      }
      if (mounted) {
        ScaffoldMessenger.of(
          context,
        ).showSnackBar(const SnackBar(content: Text('收款成功')));
        context.go('/orders/${Uri.encodeComponent(payment.relatedOrderId)}');
      }
    } on ApiException catch (error) {
      // Keep the created DTO and its paymentId.  The user can retry this
      // confirmation or return to the order; a second POST /payments is never
      // issued.  Preserve the backend-safe message for payment contract
      // errors, while explaining the frozen draft for transient failures.
      if (mounted) {
        setState(() {
          _flowState = _PaymentFlowState.draftSavedAwaitingConfirmation;
          _error = _confirmationError(error);
          _traceId = error.traceId;
        });
      }
    } finally {
      if (!keepBusy && mounted) {
        setState(() => _busy = false);
      }
    }
  }

  String _confirmationError(ApiException error) {
    if (error.code == 'PAYMENT_NOT_SUPPORTED' ||
        error.code == 'PAYMENT_INVALID') {
      return error.userMessage;
    }
    final message = error.userMessage;
    return message.isEmpty ? '收款已保存为待确认，确认到账失败' : '收款已保存为待确认，确认到账失败：$message';
  }

  @override
  Widget build(BuildContext context) {
    if (_loading) {
      return const AppScaffold(title: '记录收款', body: LoadingState());
    }
    if (_order == null) {
      return AppScaffold(
        title: '记录收款',
        body: ErrorState(
          message: _error ?? '无法加载订单',
          traceId: _traceId,
          onRetry: _load,
        ),
      );
    }
    final order = _order!;
    final remaining = _summary?.remainingToCollect;
    final frozenPayment = _createdPayment;
    final awaitingConfirmation =
        _flowState == _PaymentFlowState.draftSavedAwaitingConfirmation;
    return AppScaffold(
      title: '记录收款',
      body: ListView(
        children: [
          ColoredBox(
            color: AppColors.surface,
            child: Column(
              children: [
                _kv(
                  '客户',
                  frozenPayment?.customerName ?? order.customerName,
                  readOnly: true,
                ),
                const Divider(height: 1),
                _kv(
                  '关联订单',
                  frozenPayment?.relatedOrderId ?? order.orderId,
                  readOnly: true,
                ),
                const Divider(height: 1),
                Padding(
                  padding: const EdgeInsets.symmetric(horizontal: 16),
                  child: Row(
                    children: [
                      const SizedBox(
                        width: 64,
                        child: Text('金额', style: AppTextStyles.secondary),
                      ),
                      Expanded(
                        child: frozenPayment == null
                            ? TextField(
                                controller: _amount,
                                keyboardType:
                                    const TextInputType.numberWithOptions(
                                  decimal: true,
                                ),
                                inputFormatters: [
                                  FilteringTextInputFormatter.allow(
                                    RegExp(r'[0-9.]'),
                                  ),
                                ],
                                decoration: const InputDecoration(
                                  hintText: '收款金额',
                                  border: InputBorder.none,
                                  enabledBorder: InputBorder.none,
                                  focusedBorder: InputBorder.none,
                                  filled: false,
                                ),
                              )
                            : Text(
                                MoneyFormat.cny(frozenPayment.amount),
                                style: AppTextStyles.bodyStrong,
                              ),
                      ),
                    ],
                  ),
                ),
              ],
            ),
          ),
          const SizedBox(height: 8),
          ColoredBox(
            color: AppColors.surface,
            child: Padding(
              padding: const EdgeInsets.fromLTRB(16, 14, 16, 14),
              child: Column(
                crossAxisAlignment: CrossAxisAlignment.start,
                children: [
                  const Text('收款方式', style: AppTextStyles.tertiary),
                  const SizedBox(height: 10),
                  if (frozenPayment != null)
                    _frozenMethodAndStatus(frozenPayment)
                  else if (_methods.isEmpty)
                    const Text('当前企业尚未配置可用收款方式', style: AppTextStyles.secondary)
                  else
                    Wrap(
                      spacing: 8,
                      children: [
                        for (final method in _methods)
                          ChoiceChip(
                            label: Text(method.paymentMethodName),
                            selected: _methodId == method.paymentMethodId,
                            selectedColor: AppColors.primaryTint,
                            side: BorderSide(
                              color: _methodId == method.paymentMethodId
                                  ? AppColors.primary
                                  : AppColors.border,
                            ),
                            labelStyle: AppTextStyles.caption.copyWith(
                              color: _methodId == method.paymentMethodId
                                  ? AppColors.primary
                                  : AppColors.textSecondary,
                              fontWeight: FontWeight.w600,
                            ),
                            shape: RoundedRectangleBorder(
                              borderRadius: BorderRadius.circular(AppRadius.sm),
                            ),
                            onSelected: (_) => setState(
                              () => _methodId = method.paymentMethodId,
                            ),
                          ),
                      ],
                    ),
                  if (frozenPayment == null) ...[
                    const SizedBox(height: 16),
                    const Text('到账状态', style: AppTextStyles.tertiary),
                    const SizedBox(height: 10),
                    Wrap(
                      spacing: 8,
                      children: [
                        _statusChip(
                          '待确认',
                          !_confirmed,
                          () => setState(() => _confirmed = false),
                        ),
                        _statusChip(
                          '已到账',
                          _confirmed,
                          () => setState(() => _confirmed = true),
                        ),
                      ],
                    ),
                  ],
                ],
              ),
            ),
          ),
          if (remaining != null)
            Padding(
              padding: const EdgeInsets.all(16),
              child: Text(
                '未收 ${MoneyFormat.cny(remaining)}',
                style: AppTextStyles.secondary,
              ),
            ),
          if (_error != null)
            Padding(
              padding: const EdgeInsets.symmetric(horizontal: 16),
              child: Text(
                _error!,
                style: AppTextStyles.caption.copyWith(color: AppColors.danger),
              ),
            ),
          if (_traceId != null)
            Padding(
              padding: const EdgeInsets.fromLTRB(16, 4, 16, 0),
              child: Text('错误编号 $_traceId', style: AppTextStyles.tertiary),
            ),
          if (_unknown || awaitingConfirmation)
            Padding(
              padding: const EdgeInsets.all(16),
              child: SecondaryButton(
                label: '返回订单详情',
                onPressed: () =>
                    context.go('/orders/${Uri.encodeComponent(order.orderId)}'),
              ),
            ),
        ],
      ),
      bottomAction: BusinessActionBar(
        children: [
          SecondaryButton(label: '取消', onPressed: () => context.pop()),
          PrimaryButton(
            label: awaitingConfirmation ? '确认到账' : '保存收款',
            loading: _busy,
            onPressed:
                _busy || (_methods.isEmpty && frozenPayment == null) || _unknown
                    ? null
                    : _submit,
          ),
        ],
      ),
    );
  }

  Widget _statusChip(String label, bool selected, VoidCallback onTap) {
    return ChoiceChip(
      label: Text(label),
      selected: selected,
      selectedColor: AppColors.primaryTint,
      side: BorderSide(color: selected ? AppColors.primary : AppColors.border),
      labelStyle: AppTextStyles.caption.copyWith(
        color: selected ? AppColors.primary : AppColors.textSecondary,
        fontWeight: FontWeight.w600,
      ),
      shape: RoundedRectangleBorder(
        borderRadius: BorderRadius.circular(AppRadius.sm),
      ),
      onSelected: (_) => onTap(),
    );
  }

  Widget _frozenMethodAndStatus(Payment payment) {
    return Column(
      crossAxisAlignment: CrossAxisAlignment.start,
      children: [
        Text(payment.paymentMethodName, style: AppTextStyles.bodyStrong),
        const SizedBox(height: 16),
        const Text('到账状态', style: AppTextStyles.tertiary),
        const SizedBox(height: 10),
        StatusBadge(
          label: payment.paymentStatusLabel,
          kind: paymentStatusKind(payment.paymentStatusLabel),
        ),
      ],
    );
  }

  Widget _kv(String label, String value, {bool readOnly = false}) {
    return Padding(
      padding: const EdgeInsets.symmetric(horizontal: 16, vertical: 14),
      child: Row(
        children: [
          SizedBox(
            width: 64,
            child: Text(label, style: AppTextStyles.secondary),
          ),
          Expanded(child: Text(value, style: AppTextStyles.bodyStrong)),
        ],
      ),
    );
  }
}
