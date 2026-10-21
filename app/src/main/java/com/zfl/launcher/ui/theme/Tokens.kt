package com.zfl.launcher.ui.theme

import androidx.compose.ui.graphics.Color
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp

/** Design tokens. Values are measured from the reference screenshots, see docs/DESIGN.md. */
object Tokens {
    val IconSize = 70.dp
    const val IconCornerRatio = 0.225f
    /** Default margins of the home grid, and the largest ones offered in settings. */
    val GridSidePadding = 22.dp
    val GridTopPadding = 12.dp
    val GridSideMarginMax = 48.dp
    val GridVerticalMarginMax = 64.dp
    /** Tallest a grid cell may get at the default icon size, so icons don't drift apart on a sparse grid. */
    val MaxCellHeight = 112.dp
    /** Smallest cell the grid keeps whatever the margins: a touch target. */
    val MinCellSize = 48.dp
    /** Smallest space between two icons side by side, and the smallest icon a narrow cell may draw. */
    val MinIconGap = 8.dp
    val MinIconSize = 24.dp
    /** Columns of an App Library category, independent of the Home Screen grid. */
    const val LibraryColumns = 4
    val LibraryDragScrollEdge = 56.dp
    val LibraryDragScrollSpeed = 640.dp
    const val LibraryGroupHoverMillis = 550L
    val LabelSize = 13.sp

    /** Folder preview and expanded surface share the same three-column arrangement. */
    const val FolderPreviewPadding = 0.13f
    const val FolderPreviewGap = 0.05f
    val FolderPanelWidth = 340.dp
    val FolderPanelHeight = 356.dp
    val FolderPanelRadius = 40.dp
    val FolderPanelMinWidth = 280.dp
    val FolderPanelMaxWidth = 400.dp
    val FolderPanelMinHeight = 280.dp
    val FolderPanelMaxHeight = 460.dp
    val FolderPanelMaxRadius = 64.dp
    const val FolderMinTitleScale = 0.75f
    const val FolderMaxTitleScale = 1.25f
    const val FolderMaxScrimAlpha = 0.6f
    val FolderSettingsPreviewHeight = 280.dp
    const val FolderSettingsPreviewScale = 0.6f
    val FolderScreenMargin = 24.dp
    val FolderGridTop = 20.dp
    val FolderTitleSize = 32.sp
    val FolderTitleGap = 24.dp
    val FolderTitleFieldRadius = 14.dp
    val FolderTitleFieldPadding = 4.dp
    val FolderPageHeight = 28.dp
    val FolderPageDotSize = 6.dp
    val FolderPageDotGap = 8.dp
    const val FolderPageInactiveAlpha = 0.3f
    const val FolderScrimAlpha = 0.18f
    const val FolderDetailRevealStart = 0.35f

    val DockHeight = 108.dp
    val DockRadius = 38.dp
    val DockSideMargin = 13.dp
    val DockBottomMargin = 14.dp

    /** Smart Stack side dots: diameter, gap between dots and distance from the stack's edge. */
    val StackDotSize = 5.dp
    val StackDotGap = 4.dp
    val StackIndicatorMargin = 5.dp

    val PillHeight = 26.dp
    val SearchSwipeThreshold = 56.dp
    val DragPageSwipeThreshold = 48.dp

    val GlassTint = Color.White.copy(alpha = 0.12f)
    val GlassFallback = Color(0xB32D2D2A)
    val GlassStroke = Color.White.copy(alpha = 0.35f)
    /** Edge highlight of glass surfaces (and app icons): a top-lit vertical gradient of [GlassStroke]. */
    val GlassStrokeWidth = 1.dp
    const val GlassStrokeMidAlpha = 0.25f
    const val GlassStrokeBottomAlpha = 0.5f

    val MenuBackground = Color(0xF21C1C1E)
    val MenuStroke = Color.White.copy(alpha = 0.16f)
    val MenuWidth = 230.dp
    val MenuItemHeight = 40.dp
    val MenuRadius = 24.dp
    val MenuVerticalPadding = 6.dp
    val WidgetBackground = Color(0xFF1C1C1D)
    /** Corner radius of a widget card on the home screen. */
    val WidgetRadius = 22.dp
    /** Shadow under an icon or widget lifted by a drag. */
    val DragShadow = 16.dp

    /** Vertical gradient behind Dark and Tinted app icons (iOS 18 dark icon backdrop). */
    val IconDarkTop = Color(0xFF323235)
    val IconDarkBottom = Color(0xFF0F0F11)

    /**
     * Notification badge over an app icon's top-end corner, measured in shares of the icon size so
     * it follows every area's icon size (iOS: about 22 pt on a 60 pt icon, 6 pt past the corner).
     */
    const val BadgeSizeRatio = 0.36f
    const val BadgeDotRatio = 0.2f
    const val BadgeOverhangRatio = 0.08f
    /** Number height relative to the badge, and the side padding of a two- or three-character pill. */
    const val BadgeTextRatio = 0.64f
    const val BadgePaddingRatio = 0.3f
    /** Hairline that keeps the badge apart from a red icon. */
    const val BadgeStrokeRatio = 0.012f
    /** iOS system red. */
    val BadgeColor = Color(0xFFFF3B30)
    val BadgeStroke = Color.Black.copy(alpha = 0.15f)
    val BadgeText = Color.White

    val Destructive = Color(0xFFFF453A)
    /** Default accent (iOS system blue); Dynamic Color replaces it, see [AccentColors]. */
    val Accent = Color(0xFF0A84FF)
    /** Default "on" track of switches (iOS system green). */
    val SwitchOn = Color(0xFF34C759)
    val Scrim = Color.Black.copy(alpha = 0.45f)

    /** Dark tint of full-height glass sheets (widget gallery, stack editor) so their content stays legible. */
    val SheetTint = Color.Black.copy(alpha = 0.35f)
    val TextPrimary = Color.White
    val TextSecondary = Color.White.copy(alpha = 0.6f)
}
