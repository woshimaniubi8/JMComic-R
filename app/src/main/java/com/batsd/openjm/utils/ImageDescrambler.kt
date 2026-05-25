package com.batsd.openjm.utils

import android.graphics.Bitmap
import android.graphics.Canvas
import java.security.MessageDigest

/**
 * 图片解密工具 — 严格对照 JS jquery.photo-0.5.js scramble_image() + get_num()
 * 
 * 禁漫的图片是"打乱"的：图像被纵向切成若干段，从下到上重新排列。
 * 解密过程就是逆向：把底部的段移回顶部。
 *
 * 关键: JS 中 page = img.parentNode.id.split(".")[0] = filename (如 "00001")
 *      所以 key = md5(aid + filename) = md5("144153100001")
 *      Android 使用相同逻辑: key = md5("${aid}${filename}")
 */
object ImageDescrambler {

    // 来自 jm_config.py JmMagicConstants
    private const val SCRAMBLE_268850 = 268850
    private const val SCRAMBLE_421926 = 421926

    /**
     * 计算图片分割数 — 严格对照 JS jquery.photo-0.5.js get_num()
     *
     * 关键: JS 中 page 来自 img.parentNode.id = "{filename}.webp" → split(".")[0] = "{filename}"
     * 所以 key = aid + filename (如 "144153100001")，不是 "aid + page_N"
     *
     * @param scrambleId 从 /chapter_view_template 获取
     * @param aid        章节/相册 ID (从 URL 提取)
     * @param filename   图片文件名 (不含扩展名，如 "00001")
     */
    fun getNum(scrambleId: Int, aid: Int, filename: String): Int {
        if (aid < scrambleId) return 0
        if (aid < SCRAMBLE_268850) return 10

        val x = if (aid < SCRAMBLE_421926) 10 else 8
        val s = "$aid$filename"
        val hash = MessageDigest.getInstance("MD5").digest(s.toByteArray())
        val hex = hash.joinToString("") { "%02x".format(it) }
        val lastChar = hex.last()
        return (lastChar.code % x) * 2 + 2
    }

    /**
     * 从图片 URL 计算分割数
     * URL 格式: https://cdn.../media/photos/{aid}/{filename}.ext
     */
    fun getNumFromUrl(scrambleId: Int, url: String): Int {
        val parts = url.split("/")
        val aid = parts.getOrNull(parts.size - 2)?.toIntOrNull() ?: return 0
        val filenameWithExt = parts.lastOrNull() ?: return 0
        val filename = filenameWithExt.substringBeforeLast(".")
        return getNum(scrambleId, aid, filename)
    }

    /**
     * 解密图片 — 严格对照原项目 JmImageTool.decode_and_save()
     *
     * @param src    原始（加密）图片
     * @param num    分割数
     * @return 解密后的图片
     */
    fun descramble(src: Bitmap, num: Int): Bitmap {
        if (num == 0) return src

        val w = src.width
        val h = src.height
        // 空白画布（不复制原图，避免原图像素残留）
        val dest = Bitmap.createBitmap(w, h, src.config ?: Bitmap.Config.ARGB_8888)
        val canvas = Canvas(dest)
        val move = h / num
        val over = h % num

        for (i in 0 until num) {
            var segH = move
            val ySrc = h - move * (i + 1) - over
            var yDst = move * i

            if (i == 0) segH += over
            else yDst += over

            // 直接 drawBitmap(src, srcRect, dstRect) — 不创建中间 Bitmap
            val srcRect = android.graphics.Rect(0, ySrc, w, ySrc + segH)
            val dstRect = android.graphics.Rect(0, yDst, w, yDst + segH)
            canvas.drawBitmap(src, srcRect, dstRect, null)
        }
        return dest
    }
}
