"""运行标准 ERPNext 安装向导，建立 Company / Warehouse / Price List 等基础主数据。

这是 ERPNext 官方 onboarding 流程，不修改 ERPNext 源码或数据模型。
"""

import frappe
from frappe.desk.page.setup_wizard.setup_wizard import setup_complete

frappe.init(site="frontend")
frappe.connect()
frappe.set_user("Administrator")
frappe.flags.in_setup_wizard = True

args = {
    "language": "English",
    "country": "China",
    "timezone": "Asia/Shanghai",
    "currency": "CNY",
    "company_name": "农批测试档口",
    "company_abbr": "NPT",
    "chart_of_accounts": "Standard",
    "fy_start_date": "2026-01-01",
    "fy_end_date": "2026-12-31",
    "full_name": "Administrator",
    "email": "admin@example.com",
    "password": "admin",
}

setup_complete(args)
frappe.db.commit()

print("SETUP_COMPLETE=" + str(frappe.db.get_single_value("System Settings", "setup_complete")))
print("COMPANIES=" + str([c["name"] for c in frappe.get_all("Company")]))
print("WAREHOUSES=" + str([w["name"] for w in frappe.get_all("Warehouse")]))
print("PRICE_LISTS=" + str([p["name"] for p in frappe.get_all("Price List")]))
