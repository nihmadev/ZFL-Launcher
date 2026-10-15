package com.zfl.launcher.settings

import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import com.zfl.launcher.data.DEFAULT_DOCK_CAPACITY
import com.zfl.launcher.data.DOCK_CAPACITY_OPTIONS
import com.zfl.launcher.ui.theme.Tokens

/** How home screen pages animate while swiping between them. */
enum class PageTransition { Default, Rotation, Flip, ZoomIn, Alpha }

/** Items of the long-press menu that the user can turn off. */
enum class MenuAction { Shortcuts, RenameFolder, AddToHome, AppInfo, Dock, HideApp, EditHome, RemoveApp }

/** Every user-configurable option of the launcher. Defaults match a fresh install. */
data class SettingsState(
    /** Hide the system navigation bar on the home screen (swipe from the edge to show it). */
    val hideNavigation: Boolean = false,
    /** Frosted glass (blurred wallpaper) on the dock, pills and menus. Off = flat tint. */
    val blurEffect: Boolean = true,
    /** Whether [textScale] is applied to icon labels. */
    val customTextSize: Boolean = false,
    /** Icon label size multiplier, see [MIN_TEXT_SCALE]..[MAX_TEXT_SCALE]. */
    val textScale: Float = 1f,
    /** Rows of icons per home screen page (the grid is always 4 columns wide). */
    val gridRows: Int = 6,
    val pageTransition: PageTransition = PageTransition.Default,
    /** Scale animation when an app is launched from its icon. */
    val openCloseAnimation: Boolean = true,
    /** Icons zoom into place when returning to the home screen from an app. */
    val resumeAnimation: Boolean = true,

    /** Opacity of the white tint laid over glass surfaces, see [MAX_GLASS_TINT]. */
    val glassTint: Float = DEFAULT_GLASS_TINT,
    /** Thin highlight along the edge of glass surfaces. */
    val glassEdge: Boolean = true,
    /** Blur the home screen behind folders, menus and search. */
    val homeBlur: Boolean = true,
    /** Radius of [homeBlur] in dp, see [MIN_HOME_BLUR]..[MAX_HOME_BLUR]. */
    val homeBlurRadius: Float = DEFAULT_HOME_BLUR,
    /** How much the screen is darkened behind the long-press menu (0..[MAX_MENU_DIM]). */
    val menuDim: Float = DEFAULT_MENU_DIM,

    /** App icon size multiplier, see [MIN_ICON_SCALE]..[MAX_ICON_SCALE]. */
    val iconScale: Float = 1f,
    /** Icon corner radius relative to the icon size: 0 is a square, 0.5 a circle. */
    val iconCorner: Float = DEFAULT_ICON_CORNER,
    /** Labels under home screen and folder icons. */
    val showLabels: Boolean = true,

    val showDock: Boolean = true,
    /** Favorite apps the dock holds, one of [DOCK_CAPACITY_OPTIONS]. */
    val dockSlots: Int = DEFAULT_DOCK_CAPACITY,
    /** Glass background behind the dock icons. */
    val dockBackground: Boolean = true,
    /** The "Search" / page dots pill above the dock. */
    val showPagePill: Boolean = true,
    /** Gesture used to open app search on a home screen page. */
    val searchSwipe: SearchSwipe = SearchSwipe.Down,
    /** The App Library page after the last home screen page. */
    val appLibrary: Boolean = true,
    /** Long-press menu items the user turned off. */
    val hiddenMenuActions: Set<MenuAction> = emptySet(),

    /** Keys ([com.zfl.launcher.data.AppInfo.key]) of apps hidden from the home screen. */
    val hiddenApps: Set<String> = emptySet(),
    /** App key → label chosen by the user. */
    val customLabels: Map<String, String> = emptyMap(),
    /** App key → version stamp of a custom icon image stored in [LauncherSettings.customIconFile]. */
    val customIcons: Map<String, Long> = emptyMap(),
) {
    /** The label multiplier that should actually be used. */
    val labelScale: Float get() = if (customTextSize) textScale else 1f

    /** Size of app and folder icons on the home screen, in the dock and in folders. */
    val iconSize: Dp get() = Tokens.IconSize * iconScale

    /** Dock height: grows and shrinks with the icons so their margins stay the same. */
    val dockHeight: Dp get() = Tokens.DockHeight + (iconSize - Tokens.IconSize)

    /** Space under the home grid taken by the page pill and the dock. */
    val bottomBarHeight: Dp
        get() = (if (showPagePill) Tokens.PillHeight + PILL_MARGIN * 2 else 0.dp) +
            (if (showDock) dockHeight + Tokens.DockBottomMargin else 0.dp)

    fun isMenuActionEnabled(action: MenuAction): Boolean = action !in hiddenMenuActions

    /** Brings every numeric value back into its allowed range. */
    fun sanitized(): SettingsState = copy(
        textScale = textScale.coerceIn(MIN_TEXT_SCALE, MAX_TEXT_SCALE),
        gridRows = gridRows.coerceIn(GRID_ROW_OPTIONS.first(), GRID_ROW_OPTIONS.last()),
        glassTint = glassTint.coerceIn(0f, MAX_GLASS_TINT),
        homeBlurRadius = homeBlurRadius.coerceIn(MIN_HOME_BLUR, MAX_HOME_BLUR),
        menuDim = menuDim.coerceIn(0f, MAX_MENU_DIM),
        iconScale = iconScale.coerceIn(MIN_ICON_SCALE, MAX_ICON_SCALE),
        iconCorner = iconCorner.coerceIn(0f, MAX_ICON_CORNER),
        dockSlots = dockSlots.coerceIn(DOCK_CAPACITY_OPTIONS.first(), DOCK_CAPACITY_OPTIONS.last()),
    )

    companion object {
        const val MIN_TEXT_SCALE = 0.7f
        const val MAX_TEXT_SCALE = 1.3f
        val GRID_ROW_OPTIONS: List<Int> = listOf(5, 6, 7)

        const val DEFAULT_GLASS_TINT = 0.12f
        const val MAX_GLASS_TINT = 0.5f
        const val DEFAULT_HOME_BLUR = 20f
        const val MIN_HOME_BLUR = 4f
        const val MAX_HOME_BLUR = 40f
        const val DEFAULT_MENU_DIM = 0.45f
        const val MAX_MENU_DIM = 0.85f
        const val MIN_ICON_SCALE = 0.75f
        const val MAX_ICON_SCALE = 1.25f
        const val DEFAULT_ICON_CORNER = 0.225f
        const val MAX_ICON_CORNER = 0.5f

        /** Vertical margin above and below the page pill. */
        val PILL_MARGIN: Dp = 10.dp
    }
}
