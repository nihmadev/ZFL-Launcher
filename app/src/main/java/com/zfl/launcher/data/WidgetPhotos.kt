package com.zfl.launcher.data

import android.content.Context
import android.graphics.Bitmap
import android.graphics.BitmapFactory
import android.graphics.ImageDecoder
import android.net.Uri
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import java.io.File
import kotlin.math.max

/** Longest side of a stored widget photo, in px: sharp on a large widget, small on disk. */
private const val PHOTO_MAX_SIDE = 1080

/** Images chosen for photo widgets, one file per widget in the app's private storage. */
object WidgetPhotos {
    fun file(context: Context, widgetId: String): File =
        File(context.filesDir, "widgets/${widgetId.replace(Regex("[^A-Za-z0-9._-]"), "_")}.jpg")

    /** Copies a picked image (downscaled) for [widgetId]. Returns false if it can't be read. */
    suspend fun import(context: Context, widgetId: String, image: Uri): Boolean = withContext(Dispatchers.IO) {
        runCatching {
            val source = ImageDecoder.createSource(context.contentResolver, image)
            val bitmap = ImageDecoder.decodeBitmap(source) { decoder, info, _ ->
                decoder.allocator = ImageDecoder.ALLOCATOR_SOFTWARE
                val longest = max(info.size.width, info.size.height)
                if (longest > PHOTO_MAX_SIDE) {
                    val factor = PHOTO_MAX_SIDE.toFloat() / longest
                    decoder.setTargetSize((info.size.width * factor).toInt(), (info.size.height * factor).toInt())
                }
            }
            val target = file(context, widgetId)
            target.parentFile?.mkdirs()
            target.outputStream().use { bitmap.compress(Bitmap.CompressFormat.JPEG, 90, it) }
        }.isSuccess
    }

    suspend fun load(context: Context, widgetId: String): Bitmap? = withContext(Dispatchers.IO) {
        file(context, widgetId).takeIf { it.exists() }?.let { BitmapFactory.decodeFile(it.path) }
    }

    fun delete(context: Context, widgetId: String) {
        file(context, widgetId).delete()
    }
}
