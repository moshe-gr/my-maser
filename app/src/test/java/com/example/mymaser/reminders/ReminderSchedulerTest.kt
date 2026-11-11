package com.example.mymaser.reminders

import java.time.Duration
import java.time.LocalDateTime
import org.junit.Assert.assertEquals
import org.junit.Test

class ReminderSchedulerTest {

    private val sevenPm = 19 * 60

    @Test
    fun `later today when the time hasn't passed`() {
        val now = LocalDateTime.of(2026, 11, 10, 18, 30)
        assertEquals(Duration.ofMinutes(30), ReminderScheduler.delayUntil(now, sevenPm))
    }

    @Test
    fun `tomorrow when the time has passed or is now`() {
        assertEquals(
            Duration.ofHours(23),
            ReminderScheduler.delayUntil(LocalDateTime.of(2026, 11, 10, 20, 0), sevenPm)
        )
        assertEquals(
            Duration.ofDays(1),
            ReminderScheduler.delayUntil(LocalDateTime.of(2026, 11, 10, 19, 0), sevenPm)
        )
    }
}
