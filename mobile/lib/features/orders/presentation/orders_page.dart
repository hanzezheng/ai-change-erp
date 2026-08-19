import 'dart:async';

import 'package:dio/dio.dart';
import 'package:flutter/material.dart';
import 'package:flutter_riverpod/flutter_riverpod.dart';
import 'package:go_router/go_router.dart';

import '../../../app/theme/app_colors.dart';
import '../../../app/theme/app_text_styles.dart';
import '../../../core/api/api_exception.dart';
import '../../../core/utils/datetime_fmt.dart';
import '../../../core/widgets/buttons.dart';
import '../../../core/widgets/feedback.dart';
import '../../../core/widgets/search_and_section.dart';
import '../../../core/widgets/transaction_list_row.dart';
import '../../feature_providers.dart';
import '../data/order_models.dart';

class OrdersPage extends ConsumerStatefulWidget {
  const OrdersPage({super.key, this.collectMode = false});

  final bool collectMode;

  @override
  ConsumerState<OrdersPage> createState() => _OrdersPageState();
}

class _OrdersPageState extends ConsumerState<OrdersPage> {
  final _search = TextEditingController();
  Timer? _debounce;
  CancelToken? _token;
  OrderStatus? _status;
  final _items = <OrderSummary>[];
  int _page = 1;
  bool _hasMore = false;
  bool _loading = true;
  bool _loadingMore = false;
  String? _error;

  static const _filters = <(String, OrderStatus?)>[
    ('全部', null),
    ('草稿', OrderStatus.draft),
    ('已提交', OrderStatus.submitted),
    ('已完成', OrderStatus.completed),
    ('已取消', OrderStatus.cancelled),
  ];

  @override
  void initState() {
    super.initState();
    if (widget.collectMode) {
      _status = OrderStatus.submitted;
    }
    _load(reset: true);
  }

  @override
  void dispose() {
    _debounce?.cancel();
    _token?.cancel();
    _search.dispose();
    super.dispose();
  }

  Future<void> _load({required bool reset}) async {
    _token?.cancel();
    final token = CancelToken();
    _token = token;
    if (reset) {
      setState(() {
        _loading = true;
        _error = null;
        _page = 1;
      });
    } else {
      setState(() => _loadingMore = true);
    }
    try {
      final result = await ref.read(orderRepositoryProvider).list(
            q: _search.text.trim().isEmpty ? null : _search.text.trim(),
            status: widget.collectMode ? OrderStatus.submitted : _status,
            page: reset ? 1 : _page,
            cancelToken: token,
          );
      if (!mounted) {
        return;
      }
      var content = result.content;
      if (widget.collectMode) {
        content = content.where((item) => item.paymentStatus != PaymentCollectionStatus.paid).toList();
      }
      setState(() {
        if (reset) {
          _items
            ..clear()
            ..addAll(content);
          _page = 1;
        } else {
          _items.addAll(content);
        }
        _hasMore = result.hasMore;
        _loading = false;
        _loadingMore = false;
      });
    } on ApiException catch (error) {
      if (error.code == 'CANCELLED') {
        return;
      }
      if (!mounted) {
        return;
      }
      setState(() {
        _error = error.userMessage;
        _loading = false;
        _loadingMore = false;
      });
    } catch (_) {
      if (!mounted) {
        return;
      }
      setState(() {
        _error = '订单暂时无法加载';
        _loading = false;
        _loadingMore = false;
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
            padding: EdgeInsets.only(top: widget.collectMode ? 0 : MediaQuery.paddingOf(context).top),
            child: Column(
              children: [
                if (!widget.collectMode)
                  Padding(
                    padding: const EdgeInsets.fromLTRB(16, 14, 16, 10),
                    child: Row(
                      children: [
                        const Expanded(child: Text('订单', style: AppTextStyles.pageTitle)),
                        PrimaryButton(
                          label: '新增',
                          expanded: false,
                          onPressed: () => context.push('/orders/new'),
                        ),
                      ],
                    ),
                  )
                else
                  AppBar(
                    title: const Text('选择待收款订单'),
                    leading: IconButton(
                      icon: const Icon(Icons.arrow_back),
                      onPressed: () => context.pop(),
                    ),
                  ),
                AppSearchField(
                  controller: _search,
                  hint: '搜索客户、商品、订单号',
                  onChanged: (_) {
                    _debounce?.cancel();
                    _debounce = Timer(const Duration(milliseconds: 400), () => _load(reset: true));
                  },
                ),
                if (!widget.collectMode)
                  SizedBox(
                    height: 42,
                    child: ListView(
                      scrollDirection: Axis.horizontal,
                      padding: const EdgeInsets.symmetric(horizontal: 8),
                      children: [
                        for (final filter in _filters)
                          Padding(
                            padding: const EdgeInsets.symmetric(horizontal: 8),
                            child: GestureDetector(
                              onTap: () {
                                setState(() => _status = filter.$2);
                                _load(reset: true);
                              },
                              child: Column(
                                children: [
                                  Text(
                                    filter.$1,
                                    style: AppTextStyles.body.copyWith(
                                      fontSize: 14,
                                      color: _status == filter.$2 ? AppColors.primary : AppColors.textTertiary,
                                      fontWeight: _status == filter.$2 ? FontWeight.w600 : FontWeight.w400,
                                    ),
                                  ),
                                  const SizedBox(height: 7),
                                  Container(
                                    height: 2,
                                    width: 28,
                                    color: _status == filter.$2 ? AppColors.primary : Colors.transparent,
                                  ),
                                ],
                              ),
                            ),
                          ),
                      ],
                    ),
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
                                    message: widget.collectMode
                                        ? '暂无待收款订单'
                                        : (_search.text.isEmpty ? '暂无订单' : '没有找到相关结果'),
                                    actionLabel: widget.collectMode ? null : '新建订单',
                                    onAction: widget.collectMode ? null : () => context.push('/orders/new'),
                                  ),
                                ),
                              ],
                            )
                          : NotificationListener<ScrollNotification>(
                              onNotification: (notification) {
                                if (notification.metrics.pixels > notification.metrics.maxScrollExtent - 80 &&
                                    _hasMore &&
                                    !_loadingMore) {
                                  _page += 1;
                                  _load(reset: false);
                                }
                                return false;
                              },
                              child: ListView.separated(
                                itemCount: _items.length + (_loadingMore ? 1 : 0),
                                separatorBuilder: (_, __) => const Divider(height: 1),
                                itemBuilder: (context, index) {
                                  if (index >= _items.length) {
                                    return const Padding(
                                      padding: EdgeInsets.all(16),
                                      child: Center(child: CircularProgressIndicator(strokeWidth: 2)),
                                    );
                                  }
                                  final order = _items[index];
                                  return ColoredBox(
                                    color: AppColors.surface,
                                    child: TransactionListRow(
                                      title: order.customerName,
                                      subtitle:
                                          '${order.itemSummary} · ${BusinessTime.formatListTime(order.transactionTime)}',
                                      amount: order.totalAmount,
                                      orderStatus: orderStatusLabelOf(order.orderStatus),
                                      paymentStatus: paymentCollectionLabel(order.paymentStatus),
                                      onTap: () {
                                        final id = Uri.encodeComponent(order.orderId);
                                        if (widget.collectMode) {
                                          context.push('/orders/$id/payment');
                                        } else if (order.orderStatus == OrderStatus.draft) {
                                          context.push('/orders/$id/edit');
                                        } else {
                                          context.push('/orders/$id');
                                        }
                                      },
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

