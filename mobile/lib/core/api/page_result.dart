class PageResult<T> {
  const PageResult({
    required this.content,
    required this.page,
    required this.pageSize,
    required this.hasMore,
  });

  final List<T> content;
  final int page;
  final int pageSize;
  final bool hasMore;

  factory PageResult.fromJson(
    Map<String, dynamic> json,
    T Function(Map<String, dynamic> json) mapItem,
  ) {
    final raw = json['content'];
    final items = <T>[];
    if (raw is List) {
      for (final item in raw) {
        if (item is Map<String, dynamic>) {
          items.add(mapItem(item));
        } else if (item is Map) {
          items.add(mapItem(Map<String, dynamic>.from(item)));
        }
      }
    }
    return PageResult(
      content: items,
      page: (json['page'] as num?)?.toInt() ?? 1,
      pageSize: (json['pageSize'] as num?)?.toInt() ?? 20,
      hasMore: json['hasMore'] == true,
    );
  }
}
