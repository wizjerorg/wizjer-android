package org.wizjer.android.ui.permissions

import android.content.Context
import androidx.activity.compose.LocalActivity
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.runtime.Composable
import androidx.compose.runtime.Stable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.platform.LocalContext
import androidx.core.content.edit
import androidx.lifecycle.Lifecycle
import androidx.lifecycle.compose.LifecycleEventEffect
import org.wizjer.android.protection.AppPermission
import org.wizjer.android.protection.openSettingsFor

/** Live grant status of every [AppPermission], refreshed whenever the screen resumes. */
@Stable
class PermissionsState internal constructor(private val context: Context) {
    private val prefs = context.getSharedPreferences("permissions", Context.MODE_PRIVATE)

    var granted by mutableStateOf(check())
        private set

    val allGranted: Boolean get() = granted.values.all { it }

    internal var onRequest: (AppPermission) -> Unit = {}

    /** Asks for [permission]: the system dialog when possible, otherwise its settings page. */
    fun request(permission: AppPermission) = onRequest(permission)

    internal fun refresh() {
        granted = check()
        granted.filterValues { it }.keys.forEach { setBlocked(it, false) }
    }

    /**
     * After the user denies twice, Android stops showing the dialog; we remember that
     * and send them to Settings instead of a button that silently does nothing.
     */
    internal fun isBlocked(permission: AppPermission) = prefs.getBoolean(permission.name, false)

    internal fun setBlocked(permission: AppPermission, blocked: Boolean) =
        prefs.edit { putBoolean(permission.name, blocked) }

    private fun check() = AppPermission.entries.associateWith { it.isGranted(context) }
}

@Composable
fun rememberPermissionsState(): PermissionsState {
    val context = LocalContext.current
    val activity = LocalActivity.current
    val state = remember { PermissionsState(context.applicationContext) }
    var pending by rememberSaveable { mutableStateOf<String?>(null) }

    val launcher = rememberLauncherForActivityResult(
        ActivityResultContracts.RequestMultiplePermissions()
    ) { results ->
        val permission = pending?.let(AppPermission::valueOf) ?: return@rememberLauncherForActivityResult
        pending = null
        state.refresh()
        val denied = results.filterValues { !it }.keys
        if (denied.isNotEmpty() && activity != null &&
            denied.none { activity.shouldShowRequestPermissionRationale(it) }
        ) {
            state.setBlocked(permission, true)
        }
    }

    LifecycleEventEffect(Lifecycle.Event.ON_RESUME) { state.refresh() }

    state.onRequest = { permission ->
        val runtime = permission.runtimePermissions
        if (runtime.isEmpty() || state.isBlocked(permission)) {
            context.openSettingsFor(permission)
        } else {
            pending = permission.name
            launcher.launch(runtime)
        }
    }
    return state
}
