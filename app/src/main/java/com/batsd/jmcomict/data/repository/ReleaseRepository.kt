package com.batsd.jmcomict.data.repository

import com.batsd.jmcomict.data.model.GitHubRelease
import kotlinx.serialization.json.Json
import okhttp3.Request

class ReleaseRepository {
    private val json = Json { ignoreUnknownKeys = true; isLenient = true }
    private val client = UnsafeUpdateHttpClient.create(
        connectTimeoutSeconds = 15,
        readTimeoutSeconds = 15
    )

    /** 获取最新版本信息 */
    suspend fun getLatestRelease(): Result<GitHubRelease> {
        return try {
            val request = Request.Builder()
                .url("https://api.github.com/repos/woshimaniubi8/JMComic-R/releases/latest")
                .header("Accept", "application/json")
                .build()
            val response = kotlinx.coroutines.withContext(kotlinx.coroutines.Dispatchers.IO) {
                client.newCall(request).execute()
            }
            val body = response.body?.string() ?: return Result.failure(Exception("Empty response"))
            val release = json.decodeFromString<GitHubRelease>(body)
            Result.success(release)
        } catch (e: Exception) {
            Result.failure(e)
        }
    }

    /** 获取 release APK 下载链接（优先 app-release.apk） */
    fun getDownloadUrl(release: GitHubRelease): String? {
        return release.assets
            .firstOrNull { it.name == "app-release.apk" }
            ?.browser_download_url
            ?: release.assets.firstOrNull { it.name.endsWith(".apk") }?.browser_download_url
    }

    /** 检查是否有新版本 */
    fun hasNewVersion(latestTag: String, currentVersion: String): Boolean {
        val latest = latestTag.removePrefix("v").split(".").mapNotNull { it.toIntOrNull() }
        val current = currentVersion.split(".").mapNotNull { it.toIntOrNull() }
        if (latest.isEmpty() || current.isEmpty()) return latestTag != "v$currentVersion"
        return compareVersionList(latest, current) > 0
    }

    private fun compareVersionList(a: List<Int>, b: List<Int>): Int {
        val maxLen = maxOf(a.size, b.size)
        for (i in 0 until maxLen) {
            val va = a.getOrElse(i) { 0 }
            val vb = b.getOrElse(i) { 0 }
            if (va != vb) return va - vb
        }
        return 0
    }
}
