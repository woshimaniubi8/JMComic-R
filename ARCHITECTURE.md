# JM漫画 Android 版本 - 架构设计文档

## 1. 架构概述

本项目采用 **MVVM + Repository Pattern** 架构，分为以下几层：

```
┌─────────────────────────────────────┐
│         UI Layer (Compose)           │
│  ├─ LoginScreen                     │
│  ├─ HomeScreen                      │
│  ├─ BookDetailScreen                │
│  ├─ ReaderScreen                    │
│  └─ ...                             │
└────────────┬────────────────────────┘
             │ observes StateFlow
┌────────────▼────────────────────────┐
│        ViewModel Layer               │
│  ├─ UserViewModel                   │
│  ├─ BookViewModel                   │
│  └─ CategoryViewModel               │
└────────────┬────────────────────────┘
             │ uses
┌────────────▼────────────────────────┐
│     Repository Layer                 │
│  ├─ UserRepository                  │
│  ├─ BookRepository                  │
│  ├─ CategoryRepository              │
│  └─ CommentRepository               │
└────────────┬────────────────────────┘
             │ uses
┌────────────▼────────────────────────┐
│      Data Layer                      │
│  ├─ API Service (Retrofit)          │
│  ├─ Local Storage (Preferences)     │
│  └─ Database (Future)               │
└──────────────────────────────────────┘
```

## 2. 各层详细说明

### 2.1 UI Layer (Presentation)

**职责**: 显示数据、接收用户输入、响应用户交互

**技术**: Jetpack Compose

**主要组件**:
- `LoginScreen`: 登录界面
- `HomeScreen`: 首页（漫画列表）
- `SearchScreen`: 搜索界面
- `BookDetailScreen`: 漫画详情
- `ReaderScreen`: 阅读界面
- `CategoryScreen`: 分类界面
- `FavoritesScreen`: 收藏列表
- `UserProfileScreen`: 用户信息

**特点**:
- 使用 Compose 声明式 UI
- 通过 StateFlow 订阅数据变化
- 完全响应式设计

### 2.2 ViewModel Layer

**职责**: 管理 UI 相关的业务逻辑和状态

**主要类**:
- `UserViewModel`: 用户相关逻辑
- `BookViewModel`: 漫画相关逻辑
- `CategoryViewModel`: 分类相关逻辑

**特点**:
- 继承 `ViewModel`，生命周期与 Activity/Fragment 绑定
- 使用 `viewModelScope` 管理协程
- 暴露 `StateFlow` 供 UI 观察

**示例**:
```kotlin
class BookViewModel(private val bookRepository: BookRepository) : ViewModel() {
    private val _bookList = MutableStateFlow<List<BookItem>>(emptyList())
    val bookList: StateFlow<List<BookItem>> = _bookList
    
    fun searchBooks(query: String) {
        viewModelScope.launch {
            bookRepository.searchBooks(query)
                .onSuccess { _bookList.value = it.results }
                .onFailure { /* 处理错误 */ }
        }
    }
}
```

### 2.3 Repository Layer

**职责**: 抽象数据源，提供统一的数据访问接口

**主要类**:
- `UserRepository`: 用户数据访问
- `BookRepository`: 漫画数据访问
- `CategoryRepository`: 分类数据访问
- `CommentRepository`: 评论数据访问

**特点**:
- 隐藏数据来源的细节（API、本地存储、数据库）
- 返回 `Result<T>` 类型
- 支持多数据源组合

**示例**:
```kotlin
class BookRepository(private val apiService: JMComicApiService) {
    suspend fun searchBooks(query: String): Result<BookListResponse> {
        return try {
            val response = apiService.searchBooks(query)
            if (response.isSuccess() && response.data != null) {
                Result.success(response.data!!)
            } else {
                Result.failure(Exception(response.message))
            }
        } catch (e: Exception) {
            Result.failure(e)
        }
    }
}
```

### 2.4 Data Layer

#### 2.4.1 API Service (Retrofit)

**职责**: 定义网络 API 接口

**主要组件**:
- `JMComicApiService`: API 接口定义
- `ApiClientFactory`: Retrofit 客户端工厂
- `ApiResponse<T>`: API 响应包装类

**特点**:
- 使用 Retrofit 框架
- 使用 Kotlinx Serialization 进行序列化
- OkHttp 作为 HTTP 客户端
- 支持自定义拦截器

#### 2.4.2 Local Storage

**职责**: 本地数据持久化

**主要组件**:
- `PreferencesManager`: SharedPreferences 管理
  - 用户信息保存
  - 阅读历史记录
  - 收藏列表
  - 应用偏好设置

**示例**:
```kotlin
val prefsManager = PreferencesManager(context)
prefsManager.saveUser(userId, userName, token)
val token = prefsManager.getToken()
```

#### 2.4.3 Models (Data Classes)

**数据模型**:
- `User`: 用户信息
- `BookDetail`: 漫画详情
- `BookItem`: 漫画列表项
- `BookEps`: 章节信息
- `Category`: 分类
- `CommentInfo`: 评论
- `DownloadTask`: 下载任务

## 3. 数据流

### 3.1 搜索漫画流程

```
User Input (SearchScreen)
    ↓
onSearchClick(query)
    ↓
BookViewModel.searchBooks(query)
    ↓
Repository.searchBooks(query)
    ↓
API.searchBooks(query)
    ↓
HTTP Request via Retrofit
    ↓
API Response (BookListResponse)
    ↓
Kotlinx Serialization deserialization
    ↓
Result<BookListResponse>
    ↓
ViewModel updates _bookList StateFlow
    ↓
UI collects state and re-renders
    ↓
GridList shows search results
```

### 3.2 登录流程

```
User Login (LoginScreen)
    ↓
onLoginClick(username, password)
    ↓
UserViewModel.login(username, password)
    ↓
Repository.login(username, password)
    ↓
API.login(username, password)
    ↓
API Response (User)
    ↓
Result.success(user)
    ↓
ViewModel saves user to SharedPreferences
    ↓
ViewModel updates _user StateFlow
    ↓
Navigation to HomeScreen
```

## 4. 网络层设计

### 4.1 请求流程

```
ViewModel
    ↓ 调用
Repository
    ↓ 调用
Retrofit Interface (JMComicApiService)
    ↓ 使用 Retrofit 执行
HTTP Client (OkHttp)
    ↓ 经过拦截器
RequestInterceptor (添加 header)
CookieInterceptor (添加 cookie)
LoggingInterceptor (日志记录)
    ↓ 发送
HTTP Request
    ↓ 接收
HTTP Response
    ↓ 反序列化
Kotlinx Serialization
    ↓ 返回
ApiResponse<T>
    ↓ 处理
Repository 检查状态
    ↓ 返回
Result<T>
```

### 4.2 错误处理

```
API Error
    ↓
Try-Catch Block in Repository
    ↓
Return Result.failure(Exception)
    ↓
ViewModel catches failure
    ↓
Update error StateFlow
    ↓
UI displays error message
```

## 5. 状态管理

### 5.1 StateFlow 使用

每个 ViewModel 都使用 `StateFlow` 管理状态：

```kotlin
private val _uiState = MutableStateFlow<UiState>(UiState.Loading)
val uiState: StateFlow<UiState> = _uiState
```

### 5.2 UI 订阅状态

```kotlin
@Composable
fun MyScreen(viewModel: MyViewModel) {
    val state by viewModel.uiState.collectAsState()
    
    when (state) {
        is UiState.Loading -> LoadingScreen()
        is UiState.Success -> SuccessScreen(state.data)
        is UiState.Error -> ErrorScreen(state.error)
    }
}
```

## 6. 依赖注入

当前项目使用手动依赖注入（在 MainActivity 中）：

```kotlin
val apiService = ApiClientFactory.getInstance(this)
val userRepository = UserRepository(apiService)
val userViewModel = UserViewModel(userRepository)
```

**未来可以考虑**：
- Hilt
- Dagger
- Koin

## 7. 并发模型

### 7.1 Coroutines

所有网络请求都在 Coroutine 中执行：

```kotlin
viewModelScope.launch {
    val result = repository.getData()
    // 处理结果
}
```

### 7.2 线程管理

- 网络请求在 IO 线程池中执行
- UI 更新在主线程中执行
- Coroutine 自动处理线程切换

## 8. 测试策略

### 8.1 单元测试

测试 Repository 和 ViewModel 的业务逻辑：

```kotlin
@Test
fun testSearchBooks() = runTest {
    val result = bookRepository.searchBooks("test")
    assertTrue(result.isSuccess)
}
```

### 8.2 UI 测试

使用 Compose Test 框架：

```kotlin
@Test
fun testLoginScreen() {
    composeTestRule.setContent {
        LoginScreen(...)
    }
    
    composeTestRule.onNodeWithText("登录").performClick()
}
```

## 9. 扩展性设计

### 9.1 添加新数据源

只需创建新的 Repository：

```kotlin
class BookDatabaseRepository(private val bookDao: BookDao) {
    fun getLocalBooks(): Flow<List<BookEntity>> {
        return bookDao.getAllBooks()
    }
}
```

### 9.2 添加新功能

只需创建新的 ViewModel 和 Screen。

## 10. 性能考虑

1. **图片加载**: 使用 Coil 进行异步加载和缓存
2. **列表渲染**: 使用 LazyColumn/LazyVerticalGrid 进行虚拟化
3. **网络请求**: 缓存常用数据
4. **内存管理**: ViewModel 生命周期管理

---

这个架构设计确保了代码的清晰性、可维护性和可扩展性。
