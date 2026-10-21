package org.wizjer.android.protection

import android.Manifest
import android.content.ActivityNotFoundException
import android.content.ComponentName
import android.content.Context
import android.content.Intent
import android.content.pm.PackageManager
import android.net.Uri
import android.os.Build
import android.provider.Settings
import androidx.core.app.NotificationManagerCompat
import androidx.core.content.ContextCompat

/** Everything Wizjer needs from the system to protect the user. */
enum class AppPermission {
    /** Posting scam alerts (runtime permission on Android 13+). */
    Alerts,
    /** Reading other apps' notifications (special access, granted in system settings). */
    NotificationAccess,
    /** Reading incoming and stored SMS (runtime permissions). */
    Sms;

    /** Runtime permissions to request with the system dialog; empty when granted elsewhere. */
    val runtimePermissions: Array<String>
        get() = when (this) {
            Alerts ->
                if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU) arrayOf(Manifest.permission.POST_NOTIFICATIONS)
                else emptyArray()
            NotificationAccess -> emptyArray()
            Sms -> arrayOf(Manifest.permission.RECEIVE_SMS, Manifest.permission.READ_SMS)
        }

    fun isGranted(context: Context): Boolean = when (this) {
        Alerts -> NotificationManagerCompat.from(context).areNotificationsEnabled()
        NotificationAccess ->
            NotificationManagerCompat.getEnabledListenerPackages(context).contains(context.packageName)
        Sms -> runtimePermissions.all {
            ContextCompat.checkSelfPermission(context, it) == PackageManager.PERMISSION_GRANTED
        }
    }

    /** The system settings page where this permission can be granted by hand. */
    fun settingsIntent(context: Context): Intent = when (this) {
        Alerts -> Intent(Settings.ACTION_APP_NOTIFICATION_SETTINGS)
            .putExtra(Settings.EXTRA_APP_PACKAGE, context.packageName)
        NotificationAccess ->
            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.R) {
                // Opens Wizjer's own toggle instead of the full list.
                Intent(Settings.ACTION_NOTIFICATION_LISTENER_DETAIL_SETTINGS).putExtra(
                    Settings.EXTRA_NOTIFICATION_LISTENER_COMPONENT_NAME,
                    ComponentName(context, WizjerNotificationListener::class.java).flattenToString(),
                )
            } else {
                Intent(Settings.ACTION_NOTIFICATION_LISTENER_SETTINGS)
            }
        Sms -> appDetailsIntent(context)
    }
}

/** Starts [AppPermission.settingsIntent], falling back to broader pages some OEMs don't support. */
fun Context.openSettingsFor(permission: AppPermission) {
    val fallbacks = listOf(
        permission.settingsIntent(this),
        if (permission == AppPermission.NotificationAccess) Intent(Settings.ACTION_NOTIFICATION_LISTENER_SETTINGS)
        else appDetailsIntent(this),
        appDetailsIntent(this),
    )
    for (intent in fallbacks) {
        try {
            startActivity(intent)
            return
        } catch (_: ActivityNotFoundException) {
            // try the next one
        }
    }
}

private fun appDetailsIntent(context: Context) =
    Intent(Settings.ACTION_APPLICATION_DETAILS_SETTINGS, Uri.fromParts("package", context.packageName, null))
