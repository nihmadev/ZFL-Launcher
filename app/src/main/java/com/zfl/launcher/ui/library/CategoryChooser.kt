package com.zfl.launcher.ui.library

import androidx.activity.compose.BackHandler
import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.animation.scaleIn
import androidx.compose.animation.scaleOut
import androidx.compose.foundation.background
import androidx.compose.foundation.selection.selectable
import androidx.compose.foundation.gestures.detectTapGestures
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.WindowInsets
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.navigationBars
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.statusBars
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.layout.widthIn
import androidx.compose.foundation.layout.windowInsetsPadding
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.rounded.Check
import androidx.compose.material3.Icon
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.zfl.launcher.R
import com.zfl.launcher.data.AppInfo
import com.zfl.launcher.data.AppLibraryState
import com.zfl.launcher.ui.icon.AppIconImage
import com.zfl.launcher.ui.shape.ContinuousRoundedShape
import com.zfl.launcher.ui.theme.LocalAccent
import com.zfl.launcher.ui.theme.Motion
import com.zfl.launcher.ui.theme.Tokens

/**
 * "Category" from the long-press menu of an App Library app: Automatic (its system category)
 * or any category, user ones included. Picking one assigns it right away; [onAssign] gets
 * null for Automatic. New categories are made in Settings › App Library.
 */
@Composable
fun CategoryChooser(app: AppInfo?, library: AppLibraryState?, onAssign: (AppInfo, String?) -> Unit, onDismiss: () -> Unit) {
    var last by remember { mutableStateOf(app) }
    if (app != null) last = app
    BackHandler(enabled = app != null, onBack = onDismiss)
    AnimatedVisibility(
        visible = app != null && library != null,
        enter = fadeIn(Motion.fade()) + scaleIn(Motion.pop(), initialScale = 0.9f),
        exit = fadeOut(Motion.fade()) + scaleOut(Motion.pop(), targetScale = 0.9f),
    ) {
        val shown = last ?: return@AnimatedVisibility
        val state = library ?: return@AnimatedVisibility
        val current = state.groupOf[shown.key]
        val auto = state.autoGroup(shown.key)
        val assigned = shown.key in state.autoGroupOf
        Box(
            Modifier
                .fillMaxSize()
                .background(Color.Black.copy(alpha = 0.35f))
                .pointerInput(Unit) { detectTapGestures { onDismiss() } }
                .windowInsetsPadding(WindowInsets.statusBars)
                .windowInsetsPadding(WindowInsets.navigationBars),
            contentAlignment = Alignment.Center,
        ) {
            Column(
                Modifier
                    .widthIn(max = Tokens.MenuWidth + 60.dp)
                    .fillMaxWidth()
                    .padding(horizontal = 24.dp)
                    .background(Tokens.MenuBackground, ChooserShape)
                    .pointerInput(Unit) { detectTapGestures { } }
                    .padding(vertical = Tokens.MenuVerticalPadding),
            ) {
                Row(Modifier.padding(horizontal = 16.dp, vertical = 10.dp), verticalAlignment = Alignment.CenterVertically) {
                    AppIconImage(shown, size = 36.dp)
                    Spacer(Modifier.width(12.dp))
                    Text(
                        stringResource(R.string.library_choose_category),
                        color = Tokens.TextPrimary,
                        fontSize = 17.sp,
                        fontWeight = FontWeight.SemiBold,
                        maxLines = 1,
                        overflow = TextOverflow.Ellipsis,
                    )
                }
                LazyColumn(Modifier.heightIn(max = 420.dp)) {
                    item(key = "auto") {
                        ChooserRow(
                            title = stringResource(R.string.library_category_automatic),
                            subtitle = auto?.let { state.summary(it)?.title },
                            selected = !assigned,
                        ) { onAssign(shown, null) }
                    }
                    items(state.allGroups, key = { it.id }) { group ->
                        ChooserRow(group.title, subtitle = null, selected = assigned && group.id == current) {
                            onAssign(shown, group.id.takeIf { it != auto })
                        }
                    }
                }
            }
        }
    }
}

@Composable
private fun ChooserRow(title: String, subtitle: String?, selected: Boolean, onClick: () -> Unit) {
    Row(
        Modifier
            .fillMaxWidth()
            .heightIn(min = Tokens.MenuItemHeight)
            .selectable(selected = selected, role = Role.RadioButton, onClick = onClick)
            .padding(horizontal = 16.dp, vertical = 6.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Column(Modifier.weight(1f)) {
            Text(title, color = Tokens.TextPrimary, fontSize = 17.sp, maxLines = 1, overflow = TextOverflow.Ellipsis)
            if (subtitle != null) Text(subtitle, color = Tokens.TextSecondary, fontSize = 13.sp, maxLines = 1)
        }
        if (selected) Icon(Icons.Rounded.Check, contentDescription = null, tint = LocalAccent.current.primary, modifier = Modifier.size(22.dp))
    }
}

private val ChooserShape = ContinuousRoundedShape(Tokens.MenuRadius)
