package com.batsd.jmcomict.ui.viewmodel

import android.content.Context
import android.content.Intent
import android.os.Build
import androidx.core.content.FileProvider
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.batsd.jmcomict.data.local.PreferencesManager
import com.batsd.jmcomict.data.model.GitHubRelease
import com.batsd.jmcomict.data.repository.ReleaseRepository
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import okhttp3.OkHttpClient
import okhttp3.Request
import java.io.File
import java.security.SecureRandom
import java.security.cert.X509Certificate
import java.util.concurrent.TimeUnit
import javax.net.ssl.SSLContext
import javax.net.ssl.TrustManager
import javax.net.ssl.X509TrustManager

class UpdateViewModel(
    private val releaseRepository: ReleaseRepository = ReleaseRepository(),
    private val prefs: PreferencesManager? = null
) : ViewModel() {

    private val _release = MutableStateFlow<GitHubRelease?>(null)
    val release: StateFlow<GitHubRelease?> = _release

    private val _isChecking = MutableStateFlow(false)
    val isChecking: StateFlow<Boolean> = _isChecking

    private val _error = MutableStateFlow<String?>(null)
    val error: StateFlow<String?> = _error

    /** 下载进度 (已下载字节, 总字节) */
    private val _downloadProgress = MutableStateFlow<Pair<Long, Long>?>(null)
    val downloadProgress: StateFlow<Pair<Long, Long>?> = _downloadProgress

    private val _downloadStarted = MutableStateFlow(false)
    val downloadStarted: StateFlow<Boolean> = _downloadStarted

    /** 是否正在下载中 */
    private val _isDownloading = MutableStateFlow(false)
    val isDownloading: StateFlow<Boolean> = _isDownloading

    /** 是否自动检测更新 */
    val autoCheckEnabled: Boolean get() = prefs?.getAutoCheckUpdate() ?: true
    fun setAutoCheckEnabled(enabled: Boolean) { prefs?.setAutoCheckUpdate(enabled) }

    companion object {
        /** 信任所有证书的 TrustManager（用于下载更新，GitHub/CDN可能使用自定义证书） */
        private val trustAllCerts: Array<TrustManager> = arrayOf(object : X509TrustManager {
            override fun checkClientTrusted(chain: Array<X509Certificate>, authType: String) {}
            override fun checkServerTrusted(chain: Array<X509Certificate>, authType: String) {}
            override fun getAcceptedIssuers(): Array<X509Certificate> = emptyArray()
        })

        private fun createUnsafeSslContext(): SSLContext {
            val sslContext = SSLContext.getInstance("TLS")
            sslContext.init(null, trustAllCerts, SecureRandom())
            return sslContext
        }
    }

    private val downloadClient = OkHttpClient.Builder()
        .connectTimeout(30, TimeUnit.SECONDS)
        .readTimeout(60, TimeUnit.SECONDS)
        .followRedirects(true)
        .sslSocketFactory(createUnsafeSslContext().socketFactory, trustAllCerts[0] as X509TrustManager)
        .hostnameVerifier { _, _ -> true }
        .build()

    fun checkForUpdates() {
        viewModelScope.launch {
            _isChecking.value = true
            _error.value = null
            releaseRepository.getLatestRelease()
                .onSuccess { r -> _release.value = r }
                .onFailure { e -> _error.value = e.message }
            _isChecking.value = false
        }
    }

    /** 获取APK文件路径 */
    private fun getApkFile(context: Context, tagName: String): File {
        val dir = File(context.filesDir, "updates")
        dir.mkdirs()
        return File(dir, "JMComic-R-${tagName}.apk")
    }

    /** 下载最新版APK到应用内部目录，完成后触发安装 */
    fun downloadRelease(context: Context, release: GitHubRelease) {
        val url = releaseRepository.getDownloadUrl(release) ?: run {
            _error.value = "未找到 APK 下载链接"
            return
        }

        val apkFile = getApkFile(context, release.tag_name)

        // 检查是否已下载完成（避免重复下载）
        if (apkFile.exists() && apkFile.length() > 0) {
            installApk(context, apkFile)
            return
        }

        // 清理同版本旧文件
        apkFile.delete()

        viewModelScope.launch {
            _isDownloading.value = true
            _error.value = null
            _downloadProgress.value = null

            try {
                withContext(Dispatchers.IO) {
                    val request = Request.Builder().url(url).build()
                    val response = downloadClient.newCall(request).execute()
                    val body = response.body ?: throw Exception("响应为空")
                    val totalBytes = body.contentLength()
                    val inputStream = body.byteStream()
                    val outputStream = apkFile.outputStream()
                    val buffer = ByteArray(8192)
                    var bytesRead: Int
                    var downloadedBytes = 0L

                    while (inputStream.read(buffer).also { bytesRead = it } != -1) {
                        outputStream.write(buffer, 0, bytesRead)
                        downloadedBytes += bytesRead
                        val progress = downloadedBytes to totalBytes
                        withContext(Dispatchers.Main) {
                            _downloadProgress.value = progress
                        }
                    }
                    outputStream.close()
                    inputStream.close()
                }

                _downloadStarted.value = true
                _downloadProgress.value = null
                installApk(context, apkFile)
            } catch (e: Exception) {
                apkFile.delete()
                _error.value = "下载失败: ${e.message}"
            } finally {
                _isDownloading.value = false
            }
        }
    }

    /** 安装APK */
    private fun installApk(context: Context, apkFile: File) {
        try {
            val uri = FileProvider.getUriForFile(context,
                "${context.packageName}.fileprovider", apkFile)
            val intent = Intent(Intent.ACTION_VIEW).apply {
                setDataAndType(uri, "application/vnd.android.package-archive")
                addFlags(Intent.FLAG_GRANT_READ_URI_PERMISSION)
                addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
                if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
                    addFlags(Intent.FLAG_GRANT_PERSISTABLE_URI_PERMISSION)
                }
            }
            context.startActivity(intent)
        } catch (e: Exception) {
            _error.value = "安装失败: ${e.message}"
        }
    }

    /** 清理所有已下载的APK文件（安装完成后调用） */
    fun cleanupDownloadedApks(context: Context) {
        val dir = File(context.filesDir, "updates")
        if (dir.exists()) {
            dir.listFiles()?.forEach { it.delete() }
        }
    }

    fun clearDownloadState() {
        _downloadStarted.value = false
        _downloadProgress.value = null
    }
}
