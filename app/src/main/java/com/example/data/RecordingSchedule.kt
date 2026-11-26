package com.example.data

import java.util.Calendar
import java.util.Locale

enum class ScheduleMode(val label: String, val subtitle: String) {
    ALWAYS("Always", "24/7 anytime"),
    DAY_ONLY("Day Only", "Daytime hours"),
    SUNRISE_SUNSET("Sunrise & Sunset", "Dawn & dusk"),
    CUSTOM_WINDOW("Specific Time", "Custom window")
}

data class RecordingSchedule(
    val mode: ScheduleMode = ScheduleMode.ALWAYS,
    val customStartMinuteOfDay: Int = 12 * 60, // 12:00 PM
    val customEndMinuteOfDay: Int = 14 * 60,   // 2:00 PM
    val dayStartMinuteOfDay: Int = 6 * 60,     // 6:00 AM
    val dayEndMinuteOfDay: Int = 18 * 60,      // 6:00 PM
    val sunriseStartMinuteOfDay: Int = 5 * 60 + 30, // 5:30 AM
    val sunriseEndMinuteOfDay: Int = 7 * 60 + 30,   // 7:30 AM
    val sunsetStartMinuteOfDay: Int = 17 * 60 + 30, // 5:30 PM
    val sunsetEndMinuteOfDay: Int = 19 * 60 + 30    // 7:30 PM
) {
    fun isActiveAt(calendar: Calendar): Boolean {
        val minuteOfDay = calendar.get(Calendar.HOUR_OF_DAY) * 60 + calendar.get(Calendar.MINUTE)
        return isActiveAtMinute(minuteOfDay)
    }

    fun isActiveAtMinute(minuteOfDay: Int): Boolean {
        val m = minuteOfDay.mod(MINUTES_PER_DAY)
        return when (mode) {
            ScheduleMode.ALWAYS -> true
            ScheduleMode.DAY_ONLY -> isInWindow(m, dayStartMinuteOfDay, dayEndMinuteOfDay)
            ScheduleMode.SUNRISE_SUNSET ->
                isInWindow(m, sunriseStartMinuteOfDay, sunriseEndMinuteOfDay) ||
                    isInWindow(m, sunsetStartMinuteOfDay, sunsetEndMinuteOfDay)
            ScheduleMode.CUSTOM_WINDOW -> isInWindow(m, customStartMinuteOfDay, customEndMinuteOfDay)
        }
    }

    /**
     * Returns milliseconds until the next active schedule window opens, or 0L if currently active.
     */
    fun millisUntilNextWindow(calendar: Calendar): Long {
        if (isActiveAt(calendar)) return 0L

        val hour = calendar.get(Calendar.HOUR_OF_DAY)
        val minute = calendar.get(Calendar.MINUTE)
        val second = calendar.get(Calendar.SECOND)
        val millis = calendar.get(Calendar.MILLISECOND)
        val currentMinuteOfDay = hour * 60 + minute
        val secondsIntoMinuteMs = second * 1000L + millis

        val candidateStarts = when (mode) {
            ScheduleMode.ALWAYS -> return 0L
            ScheduleMode.DAY_ONLY -> listOf(dayStartMinuteOfDay)
            ScheduleMode.SUNRISE_SUNSET -> listOf(sunriseStartMinuteOfDay, sunsetStartMinuteOfDay)
            ScheduleMode.CUSTOM_WINDOW -> listOf(customStartMinuteOfDay)
        }

        val minMinutesAhead = candidateStarts.minOf { startMin ->
            val diff = (startMin - currentMinuteOfDay).mod(MINUTES_PER_DAY)
            if (diff == 0) MINUTES_PER_DAY else diff
        }

        return (minMinutesAhead * 60_000L - secondsIntoMinuteMs).coerceAtLeast(1000L)
    }

    fun nextWindowStartMinute(calendar: Calendar): Int? {
        if (isActiveAt(calendar)) return null
        val currentMinuteOfDay = calendar.get(Calendar.HOUR_OF_DAY) * 60 + calendar.get(Calendar.MINUTE)
        val candidateStarts = when (mode) {
            ScheduleMode.ALWAYS -> return null
            ScheduleMode.DAY_ONLY -> listOf(dayStartMinuteOfDay)
            ScheduleMode.SUNRISE_SUNSET -> listOf(sunriseStartMinuteOfDay, sunsetStartMinuteOfDay)
            ScheduleMode.CUSTOM_WINDOW -> listOf(customStartMinuteOfDay)
        }
        return candidateStarts.minByOrNull { startMin ->
            val diff = (startMin - currentMinuteOfDay).mod(MINUTES_PER_DAY)
            if (diff == 0) MINUTES_PER_DAY else diff
        }
    }

    fun summaryLabel(): String {
        return when (mode) {
            ScheduleMode.ALWAYS -> "24/7 Anytime"
            ScheduleMode.DAY_ONLY -> "${formatTimeOfDay(dayStartMinuteOfDay)} – ${formatTimeOfDay(dayEndMinuteOfDay)}"
            ScheduleMode.SUNRISE_SUNSET ->
                "${formatTimeOfDay(sunriseStartMinuteOfDay)}–${formatTimeOfDay(sunriseEndMinuteOfDay)} & " +
                    "${formatTimeOfDay(sunsetStartMinuteOfDay)}–${formatTimeOfDay(sunsetEndMinuteOfDay)}"
            ScheduleMode.CUSTOM_WINDOW ->
                "${formatTimeOfDay(customStartMinuteOfDay)} – ${formatTimeOfDay(customEndMinuteOfDay)}"
        }
    }

    companion object {
        const val MINUTES_PER_DAY = 24 * 60

        fun isInWindow(minuteOfDay: Int, startMinute: Int, endMinute: Int): Boolean {
            val m = minuteOfDay.mod(MINUTES_PER_DAY)
            val s = startMinute.mod(MINUTES_PER_DAY)
            val e = endMinute.mod(MINUTES_PER_DAY)
            return when {
                s == e -> true // 24-hour span
                s < e -> m in s until e
                else -> m >= s || m < e // Overnight wrap (e.g. 22:00 to 06:00)
            }
        }

        fun formatTimeOfDay(minuteOfDay: Int): String {
            val clamped = minuteOfDay.mod(MINUTES_PER_DAY)
            val hour24 = clamped / 60
            val minute = clamped % 60
            val amPm = if (hour24 < 12) "AM" else "PM"
            val hour12 = when (val h = hour24 % 12) {
                0 -> 12
                else -> h
            }
            return if (minute == 0) {
                "$hour12 $amPm"
            } else {
                String.format(Locale.US, "%d:%02d %s", hour12, minute, amPm)
            }
        }

        fun formatTimeOfDayFull(minuteOfDay: Int): String {
            val clamped = minuteOfDay.mod(MINUTES_PER_DAY)
            val hour24 = clamped / 60
            val minute = clamped % 60
            val amPm = if (hour24 < 12) "AM" else "PM"
            val hour12 = when (val h = hour24 % 12) {
                0 -> 12
                else -> h
            }
            return String.format(Locale.US, "%d:%02d %s", hour12, minute, amPm)
        }
    }
}
