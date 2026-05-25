package com.batsd.openjm.ui.components

import android.graphics.Bitmap
import coil.size.Size
import coil.transform.Transformation
import com.batsd.openjm.utils.ImageDescrambler

/**
 * Coil 图片解密 Transformation
 * 根据图片 URL 和 scramble_id 对下载的图片进行解密
 * 严格对照原项目 JmImageTool.decode_and_save() + get_num_by_url()
 */
class DescrambleUrlTransformation(
    private val scrambleId: Int,
    private val imageUrl: String
) : Transformation {

    override val cacheKey: String = "descramble_v2_${scrambleId}_${imageUrl.hashCode()}"

    override suspend fun transform(
        input: Bitmap,
        size: Size
    ): Bitmap {
        val num = ImageDescrambler.getNumFromUrl(scrambleId, imageUrl)
        android.util.Log.d("Descramble", "url=${imageUrl.takeLast(30)}, scrambleId=$scrambleId, num=$num, size=${input.width}x${input.height}")
        return ImageDescrambler.descramble(input, num)
    }
}
