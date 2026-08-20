"""把 韩兆亮 的主地址设为 customer_primary_address（标准 ERPNext 数据录入行为）。

王建国 故意保持不设置主地址，用于验证「ERP 没配置某字段时系统不伪造」。
"""

import frappe

frappe.init(site="frontend")
frappe.connect()
frappe.set_user("Administrator")

addr = frappe.db.get_value("Address", {"address_title": "韩兆亮-Billing"}, "name")
cust = frappe.get_doc("Customer", "韩兆亮")
cust.customer_primary_address = addr
cust.save(ignore_permissions=True)
frappe.db.commit()

cust.reload()
print("customer_primary_address=%r" % cust.customer_primary_address)
print("primary_address=%r" % cust.primary_address)

wang = frappe.get_doc("Customer", "王建国")
print("wang customer_primary_address=%r" % wang.customer_primary_address)
print("wang primary_address=%r" % wang.primary_address)
