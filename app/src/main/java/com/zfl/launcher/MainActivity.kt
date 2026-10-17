package com.zfl.launcher

import android.Manifest
import android.content.ContentUris
import android.provider.AlarmClock
import android.provider.CalendarContract
import androidx.activity.result.PickVisualMediaRequest
import androidx.activity.result.contract.ActivityResultContracts
import android.app.ActivityOptions
import android.appwidget.AppWidgetManager
import android.appwidget.AppWidgetProviderInfo
import androidx.core.net.toUri
import com.zfl.launcher.data.WidgetTarget
import android.app.SearchManager
import android.app.WallpaperColors
import android.app.WallpaperManager
import android.content.Context
import android.content.Intent
import android.os.Build
import android.content.pm.LauncherApps
import android.content.pm.ShortcutInfo
import android.graphics.Color
import android.net.Uri
import android.os.Bundle
import android.os.Handler
import android.os.Looper
import android.provider.Settings
import androidx.activity.ComponentActivity
import androidx.activity.SystemBarStyle
import androidx.activity.enableEdgeToEdge
import androidx.activity.viewModels
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.ui.geometry.Rect
import androidx.core.view.WindowCompat
import androidx.core.view.WindowInsetsCompat
import androidx.core.view.WindowInsetsControllerCompat
import androidx.lifecycle.Lifecycle
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.lifecycle.lifecycleScope
import androidx.lifecycle.repeatOnLifecycle
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.distinctUntilChanged
import kotlinx.coroutines.launch
import androidx.activity.compose.setContent
import com.zfl.launcher.data.AppInfo
import com.zfl.launcher.settings.AppLanguage
import com.zfl.launcher.settings.LocalSettingsState
import com.zfl.launcher.ui.glass.GlassLight
import com.zfl.launcher.ui.glass.GlassMode
import com.zfl.launcher.ui.glass.GlassTiltSensor
import com.zfl.launcher.ui.glass.LocalGlassLight
import com.zfl.launcher.ui.glass.LocalGlassSource
import com.zfl.launcher.ui.glass.glassStyle
import com.zfl.launcher.ui.glass.isLiquidGlassReady
import com.zfl.launcher.ui.glass.resolveGlassMode
import com.zfl.launcher.ui.glass.LocalGlassStyle
import com.zfl.launcher.ui.home.HomeScreen
import com.zfl.launcher.ui.home.LauncherActions
import com.zfl.launcher.ui.icon.IconLoader
import com.zfl.launcher.ui.icon.LocalIconLoader
import com.zfl.launcher.ui.theme.AccentColors
import com.zfl.launcher.ui.theme.LocalAccent
import com.zfl.launcher.ui.theme.Tokens
import kotlin.math.roundToInt

/**
 * The home screen. Declared with the HOME intent filter, so Android offers it as the
 * default launcher. The window is transparent and shows the system wallpaper behind it.
 */
class MainActivity : ComponentActivity(), LauncherActions {

    private val vm: LauncherViewModel by viewModels()
    private val launcherApps: LauncherApps by lazy { getSystemService(LauncherApps::class.java) }

    /** Photo widget waiting for the picker result; kept across process death. */
    private var pendingPhotoWidget: String? = null

    private val pickPhoto = registerForActivityResult(ActivityResultContracts.PickVisualMedia()) { uri ->
        val widgetId = pendingPhotoWidget
        pendingPhotoWidget = null
        if (uri != null && widgetId != null) vm.setWidgetPhoto(widgetId, uri)
    }

    /** Another app's widget being added; kept across the bind/configure screens and process death. */
    private var pendingWidget: PendingAppWidget? = null

    private val bindWidget = registerForActivityResult(ActivityResultContracts.StartActivityForResult()) { result ->
        val pending = pendingWidget ?: return@registerForActivityResult
        if (result.resultCode == RESULT_OK) configureOrPlace(pending) else cancelWidget(pending)
    }

    private val requestCalendar = registerForActivityResult(ActivityResultContracts.RequestPermission()) {
        vm.refreshWidgets()
    }

    /** Light on Liquid glass, moved by [tiltSensor] while the home screen is started. */
    private val glassLight = GlassLight()
    private val tiltSensor by lazy { GlassTiltSensor(this, glassLight) }

    private val wallpaperListener = WallpaperManager.OnColorsChangedListener { _: WallpaperColors?, _: Int ->
        vm.refreshWallpaper(force = true)
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
        pendingPhotoWidget = savedInstanceState?.getString(KEY_PENDING_PHOTO)
        pendingWidget = savedInstanceState?.let(PendingAppWidget::restore)

        val iconSizePx = (Tokens.IconSize.value * resources.displayMetrics.density).roundToInt()
        val iconLoader = IconLoader(this, iconSizePx, vm.settings)

        setContent {
            val glass by vm.glass.collectAsStateWithLifecycle()
            val settings by vm.settings.state.collectAsStateWithLifecycle()
            val editMode by vm.editMode.collectAsStateWithLifecycle()
            LaunchedEffect(settings.hideNavigation) { applyNavigationBarVisibility(settings.hideNavigation) }
            // Like iOS27L, the status bar makes room for the Edit/Done bar in edit mode.
            LaunchedEffect(editMode) { applyStatusBarVisibility(hidden = editMode) }
            CompositionLocalProvider(
                LocalSettingsState provides settings,
                LocalGlassSource provides glass.takeIf { settings.blurEffect },
                LocalIconLoader provides iconLoader,
                LocalGlassStyle provides settings.glassStyle(),
                LocalGlassLight provides glassLight,
                LocalAccent provides AccentColors.resolve(settings.dynamicColor, iconLoader.wallpaperColor),
            ) {
                HomeScreen(vm, actions = this)
            }
        }
        WallpaperManager.getInstance(this)
            .addOnColorsChangedListener(wallpaperListener, Handler(Looper.getMainLooper()))
        trackTilt()
    }

    /**
     * Runs the tilt sensor only while the home screen is started (stopped when another app
     * covers it or the screen turns off) and Liquid glass with a wallpaper to refract asks for it.
     */
    private fun trackTilt() {
        lifecycleScope.launch {
            repeatOnLifecycle(Lifecycle.State.STARTED) {
                try {
                    combine(vm.settings.state, vm.glass) { settings, source ->
                        val mode = resolveGlassMode(settings.glassMode, Build.VERSION.SDK_INT, isLiquidGlassReady())
                        Triple(mode, settings.glassTiltLight, settings.blurEffect && source != null)
                    }.distinctUntilChanged().collect { (mode, tilt, hasGlass) ->
                        tiltSensor.update(mode, tilt, visible = hasGlass)
                    }
                } finally {
                    tiltSensor.update(GlassMode.Frosted, tiltEnabled = false, visible = false)
                }
            }
        }
    }

    /** True after another activity covered the home screen; used for the resume animation. */
    private var leftHome = false

    override fun onResume() {
        super.onResume()
        // Android 12: the language was changed in settings while the home screen was paused.
        if (AppLanguage.isStale(this)) {
            recreate()
            return
        }
        // Cheap when nothing changed; also picks up a freshly granted file access permission.
        vm.refreshWallpaper()
        vm.refreshWidgets()
        if (leftHome) {
            leftHome = false
            vm.returnedHome.tryEmit(Unit)
        }
    }

    override fun onStart() {
        super.onStart()
        // Widgets of other apps only update while their host listens.
        vm.appWidgets.startListening()
    }

    override fun onStop() {
        super.onStop()
        vm.appWidgets.stopListening()
        leftHome = true
    }

    private fun applyStatusBarVisibility(hidden: Boolean) {
        val controller = WindowCompat.getInsetsController(window, window.decorView)
        if (hidden) controller.hide(WindowInsetsCompat.Type.statusBars()) else controller.show(WindowInsetsCompat.Type.statusBars())
    }

    /** Hides the navigation bar; it can still be revealed temporarily by swiping from the edge. */
    private fun applyNavigationBarVisibility(hidden: Boolean) {
        val controller = WindowCompat.getInsetsController(window, window.decorView)
        if (hidden) {
            controller.systemBarsBehavior = WindowInsetsControllerCompat.BEHAVIOR_SHOW_TRANSIENT_BARS_BY_SWIPE
            controller.hide(WindowInsetsCompat.Type.navigationBars())
        } else {
            controller.show(WindowInsetsCompat.Type.navigationBars())
        }
    }

    override fun onNewIntent(intent: Intent) {
        super.onNewIntent(intent)
        val isHome = intent.action == Intent.ACTION_MAIN && intent.hasCategory(Intent.CATEGORY_HOME)
        // React only when Home is pressed while the home screen is already showing, like iOS:
        // returning from an app keeps the page you left. The activity is paused during
        // onNewIntent, so the lifecycle can't tell us that; window focus can (same as Launcher3).
        val broughtToFront = intent.flags and Intent.FLAG_ACTIVITY_BROUGHT_TO_FRONT != 0
        if (isHome && hasWindowFocus() && !broughtToFront) {
            vm.closeMenu()
            vm.homePressed.tryEmit(Unit)
        }
    }

    override fun onSaveInstanceState(outState: Bundle) {
        super.onSaveInstanceState(outState)
        outState.putString(KEY_PENDING_PHOTO, pendingPhotoWidget)
        pendingWidget?.save(outState)
    }

    override fun onDestroy() {
        WallpaperManager.getInstance(this).removeOnColorsChangedListener(wallpaperListener)
        super.onDestroy()
    }

    // region LauncherActions

    override fun webSearch(query: String) {
        val intent = Intent(Intent.ACTION_WEB_SEARCH).putExtra(SearchManager.QUERY, query)
        runCatching { startActivity(intent) }
            .onFailure { runCatching { startActivity(Intent(Intent.ACTION_VIEW, ("https://duckduckgo.com/?q=" + Uri.encode(query)).toUri())) } }
    }

    override fun launch(app: AppInfo, bounds: Rect) {
        vm.launchStats.recordLaunch(app.key)
        val source = bounds.toAndroidRect()
        val options = if (vm.settings.state.value.openCloseAnimation) {
            // The app grows out of its icon.
            ActivityOptions.makeScaleUpAnimation(
                window.decorView, source.left, source.top, source.width(), source.height(),
            ).toBundle()
        } else {
            ActivityOptions.makeCustomAnimation(this, 0, 0).toBundle()
        }
        runCatching { launcherApps.startMainActivity(app.component, app.user, source, options) }
    }

    override fun launchShortcut(shortcut: ShortcutInfo, bounds: Rect) {
        runCatching { launcherApps.startShortcut(shortcut, bounds.toAndroidRect(), null) }
    }

    override fun openAppInfo(app: AppInfo) {
        launcherApps.startAppDetailsActivity(app.component, app.user, null, null)
    }

    override fun uninstall(app: AppInfo) {
        startActivity(Intent(Intent.ACTION_DELETE, "package:${app.packageName}".toUri()))
    }

    override fun requestGlassAccess() {
        val intent = Intent(Settings.ACTION_MANAGE_APP_ALL_FILES_ACCESS_PERMISSION, "package:$packageName".toUri())
        runCatching { startActivity(intent) }
            .onFailure { startActivity(Intent(Settings.ACTION_MANAGE_ALL_FILES_ACCESS_PERMISSION)) }
    }

    override fun openSettings() {
        startActivity(Intent(this, SettingsActivity::class.java))
    }

    override fun openWallpaperPicker() {
        runCatching { startActivity(Intent.createChooser(Intent(Intent.ACTION_SET_WALLPAPER), null)) }
    }

    override fun openClock() {
        // Opening ACTION_SHOW_ALARMS directly needs the SET_ALARM permission on some ROMs (MIUI),
        // so find the clock app that handles it and start it like from its icon.
        val clockPackage = packageManager.resolveActivity(Intent(AlarmClock.ACTION_SHOW_ALARMS), 0)?.activityInfo?.packageName
        val launch = clockPackage?.let { packageManager.getLaunchIntentForPackage(it) } ?: return
        runCatching { startActivity(launch) }
    }

    override fun openCalendar() {
        // The system calendar's "today" view, whichever calendar app handles it.
        val today = CalendarContract.CONTENT_URI.buildUpon().appendPath("time")
            .also { ContentUris.appendId(it, System.currentTimeMillis()) }.build()
        runCatching { startActivity(Intent(Intent.ACTION_VIEW, today)) }
    }

    override fun requestCalendarPermission() {
        requestCalendar.launch(Manifest.permission.READ_CALENDAR)
    }

    override fun openBatterySettings() {
        runCatching { startActivity(Intent(Intent.ACTION_POWER_USAGE_SUMMARY)) }
            .onFailure { runCatching { startActivity(Intent(Settings.ACTION_BATTERY_SAVER_SETTINGS)) } }
    }

    override fun openWeatherSettings() {
        startActivity(Intent(this, SettingsActivity::class.java).putExtra(SettingsActivity.EXTRA_ROUTE, SettingsActivity.ROUTE_WEATHER))
    }

    override fun pickWidgetPhoto(widgetId: String) {
        pendingPhotoWidget = widgetId
        pickPhoto.launch(PickVisualMediaRequest(ActivityResultContracts.PickVisualMedia.ImageOnly))
    }

    override fun addAppWidget(info: AppWidgetProviderInfo, target: WidgetTarget) {
        val widgets = vm.appWidgets
        val pending = PendingAppWidget(widgets.allocateId(), info.provider.flattenToString(), target)
        pendingWidget = pending
        if (widgets.manager.bindAppWidgetIdIfAllowed(pending.appWidgetId, info.profile, info.provider, null)) {
            configureOrPlace(pending)
        } else {
            // Android asks the user once whether this launcher may create widgets.
            bindWidget.launch(
                Intent(AppWidgetManager.ACTION_APPWIDGET_BIND)
                    .putExtra(AppWidgetManager.EXTRA_APPWIDGET_ID, pending.appWidgetId)
                    .putExtra(AppWidgetManager.EXTRA_APPWIDGET_PROVIDER, info.provider)
                    .putExtra(AppWidgetManager.EXTRA_APPWIDGET_PROVIDER_PROFILE, info.profile),
            )
        }
    }

    /** Opens the widget's own setup screen when it has a required one, then places it. */
    private fun configureOrPlace(pending: PendingAppWidget) {
        val info = vm.appWidgets.info(pending.appWidgetId) ?: return cancelWidget(pending)
        val optional = info.widgetFeatures and AppWidgetProviderInfo.WIDGET_FEATURE_CONFIGURATION_OPTIONAL != 0
        if (info.configure != null && !optional) {
            // The system starts the (possibly non-exported) configure activity for us, through a
            // PendingIntent; Android 14 blocks that start unless we, the visible sender, allow it.
            val options = if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.UPSIDE_DOWN_CAKE) {
                ActivityOptions.makeBasic()
                    .setPendingIntentBackgroundActivityStartMode(ActivityOptions.MODE_BACKGROUND_ACTIVITY_START_ALLOWED)
                    .toBundle()
            } else null
            runCatching {
                vm.appWidgets.host.startAppWidgetConfigureActivityForResult(this, pending.appWidgetId, 0, REQUEST_CONFIGURE_WIDGET, options)
            }.onFailure { cancelWidget(pending) }
            return
        }
        placeWidget(pending)
    }

    private fun placeWidget(pending: PendingAppWidget) {
        pendingWidget = null
        val info = vm.appWidgets.info(pending.appWidgetId) ?: return cancelWidget(pending)
        vm.addAppWidget(pending.appWidgetId, info, pending.target)
    }

    private fun cancelWidget(pending: PendingAppWidget) {
        pendingWidget = null
        vm.appWidgets.deleteId(pending.appWidgetId)
    }

    /** Result of the widget configure activity (started by AppWidgetHost, which only offers the request-code API). */
    @Deprecated("AppWidgetHost.startAppWidgetConfigureActivityForResult reports through onActivityResult")
    override fun onActivityResult(requestCode: Int, resultCode: Int, data: Intent?) {
        super.onActivityResult(requestCode, resultCode, data)
        if (requestCode != REQUEST_CONFIGURE_WIDGET) return
        val pending = pendingWidget ?: return
        if (resultCode == RESULT_OK) placeWidget(pending) else cancelWidget(pending)
    }

    // endregion

    private companion object {
        const val REQUEST_CONFIGURE_WIDGET = 41
        const val KEY_PENDING_PHOTO = "pending_photo_widget"
    }

    private fun Rect.toAndroidRect(): android.graphics.Rect =
        android.graphics.Rect(left.roundToInt(), top.roundToInt(), right.roundToInt(), bottom.roundToInt())
}
