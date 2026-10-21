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
import androidx.compose.runtime.setValue
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
import com.zfl.launcher.ui.theme.Tokens
import androidx.compose.ui.platform.LocalConfiguration
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.platform.LocalLayoutDirection
import androidx.compose.ui.platform.LocalWindowInfo
import androidx.compose.ui.unit.LayoutDirection
import com.zfl.launcher.settings.IconArea
import androidx.compose.ui.unit.dp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.zfl.launcher.LauncherViewModel
import androidx.compose.runtime.CompositionLocalProvider
import com.zfl.launcher.data.WidgetTarget
import com.zfl.launcher.ui.widgets.LocalWidgetEnvironment
import com.zfl.launcher.SearchMode
import com.zfl.launcher.data.AppInfo
import com.zfl.launcher.data.libraryTitles
import com.zfl.launcher.settings.LocalSettingsState
import com.zfl.launcher.settings.SettingsState
import com.zfl.launcher.ui.glass.LocalGlassSource
import com.zfl.launcher.ui.library.AppLibraryPage
import com.zfl.launcher.ui.theme.Motion
import androidx.compose.runtime.snapshotFlow
import com.zfl.launcher.ui.theme.launcherHaptics
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
    val library by vm.library.state.collectAsStateWithLifecycle()
    val openGroupId by vm.library.openGroup.collectAsStateWithLifecycle()
    val chooserApp by vm.library.choosingCategory.collectAsStateWithLifecycle()
    val visibleApps by vm.visibleApps.collectAsStateWithLifecycle()
    val settings = LocalSettingsState.current
    val scope = rememberCoroutineScope()
    val density = LocalDensity.current
    // Group names follow the UI language; read from the Activity so the in-app language applies.
    val context = LocalContext.current
    val locale = LocalConfiguration.current.locales[0]
    LaunchedEffect(locale) { vm.library.titles.value = libraryTitles(context) }

    val pagerMap = remember { androidx.compose.runtime.mutableStateOf(HomePages(state.pages.size, settings.homeBehavior.showToday, settings.appLibrary)) }
    val pager = rememberPagerState(initialPage = pagerMap.value.main(settings.homeBehavior.mainPage)) { pagerMap.value.count }
    var pagerInitialized by androidx.compose.runtime.saveable.rememberSaveable { androidx.compose.runtime.mutableStateOf(false) }
    val drag = remember {
        DragController(
            scope = scope,
            slopPx = with(density) { 10.dp.toPx() },
            edgePx = with(density) { 28.dp.toPx() },
            pageSwipePx = with(density) { Tokens.DragPageSwipeThreshold.toPx() },
            callbacks = HomeDragCallbacks(vm, scope, pager) { pagerMap.value.firstHome },
        )
    }

    // A tick when a dragged item is about to make (or join) a folder or a stack.
    val haptic = launcherHaptics()
    LaunchedEffect(drag) {
        snapshotFlow { drag.mergeArmed }.collect { armed -> if (armed) haptic.performHapticFeedback(Haptics.Tick) }
    }

    // While dragging, show the layout with a gap where the item would land.
    val pages = if (drag.isDragging) drag.previewPages() else state.pages
    val dock = if (drag.isDragging) drag.previewDock() else state.dock
    val pageMap = HomePages(pages.size, settings.homeBehavior.showToday, settings.appLibrary)
    SideEffect {
        val previous = pagerMap.value
        when {
            !pagerInitialized && vm.homeLoaded -> {
                pager.requestScrollToPage(pageMap.main(settings.homeBehavior.mainPage))
                pagerInitialized = true
            }
            previous != pageMap -> pager.requestScrollToPage(pageMap.position(previous.page(pager.currentPage), settings.homeBehavior.mainPage))
        }
        pagerMap.value = pageMap
    }
    val windowWidth = LocalWindowInfo.current.containerSize.width.toFloat()
    val rtl = LocalLayoutDirection.current == LayoutDirection.Rtl
    // Each area resolves its own icon size and names; the pages leave room for the dock it measures.
    val homeMetrics = rememberAreaMetrics(settings, IconArea.Home)
    val dockMetrics = rememberAreaMetrics(settings, IconArea.Dock)
    // Today, then the Home Screen pages, then the App Library (-1 when it is turned off).
    val libraryPage = pageMap.libraryPosition
    SideEffect {
        drag.state = state
        drag.pageMap = pageMap
        drag.currentPage = pager.currentPage - pageMap.firstHome
        drag.screenWidth = windowWidth
        drag.rtl = rtl
        drag.dockCapacity = settings.dockSlots
    }
    // Locking the layout mid-drag drops nothing: the item goes back and edit mode ends.
    LaunchedEffect(settings.layoutLocked) {
        if (settings.layoutLocked) {
            drag.cancel()
            vm.exitEditMode()
        }
    }
    LaunchedEffect(drag.isDragging) {
        if (drag.isDragging && drag.session?.source == DragSource.Library) {
            vm.library.close()
            pager.scrollToPage(pageMap.firstHome + state.pages.lastIndex)
        }
    }

    val pageIcons = remember { iconCallbacks(vm, actions, drag, DragSource.Page) }
    val dockIcons = remember { iconCallbacks(vm, actions, drag, DragSource.Dock) }
    val folderIcons = remember { iconCallbacks(vm, actions, drag, DragSource.Folder) }
    val libraryCallbacks = remember { libraryCallbacks(vm, actions, drag) }
    val searchCallbacks = remember { searchCallbacks(vm, actions) }
    val pageWidgets = remember { widgetCallbacks(vm, actions, drag, onToday = false) }
    val todayWidgets = remember { widgetCallbacks(vm, actions, drag, onToday = true) }
    val widgetMenu = remember { widgetMenuActions(vm, actions) }

    val dispatcher = remember(vm, actions, drag, pager, scope) { homeDispatcher(vm, actions, drag, pager, scope) }
    LaunchedEffect(dispatcher) {
        vm.homePressed.collect {
            val prefs = vm.settings.state.value
            dispatcher.home(prefs.homeBehavior.repeatHome, prefs.appLibrary, drag.isDragging)
        }
    }
    LaunchedEffect(dispatcher) {
        vm.returnToHome.collect { dispatcher.returned(vm.settings.state.value.homeBehavior.returnPolicy) }
    }
    BackHandler(enabled = menu != null || openFolder != null || editMode || drag.isDragging) {
        when {
            drag.isDragging -> { drag.cancel(); vm.exitEditMode() }
            menu != null -> vm.closeMenu()
            openFolder != null -> vm.closeFolder()
            else -> vm.exitEditMode()
        }
    }

    // New widgets go to the page being looked at: Today, or the top of a home page.
    fun widgetTarget(): WidgetTarget = if (pageMap.page(pager.currentPage) == HomePage.Today) {
        WidgetTarget.Today
    } else {
        WidgetTarget.Page((pager.currentPage - pageMap.firstHome).coerceIn(0, state.pages.lastIndex))
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
    val overlayOpen = menu != null || openFolder != null || search != null || openGroupId != null || chooserApp != null || gallery
    val blur = animateFloatAsState(
        if (overlayOpen && settings.homeBlur) 1f else 0f,
        Motion.fade(),
        label = "blur",
    )
    val homeLayer = rememberGraphicsLayer()
    // Edit mode hides the status bar and shows the Edit/Done bar: the grid moves down a bit.
    val editInsetSpring by animateDpAsState(if (editMode) 44.dp else 0.dp, Motion.settle(), label = "editInset")
    // The spring undershoots below zero on the way back, and padding must be non-negative.
    val editInset = editInsetSpring.coerceAtLeast(0.dp)
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
                    key = { if (it < pageMap.count) pageMap.key(it) else "retired_$it" },
                ) { page ->
                    if (page !in 0 until pageMap.count) return@HorizontalPager
                    if (pageMap.page(page) == HomePage.Today) {
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
                            library = library,
                            onSearch = { vm.openSearch(SearchMode.Library) },
                            onOpenGroup = vm.library::open,
                            onViewChange = vm.library::setView,
                            callbacks = libraryCallbacks,
                            drag = drag,
                            modifier = Modifier.padding(top = editInset)
                                .windowInsetsPadding(WindowInsets.navigationBarsIgnoringVisibility),
                        )
                        return@HorizontalPager
                    }
                    val homePage = page - pageMap.firstHome
                    val entries = pages.getOrElse(homePage) { emptyList() }
                    PageContent(
                        page = homePage,
                        entries = entries,
                        grid = state.grid,
                        metrics = homeMetrics,
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
                        behavior = settings.homeBehavior,
                        gesturesEnabled = !vm.globalGestureBlocked(drag),
                        onAction = { dispatcher.execute(it, vm.settings.state.value.appLibrary, vm.globalGestureBlocked(drag)) },
                        modifier = Modifier
                            .pageTransition(settings.pageTransition) {
                                (page - pager.currentPage) - pager.currentPageOffsetFraction
                            }
                            .padding(top = editInset, bottom = settings.bottomBarHeight(dockMetrics.labelBlock))
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
                            val today = if (pageMap.today) (pageMap.firstHome - position).coerceIn(0f, 1f) else 0f
                            val library = if (libraryPage < 0) 0f else (position - (libraryPage - 1)).coerceIn(0f, 1f)
                            val away = maxOf(today, library)
                            alpha = 1f - away
                            translationY = away * size.height
                        },
                ) {
                    if (settings.showPagePill) {
                        PagePill(
                            pager = pager,
                            firstPage = pageMap.firstHome,
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
                        metrics = dockMetrics,
                        slots = settings.dockSlots,
                        height = settings.dockHeight(dockMetrics.labelBlock),
                        background = settings.dockBackground,
                        onBounds = { drag.dockBounds = it },
                        dropOrigin = drag::takeDropOrigin,
                    ) { entry, slot ->
                        HomeIcon(
                            entry = entry,
                            slot = slot,
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
