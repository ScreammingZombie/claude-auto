package com.example.receiver

import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent
import com.example.ClaudeAutoRunApp
import com.example.model.ActivityLogEntry
import com.example.model.AutomationStatus
import com.example.service.AutoRunForegroundService
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale

class AutoRunAlarmReceiver : BroadcastReceiver() {

    override fun onReceive(context: Context, intent: Intent?) {
        val pendingResult = goAsync()
        val app = ClaudeAutoRunApp.from(context)

        CoroutineScope(Dispatchers.IO).launch {
            try {
                val config = app.repository.getConfig()
                if (!config.isScheduleActive) {
                    return@launch
                }

                val minuteKey = SimpleDateFormat("yyyy-MM-dd HH:mm", Locale.US).format(Date())
                val claimed = app.repository.tryClaimAlarmMinuteKey(minuteKey)
                if (!claimed) {
                    app.repository.appendLog(
                        ActivityLogEntry(
                            status = AutomationStatus.PROGRAMADA,
                            message = "Alarma duplicada ignorada para el minuto $minuteKey.",
                        ),
                    )
                    return@launch
                }

                // Reprogramar inmediatamente la siguiente ejecución periódica
                val nextResult = app.alarmScheduler.scheduleNextAlarm(
                    config = config,
                    nowMillis = System.currentTimeMillis() + 61_000L,
                )
                app.repository.updateScheduleState(
                    isActive = nextResult.scheduled,
                    nextExecutionMillis = nextResult.nextExecutionMillis,
                )

                AutoRunForegroundService.start(
                    context = context.applicationContext,
                    triggerSource = "Alarma programada",
                )
            } finally {
                pendingResult.finish()
            }
        }
    }
}
