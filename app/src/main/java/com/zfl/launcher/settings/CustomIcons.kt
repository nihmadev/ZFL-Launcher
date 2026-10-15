package com.zfl.launcher.settings

import android.content.Context
import android.graphics.Bitmap
import android.graphics.ImageDecoder
import android.net.Uri
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import kotlin.math.min

/** Size of stored custom icons, in px. Large enough for any icon size the launcher draws. */
private const val CUSTOM_ICON_SIZE = 256

/**
 * Turns an image picked by the user into a custom app icon: center-crops it to a square,
 * scales it down, stores it as PNG and notifies every icon cache.
 *
 * @return false if the image could not be read.
 */
suspend fun importCustomIcon(context: Context, settings: LauncherSettings, appKey: String, image: Uri): Boolean =
    withContext(Dispatchers.IO) {
        runCatching {
            val source = ImageDecoder.createSource(context.contentResolver, image)
            val decoded = ImageDecoder.decodeBitmap(source) { decoder, info, _ ->
                decoder.allocator = ImageDecoder.ALLOCATOR_SOFTWARE
                // Decode big photos at a reduced size right away.
                val shortSide = min(info.size.width, info.size.height)
                if (shortSide > CUSTOM_ICON_SIZE * 2) {
                    val factor = shortSide / (CUSTOM_ICON_SIZE * 2)
                    decoder.setTargetSize(info.size.width / factor, info.size.height / factor)
                }
            }
            val side = min(decoded.width, decoded.height)
            val square = Bitmap.createBitmap(decoded, (decoded.width - side) / 2, (decoded.height - side) / 2, side, side)
            val icon = Bitmap.createScaledBitmap(square, CUSTOM_ICON_SIZE, CUSTOM_ICON_SIZE, true)

            val file = settings.customIconFile(appKey)
            file.parentFile?.mkdirs()
            file.outputStream().use { icon.compress(Bitmap.CompressFormat.PNG, 100, it) }
            settings.markCustomIconChanged(appKey)
        }.isSuccess
    }
