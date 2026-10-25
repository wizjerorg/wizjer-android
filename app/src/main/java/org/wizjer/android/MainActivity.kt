package org.wizjer.android

import android.content.Intent
import android.graphics.Color
import android.os.Build
import android.os.Bundle
import androidx.activity.SystemBarStyle
import androidx.activity.compose.setContent
import androidx.activity.enableEdgeToEdge
import androidx.appcompat.app.AppCompatActivity
import androidx.appcompat.app.AppCompatDelegate
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableFloatStateOf
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.core.splashscreen.SplashScreen.Companion.installSplashScreen
import org.wizjer.android.protection.AlertLog
import org.wizjer.android.protection.ScamAlerts
import org.wizjer.android.ui.WizjerApp
import org.wizjer.android.ui.theme.WizjerTheme

// AppCompatActivity (not ComponentActivity) so per-app language switching works below Android 13.
class MainActivity : AppCompatActivity() {
    override fun onCreate(savedInstanceState: Bundle?) {
        // Light only, whatever the phone's setting (a dark-mode toggle may come later).
        AppCompatDelegate.setDefaultNightMode(AppCompatDelegate.MODE_NIGHT_NO)
        installSplashScreen()
        super.onCreate(savedInstanceState)
        // Transparent bars with dark icons, since every screen is light.
        enableEdgeToEdge(
            statusBarStyle = SystemBarStyle.light(Color.TRANSPARENT, Color.TRANSPARENT),
            navigationBarStyle = SystemBarStyle.light(Color.TRANSPARENT, Color.TRANSPARENT),
        )
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.Q) {
            window.isNavigationBarContrastEnforced = false
        }
        AlertLog.restore(this)
        if (savedInstanceState == null) openAlertFrom(intent)
        setContent {
            // In-app text size (Settings). Not persisted yet.
            var textScale by rememberSaveable { mutableFloatStateOf(1f) }
            WizjerTheme(textScale = textScale) {
                WizjerApp(textScale = textScale, onTextScaleChange = { textScale = it })
            }
        }
    }

    override fun onNewIntent(intent: Intent) {
        super.onNewIntent(intent)
        openAlertFrom(intent)
    }

    /** A tapped scam alert opens its message. */
    private fun openAlertFrom(intent: Intent?) {
        intent?.getIntExtra(ScamAlerts.EXTRA_ALERT_ID, 0)?.takeIf { it != 0 }?.let(AlertLog::open)
    }
}
