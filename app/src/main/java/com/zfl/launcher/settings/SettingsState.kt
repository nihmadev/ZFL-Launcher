package com.zfl.launcher.settings

import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import com.zfl.launcher.data.DEFAULT_DOCK_CAPACITY
import com.zfl.launcher.data.DOCK_CAPACITY_OPTIONS
import com.zfl.launcher.data.LibraryConfig
import com.zfl.launcher.ui.glass.GlassMode
import com.zfl.launcher.ui.icon.DEFAULT_ICON_TINT
import com.zfl.launcher.ui.icon.IconAppearance
import com.zfl.launcher.ui.theme.Tokens

/** How home screen pages animate while swiping between them. */
enum class PageTransition { Default, Rotation, Flip, ZoomIn, Alpha }

/** Items of the long-press menu that the user can turn off. */
enum class MenuAction {
    Shortcuts, RenameFolder, AddToHome, AppInfo, Dock, HideApp, EditHome, RemoveApp,
    /** App Library only: pin or unpin the app at the top of the library. */
    LibraryPin,
    /** App Library only: choose the app's category. */
    LibraryCategory,
}

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
    /** Space around the home grid; the grid size itself is saved with the layout (see LayoutData.grid). */
    val gridMargins: GridMargins = GridMargins.Default,
    val gridAlignment: GridAlignment = GridAlignment.Top,
    /** Turns off moving, adding and removing things on the Home Screen; apps still open and pages still swipe. */
    val layoutLocked: Boolean = false,
    val pageTransition: PageTransition = PageTransition.Default,
    /** Scale animation when an app is launched from its icon. */
    val openCloseAnimation: Boolean = true,
    /** Icons zoom into place when returning to the home screen from an app. */
    val resumeAnimation: Boolean = true,

    /** Opacity of the white tint laid over glass surfaces, see [MAX_GLASS_TINT]. */
    val glassTint: Float = DEFAULT_GLASS_TINT,
    /** Thin highlight along the edge of glass surfaces. */
    val glassEdge: Boolean = true,
    /** Frosted or Liquid glass; Liquid needs Android 13+ and falls back to Frosted elsewhere. */
    val glassMode: GlassMode = DEFAULT_GLASS_MODE,
    /** How strongly the rim of Liquid glass bends the wallpaper, 0..1. */
    val glassRefraction: Float = DEFAULT_GLASS_REFRACTION,
    /** The highlight of Liquid glass follows the tilt of the phone. */
    val glassTiltLight: Boolean = true,
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
    /** Own icon and label sizes of the home pages, the dock and folders; inherited by default. */
    val homeStyle: AreaStyle = AreaStyle.Inherit,
    val dockStyle: AreaStyle = AreaStyle.Inherit,
    val folderStyle: AreaStyle = AreaStyle.Inherit,
    val folders: FolderPreferences = FolderPreferences(),
    /** Default, Dark, Tinted or Auto (Default/Dark following the system theme). */
    val iconAppearance: IconAppearance = IconAppearance.Default,
    /** Tinted icons take their hue from the wallpaper instead of [iconTintColor]. */
    val iconTintFromWallpaper: Boolean = true,
    /** Opaque ARGB color picked by the user for Tinted icons. */
    val iconTintColor: Int = DEFAULT_ICON_TINT,
    /** Saturation of the tint, 0 (white) to 1 (vivid). */
    val iconTintSaturation: Float = DEFAULT_TINT_SATURATION,
    /** Whether Dark and Tinted also restyle custom icons picked by the user. */
    val tintCustomIcons: Boolean = true,
    /** The glass edge highlight (as on the dock and menus) along every app icon, in every appearance. */
    val iconEdge: Boolean = true,

    val showDock: Boolean = true,
    /** Favorite apps the dock holds, one of [DOCK_CAPACITY_OPTIONS]. */
    val dockSlots: Int = DEFAULT_DOCK_CAPACITY,
    /** Glass background behind the dock icons. */
    val dockBackground: Boolean = true,
    /** The "Search" / page dots pill above the dock. */
    val showPagePill: Boolean = true,
    /** Saved gesture commands, Home navigation and launcher feedback. */
    val homeBehavior: HomeBehavior = HomeBehavior(),
    /** The optional App Library page; disabling it preserves its configuration. */
    val appLibrary: Boolean = true,
    /** View, categories, pins and new-app placement of the App Library. */
    val library: LibraryConfig = LibraryConfig(),
    /** Long-press menu items the user turned off. */
    val hiddenMenuActions: Set<MenuAction> = emptySet(),

    /** Keys ([com.zfl.launcher.data.AppInfo.key]) of apps hidden from the home screen. */
    val hiddenApps: Set<String> = emptySet(),
    /** App key → label chosen by the user. */
    val customLabels: Map<String, String> = emptyMap(),
    /** App key → version stamp of a custom icon image stored in [LauncherSettings.customIconFile]. */
    val customIcons: Map<String, Long> = emptyMap(),

    /** Accent color of buttons, switches and sliders taken from the wallpaper (Dynamic Color). */
    val dynamicColor: Boolean = false,

    /** Optional music/charging window; access is checked separately at runtime. */
    val island: IslandPreferences = IslandPreferences(),

    /** The first-run setup was finished with "Get Started" (or the install predates it, see [isOnboardingNeeded]). */
    val onboardingDone: Boolean = false,
) {
    /** The label multiplier that should actually be used. */
    val labelScale: Float get() = if (customTextSize) textScale else 1f

    /** General icon size; the home pages, the dock and folders may override it ([look]). */
    val iconSize: Dp get() = Tokens.IconSize * iconScale

    fun style(area: IconArea): AreaStyle = when (area) {
        IconArea.Home -> homeStyle
        IconArea.Dock -> dockStyle
        IconArea.Folder -> folderStyle
    }

    fun withStyle(area: IconArea, style: AreaStyle): SettingsState = when (area) {
        IconArea.Home -> copy(homeStyle = style)
        IconArea.Dock -> copy(dockStyle = style)
        IconArea.Folder -> copy(folderStyle = style)
    }

    /** What [area] draws: its own values, the general ones otherwise. The dock shows no names unless asked to. */
    fun look(area: IconArea): AreaLook {
        val style = style(area)
        return AreaLook(
            iconScale = style.iconScale ?: iconScale,
            labelScale = style.labelScale ?: labelScale,
            showLabels = style.showLabels ?: (area != IconArea.Dock && showLabels),
        )
    }

    /**
     * Dock height: grows and shrinks with the dock icons so their margins stay the same, and
     * makes room for [dockLabelBlock] (gap and one line of text) when the dock shows names.
     */
    fun dockHeight(dockLabelBlock: Dp): Dp {
        val dock = look(IconArea.Dock)
        return Tokens.DockHeight + (dock.iconSize - Tokens.IconSize) + if (dock.showLabels) dockLabelBlock else 0.dp
    }

    /** Space under the home grid taken by the page pill and the dock. */
    fun bottomBarHeight(dockLabelBlock: Dp): Dp =
        (if (showPagePill) Tokens.PillHeight + PILL_MARGIN * 2 else 0.dp) +
            (if (showDock) dockHeight(dockLabelBlock) + Tokens.DockBottomMargin else 0.dp)

    fun isMenuActionEnabled(action: MenuAction): Boolean = action !in hiddenMenuActions

    /** Search can be opened from a home page: the swipe or the "Search" pill. */
    val hasHomeSearch: Boolean get() = homeBehavior.swipeUp == HomeAction.Search || homeBehavior.swipeDown == HomeAction.Search ||
        homeBehavior.emptyDoubleTap == HomeAction.Search || homeBehavior.repeatHome == HomeAction.Search || showPagePill

    /** Brings every numeric value back into its allowed range. */
    fun sanitized(): SettingsState = copy(
        textScale = textScale.coerceIn(MIN_TEXT_SCALE, MAX_TEXT_SCALE),
        gridMargins = gridMargins.sanitized(),
        homeStyle = homeStyle.sanitized(),
        dockStyle = dockStyle.sanitized(),
        folderStyle = folderStyle.sanitized(),
        folders = folders.sanitized(),
        glassTint = glassTint.coerceIn(0f, MAX_GLASS_TINT),
        glassRefraction = if (glassRefraction.isNaN()) DEFAULT_GLASS_REFRACTION else glassRefraction.coerceIn(0f, 1f),
        homeBlurRadius = homeBlurRadius.coerceIn(MIN_HOME_BLUR, MAX_HOME_BLUR),
        menuDim = menuDim.coerceIn(0f, MAX_MENU_DIM),
        iconScale = iconScale.coerceIn(MIN_ICON_SCALE, MAX_ICON_SCALE),
        iconCorner = iconCorner.coerceIn(0f, MAX_ICON_CORNER),
        iconTintColor = iconTintColor or OPAQUE,
        iconTintSaturation = if (iconTintSaturation.isNaN()) DEFAULT_TINT_SATURATION else iconTintSaturation.coerceIn(0f, 1f),
        dockSlots = dockSlots.coerceIn(DOCK_CAPACITY_OPTIONS.first(), DOCK_CAPACITY_OPTIONS.last()),
        homeBehavior = homeBehavior.sanitized(),
    )

    companion object {
        const val MIN_TEXT_SCALE = 0.7f
        const val MAX_TEXT_SCALE = 1.3f

        const val DEFAULT_GLASS_TINT = 0.12f
        const val MAX_GLASS_TINT = 0.5f
        val DEFAULT_GLASS_MODE: GlassMode = GlassMode.Liquid
        const val DEFAULT_GLASS_REFRACTION: Float = 0.8f
        const val DEFAULT_HOME_BLUR = 20f
        const val MIN_HOME_BLUR = 4f
        const val MAX_HOME_BLUR = 40f
        const val DEFAULT_MENU_DIM = 0.45f
        const val MAX_MENU_DIM = 0.85f
        const val MIN_ICON_SCALE = 0.75f
        const val MAX_ICON_SCALE = 1.25f
        const val DEFAULT_ICON_CORNER = 0.225f
        const val MAX_ICON_CORNER = 0.5f
        const val DEFAULT_TINT_SATURATION = 0.7f
        private const val OPAQUE = 0xFF000000.toInt()

        /** Vertical margin above and below the page pill. */
        val PILL_MARGIN: Dp = 10.dp
    }
}
