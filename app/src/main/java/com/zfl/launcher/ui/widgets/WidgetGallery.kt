package com.zfl.launcher.ui.widgets

import android.appwidget.AppWidgetProviderInfo
import androidx.activity.compose.BackHandler
import androidx.compose.animation.AnimatedContent
import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.core.spring
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.animation.slideInHorizontally
import androidx.compose.animation.slideInVertically
import androidx.compose.animation.slideOutHorizontally
import androidx.compose.animation.slideOutVertically
import androidx.compose.animation.togetherWith
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.gestures.detectTapGestures
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.WindowInsets
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.navigationBars
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.statusBars
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.layout.windowInsetsPadding
import androidx.compose.foundation.lazy.grid.GridCells
import androidx.compose.foundation.lazy.grid.GridItemSpan
import androidx.compose.foundation.lazy.grid.LazyGridState
import androidx.compose.foundation.lazy.grid.LazyVerticalGrid
import androidx.compose.foundation.lazy.grid.rememberLazyGridState
import androidx.compose.foundation.lazy.grid.items
import androidx.compose.foundation.pager.HorizontalPager
import androidx.compose.foundation.pager.PagerState
import androidx.compose.foundation.pager.rememberPagerState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.BasicTextField
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Search
import androidx.compose.material.icons.rounded.AddCircle
import androidx.compose.material.icons.rounded.ChevronLeft
import androidx.compose.material.icons.rounded.Close
import androidx.compose.material3.Icon
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.SolidColor
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.zfl.launcher.data.WidgetEntry
import com.zfl.launcher.data.WidgetKind
import com.zfl.launcher.data.WidgetSize
import com.zfl.launcher.ui.shape.ContinuousRoundedShape
import com.zfl.launcher.ui.theme.Tokens
import com.zfl.launcher.R

private val SheetColor = Color(0xFF242426)
private val FieldColor = Color(0xFF3A3A3C)
private val SheetShape = RoundedCornerShape(topStart = 34.dp, topEnd = 34.dp)

/** Widgets shown as big previews at the top of the gallery, like iOS27L. */
private val Featured = listOf(
    WidgetKind.Clock to WidgetSize.Small,
    WidgetKind.Calendar to WidgetSize.Small,
    WidgetKind.Weather to WidgetSize.Small,
    WidgetKind.Photo to WidgetSize.Small,
    WidgetKind.Battery to WidgetSize.Small,
    WidgetKind.Suggestions to WidgetSize.Medium,
)

/**
 * The "Add Widget" sheet: search, featured previews and the list of widgets; picking one
 * opens its sizes (swipe between them) with an "Add Widget" button.
 */
@Composable
fun WidgetGallery(
    visible: Boolean,
    appWidgets: List<WidgetProviderGroup>,
    onAdd: (WidgetKind, WidgetSize) -> Unit,
    onAddApp: (AppWidgetProviderInfo) -> Unit,
    onClose: () -> Unit,
) {
    // A built-in kind (WidgetKind) or an app with widgets (WidgetProviderGroup); null = the list.
    var selected by remember { mutableStateOf<Any?>(null) }
    // Kept here, not in the list, so coming back from a widget returns to the same spot.
    val listState = rememberLazyGridState()
    var query by remember { mutableStateOf("") }
    BackHandler(enabled = visible) { if (selected != null) selected = null else onClose() }
    AnimatedVisibility(visible, enter = fadeIn(), exit = fadeOut()) {
        Box(
            Modifier
                .fillMaxSize()
                .background(Color.Black.copy(alpha = 0.4f))
                .pointerInput(Unit) { detectTapGestures { onClose() } },
        )
    }
    AnimatedVisibility(
        visible,
        enter = slideInVertically(spring(dampingRatio = 0.9f, stiffness = 400f)) { it },
        exit = slideOutVertically { it },
        modifier = Modifier.windowInsetsPadding(WindowInsets.statusBars),
    ) {
        Column(
            Modifier
                .fillMaxSize()
                .padding(top = 56.dp)
                .clip(SheetShape)
                .background(SheetColor)
                .pointerInput(Unit) { detectTapGestures { } }
                .windowInsetsPadding(WindowInsets.navigationBars),
        ) {
            Box(Modifier.fillMaxWidth().padding(top = 8.dp), contentAlignment = Alignment.Center) {
                Box(Modifier.size(36.dp, 5.dp).background(Color.White.copy(alpha = 0.3f), CircleShape))
            }
            AnimatedContent(
                targetState = selected,
                transitionSpec = {
                    if (targetState != null) slideInHorizontally { it } togetherWith slideOutHorizontally { -it / 4 } + fadeOut()
                    else slideInHorizontally { -it / 4 } + fadeIn() togetherWith slideOutHorizontally { it }
                },
                label = "gallery",
            ) { selection ->
                when (selection) {
                    is WidgetKind -> KindDetail(selection, onAdd = { size -> selected = null; onAdd(selection, size) }, onBack = { selected = null }, onClose = onClose)
                    is WidgetProviderGroup -> AppWidgetsDetail(selection, onAdd = { info -> selected = null; onAddApp(info) }, onBack = { selected = null }, onClose = onClose)
                    else -> GalleryHome(appWidgets, listState, query, onQueryChange = { query = it }, onPick = { selected = it })
                }
            }
        }
    }
    // Reopening the gallery starts from its first screen.
    LaunchedEffect(visible) {
        if (!visible) {
            selected = null
            query = ""
            listState.scrollToItem(0)
        }
    }
}

@Composable
private fun GalleryHome(
    appWidgets: List<WidgetProviderGroup>,
    listState: LazyGridState,
    query: String,
    onQueryChange: (String) -> Unit,
    onPick: (Any) -> Unit,
) {
    val titles = WidgetKind.entries.associateWith { stringResource(it.titleRes) }
    val matching = WidgetKind.entries.filter { query.isBlank() || titles.getValue(it).contains(query.trim(), ignoreCase = true) }
    LazyVerticalGrid(
        columns = GridCells.Fixed(2),
        state = listState,
        contentPadding = PaddingValues(start = 20.dp, end = 20.dp, top = 12.dp, bottom = 24.dp),
        horizontalArrangement = Arrangement.spacedBy(18.dp),
        verticalArrangement = Arrangement.spacedBy(18.dp),
    ) {
        item(span = { GridItemSpan(2) }) { SearchField(query, onQueryChange) }
        if (query.isBlank()) {
            items(Featured, span = { (_, size) -> GridItemSpan(if (size == WidgetSize.Small) 1 else 2) }) { (kind, size) ->
                FeaturedPreview(kind, size, onClick = { onPick(kind) })
            }
        }
        items(matching, span = { GridItemSpan(2) }) { kind -> KindRow(kind, titles.getValue(kind), onClick = { onPick(kind) }) }
        // Then every app that offers widgets, as in iOS27L.
        val apps = appWidgets.filter { query.isBlank() || it.label.contains(query.trim(), ignoreCase = true) }
        items(apps, key = { it.packageName }, span = { GridItemSpan(2) }) { group -> AppWidgetsRow(group, onClick = { onPick(group) }) }
    }
}

@Composable
private fun SearchField(query: String, onChange: (String) -> Unit) {
    Row(
        Modifier
            .fillMaxWidth()
            .height(42.dp)
            .background(FieldColor, CircleShape)
            .padding(horizontal = 14.dp),
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.Center,
    ) {
        Icon(Icons.Filled.Search, null, tint = Tokens.TextSecondary, modifier = Modifier.size(20.dp))
        Spacer(Modifier.width(8.dp))
        Box(Modifier.weight(1f, fill = query.isNotEmpty())) {
            if (query.isEmpty()) Text(stringResource(R.string.widget_gallery_search), color = Tokens.TextSecondary, fontSize = 16.sp)
            BasicTextField(
                value = query,
                onValueChange = onChange,
                singleLine = true,
                textStyle = TextStyle(color = Tokens.TextPrimary, fontSize = 16.sp),
                cursorBrush = SolidColor(Tokens.Accent),
                modifier = Modifier.fillMaxWidth(),
            )
        }
    }
}

/** A real, live widget at its gallery size, with its name and variant under it. */
@Composable
private fun FeaturedPreview(kind: WidgetKind, size: WidgetSize, onClick: () -> Unit) {
    Column(
        Modifier.clickable(interactionSource = null, indication = null, onClick = onClick),
        horizontalAlignment = Alignment.CenterHorizontally,
    ) {
        val height = 160.dp
        val width = if (size == WidgetSize.Small) height else height * size.designSize.width.value / size.designSize.height.value
        WidgetBody(previewEntry(kind, size), Modifier.size(width, height))
        Spacer(Modifier.height(8.dp))
        Text(stringResource(kind.titleRes), color = Tokens.TextPrimary, fontSize = 13.sp, fontWeight = FontWeight.SemiBold)
        Text(stringResource(variantTitleRes(kind, size)), color = Tokens.TextSecondary, fontSize = 11.sp)
    }
}

@Composable
private fun KindRow(kind: WidgetKind, title: String, onClick: () -> Unit) = GalleryRow({ KindIcon(kind, 52.dp) }, title, onClick)

/** The sizes of one widget, swiped like iOS, and the button that adds the shown one. */
@Composable
private fun KindDetail(kind: WidgetKind, onAdd: (WidgetSize) -> Unit, onBack: () -> Unit, onClose: () -> Unit) {
    val pager = rememberPagerState { kind.sizes.size }
    val size = kind.sizes[pager.currentPage]
    GalleryDetail(
        icon = { KindIcon(kind, 40.dp) },
        name = stringResource(kind.titleRes),
        title = stringResource(variantTitleRes(kind, size)),
        description = stringResource(kind.descriptionRes),
        pager = pager,
        onAdd = { onAdd(size) },
        onBack = onBack,
        onClose = onClose,
    ) { page ->
        val design = kind.sizes[page].designSize
        WidgetBody(previewEntry(kind, kind.sizes[page]), Modifier.size(design.width * 0.95f, design.height * 0.95f))
    }
}

/**
 * Detail screen of the gallery: header with "back to the list", the icon, name and close button, the shown
 * variant's title and description, a pager of previews, page dots and "Add Widget".
 */
@Composable
internal fun GalleryDetail(
    icon: @Composable () -> Unit,
    name: String,
    title: String,
    description: String,
    pager: PagerState,
    onAdd: () -> Unit,
    onBack: () -> Unit,
    onClose: () -> Unit,
    preview: @Composable (page: Int) -> Unit,
) {
    Column(Modifier.fillMaxSize().padding(horizontal = 20.dp)) {
        Row(Modifier.fillMaxWidth().padding(top = 12.dp), verticalAlignment = Alignment.CenterVertically) {
            Box(
                Modifier
                    .size(30.dp)
                    .background(FieldColor, CircleShape)
                    .clickable(onClick = onBack),
                contentAlignment = Alignment.Center,
            ) { Icon(Icons.Rounded.ChevronLeft, stringResource(R.string.action_back), tint = Tokens.TextSecondary, modifier = Modifier.size(22.dp)) }
            Spacer(Modifier.width(12.dp))
            icon()
            Spacer(Modifier.width(14.dp))
            Text(name, color = Tokens.TextPrimary, fontSize = 18.sp, fontWeight = FontWeight.SemiBold, modifier = Modifier.weight(1f))
            Box(
                Modifier
                    .size(30.dp)
                    .background(FieldColor, CircleShape)
                    .clickable(onClick = onClose),
                contentAlignment = Alignment.Center,
            ) { Icon(Icons.Rounded.Close, stringResource(R.string.action_cancel), tint = Tokens.TextSecondary, modifier = Modifier.size(18.dp)) }
        }
        Text(
            title,
            color = Tokens.TextPrimary,
            fontSize = 26.sp,
            fontWeight = FontWeight.SemiBold,
            textAlign = TextAlign.Center,
            modifier = Modifier.fillMaxWidth().padding(top = 28.dp),
        )
        Text(
            description,
            color = Tokens.TextSecondary,
            fontSize = 13.sp,
            textAlign = TextAlign.Center,
            modifier = Modifier.fillMaxWidth().padding(top = 8.dp),
        )
        HorizontalPager(pager, modifier = Modifier.weight(1f).fillMaxWidth()) { page ->
            Box(Modifier.fillMaxSize(), contentAlignment = Alignment.Center) { preview(page) }
        }
        Row(Modifier.fillMaxWidth().padding(bottom = 16.dp), horizontalArrangement = Arrangement.Center) {
            repeat(pager.pageCount) { i ->
                Box(
                    Modifier
                        .padding(horizontal = 4.dp)
                        .size(7.dp)
                        .background(Color.White.copy(alpha = if (i == pager.currentPage) 0.9f else 0.3f), CircleShape),
                )
            }
        }
        Row(
            Modifier
                .fillMaxWidth()
                .padding(bottom = 16.dp)
                .height(52.dp)
                .clip(ContinuousRoundedShape(18.dp))
                .background(Tokens.Accent)
                .clickable(onClick = onAdd),
            horizontalArrangement = Arrangement.Center,
            verticalAlignment = Alignment.CenterVertically,
        ) {
            Icon(Icons.Rounded.AddCircle, null, tint = Color.White, modifier = Modifier.size(20.dp))
            Spacer(Modifier.width(8.dp))
            Text(stringResource(R.string.widget_add), color = Color.White, fontSize = 16.sp, fontWeight = FontWeight.SemiBold)
        }
    }
}

/** A row of the gallery list: an icon, a name and a hairline divider. */
@Composable
internal fun GalleryRow(icon: @Composable () -> Unit, title: String, onClick: () -> Unit) {
    Column(Modifier.clickable(onClick = onClick)) {
        Row(Modifier.fillMaxWidth().padding(vertical = 10.dp), verticalAlignment = Alignment.CenterVertically) {
            icon()
            Spacer(Modifier.width(16.dp))
            Text(title, color = Tokens.TextPrimary, fontSize = 17.sp)
        }
        Box(Modifier.padding(start = 68.dp).fillMaxWidth().height(0.5.dp).background(Color.White.copy(alpha = 0.12f)))
    }
}

private fun previewEntry(kind: WidgetKind, size: WidgetSize) = WidgetEntry("preview-${kind.name}-${size.name}", kind, size, photo = null)
