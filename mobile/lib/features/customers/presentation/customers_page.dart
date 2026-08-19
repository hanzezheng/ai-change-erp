import 'dart:async';

import 'package:flutter/material.dart';
import 'package:flutter_riverpod/flutter_riverpod.dart';
import 'package:go_router/go_router.dart';

import '../../../app/theme/app_colors.dart';
import '../../../app/theme/app_text_styles.dart';
import '../../../core/widgets/app_scaffold.dart';
import '../../../core/widgets/buttons.dart';
import '../../../core/widgets/feedback.dart';
import '../../../core/widgets/search_and_section.dart';
import '../../feature_providers.dart';
import '../data/customer_models.dart';

class CustomersPage extends ConsumerStatefulWidget {
  const CustomersPage({super.key});

  @override
  ConsumerState<CustomersPage> createState() => _CustomersPageState();
}

class _CustomersPageState extends ConsumerState<CustomersPage> {
  final _search = TextEditingController();
  Timer? _debounce;
  final _items = <CustomerSummary>[];
  int _page = 1;
  bool _hasMore = false;
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
      final result = await ref.read(customerRepositoryProvider).list(
            q: _search.text.trim().isEmpty ? null : _search.text.trim(),
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
        _hasMore = result.hasMore;
        _loading = false;
      });
    } catch (_) {
      if (!mounted) {
        return;
      }
      setState(() {
        _error = '客户暂时无法加载';
        _loading = false;
      });
    }
  }

  @override
  Widget build(BuildContext context) {
    return ColoredBox(
      color: AppColors.background,
      child: Column(
        children: [
          Container(
            color: AppColors.surface,
            padding: EdgeInsets.only(top: MediaQuery.paddingOf(context).top),
            child: Column(
              crossAxisAlignment: CrossAxisAlignment.start,
              children: [
                const Padding(
                  padding: EdgeInsets.fromLTRB(16, 14, 16, 10),
                  child: Text('客户', style: AppTextStyles.pageTitle),
                ),
                AppSearchField(
                  controller: _search,
                  hint: '搜索姓名、称呼、电话',
                  onChanged: (_) {
                    _debounce?.cancel();
                    _debounce = Timer(const Duration(milliseconds: 400), () => _load(reset: true));
                  },
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
                              children: [
                                SizedBox(
                                  height: 240,
                                  child: EmptyState(
                                    message: _search.text.isEmpty ? '暂无客户' : '没有找到相关结果',
                                  ),
                                ),
                              ],
                            )
                          : NotificationListener<ScrollNotification>(
                              onNotification: (n) {
                                if (n.metrics.pixels > n.metrics.maxScrollExtent - 80 && _hasMore && !_loading) {
                                  _page += 1;
                                  _load(reset: false);
                                }
                                return false;
                              },
                              child: ListView.separated(
                                itemCount: _items.length,
                                separatorBuilder: (_, __) => const Divider(height: 1),
                                itemBuilder: (context, index) {
                                  final customer = _items[index];
                                  final extras = [
                                    if (customer.aliases.isNotEmpty) customer.aliases.take(2).join(' · '),
                                    if (customer.phone != null && customer.phone!.isNotEmpty) customer.phone!,
                                  ].join('  ');
                                  return ColoredBox(
                                    color: AppColors.surface,
                                    child: ListTile(
                                      title: Text(customer.customerName, style: AppTextStyles.bodyStrong),
                                      subtitle: extras.isEmpty ? null : Text(extras, style: AppTextStyles.tertiary),
                                      onTap: () => context.push(
                                        '/customers/${Uri.encodeComponent(customer.customerId)}',
                                      ),
                                    ),
                                  );
                                },
                              ),
                            ),
            ),
          ),
        ],
      ),
    );
  }
}

class CustomerDetailPage extends ConsumerStatefulWidget {
  const CustomerDetailPage({super.key, required this.customerId});

  final String customerId;

  @override
  ConsumerState<CustomerDetailPage> createState() => _CustomerDetailPageState();
}

class _CustomerDetailPageState extends ConsumerState<CustomerDetailPage> {
  CustomerSummary? _customer;
  bool _loading = true;
  String? _error;

  @override
  void initState() {
    super.initState();
    _load();
  }

  Future<void> _load() async {
    try {
      final customer = await ref.read(customerRepositoryProvider).getById(widget.customerId);
      if (!mounted) {
        return;
      }
      setState(() {
        _customer = customer;
        _loading = false;
      });
    } catch (_) {
      setState(() {
        _error = '客户信息暂时无法加载';
        _loading = false;
      });
    }
  }

  @override
  Widget build(BuildContext context) {
    final customer = _customer;
    return AppScaffold(
      title: '客户详情',
      body: _loading
          ? const LoadingState()
          : _error != null || customer == null
              ? ErrorState(message: _error ?? '客户不存在', onRetry: _load)
              : ListView(
                  children: [
                    ColoredBox(
                      color: AppColors.surface,
                      child: Padding(
                        padding: const EdgeInsets.all(16),
                        child: Column(
                          crossAxisAlignment: CrossAxisAlignment.start,
                          children: [
                            Text(customer.customerName, style: AppTextStyles.greeting),
                            if (customer.aliases.isNotEmpty) ...[
                              const SizedBox(height: 8),
                              Wrap(
                                spacing: 6,
                                children: [
                                  for (final alias in customer.aliases)
                                    Chip(
                                      label: Text(alias, style: AppTextStyles.tertiary),
                                      visualDensity: VisualDensity.compact,
                                      backgroundColor: AppColors.background,
                                      side: const BorderSide(color: AppColors.border),
                                    ),
                                ],
                              ),
                            ],
                          ],
                        ),
                      ),
                    ),
                    const SizedBox(height: 8),
                    ColoredBox(
                      color: AppColors.surface,
                      child: Column(
                        children: [
                          if (customer.phone != null && customer.phone!.isNotEmpty)
                            ListTile(title: const Text('电话'), subtitle: Text(customer.phone!)),
                          if (customer.address != null && customer.address!.isNotEmpty)
                            ListTile(title: const Text('地址'), subtitle: Text(customer.address!)),
                        ],
                      ),
                    ),
                  ],
                ),
      bottomAction: customer == null
          ? null
          : BusinessActionBar(
              children: [
                PrimaryButton(
                  label: '新建订单',
                  onPressed: () => context.push(
                    '/orders/new?customerId=${Uri.encodeComponent(customer.customerId)}&customerName=${Uri.encodeComponent(customer.customerName)}',
                  ),
                ),
              ],
            ),
    );
  }
}
