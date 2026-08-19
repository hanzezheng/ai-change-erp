import 'dart:async';

import 'package:flutter/material.dart';
import 'package:flutter_riverpod/flutter_riverpod.dart';

import '../../../app/theme/app_colors.dart';
import '../../../app/theme/app_text_styles.dart';
import '../../../core/widgets/app_scaffold.dart';
import '../../../core/widgets/feedback.dart';
import '../../../core/widgets/search_and_section.dart';
import '../../feature_providers.dart';
import '../data/inventory_models.dart';

class InventoryPage extends ConsumerStatefulWidget {
  const InventoryPage({super.key});

  @override
  ConsumerState<InventoryPage> createState() => _InventoryPageState();
}

class _InventoryPageState extends ConsumerState<InventoryPage> {
  final _search = TextEditingController();
  Timer? _debounce;
  bool _lowStock = false;
  final _items = <InventoryItem>[];
  int _page = 1;
  bool _loading = true;
  String? _error;

  @override
  void initState() {
    super.initState();
    _load(reset: true);
  }

  @override
  void dispose() {
    _debounce?.cancel();
    _search.dispose();
    super.dispose();
  }

  Future<void> _load({required bool reset}) async {
    if (reset) {
      setState(() {
        _loading = true;
        _error = null;
        _page = 1;
      });
    }
    try {
      final result = await ref.read(inventoryRepositoryProvider).list(
            q: _search.text.trim().isEmpty ? null : _search.text.trim(),
            lowStock: _lowStock,
            page: reset ? 1 : _page,
          );
      if (!mounted) {
        return;
      }
      setState(() {
        if (reset) {
          _items
            ..clear()
            ..addAll(result.content);
        } else {
          _items.addAll(result.content);
        }
        _loading = false;
      });
    } catch (_) {
      if (!mounted) {
        return;
      }
      setState(() {
        _error = '库存暂时无法加载';
        _loading = false;
      });
    }
  }

  @override
  Widget build(BuildContext context) {
    return AppScaffold(
      title: '库存查询',
      body: Column(
        children: [
          ColoredBox(
            color: AppColors.surface,
            child: Column(
              children: [
                const SizedBox(height: 10),
                AppSearchField(
                  controller: _search,
                  hint: '搜索商品名、简称、规格',
                  onChanged: (_) {
                    _debounce?.cancel();
                    _debounce = Timer(const Duration(milliseconds: 400), () => _load(reset: true));
                  },
                ),
                Row(
                  children: [
                    _tab('全部', !_lowStock, () {
                      setState(() => _lowStock = false);
                      _load(reset: true);
                    }),
                    _tab('低库存', _lowStock, () {
                      setState(() => _lowStock = true);
                      _load(reset: true);
                    }),
                  ],
                ),
              ],
            ),
          ),
          Expanded(
            child: RefreshIndicator(
              color: AppColors.primary,
              onRefresh: () => _load(reset: true),
              child: _loading
                  ? const LoadingState()
                  : _error != null
                      ? ErrorState(message: _error!, onRetry: () => _load(reset: true))
                      : _items.isEmpty
                          ? ListView(
                              children: const [
                                SizedBox(height: 80),
                                EmptyState(message: '暂无库存数据'),
                              ],
                            )
                          : ListView.separated(
                              itemCount: _items.length,
                              separatorBuilder: (_, __) => const Divider(height: 1),
                              itemBuilder: (context, index) {
                                final item = _items[index];
                                final spec = item.spec == null || item.spec!.isEmpty ? '' : item.spec!;
                                return ColoredBox(
                                  color: AppColors.surface,
                                  child: ListTile(
                                    title: Text(
                                      spec.isEmpty ? item.productName : '${item.productName}  $spec',
                                      style: AppTextStyles.rowTitle,
                                    ),
                                    subtitle: item.lowStock == true
                                        ? Text('低库存', style: AppTextStyles.caption.copyWith(color: AppColors.warning))
                                        : (item.warehouse == null || item.warehouse!.isEmpty
                                            ? null
                                            : Text(item.warehouse!, style: AppTextStyles.tertiary)),
                                    trailing: Text(
                                      '${item.quantity} ${item.stockUom}',
                                      style: AppTextStyles.moneyLarge.copyWith(
                                        color: item.lowStock == true ? AppColors.warning : AppColors.textPrimary,
                                      ),
                                    ),
                                  ),
                                );
                              },
                            ),
            ),
          ),
        ],
      ),
    );
  }

  Widget _tab(String label, bool on, VoidCallback onTap) {
    return GestureDetector(
      onTap: onTap,
      child: Padding(
        padding: const EdgeInsets.fromLTRB(16, 7, 16, 9),
        child: Column(
          children: [
            Text(
              label,
              style: AppTextStyles.body.copyWith(
                fontSize: 14,
                color: on ? AppColors.primary : AppColors.textTertiary,
                fontWeight: on ? FontWeight.w600 : FontWeight.w400,
              ),
            ),
            const SizedBox(height: 4),
            Container(height: 2, width: 28, color: on ? AppColors.primary : Colors.transparent),
          ],
        ),
      ),
    );
  }
}
