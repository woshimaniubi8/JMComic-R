# JM漫画 Android 版本 - 快速开始指南

## 1. 项目初始化

### 1.1 克隆和打开项目

```bash
# 克隆项目
git clone <repository-url>
cd OpenJM

# 用 Android Studio 打开项目
```

### 1.2 同步依赖

```bash
# 通过 Gradle 同步
./gradlew sync

# 或在 Android Studio 中选择 "Sync Now"
```

### 1.3 配置 local.properties

创建或编辑 `local.properties` 文件：

```properties
# SDK 路径
sdk.dir=/path/to/Android/Sdk

# 签名配置（可选）
keystore.file=/path/to/keystore
keystore.password=password
key.alias=key_alias
key.password=key_password
```

## 2. 构建和运行

### 2.1 调试构建

```bash
# 构建调试版本
./gradlew assembleDebug

# 或在 Android Studio 中点击 "Run" 按钮
# 快捷键: Shift + F10
```

### 2.2 发布构建

```bash
# 构建发布版本（需要签名配置）
./gradlew assembleRelease

# 生成 AAB（Android App Bundle）
./gradlew bundleRelease
```

## 3. 项目探索

### 3.1 查看项目结构

```
app/
├── src/
│   ├── main/
│   │   ├── java/com/batsd/openjm/
│   │   │   ├── data/              # 数据层
│   │   │   ├── ui/                # UI 层
│   │   │   ├── utils/             # 工具类
│   │   │   └── MainActivity.kt
│   │   ├── res/                   # 资源文件
│   │   └── AndroidManifest.xml
│   ├── test/                      # 单元测试
│   └── androidTest/               # UI 测试
├── build.gradle
└── proguard-rules.pro
```

### 3.2 主要文件说明

| 文件 | 说明 |
|------|------|
| `MainActivity.kt` | 应用入口点 |
| `AppNavigation.kt` | 导航管理 |
| `JMComicApiService.kt` | API 接口 |
| `*Repository.kt` | 数据仓库 |
| `*ViewModel.kt` | 视图模型 |
| `*Screen.kt` | UI 屏幕 |

## 4. 常见开发任务

### 4.1 添加新的 API 端点

1. 在 `JMComicApiService.kt` 中添加接口方法：

```kotlin
@GET("api/new-endpoint")
suspend fun getNewData(
    @Query("param") param: String
): ApiResponse<MyModel>
```

2. 在对应的 Repository 中添加方法：

```kotlin
suspend fun getNewData(param: String): Result<MyModel> {
    return try {
        val response = apiService.getNewData(param)
        if (response.isSuccess() && response.data != null) {
            Result.success(response.data!!)
        } else {
            Result.failure(Exception(response.message))
        }
    } catch (e: Exception) {
        Result.failure(e)
    }
}
```

3. 在 ViewModel 中添加使用：

```kotlin
fun loadNewData(param: String) {
    viewModelScope.launch {
        repository.getNewData(param)
            .onSuccess { _data.value = it }
            .onFailure { _error.value = it.message }
    }
}
```

4. 在 Screen 中使用：

```kotlin
@Composable
fun MyScreen(viewModel: MyViewModel) {
    val data by viewModel.data.collectAsState()
    
    LaunchedEffect(Unit) {
        viewModel.loadNewData("value")
    }
    
    when {
        data != null -> ShowData(data)
        else -> ShowLoading()
    }
}
```

### 4.2 修改 API 基础 URL

在 `ApiClientFactory.kt` 中修改：

```kotlin
private const val BASE_URL = "https://new-api-server.com/"
```

### 4.3 添加请求拦截器

在 `ApiClientFactory.kt` 的 `createOkHttpClient` 方法中添加：

```kotlin
.addInterceptor(createCustomInterceptor())

private fun createCustomInterceptor(): Interceptor {
    return Interceptor { chain ->
        val originalRequest = chain.request()
        val newRequest = originalRequest.newBuilder()
            .header("X-Custom-Header", "value")
            .build()
        chain.proceed(newRequest)
    }
}
```

### 4.4 调试网络请求

启用日志记录（默认已启用）：

```kotlin
// 在 createLoggingInterceptor 中
logging.level = HttpLoggingInterceptor.Level.BODY  // 显示请求和响应体
```

查看日志：
```bash
./gradlew run -i | grep "OkHttp"
```

### 4.5 测试 API

在 `test/` 目录中添加单元测试：

```kotlin
@Test
fun testSearchBooks() = runTest {
    val bookRepository = BookRepository(mockApiService)
    val result = bookRepository.searchBooks("test")
    assertTrue(result.isSuccess)
}
```

## 5. 常见问题解决

### 问题 1: Gradle 同步失败

**解决方案**:
```bash
# 清除 Gradle 缓存
./gradlew clean

# 重新同步
./gradlew sync
```

### 问题 2: 编译错误 - 未找到类

**解决方案**:
- 检查是否导入了正确的包
- 确保依赖已正确配置
- 重建项目: `./gradlew clean build`

### 问题 3: API 请求失败

**解决方案**:
1. 检查网络连接
2. 检查 API URL 是否正确
3. 检查请求头是否完整
4. 查看日志输出

### 问题 4: 图片无法加载

**解决方案**:
- 确保 Coil 依赖已添加
- 检查图片 URL 是否有效
- 检查网络权限是否在 AndroidManifest.xml 中声明

## 6. 性能优化建议

### 6.1 图片优化

```kotlin
// 使用 Coil 的缓存和交叉淡入
AsyncImage(
    model = imageUrl,
    contentDescription = null,
    contentScale = ContentScale.Crop,
    modifier = Modifier
        .width(200.dp)
        .height(300.dp)
)
```

### 6.2 列表优化

```kotlin
// 使用 LazyColumn 而不是 Column
LazyColumn(
    modifier = Modifier.fillMaxSize(),
    contentPadding = PaddingValues(8.dp),
    verticalArrangement = Arrangement.spacedBy(8.dp)
) {
    items(items.size) { index ->
        ItemCard(items[index])
    }
}
```

### 6.3 状态优化

```kotlin
// 使用 remember 避免重复计算
val filteredList = remember(bookList, searchQuery) {
    bookList.filter { it.title.contains(searchQuery) }
}
```

## 7. 发布流程

### 7.1 准备发布

1. 更新版本号（`build.gradle`）：
```gradle
versionCode = 2
versionName = "1.1.0"
```

2. 更新 `CHANGELOG.md`

3. 运行测试：
```bash
./gradlew test
```

### 7.2 签名和打包

```bash
# 生成签名密钥（首次）
keytool -genkey -v -keystore release.keystore \
  -keyalg RSA -keysize 2048 -validity 365

# 构建签名的 APK
./gradlew assembleRelease

# 验证签名
jarsigner -verify app/build/outputs/apk/release/app-release.apk
```

### 7.3 上传到 Play Store

1. 登录 [Google Play Console](https://play.google.com/console)
2. 创建新应用
3. 上传 APK 或 AAB
4. 填写应用信息和隐私政策
5. 提交审核

## 8. 代码规范

### 8.1 命名规范

```kotlin
// 常量
private const val MAX_PAGE_SIZE = 20

// 私有属性
private val _bookList = MutableStateFlow<List<BookItem>>(emptyList())

// 公开属性
val bookList: StateFlow<List<BookItem>> = _bookList

// 函数
fun getBookDetail(bookId: String) { }

// 类名
class BookViewModel { }
```

### 8.2 代码风格

```kotlin
// 使用类型推断
val books = listOf(book1, book2)

// 避免嵌套过深
if (condition1) {
    if (condition2) {
        // 避免
    }
}

// 改为
if (condition1 && condition2) {
    // 优先
}

// 使用作用域函数
bookRepository.apply {
    getBookList()
}.also { result ->
    _bookList.value = result
}
```

## 9. 调试技巧

### 9.1 使用 Logcat

```bash
# 过滤日志
./gradlew run -i | grep "MyTag"

# 显示详细日志
adb logcat *:V
```

### 9.2 使用断点

1. 在代码中设置断点（点击行号）
2. 运行调试版本（Shift + F9）
3. 程序会在断点处暂停
4. 使用 "Step Over" (F10) 或 "Step Into" (F11) 单步调试

### 9.3 使用 Layout Inspector

1. 运行应用
2. Android Studio → Tools → Layout Inspector
3. 检查 UI 层级和属性

## 10. 下一步

- 阅读 [完整 README](./ANDROID_README.md)
- 查看 [架构设计文档](./ARCHITECTURE.md)
- 参考 [API 文档](./API_DOCUMENTATION.md)
- 探索代码示例

---

需要帮助？查看项目 Issue 或创建新的 Issue！
