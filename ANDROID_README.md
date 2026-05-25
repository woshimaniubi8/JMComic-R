# JM漫画 - Android 客户端

这是禁漫天堂（JM漫画）的Android版本客户端，基于现有的Qt版本Windows客户端进行开发。

## 功能特性

- ✅ 用户登录/注册
- ✅ 漫画搜索和浏览
- ✅ 漫画分类浏览
- ✅ 漫画详情页面
- ✅ 在线阅读漫画
- ✅ 收藏漫画
- ✅ 阅读历史记录
- ✅ 用户信息管理
- 🔄 漫画下载（开发中）
- 🔄 离线阅读（开发中）

## 项目结构

```
app/src/main/java/com/batsd/openjm/
├── data/
│   ├── api/              # API 接口定义和网络配置
│   │   ├── JMComicApiService.kt
│   │   └── ApiClientFactory.kt
│   ├── local/            # 本地数据存储
│   │   └── PreferencesManager.kt
│   ├── model/            # 数据模型
│   │   ├── User.kt
│   │   ├── Book.kt
│   │   ├── Category.kt
│   │   ├── Comment.kt
│   │   └── Download.kt
│   └── repository/       # 数据仓库（Repository Pattern）
│       ├── UserRepository.kt
│       ├── BookRepository.kt
│       ├── CategoryRepository.kt
│       └── CommentRepository.kt
├── ui/
│   ├── screen/           # UI 屏幕
│   │   ├── LoginScreen.kt
│   │   ├── HomeScreen.kt
│   │   ├── SearchScreen.kt
│   │   ├── BookDetailScreen.kt
│   │   ├── ReaderScreen.kt
│   │   ├── CategoryScreen.kt
│   │   ├── FavoritesScreen.kt
│   │   └── UserProfileScreen.kt
│   ├── viewmodel/        # ViewModel
│   │   ├── UserViewModel.kt
│   │   ├── BookViewModel.kt
│   │   └── CategoryViewModel.kt
│   ├── components/       # 通用 UI 组件
│   │   ├── Dialogs.kt
│   │   └── Placeholders.kt
│   ├── navigation/       # 导航管理
│   │   └── AppNavigation.kt
│   └── theme/            # 主题配置
│       └── Theme.kt
├── utils/                # 工具类
│   ├── ToastUtil.kt
│   └── FileUtil.kt
└── MainActivity.kt       # 主活动
```

## 技术栈

### Android & Jetpack
- **Jetpack Compose** - 现代化的UI框架
- **Lifecycle & ViewModel** - 生命周期管理
- **Kotlin Coroutines** - 异步编程

### 网络和数据
- **Retrofit 2** - HTTP 客户端
- **OkHttp 3** - HTTP 拦截器
- **Kotlinx Serialization** - JSON 序列化
- **Coil** - 图片加载库

### 架构
- **MVVM** - Model-View-ViewModel 架构
- **Repository Pattern** - 数据层抽象
- **Clean Architecture** - 分层架构

## 开发指南

### 设置开发环境

1. **克隆项目**
```bash
git clone <repository-url>
cd OpenJM
```

2. **打开项目**
使用 Android Studio 打开项目

3. **同步 Gradle**
```bash
./gradlew sync
```

4. **构建和运行**
```bash
./gradlew build
./gradlew installDebug
```

### 添加新功能

#### 1. 定义数据模型
在 `data/model/` 中创建新的数据类：
```kotlin
@Serializable
data class MyModel(
    @SerialName("field_name")
    val fieldName: String = ""
)
```

#### 2. 添加 API 接口
在 `data/api/JMComicApiService.kt` 中添加新的 API 端点：
```kotlin
@GET("api/endpoint")
suspend fun getMyData(): ApiResponse<MyModel>
```

#### 3. 创建 Repository
创建 `data/repository/MyRepository.kt`：
```kotlin
class MyRepository(private val apiService: JMComicApiService) {
    suspend fun getMyData(): Result<MyModel> {
        // 实现获取数据的逻辑
    }
}
```

#### 4. 创建 ViewModel
创建 `ui/viewmodel/MyViewModel.kt`：
```kotlin
class MyViewModel(private val repository: MyRepository) : ViewModel() {
    private val _data = MutableStateFlow<MyModel?>(null)
    val data: StateFlow<MyModel?> = _data
    
    fun loadData() {
        viewModelScope.launch {
            repository.getMyData()
                .onSuccess { _data.value = it }
                .onFailure { /* 处理错误 */ }
        }
    }
}
```

#### 5. 创建 UI 屏幕
创建 `ui/screen/MyScreen.kt`：
```kotlin
@Composable
fun MyScreen(
    viewModel: MyViewModel,
    onNavigate: () -> Unit
) {
    val data by viewModel.data.collectAsState()
    
    // 构建 UI
}
```

### API 配置

API 基础 URL 在 `data/api/ApiClientFactory.kt` 中配置：
```kotlin
private const val BASE_URL = "https://api.jmcomic.com/"
```

### 本地存储

使用 `PreferencesManager` 进行本地数据存储：
```kotlin
val prefsManager = PreferencesManager(context)
prefsManager.saveUser(userId, userName, token)
val token = prefsManager.getToken()
```

## 依赖版本

| 库 | 版本 |
|---|---|
| Kotlin | 2.2.10 |
| Gradle AGP | 9.2.1 |
| Jetpack Compose | 2026.02.01 |
| Retrofit | 2.9.0 |
| OkHttp | 4.11.0 |
| Coil | 2.4.0 |

## 网络请求说明

所有网络请求都使用以下流程：

1. **ViewModel** 中的方法被调用
2. **Repository** 执行网络请求
3. **API Service** 通过 Retrofit 发送 HTTP 请求
4. **OkHttp** 处理网络层细节
5. 响应通过 **Kotlinx Serialization** 反序列化
6. **Result** 对象返回给 ViewModel
7. ViewModel 更新 **StateFlow**
8. UI 通过 **collectAsState()** 响应更新

## 用户界面流程

### 登录流程
```
LoginScreen → UserViewModel.login() → UserRepository.login() 
→ API.login() → 保存用户信息 → HomeScreen
```

### 浏览漫画流程
```
HomeScreen → BookViewModel.searchBooks() → BookRepository.searchBooks()
→ API.searchBooks() → 更新 bookList StateFlow → 重新渲染网格
```

### 阅读漫画流程
```
HomeScreen → BookDetailScreen → ReaderScreen
→ BookViewModel.getEpisodeImages() → BookRepository.getEpisodeImages()
→ API.getEpisodeImages() → 显示图片
```

## 常见问题

### Q: 如何更改 API 端点？
A: 在 `ApiClientFactory.kt` 中修改 `BASE_URL` 常量。

### Q: 如何添加新的请求头？
A: 在 `createHeaderInterceptor()` 方法中添加新的 header。

### Q: 如何实现图片缓存？
A: Coil 会自动处理图片缓存。可以在 `ImageCache` 配置中调整缓存大小。

### Q: 如何处理 API 错误？
A: 所有 Repository 方法都返回 `Result` 对象，使用 `onSuccess` 和 `onFailure` 处理结果。

## 状态管理

项目使用 `StateFlow` 管理应用状态：

```kotlin
// ViewModel 中
private val _uiState = MutableStateFlow<UiState>(UiState.Loading)
val uiState: StateFlow<UiState> = _uiState

// UI 中
val state by viewModel.uiState.collectAsState()
```

## 协程使用

所有异步操作都使用 Kotlin Coroutines：

```kotlin
viewModelScope.launch {
    val result = repository.getData()
    result.onSuccess { data -> _data.value = data }
}
```

## 性能优化建议

1. **图片加载**: 使用 Coil 的 `crossfade` 和缓存功能
2. **列表渲染**: 使用 `LazyColumn` 和 `LazyVerticalGrid` 进行虚拟化
3. **数据缓存**: 在 Repository 层实现简单的内存缓存
4. **网络请求**: 使用适当的超时配置（30 秒）

## 测试

项目包含以下测试类型：

- **单元测试**: 测试业务逻辑
- **UI 测试**: 使用 Compose Test 框架
- **集成测试**: 测试 API 集成

## 构建和部署

### 调试构建
```bash
./gradlew installDebug
```

### 发布构建
```bash
./gradlew assembleRelease
./gradlew bundleRelease
```

### 签名配置
在 `local.properties` 中配置签名密钥。

## 贡献指南

1. Fork 项目
2. 创建功能分支 (`git checkout -b feature/AmazingFeature`)
3. 提交更改 (`git commit -m 'Add some AmazingFeature'`)
4. 推送到分支 (`git push origin feature/AmazingFeature`)
5. 开启 Pull Request

## 许可证

该项目遵循原 JMComic-qt 项目的许可证。仅供技术研究使用，请勿用于其他目的。

## 致谢

- 感谢原 JMComic-qt 项目的贡献者
- 感谢 Jetpack Compose 和各个开源库的开发者

## 联系方式

如有问题或建议，欢迎提交 Issue 或 Pull Request。

---

**注意**: 该项目仅供学习和技术研究使用。使用时请遵守相关法律法规。
