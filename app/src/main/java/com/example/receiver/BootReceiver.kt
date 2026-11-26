package com.example.receiver

import android.app.AlarmManager
import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent
import com.example.ClaudeAutoRunApp
import com.example.model.ActivityLogEntry
import com.example.model.AutomationStatus
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch

class BootReceiver : BroadcastReceiver() {

    override fun onReceive(context: Context, intent: Intent?) {
        val action = intent?.action ?: return
        if (action != Intent.ACTION_BOOT_COMPLETED &&
            action != Intent.ACTION_LOCKED_BOOT_COMPLETED &&
            action != Intent.ACTION_MY_PACKAGE_REPLACED &&
            action != AlarmManager.ACTION_SCHEDULE_EXACT_ALARM_PERMISSION_STATE_CHANGED
        ) {
            return
        }

        val pendingResult = goAsync()
        val app = ClaudeAutoRunApp.from(context)

        CoroutineScope(Dispatchers.IO).launch {
            try {
                val config = app.repository.getConfig()
                if (config.isScheduleActive) {
                    val result = app.alarmScheduler.scheduleNextAlarm(config)
                    app.repository.updateScheduleState(
                        isActive = result.scheduled,
                        nextExecutionMillis = result.nextExecutionMillis,
                    )
                    app.repository.appendLog(
                        ActivityLogEntry(
                            status = if (result.scheduled) AutomationStatus.PROGRAMADA else AutomationStatus.ERROR,
                            message = "Sistema iniciado ($action): ${result.message}",
                            isError = !result.scheduled,
                        ),
                    )
                    app.coordinator.markScheduleUpdated(result.scheduled, result.nextExecutionMillis)
                }
            } finally {
                pendingResult.finish()
            }
        }
    }
}
