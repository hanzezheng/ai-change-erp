import 'package:flutter/material.dart';
import 'package:flutter_riverpod/flutter_riverpod.dart';
import 'package:go_router/go_router.dart';

import '../../../app/theme/app_colors.dart';
import '../../../app/theme/app_text_styles.dart';
import '../../../core/auth/auth_providers.dart';
import '../../../core/utils/datetime_fmt.dart';
import '../../../core/widgets/feedback.dart';
import '../../../core/widgets/search_and_section.dart';
import '../../../core/widgets/transaction_list_row.dart';
import '../../feature_providers.dart';
import '../../orders/data/order_models.dart';

class HomePage extends ConsumerStatefulWidget {
  const HomePage({super.key});

  @override
  ConsumerState<HomePage> createState() => _HomePageState();
}

class _HomePageState extends ConsumerState<HomePage> {
  List<OrderSummary> _orders = const [];
  bool _loading = true;
  String? _error;

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
      final page = await ref.read(orderRepositoryProvider).list(page: 1, pageSize: 10);
      if (!mounted) {
        return;
      }
      setState(() {
        _orders = page.content;
        _loading = false;
      });
    } catch (_) {
      if (!mounted) {
        return;
      }
      setState(() {
        _error = '订单暂时无法加载';
        _loading = false;
      });
    }
  }

  @override
  Widget build(BuildContext context) {
    final session = ref.watch(authControllerProvider).state.session;
    return ColoredBox(
      color: AppColors.background,
      child: Column(
        children: [
          Container(
            color: AppColors.surface,
            width: double.infinity,
            padding: EdgeInsets.fromLTRB(16, MediaQuery.paddingOf(context).top + 14, 16, 12),
            child: Column(
              crossAxisAlignment: CrossAxisAlignment.start,
              children: [
                Text(BusinessTime.formatDateLong(), style: AppTextStyles.tertiary),
                const SizedBox(height: 3),
                Text(
                  '${BusinessTime.greeting()}，${session?.displayName ?? ''}',
                  style: AppTextStyles.greeting,
                ),
                if (session?.tenantName != null && session!.tenantName.isNotEmpty)
                  Padding(
                    padding: const EdgeInsets.only(top: 4),
                    child: Text(session.tenantName, style: AppTextStyles.tertiary),
                  ),
              ],
            ),
          ),
          Container(
            width: double.infinity,
            decoration: const BoxDecoration(
              color: AppColors.surface,
              border: Border(top: BorderSide(color: AppColors.border), bottom: BorderSide(color: AppColors.border)),
            ),
            child: Row(
              children: [
                _quick('开订单', Icons.receipt_long_outlined, () => context.push('/orders/new')),
                _quick('记收款', Icons.payments_outlined, () => context.push('/orders/collect')),
                _quick('查客户', Icons.person_outline, () => context.go('/customers')),
                _quick('查库存', Icons.inventory_2_outlined, () => context.push('/inventory')),
              ],
            ),
          ),
          const SectionHeader(title: '最近订单'),
          Expanded(
            child: RefreshIndicator(
              color: AppColors.primary,
              onRefresh: _load,
              child: _loading
                  ? const LoadingState()
                  : _error != null
                      ? ErrorState(message: _error!, onRetry: _load)
                      : _orders.isEmpty
                          ? ListView(children: const [SizedBox(height: 80), EmptyState(message: '暂无订单')])
                          : ListView.separated(
                              itemCount: _orders.length,
                              separatorBuilder: (_, __) => const Divider(height: 1),
                              itemBuilder: (context, index) {
                                final order = _orders[index];
                                return ColoredBox(
                                  color: AppColors.surface,
                                  child: TransactionListRow(
                                    title: order.customerName,
                                    subtitle:
                                        '${order.itemSummary} · ${BusinessTime.formatListTime(order.transactionTime)}',
                                    amount: order.totalAmount,
                                    orderStatus: orderStatusLabelOf(order.orderStatus),
                                    paymentStatus: paymentCollectionLabel(order.paymentStatus),
                                    onTap: () => context.push('/orders/${Uri.encodeComponent(order.orderId)}'),
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

  Widget _quick(String label, IconData icon, VoidCallback onTap) {
    return Expanded(
      child: InkWell(
        onTap: onTap,
        child: Padding(
          padding: const EdgeInsets.symmetric(vertical: 14),
          child: Column(
            children: [
              Icon(icon, size: 20, color: AppColors.textSecondary),
              const SizedBox(height: 6),
              Text(label, style: AppTextStyles.caption),
            ],
          ),
        ),
      ),
    );
  }
}
