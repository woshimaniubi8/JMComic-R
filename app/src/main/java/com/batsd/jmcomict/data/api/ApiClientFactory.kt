package com.batsd.jmcomict.data.api

import android.content.Context
import android.util.Base64
import okhttp3.Cookie
import okhttp3.CookieJar
import okhttp3.HttpUrl
import okhttp3.Interceptor
import okhttp3.OkHttpClient
import okhttp3.logging.HttpLoggingInterceptor
import retrofit2.Retrofit
import com.jakewharton.retrofit2.converter.kotlinx.serialization.asConverterFactory
import kotlinx.serialization.json.Json
import okhttp3.MediaType.Companion.toMediaType
import java.security.MessageDigest
import java.util.concurrent.ConcurrentHashMap
import java.util.concurrent.ConcurrentLinkedDeque
import java.util.concurrent.TimeUnit
import javax.crypto.Cipher
import javax.crypto.spec.SecretKeySpec

/**
 * API 客户端工厂
 * 基于 JMComic-qt 原项目 req.py 中的认证 + 解密逻辑
 */
object ApiClientFactory {

    private var apiService: JMComicApiService? = null
    private var okHttpClient: OkHttpClient? = null
    private const val BASE_URL = "https://www.cdnhth.club/"
    /** Web 端 AJAX API 基础 URL（点赞等） */
    const val WEB_BASE_URL = "https://jmcomic-zzz.one/"
    /** 图片 CDN 基础 URL，用于拼接章节图片链接 */
    private const val IMAGE_BASE_URL = "https://cdn-msp.jmapiproxy3.cc"
    
    /** 备用 CDN 列表 */
    private val CDN_LIST = listOf(
        "https://cdn-msp.jmapiproxy3.cc",
        "https://cdn-msp.jmdanjonproxy.xyz",
        "https://cdn-msp.jmspmobiaso2rx.xyz"
    )
    @Volatile
    private var currentCdnIndex = 0

    /** 从持久化恢复 CDN */
    @JvmStatic
    fun restoreCdnIndex(index: Int) {
        currentCdnIndex = index.coerceIn(0, CDN_LIST.lastIndex)
    }
    /** 图片 CDN 主机名，用于 Referer 拦截器匹配 */
    private const val IMAGE_CDN_HOST = "cdn-msp.jmapiproxy3.cc"
    // 来自 jm_config.py JmMagicConstants
    private const val HEADER_VER = "2.0.21"          // APP_VERSION
    private const val APP_TOKEN_SECRET = "18comicAPP"  // 用于 token 签名
    private const val APP_TOKEN_SECRET_2 = "18comicAPPContent"  // 用于 chapter_view_template
    private const val APP_DATA_SECRET = "185Hcomic3PAPP7R"  // 用于响应解密

    /** 最近一次请求的时间戳，用于解密响应数据 */
    @Volatile
    var lastTimestamp: String = ""
        private set

    /** 最近的时间戳队列（最多保留 10 个），解决并发请求解密时戳不匹配问题 */
    private val recentTimestamps = java.util.concurrent.ConcurrentLinkedDeque<String>()

    /** AVS 会话令牌 (登录后设置, 所有请求需要携带) */
    @Volatile
    var avsToken: String = ""

    /** 自动登录回调：返回 true 表示重登成功，false 失败 */
    @Volatile
    var autoLoginCallback: (suspend () -> Boolean)? = null

    private val authenticatorLock = Any()
    @Volatile
    private var autoLoginInProgress = false

    private val cookieStore = ConcurrentHashMap<String, MutableList<Cookie>>()

    fun getInstance(context: Context): JMComicApiService {
        if (apiService == null) {
            apiService = createRetrofit(context).create(JMComicApiService::class.java)
        }
        return apiService!!
    }

    /** 获取共享的 OkHttpClient (可用于 Coil 等图片加载器) */
    fun getOkHttpClient(context: Context): OkHttpClient {
        if (okHttpClient == null) {
            okHttpClient = createOkHttpClient(context)
        }
        return okHttpClient!!
    }

    /** 获取 Web AJAX 请求用的 OkHttpClient（共享 CookieJar，不加 Auth 拦截器） */
    fun getOkHttpClientForWeb(): OkHttpClient {
        // Web 请求不需要移动端 Token 认证，但需要共享 Cookie（AVS）
        return OkHttpClient.Builder()
            .connectTimeout(30, TimeUnit.SECONDS)
            .readTimeout(30, TimeUnit.SECONDS)
            .cookieJar(object : CookieJar {
                override fun loadForRequest(url: HttpUrl) =
                    cookieStore[url.host] ?: emptyList()
                override fun saveFromResponse(url: HttpUrl, cookies: List<Cookie>) {
                    cookieStore.getOrPut(url.host) { mutableListOf() }.let { existing ->
                        cookies.forEach { cookie ->
                            existing.removeAll { it.name == cookie.name }
                            if (cookie.expiresAt == 0L || cookie.expiresAt > System.currentTimeMillis()) {
                                existing.add(cookie)
                            }
                        }
                    }
                }
            })
            .addInterceptor(HttpLoggingInterceptor().apply { level = HttpLoggingInterceptor.Level.BODY })
            .build()
    }

    private fun createRetrofit(context: Context): Retrofit {
        return Retrofit.Builder()
            .baseUrl(BASE_URL)
            .client(createOkHttpClient(context))
            .addConverterFactory(createJsonConverter().asConverterFactory("application/json".toMediaType()))
            .build()
    }

    private fun createOkHttpClient(context: Context): OkHttpClient {
        return OkHttpClient.Builder()
            .connectTimeout(30, TimeUnit.SECONDS)
            .writeTimeout(30, TimeUnit.SECONDS)
            .readTimeout(30, TimeUnit.SECONDS)
            .cookieJar(object : CookieJar {
                override fun loadForRequest(url: HttpUrl): List<Cookie> {
                    val stored = cookieStore[url.host] ?: emptyList()
                    val result = mutableListOf<Cookie>()
                    result.addAll(stored)
                    // 注入 AVS 令牌
                    if (avsToken.isNotEmpty() && stored.none { it.name == "AVS" }) {
                        Cookie.parse(url, "AVS=$avsToken")?.let { result.add(it) }
                    }
                    // 确保 ipcountry / ipm5 存在（会话稳定性）
                    if (stored.none { it.name == "ipcountry" }) {
                        Cookie.parse(url, "ipcountry=CN")?.let { result.add(it) }
                    }
                    if (stored.none { it.name == "ipm5" }) {
                        Cookie.parse(url, "ipm5=4f15409f804567cd4f4344fae94126e5")?.let { result.add(it) }
                    }
                    return result
                }
                override fun saveFromResponse(url: HttpUrl, cookies: List<Cookie>) {
                    val existing = cookieStore.getOrPut(url.host) { mutableListOf() }
                    for (cookie in cookies) {
                        existing.removeAll { it.name == cookie.name }
                        if (cookie.expiresAt == 0L || cookie.expiresAt > System.currentTimeMillis()) {
                            existing.add(cookie)
                        }
                    }
                    cookies.find { it.name == "AVS" }?.let { avsToken = it.value }
                }
            })
            .addInterceptor(createLoggingInterceptor())
            .addInterceptor(createAuthInterceptor())
            .addInterceptor(createImageRefererInterceptor())
            .authenticator { _, response ->
                if (response.code == 401 || response.code == 403) {
                    val reqUrl = response.request.url.toString()
                    // 不对登录请求自身触发自动重登
                    if (reqUrl.contains("/login")) {
                        android.util.Log.d("ApiClientFactory", "Auth failed on /login, skip auto-login")
                        return@authenticator null
                    }
                    // 避免循环重试
                    if (response.request.header("X-Auto-Retry") != null) {
                        android.util.Log.d("ApiClientFactory", "Already retried, give up")
                        return@authenticator null
                    }
                    // 并发控制
                    synchronized(authenticatorLock) {
                        if (autoLoginInProgress) {
                            android.util.Log.d("ApiClientFactory", "Auto-login already in progress, skip")
                            return@authenticator null
                        }
                        autoLoginInProgress = true
                    }
                    try {
                        android.util.Log.w("ApiClientFactory", "Auth failed (${response.code}) on $reqUrl, auto-login...")
                        cookieStore.clear()
                        avsToken = ""
                        val callback = autoLoginCallback
                        var success = false
                        if (callback != null) {
                            kotlinx.coroutines.runBlocking {
                                success = callback()
                            }
                        }
                        if (success) {
                            android.util.Log.i("ApiClientFactory", "Auto-login OK, retrying request")
                            response.request.newBuilder()
                                .header("X-Auto-Retry", "1")
                                .build()
                        } else {
                            android.util.Log.w("ApiClientFactory", "Auto-login failed")
                            null
                        }
                    } catch (e: Exception) {
                        android.util.Log.e("ApiClientFactory", "Auto-login exception", e)
                        null
                    } finally {
                        synchronized(authenticatorLock) { autoLoginInProgress = false }
                    }
                } else null
            }
            .build()
    }

    private fun createLoggingInterceptor(): HttpLoggingInterceptor {
        val logging = HttpLoggingInterceptor()
        logging.level = HttpLoggingInterceptor.Level.BODY
        return logging
    }

    /**
     * 认证拦截器 — 复现 ServerReq.GetHeader() 的 Token 签名
     * 对于 /chapter_view_template 使用 APP_TOKEN_SECRET_2
     * 
     * 同时将时间戳存入 recentTimestamps 队列，供解密时多时间戳尝试
     */
    private fun createAuthInterceptor(): Interceptor {
        return Interceptor { chain ->
            val now = (System.currentTimeMillis() / 1000).toString()
            lastTimestamp = now
            // 维护最近时间戳队列
            recentTimestamps.addFirst(now)
            if (recentTimestamps.size > 10) {
                recentTimestamps.pollLast()
            }
            
            val originalRequest = chain.request()
            // /chapter_view_template 使用不同的 secret
            val isScramble = originalRequest.url.encodedPath.contains("chapter_view_template")
            val secret = if (isScramble) APP_TOKEN_SECRET_2 else APP_TOKEN_SECRET
            val token = md5("${now}$secret")
            val tokenparam = "$now,$HEADER_VER"

            val newRequest = originalRequest.newBuilder()
                .header("User-Agent", "Mozilla/5.0 (Linux; Android 7.1.2; DT1901A Build/N2G47O; wv) AppleWebKit/537.36 (KHTML, like Gecko) Version/4.0 Chrome/86.0.4240.198 Mobile Safari/537.36")
                .header("Accept", "application/json, text/plain, */*")
                .header("tokenparam", tokenparam)
                .header("token", token)
                .header("version", HEADER_VER)
                // 注意: 不要设置 Accept-Encoding, OkHttp 会自动处理 gzip 透明解压
                .build()
            chain.proceed(newRequest)
        }
    }

    /** 图片 Referer 拦截器 — 图片 CDN (jmapiproxy/cdn) 要求 Referer 请求头 */
    private fun createImageRefererInterceptor(): Interceptor {
        return Interceptor { chain ->
            val request = chain.request()
            val host = request.url.host
            if (host.contains("jmapiproxy") || host.contains("cdn")) {
                val newRequest = request.newBuilder()
                    .header("Referer", BASE_URL)
                    .build()
                chain.proceed(newRequest)
            } else {
                chain.proceed(request)
            }
        }
    }

    /**
     * 解密 API 响应中的 data 字段
     * 对应原项目 req.py ParseData() + jmcomic.JmCryptoTool.decode_resp_data()
     *
     * 算法: AES-256/ECB/PKCS5Padding
     * Key:  MD5("{timestamp}185Hcomic3PAPP7R")
     * 输入: Base64 编码的密文
     * 输出: UTF-8 JSON 字符串
     *
     * 尝试多个最近时间戳来解密，防止并发请求时时间戳不匹配。
     */
    fun decryptData(encryptedBase64: String): String {
        // 收集候选时间戳（最近的优先，去重）
        val candidates = LinkedHashSet<String>()
        if (lastTimestamp.isNotEmpty()) candidates.add(lastTimestamp)
        candidates.addAll(recentTimestamps)

        for (ts in candidates) {
            try {
                val keyStr = md5("${ts}$APP_DATA_SECRET")
                val keyBytes = keyStr.toByteArray(Charsets.UTF_8)
                val cipherBytes = Base64.decode(encryptedBase64, Base64.DEFAULT)
                val cipher = Cipher.getInstance("AES/ECB/PKCS5Padding")
                cipher.init(Cipher.DECRYPT_MODE, SecretKeySpec(keyBytes, "AES"))
                val decrypted = cipher.doFinal(cipherBytes)
                return String(decrypted, Charsets.UTF_8)
            } catch (_: Exception) {
                // 尝试下一个时间戳
            }
        }
        android.util.Log.e("ApiClientFactory", "Decrypt failed with all ${candidates.size} timestamps")
        return ""
    }

    /** 将相对图片路径转为完整 URL */
    fun fullImageUrl(relativePath: String): String {
        if (relativePath.startsWith("http")) return relativePath
        return BASE_URL + relativePath
    }

    /** 获取图片 CDN 基础 URL (用于拼接章节图片链接) */
    fun getImageBaseUrl(): String = CDN_LIST[currentCdnIndex.coerceIn(0, CDN_LIST.lastIndex)]

    /** 切换 CDN (返回新的 CDN URL) */
    fun switchCdn(): String {
        currentCdnIndex = (currentCdnIndex + 1) % CDN_LIST.size
        return CDN_LIST[currentCdnIndex]
    }

    /** 获取当前 CDN 名称 */
    fun getCurrentCdnName(): String = when (currentCdnIndex) {
        0 -> "主线路"
        1 -> "备用线路1"
        2 -> "备用线路2"
        else -> "未知"
    }

    /** 设置 AVS 会话令牌 (登录后调用) — 自动更新 avsToken 属性 */
    fun saveAvsToken(token: String) {
        avsToken = token
    }

    /** 清除所有 Cookie（退出登录时调用） */
    fun clearCookies() {
        cookieStore.clear()
    }

    private fun md5(input: String): String {
        val md = MessageDigest.getInstance("MD5")
        val digest = md.digest(input.toByteArray(Charsets.UTF_8))
        return digest.joinToString("") { "%02x".format(it) }
    }

    private fun createJsonConverter(): Json {
        return Json {
            ignoreUnknownKeys = true
            coerceInputValues = true
            isLenient = true
        }
    }
}
