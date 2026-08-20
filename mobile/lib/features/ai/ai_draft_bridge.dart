import '../orders/presentation/order_edit_controller.dart';

/// 当前正在编辑的订单草稿，供 AI update_current_order 使用。
class AiDraftBridge {
  OrderEditController? controller;
  void Function()? notifyUi;

  bool get hasActiveDraft => controller != null && !controller!.state.readOnly;

  void attach(OrderEditController value, void Function() onChanged) {
    controller = value;
    notifyUi = onChanged;
  }

  void detach(OrderEditController value) {
    if (identical(controller, value)) {
      controller = null;
      notifyUi = null;
    }
  }

  void refresh() => notifyUi?.call();
}
