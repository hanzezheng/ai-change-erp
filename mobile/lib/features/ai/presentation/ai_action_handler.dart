import 'package:decimal/decimal.dart';
import 'package:flutter/material.dart';
import 'package:flutter_riverpod/flutter_riverpod.dart';
import 'package:go_router/go_router.dart';

import '../../feature_providers.dart';
import '../../orders/presentation/order_edit_controller.dart';
import '../../orders/presentation/order_edit_page.dart';
import '../data/ai_models.dart';

Future<void> handleAiActionResult(
  BuildContext context,
  WidgetRef ref,
  AiActionResponse response,
) async {
  if (response.status == 'FAILED') {
    _toast(context, response.message ?? '无法理解该指令');
    return;
  }

  if (response.status == 'NEED_USER_INPUT') {
    await _handleAmbiguity(context, ref, response);
    return;
  }

  final type = response.actionType ?? '';
  if (type == 'CREATE_ORDER') {
    await _openCreateOrder(context, response.payload);
    return;
  }
  if (type == 'UPDATE_CURRENT_ORDER') {
    await _applyUpdate(context, ref, response.payload);
    return;
  }
  _toast(context, '暂不支持该操作：${type.isEmpty ? response.status : type}');
}

Future<void> _openCreateOrder(
  BuildContext context,
  Map<String, dynamic> payload,
) async {
  final seed = OrderEditSeed.fromAiPayload(payload);
  if (seed.customerId == null || seed.customerId!.isEmpty) {
    _toast(context, '未能识别客户，请手动选择');
  }
  if (!context.mounted) {
    return;
  }
  await context.push('/orders/new', extra: seed);
}

Future<void> _applyUpdate(
  BuildContext context,
  WidgetRef ref,
  Map<String, dynamic> payload,
) async {
  final bridge = ref.read(aiDraftBridgeProvider);
  final controller = bridge.controller;
  if (controller == null || controller.state.readOnly) {
    // 无当前草稿时，把 ADD/SET 退化成新建草稿（仅当 payload 能形成商品行）
    final ops = payload['operations'];
    if (ops is List && ops.isNotEmpty) {
      final items = <LocalOrderItem>[];
      for (final raw in ops.whereType<Map>()) {
        final map = Map<String, dynamic>.from(raw);
        final op = map['operation']?.toString();
        if (op == 'ADD_ITEM' || op == 'SET_QTY') {
          final item = _itemFromOp(map);
          if (item != null) {
            items.add(item);
          }
        }
      }
      if (items.isNotEmpty) {
        await context.push(
          '/orders/new',
          extra: OrderEditSeed(items: items),
        );
        return;
      }
    }
    _toast(context, '请先打开订单编辑页，再使用改单指令');
    return;
  }

  final ops = payload['operations'];
  if (ops is! List || ops.isEmpty) {
    _toast(context, '没有可应用的改单内容');
    return;
  }

  for (final raw in ops.whereType<Map>()) {
    final map = Map<String, dynamic>.from(raw);
    final op = map['operation']?.toString();
    final itemCode = map['itemCode']?.toString() ?? '';
    if (op == 'SET_QTY') {
      final idx = controller.state.items.indexWhere((e) => e.itemCode == itemCode);
      if (idx < 0) {
        continue;
      }
      final item = controller.state.items[idx];
      item.qty = aiDecimal(map['qty']);
      final uom = map['uom']?.toString();
      if (uom != null && uom.isNotEmpty) {
        item.uom = uom;
      }
      controller.state.dirty = true;
    } else if (op == 'ADD_ITEM') {
      final item = _itemFromOp(map);
      if (item != null) {
        controller.addOrReplaceItem(item);
      }
    }
  }
  bridge.refresh();
  _toast(context, '已更新当前订单草稿');
}

LocalOrderItem? _itemFromOp(Map<String, dynamic> map) {
  final itemCode = map['itemCode']?.toString() ?? '';
  if (itemCode.isEmpty) {
    return null;
  }
  final uom = map['uom']?.toString() ?? '';
  return LocalOrderItem(
    productId: map['productId']?.toString() ?? itemCode,
    itemCode: itemCode,
    productName: map['productName']?.toString() ?? itemCode,
    spec: map['spec']?.toString(),
    qty: aiDecimal(map['qty']),
    uom: uom.isEmpty ? '箱' : uom,
    rate: aiDecimal(map['rate']) ?? Decimal.zero,
  );
}

Future<void> _handleAmbiguity(
  BuildContext context,
  WidgetRef ref,
  AiActionResponse response,
) async {
  final amb = response.ambiguities;
  if (amb.isEmpty) {
    _toast(context, response.message ?? '需要补充信息');
    return;
  }
  final first = amb.first;
  if (first.candidates.isEmpty) {
    _toast(context, '无法确认「${first.expression}」，请手动选择');
    return;
  }

  final selected = await showModalBottomSheet<Map<String, dynamic>>(
    context: context,
    isScrollControlled: true,
    useSafeArea: true,
    builder: (ctx) {
      return SafeArea(
        child: ListView(
          shrinkWrap: true,
          children: [
            Padding(
              padding: const EdgeInsets.fromLTRB(16, 16, 16, 8),
              child: Text(
                first.field == 'customer' ? '请选择客户' : '请选择商品',
                style: Theme.of(ctx).textTheme.titleMedium,
              ),
            ),
            Padding(
              padding: const EdgeInsets.fromLTRB(16, 0, 16, 8),
              child: Text('「${first.expression}」有多个候选'),
            ),
            for (final c in first.candidates)
              ListTile(
                title: Text(c['name']?.toString() ?? c['customerName']?.toString() ?? ''),
                subtitle: Text(
                  [
                    if (c['spec'] != null) c['spec'].toString(),
                    if (c['itemCode'] != null) c['itemCode'].toString(),
                    if (c['customerId'] != null) c['customerId'].toString(),
                  ].where((e) => e.isNotEmpty).join(' · '),
                ),
                onTap: () => Navigator.pop(ctx, c),
              ),
          ],
        ),
      );
    },
  );

  if (selected == null || !context.mounted) {
    return;
  }

  // 局部消歧：客户选完后带着 payload 已解析商品开单；商品选完则并入 items。
  if (first.field == 'customer') {
    final payload = Map<String, dynamic>.from(response.payload);
    payload['customer'] = {
      'customerId': selected['customerId'],
      'customerName': selected['name'] ?? selected['customerName'],
    };
    await _openCreateOrder(context, payload);
    return;
  }

  if (first.field == 'item') {
    final payload = Map<String, dynamic>.from(response.payload);
    final items = <Map<String, dynamic>>[
      ...((payload['items'] is List)
          ? (payload['items'] as List).whereType<Map>().map((e) => Map<String, dynamic>.from(e))
          : const <Map<String, dynamic>>[]),
      {
        'itemCode': selected['itemCode'],
        'productId': selected['productId'] ?? selected['itemCode'],
        'productName': selected['name'] ?? selected['productName'],
        'spec': selected['spec'],
        'qty': 1,
        'uom': '箱',
      },
    ];
    payload['items'] = items;
    final bridge = ref.read(aiDraftBridgeProvider);
    if (bridge.hasActiveDraft) {
      final item = _itemFromOp(items.last);
      if (item != null) {
        bridge.controller!.addOrReplaceItem(item);
        bridge.refresh();
        _toast(context, '已加入当前草稿');
      }
      return;
    }
    await _openCreateOrder(context, payload);
  }
}

void _toast(BuildContext context, String message) {
  ScaffoldMessenger.of(context).showSnackBar(SnackBar(content: Text(message)));
}
