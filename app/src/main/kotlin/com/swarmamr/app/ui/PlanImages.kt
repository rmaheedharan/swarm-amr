package com.swarmamr.app.ui

import android.content.Context
import android.graphics.Bitmap
import android.graphics.BitmapFactory
import android.net.Uri
import androidx.compose.runtime.Composable
import androidx.compose.runtime.produceState
import androidx.compose.ui.graphics.ImageBitmap
import androidx.compose.ui.graphics.asImageBitmap
import androidx.compose.ui.platform.LocalContext
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import java.io.File
import kotlin.math.max
import kotlin.math.roundToInt

/** Floor plan pictures, copied into the app's own storage so they stay after the original is gone. */
object PlanImages {
    /** Bigger pictures are made smaller, so the map stays fast and fits in memory. */
    private const val MAX_SIDE = 3000

    private var cachedName: String? = null
    private var cached: ImageBitmap? = null

    fun cachedFor(name: String?): ImageBitmap? = if (name != null && name == cachedName) cached else null

    /** Copy a picture the user chose into app storage. Returns (file name, width, height). */
    suspend fun import(ctx: Context, uri: Uri): Triple<String, Int, Int>? = withContext(Dispatchers.IO) {
        runCatching {
            val bounds = BitmapFactory.Options().apply { inJustDecodeBounds = true }
            ctx.contentResolver.openInputStream(uri)!!.use { BitmapFactory.decodeStream(it, null, bounds) }
            if (bounds.outWidth <= 0 || bounds.outHeight <= 0) return@runCatching null
            var sample = 1
            while (max(bounds.outWidth, bounds.outHeight) / (sample * 2) >= MAX_SIDE) sample *= 2
            val opts = BitmapFactory.Options().apply { inSampleSize = sample }
            var bmp = ctx.contentResolver.openInputStream(uri)!!.use { BitmapFactory.decodeStream(it, null, opts) }
                ?: return@runCatching null
            val side = max(bmp.width, bmp.height)
            if (side > MAX_SIDE) {
                val k = MAX_SIDE.toDouble() / side
                bmp = Bitmap.createScaledBitmap(bmp, (bmp.width * k).roundToInt(), (bmp.height * k).roundToInt(), true)
            }
            val name = "plan-${System.currentTimeMillis()}.png"
            File(ctx.filesDir, name).outputStream().use { bmp.compress(Bitmap.CompressFormat.PNG, 100, it) }
            Triple(name, bmp.width, bmp.height)
        }.getOrNull()
    }

    suspend fun load(ctx: Context, name: String): ImageBitmap? {
        cachedFor(name)?.let { return it }
        val img = withContext(Dispatchers.IO) {
            runCatching { BitmapFactory.decodeFile(File(ctx.filesDir, name).path)?.asImageBitmap() }.getOrNull()
        }
        if (img != null) { cachedName = name; cached = img }
        return img
    }
}

/** The floor plan picture [name], loaded in the background; null while loading or when missing. */
@Composable
fun rememberPlanBitmap(name: String?): ImageBitmap? {
    val ctx = LocalContext.current
    val state = produceState(initialValue = PlanImages.cachedFor(name), name) {
        val loaded: ImageBitmap? = if (name == null) null else PlanImages.load(ctx, name)
        this.value = loaded
    }
    return state.value
}
