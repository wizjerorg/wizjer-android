package org.wizjer.android.data.lists

import android.content.Context
import androidx.work.Constraints
import androidx.work.CoroutineWorker
import androidx.work.ExistingPeriodicWorkPolicy
import androidx.work.ExistingWorkPolicy
import androidx.work.NetworkType
import androidx.work.OneTimeWorkRequestBuilder
import androidx.work.PeriodicWorkRequestBuilder
import androidx.work.WorkManager
import androidx.work.WorkerParameters
import androidx.work.workDataOf
import java.util.concurrent.TimeUnit

/** Keeps the threat lists fresh in the background. */
class ThreatListsWorker(context: Context, params: WorkerParameters) : CoroutineWorker(context, params) {

    override suspend fun doWork(): Result {
        val lists = ThreatLists.get(applicationContext)
        // Without a source, everything is updated.
        val sources = inputData.getString(KEY_SOURCE)
            ?.let { name -> ListSource.entries.filter { it.name == name } }
            ?: ListSource.entries
        val onlyIfStale = inputData.getBoolean(KEY_ONLY_IF_STALE, false)

        var failed = false
        for (source in sources) {
            if (onlyIfStale && System.currentTimeMillis() - lists.checkedAt(source) < STALE_AFTER_MILLIS) continue
            if (lists.update(source) == ThreatLists.UpdateResult.Failed) failed = true
        }
        return when {
            !failed -> Result.success()
            runAttemptCount < MAX_ATTEMPTS -> Result.retry()
            else -> Result.failure()
        }
    }

    companion object {
        /** Unique name of a one-off update, e.g. to watch its progress. */
        const val UPDATE_NOW = "threat_lists_now"
        private const val KEY_SOURCE = "source"
        private const val KEY_ONLY_IF_STALE = "only_if_stale"
        private const val MAX_ATTEMPTS = 3
        private val STALE_AFTER_MILLIS = TimeUnit.HOURS.toMillis(24)

        /**
         * Main lists: every 6 hours on Wi-Fi, on mobile data only once they're a day old, and
         * the first download right away on any network. New CERT entries: every 15 minutes, on
         * any network (a few KB). Gambling: weekly, on Wi-Fi only.
         * Safe to call on every launch; it also applies schedule changes from app updates.
         */
        fun schedule(context: Context) {
            periodic(context, "threat_lists_recent", ListSource.RecentCert, 15, TimeUnit.MINUTES, NetworkType.CONNECTED)
            periodic(context, "threat_lists_wifi", ListSource.Main, 6, TimeUnit.HOURS, NetworkType.UNMETERED)
            periodic(context, "threat_lists_any", ListSource.Main, 24, TimeUnit.HOURS, NetworkType.CONNECTED, onlyIfStale = true)
            periodic(context, "threat_lists_gambling", ListSource.Gambling, 7, TimeUnit.DAYS, NetworkType.UNMETERED)
            if (ListSource.Main !in ThreatLists.get(context).loaded.value) {
                updateNow(context, ListSource.Main, ExistingWorkPolicy.KEEP)
            }
        }

        /** Updates [source] (or everything) as soon as there's a connection. */
        fun updateNow(context: Context, source: ListSource? = null, policy: ExistingWorkPolicy = ExistingWorkPolicy.REPLACE) {
            val request = OneTimeWorkRequestBuilder<ThreatListsWorker>()
                .setConstraints(constraints(NetworkType.CONNECTED, batteryNotLow = false))
                .setInputData(workDataOf(KEY_SOURCE to source?.name))
                .build()
            WorkManager.getInstance(context).enqueueUniqueWork(UPDATE_NOW, policy, request)
        }

        private fun periodic(
            context: Context,
            name: String,
            source: ListSource,
            every: Long,
            unit: TimeUnit,
            network: NetworkType,
            onlyIfStale: Boolean = false,
        ) {
            val request = PeriodicWorkRequestBuilder<ThreatListsWorker>(every, unit)
                .setConstraints(constraints(network, batteryNotLow = true))
                .setInputData(workDataOf(KEY_SOURCE to source.name, KEY_ONLY_IF_STALE to onlyIfStale))
                .build()
            WorkManager.getInstance(context).enqueueUniquePeriodicWork(name, ExistingPeriodicWorkPolicy.UPDATE, request)
        }

        private fun constraints(network: NetworkType, batteryNotLow: Boolean) =
            Constraints.Builder().setRequiredNetworkType(network).setRequiresBatteryNotLow(batteryNotLow).build()
    }
}
