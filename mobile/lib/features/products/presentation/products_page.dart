import 'package:flutter/material.dart';
import 'package:flutter_riverpod/flutter_riverpod.dart';

import '../../../app/theme/app_colors.dart';
import '../../../app/theme/app_text_styles.dart';
import '../../../core/utils/money.dart';
import '../../../core/widgets/app_scaffold.dart';
import '../../../core/widgets/feedback.dart';
import '../../../core/widgets/search_and_section.dart';
import '../../feature_providers.dart';
import '../data/product_models.dart';

class ProductsPage extends ConsumerStatefulWidget {
  const ProductsPage({super.key});

  @override
  ConsumerState<ProductsPage> createState() => _ProductsPageState();
}

class _ProductsPageState extends ConsumerState<ProductsPage> {
  final _search = TextEditingController();
  List<ProductVariant> _items = const [];
  bool _loading = true;
  String? _error;

  @override
  void initState() {
    super.initState();
    _load();
  }

  @override
  void dispose() {
    _search.dispose();
    super.dispose();
  }

  Future<void> _load() async {
    setState(() {
      _loading = true;
      _error = null;
    });
    try {
      final result = await ref.read(productRepositoryProvider).selector(q: _search.text.trim());
      if (!mounted) {
        return;
      }
      setState(() {
        _items = result.results;
        _loading = false;
      });
    } catch (_) {
      setState(() {
        _error = '商品暂时无法加载';
        _loading = false;
      });
    }
  }

  @override
  Widget build(BuildContext context) {
    return AppScaffold(
      title: '商品',
      body: Column(
        children: [
          const SizedBox(height: 10),
          AppSearchField(
            controller: _search,
            hint: '搜索商品名称或简称',
            onChanged: (_) => _load(),
          ),
          Expanded(
            child: _loading
                ? const LoadingState()
                : _error != null
                    ? ErrorState(message: _error!, onRetry: _load)
                    : _items.isEmpty
                        ? const EmptyState(message: '暂无商品')
                        : ListView.separated(
                            itemCount: _items.length,
                            separatorBuilder: (_, __) => const Divider(height: 1),
                            itemBuilder: (context, index) {
                              final item = _items[index];
                              final spec = item.spec == null || item.spec!.isEmpty ? '' : item.spec!;
                              final uoms = item.allowedUoms.map((u) {
                                if (u.referencePrice == null) {
                                  return u.uom;
                                }
                                return '${u.uom}·${MoneyFormat.cny(u.referencePrice)}';
                              }).join(' / ');
                              return ColoredBox(
                                color: AppColors.surface,
                                child: ListTile(
                                  title: Text(
                                    spec.isEmpty ? item.productName : '${item.productName}  $spec',
                                    style: AppTextStyles.rowTitle,
                                  ),
                                  subtitle: uoms.isEmpty ? null : Text(uoms, style: AppTextStyles.tertiary),
                                  trailing: item.referencePrice == null
                                      ? null
                                      : Text(
                                          MoneyFormat.cnyWithUom(item.referencePrice, item.priceUom ?? item.defaultUom),
                                          style: AppTextStyles.money,
                                        ),
                                ),
                              );
                            },
                          ),
          ),
        ],
      ),
    );
  }
}
