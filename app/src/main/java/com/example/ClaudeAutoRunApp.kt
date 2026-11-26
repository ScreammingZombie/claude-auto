package com.example

import android.app.Application
import android.content.Context
import com.example.automation.AutomationCoordinator
import com.example.data.AutoRunRepository
import com.example.scheduler.AlarmScheduler
import com.example.util.NotificationHelper

class ClaudeAutoRunApp : Application() {

    lateinit var repository: AutoRunRepository
        private set

    lateinit var alarmScheduler: AlarmScheduler
        private set

    lateinit var coordinator: AutomationCoordinator
        private set

    override fun onCreate() {
        super.onCreate()
        NotificationHelper.ensureChannels(this)
        repository = AutoRunRepository(this)
        alarmScheduler = AlarmScheduler(this)
        coordinator = AutomationCoordinator(this, repository)
    }

    companion object {
        fun from(context: Context): ClaudeAutoRunApp {
            return context.applicationContext as ClaudeAutoRunApp
        }

        fun fromOrNull(context: Context): ClaudeAutoRunApp? {
            return context.applicationContext as? ClaudeAutoRunApp
        }
    }
}
