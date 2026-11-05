package io.github.xxtvrxx233.wearcompass.presentation

import android.content.pm.ApplicationInfo
import android.os.Bundle
import android.view.WindowManager
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.core.splashscreen.SplashScreen.Companion.installSplashScreen
import io.github.xxtvrxx233.wearcompass.presentation.theme.CompassTheme
import io.github.xxtvrxx233.wearcompass.presentation.theme.ThemePreferences

class MainActivity : ComponentActivity() {

    private lateinit var controller: CompassController
    private lateinit var themePreferences: ThemePreferences

    override fun onCreate(savedInstanceState: Bundle?) {

        installSplashScreen()
        super.onCreate(savedInstanceState)

        window.addFlags(WindowManager.LayoutParams.FLAG_KEEP_SCREEN_ON)
        themePreferences = ThemePreferences(applicationContext)
        controller = CompassController(
            context = applicationContext,
            demoHeading = demoHeadingFromIntent(),
            demoSpin = isDebuggable() && intent.getBooleanExtra(EXTRA_DEMO_SPIN, false),
        )
        setContent {
            CompassTheme(themePreferences) {
                CompassApp(controller, themePreferences)
            }
        }
    }

    override fun onResume() {
        super.onResume()
        controller.start()
    }

    override fun onPause() {
        controller.stop()
        super.onPause()
    }

    private fun demoHeadingFromIntent(): Float? {
        if (!isDebuggable()) return null
        val value = intent.getFloatExtra(EXTRA_DEMO_HEADING, -1f)
        return value.takeIf { it >= 0f }
    }

    private fun isDebuggable(): Boolean =
        (applicationInfo.flags and ApplicationInfo.FLAG_DEBUGGABLE) != 0

    private companion object {
        const val EXTRA_DEMO_HEADING = "demo_heading"
        const val EXTRA_DEMO_SPIN = "demo_spin"
    }
}
