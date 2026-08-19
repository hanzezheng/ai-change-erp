import 'dart:async';

import 'package:flutter/material.dart';
import 'package:flutter_riverpod/flutter_riverpod.dart';

import '../../../app/theme/app_text_styles.dart';
import '../../../core/widgets/app_bottom_sheet.dart';
import '../../../core/widgets/feedback.dart';
import '../../../core/widgets/search_and_section.dart';
import '../../feature_providers.dart';
import '../data/customer_models.dart';

Future<CustomerSummary?> showCustomerSelector(BuildContext context) {
  return showAppBottomSheet<CustomerSummary>(
    context: context,
    builder: (_) => const CustomerSelectorSheet(),
  );
}

class CustomerSelectorSheet extends ConsumerStatefulWidget {
  const CustomerSelectorSheet({super.key});

  @override
  ConsumerState<CustomerSelectorSheet> createState() =>
      _CustomerSelectorSheetState();
}

class _CustomerSelectorSheetState extends ConsumerState<CustomerSelectorSheet> {
  final _controller = TextEditingController();
  Timer? _debounce;
  CustomerSelectorResult? _data;
  String? _error;
  bool _loading = true;
  int _requestGeneration = 0;

  @override
  void initState() {
    super.initState();
    _load();
  }

  @override
  void dispose() {
    _debounce?.cancel();
    _requestGeneration++;
    _controller.dispose();
    super.dispose();
  }

  Future<void> _load({int? generation}) async {
    if (!mounted) {
      return;
    }
    if (generation != null && generation != _requestGeneration) {
      return;
    }
    final requestGeneration = generation ?? ++_requestGeneration;
    final query = _controller.text.trim();
    setState(() {
      _loading = true;
      _error = null;
    });
    try {
      final result = await ref
          .read(customerRepositoryProvider)
          .selector(q: query);
      if (!mounted || requestGeneration != _requestGeneration) {
        return;
      }
      setState(() {
        _data = result;
        _loading = false;
      });
    } catch (error) {
      if (!mounted || requestGeneration != _requestGeneration) {
        return;
      }
      setState(() {
        _error = '客户列表暂时无法加载';
        _loading = false;
      });
    }
  }

  void _onQuery(String value) {
    // Invalidate an older in-flight query immediately; waiting for debounce
    // would still allow a late response to replace the current results.
    final generation = ++_requestGeneration;
    _debounce?.cancel();
    _debounce = Timer(
      const Duration(milliseconds: 400),
      () => _load(generation: generation),
    );
  }

  @override
  Widget build(BuildContext context) {
    final recent = _data?.recent ?? const <CustomerSummary>[];
    final results = _data?.results ?? const <CustomerSummary>[];
    final recentIds = recent.map((item) => item.customerId).toSet();
    final rest = results
        .where((item) => !recentIds.contains(item.customerId))
        .toList();

    return Column(
      children: [
        const Padding(
          padding: EdgeInsets.fromLTRB(16, 12, 16, 8),
          child: Align(
            alignment: Alignment.centerLeft,
            child: Text('选择客户', style: AppTextStyles.appBarTitle),
          ),
        ),
        AppSearchField(
          controller: _controller,
          hint: '搜索姓名、称呼、电话',
          onChanged: _onQuery,
        ),
        Expanded(
          child: _loading
              ? const LoadingState()
              : _error != null
              ? ErrorState(message: _error!, onRetry: _load)
              : ListView(
                  children: [
                    if (recent.isNotEmpty) ...[
                      const SectionHeader(title: '最近交易'),
                      ...recent.map(_row),
                    ],
                    const SectionHeader(title: '全部客户'),
                    if (rest.isEmpty && recent.isEmpty)
                      const EmptyState(message: '暂无客户')
                    else
                      ...rest.map(_row),
                  ],
                ),
        ),
      ],
    );
  }

  Widget _row(CustomerSummary customer) {
    final extras = [
      if (customer.aliases.isNotEmpty) customer.aliases.take(2).join(' · '),
      if (customer.phone != null && customer.phone!.isNotEmpty) customer.phone!,
    ].where((item) => item.isNotEmpty).join('  ');
    return InkWell(
      onTap: () => Navigator.pop(context, customer),
      child: Padding(
        padding: const EdgeInsets.symmetric(horizontal: 16, vertical: 12),
        child: Column(
          crossAxisAlignment: CrossAxisAlignment.start,
          children: [
            Text(customer.customerName, style: AppTextStyles.bodyStrong),
            if (extras.isNotEmpty) ...[
              const SizedBox(height: 2),
              Text(extras, style: AppTextStyles.tertiary),
            ],
          ],
        ),
      ),
    );
  }
}
