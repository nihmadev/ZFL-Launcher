package com.zfl.launcher.ui.icon

import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.size
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.staticCompositionLocalOf
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.drawBehind
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.BlendMode
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.ColorFilter
import androidx.compose.ui.graphics.Shadow
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.role
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.IntSize
import com.zfl.launcher.data.AppInfo
import com.zfl.launcher.settings.LocalSettingsState
import com.zfl.launcher.ui.theme.Tokens
import kotlin.math.roundToInt

val LocalIconLoader = staticCompositionLocalOf<IconLoader> { error("IconLoader not provided") }

/**
 * Just the icon image (the bitmap is already cut to a squircle by [IconLoader]). Loads
 * asynchronously. [dimmed] darkens it the way iOS does while the icon is pressed.
 */
@Composable
fun AppIconImage(
    app: AppInfo,
    modifier: Modifier = Modifier,
    size: Dp = LocalSettingsState.current.iconSize,
    dimmed: Boolean = false,
) {
    val loader = LocalIconLoader.current
    // Changes with a custom icon, the icon size or shape, which triggers a reload.
    val cacheKey = loader.cacheKey(app)
    val bitmap = remember(app.key) { mutableStateOf(loader.cached(app)) }
    LaunchedEffect(cacheKey) {
        // The previous bitmap stays visible until the new one is ready: no blink on a restyle.
        bitmap.value = loader.cached(app) ?: loader.load(app)
    }
    val label = app.label
    Spacer(
        modifier
            .size(size)
            .semantics {
                contentDescription = label
                role = Role.Image
            }
            // The bitmap is read in the draw phase: loading it only redraws, never recomposes.
            .drawBehind {
                val image = bitmap.value ?: return@drawBehind
                drawImage(
                    image = image,
                    dstSize = IntSize(this.size.width.roundToInt(), this.size.height.roundToInt()),
                    // SrcAtop keeps the squircle's transparent corners untouched.
                    colorFilter = if (dimmed) PressedDim else null,
                )
            },
    )
}

private val PressedDim = ColorFilter.tint(Color.Black.copy(alpha = 0.3f), BlendMode.SrcAtop)

@Composable
fun IconLabel(text: String) {
    Text(
        text = text,
        style = TextStyle(
            color = Tokens.TextPrimary,
            fontSize = Tokens.LabelSize * LocalSettingsState.current.labelScale,
            textAlign = TextAlign.Center,
            shadow = Shadow(Color.Black.copy(alpha = 0.4f), Offset(0f, 1f), 4f),
        ),
        maxLines = 1,
        overflow = TextOverflow.Ellipsis,
    )
}
