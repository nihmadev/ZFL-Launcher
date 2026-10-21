package com.zfl.launcher.ui.library

import androidx.compose.foundation.gestures.awaitEachGesture
import androidx.compose.foundation.gestures.awaitFirstDown
import androidx.compose.foundation.gestures.detectTapGestures
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.LazyListScope
import androidx.compose.foundation.lazy.LazyListState
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.lazy.rememberLazyListState
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.rememberUpdatedState
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.layout.onGloballyPositioned
import com.zfl.launcher.ui.theme.launcherHaptics
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.zfl.launcher.data.AppInfo
import com.zfl.launcher.data.LetterSection
import com.zfl.launcher.ui.icon.AppIconImage
import com.zfl.launcher.ui.icon.IconBadge
import com.zfl.launcher.ui.theme.Haptics
import com.zfl.launcher.ui.theme.LocalAccent
import com.zfl.launcher.ui.theme.Tokens
import kotlinx.coroutines.launch

/**
 * Apps under letter headers with an index on the end edge: tap a letter, or slide along the
 * index, to jump to its section. Used by the App Library List view and the App Library search.
 *
 * @param leading items above the sections (headers, pinned apps); [leadingCount] of them.
 * @param keyPrefix namespace of the app items, so the same app can also appear in [leading].
 */
@Composable
internal fun LetterIndexedList(
    sections: List<LetterSection<AppInfo>>,
    callbacks: LibraryCallbacks,
    modifier: Modifier = Modifier,
    state: LazyListState = rememberLazyListState(),
    contentPadding: PaddingValues = PaddingValues(start = 16.dp, end = 32.dp, top = 8.dp, bottom = 16.dp),
    leadingCount: Int = 0,
    keyPrefix: String = "app",
    leading: LazyListScope.() -> Unit = {},
) {
    val scope = rememberCoroutineScope()
    val sectionStart = remember(sections, leadingCount) {
        var index = leadingCount
        sections.associate { section -> section.letter to index.also { index += 1 + section.apps.size } }
    }
    Box(modifier) {
        LazyColumn(state = state, contentPadding = contentPadding) {
            leading()
            sections.forEach { section ->
                item(key = "letter:${section.letter}", contentType = "letter") { LetterHeader(section.letter) }
                items(section.apps, key = { "$keyPrefix:${it.key}" }, contentType = { "app" }) { app ->
                    AppListRow(app, callbacks)
                }
            }
        }
        LetterIndex(
            letters = sections.map { it.letter },
            onLetter = { letter -> sectionStart[letter]?.let { scope.launch { state.scrollToItem(it) } } },
            modifier = Modifier.align(Alignment.CenterEnd).padding(end = 6.dp),
        )
    }
}

@Composable
internal fun LetterHeader(text: String) {
    Text(
        text,
        color = Tokens.TextPrimary,
        fontSize = 17.sp,
        fontWeight = FontWeight.Bold,
        modifier = Modifier.padding(start = 6.dp, top = 12.dp, bottom = 4.dp),
    )
}

/** The letters on the end edge; follows the finger while it slides along them. */
@Composable
private fun LetterIndex(letters: List<String>, onLetter: (String) -> Unit, modifier: Modifier = Modifier) {
    if (letters.size < 2) return
    val haptic = launcherHaptics()
    val currentLetters by rememberUpdatedState(letters)
    val currentOnLetter by rememberUpdatedState(onLetter)
    var height by remember { mutableStateOf(0) }
    Column(
        modifier
            .onGloballyPositioned { height = it.size.height }
            .pointerInput(Unit) {
                awaitEachGesture {
                    var last: String? = null
                    fun jump(y: Float) {
                        val all = currentLetters
                        if (height <= 0 || all.isEmpty()) return
                        val letter = all[(y / height * all.size).toInt().coerceIn(0, all.lastIndex)]
                        if (letter != last) {
                            last = letter
                            haptic.performHapticFeedback(Haptics.Tick)
                            currentOnLetter(letter)
                        }
                    }
                    val down = awaitFirstDown()
                    down.consume()
                    jump(down.position.y)
                    while (true) {
                        val event = awaitPointerEvent()
                        val change = event.changes.firstOrNull { it.id == down.id } ?: break
                        if (!change.pressed) break
                        change.consume()
                        jump(change.position.y)
                    }
                }
            },
        horizontalAlignment = Alignment.CenterHorizontally,
    ) {
        letters.forEach { letter ->
            Text(
                letter,
                color = LocalAccent.current.primary,
                fontSize = 11.sp,
                fontWeight = FontWeight.SemiBold,
                modifier = Modifier
                    .semantics { contentDescription = letter }
                    .padding(horizontal = 6.dp, vertical = 1.dp),
            )
        }
    }
}

/** An app as a list row: icon and name. Tap opens it, long-press shows its menu. */
@Composable
internal fun AppListRow(app: AppInfo, callbacks: LibraryCallbacks) {
    val haptic = launcherHaptics()
    val bounds = remember { WindowBounds() }
    val rowBounds = remember { WindowBounds() }
    val currentApp by rememberUpdatedState(app)
    Row(
        Modifier
            .fillMaxWidth()
            .then(rowBounds.modifier)
            .pointerInput(Unit) {
                detectTapGestures(
                    onTap = { callbacks.onLaunch(currentApp, bounds.current()) },
                    onLongPress = { position ->
                        haptic.performHapticFeedback(Haptics.Lift)
                        callbacks.onLongPress(currentApp, bounds.current(), rowBounds.current().topLeft + position)
                    },
                )
            }
            .padding(horizontal = 10.dp, vertical = 8.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        AppIconImage(app, size = ListIconSize, modifier = bounds.modifier, badge = IconBadge.Shown)
        Spacer(Modifier.width(16.dp))
        Text(app.label, color = Tokens.TextPrimary, fontSize = 17.sp, maxLines = 1, overflow = TextOverflow.Ellipsis)
    }
}

/** A small gray section title in the Grid and List views ("Pinned", "Suggestions"). */
@Composable
internal fun SectionTitle(text: String, modifier: Modifier = Modifier) {
    Text(
        text,
        color = Tokens.TextSecondary,
        fontSize = 15.sp,
        fontWeight = FontWeight.SemiBold,
        modifier = modifier.fillMaxWidth().padding(start = 6.dp, top = 14.dp, bottom = 6.dp),
    )
}

private val ListIconSize = 44.dp

