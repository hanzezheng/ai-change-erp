import 'package:flutter/material.dart';
import 'package:flutter/services.dart';

import '../../../app/theme/app_colors.dart';
import '../../../app/theme/app_text_styles.dart';
import '../../../core/utils/decimal_json.dart';
import '../../../core/utils/money.dart';
import '../../../core/widgets/app_bottom_sheet.dart';
import '../../../core/widgets/buttons.dart';
import '../../orders/presentation/order_edit_controller.dart';
import '../data/product_models.dart';

class ItemEditorResult {
  const ItemEditorResult.save(this.item) : deleted = false;
  const ItemEditorResult.delete() : item = null, deleted = true;

  final LocalOrderItem? item;
  final bool deleted;
}

Future<ItemEditorResult?> showItemEditor(
  BuildContext context, {
  required LocalOrderItem item,
  required Future<void> Function(LocalOrderItem item, String uom) onUomChanged,
  bool allowDelete = false,
}) {
  return showAppBottomSheet<ItemEditorResult>(
    context: context,
    builder: (_) => ItemEditorSheet(
      item: item.copy(),
      onUomChanged: onUomChanged,
      allowDelete: allowDelete,
    ),
  );
}

Future<AllowedUom?> showUomSelector(
  BuildContext context, {
  required List<AllowedUom> allowed,
  required String current,
}) {
  return showAppBottomSheet<AllowedUom>(
    context: context,
    heightFactor: 0.45,
    builder: (_) => UomSelectorSheet(allowed: allowed, current: current),
  );
}

class ItemEditorSheet extends StatefulWidget {
  const ItemEditorSheet({
    super.key,
    required this.item,
    required this.onUomChanged,
    required this.allowDelete,
  });

  final LocalOrderItem item;
  final Future<void> Function(LocalOrderItem item, String uom) onUomChanged;
  final bool allowDelete;

  @override
  State<ItemEditorSheet> createState() => _ItemEditorSheetState();
}

class _ItemEditorSheetState extends State<ItemEditorSheet> {
  late final TextEditingController _qty;
  late final TextEditingController _rate;
  final _qtyFocus = FocusNode();

  @override
  void initState() {
    super.initState();
    _qty = TextEditingController(text: widget.item.qty?.toString() ?? '');
    _rate = TextEditingController(text: widget.item.rate?.toString() ?? '');
    WidgetsBinding.instance.addPostFrameCallback((_) => _qtyFocus.requestFocus());
  }

  @override
  void dispose() {
    _qty.dispose();
    _rate.dispose();
    _qtyFocus.dispose();
    super.dispose();
  }

  void _sync() {
    widget.item.qty = decimalFromJson(_qty.text);
    widget.item.rate = decimalFromJson(_rate.text);
    setState(() {});
  }

  @override
  Widget build(BuildContext context) {
    final item = widget.item;
    final spec = item.spec == null || item.spec!.isEmpty ? '' : ' · ${item.spec}';
    final last = item.lastDealPrice == null ? null : '上次 ${MoneyFormat.cnyWithUom(item.lastDealPrice, item.uom)}';
    final multipleUom = item.allowedUoms.length > 1;

    return Column(
      children: [
        Padding(
          padding: const EdgeInsets.fromLTRB(16, 12, 16, 8),
          child: Align(
            alignment: Alignment.centerLeft,
            child: Text('${item.productName}$spec', style: AppTextStyles.appBarTitle),
          ),
        ),
        Expanded(
          child: ListView(
            padding: const EdgeInsets.symmetric(horizontal: 16),
            children: [
              _field(
                label: '数量',
                child: TextField(
                  controller: _qty,
                  focusNode: _qtyFocus,
                  keyboardType: const TextInputType.numberWithOptions(decimal: true),
                  inputFormatters: [FilteringTextInputFormatter.allow(RegExp(r'[0-9.]'))],
                  onChanged: (_) => _sync(),
                  decoration: const InputDecoration(hintText: '请输入数量'),
                ),
              ),
              _field(
                label: '单位',
                child: multipleUom
                    ? InkWell(
                        onTap: () async {
                          final selected = await showUomSelector(
                            context,
                            allowed: item.allowedUoms,
                            current: item.uom,
                          );
                          if (selected == null) {
                            return;
                          }
                          await widget.onUomChanged(item, selected.uom);
                          if (item.rate != null) {
                            _rate.text = item.rate.toString();
                          }
                          setState(() {});
                        },
                        child: Padding(
                          padding: const EdgeInsets.symmetric(vertical: 12),
                          child: Row(
                            children: [
                              Expanded(child: Text(item.uom, style: AppTextStyles.body)),
                              const Icon(Icons.chevron_right, color: AppColors.textMuted),
                            ],
                          ),
                        ),
                      )
                    : Padding(
                        padding: const EdgeInsets.symmetric(vertical: 12),
                        child: Text(item.uom, style: AppTextStyles.body),
                      ),
              ),
              _field(
                label: '单价',
                child: TextField(
                  controller: _rate,
                  keyboardType: const TextInputType.numberWithOptions(decimal: true),
                  inputFormatters: [FilteringTextInputFormatter.allow(RegExp(r'[0-9.]'))],
                  onChanged: (_) => _sync(),
                  decoration: const InputDecoration(hintText: '请输入单价'),
                ),
              ),
              if (last != null)
                Padding(
                  padding: const EdgeInsets.only(top: 8),
                  child: Text(last, style: AppTextStyles.tertiary),
                ),
              const SizedBox(height: 12),
              Text('小计 ${MoneyFormat.cny(item.subtotal)}', style: AppTextStyles.moneyLarge),
            ],
          ),
        ),
        Padding(
          padding: const EdgeInsets.fromLTRB(16, 8, 16, 16),
          child: Row(
            children: [
              if (widget.allowDelete)
                Expanded(
                  child: SecondaryButton(
                    label: '删除',
                    onPressed: () => Navigator.pop(context, const ItemEditorResult.delete()),
                  ),
                ),
              if (widget.allowDelete) const SizedBox(width: 10),
              Expanded(
                    child: PrimaryButton(
                  label: '完成',
                  onPressed: () {
                    _sync();
                    Navigator.pop(context, ItemEditorResult.save(item));
                  },
                ),
              ),
            ],
          ),
        ),
      ],
    );
  }

  Widget _field({required String label, required Widget child}) {
    return Padding(
      padding: const EdgeInsets.only(bottom: 8),
      child: Row(
        crossAxisAlignment: CrossAxisAlignment.center,
        children: [
          SizedBox(width: 48, child: Text(label, style: AppTextStyles.secondary)),
          Expanded(child: child),
        ],
      ),
    );
  }
}

class UomSelectorSheet extends StatelessWidget {
  const UomSelectorSheet({
    super.key,
    required this.allowed,
    required this.current,
  });

  final List<AllowedUom> allowed;
  final String current;

  @override
  Widget build(BuildContext context) {
    return Column(
      children: [
        const Padding(
          padding: EdgeInsets.fromLTRB(16, 12, 16, 8),
          child: Align(
            alignment: Alignment.centerLeft,
            child: Text('选择单位', style: AppTextStyles.appBarTitle),
          ),
        ),
        Expanded(
          child: ListView.builder(
            itemCount: allowed.length,
            itemBuilder: (context, index) {
              final item = allowed[index];
              final selected = item.uom == current;
              final price = item.referencePrice == null
                  ? null
                  : MoneyFormat.cnyWithUom(item.referencePrice, item.uom);
              return ListTile(
                title: Text(item.uom, style: AppTextStyles.bodyStrong),
                subtitle: price == null ? null : Text(price, style: AppTextStyles.tertiary),
                trailing: selected ? const Icon(Icons.check, color: AppColors.primary) : null,
                onTap: () => Navigator.pop(context, item),
              );
            },
          ),
        ),
      ],
    );
  }
}
