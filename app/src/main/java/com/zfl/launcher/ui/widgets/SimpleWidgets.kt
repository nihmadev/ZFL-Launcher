package com.zfl.launcher.ui.widgets

import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent
import android.content.IntentFilter
import android.os.BatteryManager
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.Image
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.offset
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.rounded.AddPhotoAlternate
import androidx.compose.material.icons.rounded.Bolt
import androidx.compose.material.icons.rounded.Smartphone
import androidx.compose.material3.Icon
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.produceState
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.ImageBitmap
import androidx.compose.ui.graphics.StrokeCap
import androidx.compose.ui.graphics.asImageBitmap
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.zfl.launcher.R
import com.zfl.launcher.data.WidgetEntry
import com.zfl.launcher.data.WidgetPhotos
import com.zfl.launcher.data.WidgetSize
import com.zfl.launcher.ui.icon.AppIconImage

// region Battery

private val BatteryGreen = Color(0xFF30D158)
private val BatteryRed = Color(0xFFFF453A)
private val RingTrack = Color(0xFF8E8E93)

/** Battery level (0..100) and charging state, from the sticky battery broadcast. */
private data class BatteryStatus(val level: Int, val charging: Boolean)

@Composable
private fun rememberBattery(): BatteryStatus {
    val context = LocalContext.current
    var status by remember { mutableStateOf(BatteryStatus(0, false)) }
    DisposableEffect(context) {
        fun update(intent: Intent?) {
            intent ?: return
            val level = intent.getIntExtra(BatteryManager.EXTRA_LEVEL, 0)
            val scale = intent.getIntExtra(BatteryManager.EXTRA_SCALE, 100).coerceAtLeast(1)
            val plugged = intent.getIntExtra(BatteryManager.EXTRA_PLUGGED, 0) != 0
            status = BatteryStatus(level * 100 / scale, plugged)
        }
        val receiver = object : BroadcastReceiver() {
            override fun onReceive(context: Context, intent: Intent) = update(intent)
        }
        update(context.registerReceiver(receiver, IntentFilter(Intent.ACTION_BATTERY_CHANGED)))
        onDispose { context.unregisterReceiver(receiver) }
    }
    return status
}

/** Small "Status": a ring around a phone glyph and the charge in big digits, as in iOS27L. */
@Composable
internal fun BatteryWidget() {
    val battery = rememberBattery()
    val color = if (battery.level <= LOW_BATTERY && !battery.charging) BatteryRed else BatteryGreen
    Column(Modifier.fillMaxSize().padding(16.dp)) {
        Box(Modifier.size(68.dp), contentAlignment = Alignment.Center) {
            Canvas(Modifier.fillMaxSize()) {
                val stroke = 7.dp.toPx()
                val inset = stroke / 2
                val arcSize = androidx.compose.ui.geometry.Size(size.width - stroke, size.height - stroke)
                val topLeft = androidx.compose.ui.geometry.Offset(inset, inset)
                drawArc(RingTrack, 0f, 360f, false, topLeft, arcSize, style = Stroke(stroke))
                drawArc(color, -90f, 360f * battery.level / 100f, false, topLeft, arcSize, style = Stroke(stroke, cap = StrokeCap.Round))
            }
            Icon(Icons.Rounded.Smartphone, null, tint = Color.White.copy(alpha = 0.5f), modifier = Modifier.size(28.dp))
            if (battery.charging) {
                Icon(
                    Icons.Rounded.Bolt,
                    null,
                    tint = Color.White,
                    modifier = Modifier
                        .size(16.dp)
                        .align(Alignment.TopCenter)
                        .offset(y = (-6).dp)
                        .background(Color(0xFF1C1C1D), CircleShape),
                )
            }
        }
        Spacer(Modifier.weight(1f))
        Text("${battery.level}%", color = Color.White, fontSize = 40.sp, fontWeight = FontWeight.Light)
    }
}

private const val LOW_BATTERY = 20

// endregion

// region Photo

/** The photo chosen for this widget, filling the card; a prompt to pick one otherwise. */
@Composable
internal fun PhotoWidget(entry: WidgetEntry) {
    val context = LocalContext.current
    val image: ImageBitmap? by produceState<ImageBitmap?>(null, entry.id, entry.photo) {
        value = if (entry.photo == null) null else WidgetPhotos.load(context, entry.id)?.asImageBitmap()
    }
    val photo = image
    if (photo != null) {
        Image(photo, contentDescription = null, contentScale = ContentScale.Crop, modifier = Modifier.fillMaxSize())
        return
    }
    Column(
        Modifier
            .fillMaxSize()
            .background(Brush.linearGradient(listOf(Color(0xFF3A3A3C), Color(0xFF1C1C1E)))),
        horizontalAlignment = Alignment.CenterHorizontally,
        verticalArrangement = Arrangement.Center,
    ) {
        Icon(Icons.Rounded.AddPhotoAlternate, null, tint = Color.White, modifier = Modifier.size(34.dp))
        Spacer(Modifier.height(8.dp))
        Text(stringResource(R.string.widget_choose_photo), color = Color.White, fontSize = 14.sp, fontWeight = FontWeight.SemiBold)
    }
}

// endregion

// region Suggestions

/** "App Suggestion": the most used apps, 2×2 (small) or 4×2 (medium). Tapping one opens it. */
@Composable
internal fun SuggestionsWidget(size: WidgetSize) {
    val apps = LocalWidgetEnvironment.current.suggestions.take(size.suggestionCount())
    val columns = if (size == WidgetSize.Small) 2 else 4
    Column(
        Modifier.fillMaxSize().padding(horizontal = 14.dp, vertical = 14.dp),
        verticalArrangement = Arrangement.SpaceEvenly,
    ) {
        apps.chunked(columns).forEach { row ->
            Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceEvenly) {
                row.forEach { AppIconImage(it, size = 64.dp) }
                repeat(columns - row.size) { Spacer(Modifier.size(64.dp)) }
            }
        }
    }
}

// endregion
