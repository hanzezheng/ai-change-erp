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
  bool _loadingMore = false;
  String? _loadMoreError;
  String? _error;
  int _requestGeneration = 0;

  @override
  void initState() {
    super.initState();
    _load(reset: true);
  }

  @override
  void dispose() {
    _debounce?.cancel();
    _requestGeneration++;
    _search.dispose();
    super.dispose();
  }

  Future<void> _load({required bool reset, int? generation}) async {
    if (!mounted) {
      return;
    }
    if (generation != null && generation != _requestGeneration) {
      return;
    }
    if (!reset && (_loadingMore || !_hasMore)) {
      return;
    }
    final requestGeneration = generation ?? ++_requestGeneration;
    final requestedPage = reset ? 1 : _page + 1;
    final query = _search.text.trim();
    if (reset) {
      setState(() {
        _loading = true;
        _loadingMore = false;
        _error = null;
        _loadMoreError = null;
        _page = 1;
        _hasMore = false;
        _items.clear();
      });
    } else {
      setState(() {
        _loadingMore = true;
        _loadMoreError = null;
      });
    }
    try {
      final result = await ref
          .read(customerRepositoryProvider)
          .list(q: query.isEmpty ? null : query, page: requestedPage);
      if (!mounted || requestGeneration != _requestGeneration) {
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
        // Commit the cursor only after the requested page was received.
        _page = requestedPage;
        _hasMore = result.hasMore;
        _loading = false;
        _loadingMore = false;
        _loadMoreError = null;
        _error = null;
      });
    } catch (_) {
      if (!mounted || requestGeneration != _requestGeneration) {
        return;
      }
      setState(() {
        if (reset) {
          _error = '客户暂时无法加载';
          _loading = false;
        } else {
          // Keep already loaded customers visible and make the failed page
          // retryable without advancing _page.
          _loadMoreError = '更多客户加载失败';
        }
        _loadingMore = false;
      });
    }
  }

  void _onSearchChanged(String value) {
    // Invalidate an in-flight response immediately, before the debounce
    // elapses.  Otherwise a slow old query can repaint the new query's list.
    final generation = ++_requestGeneration;
    _debounce?.cancel();
    _debounce = Timer(
      const Duration(milliseconds: 400),
      () => _load(reset: true, generation: generation),
    );
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
                  onChanged: _onSearchChanged,
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
                  ? ErrorState(
                      message: _error!,
                      onRetry: () => _load(reset: true),
                    )
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
                        if (n.metrics.pixels > n.metrics.maxScrollExtent - 80 &&
                            _hasMore &&
                            !_loadingMore &&
                            _loadMoreError == null) {
                          _load(reset: false);
                        }
                        return false;
                      },
                      child: ListView.separated(
                        itemCount:
                            _items.length +
                            (_loadingMore || _loadMoreError != null ? 1 : 0),
                        separatorBuilder: (_, __) => const Divider(height: 1),
                        itemBuilder: (context, index) {
                          if (index >= _items.length) {
                            if (_loadMoreError != null) {
                              return TextButton(
                                onPressed: () => _load(reset: false),
                                child: Text(_loadMoreError!),
                              );
                            }
                            return const Padding(
                              padding: EdgeInsets.all(16),
                              child: Center(
                                child: CircularProgressIndicator(
                                  strokeWidth: 2,
                                ),
                              ),
                            );
                          }
                          final customer = _items[index];
                          final extras = [
                            if (customer.aliases.isNotEmpty)
                              customer.aliases.take(2).join(' · '),
                            if (customer.phone != null &&
                                customer.phone!.isNotEmpty)
                              customer.phone!,
                          ].join('  ');
                          return ColoredBox(
                            color: AppColors.surface,
                            child: Material(
                              color: AppColors.surface,
                              child: ListTile(
                                title: Text(
                                  customer.customerName,
                                  style: AppTextStyles.bodyStrong,
                                ),
                                subtitle: extras.isEmpty
                                    ? null
                                    : Text(
                                        extras,
                                        style: AppTextStyles.tertiary,
                                      ),
                                onTap: () => context.push(
                                  '/customers/${Uri.encodeComponent(customer.customerId)}',
                                ),
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
      final customer = await ref
          .read(customerRepositoryProvider)
          .getById(widget.customerId);
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
                        Text(
                          customer.customerName,
                          style: AppTextStyles.greeting,
                        ),
                        if (customer.aliases.isNotEmpty) ...[
                          const SizedBox(height: 8),
                          Wrap(
                            spacing: 6,
                            children: [
                              for (final alias in customer.aliases)
                                Chip(
                                  label: Text(
                                    alias,
                                    style: AppTextStyles.tertiary,
                                  ),
                                  visualDensity: VisualDensity.compact,
                                  backgroundColor: AppColors.background,
                                  side: const BorderSide(
                                    color: AppColors.border,
                                  ),
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
                        ListTile(
                          title: const Text('电话'),
                          subtitle: Text(customer.phone!),
                        ),
                      if (customer.address != null &&
                          customer.address!.isNotEmpty)
                        ListTile(
                          title: const Text('地址'),
                          subtitle: Text(customer.address!),
                        ),
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
