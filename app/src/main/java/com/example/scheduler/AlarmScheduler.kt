package com.example.scheduler

import android.app.AlarmManager
import android.app.PendingIntent
import android.content.Context
import android.content.Intent
import android.os.Build
import com.example.model.AutoRunConfig
import com.example.receiver.AutoRunAlarmReceiver

data class ScheduleResult(
    val scheduled: Boolean,
    val nextExecutionMillis: Long?,
    val usedExactAlarm: Boolean,
    val message: String,
)

class AlarmScheduler(private val context: Context) {

    private val alarmManager: AlarmManager
        get() = context.getSystemService(Context.ALARM_SERVICE) as AlarmManager

    fun canScheduleExactAlarms(): Boolean {
        return if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.S) {
            alarmManager.canScheduleExactAlarms()
        } else {
            true
        }
    }

    fun scheduleNextAlarm(
        config: AutoRunConfig,
        nowMillis: Long = System.currentTimeMillis(),
    ): ScheduleResult {
        cancelAlarm()

        val triggerAtMillis = ScheduleCalculator.calculateNextExecutionTimeMillis(
            nowMillis = nowMillis,
            hour = config.hour,
            minute = config.minute,
            selectedDays = config.selectedDays,
        ) ?: return ScheduleResult(
            scheduled = false,
            nextExecutionMillis = null,
            usedExactAlarm = false,
            message = "Selecciona al menos un día de la semana para programar.",
        )

        val pendingIntent = buildAlarmPendingIntent(triggerAtMillis)
        val exactAllowed = canScheduleExactAlarms()

        return try {
            if (exactAllowed) {
                alarmManager.setExactAndAllowWhileIdle(
                    AlarmManager.RTC_WAKEUP,
                    triggerAtMillis,
                    pendingIntent,
                )
            } else {
                alarmManager.setAndAllowWhileIdle(
                    AlarmManager.RTC_WAKEUP,
                    triggerAtMillis,
                    pendingIntent,
                )
            }
            ScheduleResult(
                scheduled = true,
                nextExecutionMillis = triggerAtMillis,
                usedExactAlarm = exactAllowed,
                message = if (exactAllowed) {
                    "Alarma exacta programada para ${ScheduleCalculator.formatNextExecution(triggerAtMillis, nowMillis)}"
                } else {
                    "Alarma aproximada programada (habilita alarmas exactas en Ajustes para mayor precisión)"
                },
            )
        } catch (se: SecurityException) {
            alarmManager.setAndAllowWhileIdle(
                AlarmManager.RTC_WAKEUP,
                triggerAtMillis,
                pendingIntent,
            )
            ScheduleResult(
                scheduled = true,
                nextExecutionMillis = triggerAtMillis,
                usedExactAlarm = false,
                message = "Alarma programada sin permiso exacto (${se.message ?: "SecurityException"})",
            )
        }
    }

    fun cancelAlarm() {
        val existingIntent = PendingIntent.getBroadcast(
            context,
            REQUEST_CODE_AUTORUN,
            Intent(context, AutoRunAlarmReceiver::class.java).apply {
                action = ACTION_TRIGGER_AUTORUN
            },
            PendingIntent.FLAG_NO_CREATE or PendingIntent.FLAG_IMMUTABLE,
        )
        if (existingIntent != null) {
            alarmManager.cancel(existingIntent)
            existingIntent.cancel()
        }
    }

    private fun buildAlarmPendingIntent(triggerAtMillis: Long): PendingIntent {
        val intent = Intent(context, AutoRunAlarmReceiver::class.java).apply {
            action = ACTION_TRIGGER_AUTORUN
            putExtra(EXTRA_SCHEDULED_MILLIS, triggerAtMillis)
        }
        return PendingIntent.getBroadcast(
            context,
            REQUEST_CODE_AUTORUN,
            intent,
            PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE,
        )
    }

    companion object {
        const val REQUEST_CODE_AUTORUN = 1001
        const val ACTION_TRIGGER_AUTORUN = "com.example.action.TRIGGER_CLAUDE_AUTORUN"
        const val EXTRA_SCHEDULED_MILLIS = "extra_scheduled_millis"
    }
}
