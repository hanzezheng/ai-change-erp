import 'package:decimal/decimal.dart';
import 'package:uuid/uuid.dart';

import '../../../core/api/api_exception.dart';
import '../../../core/utils/datetime_fmt.dart';
import '../../customers/data/customer_models.dart';
import '../../products/data/product_models.dart';
import '../data/order_models.dart';
import '../data/order_repository.dart';

class LocalOrderItem {
  LocalOrderItem({
    this.orderItemId,
    required this.productId,
    required this.itemCode,
    required this.productName,
    this.spec,
    this.qty,
    required this.uom,
    this.rate,
    this.referencePrice,
    this.lastDealPrice,
    this.allowedUoms = const [],
    this.lineError,
  });

  String? orderItemId;
  String productId;
  String itemCode;
  String productName;
  String? spec;
  Decimal? qty;
  String uom;
  Decimal? rate;
  Decimal? referencePrice;
  Decimal? lastDealPrice;
  List<AllowedUom> allowedUoms;
  String? lineError;

  Decimal get subtotal {
    if (qty == null || rate == null) {
      return Decimal.zero;
    }
    return qty! * rate!;
  }

  LocalOrderItem copy() {
    return LocalOrderItem(
      orderItemId: orderItemId,
      productId: productId,
      itemCode: itemCode,
      productName: productName,
      spec: spec,
      qty: qty,
      uom: uom,
      rate: rate,
      referencePrice: referencePrice,
      lastDealPrice: lastDealPrice,
      allowedUoms: allowedUoms,
      lineError: lineError,
    );
  }

  Map<String, dynamic> toPayload() {
    return orderItemPayload(
      orderItemId: orderItemId,
      itemCode: itemCode,
      qty: qty ?? Decimal.zero,
      uom: uom,
      rate: rate ?? Decimal.zero,
    );
  }
}

class OrderEditState {
  OrderEditState({
    this.orderId,
    this.customerId,
    this.customerName,
    this.transactionDate,
    List<LocalOrderItem>? items,
    this.updatedAt,
    this.orderStatus = OrderStatus.draft,
    this.serverTotal,
    this.dirty = false,
    this.busy = false,
    this.submitting = false,
    this.error,
    this.traceId,
    this.conflict = false,
    this.unknownOutcome = false,
    this.submitFailedAfterCreate = false,
    required this.createIdempotencyKey,
  }) : items = items ?? [];

  String? orderId;
  String? customerId;
  String? customerName;
  DateTime? transactionDate;
  List<LocalOrderItem> items;
  DateTime? updatedAt;
  OrderStatus orderStatus;
  Decimal? serverTotal;
  bool dirty;
  bool busy;
  bool submitting;
  String? error;
  String? traceId;
  bool conflict;
  bool unknownOutcome;
  bool submitFailedAfterCreate;
  String createIdempotencyKey;

  bool get isNew => orderId == null;
  bool get readOnly => orderStatus != OrderStatus.draft;

  Decimal get localTotal =>
      items.fold(Decimal.zero, (sum, item) => sum + item.subtotal);

  OrderEditState copy() {
    return OrderEditState(
      orderId: orderId,
      customerId: customerId,
      customerName: customerName,
      transactionDate: transactionDate,
      items: items.map((item) => item.copy()).toList(),
      updatedAt: updatedAt,
      orderStatus: orderStatus,
      serverTotal: serverTotal,
      dirty: dirty,
      busy: busy,
      submitting: submitting,
      error: error,
      traceId: traceId,
      conflict: conflict,
      unknownOutcome: unknownOutcome,
      submitFailedAfterCreate: submitFailedAfterCreate,
      createIdempotencyKey: createIdempotencyKey,
    );
  }
}

class OrderEditController {
  OrderEditController({
    required OrderRepository orders,
    required Future<LastDealPrice?> Function({
      required String customerId,
      required String itemCode,
      required String uom,
    })
    loadLastDeal,
    String Function()? keyFactory,
  }) : _orders = orders,
       _loadLastDeal = loadLastDeal,
       _keyFactory = keyFactory ?? const Uuid().v4;

  final OrderRepository _orders;
  final Future<LastDealPrice?> Function({
    required String customerId,
    required String itemCode,
    required String uom,
  })
  _loadLastDeal;
  final String Function() _keyFactory;

  late OrderEditState state = OrderEditState(
    createIdempotencyKey: _keyFactory(),
  );

  void startNew({
    String? customerId,
    String? customerName,
    List<LocalOrderItem>? items,
  }) {
    state = OrderEditState(
      customerId: customerId,
      customerName: customerName,
      items: items?.map((e) => e.copy()).toList(),
      dirty: items != null && items.isNotEmpty,
      createIdempotencyKey: _keyFactory(),
    );
  }

  void loadExisting(Order order) {
    final key = state.createIdempotencyKey;
    state = OrderEditState(
      orderId: order.orderId,
      customerId: order.customerId,
      customerName: order.customerName,
      transactionDate: order.transactionDate,
      items: order.items
          .map(
            (item) => LocalOrderItem(
              orderItemId: item.orderItemId,
              productId: item.productId,
              itemCode: item.itemCode,
              productName: item.productName,
              spec: item.spec,
              qty: item.qty,
              uom: item.uom,
              rate: item.rate,
            ),
          )
          .toList(),
      updatedAt: order.updatedAt,
      orderStatus: order.orderStatus,
      serverTotal: order.totalAmount,
      dirty: false,
      createIdempotencyKey: key,
    );
  }

  void selectCustomer(CustomerSummary customer) {
    state.customerId = customer.customerId;
    state.customerName = customer.customerName;
    state.dirty = true;
    state.error = null;
  }

  void addOrReplaceItem(LocalOrderItem item, {int? replaceIndex}) {
    if (replaceIndex != null &&
        replaceIndex >= 0 &&
        replaceIndex < state.items.length) {
      state.items[replaceIndex] = item;
    } else {
      state.items.add(item);
    }
    state.dirty = true;
    state.error = null;
  }

  void removeItem(int index) {
    state.items.removeAt(index);
    state.dirty = true;
  }

  Future<void> applyUom(LocalOrderItem item, String uom) async {
    item.uom = uom;
    final info = item.allowedUoms.where((u) => u.uom == uom).firstOrNull;
    item.referencePrice = info?.referencePrice;
    item.lastDealPrice = null;
    // A UOM change starts a new price context.  Clear an old rate even when
    // the selected UOM has no reference price; carrying the previous UOM's
    // amount would create a misleading order line.
    item.rate = item.referencePrice;
    final customerId = state.customerId;
    if (customerId != null && customerId.isNotEmpty) {
      final last = await _loadLastDeal(
        customerId: customerId,
        itemCode: item.itemCode,
        uom: uom,
      );
      item.lastDealPrice = last?.price;
    }
    state.dirty = true;
  }

  bool validate() {
    var ok = true;
    state.error = null;
    if (state.customerId == null || state.customerId!.isEmpty) {
      state.error = '请选择客户';
      ok = false;
    }
    if (state.items.isEmpty) {
      state.error = '请添加商品';
      ok = false;
    }
    for (final item in state.items) {
      item.lineError = null;
      if (item.itemCode.isEmpty) {
        item.lineError = '商品未选择';
        ok = false;
      } else if (item.qty == null || item.qty! <= Decimal.zero) {
        item.lineError = '数量必须大于 0';
        ok = false;
      } else if (item.uom.isEmpty) {
        item.lineError = '请选择单位';
        ok = false;
      } else if (item.rate == null || item.rate! < Decimal.zero) {
        item.lineError = '单价不能为负';
        ok = false;
      }
    }
    return ok;
  }

  Future<Order?> saveDraft() async {
    if (state.readOnly) {
      return null;
    }
    if (!validate()) {
      return null;
    }
    if (state.unknownOutcome) {
      return null;
    }
    state.busy = true;
    state.error = null;
    state.conflict = false;
    try {
      final Order saved;
      if (state.orderId == null) {
        saved = await _orders.createDraft(
          customerId: state.customerId!,
          transactionDate: state.transactionDate,
          items: state.items.map((item) => item.toPayload()).toList(),
          idempotencyKey: state.createIdempotencyKey,
        );
      } else {
        saved = await _orders.updateDraft(
          orderId: state.orderId!,
          customerId: state.customerId!,
          transactionDate: state.transactionDate ?? savedOrToday(),
          expectedModifiedAt: state.updatedAt ?? DateTime.now().toUtc(),
          items: state.items.map((item) => item.toPayload()).toList(),
        );
      }
      loadExisting(saved);
      return saved;
    } on ApiException catch (error) {
      _applyError(error);
      return null;
    } finally {
      state.busy = false;
    }
  }

  DateTime savedOrToday() =>
      state.transactionDate ?? BusinessTime.shanghaiNow();

  Future<Order?> submit() async {
    if (state.readOnly) {
      return null;
    }
    if (!validate()) {
      return null;
    }
    if (state.unknownOutcome) {
      return null;
    }
    state.submitting = true;
    state.error = null;
    try {
      if (state.orderId == null || state.dirty) {
        final saved = await saveDraft();
        if (saved == null) {
          return null;
        }
      }
      final submitted = await _orders.submit(state.orderId!);
      loadExisting(submitted);
      return submitted;
    } on ApiException catch (error) {
      if (state.orderId != null && error.code != 'ORDER_CONFLICT') {
        state.submitFailedAfterCreate = true;
        state.error = '订单已保存为草稿，提交失败';
        state.traceId = error.traceId;
      } else {
        _applyError(error);
      }
      return null;
    } finally {
      state.submitting = false;
    }
  }

  void _applyError(ApiException error) {
    state.traceId = error.traceId;
    if (error.code == 'ORDER_CONFLICT') {
      state.conflict = true;
      state.error = error.userMessage;
      return;
    }
    if (error.code == 'IDEMPOTENCY_OUTCOME_UNKNOWN') {
      state.unknownOutcome = true;
      state.error = error.userMessage;
      return;
    }
    state.error = error.userMessage;
  }
}
