class CustomerSummary {
  const CustomerSummary({
    required this.customerId,
    required this.customerName,
    this.aliases = const [],
    this.phone,
    this.address,
  });

  final String customerId;
  final String customerName;
  final List<String> aliases;
  final String? phone;
  final String? address;

  factory CustomerSummary.fromJson(Map<String, dynamic> json) {
    return CustomerSummary(
      customerId: json['customerId']?.toString() ?? '',
      customerName: json['customerName']?.toString() ?? '',
      aliases: (json['aliases'] as List?)?.map((e) => e.toString()).toList() ?? const [],
      phone: json['phone']?.toString(),
      address: json['address']?.toString(),
    );
  }
}

class CustomerSelectorResult {
  const CustomerSelectorResult({
    required this.recent,
    required this.results,
  });

  final List<CustomerSummary> recent;
  final List<CustomerSummary> results;

  factory CustomerSelectorResult.fromJson(Map<String, dynamic> json) {
    List<CustomerSummary> parse(String key) {
      final raw = json[key];
      if (raw is! List) {
        return const [];
      }
      return raw
          .whereType<Map>()
          .map((item) => CustomerSummary.fromJson(Map<String, dynamic>.from(item)))
          .toList();
    }

    return CustomerSelectorResult(recent: parse('recent'), results: parse('results'));
  }
}
