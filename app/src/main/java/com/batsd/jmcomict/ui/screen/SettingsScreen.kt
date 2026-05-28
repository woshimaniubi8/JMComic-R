package com.batsd.jmcomict.ui.screen

import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.automirrored.filled.Logout
import androidx.compose.material.icons.filled.*
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.runtime.rememberCoroutineScope
import com.batsd.jmcomict.R
import com.batsd.jmcomict.ui.components.*
import kotlinx.coroutines.launch

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun SettingsScreen(
    cdnName: String = "分流1",
    cdnIndex: Int = 0,
    cdnCount: Int = 4,
    isDarkTheme: Boolean = false,
    themeMode: Int = 0,
    isLoggedIn: Boolean = false,
    onBackClick: () -> Unit,
    onSelectCdn: (Int) -> Unit,
    onSetThemeMode: (Int) -> Unit,
    onLogoutClick: () -> Unit,
    onLineTestClick: (() -> Unit)? = null,
    onShowDisclaimer: (() -> Unit)? = null,
    onAboutClick: (() -> Unit)? = null
) {
    var showCdnDialog by remember { mutableStateOf(false) }
    var showThemeDialog by remember { mutableStateOf(false) }
    var showAboutDialog by remember { mutableStateOf(false) }
    val colorScheme = MaterialTheme.colorScheme
    val modeNames = listOf("跟随系统", "浅色", "深色")
    val toast = LocalToast.current
    val scope = rememberCoroutineScope()
    val context = LocalContext.current
    val versionName = remember {
        try {
            @Suppress("DEPRECATION")
            context.packageManager.getPackageInfo(context.packageName, 0).versionName.orEmpty()
        } catch (e: Exception) {
            ""
        }
    }
    val userAgreementLabel = stringResource(R.string.user_agreement)
    val aboutLabel = stringResource(R.string.about)

    Scaffold(
        topBar = {
            TopAppBar(
                title = { Text("设置") },
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
            item { InfoHeader(title = "网络", icon = Icons.Default.Language) }
            item {
                CommonCard(variant = CardVariant.Filled, onClick = { showCdnDialog = true }) {
                    Row(
                        modifier = Modifier.fillMaxWidth().padding(horizontal = 16.dp, vertical = 14.dp),
                        verticalAlignment = Alignment.CenterVertically,
                        horizontalArrangement = Arrangement.SpaceBetween
                    ) {
                        Row(verticalAlignment = Alignment.CenterVertically) {
                            Icon(Icons.Default.SwapHoriz, null, Modifier.size(20.dp), tint = colorScheme.primary)
                            Spacer(Modifier.width(12.dp))
                            Text("分流切换", style = MaterialTheme.typography.bodyMedium, color = colorScheme.onSurface)
                        }
                        Row(verticalAlignment = Alignment.CenterVertically) {
                            Text(cdnName, style = MaterialTheme.typography.bodySmall, color = colorScheme.onSurfaceVariant.opacity60)
                            Spacer(Modifier.width(4.dp))
                            Icon(Icons.Default.ChevronRight, null, Modifier.size(18.dp), tint = colorScheme.onSurfaceVariant.opacity38)
                        }
                    }
                }
            }
            item {
                CommonCard(variant = CardVariant.Filled, onClick = { onLineTestClick?.invoke() }) {
                    Row(
                        modifier = Modifier.fillMaxWidth().padding(horizontal = 16.dp, vertical = 14.dp),
                        verticalAlignment = Alignment.CenterVertically,
                        horizontalArrangement = Arrangement.SpaceBetween
                    ) {
                        Row(verticalAlignment = Alignment.CenterVertically) {
                            Icon(Icons.Default.Speed, null, Modifier.size(20.dp), tint = colorScheme.primary)
                            Spacer(Modifier.width(12.dp))
                            Text("线路测试", style = MaterialTheme.typography.bodyMedium, color = colorScheme.onSurface)
                        }
                        Icon(Icons.Default.ChevronRight, null, Modifier.size(18.dp), tint = colorScheme.onSurfaceVariant.opacity38)
                    }
                }
            }
            item { Spacer(Modifier.height(8.dp)); InfoHeader(title = "外观", icon = Icons.Default.Palette) }
            item {
                CommonCard(variant = CardVariant.Filled, onClick = { showThemeDialog = true }) {
                    Row(
                        modifier = Modifier.fillMaxWidth().padding(horizontal = 16.dp, vertical = 14.dp),
                        verticalAlignment = Alignment.CenterVertically,
                        horizontalArrangement = Arrangement.SpaceBetween
                    ) {
                        Row(verticalAlignment = Alignment.CenterVertically) {
                            Icon(if (isDarkTheme) Icons.Default.DarkMode else Icons.Default.LightMode, null, Modifier.size(20.dp), tint = colorScheme.primary)
                            Spacer(Modifier.width(12.dp))
                            Text("主题模式", style = MaterialTheme.typography.bodyMedium, color = colorScheme.onSurface)
                        }
                        Row(verticalAlignment = Alignment.CenterVertically) {
                            Text(modeNames[themeMode.coerceIn(0, 2)], style = MaterialTheme.typography.bodySmall, color = colorScheme.onSurfaceVariant.opacity60)
                            Spacer(Modifier.width(4.dp))
                            Icon(Icons.Default.ChevronRight, null, Modifier.size(18.dp), tint = colorScheme.onSurfaceVariant.opacity38)
                        }
                    }
                }
            }

            if (isLoggedIn) {
                item { Spacer(Modifier.height(8.dp)); InfoHeader(title = "账户", icon = Icons.Default.Person) }
                item {
                    CommonCard(variant = CardVariant.Filled, onClick = onLogoutClick) {
                        Row(
                            modifier = Modifier.fillMaxWidth().padding(horizontal = 16.dp, vertical = 14.dp),
                            verticalAlignment = Alignment.CenterVertically
                        ) {
                            Icon(Icons.AutoMirrored.Filled.Logout, null, Modifier.size(20.dp), tint = colorScheme.error.copy(alpha = 0.7f))
                            Spacer(Modifier.width(12.dp))
                            Text("退出登录", style = MaterialTheme.typography.bodyMedium, color = colorScheme.error)
                        }
                    }
                }
            }
            item { Spacer(Modifier.height(8.dp)); InfoHeader(title = "更多", icon = Icons.Default.Info) }
            item {
                CommonCard(variant = CardVariant.Filled, onClick = {
                    if (onShowDisclaimer != null) {
                        onShowDisclaimer()
                    } else {
                        scope.launch { toast(userAgreementLabel) }
                    }
                }) {
                    Row(
                        modifier = Modifier.fillMaxWidth().padding(horizontal = 16.dp, vertical = 14.dp),
                        verticalAlignment = Alignment.CenterVertically,
                        horizontalArrangement = Arrangement.SpaceBetween
                    ) {
                        Row(verticalAlignment = Alignment.CenterVertically) {
                            Icon(Icons.Default.Policy, null, Modifier.size(20.dp), tint = colorScheme.primary)
                            Spacer(Modifier.width(12.dp))
                            Text(stringResource(R.string.user_agreement), style = MaterialTheme.typography.bodyMedium, color = colorScheme.onSurface)
                        }
                        Icon(Icons.Default.ChevronRight, null, Modifier.size(18.dp), tint = colorScheme.onSurfaceVariant.opacity38)
                    }
                }
            }
            item {
                CommonCard(variant = CardVariant.Filled, onClick = {
                    if (onAboutClick != null) {
                        onAboutClick()
                    } else {
                        showAboutDialog = true
                    }
                }) {
                    Row(
                        modifier = Modifier.fillMaxWidth().padding(horizontal = 16.dp, vertical = 14.dp),
                        verticalAlignment = Alignment.CenterVertically,
                        horizontalArrangement = Arrangement.SpaceBetween
                    ) {
                        Row(verticalAlignment = Alignment.CenterVertically) {
                            Icon(Icons.Default.Info, null, Modifier.size(20.dp), tint = colorScheme.primary)
                            Spacer(Modifier.width(12.dp))
                            Text(stringResource(R.string.about), style = MaterialTheme.typography.bodyMedium, color = colorScheme.onSurface)
                        }
                        Row(verticalAlignment = Alignment.CenterVertically) {
                            Text("v${versionName.ifEmpty { "?" }}", style = MaterialTheme.typography.bodySmall, color = colorScheme.onSurfaceVariant.opacity60)
                            Spacer(Modifier.width(8.dp))
                            Icon(Icons.Default.ChevronRight, null, Modifier.size(18.dp), tint = colorScheme.onSurfaceVariant.opacity38)
                        }
                    }
                }
            }
        }
    }

    if (showCdnDialog) {
        AlertDialog(
            onDismissRequest = { showCdnDialog = false },
            confirmButton = { TextButton(onClick = { showCdnDialog = false }) { Text("取消") } },
            title = { Text("选择线路") },
            shape = MaterialTheme.shapes.medium,
            containerColor = colorScheme.surfaceContainerHigh,
            text = {
                Column {
                    (0 until cdnCount).forEach { index ->
                        val label = "分流${index + 1}"
                        Row(
                            Modifier.fillMaxWidth()
                                .clickable { onSelectCdn(index); showCdnDialog = false }
                                .padding(horizontal = 12.dp, vertical = 12.dp),
                            verticalAlignment = Alignment.CenterVertically
                        ) {
                            RadioButton(selected = cdnIndex == index, onClick = null)
                            Spacer(Modifier.width(12.dp))
                            Text(label, style = MaterialTheme.typography.bodyLarge)
                        }
                    }
                }
            }
        )
    }

    if (showThemeDialog) {
        AlertDialog(
            onDismissRequest = { showThemeDialog = false },
            confirmButton = { TextButton(onClick = { showThemeDialog = false }) { Text("取消") } },
            title = { Text("选择主题") },
            shape = MaterialTheme.shapes.medium,
            containerColor = colorScheme.surfaceContainerHigh,
            text = {
                Column {
                    listOf("跟随系统", "浅色", "深色").forEachIndexed { index, name ->
                        Row(Modifier.fillMaxWidth().clickable { onSetThemeMode(index); showThemeDialog = false }.padding(horizontal = 12.dp, vertical = 12.dp),
                            verticalAlignment = Alignment.CenterVertically) {
                            RadioButton(selected = themeMode == index, onClick = null)
                            Spacer(Modifier.width(12.dp))
                            Text(name, style = MaterialTheme.typography.bodyLarge)
                        }
                    }
                }
            }
        )
    }

    if (showAboutDialog) {
        AlertDialog(
            onDismissRequest = { showAboutDialog = false },
            confirmButton = { TextButton(onClick = { showAboutDialog = false }) { Text(stringResource(R.string.confirm)) } },
            title = { Text(stringResource(R.string.about_title)) },
            text = {
                Column {
                    Text(stringResource(R.string.about_content))
                    Spacer(Modifier.height(12.dp))
                    Text(
                        "v${versionName.ifEmpty { "?" }}",
                        style = MaterialTheme.typography.bodySmall,
                        color = colorScheme.onSurfaceVariant
                    )
                }
            },
            shape = MaterialTheme.shapes.medium,
            containerColor = colorScheme.surfaceContainerHigh,
        )
    }
}