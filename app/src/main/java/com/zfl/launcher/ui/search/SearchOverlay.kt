package com.zfl.launcher.ui.search

import androidx.activity.compose.BackHandler
import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.animation.slideInVertically
import androidx.compose.animation.slideOutVertically
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
import androidx.compose.foundation.layout.imePadding
import androidx.compose.foundation.layout.navigationBars
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.statusBars
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.layout.windowInsetsPadding
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.lazy.rememberLazyListState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.text.BasicTextField
import androidx.compose.foundation.text.KeyboardActions
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Search
import androidx.compose.material.icons.rounded.Cancel
import androidx.compose.material.icons.rounded.Public
import androidx.compose.material3.Icon
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.focus.FocusRequester
import androidx.compose.ui.focus.focusRequester
import androidx.compose.ui.geometry.Rect
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.SolidColor
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.layout.boundsInWindow
import androidx.compose.ui.layout.onGloballyPositioned
import androidx.compose.ui.platform.LocalSoftwareKeyboardController
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.ImeAction
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.zfl.launcher.ui.theme.LocalAccent
import com.zfl.launcher.R
import com.zfl.launcher.SearchMode
import com.zfl.launcher.data.AppInfo
import com.zfl.launcher.ui.glass.GlassProfile
import com.zfl.launcher.ui.glass.glass
import com.zfl.launcher.ui.icon.AppIconImage
import com.zfl.launcher.ui.icon.IconLabel
import com.zfl.launcher.ui.shape.ContinuousRoundedShape
import com.zfl.launcher.ui.theme.Tokens
import kotlinx.coroutines.launch
import java.util.Locale
import com.zfl.launcher.ui.theme.Motion

/** What search reports back. */
interface SearchCallbacks {
    fun onLaunch(app: AppInfo, bounds: Rect)
    fun onLongPress(app: AppInfo, bounds: Rect)
    fun onWebSearch(query: String)
    fun onClose()
}

/**
 * Search over the blurred Home Screen, in one of two looks from iOS27L:
 * - [SearchMode.Spotlight] (home screen swipe): suggestions on top, the field above the keyboard;
 * - [SearchMode.Library] (App Library pill): field on top and every app A–Z with a letter index.
 * Matching is local and instant; Enter opens the best match.
 */
@Composable
fun SearchOverlay(
    mode: SearchMode?,
    apps: List<AppInfo>,
    suggestions: List<AppInfo>,
    callbacks: SearchCallbacks,
) {
    BackHandler(enabled = mode != null, onBack = callbacks::onClose)
    var lastMode by remember { mutableStateOf(mode) }
    if (mode != null) lastMode = mode
    AnimatedVisibility(
        visible = mode != null,
        enter = fadeIn(Motion.fade()) + slideInVertically(Motion.expand()) { -it / 12 },
        exit = fadeOut(Motion.fade()) + slideOutVertically(Motion.expand()) { -it / 12 },
    ) {
        var query by remember { mutableStateOf("") }
        val results = remember(query, apps) { search(apps, query) }
        Box(
            Modifier
                .fillMaxSize()
                .background(Color.Black.copy(alpha = 0.45f))
                .pointerInput(Unit) { detectTapGestures { callbacks.onClose() } }
                .windowInsetsPadding(WindowInsets.statusBars)
                .windowInsetsPadding(WindowInsets.navigationBars)
                .imePadding(),
        ) {
            when (lastMode) {
                SearchMode.Library -> LibrarySearch(query, { query = it }, results, callbacks)
                else -> SpotlightSearch(query, { query = it }, results, suggestions, callbacks)
            }
        }
    }
}

// region Spotlight

@Composable
private fun SpotlightSearch(
    query: String,
    onQueryChange: (String) -> Unit,
    results: List<AppInfo>,
    suggestions: List<AppInfo>,
    callbacks: SearchCallbacks,
) {
    var showMore by remember { mutableStateOf(false) }
    Column(Modifier.fillMaxSize().padding(horizontal = 16.dp)) {
        Spacer(Modifier.height(24.dp))
        if (query.isBlank()) {
            if (suggestions.isNotEmpty()) {
                Row(Modifier.fillMaxWidth().padding(horizontal = 8.dp, vertical = 10.dp)) {
                    Text(stringResource(R.string.search_suggestions), color = Tokens.TextSecondary, fontSize = 17.sp, fontWeight = FontWeight.SemiBold, modifier = Modifier.weight(1f))
                    if (suggestions.size > SUGGESTIONS_ROW) {
                        Text(
                            stringResource(if (showMore) R.string.search_show_less else R.string.search_show_more),
                            color = Tokens.TextSecondary,
                            fontSize = 17.sp,
                            fontWeight = FontWeight.SemiBold,
                            modifier = Modifier.clickable(interactionSource = null, indication = null) { showMore = !showMore },
                        )
                    }
                }
                SuggestionsCard(suggestions.take(if (showMore) SUGGESTIONS_ROW * 2 else SUGGESTIONS_ROW), callbacks)
            }
        } else {
            ResultsCard(query, results.take(MAX_SPOTLIGHT_RESULTS), callbacks)
        }
        Spacer(Modifier.weight(1f))
        SearchField(
            query = query,
            onQueryChange = onQueryChange,
            hint = stringResource(R.string.search_hint),
            onSubmit = { submit(query, results, callbacks) },
            modifier = Modifier.padding(bottom = 12.dp),
        )
    }
}

@Composable
private fun SuggestionsCard(apps: List<AppInfo>, callbacks: SearchCallbacks) {
    Column(
        Modifier
            .fillMaxWidth()
            .glass(ContinuousRoundedShape(28.dp))
            .consumeTaps()
            .padding(vertical = 16.dp),
        verticalArrangement = Arrangement.spacedBy(14.dp),
    ) {
        apps.chunked(SUGGESTIONS_ROW).forEach { row ->
            Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceEvenly) {
                row.forEach { app ->
                    Column(Modifier.width(Tokens.IconSize + 16.dp), horizontalAlignment = Alignment.CenterHorizontally) {
                        TappableIcon(app, 62.dp, callbacks)
                        Spacer(Modifier.height(6.dp))
                        IconLabel(app.label)
                    }
                }
                repeat(SUGGESTIONS_ROW - row.size) { Spacer(Modifier.width(Tokens.IconSize + 16.dp)) }
            }
        }
    }
}

@Composable
private fun ResultsCard(query: String, results: List<AppInfo>, callbacks: SearchCallbacks) {
    Column(
        Modifier
            .fillMaxWidth()
            .glass(ContinuousRoundedShape(24.dp))
            .consumeTaps()
            .padding(vertical = 6.dp),
    ) {
        results.forEach { app -> ResultRow(app, callbacks) }
        // Always offer a web search for the query, handled by the user's browser.
        Row(
            Modifier
                .fillMaxWidth()
                .clickable { callbacks.onWebSearch(query) }
                .padding(horizontal = 16.dp, vertical = 10.dp),
            verticalAlignment = Alignment.CenterVertically,
        ) {
            Box(Modifier.size(40.dp), contentAlignment = Alignment.Center) {
                Icon(Icons.Rounded.Public, contentDescription = null, tint = Tokens.TextPrimary, modifier = Modifier.size(26.dp))
            }
            Spacer(Modifier.width(14.dp))
            Text(
                stringResource(R.string.search_web, query),
                color = Tokens.TextPrimary,
                fontSize = 16.sp,
                maxLines = 1,
                overflow = TextOverflow.Ellipsis,
            )
        }
    }
}

// endregion

// region App Library search

@Composable
private fun LibrarySearch(query: String, onQueryChange: (String) -> Unit, results: List<AppInfo>, callbacks: SearchCallbacks) {
    val listState = rememberLazyListState()
    val scope = rememberCoroutineScope()
    // A–Z sections; digits and symbols go under "#".
    val sections = remember(results) { results.groupBy { sectionOf(it.label) } }
    val sectionStart = remember(sections) {
        var index = 0
        sections.mapValues { (_, apps) -> index.also { index += 1 + apps.size } }
    }
    Column(Modifier.fillMaxSize()) {
        Row(Modifier.padding(start = 16.dp, end = 8.dp, top = 12.dp), verticalAlignment = Alignment.CenterVertically) {
            SearchField(
                query = query,
                onQueryChange = onQueryChange,
                hint = stringResource(R.string.library_title),
                onSubmit = { submit(query, results, callbacks) },
                modifier = Modifier.weight(1f),
            )
            Text(
                stringResource(R.string.action_cancel),
                color = Tokens.TextPrimary,
                fontSize = 16.sp,
                modifier = Modifier
                    .clickable(interactionSource = null, indication = null, onClick = callbacks::onClose)
                    .padding(horizontal = 12.dp, vertical = 10.dp),
            )
        }
        Box(Modifier.weight(1f).consumeTaps()) {
            LazyColumn(state = listState, contentPadding = PaddingValues(start = 16.dp, end = 32.dp, top = 8.dp, bottom = 16.dp)) {
                sections.forEach { (letter, apps) ->
                    item(key = "header-$letter") {
                        Text(letter, color = Tokens.TextPrimary, fontSize = 17.sp, fontWeight = FontWeight.Bold, modifier = Modifier.padding(start = 6.dp, top = 12.dp, bottom = 4.dp))
                    }
                    items(apps, key = { it.key }) { app -> ResultRow(app, callbacks) }
                }
            }
            // Letter index on the right edge: tap a letter to jump to its section.
            Column(
                Modifier
                    .align(Alignment.CenterEnd)
                    .padding(end = 6.dp),
                horizontalAlignment = Alignment.CenterHorizontally,
            ) {
                sections.keys.forEach { letter ->
                    Text(
                        letter,
                        color = LocalAccent.current.primary,
                        fontSize = 11.sp,
                        fontWeight = FontWeight.SemiBold,
                        modifier = Modifier
                            .clickable(interactionSource = null, indication = null) {
                                scope.launch { listState.scrollToItem(sectionStart.getValue(letter)) }
                            }
                            .padding(horizontal = 6.dp, vertical = 1.dp),
                    )
                }
            }
        }
    }
}

// endregion

// region Shared parts

@Composable
private fun SearchField(
    query: String,
    onQueryChange: (String) -> Unit,
    hint: String,
    onSubmit: () -> Unit,
    modifier: Modifier = Modifier,
) {
    val focus = remember { FocusRequester() }
    val keyboard = LocalSoftwareKeyboardController.current
    LaunchedEffect(Unit) {
        focus.requestFocus()
        keyboard?.show()
    }
    Row(
        modifier
            .fillMaxWidth()
            .height(50.dp)
            .glass(CircleShape, profile = GlassProfile.Button)
            .consumeTaps()
            .padding(horizontal = 16.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Icon(Icons.Filled.Search, contentDescription = null, tint = Tokens.TextSecondary, modifier = Modifier.size(22.dp))
        Spacer(Modifier.width(10.dp))
        Box(Modifier.weight(1f), contentAlignment = Alignment.CenterStart) {
            if (query.isEmpty()) Text(hint, color = Tokens.TextSecondary, fontSize = 17.sp)
            BasicTextField(
                value = query,
                onValueChange = onQueryChange,
                singleLine = true,
                textStyle = TextStyle(color = Tokens.TextPrimary, fontSize = 17.sp),
                cursorBrush = SolidColor(LocalAccent.current.primary),
                keyboardOptions = KeyboardOptions(imeAction = ImeAction.Search),
                keyboardActions = KeyboardActions(onSearch = { onSubmit() }),
                modifier = Modifier
                    .fillMaxWidth()
                    .focusRequester(focus),
            )
        }
        if (query.isNotEmpty()) {
            Icon(
                Icons.Rounded.Cancel,
                contentDescription = null,
                tint = Tokens.TextSecondary,
                modifier = Modifier
                    .size(20.dp)
                    .clickable(interactionSource = null, indication = null) { onQueryChange("") },
            )
        }
    }
}

@Composable
private fun ResultRow(app: AppInfo, callbacks: SearchCallbacks) {
    var bounds by remember { mutableStateOf(Rect.Zero) }
    Row(
        Modifier
            .fillMaxWidth()
            .pointerInput(app.key) {
                detectTapGestures(
                    onTap = { callbacks.onLaunch(app, bounds) },
                    onLongPress = { callbacks.onLongPress(app, bounds) },
                )
            }
            .padding(horizontal = 10.dp, vertical = 8.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        AppIconImage(app, size = 44.dp, modifier = Modifier.onGloballyPositioned { bounds = it.boundsInWindow() })
        Spacer(Modifier.width(16.dp))
        Text(app.label, color = Tokens.TextPrimary, fontSize = 17.sp, maxLines = 1, overflow = TextOverflow.Ellipsis)
    }
}

@Composable
private fun TappableIcon(app: AppInfo, size: androidx.compose.ui.unit.Dp, callbacks: SearchCallbacks) {
    var bounds by remember { mutableStateOf(Rect.Zero) }
    AppIconImage(
        app = app,
        size = size,
        modifier = Modifier
            .onGloballyPositioned { bounds = it.boundsInWindow() }
            .pointerInput(app.key) {
                detectTapGestures(
                    onTap = { callbacks.onLaunch(app, bounds) },
                    onLongPress = { callbacks.onLongPress(app, bounds) },
                )
            },
    )
}

/** Stops taps on content from reaching the "tap outside to close" handler behind it. */
private fun Modifier.consumeTaps(): Modifier = pointerInput(Unit) { detectTapGestures { } }

private fun submit(query: String, results: List<AppInfo>, callbacks: SearchCallbacks) {
    when {
        results.isNotEmpty() && query.isNotBlank() -> callbacks.onLaunch(results.first(), Rect.Zero)
        query.isNotBlank() -> callbacks.onWebSearch(query)
    }
}

/**
 * Apps matching [query], best first: label starts with it, then a word starts with it,
 * then it appears anywhere. An empty query returns every app A–Z.
 */
internal fun search(apps: List<AppInfo>, query: String): List<AppInfo> {
    val sorted = apps.sortedBy { it.label.lowercase(Locale.getDefault()) }
    val q = query.trim().lowercase(Locale.getDefault())
    if (q.isEmpty()) return sorted
    return sorted
        .mapNotNull { app ->
            val label = app.label.lowercase(Locale.getDefault())
            // Also match the Latin spelling of Cyrillic names: "tel" finds «Телефон».
            val rank = listOf(label, transliterate(label)).minOf { name ->
                when {
                    name.startsWith(q) -> 0
                    name.split(' ', '-', '.', '_').any { it.startsWith(q) } -> 1
                    q in name -> 2
                    else -> Int.MAX_VALUE
                }
            }
            if (rank == Int.MAX_VALUE) null else rank to app
        }
        .sortedBy { it.first }
        .map { it.second }
}

private val CyrillicToLatin: Map<Char, String> = mapOf(
    'а' to "a", 'б' to "b", 'в' to "v", 'г' to "g", 'д' to "d", 'е' to "e", 'ё' to "e", 'ж' to "zh",
    'з' to "z", 'и' to "i", 'й' to "y", 'к' to "k", 'л' to "l", 'м' to "m", 'н' to "n", 'о' to "o",
    'п' to "p", 'р' to "r", 'с' to "s", 'т' to "t", 'у' to "u", 'ф' to "f", 'х' to "h", 'ц' to "ts",
    'ч' to "ch", 'ш' to "sh", 'щ' to "sch", 'ъ' to "", 'ы' to "y", 'ь' to "", 'э' to "e", 'ю' to "yu", 'я' to "ya",
)

/** Lower-case Cyrillic → Latin, other characters unchanged. */
private fun transliterate(text: String): String = buildString {
    text.forEach { c -> append(CyrillicToLatin[c] ?: c.toString()) }
}

private fun sectionOf(label: String): String {
    val first = label.trim().firstOrNull()?.uppercaseChar() ?: return "#"
    return if (first.isLetter()) first.toString() else "#"
}

private const val SUGGESTIONS_ROW = 4
private const val MAX_SPOTLIGHT_RESULTS = 6

// endregion
