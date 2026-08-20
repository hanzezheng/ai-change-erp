"""Phase 1B 真实 ERPNext 测试数据。

只使用标准 ERPNext DocType 与标准字段，不修改 ERPNext 数据模型，
不创建 Custom App / Custom Field。
"""

import frappe

frappe.init(site="frontend")
frappe.connect()
frappe.set_user("Administrator")
frappe.flags.in_import = True


def log(msg):
    print("[seed] " + msg)


def ensure_uom(uom_name, must_be_whole=0):
    if not frappe.db.exists("UOM", uom_name):
        frappe.get_doc({
            "doctype": "UOM",
            "uom_name": uom_name,
            "must_be_whole_number": must_be_whole,
        }).insert(ignore_permissions=True)
        log("UOM created: " + uom_name)
    return uom_name


def ensure_item_group(name, parent="All Item Groups"):
    if not frappe.db.exists("Item Group", name):
        frappe.get_doc({
            "doctype": "Item Group",
            "item_group_name": name,
            "parent_item_group": parent,
            "is_group": 0,
        }).insert(ignore_permissions=True)
        log("Item Group created: " + name)
    return name


def ensure_customer(customer_name, mobile, address_line, city):
    if not frappe.db.exists("Customer", customer_name):
        frappe.get_doc({
            "doctype": "Customer",
            "customer_name": customer_name,
            "customer_type": "Company",
            "customer_group": "Commercial",
            "territory": "All Territories",
            "mobile_no": mobile,
        }).insert(ignore_permissions=True)
        log("Customer created: " + customer_name)

    address_title = customer_name + "-Billing"
    if not frappe.db.exists("Address", {"address_title": address_title}):
        addr = frappe.get_doc({
            "doctype": "Address",
            "address_title": address_title,
            "address_type": "Billing",
            "address_line1": address_line,
            "city": city,
            "country": "China",
            "is_primary_address": 1,
            "links": [{"link_doctype": "Customer", "link_name": customer_name}],
        })
        addr.insert(ignore_permissions=True)
        log("Address created for " + customer_name)
    return customer_name


def ensure_attribute(attr_name, values):
    if not frappe.db.exists("Item Attribute", attr_name):
        frappe.get_doc({
            "doctype": "Item Attribute",
            "attribute_name": attr_name,
            "item_attribute_values": [
                {"attribute_value": v, "abbr": v} for v in values
            ],
        }).insert(ignore_permissions=True)
        log("Item Attribute created: " + attr_name)
    return attr_name


def ensure_template(item_code, item_name, item_group, stock_uom, attr_name):
    if not frappe.db.exists("Item", item_code):
        frappe.get_doc({
            "doctype": "Item",
            "item_code": item_code,
            "item_name": item_name,
            "item_group": item_group,
            "stock_uom": stock_uom,
            "is_stock_item": 1,
            "is_sales_item": 1,
            "has_variants": 1,
            "attributes": [{"attribute": attr_name}],
        }).insert(ignore_permissions=True)
        log("Item template created: " + item_code)
    return item_code


def ensure_variant(item_code, item_name, template, attr_name, attr_value,
                   stock_uom, sales_uom=None, extra_uoms=None, safety_stock=None):
    if not frappe.db.exists("Item", item_code):
        doc = frappe.get_doc({
            "doctype": "Item",
            "item_code": item_code,
            "item_name": item_name,
            "item_group": frappe.db.get_value("Item", template, "item_group"),
            "stock_uom": stock_uom,
            "is_stock_item": 1,
            "is_sales_item": 1,
            "has_variants": 0,
            "variant_of": template,
            "attributes": [{"attribute": attr_name, "attribute_value": attr_value}],
        })
        if sales_uom:
            doc.sales_uom = sales_uom
        if safety_stock is not None:
            doc.safety_stock = safety_stock
        doc.insert(ignore_permissions=True)
        log("Item variant created: " + item_code)

    if extra_uoms:
        doc = frappe.get_doc("Item", item_code)
        existing = {row.uom for row in doc.uoms}
        changed = False
        for uom, factor in extra_uoms:
            if uom not in existing:
                doc.append("uoms", {"uom": uom, "conversion_factor": factor})
                changed = True
        if changed:
            doc.save(ignore_permissions=True)
            log("Item UOMs updated: " + item_code)
    return item_code


def ensure_plain_item(item_code, item_name, item_group, stock_uom,
                      sales_uom=None, extra_uoms=None, safety_stock=None):
    if not frappe.db.exists("Item", item_code):
        doc = frappe.get_doc({
            "doctype": "Item",
            "item_code": item_code,
            "item_name": item_name,
            "item_group": item_group,
            "stock_uom": stock_uom,
            "is_stock_item": 1,
            "is_sales_item": 1,
            "has_variants": 0,
        })
        if sales_uom:
            doc.sales_uom = sales_uom
        if safety_stock is not None:
            doc.safety_stock = safety_stock
        doc.insert(ignore_permissions=True)
        log("Plain item created: " + item_code)

    if extra_uoms:
        doc = frappe.get_doc("Item", item_code)
        existing = {row.uom for row in doc.uoms}
        changed = False
        for uom, factor in extra_uoms:
            if uom not in existing:
                doc.append("uoms", {"uom": uom, "conversion_factor": factor})
                changed = True
        if changed:
            doc.save(ignore_permissions=True)
            log("Item UOMs updated: " + item_code)
    return item_code


def ensure_price(item_code, price_list, rate, uom=None):
    filters = {"item_code": item_code, "price_list": price_list}
    if uom:
        filters["uom"] = uom
    if not frappe.db.exists("Item Price", filters):
        doc = frappe.get_doc({
            "doctype": "Item Price",
            "item_code": item_code,
            "price_list": price_list,
            "price_list_rate": rate,
        })
        if uom:
            doc.uom = uom
        doc.insert(ignore_permissions=True)
        log("Item Price created: %s %s %s" % (item_code, uom, rate))


def ensure_reorder(item_code, warehouse, level, qty):
    doc = frappe.get_doc("Item", item_code)
    for row in doc.reorder_levels:
        if row.warehouse == warehouse:
            return
    doc.append("reorder_levels", {
        "warehouse": warehouse,
        "warehouse_reorder_level": level,
        "warehouse_reorder_qty": qty,
        "material_request_type": "Purchase",
    })
    doc.save(ignore_permissions=True)
    log("Reorder level set: %s @ %s = %s" % (item_code, warehouse, level))


def receive_stock(rows, warehouse):
    company = frappe.defaults.get_defaults().get("company") or \
        frappe.get_all("Company", limit=1)[0]["name"]

    pending = []
    for item_code, qty, rate in rows:
        existing = frappe.db.get_value(
            "Bin", {"item_code": item_code, "warehouse": warehouse}, "actual_qty")
        if existing:
            continue
        pending.append((item_code, qty, rate))

    if not pending:
        log("Stock already present, skipping Stock Entry")
        return

    se = frappe.get_doc({
        "doctype": "Stock Entry",
        "stock_entry_type": "Material Receipt",
        "company": company,
        "to_warehouse": warehouse,
        "items": [
            {
                "item_code": item_code,
                "qty": qty,
                "t_warehouse": warehouse,
                "basic_rate": rate,
                "allow_zero_valuation_rate": 0,
            }
            for item_code, qty, rate in pending
        ],
    })
    se.insert(ignore_permissions=True)
    se.submit()
    log("Stock Entry submitted: " + se.name)


def main():
    company = frappe.get_all("Company", limit=1)[0]["name"]
    abbr = frappe.db.get_value("Company", company, "abbr")
    warehouse = "Stores - " + abbr
    if not frappe.db.exists("Warehouse", warehouse):
        warehouse = frappe.get_all(
            "Warehouse", filters={"is_group": 0}, limit=1)[0]["name"]
    log("Company=%s Warehouse=%s" % (company, warehouse))

    price_list = "Standard Selling"
    log("Price List=%s" % price_list)

    ensure_uom("箱")
    ensure_uom("斤")
    ensure_uom("件")

    group = ensure_item_group("农产品")

    ensure_customer("韩兆亮", "13800003456", "雨润农副产品批发市场 A12", "徐州")
    ensure_customer("王建国", "13900007788", "江南果品市场 B07", "广州")

    attr = ensure_attribute("果径", ["70果", "75果", "80果", "85果"])
    template = ensure_template("APPLE", "苹果", group, "箱", attr)

    ensure_variant("APPLE-70", "苹果70果", template, attr, "70果", "箱", sales_uom="箱")
    ensure_variant("APPLE-75", "苹果75果", template, attr, "75果", "箱", sales_uom="箱")
    ensure_variant("APPLE-80", "苹果80果", template, attr, "80果", "箱",
                   sales_uom="箱", extra_uoms=[("斤", 20)])
    ensure_variant("APPLE-85", "苹果85果", template, attr, "85果", "箱", sales_uom="箱")

    ensure_plain_item("BANANA-FEN", "香蕉粉蕉", group, "件",
                      sales_uom="件", extra_uoms=[("箱", 2)], safety_stock=20)

    ensure_price("APPLE-80", price_list, 68, uom="箱")
    ensure_price("APPLE-80", price_list, 3.8, uom="斤")
    ensure_price("APPLE-70", price_list, 55, uom="箱")
    ensure_price("APPLE-75", price_list, 60, uom="箱")
    ensure_price("BANANA-FEN", price_list, 32, uom="件")

    receive_stock([
        ("APPLE-70", 120, 50),
        ("APPLE-75", 200, 55),
        ("APPLE-80", 450, 60),
        ("APPLE-85", 80, 65),
        ("BANANA-FEN", 15, 28),
    ], warehouse)

    ensure_reorder("APPLE-85", warehouse, 100, 50)

    frappe.db.commit()
    log("DONE")


main()
