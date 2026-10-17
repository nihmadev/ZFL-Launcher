package com.zfl.launcher.ui.home

import androidx.compose.foundation.pager.PagerState
import androidx.compose.ui.geometry.Rect
import com.zfl.launcher.LauncherViewModel
import com.zfl.launcher.SearchMode
import com.zfl.launcher.settings.AppTarget
import com.zfl.launcher.settings.resolveHomeTarget
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.launch

internal fun homeDispatcher(
    vm: LauncherViewModel,
    actions: LauncherActions,
    drag: DragController,
    pager: PagerState,
    scope: CoroutineScope,
): HomeCommandDispatcher {
    fun pages(): HomePages = HomePages(vm.state.value.pages.size, vm.settings.state.value.homeBehavior.showToday, vm.settings.state.value.appLibrary)
    return HomeCommandDispatcher(object : HomeCommands {
        override fun search() = vm.openSearch(SearchMode.Spotlight)
        override fun settings() = actions.openSettings()
        override fun library() { scope.launch { pager.animateScrollToPage(pages().libraryPosition) } }
        override fun mainPage() { scope.launch { pager.animateScrollToPage(pages().main(vm.settings.state.value.homeBehavior.mainPage)) } }
        override fun launch(target: AppTarget) {
            val snapshot = vm.repository.snapshot.value
            resolveHomeTarget(target, snapshot.apps, snapshot.unavailableUsers) { it.key }?.let { actions.launch(it, Rect.Zero) }
        }
        override fun closeTop(): Boolean = when {
            vm.menu.value != null -> { vm.closeMenu(); true }
            vm.removeRequest.value != null -> { vm.removeRequest.value = null; true }
            vm.stacks.editing.value != null -> { vm.stacks.closeEditor(); true }
            vm.widgetGallery.value -> { vm.closeWidgetGallery(); true }
            vm.library.choosingCategory.value != null -> { vm.library.closeCategoryChooser(); true }
            vm.search.value != null -> { vm.closeSearch(); true }
            vm.openFolder.value != null -> { vm.closeFolder(); true }
            vm.library.openGroup.value != null -> { vm.library.close(); true }
            vm.editMode.value || drag.isDragging -> { drag.cancel(); vm.exitEditMode(); true }
            else -> false
        }
    })
}

internal fun LauncherViewModel.globalGestureBlocked(drag: DragController): Boolean =
    editMode.value || drag.isDragging || menu.value != null || removeRequest.value != null ||
        openFolder.value != null || search.value != null || widgetGallery.value ||
        stacks.editing.value != null || library.openGroup.value != null || library.choosingCategory.value != null
