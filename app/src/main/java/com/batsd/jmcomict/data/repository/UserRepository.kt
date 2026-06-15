package com.batsd.jmcomict.data.repository

import android.util.Log
import com.batsd.jmcomict.data.api.ApiClientFactory
import com.batsd.jmcomict.data.api.JMComicApiService
import com.batsd.jmcomict.data.api.LoginResult
import com.batsd.jmcomict.data.model.*
import kotlinx.serialization.json.JsonPrimitive

/**
 * 用户仓库
 */
class UserRepository(
    internal val prefs: com.batsd.jmcomict.data.local.PreferencesManager
) {
    private val apiService: JMComicApiService get() = ApiClientFactory.getApiService()

    companion object {
        private const val TAG = "UserRepository"
    }

    suspend fun login(username: String, password: String): Result<User> {
        return try {
            // === 预登录 — 对照原项目 LoginPreReq/LoginCheck301Req: 获取初始 Cookie ===
            try {
                apiService.getLoginPage()
                Log.d(TAG, "Pre-login: got initial cookies")
            } catch (e: Exception) {
                Log.w(TAG, "Pre-login failed (non-critical): ${e.message}")
            }

            // === 发起登录 — 对照原项目 LoginReq2 ===
            Log.d(TAG, "POST /login with username=$username")
            val response = apiService.login(username, password)

            if (response.isSuccess()) {
                // 使用统一的 decryptAndParse 而非手动解析
                val loginResult = response.decryptAndParse<LoginResult>()
                    ?: return Result.failure(Exception("登录数据解析失败"))

                Log.d(TAG, "Login OK: uid=${loginResult.uid}, username=${loginResult.username}, s=${loginResult.s.take(8)}...")
                
                val user = User(
                    uid = loginResult.uid,
                    userName = loginResult.username,
                    title = loginResult.levelName,
                    levelName = loginResult.levelName,
                    level = loginResult.level.toString(),
                    exp = if (loginResult.exp.isNotEmpty()) JsonPrimitive(loginResult.exp) else null,
                    nextExp = JsonPrimitive(loginResult.nextLevelExp),
                    expPercent = loginResult.expPercent,
                    coin = loginResult.coin,
                    favorites = loginResult.albumFavorites,
                    photo = loginResult.photo,
                    badges = loginResult.badges,
                    isLogin = true
                )
                
                // 对照原项目: cookies.update({'AVS': resp.res_data['s']})
                if (loginResult.s.isNotEmpty()) {
                    ApiClientFactory.saveAvsToken(loginResult.s)
                    prefs.saveSessionToken(loginResult.s)
                    Log.d(TAG, "AVS token saved: ${loginResult.s.take(8)}...")
                } else {
                    Log.w(TAG, "Login response has empty 's' field — AVS not saved!")
                }
                
                // 持久化登录状态
                prefs.saveLoginUser(user)
                // 保存凭证用于自动续期
                prefs.saveCredentials(username, password)
                Result.success(user)
            } else {
                Log.e(TAG, "Login failed: code=${response.code}, msg=${response.errorMessage()}")
                Result.failure(Exception(response.errorMessage()))
            }
        } catch (e: Exception) {
            Log.e(TAG, "Login exception", e)
            Result.failure(e)
        }
    }

    suspend fun register(username: String, password: String, email: String): Result<User> {
        return try {
            val response = apiService.register(username, password, email)
            if (response.isSuccess()) {
                Result.success(User(userName = username, isLogin = true))
            } else {
                Result.failure(Exception(response.errorMessage()))
            }
        } catch (e: Exception) {
            Result.failure(e)
        }
    }

    suspend fun dailyCheckIn(uid: String): Result<String> {
        return try {
            // 步骤1: GET /daily 获取 daily_id
            val dailyId = getDailyId(uid).getOrElse { return Result.failure(it) }
            if (dailyId.isEmpty()) return Result.failure(Exception("今天已签到"))

            // 步骤2: POST /daily_chk 提交签到
            val chkResp = apiService.dailyCheckIn(uid, dailyId)
            if (!chkResp.isSuccess()) return Result.failure(Exception(chkResp.errorMessage()))
            var responseMessage = chkResp.message
            val chkData = when (val d = chkResp.data) {
                is kotlinx.serialization.json.JsonPrimitive -> d.content
                else -> null
            }
            if (!chkData.isNullOrEmpty() && chkData.length > 5) {
                val dec = ApiClientFactory.decryptData(chkData)
                if (dec.isNotEmpty()) {
                    try {
                        val json = kotlinx.serialization.json.Json { ignoreUnknownKeys = true }
                        val obj = json.decodeFromString<kotlinx.serialization.json.JsonObject>(dec)
                        val msg = obj["msg"]?.let { (it as? kotlinx.serialization.json.JsonPrimitive)?.content }
                            ?: obj["message"]?.let { (it as? kotlinx.serialization.json.JsonPrimitive)?.content }
                        if (!msg.isNullOrEmpty()) responseMessage = msg
                        else responseMessage = dec.take(100)
                    } catch (_: Exception) {
                        responseMessage = dec.take(100)
                    }
                }
            }

            // 步骤3: 再次读取状态确认签到是否真的完成，避免 code=200 但实际未签到的假成功。
            val remainingDailyId = getDailyId(uid).getOrElse {
                return Result.failure(Exception("签到状态确认失败：${it.message ?: "未知错误"}"))
            }
            if (remainingDailyId.isEmpty()) {
                Result.success(responseMessage.ifBlank { "签到成功" })
            } else {
                Result.failure(Exception(responseMessage.ifBlank { "签到未确认，请稍后重试" }))
            }
        } catch (e: Exception) {
            Result.failure(e)
        }
    }

    private suspend fun getDailyId(uid: String): Result<String> {
        return try {
            val statusResp = apiService.dailyStatus(uid)
            if (!statusResp.isSuccess()) return Result.failure(Exception(statusResp.errorMessage()))
            val statusData = when (val d = statusResp.data) {
                is kotlinx.serialization.json.JsonPrimitive -> d.content
                else -> null
            }
            if (statusData.isNullOrEmpty() || statusData.length <= 20) {
                return Result.success("")
            }
            val dec = ApiClientFactory.decryptData(statusData)
            if (dec.isEmpty()) return Result.success("")
            val json = kotlinx.serialization.json.Json { ignoreUnknownKeys = true }
            val obj = json.decodeFromString<kotlinx.serialization.json.JsonObject>(dec)
            val dailyId = obj["daily_id"]?.let {
                (it as? kotlinx.serialization.json.JsonPrimitive)?.content
            }.orEmpty()
            Result.success(dailyId)
        } catch (e: Exception) {
            Result.failure(e)
        }
    }

    fun logout() {
        prefs.logout()
        ApiClientFactory.saveAvsToken("")
        ApiClientFactory.clearCookies()
    }
}
