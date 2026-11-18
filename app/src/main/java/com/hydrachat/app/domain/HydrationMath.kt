package com.hydrachat.app.domain

import java.time.DayOfWeek
import java.time.Instant
import java.time.LocalDate
import java.time.ZoneId
import java.time.ZonedDateTime

data class WorkSchedule(
    val startMinute: Int,
    val endMinute: Int,
    val skipWeekends: Boolean,
)

data class DayTotal(val date: LocalDate, val totalMl: Int)

object HydrationMath {
    const val SIP_ML = 100
    const val SNOOZE_MINUTES = 20
    const val DEFER_MINUTES = 10
    const val POINTS_PER_100_ML = 4
    const val MINI_QUEST_POINTS = 5
    const val GOAL_BONUS_POINTS = 50

    // Vietnam National Institute of Nutrition (2016): 35 ml/kg for moderately active adults, 30 ml/kg from age 55.
    private const val ML_PER_KG_ADULT = 35
    private const val ML_PER_KG_AGE_55_PLUS = 30
    private const val ROUNDING_ML = 50
    // Clamp so very light people are not under-served and heavy people are not pushed to over-drink.
    private const val MIN_TARGET_ML = 1500
    private const val MAX_TARGET_ML = 3000
    private const val MIN_INTERVAL_MINUTES = 30
    private const val MAX_INTERVAL_MINUTES = 90
    private const val INTERVAL_STEP_MINUTES = 5
    private const val FIRST_REMINDER_OFFSET_MINUTES = 10
    // Inexact alarms can fire a little late; still show them right after closing time.
    private const val END_OF_DAY_SLACK_MINUTES = 15
    private const val MINUTES_PER_HOUR = 60
    private const val MAX_STREAK_LOOKBACK_DAYS = 365L

    fun dailyTargetMl(weightKg: Int, isAge55Plus: Boolean): Int {
        val raw = weightKg * (if (isAge55Plus) ML_PER_KG_AGE_55_PLUS else ML_PER_KG_ADULT)
        val rounded = (raw + ROUNDING_ML / 2) / ROUNDING_ML * ROUNDING_ML
        return rounded.coerceIn(MIN_TARGET_ML, MAX_TARGET_ML)
    }

    fun autoIntervalMinutes(targetMl: Int, cupMl: Int, schedule: WorkSchedule): Int {
        val cups = ((targetMl + cupMl - 1) / cupMl).coerceAtLeast(1)
        val workMinutes = (schedule.endMinute - schedule.startMinute).coerceAtLeast(MIN_INTERVAL_MINUTES)
        val raw = workMinutes / cups / INTERVAL_STEP_MINUTES * INTERVAL_STEP_MINUTES
        return raw.coerceIn(MIN_INTERVAL_MINUTES, MAX_INTERVAL_MINUTES)
    }

    fun isWorkday(date: LocalDate, schedule: WorkSchedule): Boolean {
        val isWeekend = date.dayOfWeek == DayOfWeek.SATURDAY || date.dayOfWeek == DayOfWeek.SUNDAY
        return !(schedule.skipWeekends && isWeekend)
    }

    fun isWithinWork(nowMillis: Long, schedule: WorkSchedule, zone: ZoneId): Boolean {
        val now = Instant.ofEpochMilli(nowMillis).atZone(zone)
        val minute = minuteOfDay(now)
        return isWorkday(now.toLocalDate(), schedule) &&
            minute >= schedule.startMinute &&
            minute <= schedule.endMinute + END_OF_DAY_SLACK_MINUTES
    }

    /** Next reminder time; `skipToday` is used once the daily goal is reached. */
    fun nextReminderAt(
        nowMillis: Long,
        schedule: WorkSchedule,
        intervalMinutes: Int,
        zone: ZoneId,
        skipToday: Boolean = false,
    ): Long {
        val now = Instant.ofEpochMilli(nowMillis).atZone(zone)
        val today = now.toLocalDate()
        if (!skipToday && isWorkday(today, schedule)) {
            val candidate = now.plusMinutes(intervalMinutes.toLong())
            val isSameDay = candidate.toLocalDate() == today
            if (isSameDay && minuteOfDay(candidate) < schedule.startMinute) {
                return firstReminderOf(today, schedule, zone)
            }
            if (isSameDay && minuteOfDay(candidate) <= schedule.endMinute) {
                return candidate.toInstant().toEpochMilli()
            }
        }
        var date = today.plusDays(1)
        while (!isWorkday(date, schedule)) {
            date = date.plusDays(1)
        }
        return firstReminderOf(date, schedule, zone)
    }

    fun dayStartMillis(nowMillis: Long, zone: ZoneId): Long =
        Instant.ofEpochMilli(nowMillis).atZone(zone).toLocalDate().atStartOfDay(zone).toInstant().toEpochMilli()

    /** Totals for the last `days` days ending today, oldest first, zero-filled. */
    fun dailyTotals(
        entries: List<Pair<Long, Int>>,
        days: Int,
        today: LocalDate,
        zone: ZoneId,
    ): List<DayTotal> {
        val byDate = totalsByDate(entries, zone)
        return (days - 1 downTo 0).map { offset ->
            val date = today.minusDays(offset.toLong())
            DayTotal(date, byDate[date] ?: 0)
        }
    }

    /** Consecutive goal-reached days; an unfinished today does not break the streak. */
    fun streakDays(entries: List<Pair<Long, Int>>, targetMl: Int, today: LocalDate, zone: ZoneId): Int {
        val byDate = totalsByDate(entries, zone)
        var streak = 0
        var date = today
        if ((byDate[date] ?: 0) < targetMl) {
            date = date.minusDays(1)
        }
        while ((byDate[date] ?: 0) >= targetMl && streak < MAX_STREAK_LOOKBACK_DAYS) {
            streak++
            date = date.minusDays(1)
        }
        return streak
    }

    fun timeContext(millis: Long, zone: ZoneId): String {
        val time = Instant.ofEpochMilli(millis).atZone(zone)
        val partOfDay = when (time.hour) {
            in 0..10 -> "sáng"
            in 11..12 -> "trưa"
            in 13..17 -> "chiều"
            else -> "tối"
        }
        val weekday = when (time.dayOfWeek) {
            DayOfWeek.SUNDAY -> "chủ nhật"
            else -> "thứ ${time.dayOfWeek.value + 1}"
        }
        return "%02d:%02d %s %s".format(time.hour, time.minute, partOfDay, weekday)
    }

    fun formatMinuteOfDay(minute: Int): String =
        "%02d:%02d".format(minute / MINUTES_PER_HOUR, minute % MINUTES_PER_HOUR)

    private fun totalsByDate(entries: List<Pair<Long, Int>>, zone: ZoneId): Map<LocalDate, Int> =
        entries.groupBy({ Instant.ofEpochMilli(it.first).atZone(zone).toLocalDate() }, { it.second })
            .mapValues { it.value.sum() }

    private fun minuteOfDay(time: ZonedDateTime): Int = time.hour * MINUTES_PER_HOUR + time.minute

    private fun firstReminderOf(date: LocalDate, schedule: WorkSchedule, zone: ZoneId): Long =
        date.atStartOfDay(zone)
            .plusMinutes((schedule.startMinute + FIRST_REMINDER_OFFSET_MINUTES).toLong())
            .toInstant()
            .toEpochMilli()
}
