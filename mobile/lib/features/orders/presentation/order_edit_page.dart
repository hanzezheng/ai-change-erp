import 'package:decimal/decimal.dart';
import 'package:flutter/material.dart';
import 'package:flutter_riverpod/flutter_riverpod.dart';
import 'package:go_router/go_router.dart';

import '../../../app/theme/app_colors.dart';
import '../../../app/theme/app_text_styles.dart';
import '../../../core/api/api_exception.dart';
import '../../../core/utils/decimal_json.dart';
import '../../../core/utils/money.dart';
import '../../../core/widgets/app_scaffold.dart';
import '../../../core/widgets/buttons.dart';
import '../../../core/widgets/feedback.dart';
import '../../../core/widgets/transaction_list_row.dart';
import '../../customers/presentation/customer_selector_sheet.dart';
import '../../feature_providers.dart';
import '../../ai/ai_draft_bridge.dart';
import '../../products/data/product_models.dart';
import '../../products/presentation/item_editor_sheet.dart';
import '../../products/presentation/product_selector_sheet.dart';
import 'order_edit_controller.dart';

class OrderEditSeed {
  const OrderEditSeed({
    this.customerId,
    this.customerName,
    this.items = const [],
  });

  final String? customerId;
  final String? customerName;
  final List<LocalOrderItem> items;

  factory OrderEditSeed.fromAiPayload(Map<String, dynamic> payload) {
    final customer = payload['customer'];
    String? customerId;
    String? customerName;
    if (customer is Map) {
      customerId = customer['customerId']?.toString();
      customerName = customer['customerName']?.toString();
    }
    final itemsRaw = payload['items'];
    final items = <LocalOrderItem>[];
    if (itemsRaw is List) {
      for (final raw in itemsRaw.whereType<Map>()) {
        final map = Map<String, dynamic>.from(raw);
        final itemCode = map['itemCode']?.toString() ?? '';
        if (itemCode.isEmpty) {
          continue;
        }
        final uom = map['uom']?.toString() ?? '';
        items.add(
          LocalOrderItem(
            productId: map['productId']?.toString() ?? itemCode,
            itemCode: itemCode,
            productName: map['productName']?.toString() ?? itemCode,
            spec: map['spec']?.toString(),
            qty: decimalFromJson(map['qty']),
            uom: uom.isEmpty ? '箱' : uom,
            rate: decimalFromJson(map['rate']) ?? Decimal.zero,
          ),
        );
      }
    }
    return OrderEditSeed(
      customerId: customerId,
      customerName: customerName,
      items: items,
    );
  }
}

class OrderEditPage extends ConsumerStatefulWidget {
  const OrderEditPage({
    super.key,
    this.orderId,
    this.customerId,
    this.customerName,
    this.seed,
  });

  final String? orderId;
  final String? customerId;
  final String? customerName;
  final OrderEditSeed? seed;

  @override
  ConsumerState<OrderEditPage> createState() => _OrderEditPageState();
}

class _OrderEditPageState extends ConsumerState<OrderEditPage> {
  late final OrderEditController _controller;
  late final AiDraftBridge _draftBridge;
  var _ready = false;
  String? _loadError;

  @override
  void initState() {
    super.initState();
    _draftBridge = ref.read(aiDraftBridgeProvider);
    _controller = OrderEditController(
      orders: ref.read(orderRepositoryProvider),
      loadLastDeal: ({required customerId, required itemCode, required uom}) {
        return ref
            .read(productRepositoryProvider)
            .lastDeal(customerId: customerId, itemCode: itemCode, uom: uom);
      },
    );
    _draftBridge.attach(_controller, () {
      if (mounted) {
        setState(() {});
      }
    });
    _bootstrap();
  }

  @override
  void dispose() {
    _draftBridge.detach(_controller);
    super.dispose();
  }

  Future<void> _bootstrap() async {
    if (widget.orderId == null) {
      final seed = widget.seed;
      _controller.startNew(
        customerId: seed?.customerId ?? widget.customerId,
        customerName: seed?.customerName ?? widget.customerName,
        items: seed?.items,
      );
      setState(() => _ready = true);
      return;
    }
    try {
      final order = await ref
          .read(orderRepositoryProvider)
          .getById(widget.orderId!);
      _controller.loadExisting(order);
      setState(() => _ready = true);
    } on ApiException catch (error) {
      setState(() => _loadError = error.userMessage);
    }
  }

  Future<bool> _confirmLeave() async {
    if (!_controller.state.dirty) {
      return true;
    }
    final ok = await showDialog<bool>(
      context: context,
      builder: (context) => AlertDialog(
        title: const Text('放弃未保存内容？'),
        content: const Text('当前修改尚未保存。'),
        actions: [
          TextButton(
            onPressed: () => Navigator.pop(context, false),
            child: const Text('继续编辑'),
          ),
          TextButton(
            onPressed: () => Navigator.pop(context, true),
            child: const Text('放弃'),
          ),
        ],
      ),
    );
    return ok == true;
  }

  Future<void> _pickCustomer() async {
    if (_controller.state.readOnly) {
      return;
    }
    final customer = await showCustomerSelector(context);
    if (customer == null) {
      return;
    }
    setState(() => _controller.selectCustomer(customer));
  }

  Future<void> _addProduct() async {
    final selected = await showProductSelector(
      context,
      customerId: _controller.state.customerId,
    );
    if (selected == null || !mounted) {
      return;
    }
    final item = _fromVariant(selected);
    final edited = await showItemEditor(
      context,
      item: item,
      onUomChanged: _controller.applyUom,
    );
    if (edited?.item == null) {
      return;
    }
    setState(() => _controller.addOrReplaceItem(edited!.item!));
  }

  Future<void> _editLine(int index) async {
    final current = _controller.state.items[index];
    final edited = await showItemEditor(
      context,
      item: current,
      onUomChanged: _controller.applyUom,
      allowDelete: true,
    );
    if (!mounted || edited == null) {
      return;
    }
    if (edited.deleted) {
      setState(() => _controller.removeItem(index));
      return;
    }
    if (edited.item != null) {
      setState(
        () => _controller.addOrReplaceItem(edited.item!, replaceIndex: index),
      );
    }
  }

  LocalOrderItem _fromVariant(ProductVariant variant) {
    final uom = variant.defaultUom;
    // A variant can expose a top-level price as well as per-UOM prices.  The
    // top-level value is only a safe fallback when the response does not have
    // an entry for this UOM (or explicitly identifies the same price UOM).
    // Never use a price for a different UOM as the new line's rate.
    final uomInfo = variant.uomInfo(uom);
    final ref = uomInfo != null
        ? uomInfo.referencePrice
        : (variant.priceUom == null || variant.priceUom == uom
              ? variant.referencePrice
              : null);
    return LocalOrderItem(
      productId: variant.productId,
      itemCode: variant.itemCode,
      productName: variant.productName,
      spec: variant.spec,
      uom: uom,
      // lastDealPrice is context for the operator, not the new transaction's
      // agreed rate.  If no reference price exists the rate stays blank so the
      // user must explicitly enter it.
      rate: ref,
      referencePrice: ref,
      lastDealPrice: variant.lastDealPrice,
      allowedUoms: variant.allowedUoms,
    );
  }

  Future<void> _save() async {
    final order = await _controller.saveDraft();
    setState(() {});
    if (!mounted) {
      return;
    }
    if (order != null) {
      ScaffoldMessenger.of(
        context,
      ).showSnackBar(const SnackBar(content: Text('草稿已保存')));
    }
  }

  Future<void> _submit() async {
    final order = await _controller.submit();
    setState(() {});
    if (!mounted) {
      return;
    }
    if (order != null) {
      context.go('/orders/${Uri.encodeComponent(order.orderId)}');
    }
  }

  Future<void> _refreshConflict() async {
    if (_controller.state.orderId == null) {
      return;
    }
    final order = await ref
        .read(orderRepositoryProvider)
        .getById(_controller.state.orderId!);
    setState(() => _controller.loadExisting(order));
  }

  @override
  Widget build(BuildContext context) {
    if (_loadError != null) {
      return AppScaffold(
        title: '订单',
        body: ErrorState(message: _loadError!, onRetry: _bootstrap),
      );
    }
    if (!_ready) {
      return const AppScaffold(title: '订单', body: LoadingState());
    }
    final state = _controller.state;
    final title = state.isNew ? '新订单' : (state.readOnly ? '订单' : '编辑订单');
    return PopScope(
      canPop: !state.dirty,
      onPopInvokedWithResult: (didPop, _) async {
        if (didPop) {
          return;
        }
        if (await _confirmLeave() && mounted) {
          context.pop();
        }
      },
      child: AppScaffold(
        title: title,
        body: ListView(
          children: [
            ColoredBox(
              color: AppColors.surface,
              child: ListTile(
                title: const Text('客户', style: AppTextStyles.secondary),
                subtitle: Text(
                  state.customerName ?? '点击选择客户',
                  style: state.customerName == null
                      ? AppTextStyles.tertiary
                      : AppTextStyles.bodyStrong,
                ),
                trailing: state.readOnly
                    ? null
                    : const Icon(
                        Icons.chevron_right,
                        color: AppColors.textMuted,
                      ),
                onTap: state.readOnly ? null : _pickCustomer,
              ),
            ),
            const SizedBox(height: 8),
            ColoredBox(
              color: AppColors.surface,
              child: Column(
                children: [
                  if (state.items.isEmpty)
                    const Padding(
                      padding: EdgeInsets.all(24),
                      child: Text('还没有商品', style: AppTextStyles.tertiary),
                    )
                  else
                    for (var i = 0; i < state.items.length; i++)
                      Column(
                        children: [
                          TransactionListRow(
                            title: _itemTitle(state.items[i]),
                            subtitle:
                                '${state.items[i].qty ?? '-'} ${state.items[i].uom} × ${MoneyFormat.cnyWithUom(state.items[i].rate, state.items[i].uom)}',
                            amount: state.items[i].subtotal,
                            lastDeal: state.items[i].lastDealPrice == null
                                ? null
                                : '上次 ${MoneyFormat.cnyWithUom(state.items[i].lastDealPrice, state.items[i].uom)}',
                            showChevron: !state.readOnly,
                            onTap: state.readOnly ? null : () => _editLine(i),
                          ),
                          if (state.items[i].lineError != null)
                            Padding(
                              padding: const EdgeInsets.fromLTRB(16, 0, 16, 8),
                              child: Align(
                                alignment: Alignment.centerLeft,
                                child: Text(
                                  state.items[i].lineError!,
                                  style: AppTextStyles.caption.copyWith(
                                    color: AppColors.danger,
                                  ),
                                ),
                              ),
                            ),
                          if (i != state.items.length - 1)
                            const Divider(height: 1),
                        ],
                      ),
                  if (!state.readOnly)
                    TextButton.icon(
                      onPressed: _addProduct,
                      icon: const Icon(Icons.add, size: 18),
                      label: const Text('添加商品'),
                    ),
                ],
              ),
            ),
            Padding(
              padding: const EdgeInsets.all(16),
              child: Row(
                children: [
                  const Text('合计', style: AppTextStyles.secondary),
                  const Spacer(),
                  Text(
                    MoneyFormat.cny(state.serverTotal ?? state.localTotal),
                    style: AppTextStyles.moneyLarge,
                  ),
                ],
              ),
            ),
            if (state.error != null)
              Padding(
                padding: const EdgeInsets.symmetric(horizontal: 16),
                child: Text(
                  state.error!,
                  style: AppTextStyles.caption.copyWith(
                    color: AppColors.danger,
                  ),
                ),
              ),
            if (state.traceId != null)
              Padding(
                padding: const EdgeInsets.fromLTRB(16, 4, 16, 0),
                child: Text(
                  '错误编号 ${state.traceId}',
                  style: AppTextStyles.tertiary,
                ),
              ),
            if (state.conflict)
              Padding(
                padding: const EdgeInsets.all(16),
                child: SecondaryButton(
                  label: '刷新最新订单',
                  onPressed: _refreshConflict,
                ),
              ),
            if (state.unknownOutcome)
              Padding(
                padding: const EdgeInsets.all(16),
                child: SecondaryButton(
                  label: '查看订单列表',
                  onPressed: () => context.go('/orders'),
                ),
              ),
            const SizedBox(height: 80),
          ],
        ),
        bottomAction: state.readOnly
            ? null
            : BusinessActionBar(
                children: [
                  SecondaryButton(
                    label: state.isNew ? '保存草稿' : '保存修改',
                    loading: state.busy,
                    onPressed:
                        state.busy || state.submitting || state.unknownOutcome
                        ? null
                        : _save,
                  ),
                  PrimaryButton(
                    label: '提交订单',
                    loading: state.submitting,
                    onPressed:
                        state.busy || state.submitting || state.unknownOutcome
                        ? null
                        : _submit,
                  ),
                ],
              ),
      ),
    );
  }

  String _itemTitle(LocalOrderItem item) {
    if (item.spec == null || item.spec!.isEmpty) {
      return item.productName;
    }
    return '${item.productName} · ${item.spec}';
  }
}
