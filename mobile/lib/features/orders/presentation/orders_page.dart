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
  String? _loadMoreError;
  String? _error;
  int _requestGeneration = 0;

  // A malformed backend must not make a collect-mode reset issue requests
  // forever.  Normal ERP lists are far below this bound.
  static const _maxCollectPageHops = 100;

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
    _requestGeneration++;
    _token?.cancel();
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
    final firstPage = reset ? 1 : _page + 1;
    final query = _search.text.trim();
    _token?.cancel();
    final token = CancelToken();
    _token = token;
    if (reset) {
      setState(() {
        _loading = true;
        _loadingMore = false;
        _error = null;
        _loadMoreError = null;
        _items.clear();
        _page = 1;
        _hasMore = false;
      });
    } else {
      setState(() {
        _loadingMore = true;
        _loadMoreError = null;
      });
    }

    var page = firstPage;
    var lastSuccessfulPage = reset ? 0 : _page;
    var lastHasMore = reset ? false : _hasMore;
    final loaded = <OrderSummary>[];
    try {
      var hops = 0;
      while (hops < _maxCollectPageHops) {
        hops++;
        final result = await ref
            .read(orderRepositoryProvider)
            .list(
              q: query.isEmpty ? null : query,
              status: widget.collectMode ? OrderStatus.submitted : _status,
              page: page,
              cancelToken: token,
            );
        if (!mounted || requestGeneration != _requestGeneration) {
          return;
        }
        // This page is now committed locally.  If a later page fails (for
        // example while skipping paid orders), a retry starts from this page
        // rather than silently skipping it.
        lastSuccessfulPage = page;
        lastHasMore = result.hasMore;
        var content = result.content;
        if (widget.collectMode) {
          content = content
              .where(
                (item) => item.paymentStatus != PaymentCollectionStatus.paid,
              )
              .toList();
        }
        loaded.addAll(content);

        // In collect mode, an all-paid backend page is not an empty result
        // yet: continue until an eligible order is found or the backend is
        // exhausted.
        if (!widget.collectMode || content.isNotEmpty || !result.hasMore) {
          break;
        }
        page++;
      }

      // Protect the UI from a broken backend that reports hasMore forever.
      if (hops >= _maxCollectPageHops && lastHasMore) {
        lastHasMore = false;
      }
      if (!mounted || requestGeneration != _requestGeneration) {
        return;
      }
      setState(() {
        if (reset) {
          _items
            ..clear()
            ..addAll(loaded);
        } else {
          _items.addAll(loaded);
        }
        _page = lastSuccessfulPage == 0 ? firstPage : lastSuccessfulPage;
        _hasMore = lastHasMore;
        _loading = false;
        _loadingMore = false;
        _loadMoreError = null;
        _error = null;
      });
    } on ApiException catch (error) {
      if (error.code == 'CANCELLED' ||
          !mounted ||
          requestGeneration != _requestGeneration) {
        return;
      }
      setState(() {
        final hasPartialPage = lastSuccessfulPage > (reset ? 0 : _page);
        if (reset) {
          // A collect-mode reset may fetch one or more backend pages before a
          // later page fails. Keep successful results and the cursor so retry
          // starts at the failed page instead of re-reading page one.
          _items
            ..clear()
            ..addAll(loaded);
          _page = lastSuccessfulPage;
          _hasMore = lastHasMore;
        } else if (loaded.isNotEmpty) {
          _items.addAll(loaded);
          _page = lastSuccessfulPage;
          _hasMore = lastHasMore;
        }
        if (reset && !hasPartialPage) {
          _error = error.userMessage;
          _loading = false;
        } else {
          // Preserve the already rendered orders and expose a bottom retry.
          _error = null;
          _loadMoreError = '更多订单加载失败';
          _loading = false;
        }
        _loadingMore = false;
      });
    } catch (_) {
      if (!mounted || requestGeneration != _requestGeneration) {
        return;
      }
      setState(() {
        final hasPartialPage = lastSuccessfulPage > (reset ? 0 : _page);
        if (reset) {
          _items
            ..clear()
            ..addAll(loaded);
          _page = lastSuccessfulPage;
          _hasMore = lastHasMore;
        } else if (loaded.isNotEmpty) {
          _items.addAll(loaded);
          _page = lastSuccessfulPage;
          _hasMore = lastHasMore;
        }
        if (reset && !hasPartialPage) {
          _error = '订单暂时无法加载';
          _loading = false;
        } else {
          _error = null;
          _loadMoreError = '更多订单加载失败';
          _loading = false;
        }
        _loadingMore = false;
      });
    }
  }

  void _onSearchChanged(String value) {
    // Invalidate old responses immediately; debounce only controls when the
    // new request is sent.
    final generation = ++_requestGeneration;
    _token?.cancel();
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
            padding: EdgeInsets.only(
              top: widget.collectMode ? 0 : MediaQuery.paddingOf(context).top,
            ),
            child: Column(
              children: [
                if (!widget.collectMode)
                  Padding(
                    padding: const EdgeInsets.fromLTRB(16, 14, 16, 10),
                    child: Row(
                      children: [
                        const Expanded(
                          child: Text('订单', style: AppTextStyles.pageTitle),
                        ),
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
                  onChanged: _onSearchChanged,
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
                                _debounce?.cancel();
                                setState(() => _status = filter.$2);
                                _load(reset: true);
                              },
                              child: Column(
                                children: [
                                  Text(
                                    filter.$1,
                                    style: AppTextStyles.body.copyWith(
                                      fontSize: 14,
                                      color: _status == filter.$2
                                          ? AppColors.primary
                                          : AppColors.textTertiary,
                                      fontWeight: _status == filter.$2
                                          ? FontWeight.w600
                                          : FontWeight.w400,
                                    ),
                                  ),
                                  const SizedBox(height: 7),
                                  Container(
                                    height: 2,
                                    width: 28,
                                    color: _status == filter.$2
                                        ? AppColors.primary
                                        : Colors.transparent,
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
                  ? ErrorState(
                      message: _error!,
                      onRetry: () => _load(reset: true),
                    )
                  : _items.isEmpty
                  ? ListView(
                      children: [
                        if (_loadMoreError != null)
                          Padding(
                            padding: const EdgeInsets.all(24),
                            child: Column(
                              children: [
                                Text(
                                  _loadMoreError!,
                                  textAlign: TextAlign.center,
                                  style: AppTextStyles.secondary,
                                ),
                                TextButton(
                                  onPressed: () => _load(reset: false),
                                  child: const Text('重试'),
                                ),
                              ],
                            ),
                          )
                        else
                          SizedBox(
                            height: 240,
                            child: EmptyState(
                              message: widget.collectMode
                                  ? '暂无待收款订单'
                                  : (_search.text.isEmpty
                                        ? '暂无订单'
                                        : '没有找到相关结果'),
                              actionLabel: widget.collectMode ? null : '新建订单',
                              onAction: widget.collectMode
                                  ? null
                                  : () => context.push('/orders/new'),
                            ),
                          ),
                      ],
                    )
                  : NotificationListener<ScrollNotification>(
                      onNotification: (notification) {
                        if (notification.metrics.pixels >
                                notification.metrics.maxScrollExtent - 80 &&
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
                          final order = _items[index];
                          return ColoredBox(
                            color: AppColors.surface,
                            child: TransactionListRow(
                              title: order.customerName,
                              subtitle:
                                  '${order.itemSummary} · ${BusinessTime.formatListTime(order.transactionTime)}',
                              amount: order.totalAmount,
                              orderStatus: orderStatusLabelOf(
                                order.orderStatus,
                              ),
                              paymentStatus: paymentCollectionLabel(
                                order.paymentStatus,
                              ),
                              onTap: () {
                                final id = Uri.encodeComponent(order.orderId);
                                if (widget.collectMode) {
                                  context.push('/orders/$id/payment');
                                } else if (order.orderStatus ==
                                    OrderStatus.draft) {
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
