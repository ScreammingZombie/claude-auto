package com.example.service

import android.app.NotificationManager
import android.app.Service
import android.content.Context
import android.content.Intent
import android.content.pm.ServiceInfo
import android.os.Build
import android.os.IBinder
import androidx.core.content.ContextCompat
import com.example.ClaudeAutoRunApp
import com.example.model.AutomationStatus
import com.example.util.NotificationHelper
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.Job
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.cancel
import kotlinx.coroutines.flow.collectLatest
import kotlinx.coroutines.launch

class AutoRunForegroundService : Service() {

    private val serviceScope = CoroutineScope(SupervisorJob() + Dispatchers.Main.immediate)
    private var observerJob: Job? = null

    override fun onBind(intent: Intent?): IBinder? = null

    override fun onCreate() {
        super.onCreate()
        NotificationHelper.ensureChannels(this)
    }

    override fun onStartCommand(intent: Intent?, flags: Int, startId: Int): Int {
        val app = ClaudeAutoRunApp.from(this)
        val coordinator = app.coordinator

        when (intent?.action) {
            ACTION_STOP -> {
                coordinator.stopAutomation("Detenida desde la notificación")
                stopSelfSafe()
                return START_NOT_STICKY
            }

            else -> {
                val triggerSource = intent?.getStringExtra(EXTRA_TRIGGER_SOURCE) ?: "Manual"
                val initialNotification = NotificationHelper.buildServiceNotification(
                    context = this,
                    status = AutomationStatus.CONECTANDO,
                    detail = "Preparando automatización ($triggerSource)...",
                )
                startForegroundCompat(initialNotification)

                coordinator.setServiceStopCallback {
                    stopSelfSafe()
                }
                observeRuntimeState()
                coordinator.startAutomation(triggerSource)
                return START_NOT_STICKY
            }
        }
    }

    private fun startForegroundCompat(notification: android.app.Notification) {
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.Q) {
            startForeground(
                NotificationHelper.NOTIFICATION_ID_SERVICE,
                notification,
                ServiceInfo.FOREGROUND_SERVICE_TYPE_DATA_SYNC,
            )
        } else {
            startForeground(NotificationHelper.NOTIFICATION_ID_SERVICE, notification)
        }
    }

    private fun observeRuntimeState() {
        observerJob?.cancel()
        val coordinator = ClaudeAutoRunApp.from(this).coordinator
        val nm = getSystemService(Context.NOTIFICATION_SERVICE) as NotificationManager

        observerJob = serviceScope.launch {
            coordinator.runtimeState.collectLatest { state ->
                if (state.status.isActiveExecution) {
                    val notification = NotificationHelper.buildServiceNotification(
                        context = this@AutoRunForegroundService,
                        status = state.status,
                        detail = state.statusDetail,
                    )
                    nm.notify(NotificationHelper.NOTIFICATION_ID_SERVICE, notification)
                }
            }
        }
    }

    private fun stopSelfSafe() {
        observerJob?.cancel()
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.N) {
            stopForeground(STOP_FOREGROUND_REMOVE)
        } else {
            @Suppress("DEPRECATION")
            stopForeground(true)
        }
        stopSelf()
    }

    override fun onDestroy() {
        ClaudeAutoRunApp.fromOrNull(this)?.coordinator?.setServiceStopCallback(null)
        serviceScope.cancel()
        super.onDestroy()
    }

    companion object {
        const val ACTION_START = "com.example.action.START_AUTORUN_SERVICE"
        const val ACTION_STOP = "com.example.action.STOP_AUTORUN_SERVICE"
        const val EXTRA_TRIGGER_SOURCE = "extra_trigger_source"

        fun start(context: Context, triggerSource: String) {
            val intent = Intent(context, AutoRunForegroundService::class.java).apply {
                action = ACTION_START
                putExtra(EXTRA_TRIGGER_SOURCE, triggerSource)
            }
            ContextCompat.startForegroundService(context, intent)
        }

        fun stop(context: Context) {
            val intent = Intent(context, AutoRunForegroundService::class.java).apply {
                action = ACTION_STOP
            }
            context.startService(intent)
        }
    }
}
