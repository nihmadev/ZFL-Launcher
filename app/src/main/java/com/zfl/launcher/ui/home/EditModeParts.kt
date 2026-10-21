package com.zfl.launcher.ui.home

import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.foundation.interaction.collectIsPressedAsState
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.rounded.Tune
import androidx.compose.material.icons.rounded.Widgets
import androidx.compose.material.icons.rounded.Wallpaper
import androidx.compose.material3.Icon
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.IntOffset
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.compose.ui.window.Dialog
import androidx.compose.ui.window.Popup
import androidx.compose.ui.window.PopupProperties
import com.zfl.launcher.R
import com.zfl.launcher.data.AppInfo
import com.zfl.launcher.ui.glass.GlassProfile
import com.zfl.launcher.ui.glass.glass
import com.zfl.launcher.ui.shape.ContinuousRoundedShape
import com.zfl.launcher.ui.shape.shapeBorder
import com.zfl.launcher.ui.theme.Tokens

/**
 * Bar shown at the top in edit mode (the status bar is hidden meanwhile):
 * "Edit" opens a small menu, "Done" leaves edit mode. As in iOS27L.
 */
@Composable
fun EditTopBar(
    onDone: () -> Unit,
    onAddWidget: () -> Unit,
    onCustomize: () -> Unit,
    onWallpapers: () -> Unit,
    modifier: Modifier = Modifier,
) {
    var menuOpen by remember { mutableStateOf(false) }
    Row(
        modifier
            .fillMaxWidth()
            .padding(horizontal = 20.dp),
        horizontalArrangement = Arrangement.SpaceBetween,
    ) {
        Box {
            GlassPill(stringResource(R.string.edit_edit), onClick = { menuOpen = true })
            if (menuOpen) {
                Popup(
                    offset = IntOffset(0, 120),
                    onDismissRequest = { menuOpen = false },
                    properties = PopupProperties(focusable = true),
                ) {
                    EditMenu(
                        onAddWidget = { menuOpen = false; onAddWidget() },
                        onCustomize = { menuOpen = false; onCustomize() },
                        onWallpapers = { menuOpen = false; onWallpapers() },
                    )
                }
            }
        }
        GlassPill(stringResource(R.string.edit_done), onClick = onDone)
    }
}

@Composable
private fun GlassPill(text: String, onClick: () -> Unit) {
    val interaction = remember { MutableInteractionSource() }
    val pressed by interaction.collectIsPressedAsState()
    Box(
        Modifier
            .height(38.dp)
            .width(96.dp)
            .glass(CircleShape, profile = GlassProfile.SmallButton)
            .clickable(interaction, indication = null, onClick = onClick),
        contentAlignment = Alignment.Center,
    ) {
        Text(
            text,
            color = if (pressed) Tokens.TextSecondary else Tokens.TextPrimary,
            fontSize = 15.sp,
            fontWeight = FontWeight.SemiBold,
        )
    }
}

@Composable
private fun EditMenu(onAddWidget: () -> Unit, onCustomize: () -> Unit, onWallpapers: () -> Unit) {
    val shape = ContinuousRoundedShape(Tokens.MenuRadius)
    Column(
        Modifier
            .width(220.dp)
            .clip(shape)
            .background(Tokens.MenuBackground)
            .shapeBorder(1.dp, Tokens.MenuStroke, shape)
            .padding(vertical = Tokens.MenuVerticalPadding),
    ) {
        EditMenuRow(Icons.Rounded.Widgets, stringResource(R.string.edit_add_widget), onAddWidget)
        EditMenuRow(Icons.Rounded.Tune, stringResource(R.string.edit_customize), onCustomize)
        EditMenuRow(Icons.Rounded.Wallpaper, stringResource(R.string.edit_wallpapers), onWallpapers)
    }
}

@Composable
private fun EditMenuRow(icon: ImageVector, text: String, onClick: () -> Unit) {
    Row(
        Modifier
            .fillMaxWidth()
            .height(Tokens.MenuItemHeight)
            .clickable(onClick = onClick)
            .padding(horizontal = 16.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Icon(icon, contentDescription = null, tint = Tokens.TextPrimary, modifier = Modifier.size(22.dp))
        Spacer(Modifier.width(14.dp))
        Text(text, color = Tokens.TextPrimary, fontSize = 16.sp)
    }
}

/**
 * Asked when the "−" badge is tapped, like iOS27L: remove from the home screen (the app
 * is hidden and can be restored in settings), uninstall, or cancel.
 */
@Composable
fun RemoveAppDialog(app: AppInfo, onRemoveFromHome: () -> Unit, onUninstall: () -> Unit, onDismiss: () -> Unit) {
    val shape = ContinuousRoundedShape(28.dp)
    Dialog(onDismissRequest = onDismiss) {
        Column(
            Modifier
                .width(320.dp)
                .clip(shape)
                .background(Color(0xF02C2C2E))
                .shapeBorder(1.dp, Color.White.copy(alpha = 0.12f), shape)
                .padding(18.dp),
            horizontalAlignment = Alignment.CenterHorizontally,
        ) {
            Text(
                stringResource(R.string.remove_dialog_title, app.label),
                color = Tokens.TextPrimary,
                fontSize = 18.sp,
                fontWeight = FontWeight.SemiBold,
                textAlign = TextAlign.Center,
            )
            Spacer(Modifier.height(8.dp))
            Text(
                stringResource(R.string.remove_dialog_message),
                color = Tokens.TextSecondary,
                fontSize = 14.sp,
                textAlign = TextAlign.Center,
            )
            Spacer(Modifier.height(18.dp))
            DialogPill(stringResource(R.string.remove_dialog_remove_from_home), Tokens.TextPrimary, onRemoveFromHome)
            Spacer(Modifier.height(10.dp))
            DialogPill(stringResource(R.string.remove_dialog_uninstall), Tokens.Destructive, onUninstall)
            Spacer(Modifier.height(10.dp))
            DialogPill(stringResource(R.string.action_cancel), Tokens.TextPrimary, onDismiss)
        }
    }
}

@Composable
private fun DialogPill(text: String, color: Color, onClick: () -> Unit) {
    Box(
        Modifier
            .fillMaxWidth()
            .height(48.dp)
            .clip(CircleShape)
            .background(Color.White.copy(alpha = 0.10f))
            .clickable(onClick = onClick),
        contentAlignment = Alignment.Center,
    ) {
        Text(text, color = color, fontSize = 15.sp, fontWeight = FontWeight.Medium)
    }
}
