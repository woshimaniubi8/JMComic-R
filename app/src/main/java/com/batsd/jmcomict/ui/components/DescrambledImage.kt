package com.batsd.jmcomict.ui.components

import android.graphics.Bitmap
import android.graphics.BitmapFactory
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
import java.util.Collections

/**
 * 自定义 descramble 图片加载组件 — 绕过 Coil，直接下载+解码+解密
 * 不含缩放（缩放由父级统一控制）
 */
@Composable
fun DescrambledImage(
    imageUrl: String,
    scrambleId: Int,
    modifier: Modifier = Modifier
) {
    var bitmap by remember(imageUrl, scrambleId) { mutableStateOf<Bitmap?>(ImageCache.get(imageUrl)) }
    var isLoading by remember(imageUrl, scrambleId) { mutableStateOf(bitmap == null) }
    var error by remember(imageUrl, scrambleId) { mutableStateOf<String?>(null) }

    val context = LocalContext.current
    val client = remember { ApiClientFactory.getOkHttpClient(context) }

    LaunchedEffect(imageUrl, scrambleId) {
        if (bitmap != null) return@LaunchedEffect
        isLoading = true
        try {
            val result = withContext(Dispatchers.IO) {
                val request = Request.Builder().url(imageUrl).build()
                val response = client.newCall(request).execute()
                if (!response.isSuccessful) throw Exception("HTTP ${response.code}")
                val bytes = response.body?.bytes() ?: throw Exception("Empty body")

                val opts = BitmapFactory.Options().apply {
                    inPreferredConfig = Bitmap.Config.ARGB_8888
                }
                val src = BitmapFactory.decodeByteArray(bytes, 0, bytes.size, opts)
                    ?: throw Exception("Decode failed")

                val num = ImageDescrambler.getNumFromUrl(scrambleId, imageUrl)
                ImageDescrambler.descramble(src, num)
            }
            bitmap = result
            ImageCache.put(imageUrl, result)
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
 * 简单的内存 LRU 缓存（最多缓存 30 张 descrambled 图片）
 */
private object ImageCache {
    private const val MAX_SIZE = 30
    private val cache = Collections.synchronizedMap(object : LinkedHashMap<String, Bitmap>(MAX_SIZE, 0.75f, true) {
        override fun removeEldestEntry(eldest: MutableMap.MutableEntry<String, Bitmap>?): Boolean {
            return size > MAX_SIZE
        }
    })

    fun get(url: String): Bitmap? = cache[url]

    fun put(url: String, bitmap: Bitmap) {
        cache[url] = bitmap
    }
}
