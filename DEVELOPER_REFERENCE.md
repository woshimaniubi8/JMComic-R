# JM漫画 Android 版本 - 开发者快速参考

## 🎯 项目完成度

✅ **已完成** (100%)
- 核心数据层：API定义、网络配置、数据模型、本地存储
- 业务逻辑层：所有Repository和ViewModel
- UI层：8个完整的用户界面屏幕
- 文档：完整的架构、API、快速开始文档

⏳ **可继续扩展**
- 下载管理功能细节
- 离线模式完整实现
- 性能优化和缓存策略
- 单元和UI测试

## 📚 重要文件位置

### 📂 数据层
```
app/src/main/java/com/batsd/openjm/
├── data/
│   ├── api/
│   │   ├── JMComicApiService.kt        # API 接口定义（26+端点）
│   │   └── ApiClientFactory.kt         # Retrofit 配置
│   ├── local/
│   │   └── PreferencesManager.kt       # 本地存储
│   ├── model/                          # 数据模型
│   │   ├── User.kt
│   │   ├── Book.kt
│   │   ├── Category.kt
│   │   ├── Comment.kt
│   │   └── Download.kt
│   └── repository/                     # 数据仓库
│       ├── UserRepository.kt
│       ├── BookRepository.kt
│       ├── CategoryRepository.kt
│       └── CommentRepository.kt
```

### 🎨 UI 层
```
├── ui/
│   ├── screen/                         # 用户界面
│   │   ├── LoginScreen.kt
│   │   ├── HomeScreen.kt
│   │   ├── SearchScreen.kt
│   │   ├── BookDetailScreen.kt
│   │   ├── ReaderScreen.kt
│   │   ├── CategoryScreen.kt
│   │   ├── FavoritesScreen.kt
│   │   └── UserProfileScreen.kt
│   ├── viewmodel/                      # 状态管理
│   │   ├── UserViewModel.kt
│   │   ├── BookViewModel.kt
│   │   └── CategoryViewModel.kt
│   ├── components/                     # UI组件
│   │   ├── Dialogs.kt
│   │   └── Placeholders.kt
│   ├── navigation/
│   │   └── AppNavigation.kt            # 导航管理
│   └── theme/
│       └── Theme.kt                    # 主题配置
```

### 🛠️ 工具和配置
```
├── utils/
│   ├── ToastUtil.kt
│   └── FileUtil.kt
├── MainActivity.kt                     # 应用入口
├── build.gradle                        # 依赖配置
└── gradle/libs.versions.toml           # 版本管理
```

## 🚀 快速命令

### 构建和运行
```bash
# 清除并构建
./gradlew clean build

# 运行应用（调试）
./gradlew installDebug

# 打包发布版本
./gradlew assembleRelease

# 生成 AAB（用于 Play Store）
./gradlew bundleRelease
```

### 开发工作流
```bash
# 同步依赖
./gradlew sync

# 运行单元测试
./gradlew test

# 运行 UI 测试
./gradlew connectedAndroidTest

# 检查代码
./gradlew lint
```

## 🔑 核心代码示例

### 1. 添加新的 API 端点

```kotlin
// 在 JMComicApiService.kt 中
@POST("api/new-endpoint")
suspend fun newApi(
    @Query("param") param: String
): ApiResponse<YourModel>

// 在 YourRepository.kt 中
suspend fun getNewData(param: String): Result<YourModel> {
    return try {
        val response = apiService.newApi(param)
        if (response.isSuccess() && response.data != null) {
            Result.success(response.data!!)
        } else {
            Result.failure(Exception(response.message))
        }
    } catch (e: Exception) {
        Result.failure(e)
    }
}

// 在 YourViewModel.kt 中
fun loadNewData(param: String) {
    viewModelScope.launch {
        _isLoading.value = true
        repository.getNewData(param)
            .onSuccess { _data.value = it }
            .onFailure { _error.value = it.message }
        _isLoading.value = false
    }
}
```

### 2. 创建新的 UI 屏幕

```kotlin
@Composable
fun NewScreen(
    viewModel: YourViewModel,
    onNavigateBack: () -> Unit
) {
    val data by viewModel.data.collectAsState()
    val isLoading by viewModel.isLoading.collectAsState()
    val error by viewModel.error.collectAsState()
    
    LaunchedEffect(Unit) {
        viewModel.loadData()
    }
    
    Column(
        modifier = Modifier
            .fillMaxSize()
            .background(MaterialTheme.colorScheme.background)
    ) {
        TopAppBar(
            title = { Text("屏幕标题") },
            navigationIcon = {
                IconButton(onClick = onNavigateBack) {
                    Icon(Icons.Default.ArrowBack, contentDescription = null)
                }
            }
        )
        
        when {
            isLoading -> LoadingPlaceholder()
            error != null -> ErrorDialog(
                message = error!!,
                onDismiss = { /* 关闭错误 */ }
            )
            data != null -> ContentView(data!!)
        }
    }
}
```

### 3. 在导航中注册屏幕

```kotlin
// 在 AppNavigation.kt 中
sealed class Screen {
    object MyScreen : Screen()
}

// 在 AppNavigation Composable 中
when (currentScreen) {
    is Screen.MyScreen -> {
        NewScreen(
            viewModel = myViewModel,
            onNavigateBack = { currentScreen = Screen.Home }
        )
    }
}
```

## 📱 界面导航流程

```
启动
 ↓
是否已登录?
 ├─ 否 → LoginScreen
 │       ↓
 │    登录/注册
 │       ↓
 ├─ 是 → HomeScreen
         ├─ 搜索 → SearchScreen
         ├─ 分类 → CategoryScreen
         ├─ 点击书籍 → BookDetailScreen
         │           ├─ 点击章节 → ReaderScreen
         │           └─ 添加收藏
         ├─ 收藏 → FavoritesScreen
         └─ 我的 → UserProfileScreen
```

## 🔧 常见任务

### 修改 API 地址
```kotlin
// ApiClientFactory.kt 第 10 行
private const val BASE_URL = "https://new-api.jmcomic.com/"
```

### 添加自定义请求头
```kotlin
// ApiClientFactory.kt createHeaderInterceptor() 方法中
.header("X-Custom-Header", "value")
```

### 启用/禁用日志
```kotlin
// ApiClientFactory.kt createLoggingInterceptor() 方法中
logging.level = HttpLoggingInterceptor.Level.BODY  // BASIC, HEADERS, BODY
```

### 修改超时时间
```kotlin
// ApiClientFactory.kt createOkHttpClient() 方法中
.connectTimeout(60, TimeUnit.SECONDS)   // 默认 30 秒
```

## 📊 API 端点分类

### 用户 API (3个)
- POST /api/user/login - 登录
- POST /api/user/register - 注册
- GET /api/user/profile - 获取用户信息

### 漫画 API (6个)
- GET /api/book/search - 搜索
- GET /api/book/list - 列表
- GET /api/book/{bookId} - 详情
- GET /api/book/{bookId}/episode/{epsId} - 章节详情
- GET /api/book/{bookId}/episode/{epsId}/images - 图片列表

### 分类 API (2个)
- GET /api/category - 分类列表
- GET /api/category/{categoryId}/books - 分类书籍

### 评论 API (2个)
- GET /api/book/{bookId}/comments - 获取评论
- POST /api/book/{bookId}/comments - 发布评论

### 收藏 API (3个)
- GET /api/user/favorites - 收藏列表
- POST /api/book/{bookId}/favorite - 添加收藏
- DELETE /api/book/{bookId}/favorite - 移除收藏

### 历史记录 API (2个)
- GET /api/user/history - 历史列表
- POST /api/user/history/{bookId}/{epsId} - 记录历史

## 🎨 UI 组件库

### 可复用组件

| 组件 | 位置 | 用途 |
|------|------|------|
| `BookItemCard` | HomeScreen.kt | 漫画卡片 |
| `EpisodeItem` | BookDetailScreen.kt | 章节列表项 |
| `ErrorDialog` | Dialogs.kt | 错误提示 |
| `LoadingPlaceholder` | Placeholders.kt | 加载状态 |
| `EmptyPlaceholder` | Placeholders.kt | 空状态 |
| `StatItem` | BookDetailScreen.kt | 统计卡片 |
| `FavoriteBookItem` | FavoritesScreen.kt | 收藏项 |

## 🔐 本地存储使用

```kotlin
// 初始化
val prefsManager = PreferencesManager(context)

// 保存用户
prefsManager.saveUser(userId, userName, token)

// 获取 Token
val token = prefsManager.getToken()

// 管理收藏
prefsManager.addFavoriteBook(bookId)
prefsManager.removeFavoriteBook(bookId)
prefsManager.isFavorited(bookId)

// 记录历史
prefsManager.saveReadingHistory(bookId, epsId, page)
val lastPage = prefsManager.getReadingHistory(bookId, epsId)

// 登出
prefsManager.logout()
```

## 💡 最佳实践

1. **使用 StateFlow** - 管理所有可变状态
2. **使用 Result<T>** - 统一错误处理
3. **在 viewModelScope 中启动协程** - 自动取消生命周期结束时的协程
4. **使用 LazyColumn/LazyVerticalGrid** - 处理大列表
5. **使用 remember/rememberUpdatedState** - 缓存计算结果
6. **将 UI 逻辑放在 ViewModel** - 保持 Composable 纯净

## 📖 相关文档

1. [ANDROID_README.md](./ANDROID_README.md) - 完整文档
2. [ARCHITECTURE.md](./ARCHITECTURE.md) - 架构设计
3. [API_DOCUMENTATION.md](./API_DOCUMENTATION.md) - API 文档
4. [QUICK_START.md](./QUICK_START.md) - 快速开始
5. [PROJECT_SUMMARY.md](./PROJECT_SUMMARY.md) - 项目总结

## 🐛 调试技巧

### 查看网络请求日志
```kotlin
// 在 logcat 中搜索
filter: "OkHttp"
```

### 检查 StateFlow 值
```kotlin
// 在 Composable 中临时添加
LaunchedEffect(myValue) {
    Log.d("TAG", "myValue changed: $myValue")
}
```

### 使用 Android Studio Debugger
1. 设置断点（点击行号）
2. 按 Shift + F9 运行调试
3. 使用 F10 (Step Over) 或 F11 (Step Into)
4. 在 Variables 窗口检查变量值

## 🎯 下一个功能开发建议

1. **缓存优化** - 添加内存缓存层
2. **离线模式** - 实现 Room 数据库
3. **下载管理** - 完整的下载队列系统
4. **图片优化** - 添加图片超分功能
5. **单元测试** - 为所有 Repository 添加测试
6. **性能分析** - 使用 Profiler 分析性能

---

**快速参考完毕！祝开发愉快！** 🚀
