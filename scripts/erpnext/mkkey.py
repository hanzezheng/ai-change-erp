import frappe

frappe.init(site="frontend")
frappe.connect()

user = frappe.get_doc("User", "Administrator")
if not user.api_key:
    user.api_key = frappe.generate_hash(length=15)
secret = frappe.generate_hash(length=15)
user.api_secret = secret
user.save(ignore_permissions=True)
frappe.db.commit()

print("APIKEY=" + user.api_key)
print("APISECRET=" + secret)
