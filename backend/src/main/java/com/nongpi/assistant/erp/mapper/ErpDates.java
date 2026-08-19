package com.nongpi.assistant.erp.mapper;

import java.time.Instant;
import java.time.LocalDate;
import java.time.LocalDateTime;
import java.time.ZoneId;
import java.time.format.DateTimeFormatter;
import java.time.format.DateTimeParseException;

/**
 * ERPNext 的日期时间是不带时区的字符串。面向 App 时按经营时区 Asia/Shanghai 解释。
 */
public final class ErpDates {

    public static final ZoneId BUSINESS_ZONE = ZoneId.of("Asia/Shanghai");
    private static final DateTimeFormatter DATE = DateTimeFormatter.ISO_LOCAL_DATE;
    private static final DateTimeFormatter[] DATE_TIMES = {
            DateTimeFormatter.ofPattern("yyyy-MM-dd HH:mm:ss.SSSSSS"),
            DateTimeFormatter.ofPattern("yyyy-MM-dd HH:mm:ss.SSS"),
            DateTimeFormatter.ofPattern("yyyy-MM-dd HH:mm:ss")
    };

    private ErpDates() {
    }

    public static LocalDate toLocalDate(String value) {
        String trimmed = ErpValues.trimToNull(value);
        return trimmed == null ? null : LocalDate.parse(trimmed.substring(0, 10), DATE);
    }

    public static Instant toInstant(String value) {
        String trimmed = ErpValues.trimToNull(value);
        if (trimmed == null) {
            return null;
        }
        if (trimmed.contains("T")) {
            try {
                return Instant.parse(trimmed);
            } catch (DateTimeParseException ignored) {
                // fall through to ERP naive datetime
            }
        }
        String normalized = trimmed.replace('T', ' ');
        if (normalized.length() > 26) {
            normalized = normalized.substring(0, 26);
        }
        for (DateTimeFormatter formatter : DATE_TIMES) {
            try {
                return LocalDateTime.parse(normalized, formatter).atZone(BUSINESS_ZONE).toInstant();
            } catch (DateTimeParseException ignored) {
                // try next pattern
            }
        }
        LocalDate date = toLocalDate(trimmed);
        return date == null ? null : date.atStartOfDay(BUSINESS_ZONE).toInstant();
    }

    public static String toErpDate(LocalDate date) {
        return date == null ? null : DATE.format(date);
    }
}
