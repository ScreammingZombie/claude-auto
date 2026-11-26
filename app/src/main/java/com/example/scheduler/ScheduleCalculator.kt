package com.example.scheduler

import com.example.model.ScheduleDays
import java.text.SimpleDateFormat
import java.util.Calendar
import java.util.Date
import java.util.Locale

object ScheduleCalculator {

    /**
     * Calcula el próximo instante exacto (en epoch millis) para la hora y días elegidos.
     * Devuelve null si no hay días seleccionados.
     */
    fun calculateNextExecutionTimeMillis(
        nowMillis: Long,
        hour: Int,
        minute: Int,
        selectedDays: Set<Int>,
    ): Long? {
        if (selectedDays.isEmpty()) return null

        val safeHour = hour.coerceIn(0, 23)
        val safeMinute = minute.coerceIn(0, 59)

        val candidate = Calendar.getInstance().apply {
            timeInMillis = nowMillis
            set(Calendar.HOUR_OF_DAY, safeHour)
            set(Calendar.MINUTE, safeMinute)
            set(Calendar.SECOND, 0)
            set(Calendar.MILLISECOND, 0)
        }

        // Buscar desde hoy hasta los próximos 7 días (inclusive hoy + 7 = 8 comprobaciones)
        for (offset in 0..7) {
            val checkCal = (candidate.clone() as Calendar).apply {
                if (offset > 0) add(Calendar.DAY_OF_YEAR, offset)
            }
            val dayOfWeek = checkCal.get(Calendar.DAY_OF_WEEK)
            if (selectedDays.contains(dayOfWeek) && checkCal.timeInMillis > nowMillis) {
                return checkCal.timeInMillis
            }
        }
        return null
    }

    fun formatNextExecution(epochMillis: Long?, nowMillis: Long = System.currentTimeMillis()): String {
        if (epochMillis == null || epochMillis <= 0L) {
            return "Sin programación activa"
        }
        val localeEs = Locale.forLanguageTag("es-ES")
        val targetCal = Calendar.getInstance().apply { timeInMillis = epochMillis }
        val todayCal = Calendar.getInstance().apply { timeInMillis = nowMillis }
        val tomorrowCal = Calendar.getInstance().apply {
            timeInMillis = nowMillis
            add(Calendar.DAY_OF_YEAR, 1)
        }

        val timeText = SimpleDateFormat("HH:mm", localeEs).format(Date(epochMillis))
        return when {
            isSameDay(targetCal, todayCal) -> "Hoy · $timeText"
            isSameDay(targetCal, tomorrowCal) -> "Mañana · $timeText"
            else -> {
                val dateText = SimpleDateFormat("EEE d MMM", localeEs).format(Date(epochMillis))
                "${dateText.replaceFirstChar { it.uppercase(localeEs) }} · $timeText"
            }
        }
    }

    fun formatSelectedDaysSummary(selectedDays: Set<Int>): String {
        if (selectedDays.isEmpty()) return "Ningún día seleccionado"
        if (selectedDays.containsAll(ScheduleDays.ALL_CALENDAR_DAYS)) return "Todos los días"
        return ScheduleDays.ALL_DAYS
            .filter { selectedDays.contains(it.calendarDay) }
            .joinToString(", ") { it.fullName.take(3) }
    }

    private fun isSameDay(a: Calendar, b: Calendar): Boolean {
        return a.get(Calendar.YEAR) == b.get(Calendar.YEAR) &&
            a.get(Calendar.DAY_OF_YEAR) == b.get(Calendar.DAY_OF_YEAR)
    }
}
