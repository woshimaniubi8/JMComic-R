package com.batsd.jmcomict.ui.components

import android.graphics.Bitmap
import android.graphics.BitmapFactory
import android.util.LruCache
import androidx.compose.foundation.Image
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.asImageBitmap
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.platform.LocalContext
import com.batsd.jmcomict.data.api.ApiClientFactory
import com.batsd.jmcomict.utils.ImageDescrambler
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import okhttp3.Request

/**
 * 自定义 descramble 图片加载组件 — 绕过 Coil，直接下载+解码+解密。
 * @param lowMemory 为 true 时，解密完成后额外降采样 + 转 RGB_565 节省内存（不影响解密过程）。
 */
@Composable
fun DescrambledImage(
    imageUrl: String,
    scrambleId: Int,
    modifier: Modifier = Modifier,
    lowMemory: Boolean = false
) {
    val cacheKey = remember(imageUrl, scrambleId, lowMemory) { "$imageUrl#$scrambleId#$lowMemory" }
    var bitmap by remember(cacheKey) { mutableStateOf<Bitmap?>(ImageCache.get(cacheKey)) }
    var isLoading by remember(imageUrl, scrambleId) { mutableStateOf(bitmap == null) }
    var error by remember(imageUrl, scrambleId) { mutableStateOf<String?>(null) }

    val context = LocalContext.current
    val client = remember { ApiClientFactory.getOkHttpClient(context) }

    LaunchedEffect(cacheKey) {
        if (bitmap != null) return@LaunchedEffect
        isLoading = true
        try {
            val result = withContext(Dispatchers.IO) {
                val preferredConfig = if (lowMemory) Bitmap.Config.RGB_565 else Bitmap.Config.ARGB_8888
                if (imageUrl.startsWith("file://") || imageUrl.startsWith("/")) {
                    val path = imageUrl.removePrefix("file://")
                    val opts = BitmapFactory.Options().apply {
                        inPreferredConfig = preferredConfig
                    }
                    val localBmp = BitmapFactory.decodeFile(path, opts)
                        ?: throw Exception("本地文件解码失败: $path")
                    localBmp  // 本地已解密图片，直接返回
                } else {
                    val request = Request.Builder().url(imageUrl).build()
                    client.newCall(request).execute().use { response ->
                        if (!response.isSuccessful) throw Exception("HTTP ${response.code}")
                        val bytes = response.body?.bytes() ?: throw Exception("Empty body")
                        val opts = BitmapFactory.Options().apply {
                            inPreferredConfig = preferredConfig
                        }
                        val src = BitmapFactory.decodeByteArray(bytes, 0, bytes.size, opts)
                            ?: throw Exception("Decode failed")
                        val num = ImageDescrambler.getNumFromUrl(scrambleId, imageUrl)
                        val decoded = ImageDescrambler.descramble(src, num)
                        if (decoded !== src) src.recycle()
                        decoded
                    }
                }
            }
            // 低内存后处理（不解码路径，仅在原始 result 为 Bitmap 时触发）
            val finalResult = if (lowMemory) {
                var bmp = result
                val sw = context.resources.displayMetrics.widthPixels
                if (bmp.width > sw) {
                    val ratio = bmp.width.toFloat() / sw
                    val nw = (bmp.width / ratio).toInt().coerceAtLeast(1)
                    val nh = (bmp.height / ratio).toInt().coerceAtLeast(1)
                    val scaled = android.graphics.Bitmap.createScaledBitmap(bmp, nw, nh, true)
                    if (scaled !== bmp) bmp.recycle()
                    bmp = scaled
                }
                if (bmp.config != android.graphics.Bitmap.Config.RGB_565) {
                    val c = bmp.copy(android.graphics.Bitmap.Config.RGB_565, false)
                    if (c !== bmp) bmp.recycle()
                    bmp = c
                }
                bmp
            } else result
            bitmap = finalResult
            ImageCache.put(cacheKey, finalResult)
            isLoading = false
        } catch (e: Exception) {
            android.util.Log.e("DescrambledImage", "Failed: $imageUrl", e)
            error = e.message
            isLoading = false
        }
    }

    Box(modifier = modifier, contentAlignment = Alignment.Center) {
        when {
            isLoading -> CircularProgressIndicator()
            error != null -> Text("加载失败: $error", color = MaterialTheme.colorScheme.error)
            bitmap != null -> {
                Image(
                    bitmap = bitmap!!.asImageBitmap(),
                    contentDescription = null,
                    modifier = Modifier.fillMaxWidth(),
                    contentScale = ContentScale.FillWidth
                )
            }
        }
    }
}

/**
 * 按像素内存限制的 LRU，避免阅读器一次缓存过多全尺寸页面导致 OOM。
 */
private object ImageCache {
    private val maxBytes = (Runtime.getRuntime().maxMemory() / 8).coerceAtMost(32L * 1024L * 1024L).toInt()
    private val cache = object : LruCache<String, Bitmap>(maxBytes) {
        override fun sizeOf(key: String, value: Bitmap): Int = value.byteCount
    }

    @Synchronized
    fun get(url: String): Bitmap? = cache.get(url)

    @Synchronized
    fun put(url: String, bitmap: Bitmap) {
        cache.put(url, bitmap)
    }
}
