import 'dart:async';

import 'package:flutter/material.dart';
import 'package:flutter_riverpod/flutter_riverpod.dart';

import '../../../app/theme/app_text_styles.dart';
import '../../../core/utils/money.dart';
import '../../../core/widgets/app_bottom_sheet.dart';
import '../../../core/widgets/feedback.dart';
import '../../../core/widgets/search_and_section.dart';
import '../../feature_providers.dart';
import '../data/product_models.dart';

Future<ProductVariant?> showProductSelector(
  BuildContext context, {
  String? customerId,
}) {
  return showAppBottomSheet<ProductVariant>(
    context: context,
    builder: (_) => ProductSelectorSheet(customerId: customerId),
  );
}

class ProductSelectorSheet extends ConsumerStatefulWidget {
  const ProductSelectorSheet({super.key, this.customerId});

  final String? customerId;

  @override
  ConsumerState<ProductSelectorSheet> createState() => _ProductSelectorSheetState();
}

class _ProductSelectorSheetState extends ConsumerState<ProductSelectorSheet> {
  final _controller = TextEditingController();
  Timer? _debounce;
  ProductSelectorResult? _data;
  String? _error;
  bool _loading = true;

  @override
  void initState() {
    super.initState();
    _load();
  }

  @override
  void dispose() {
    _debounce?.cancel();
    _controller.dispose();
    super.dispose();
  }

  Future<void> _load() async {
    setState(() {
      _loading = true;
      _error = null;
    });
    try {
      final result = await ref.read(productRepositoryProvider).selector(
            q: _controller.text.trim(),
            customerId: widget.customerId,
          );
      if (!mounted) {
        return;
      }
      setState(() {
        _data = result;
        _loading = false;
      });
    } catch (_) {
      if (!mounted) {
        return;
      }
      setState(() {
        _error = '商品列表暂时无法加载';
        _loading = false;
      });
    }
  }

  @override
  Widget build(BuildContext context) {
    final frequent = _data?.frequentItems ?? const <ProductVariant>[];
    final results = _data?.results ?? const <ProductVariant>[];
    final seen = frequent.map((item) => item.itemCode).toSet();
    final rest = results.where((item) => !seen.contains(item.itemCode)).toList();

    return Column(
      children: [
        const Padding(
          padding: EdgeInsets.fromLTRB(16, 12, 16, 8),
          child: Align(
            alignment: Alignment.centerLeft,
            child: Text('选择商品', style: AppTextStyles.appBarTitle),
          ),
        ),
        AppSearchField(
          controller: _controller,
          hint: '搜索商品名、简称、规格',
          onChanged: (_) {
            _debounce?.cancel();
            _debounce = Timer(const Duration(milliseconds: 400), _load);
          },
        ),
        Expanded(
          child: _loading
              ? const LoadingState()
              : _error != null
                  ? ErrorState(message: _error!, onRetry: _load)
                  : ListView(
                      children: [
                        if (widget.customerId != null && frequent.isNotEmpty) ...[
                          const SectionHeader(title: '当前客户常买'),
                          ...frequent.map(_row),
                        ],
                        const SectionHeader(title: '全部商品'),
                        if (rest.isEmpty && frequent.isEmpty)
                          const EmptyState(message: '没有找到相关结果')
                        else
                          ...rest.map(_row),
                      ],
                    ),
        ),
      ],
    );
  }

  Widget _row(ProductVariant item) {
    final spec = (item.spec == null || item.spec!.isEmpty) ? '' : item.spec!;
    final title = spec.isEmpty ? item.productName : '${item.productName} · $spec';
    final uoms = item.allowedUoms.map((u) => u.uom).join('/');
    final ref = item.referencePrice == null
        ? null
        : MoneyFormat.cnyWithUom(item.referencePrice, item.priceUom ?? item.defaultUom);
    final last = item.lastDealPrice == null
        ? null
        : '上次 ${MoneyFormat.cnyWithUom(item.lastDealPrice, item.defaultUom)}';
    return InkWell(
      onTap: () => Navigator.pop(context, item),
      child: Padding(
        padding: const EdgeInsets.symmetric(horizontal: 16, vertical: 12),
        child: Row(
          children: [
            Expanded(
              child: Column(
                crossAxisAlignment: CrossAxisAlignment.start,
                children: [
                  Text(title, style: AppTextStyles.rowTitle),
                  if (uoms.isNotEmpty) ...[
                    const SizedBox(height: 2),
                    Text(uoms, style: AppTextStyles.tertiary),
                  ],
                ],
              ),
            ),
            Column(
              crossAxisAlignment: CrossAxisAlignment.end,
              children: [
                if (ref != null) Text(ref, style: AppTextStyles.money),
                if (last != null) Text(last, style: AppTextStyles.tertiary),
              ],
            ),
          ],
        ),
      ),
    );
  }
}
