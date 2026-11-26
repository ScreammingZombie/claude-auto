package com.example.data

import android.content.Context
import androidx.datastore.core.DataStore
import androidx.datastore.preferences.core.Preferences
import androidx.datastore.preferences.core.booleanPreferencesKey
import androidx.datastore.preferences.core.edit
import androidx.datastore.preferences.core.intPreferencesKey
import androidx.datastore.preferences.core.longPreferencesKey
import androidx.datastore.preferences.core.stringPreferencesKey
import androidx.datastore.preferences.core.stringSetPreferencesKey
import androidx.datastore.preferences.preferencesDataStore
import com.example.model.ActivityLogEntry
import com.example.model.AutoRunConfig
import com.example.model.AutomationStatus
import com.example.model.DefaultAutomationText
import com.example.model.ExecutionHistoryItem
import com.example.model.ScheduleDays
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.flow.map
import org.json.JSONArray
import org.json.JSONObject

private val Context.autoRunDataStore: DataStore<Preferences> by preferencesDataStore(
    name = "claude_autorun_preferences",
)

class AutoRunRepository(
    private val dataStore: DataStore<Preferences>,
) {
    constructor(context: Context) : this(context.applicationContext.autoRunDataStore)

    private object Keys {
        val HOUR = intPreferencesKey("hour")
        val MINUTE = intPreferencesKey("minute")
        val SELECTED_DAYS = stringSetPreferencesKey("selected_days")
        val CONVERSATION_TITLE = stringPreferencesKey("conversation_title")
        val RESUME_PROMPT = stringPreferencesKey("resume_prompt")
        val MAX_HOURS = intPreferencesKey("max_duration_hours")
        val MAX_MINUTES = intPreferencesKey("max_duration_minutes")
        val TURN_OFF_WIFI = booleanPreferencesKey("turn_off_wifi_on_finish")
        val OPEN_BROWSER = booleanPreferencesKey("open_default_browser")
        val SCHEDULE_ACTIVE = booleanPreferencesKey("is_schedule_active")
        val NEXT_EXECUTION = longPreferencesKey("next_execution_epoch_millis")
        val LIMIT_PHRASES_JSON = stringPreferencesKey("limit_phrases_json")
        val LOGS_JSON = stringPreferencesKey("activity_logs_json")
        val HISTORY_JSON = stringPreferencesKey("execution_history_json")
        val LAST_TRIGGERED_MINUTE_KEY = stringPreferencesKey("last_triggered_minute_key")
    }

    val configFlow: Flow<AutoRunConfig> = dataStore.data.map { prefs ->
        parseConfig(prefs)
    }

    val logsFlow: Flow<List<ActivityLogEntry>> = dataStore.data.map { prefs ->
        parseLogs(prefs[Keys.LOGS_JSON])
    }

    val historyFlow: Flow<List<ExecutionHistoryItem>> = dataStore.data.map { prefs ->
        parseHistory(prefs[Keys.HISTORY_JSON])
    }

    suspend fun getConfig(): AutoRunConfig {
        return configFlow.first()
    }

    suspend fun saveConfig(config: AutoRunConfig) {
        dataStore.edit { prefs ->
            prefs[Keys.HOUR] = config.hour.coerceIn(0, 23)
            prefs[Keys.MINUTE] = config.minute.coerceIn(0, 59)
            prefs[Keys.SELECTED_DAYS] = config.selectedDays.map { it.toString() }.toSet()
            prefs[Keys.CONVERSATION_TITLE] = config.conversationTitle
            prefs[Keys.RESUME_PROMPT] = config.resumePrompt.ifBlank {
                DefaultAutomationText.DEFAULT_RESUME_PROMPT
            }
            prefs[Keys.MAX_HOURS] = config.maxDurationHours.coerceIn(0, 12)
            prefs[Keys.MAX_MINUTES] = config.maxDurationMinutes.coerceIn(0, 59)
            prefs[Keys.TURN_OFF_WIFI] = config.turnOffWifiOnFinish
            prefs[Keys.OPEN_BROWSER] = config.openDefaultBrowser
            prefs[Keys.SCHEDULE_ACTIVE] = config.isScheduleActive
            if (config.nextExecutionEpochMillis != null) {
                prefs[Keys.NEXT_EXECUTION] = config.nextExecutionEpochMillis
            } else {
                prefs.remove(Keys.NEXT_EXECUTION)
            }
            prefs[Keys.LIMIT_PHRASES_JSON] = serializeStringList(config.limitPhrases)
        }
    }

    suspend fun updateScheduleState(isActive: Boolean, nextExecutionMillis: Long?) {
        dataStore.edit { prefs ->
            prefs[Keys.SCHEDULE_ACTIVE] = isActive
            if (nextExecutionMillis != null) {
                prefs[Keys.NEXT_EXECUTION] = nextExecutionMillis
            } else {
                prefs.remove(Keys.NEXT_EXECUTION)
            }
        }
    }

    suspend fun appendLog(entry: ActivityLogEntry) {
        dataStore.edit { prefs ->
            val current = parseLogs(prefs[Keys.LOGS_JSON]).toMutableList()
            current.add(0, entry)
            val trimmed = current.take(MAX_LOG_ENTRIES)
            prefs[Keys.LOGS_JSON] = serializeLogs(trimmed)
        }
    }

    suspend fun clearLogs() {
        dataStore.edit { prefs ->
            prefs.remove(Keys.LOGS_JSON)
        }
    }

    suspend fun upsertHistoryItem(item: ExecutionHistoryItem) {
        dataStore.edit { prefs ->
            val current = parseHistory(prefs[Keys.HISTORY_JSON]).toMutableList()
            val index = current.indexOfFirst { it.id == item.id }
            if (index >= 0) {
                current[index] = item
            } else {
                current.add(0, item)
            }
            prefs[Keys.HISTORY_JSON] = serializeHistory(current.take(MAX_HISTORY_ENTRIES))
        }
    }

    suspend fun clearHistory() {
        dataStore.edit { prefs ->
            prefs.remove(Keys.HISTORY_JSON)
        }
    }

    suspend fun tryClaimAlarmMinuteKey(minuteKey: String): Boolean {
        var claimed = false
        dataStore.edit { prefs ->
            val previous = prefs[Keys.LAST_TRIGGERED_MINUTE_KEY]
            if (previous != minuteKey) {
                prefs[Keys.LAST_TRIGGERED_MINUTE_KEY] = minuteKey
                claimed = true
            }
        }
        return claimed
    }

    private fun parseConfig(prefs: Preferences): AutoRunConfig {
        val rawDays = prefs[Keys.SELECTED_DAYS]
        val parsedDays = rawDays?.mapNotNull { it.toIntOrNull() }?.toSet()
            ?: ScheduleDays.ALL_CALENDAR_DAYS
        val phrasesJson = prefs[Keys.LIMIT_PHRASES_JSON]
        val parsedPhrases = if (phrasesJson.isNullOrBlank()) {
            DefaultAutomationText.DEFAULT_LIMIT_PHRASES
        } else {
            parseStringList(phrasesJson).ifEmpty { DefaultAutomationText.DEFAULT_LIMIT_PHRASES }
        }
        return AutoRunConfig(
            hour = prefs[Keys.HOUR] ?: 8,
            minute = prefs[Keys.MINUTE] ?: 0,
            selectedDays = parsedDays,
            conversationTitle = prefs[Keys.CONVERSATION_TITLE] ?: "",
            resumePrompt = prefs[Keys.RESUME_PROMPT] ?: DefaultAutomationText.DEFAULT_RESUME_PROMPT,
            maxDurationHours = prefs[Keys.MAX_HOURS] ?: 1,
            maxDurationMinutes = prefs[Keys.MAX_MINUTES] ?: 30,
            turnOffWifiOnFinish = prefs[Keys.TURN_OFF_WIFI] ?: true,
            openDefaultBrowser = prefs[Keys.OPEN_BROWSER] ?: true,
            isScheduleActive = prefs[Keys.SCHEDULE_ACTIVE] ?: false,
            nextExecutionEpochMillis = prefs[Keys.NEXT_EXECUTION],
            limitPhrases = parsedPhrases,
        )
    }

    private fun serializeStringList(items: List<String>): String {
        val array = JSONArray()
        items.forEach { array.put(it) }
        return array.toString()
    }

    private fun parseStringList(json: String): List<String> {
        return runCatching {
            val array = JSONArray(json)
            buildList {
                for (i in 0 until array.length()) {
                    val value = array.optString(i).trim()
                    if (value.isNotEmpty()) add(value)
                }
            }
        }.getOrDefault(emptyList())
    }

    private fun serializeLogs(entries: List<ActivityLogEntry>): String {
        val array = JSONArray()
        entries.forEach { entry ->
            array.put(
                JSONObject().apply {
                    put("id", entry.id)
                    put("timestampMillis", entry.timestampMillis)
                    put("status", entry.status.name)
                    put("message", entry.message)
                    put("isError", entry.isError)
                },
            )
        }
        return array.toString()
    }

    private fun parseLogs(json: String?): List<ActivityLogEntry> {
        if (json.isNullOrBlank()) return emptyList()
        return runCatching {
            val array = JSONArray(json)
            buildList {
                for (i in 0 until array.length()) {
                    val obj = array.getJSONObject(i)
                    val statusName = obj.optString("status", AutomationStatus.INACTIVA.name)
                    val status = AutomationStatus.entries.firstOrNull { it.name == statusName }
                        ?: AutomationStatus.INACTIVA
                    add(
                        ActivityLogEntry(
                            id = obj.optString("id"),
                            timestampMillis = obj.optLong("timestampMillis"),
                            status = status,
                            message = obj.optString("message"),
                            isError = obj.optBoolean("isError", false),
                        ),
                    )
                }
            }
        }.getOrDefault(emptyList())
    }

    private fun serializeHistory(items: List<ExecutionHistoryItem>): String {
        val array = JSONArray()
        items.forEach { item ->
            array.put(
                JSONObject().apply {
                    put("id", item.id)
                    put("startTimeMillis", item.startTimeMillis)
                    if (item.endTimeMillis != null) put("endTimeMillis", item.endTimeMillis)
                    put("initialWifiState", item.initialWifiState)
                    put("connectionResult", item.connectionResult)
                    put("browserOpened", item.browserOpened)
                    put("conversationFound", item.conversationFound)
                    put("instructionSent", item.instructionSent)
                    put("limitMessageDetected", item.limitMessageDetected)
                    put("finishReason", item.finishReason)
                    put("finalWifiState", item.finalWifiState)
                    put("errorsFound", JSONArray(item.errorsFound))
                },
            )
        }
        return array.toString()
    }

    private fun parseHistory(json: String?): List<ExecutionHistoryItem> {
        if (json.isNullOrBlank()) return emptyList()
        return runCatching {
            val array = JSONArray(json)
            buildList {
                for (i in 0 until array.length()) {
                    val obj = array.getJSONObject(i)
                    val errorsArr = obj.optJSONArray("errorsFound") ?: JSONArray()
                    val errors = buildList {
                        for (j in 0 until errorsArr.length()) {
                            val err = errorsArr.optString(j)
                            if (err.isNotBlank()) add(err)
                        }
                    }
                    add(
                        ExecutionHistoryItem(
                            id = obj.optString("id"),
                            startTimeMillis = obj.optLong("startTimeMillis"),
                            endTimeMillis = if (obj.has("endTimeMillis")) obj.optLong("endTimeMillis") else null,
                            initialWifiState = obj.optString("initialWifiState", "Desconocido"),
                            connectionResult = obj.optString("connectionResult", "Pendiente"),
                            browserOpened = obj.optString("browserOpened", "No iniciado"),
                            conversationFound = obj.optString("conversationFound", "No buscada"),
                            instructionSent = obj.optString("instructionSent", "No enviada"),
                            limitMessageDetected = obj.optString("limitMessageDetected", "Ninguno"),
                            finishReason = obj.optString("finishReason", "En curso"),
                            finalWifiState = obj.optString("finalWifiState", "Sin cambios"),
                            errorsFound = errors,
                        ),
                    )
                }
            }
        }.getOrDefault(emptyList())
    }

    companion object {
        private const val MAX_LOG_ENTRIES = 200
        private const val MAX_HISTORY_ENTRIES = 60
    }
}
