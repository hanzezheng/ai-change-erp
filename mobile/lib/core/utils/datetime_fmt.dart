import 'package:intl/intl.dart';

/// Product business clock: Asia/Shanghai (UTC+8, no DST).
class BusinessTime {
  BusinessTime._();

  static const Duration shanghaiOffset = Duration(hours: 8);

  static DateTime shanghaiNow() {
    return DateTime.now().toUtc().add(shanghaiOffset);
  }

  static DateTime toShanghai(DateTime utcOrLocal) {
    return utcOrLocal.toUtc().add(shanghaiOffset);
  }

  static String formatDateLong([DateTime? shanghai]) {
    final time = shanghai ?? shanghaiNow();
    const weekdays = ['星期一', '星期二', '星期三', '星期四', '星期五', '星期六', '星期日'];
    final weekday = weekdays[time.weekday - 1];
    return '${time.year}年${time.month}月${time.day}日 $weekday';
  }

  static String greeting([DateTime? shanghai]) {
    final hour = (shanghai ?? shanghaiNow()).hour;
    if (hour < 12) {
      return '上午好';
    }
    if (hour < 18) {
      return '下午好';
    }
    return '晚上好';
  }

  static String formatListTime(DateTime instant) {
    final shanghai = toShanghai(instant);
    final today = shanghaiNow();
    final time = DateFormat('HH:mm').format(shanghai);
    if (_sameDay(shanghai, today)) {
      return '今天 $time';
    }
    final yesterday = today.subtract(const Duration(days: 1));
    if (_sameDay(shanghai, yesterday)) {
      return '昨天 $time';
    }
    return DateFormat('MM-dd HH:mm').format(shanghai);
  }

  static String formatDateTime(DateTime instant) {
    return DateFormat('yyyy-MM-dd HH:mm').format(toShanghai(instant));
  }

  static String formatDate(DateTime date) {
    return DateFormat('yyyy-MM-dd').format(date);
  }

  static DateTime? tryParseInstant(Object? value) {
    if (value == null) {
      return null;
    }
    return DateTime.tryParse(value.toString());
  }

  static DateTime parseInstant(Object value) {
    return DateTime.parse(value.toString());
  }

  static DateTime? tryParseDate(Object? value) {
    if (value == null) {
      return null;
    }
    return DateTime.tryParse(value.toString());
  }

  static bool _sameDay(DateTime a, DateTime b) {
    return a.year == b.year && a.month == b.month && a.day == b.day;
  }
}
