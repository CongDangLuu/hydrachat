package com.hydrachat.app.domain

import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test
import java.time.LocalDate
import java.time.LocalDateTime
import java.time.ZoneId

class HydrationMathTest {
    private val zone = ZoneId.of("Asia/Ho_Chi_Minh")
    private val schedule = WorkSchedule(startMinute = 8 * 60 + 30, endMinute = 17 * 60 + 30, skipWeekends = true)

    // 2026-11-16 is a Monday, 2026-11-20 a Friday.
    private fun at(day: Int, hour: Int, minute: Int): Long =
        LocalDateTime.of(2026, 11, day, hour, minute).atZone(zone).toInstant().toEpochMilli()

    @Test
    fun targetFollowsWeightAndAge() {
        assertEquals(2100, HydrationMath.dailyTargetMl(60, isAge55Plus = false))
        assertEquals(1800, HydrationMath.dailyTargetMl(60, isAge55Plus = true))
        assertEquals(2300, HydrationMath.dailyTargetMl(65, isAge55Plus = false))
    }

    @Test
    fun targetIsClampedAtBothEnds() {
        assertEquals(1500, HydrationMath.dailyTargetMl(40, isAge55Plus = false))
        assertEquals(1500, HydrationMath.dailyTargetMl(45, isAge55Plus = true))
        assertEquals(3000, HydrationMath.dailyTargetMl(110, isAge55Plus = false))
        assertEquals(3000, HydrationMath.dailyTargetMl(150, isAge55Plus = true))
    }

    @Test
    fun autoIntervalSpreadsCupsOverWorkday() {
        assertEquals(65, HydrationMath.autoIntervalMinutes(2000, 250, schedule))
        assertEquals(30, HydrationMath.autoIntervalMinutes(4000, 100, schedule))
        assertEquals(90, HydrationMath.autoIntervalMinutes(1200, 500, schedule))
    }

    @Test
    fun nextReminderStaysInsideWorkHours() {
        assertEquals(at(16, 11, 0), HydrationMath.nextReminderAt(at(16, 10, 0), schedule, 60, zone))
        assertEquals(at(16, 8, 40), HydrationMath.nextReminderAt(at(16, 6, 0), schedule, 60, zone))
        assertEquals(at(17, 8, 40), HydrationMath.nextReminderAt(at(16, 17, 0), schedule, 60, zone))
        assertEquals(at(17, 8, 40), HydrationMath.nextReminderAt(at(16, 23, 30), schedule, 60, zone))
    }

    @Test
    fun nextReminderSkipsWeekendAndFinishedDay() {
        assertEquals(at(23, 8, 40), HydrationMath.nextReminderAt(at(20, 17, 0), schedule, 60, zone))
        assertEquals(at(17, 8, 40), HydrationMath.nextReminderAt(at(16, 10, 0), schedule, 60, zone, skipToday = true))
        val everyDay = schedule.copy(skipWeekends = false)
        assertEquals(at(21, 8, 40), HydrationMath.nextReminderAt(at(20, 17, 0), everyDay, 60, zone))
    }

    @Test
    fun workWindowCheck() {
        assertTrue(HydrationMath.isWithinWork(at(16, 9, 0), schedule, zone))
        assertFalse(HydrationMath.isWithinWork(at(16, 7, 0), schedule, zone))
        assertFalse(HydrationMath.isWithinWork(at(16, 18, 0), schedule, zone))
        assertFalse(HydrationMath.isWithinWork(at(21, 9, 0), schedule, zone))
    }

    @Test
    fun streakIgnoresUnfinishedToday() {
        val entries = listOf(at(13, 9, 0) to 2000, at(14, 9, 0) to 1000, at(14, 15, 0) to 1000, at(15, 9, 0) to 2100, at(16, 9, 0) to 250)
        val today = LocalDate.of(2026, 11, 16)
        assertEquals(3, HydrationMath.streakDays(entries, 2000, today, zone))
        assertEquals(0, HydrationMath.streakDays(entries, 3000, today, zone))
        val totals = HydrationMath.dailyTotals(entries, 7, today, zone)
        assertEquals(7, totals.size)
        assertEquals(250, totals.last().totalMl)
        assertEquals(0, totals.first().totalMl)
    }

    @Test
    fun timeContextIsVietnamese() {
        assertEquals("15:15 chiều thứ 6", HydrationMath.timeContext(at(20, 15, 15), zone))
        assertEquals("10:30 sáng chủ nhật", HydrationMath.timeContext(at(22, 10, 30), zone))
    }
}
