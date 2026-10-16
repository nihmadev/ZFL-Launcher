package com.zfl.launcher.ui.home

import android.appwidget.AppWidgetProviderInfo
import android.content.pm.ShortcutInfo
import androidx.activity.compose.BackHandler
import androidx.compose.animation.core.Animatable
import androidx.compose.animation.core.animateDpAsState
import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ExperimentalLayoutApi
import androidx.compose.foundation.layout.WindowInsets
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.navigationBarsIgnoringVisibility
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.statusBarsIgnoringVisibility
import androidx.compose.foundation.layout.windowInsetsPadding
import androidx.compose.foundation.pager.HorizontalPager
import androidx.compose.foundation.pager.rememberPagerState
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.SideEffect
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.rememberUpdatedState
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.geometry.Rect
import androidx.compose.ui.graphics.rememberGraphicsLayer
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.input.pointer.PointerEventPass
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.platform.LocalWindowInfo
import androidx.compose.ui.unit.dp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.zfl.launcher.LauncherViewModel
import androidx.compose.runtime.CompositionLocalProvider
import com.zfl.launcher.data.WidgetTarget
import com.zfl.launcher.ui.widgets.LocalWidgetEnvironment
import com.zfl.launcher.SearchMode
import com.zfl.launcher.data.AppInfo
import com.zfl.launcher.settings.LocalSettingsState
import com.zfl.launcher.settings.SettingsState
import com.zfl.launcher.ui.glass.LocalGlassSource
import com.zfl.launcher.ui.library.AppLibraryPage
import com.zfl.launcher.ui.theme.Motion
import androidx.compose.runtime.snapshotFlow
import androidx.compose.ui.platform.LocalHapticFeedback
import com.zfl.launcher.ui.theme.Haptics

/** Actions that need an Activity: launching apps and opening system screens. */
interface LauncherActions {
    fun launch(app: AppInfo, bounds: Rect)
    fun launchShortcut(shortcut: ShortcutInfo, bounds: Rect)
    fun openAppInfo(app: AppInfo)
    fun uninstall(app: AppInfo)
    fun requestGlassAccess()
    fun openSettings()
    fun openWallpaperPicker()
    /** Opens the user's browser / search app for [query]. */
    fun webSearch(query: String)

    // Widget taps.
    fun openClock()
    fun openCalendar()
    fun requestCalendarPermission()
    fun openBatterySettings()
    fun openWeatherSettings()
    fun pickWidgetPhoto(widgetId: String)
    /** Binds (asking the user if needed), configures and places another app's widget. */
    fun addAppWidget(info: AppWidgetProviderInfo, target: WidgetTarget)
}

/** Pager index of the Today page; the home pages start right after it. */
internal const val TODAY_PAGE: Int = 0
internal const val FIRST_HOME_PAGE: Int = 1

/**
 * Root of the launcher UI: swipeable pages, the page pill, the glass dock, and the layers
 * above them (edit bar, open folder, long-press menu, the icon being dragged).
 */
@OptIn(ExperimentalLayoutApi::class)
@Composable
fun HomeScreen(vm: LauncherViewModel, actions: LauncherActions) {
    val state by vm.state.collectAsStateWithLifecycle()
    val homeWallpaper by vm.homeWallpaper.collectAsStateWithLifecycle()
    val menu by vm.menu.collectAsStateWithLifecycle()
    val editMode by vm.editMode.collectAsStateWithLifecycle()
    val openFolder by vm.openFolder.collectAsStateWithLifecycle()
    val search by vm.search.collectAsStateWithLifecycle()
    val gallery by vm.widgetGallery.collectAsStateWithLifecycle()
    val widgetEnvironment by vm.widgetEnvironment.collectAsStateWithLifecycle()
    val library by vm.library.collectAsStateWithLifecycle()
    val openCategoryId by vm.openCategory.collectAsStateWithLifecycle()
    val visibleApps by vm.visibleApps.collectAsStateWithLifecycle()
    val settings = LocalSettingsState.current
    val scope = rememberCoroutineScope()
    val density = LocalDensity.current

    val pageCount = remember { mutableIntStateOf(FIRST_HOME_PAGE + state.pages.size) }
    val pager = rememberPagerState(initialPage = FIRST_HOME_PAGE) { pageCount.intValue }
    val drag = remember {
        DragController(
            scope = scope,
            slopPx = with(density) { 10.dp.toPx() },
            edgePx = with(density) { 28.dp.toPx() },
            callbacks = HomeDragCallbacks(vm, scope, pager, FIRST_HOME_PAGE),
        )
    }

    // A tick when a dragged item is about to make (or join) a folder or a stack.
    val haptic = LocalHapticFeedback.current
    LaunchedEffect(drag) {
        snapshotFlow { drag.mergeArmed }.collect { armed -> if (armed) haptic.performHapticFeedback(Haptics.Tick) }
    }

    // While dragging, show the layout with a gap where the item would land.
    val pages = if (drag.isDragging) drag.previewPages() else state.pages
    val dock = if (drag.isDragging) drag.previewDock() else state.dock
    val windowWidth = LocalWindowInfo.current.containerSize.width.toFloat()
    // Today, then the Home Screen pages, then the App Library (-1 when it is turned off).
    val libraryPage = if (settings.appLibrary) FIRST_HOME_PAGE + pages.size else -1
    SideEffect {
        pageCount.intValue = FIRST_HOME_PAGE + pages.size + if (settings.appLibrary) 1 else 0
        drag.state = state
        drag.currentPage = pager.currentPage - FIRST_HOME_PAGE
        drag.screenWidth = windowWidth
        drag.dockCapacity = settings.dockSlots
    }

    val pageIcons = remember { iconCallbacks(vm, actions, drag, DragSource.Page) }
    val dockIcons = remember { iconCallbacks(vm, actions, drag, DragSource.Dock) }
    val folderIcons = remember { iconCallbacks(vm, actions, drag, DragSource.Folder) }
    val libraryCallbacks = remember { libraryCallbacks(vm, actions) }
    val searchCallbacks = remember { searchCallbacks(vm, actions) }
    val pageWidgets = remember { widgetCallbacks(vm, actions, drag, onToday = false) }
    val todayWidgets = remember { widgetCallbacks(vm, actions, drag, onToday = true) }
    val widgetMenu = remember { widgetMenuActions(vm, actions) }

    LaunchedEffect(Unit) {
        vm.homePressed.collect {
            val overlay = vm.openFolder.value != null || vm.search.value != null || vm.openCategory.value != null ||
                vm.stacks.editing.value != null
            if (vm.editMode.value || overlay || vm.widgetGallery.value) {
                vm.stacks.closeEditor()
                vm.closeFolder()
                vm.exitEditMode()
                vm.closeSearch()
                vm.closeWidgetGallery()
                vm.openCategory.value = null
            } else {
                pager.animateScrollToPage(FIRST_HOME_PAGE)
            }
        }
    }
    BackHandler(enabled = menu != null || openFolder != null || editMode) {
        when {
            menu != null -> vm.closeMenu()
            openFolder != null -> vm.closeFolder()
            else -> vm.exitEditMode()
        }
    }

    // New widgets go to the page being looked at: Today, or the top of a home page.
    fun widgetTarget(): WidgetTarget = if (pager.currentPage == TODAY_PAGE) {
        WidgetTarget.Today
    } else {
        WidgetTarget.Page((pager.currentPage - FIRST_HOME_PAGE).coerceIn(0, state.pages.lastIndex))
    }

    // iOS-like "resume" animation: coming back from an app, the icons zoom into place.
    val resume = remember { Animatable(1f) }
    val resumeEnabled by rememberUpdatedState(settings.resumeAnimation)
    LaunchedEffect(Unit) {
        vm.returnedHome.collect {
            if (!resumeEnabled) return@collect
            resume.snapTo(0f)
            resume.animateTo(1f, Motion.expand())
        }
    }

    // 0 = sharp, 1 = fully blurred. Read only in layer blocks: it never recomposes the screen.
    val overlayOpen = menu != null || openFolder != null || search != null || openCategoryId != null || gallery
    val blur = animateFloatAsState(
        if (overlayOpen && settings.homeBlur) 1f else 0f,
        Motion.fade(),
        label = "blur",
    )
    val homeLayer = rememberGraphicsLayer()
    // Edit mode hides the status bar and shows the Edit/Done bar: the grid moves down a bit.
    val editInset by animateDpAsState(if (editMode) 44.dp else 0.dp, Motion.settle(), label = "editInset")
    val liftedId = menu?.entry?.id

    CompositionLocalProvider(LocalWidgetEnvironment provides widgetEnvironment) {
    Box(
        Modifier
            .fillMaxSize()
            // Follow the finger at the root, so a drag survives its page scrolling away.
            .pointerInput(drag) {
                awaitPointerEventScope {
                    while (true) drag.onPointerEvent(awaitPointerEvent(PointerEventPass.Initial))
                }
            },
    ) {
        Box(
            Modifier
                .fillMaxSize()
                .graphicsLayer {
                    val p = resume.value
                    val scale = 1.15f - 0.15f * p
                    scaleX = scale
                    scaleY = scale
                    alpha = p.coerceIn(0f, 1f)
                },
        ) {
            Box(
                Modifier
                    .fillMaxSize()
                    .recordHomeContent(homeLayer, blur, hasWallpaper = homeWallpaper != null)
                    .windowInsetsPadding(WindowInsets.statusBarsIgnoringVisibility),
            ) {
                HorizontalPager(
                    state = pager,
                    userScrollEnabled = !drag.isDragging,
                    modifier = Modifier.fillMaxSize(),
                    beyondViewportPageCount = 1,
                ) { page ->
                    if (page == TODAY_PAGE) {
                        TodayPage(
                            widgets = state.today,
                            editMode = editMode,
                            liftedId = liftedId,
                            callbacks = todayWidgets,
                            onEnterEdit = vm::enterEditMode,
                            onExitEdit = vm::exitEditMode,
                            modifier = Modifier
                                .pageTransition(settings.pageTransition) { (page - pager.currentPage) - pager.currentPageOffsetFraction }
                                .padding(top = editInset)
                                .windowInsetsPadding(WindowInsets.navigationBarsIgnoringVisibility),
                        )
                        return@HorizontalPager
                    }
                    if (page == libraryPage) {
                        AppLibraryPage(
                            categories = library,
                            onSearch = { vm.openSearch(SearchMode.Library) },
                            onOpenCategory = { vm.openCategory.value = it },
                            callbacks = libraryCallbacks,
                            modifier = Modifier.windowInsetsPadding(WindowInsets.navigationBarsIgnoringVisibility),
                        )
                        return@HorizontalPager
                    }
                    val homePage = page - FIRST_HOME_PAGE
                    val entries = pages.getOrElse(homePage) { emptyList() }
                    PageContent(
                        page = homePage,
                        entries = entries,
                        rows = state.rows,
                        cells = if (drag.isDragging) drag.previewCells() else state.cells,
                        isCurrent = page == pager.currentPage,
                        editMode = editMode,
                        drag = drag,
                        liftedId = liftedId,
                        callbacks = pageIcons,
                        widgetCallbacks = pageWidgets,
                        showGlassPrompt = homePage == 0 && !editMode && settings.blurEffect && LocalGlassSource.current == null,
                        onGlassPrompt = actions::requestGlassAccess,
                        onEnterEdit = vm::enterEditMode,
                        onExitEdit = vm::exitEditMode,
                        searchSwipe = settings.searchSwipe,
                        onSearch = { vm.openSearch(SearchMode.Spotlight) },
                        modifier = Modifier
                            .pageTransition(settings.pageTransition) {
                                (page - pager.currentPage) - pager.currentPageOffsetFraction
                            }
                            .padding(top = editInset, bottom = settings.bottomBarHeight)
                            .windowInsetsPadding(WindowInsets.navigationBarsIgnoringVisibility),
                    )
                }

                // Page pill and dock slide away while the Today page or the App Library comes in.
                Column(
                    Modifier
                        .align(Alignment.BottomCenter)
                        .windowInsetsPadding(WindowInsets.navigationBarsIgnoringVisibility)
                        .graphicsLayer {
                            val position = pager.currentPage + pager.currentPageOffsetFraction
                            val today = (FIRST_HOME_PAGE - position).coerceIn(0f, 1f)
                            val library = if (libraryPage < 0) 0f else (position - (libraryPage - 1)).coerceIn(0f, 1f)
                            val away = maxOf(today, library)
                            alpha = 1f - away
                            translationY = away * size.height
                        },
                ) {
                    if (settings.showPagePill) {
                        PagePill(
                            pager = pager,
                            firstPage = FIRST_HOME_PAGE,
                            pageCount = pages.size,
                            editMode = editMode,
                            onSearch = { vm.openSearch(SearchMode.Spotlight) },
                            modifier = Modifier
                                .align(Alignment.CenterHorizontally)
                                .padding(vertical = SettingsState.PILL_MARGIN),
                        )
                    }
                    // A hidden dock is no drop target either.
                    if (!settings.showDock) SideEffect { drag.dockBounds = null }
                    if (settings.showDock) DockBar(
                        entries = dock,
                        background = settings.dockBackground,
                        onBounds = { drag.dockBounds = it },
                        dropOrigin = drag::takeDropOrigin,
                        marker = drag.dockMarker(),
                    ) { entry ->
                        HomeIcon(
                            entry = entry,
                            showLabel = false,
                            editMode = editMode,
                            jiggleSeed = 1000 + dock.indexOf(entry),
                            callbacks = dockIcons,
                            hidden = entry.id == liftedId,
                            mergeTarget = drag.mergeArmed && drag.mergeCandidate == entry.id,
                        )
                    }
                }
            }
            BlurredHomeCopy(homeLayer, homeWallpaper, blur, settings.homeBlurRadius.dp, Modifier.matchParentSize())
        }

        HomeOverlays(
            vm = vm,
            actions = actions,
            state = state,
            drag = drag,
            library = library,
            visibleApps = visibleApps,
            folderIcons = folderIcons,
            libraryCallbacks = libraryCallbacks,
            searchCallbacks = searchCallbacks,
            widgetMenu = widgetMenu,
            onAddWidget = { kind, size -> vm.addWidget(kind, size, widgetTarget()) },
            onAddAppWidget = { info -> actions.addAppWidget(info, widgetTarget()) },
            onAddStack = { size -> vm.addSmartStack(size, widgetTarget()) },
        )

        DraggedIcon(drag)
    }
    }
}
