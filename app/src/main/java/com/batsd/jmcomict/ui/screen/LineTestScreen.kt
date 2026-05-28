package com.batsd.jmcomict.ui.screen

import androidx.compose.animation.*
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.itemsIndexed
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.filled.*
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.batsd.jmcomict.data.api.ApiClientFactory
import com.batsd.jmcomict.ui.components.*
import kotlinx.coroutines.*
import okhttp3.OkHttpClient
import okhttp3.Request
import java.util.concurrent.TimeUnit

/**
 * 线路测试类型
 */
enum class TestLineType { API, IMAGE }

/**
 * 线路测试结果数据
 */
data class LineTestResult(
    val name: String,
    val url: String,
    val latency: Long,   // 毫秒, -1 表示超时/失败
    val isSuccess: Boolean,
    val type: TestLineType = TestLineType.API,
    val errorMsg: String = ""
)

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun LineTestScreen(
    currentCdnIndex: Int = 0,
    onBackClick: () -> Unit,
    onSelectCdn: (Int) -> Unit = {}
) {
    val colorScheme = MaterialTheme.colorScheme
    val scope = rememberCoroutineScope()

    // 测试状态
    var apiResults by remember { mutableStateOf<List<LineTestResult>>(emptyList()) }
    var imageResults by remember { mutableStateOf<List<LineTestResult>>(emptyList()) }
    var isTesting by remember { mutableStateOf(false) }
    var testingPhase by remember { mutableStateOf("") }  // "api", "image", ""
    var testingIndex by remember { mutableIntStateOf(-1) }
    var totalCount by remember { mutableIntStateOf(0) }
    var showSelectDialog by remember { mutableStateOf(false) }

    // 初始化时立即填充占位列表，让项目始终可见
    LaunchedEffect(Unit) {
        val apiUrlList = ApiClientFactory.getApiUrlList()
        val apiNameList = ApiClientFactory.getApiUrlNames()
        val cdnUrlList = ApiClientFactory.getCdnUrlList()
        val cdnNameList = ApiClientFactory.getCdnUrlNames()
        apiResults = apiUrlList.mapIndexed { index, url ->
            val name = apiNameList.getOrElse(index) { "分流${index + 1}" }
            LineTestResult(name, url, -1, false, TestLineType.API, "等待测试")
        }
        imageResults = cdnUrlList.mapIndexed { index, url ->
            val name = cdnNameList.getOrElse(index) { "图片分流${index + 1}" }
            LineTestResult(name, url, -1, false, TestLineType.IMAGE, "等待测试")
        }
        isTesting = true
        isTesting = true
        runAllTests(
            scope = scope,
            onProgress = { phase, index, total, partialApi, partialImage ->
                testingPhase = phase
                testingIndex = index
                totalCount = total
                if (partialApi != null) apiResults = partialApi
                if (partialImage != null) imageResults = partialImage
            },
            onComplete = { api, image ->
                apiResults = api
                imageResults = image
                isTesting = false
                testingPhase = ""
                testingIndex = -1
            }
        )
    }

    // 延迟文本颜色
    fun latencyColor(latency: Long): Color = when {
        latency < 0 -> colorScheme.error
        latency < 300 -> Color(0xFF4CAF50)   // 绿色 - 快
        latency < 800 -> Color(0xFFFFC107)   // 黄色 - 一般
        latency < 1500 -> Color(0xFFFF9800)  // 橙色 - 较慢
        else -> colorScheme.error             // 红色 - 很慢
    }

    // 延迟文本
    fun latencyText(result: LineTestResult): String = when {
        result.latency < 0 -> result.errorMsg.ifEmpty { "超时" }
        else -> "${result.latency} ms"
    }

    Scaffold(
        topBar = {
            TopAppBar(
                title = { Text("线路测试") },
                navigationIcon = {
                    IconButton(onClick = onBackClick) {
                        Icon(Icons.AutoMirrored.Filled.ArrowBack, "返回")
                    }
                },
                colors = TopAppBarDefaults.topAppBarColors(
                    containerColor = colorScheme.surface
                )
            )
        },
        containerColor = colorScheme.background
    ) { padding ->
        LazyColumn(
            modifier = Modifier.fillMaxSize().padding(padding),
            contentPadding = PaddingValues(16.dp),
            verticalArrangement = Arrangement.spacedBy(12.dp)
        ) {
            // 说明
            item {
                Card(
                    modifier = Modifier.fillMaxWidth(),
                    shape = MaterialTheme.shapes.medium,
                    colors = CardDefaults.cardColors(
                        containerColor = colorScheme.surfaceContainerHigh
                    )
                ) {
                    Row(
                        modifier = Modifier.fillMaxWidth().padding(16.dp),
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        Icon(
                            Icons.Default.Speed,
                            null,
                            Modifier.size(24.dp),
                            tint = colorScheme.primary
                        )
                        Spacer(Modifier.width(12.dp))
                        Text(
                            "分别测试 API 接口和图片 CDN 的响应延迟，选择延迟最低的线路以获得最佳体验",
                            style = MaterialTheme.typography.bodySmall,
                            color = colorScheme.onSurfaceVariant,
                            lineHeight = 20.sp
                        )
                    }
                }
            }

            // ==================== API 线路 ====================
            item {
                Row(
                    modifier = Modifier.fillMaxWidth().padding(horizontal = 4.dp, vertical = 4.dp),
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    val apiProgress = if (testingPhase == "api" && testingIndex >= 0) {
                        "（${testingIndex + 1}/${totalCount}）"
                    } else ""
                    InfoHeader(
                        title = "API 线路（当前：${ApiClientFactory.getCurrentCdnName()}）$apiProgress",
                        icon = Icons.Default.Api
                    )
                }
            }

            // API 测试结果
            itemsIndexed(apiResults) { index, result ->
                LineTestCard(
                    result = result,
                    isCurrent = index == currentCdnIndex,
                    latencyColor = latencyColor(result.latency),
                    latencyText = latencyText(result),
                    isTestingNow = isTesting && testingPhase == "api" && index == testingIndex,
                    onClick = {
                        if (result.isSuccess && result.latency >= 0) {
                            showSelectDialog = true
                        }
                    }
                )
            }

            // ==================== 图片 CDN 线路 ====================
            item {
                Spacer(Modifier.height(8.dp))
                Row(
                    modifier = Modifier.fillMaxWidth().padding(horizontal = 4.dp, vertical = 4.dp),
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    val imageProgress = if (testingPhase == "image" && testingIndex >= 0) {
                        "（${testingIndex + 1}/${totalCount}）"
                    } else ""
                    InfoHeader(
                        title = "图片 CDN 线路$imageProgress",
                        icon = Icons.Default.Image
                    )
                }
            }

            // 图片 CDN 测试结果
            itemsIndexed(imageResults) { index, result ->
                LineTestCard(
                    result = result,
                    isCurrent = index == currentCdnIndex,
                    latencyColor = latencyColor(result.latency),
                    latencyText = latencyText(result),
                    isTestingNow = isTesting && testingPhase == "image" && index == testingIndex,
                    onClick = {} // 图片 CDN 不支持直接切换
                )
            }

            // 测试中提示
            if (isTesting) {
                item {
                    Box(
                        modifier = Modifier.fillMaxWidth().padding(vertical = 24.dp),
                        contentAlignment = Alignment.Center
                    ) {
                        Column(horizontalAlignment = Alignment.CenterHorizontally) {
                            CircularProgressIndicator(
                                modifier = Modifier.size(32.dp),
                                strokeWidth = 3.dp,
                                color = colorScheme.primary
                            )
                            Spacer(Modifier.height(12.dp))
                            Text(
                                "正在测试各线路延迟...",
                                style = MaterialTheme.typography.bodySmall,
                                color = colorScheme.onSurfaceVariant
                            )
                        }
                    }
                }
            }

            // 重新测试按钮
            item {
                Spacer(Modifier.height(4.dp))
                Button(
                    onClick = {
                        if (!isTesting) {
                            isTesting = true
                            runAllTests(
                                scope = scope,
                                onProgress = { phase, index, total, partialApi, partialImage ->
                                    testingPhase = phase
                                    testingIndex = index
                                    totalCount = total
                                    if (partialApi != null) apiResults = partialApi
                                    if (partialImage != null) imageResults = partialImage
                                },
                                onComplete = { api, image ->
                                    apiResults = api
                                    imageResults = image
                                    isTesting = false
                                    testingPhase = ""
                                    testingIndex = -1
                                }
                            )
                        }
                    },
                    modifier = Modifier.fillMaxWidth().padding(horizontal = 4.dp),
                    enabled = !isTesting,
                    shape = MaterialTheme.shapes.medium,
                    colors = ButtonDefaults.buttonColors(
                        containerColor = colorScheme.primary
                    )
                ) {
                    Icon(
                        Icons.Default.Refresh,
                        null,
                        Modifier.size(18.dp)
                    )
                    Spacer(Modifier.width(8.dp))
                    Text(if (isTesting) "测试中..." else "重新测试", style = MaterialTheme.typography.bodyMedium)
                }
            }

            // 底部间距
            item { Spacer(Modifier.height(16.dp)) }
        }
    }

    // 选择 API 线路对话框
    if (showSelectDialog) {
        AlertDialog(
            onDismissRequest = { showSelectDialog = false },
            confirmButton = {
                TextButton(onClick = { showSelectDialog = false }) {
                    Text("取消")
                }
            },
            title = { Text("选择要切换的线路") },
            shape = MaterialTheme.shapes.medium,
            containerColor = colorScheme.surfaceContainerHigh,
            text = {
                Column {
                    apiResults.forEachIndexed { index, result ->
                        val isSuccess = result.isSuccess && result.latency >= 0
                        Row(
                            modifier = Modifier
                                .fillMaxWidth()
                                .padding(horizontal = 12.dp, vertical = 8.dp),
                            verticalAlignment = Alignment.CenterVertically
                        ) {
                            RadioButton(
                                selected = index == currentCdnIndex,
                                onClick = null,
                                enabled = isSuccess
                            )
                            Spacer(Modifier.width(12.dp))
                            Column(modifier = Modifier.weight(1f)) {
                                Text(
                                    result.name,
                                    style = MaterialTheme.typography.bodyLarge,
                                    color = if (isSuccess) colorScheme.onSurface
                                    else colorScheme.onSurfaceVariant
                                )
                                if (isSuccess) {
                                    Text(
                                        "${result.latency} ms",
                                        style = MaterialTheme.typography.bodySmall,
                                        color = colorScheme.onSurfaceVariant
                                    )
                                } else {
                                    Text(
                                        result.errorMsg.ifEmpty { "不可用" },
                                        style = MaterialTheme.typography.bodySmall,
                                        color = colorScheme.error
                                    )
                                }
                            }
                            if (isSuccess) {
                                TextButton(
                                    onClick = {
                                        onSelectCdn(index)
                                        showSelectDialog = false
                                    }
                                ) {
                                    Text("切换")
                                }
                            }
                        }
                        if (index < apiResults.lastIndex) {
                            HorizontalDivider(
                                color = colorScheme.outlineVariant.opacity50,
                                modifier = Modifier.padding(horizontal = 12.dp)
                            )
                        }
                    }
                }
            }
        )
    }
}

/**
 * 线路测试卡片
 */
@Composable
private fun LineTestCard(
    result: LineTestResult,
    isCurrent: Boolean,
    latencyColor: Color,
    latencyText: String,
    isTestingNow: Boolean = false,
    onClick: () -> Unit = {}
) {
    val colorScheme = MaterialTheme.colorScheme

    CommonCard(
        variant = CardVariant.Filled,
        onClick = onClick.takeIf { result.isSuccess && result.latency >= 0 && !isTestingNow }
    ) {
        Row(
            modifier = Modifier.fillMaxWidth().padding(horizontal = 16.dp, vertical = 14.dp),
            verticalAlignment = Alignment.CenterVertically
        ) {
            // 状态图标
            Box(
                modifier = Modifier
                    .size(40.dp)
                    .background(
                        when {
                            isTestingNow -> colorScheme.tertiaryContainer
                            result.isSuccess && result.latency >= 0 -> colorScheme.primaryContainer
                            else -> colorScheme.errorContainer
                        },
                        RoundedCornerShape(10.dp)
                    ),
                contentAlignment = Alignment.Center
            ) {
                if (isTestingNow) {
                    CircularProgressIndicator(
                        modifier = Modifier.size(20.dp),
                        strokeWidth = 2.dp,
                        color = colorScheme.tertiary
                    )
                } else {
                    Icon(
                        imageVector = when {
                            result.isSuccess && result.latency >= 0 -> Icons.Default.Speed
                            else -> Icons.Default.ErrorOutline
                        },
                        contentDescription = null,
                        modifier = Modifier.size(20.dp),
                        tint = when {
                            result.isSuccess && result.latency >= 0 -> colorScheme.primary
                            else -> colorScheme.error
                        }
                    )
                }
            }

            Spacer(Modifier.width(14.dp))

            // 名称和 URL
            Column(modifier = Modifier.weight(1f)) {
                Row(verticalAlignment = Alignment.CenterVertically) {
                    Text(
                        result.name,
                        style = MaterialTheme.typography.bodyMedium,
                        fontWeight = FontWeight.Medium,
                        color = colorScheme.onSurface
                    )
                    if (isCurrent) {
                        Spacer(Modifier.width(8.dp))
                        Surface(
                            shape = RoundedCornerShape(4.dp),
                            color = colorScheme.primary.copy(alpha = 0.15f)
                        ) {
                            Text(
                                "当前",
                                style = MaterialTheme.typography.labelSmall,
                                color = colorScheme.primary,
                                modifier = Modifier.padding(horizontal = 6.dp, vertical = 2.dp)
                            )
                        }
                    }
                }
                Spacer(Modifier.height(2.dp))
                Text(
                    result.url,
                    style = MaterialTheme.typography.bodySmall,
                    color = colorScheme.onSurfaceVariant.opacity60,
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis
                )
            }

            Spacer(Modifier.width(12.dp))

            // 延迟数值
            Column(horizontalAlignment = Alignment.End) {
                if (isTestingNow) {
                    Text(
                        "测试中...",
                        style = MaterialTheme.typography.titleSmall,
                        fontWeight = FontWeight.Bold,
                        color = colorScheme.tertiary
                    )
                } else {
                    Text(
                        latencyText,
                        style = MaterialTheme.typography.titleSmall,
                        fontWeight = FontWeight.Bold,
                        color = latencyColor
                    )
                    if (result.isSuccess && result.latency >= 0) {
                        Text(
                            when {
                                result.latency < 300 -> "流畅"
                                result.latency < 800 -> "正常"
                                result.latency < 1500 -> "较慢"
                                else -> "缓慢"
                            },
                            style = MaterialTheme.typography.labelSmall,
                            color = colorScheme.onSurfaceVariant.opacity60
                        )
                    }
                }
            }
        }
    }
}

/**
 * 测试单个 URL 的延迟
 */
private fun testSingleUrl(
    client: OkHttpClient,
    name: String,
    url: String,
    type: TestLineType
): LineTestResult {
    return try {
        val startTime = System.currentTimeMillis()
        val request = Request.Builder()
            .url(url)
            .head()
            .build()
        val response = client.newCall(request).execute()
        val elapsed = System.currentTimeMillis() - startTime
        response.close()
        LineTestResult(name, url, elapsed, true, type)
    } catch (e: Exception) {
        val errorMsg = when {
            e.message?.contains("timeout", ignoreCase = true) == true -> "超时"
            e.message?.contains("connect", ignoreCase = true) == true -> "连接失败"
            e.message?.contains("resolve", ignoreCase = true) == true -> "DNS解析失败"
            else -> e.message?.take(30) ?: "未知错误"
        }
        LineTestResult(name, url, -1, false, type, errorMsg)
    }
}

/**
 * 运行全部线路测试（API + 图片 CDN），支持逐项进度回调
 */
private fun runAllTests(
    scope: CoroutineScope,
    onProgress: (phase: String, index: Int, total: Int, partialApi: List<LineTestResult>?, partialImage: List<LineTestResult>?) -> Unit,
    onComplete: (apiResults: List<LineTestResult>, imageResults: List<LineTestResult>) -> Unit
) {
    scope.launch(Dispatchers.IO) {
        val client = OkHttpClient.Builder()
            .connectTimeout(8, TimeUnit.SECONDS)
            .readTimeout(5, TimeUnit.SECONDS)
            .followRedirects(true)
            .build()

        val apiUrls = ApiClientFactory.getApiUrlList()
        val apiNames = ApiClientFactory.getApiUrlNames()
        val cdnUrls = ApiClientFactory.getCdnUrlList()
        val cdnNames = ApiClientFactory.getCdnUrlNames()

        // 用占位结果初始化列表
        val placeholdersApi = apiUrls.mapIndexed { index, url ->
            val name = apiNames.getOrElse(index) { "分流${index + 1}" }
            LineTestResult(name, url, -1, false, TestLineType.API, "等待测试")
        }
        val placeholdersImage = cdnUrls.mapIndexed { index, url ->
            val name = cdnNames.getOrElse(index) { "图片分流${index + 1}" }
            LineTestResult(name, url, -1, false, TestLineType.IMAGE, "等待测试")
        }
        withContext(Dispatchers.Main) {
            onProgress("api", 0, apiUrls.size, placeholdersApi, placeholdersImage)
        }

        // 逐项测试 API 线路（基于占位列表原地替换，保持所有项始终可见）
        val apiResults = placeholdersApi.toMutableList()
        for ((index, url) in apiUrls.withIndex()) {
            val name = apiNames.getOrElse(index) { "分流${index + 1}" }
            val result = testSingleUrl(client, name, url, TestLineType.API)
            apiResults[index] = result
            withContext(Dispatchers.Main) {
                onProgress("api", index + 1, apiUrls.size, apiResults.toList(), placeholdersImage)
            }
        }

        // 逐项测试图片 CDN 线路（基于占位列表原地替换，保持所有项始终可见）
        val imageResults = placeholdersImage.toMutableList()
        for ((index, url) in cdnUrls.withIndex()) {
            val name = cdnNames.getOrElse(index) { "图片分流${index + 1}" }
            val result = testSingleUrl(client, name, url, TestLineType.IMAGE)
            imageResults[index] = result
            withContext(Dispatchers.Main) {
                onProgress("image", index + 1, cdnUrls.size, apiResults.toList(), imageResults.toList())
            }
        }

        withContext(Dispatchers.Main) {
            onComplete(apiResults.toList(), imageResults.toList())
        }
    }
}
