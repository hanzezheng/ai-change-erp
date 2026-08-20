import 'package:flutter/material.dart';
import 'package:flutter_riverpod/flutter_riverpod.dart';

import '../../../app/theme/app_colors.dart';
import '../../../app/theme/app_spacing.dart';
import '../../../app/theme/app_text_styles.dart';
import '../../../core/api/api_exception.dart';
import '../../../core/widgets/app_bottom_sheet.dart';
import '../../../core/widgets/buttons.dart';
import '../../feature_providers.dart';
import '../data/ai_models.dart';
import 'ai_action_handler.dart';

Future<void> showQuickActionSheet(BuildContext context) {
  return showAppBottomSheet<void>(
    context: context,
    heightFactor: 0.55,
    builder: (context) => const QuickActionSheet(),
  );
}

class QuickActionSheet extends ConsumerStatefulWidget {
  const QuickActionSheet({super.key});

  @override
  ConsumerState<QuickActionSheet> createState() => _QuickActionSheetState();
}

class _QuickActionSheetState extends ConsumerState<QuickActionSheet> {
  final _controller = TextEditingController();
  var _busy = false;
  String? _error;

  @override
  void dispose() {
    _controller.dispose();
    super.dispose();
  }

  AiActionContext _buildContext() {
    final bridge = ref.read(aiDraftBridgeProvider);
    final draft = bridge.controller?.state;
    if (draft != null) {
      return AiActionContext(
        currentPage: 'ORDER_EDIT',
        currentOrderId: draft.orderId,
        currentCustomerId: draft.customerId,
        currentCustomerName: draft.customerName,
        currentItems: draft.items
            .map(
              (e) => AiContextItem(
                itemCode: e.itemCode,
                productId: e.productId,
                productName: e.productName,
                spec: e.spec,
                qty: e.qty,
                uom: e.uom,
                rate: e.rate,
              ),
            )
            .toList(),
      );
    }
    return const AiActionContext(currentPage: 'HOME', currentItems: []);
  }

  Future<void> _submit() async {
    final text = _controller.text.trim();
    if (text.isEmpty) {
      setState(() => _error = '请输入指令，例如：老韩80果20箱，粉蕉30件');
      return;
    }
    setState(() {
      _busy = true;
      _error = null;
    });
    try {
      final response = await ref.read(aiRepositoryProvider).createAction(
            AiActionRequest(
              inputType: 'TEXT',
              text: text,
              context: _buildContext(),
            ),
          );
      if (!mounted) {
        return;
      }
      Navigator.of(context).pop();
      await handleAiActionResult(context, ref, response);
    } on ApiException catch (e) {
      if (!mounted) {
        return;
      }
      setState(() {
        _busy = false;
        _error = e.userMessage;
      });
    } catch (_) {
      if (!mounted) {
        return;
      }
      setState(() {
        _busy = false;
        _error = '处理失败，请稍后重试';
      });
    }
  }

  @override
  Widget build(BuildContext context) {
    final draft = ref.watch(aiDraftBridgeProvider).controller?.state;
    return Padding(
      padding: const EdgeInsets.fromLTRB(AppSpacing.lg, 8, AppSpacing.lg, AppSpacing.lg),
      child: Column(
        crossAxisAlignment: CrossAxisAlignment.stretch,
        children: [
          Text('快捷操作', style: AppTextStyles.pageTitle.copyWith(fontSize: 18)),
          const SizedBox(height: 4),
          Text(
            draft == null ? '输入开单指令' : '可改当前订单，例如：苹果改30箱',
            style: AppTextStyles.tertiary,
          ),
          const SizedBox(height: 16),
          TextField(
            controller: _controller,
            enabled: !_busy,
            autofocus: true,
            minLines: 2,
            maxLines: 4,
            textInputAction: TextInputAction.done,
            onSubmitted: (_) => _submit(),
            decoration: const InputDecoration(
              hintText: '老韩80果20箱，粉蕉30件',
              border: OutlineInputBorder(),
            ),
          ),
          if (_error != null) ...[
            const SizedBox(height: 8),
            Text(_error!, style: AppTextStyles.secondary.copyWith(color: AppColors.danger)),
          ],
          const Spacer(),
          PrimaryButton(
            label: _busy ? '处理中…' : '执行',
            onPressed: _busy ? null : _submit,
          ),
          const SizedBox(height: 8),
          Text(
            '语音识别稍后接入；当前先用文字。',
            style: AppTextStyles.tertiary,
            textAlign: TextAlign.center,
          ),
        ],
      ),
    );
  }
}
