package com.zfl.launcher

import android.app.role.RoleManager
import android.content.Context
import android.content.Intent
import android.graphics.Color
import android.os.Build
import android.os.Bundle
import android.provider.Settings
import androidx.activity.ComponentActivity
import androidx.activity.SystemBarStyle
import androidx.activity.compose.setContent
import androidx.activity.enableEdgeToEdge
import androidx.activity.result.contract.ActivityResultContracts
import androidx.activity.viewModels
import androidx.compose.animation.Crossfade
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.setValue
import androidx.core.net.toUri
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.zfl.launcher.ui.glass.GlassStyle
import com.zfl.launcher.ui.glass.LocalGlassSource
import com.zfl.launcher.ui.glass.LocalGlassStyle
import com.zfl.launcher.ui.glass.WallpaperBlur
import com.zfl.launcher.settings.AppLanguage
import com.zfl.launcher.settings.LocalSettingsState
import com.zfl.launcher.ui.icon.IconLoader
import com.zfl.launcher.ui.icon.LocalIconLoader
import com.zfl.launcher.ui.onboarding.OnboardingFlow
import com.zfl.launcher.ui.onboarding.OnboardingMode
import com.zfl.launcher.ui.settings.SettingsActions
import com.zfl.launcher.ui.settings.SettingsApp
import com.zfl.launcher.ui.settings.SettingsRoute
import com.zfl.launcher.ui.settings.SettingsViewModel
import com.zfl.launcher.ui.theme.Tokens
import kotlin.math.roundToInt

/**
 * Launcher settings. This is the activity behind the "ZFL Launcher" icon in the app grid,
 * the same way iOS27L exposes its settings as a regular app.
 */
class SettingsActivity : ComponentActivity(), SettingsActions {

    private val vm: SettingsViewModel by viewModels()
    private val roleManager: RoleManager by lazy { getSystemService(RoleManager::class.java) }

    override var isDefaultHome: Boolean by mutableStateOf(false)
        private set
    override var hasWallpaperAccess: Boolean by mutableStateOf(false)
        private set
    override var isHomeRoleAvailable: Boolean by mutableStateOf(true)
        private set
    override val versionName: String by lazy {
        packageManager.getPackageInfo(packageName, 0).versionName.orEmpty()
    }

    private val requestHomeRole = registerForActivityResult(ActivityResultContracts.StartActivityForResult()) {
        refreshSystemState()
        // Some OEM builds (MIUI included) ignore the role request: fall back to the system picker.
        if (!isDefaultHome) runCatching { startActivity(Intent(Settings.ACTION_HOME_SETTINGS)) }
    }

    override fun attachBaseContext(newBase: Context) {
        super.attachBaseContext(AppLanguage.wrap(newBase))
    }

    override fun onCreate(savedInstanceState: Bundle?) {
        enableEdgeToEdge(
            statusBarStyle = SystemBarStyle.dark(Color.TRANSPARENT),
            navigationBarStyle = SystemBarStyle.dark(Color.TRANSPARENT),
        )
        super.onCreate(savedInstanceState)

        val iconSizePx = (Tokens.IconSize.value * resources.displayMetrics.density).roundToInt()
        val iconLoader = IconLoader(this, iconSizePx, vm.settings)
        val initialRoute = SettingsRoute.Weather.takeIf { intent.getStringExtra(EXTRA_ROUTE) == ROUTE_WEATHER }

        setContent {
            val settings by vm.settings.state.collectAsStateWithLifecycle()
            val glass by vm.glass.collectAsStateWithLifecycle()
            CompositionLocalProvider(
                LocalSettingsState provides settings,
                LocalGlassSource provides glass,
                LocalIconLoader provides iconLoader,
                LocalGlassStyle provides GlassStyle(settings.glassTint, settings.glassEdge),
            ) {
                // First launch: the setup, unless opened on a given screen (e.g. from a widget).
                Crossfade(settings.onboardingDone || initialRoute != null, label = "onboarding") { setUp ->
                    if (setUp) {
                        SettingsApp(vm, actions = this, initialRoute = initialRoute)
                    } else {
                        OnboardingFlow(vm, actions = this, OnboardingMode.Full, onFinish = ::completeOnboarding)
                    }
                }
            }
        }
    }

    override fun onResume() {
        super.onResume()
        val hadAccess = hasWallpaperAccess
        refreshSystemState()
        if (!hadAccess && hasWallpaperAccess) vm.refreshGlass()
    }

    private fun refreshSystemState() {
        isDefaultHome = roleManager.isRoleHeld(RoleManager.ROLE_HOME)
        isHomeRoleAvailable = roleManager.isRoleAvailable(RoleManager.ROLE_HOME)
        hasWallpaperAccess = WallpaperBlur.hasAccess()
    }

    override fun requestDefaultHome() {
        if (isDefaultHome || !roleManager.isRoleAvailable(RoleManager.ROLE_HOME)) {
            startActivity(Intent(Settings.ACTION_HOME_SETTINGS))
        } else {
            requestHomeRole.launch(roleManager.createRequestRoleIntent(RoleManager.ROLE_HOME))
        }
    }

    override fun openLanguageSettings() {
        val packageUri = "package:$packageName".toUri()
        val intent = if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU) {
            // Android 13+ has a per-app language screen; our languages come from locales_config.xml.
            Intent(Settings.ACTION_APP_LOCALE_SETTINGS, packageUri)
        } else {
            Intent(Settings.ACTION_APPLICATION_DETAILS_SETTINGS, packageUri)
        }
        runCatching { startActivity(intent) }
    }

    override fun requestWallpaperAccess() {
        val intent = Intent(Settings.ACTION_MANAGE_APP_ALL_FILES_ACCESS_PERMISSION, "package:$packageName".toUri())
        runCatching { startActivity(intent) }
            .onFailure { startActivity(Intent(Settings.ACTION_MANAGE_ALL_FILES_ACCESS_PERMISSION)) }
    }

    override fun setAppLanguage(tag: String?) {
        AppLanguage.set(this, tag)
    }

    /**
     * Saves that the setup is done and shows the home screen when ZFL is the home app. Otherwise
     * Home would open another launcher, so the settings stay open (with "Finish Setting Up").
     */
    override fun completeOnboarding() {
        vm.settings.completeOnboarding()
        if (isDefaultHome) {
            startActivity(Intent(Intent.ACTION_MAIN).addCategory(Intent.CATEGORY_HOME).addFlags(Intent.FLAG_ACTIVITY_NEW_TASK))
        }
    }

    companion object {
        /** Opens settings on a given screen, e.g. from a widget. */
        const val EXTRA_ROUTE: String = "com.zfl.launcher.extra.ROUTE"
        const val ROUTE_WEATHER: String = "weather"
    }
}
