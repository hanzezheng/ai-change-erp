package com.nongpi.assistant.erp.mapper;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import java.time.Clock;
import java.time.Instant;
import java.time.LocalDate;
import java.time.ZoneOffset;

import static org.assertj.core.api.Assertions.assertThat;

@DisplayName("经营日时区")
class ErpDatesTest {

    @Test
    @DisplayName("UTC Docker 在北京时间已跨日时，经营日取 Asia/Shanghai")
    void utcMidnightUsesShanghaiBusinessDate() {
        Clock utc = Clock.fixed(Instant.parse("2026-08-18T16:30:00Z"), ZoneOffset.UTC);
        assertThat(LocalDate.now(utc)).isEqualTo(LocalDate.of(2026, 8, 18));
        assertThat(ErpDates.today(utc)).isEqualTo(LocalDate.of(2026, 8, 19));
    }

    @Test
    @DisplayName("上海仍是当天时，不提前切到下一天")
    void shanghaiAfternoonKeepsSameCalendarDate() {
        Clock utc = Clock.fixed(Instant.parse("2026-08-19T12:00:00Z"), ZoneOffset.UTC);
        assertThat(ErpDates.today(utc)).isEqualTo(LocalDate.of(2026, 8, 19));
        assertThat(ErpDates.today(utc)).isEqualTo(LocalDate.now(utc.withZone(ErpDates.BUSINESS_ZONE)));
    }
}
