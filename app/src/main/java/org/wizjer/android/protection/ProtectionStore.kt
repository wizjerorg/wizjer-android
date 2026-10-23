package org.wizjer.android.protection

import android.content.Context
import android.content.SharedPreferences
import androidx.core.content.edit
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.update
import java.time.LocalDate

/** Counts of what background protection has checked. Never contains message content. */
data class ProtectionStats(
    val checkedToday: Int = 0,
    val checkedTotal: Int = 0,
    val smsChecked: Int = 0,
    val notificationsChecked: Int = 0,
    val threatsFound: Int = 0,
    /** Epoch millis of the last checked message, or null if nothing has been checked yet. */
    val lastCheckedAt: Long? = null,
)

data class ProtectionState(
    /** The user's main switch. */
    val enabled: Boolean = true,
    val smsEnabled: Boolean = true,
    val notificationsEnabled: Boolean = true,
    /** Whether the system has connected our notification listener in this process. */
    val listenerConnected: Boolean = false,
    val stats: ProtectionStats = ProtectionStats(),
)

/**
 * Background protection's switches and statistics, shared by the UI and the background
 * receivers (same process). Observe [state]; writes are thread-safe.
 */
class ProtectionStore private constructor(context: Context) {
    private val prefs: SharedPreferences = context.getSharedPreferences("protection", Context.MODE_PRIVATE)
    private val _state = MutableStateFlow(load())
    val state: StateFlow<ProtectionState> = _state.asStateFlow()

    fun setEnabled(enabled: Boolean) = setFlag(KEY_ENABLED, enabled) { copy(enabled = enabled) }

    fun setSmsEnabled(enabled: Boolean) = setFlag(KEY_SMS, enabled) { copy(smsEnabled = enabled) }

    fun setNotificationsEnabled(enabled: Boolean) = setFlag(KEY_NOTIFICATIONS, enabled) { copy(notificationsEnabled = enabled) }

    fun setListenerConnected(connected: Boolean) = _state.update { it.copy(listenerConnected = connected) }

    /** Should a message from [source] be checked right now? */
    fun isActive(source: MessageSource): Boolean = with(_state.value) {
        enabled && when (source) {
            MessageSource.Sms -> smsEnabled
            MessageSource.Notification -> notificationsEnabled
        }
    }

    /** Counts one checked message, and one threat found if [threat]. */
    @Synchronized
    fun recordChecked(source: MessageSource, at: Long, threat: Boolean = false) {
        val today = LocalDate.now().toEpochDay()
        val sameDay = prefs.getLong(KEY_DAY, -1) == today
        prefs.edit {
            putLong(KEY_DAY, today)
            putInt(KEY_TODAY, if (sameDay) prefs.getInt(KEY_TODAY, 0) + 1 else 1)
            putInt(KEY_TOTAL, prefs.getInt(KEY_TOTAL, 0) + 1)
            when (source) {
                MessageSource.Sms -> putInt(KEY_SMS_COUNT, prefs.getInt(KEY_SMS_COUNT, 0) + 1)
                MessageSource.Notification -> putInt(KEY_NOTIFICATION_COUNT, prefs.getInt(KEY_NOTIFICATION_COUNT, 0) + 1)
            }
            if (threat) putInt(KEY_THREATS, prefs.getInt(KEY_THREATS, 0) + 1)
            putLong(KEY_LAST, at)
        }
        _state.update { it.copy(stats = loadStats()) }
    }

    private fun setFlag(key: String, value: Boolean, change: ProtectionState.() -> ProtectionState) {
        prefs.edit { putBoolean(key, value) }
        _state.update(change)
    }

    private fun load() = ProtectionState(
        enabled = prefs.getBoolean(KEY_ENABLED, true),
        smsEnabled = prefs.getBoolean(KEY_SMS, true),
        notificationsEnabled = prefs.getBoolean(KEY_NOTIFICATIONS, true),
        stats = loadStats(),
    )

    private fun loadStats(): ProtectionStats {
        val today = LocalDate.now().toEpochDay()
        return ProtectionStats(
            checkedToday = if (prefs.getLong(KEY_DAY, -1) == today) prefs.getInt(KEY_TODAY, 0) else 0,
            checkedTotal = prefs.getInt(KEY_TOTAL, 0),
            smsChecked = prefs.getInt(KEY_SMS_COUNT, 0),
            notificationsChecked = prefs.getInt(KEY_NOTIFICATION_COUNT, 0),
            threatsFound = prefs.getInt(KEY_THREATS, 0),
            lastCheckedAt = prefs.getLong(KEY_LAST, 0).takeIf { it > 0 },
        )
    }

    /** Re-reads the statistics (e.g. so "today" resets after midnight while the screen is open). */
    fun refreshStats() = _state.update { it.copy(stats = loadStats()) }

    companion object {
        private const val KEY_ENABLED = "enabled"
        private const val KEY_SMS = "sms_enabled"
        private const val KEY_NOTIFICATIONS = "notifications_enabled"
        private const val KEY_DAY = "stats_day"
        private const val KEY_TODAY = "stats_today"
        private const val KEY_TOTAL = "stats_total"
        private const val KEY_SMS_COUNT = "stats_sms"
        private const val KEY_NOTIFICATION_COUNT = "stats_notifications"
        private const val KEY_THREATS = "stats_threats"
        private const val KEY_LAST = "stats_last"

        @Volatile
        private var instance: ProtectionStore? = null

        fun get(context: Context): ProtectionStore =
            instance ?: synchronized(this) {
                instance ?: ProtectionStore(context.applicationContext).also { instance = it }
            }
    }
}
