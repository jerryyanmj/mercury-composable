package com.accenture.minigraph.mortgage;

import org.junit.jupiter.api.Test;

import java.util.List;
import java.util.Map;

import static org.junit.jupiter.api.Assertions.*;

class MortgageHelpersTest {

    @SuppressWarnings("unchecked")
    @Test
    void expandBuildsPerMonthArray() throws Exception {
        var out = new ExpandExtras().handleEvent(Map.of(), Map.of(
                "term_years", 1, "extra_monthly", 100, "extra_annual", 500,
                "lump_sums", List.of(Map.of("month", 6, "amount", 1000)),
                "annual_property_tax", 3600, "annual_home_insurance", 1200), 1);
        var extras = (List<Double>) out.get("extras");
        assertEquals(12, extras.size());
        assertEquals(100.0, extras.get(0), 0.001);         // month 1: monthly only
        assertEquals(1100.0, extras.get(5), 0.001);        // month 6: monthly + lump
        assertEquals(600.0, extras.get(11), 0.001);        // month 12: monthly + annual
        assertEquals(3600.0, (double) out.get("tax"), 0.001);
        assertEquals(1200.0, (double) out.get("insurance"), 0.001);
    }

    @Test
    void expandRejectsBadLumpMonth() {
        assertThrows(IllegalArgumentException.class, () -> new ExpandExtras().handleEvent(Map.of(),
                Map.of("term_years", 30, "lump_sums", List.of(Map.of("month", 1, "amount", 5000))), 1));
        assertThrows(IllegalArgumentException.class, () -> new ExpandExtras().handleEvent(Map.of(),
                Map.of("term_years", 1, "lump_sums", List.of(Map.of("month", 99, "amount", 5000))), 1));
    }

    @SuppressWarnings("unchecked")
    @Test
    void formatZipsTrimsAndDates() throws Exception {
        var out = new FormatSchedule().handleEvent(Map.of(), Map.of(
                "months", List.of(1, 2, 3),
                "payments", List.of(1000.0, 1000.0, 500.0),
                "principals", List.of(800.0, 850.0, 500.0),
                "interests", List.of(200.0, 150.0, 0.0),
                "extras", List.of(0.0, 0.0, 0.0),
                "balances", List.of(9200.0, 8350.0, 0.0),
                "start_date", "2026-01-01",
                "payoff_month", 2), 1);
        var schedule = (List<Map<String, Object>>) out.get("schedule");
        assertEquals(2, schedule.size());                          // trimmed to payoff_month
        assertEquals("2026-01-01", schedule.get(0).get("date"));
        assertEquals("2026-02-01", schedule.get(1).get("date"));
        assertEquals(1, schedule.get(0).get("month"));
        assertEquals("2026-02-01", out.get("payoff_date"));
    }
}
