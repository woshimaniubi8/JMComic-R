# JM漫画 API 文档

## API 基础信息

- **基础 URL**: `https://api.jmcomic.com/`
- **格式**: RESTful API
- **数据格式**: JSON
- **认证**: Token-based (Cookie)

## 通用响应格式

所有 API 响应都遵循以下格式：

```json
{
    "code": 200,
    "message": "success",
    "data": {}
}
```

### 响应码说明

| 代码 | 说明 |
|------|------|
| 200 | 成功 |
| 400 | 请求错误 |
| 401 | 未授权 |
| 403 | 禁止访问 |
| 404 | 未找到 |
| 500 | 服务器错误 |

## API 端点

### 用户相关

#### 登录
```
POST /api/user/login

请求体:
{
    "username": "user",
    "password": "pass"
}

响应:
{
    "code": 200,
    "data": {
        "uid": "12345",
        "name": "username",
        "level": "5",
        "coin": 100,
        "title": "VIP"
    }
}
```

#### 注册
```
POST /api/user/register

请求体:
{
    "username": "newuser",
    "password": "pass",
    "email": "user@example.com"
}

响应: 同登录
```

#### 获取用户信息
```
GET /api/user/profile

请求头:
Authorization: Bearer {token}

响应: 同登录
```

### 漫画相关

#### 搜索漫画
```
GET /api/book/search?q={query}&offset={offset}&limit={limit}

参数:
- q: 搜索关键词
- offset: 偏移量（默认0）
- limit: 每页数量（默认20）

响应:
{
    "code": 200,
    "data": {
        "count": 100,
        "limit": 20,
        "offset": 0,
        "results": [
            {
                "book_id": "123",
                "title": "漫画标题",
                "cover": "https://...",
                "author": "作者",
                "total_likes": 1000
            }
        ]
    }
}
```

#### 获取漫画列表
```
GET /api/book/list?offset={offset}&limit={limit}&category={category}

参数:
- offset: 偏移量（默认0）
- limit: 每页数量（默认20）
- category: 分类ID（可选）

响应: 同搜索
```

#### 获取漫画详情
```
GET /api/book/{bookId}

参数:
- bookId: 漫画ID

响应:
{
    "code": 200,
    "data": {
        "book_id": "123",
        "title": "漫画标题",
        "author": "作者",
        "cover": "https://...",
        "description": "漫画描述",
        "category": ["热血", "冒险"],
        "status": "连载中",
        "total_likes": 1000,
        "total_views": 10000,
        "eps_list": [
            {
                "index": 0,
                "title": "第一章",
                "eps_name": "开始",
                "eps_url": "...",
                "eps_id": "eps1",
                "time": "2024-01-01",
                "pages": 20
            }
        ]
    }
}
```

#### 获取章节详情
```
GET /api/book/{bookId}/episode/{epsId}

参数:
- bookId: 漫画ID
- epsId: 章节ID

响应:
{
    "code": 200,
    "data": {
        "index": 0,
        "title": "第一章",
        "eps_name": "开始",
        "eps_id": "eps1",
        "pages": 20,
        "picture_url": {
            "0": "https://image1.jpg",
            "1": "https://image2.jpg"
        }
    }
}
```

#### 获取章节图片列表
```
GET /api/book/{bookId}/episode/{epsId}/images

参数:
- bookId: 漫画ID
- epsId: 章节ID

响应:
{
    "code": 200,
    "data": [
        "https://image1.jpg",
        "https://image2.jpg",
        "https://image3.jpg"
    ]
}
```

### 分类相关

#### 获取所有分类
```
GET /api/category

响应:
{
    "code": 200,
    "data": {
        "results": [
            {
                "id": "1",
                "name": "热血",
                "slug": "hot-blood",
                "type": "tag",
                "total": 1000
            }
        ]
    }
}
```

#### 获取分类下的漫画
```
GET /api/category/{categoryId}/books?offset={offset}&limit={limit}

参数:
- categoryId: 分类ID
- offset: 偏移量（默认0）
- limit: 每页数量（默认20）

响应: 同漫画列表
```

### 评论相关

#### 获取漫画评论
```
GET /api/book/{bookId}/comments?offset={offset}&limit={limit}

参数:
- bookId: 漫画ID
- offset: 偏移量（默认0）
- limit: 每页数量（默认20）

响应:
{
    "code": 200,
    "data": [
        {
            "id": "comment1",
            "uid": "user123",
            "name": "用户名",
            "level": "5",
            "title": "VIP",
            "date": "2024-01-01",
            "content": "评论内容",
            "head_url": "https://...",
            "like": 10,
            "sub_comments": []
        }
    ]
}
```

#### 发布评论
```
POST /api/book/{bookId}/comments

请求头:
Authorization: Bearer {token}

请求体:
{
    "content": "我的评论"
}

响应: 返回创建的评论对象
```

### 收藏相关

#### 获取收藏列表
```
GET /api/user/favorites?offset={offset}&limit={limit}

请求头:
Authorization: Bearer {token}

参数:
- offset: 偏移量（默认0）
- limit: 每页数量（默认20）

响应: 同漫画列表
```

#### 添加收藏
```
POST /api/book/{bookId}/favorite

请求头:
Authorization: Bearer {token}

参数:
- bookId: 漫画ID

响应:
{
    "code": 200,
    "message": "收藏成功"
}
```

#### 移除收藏
```
DELETE /api/book/{bookId}/favorite

请求头:
Authorization: Bearer {token}

参数:
- bookId: 漫画ID

响应:
{
    "code": 200,
    "message": "取消收藏成功"
}
```

### 历史记录相关

#### 获取历史记录
```
GET /api/user/history?offset={offset}&limit={limit}

请求头:
Authorization: Bearer {token}

参数:
- offset: 偏移量（默认0）
- limit: 每页数量（默认20）

响应: 同漫画列表
```

#### 记录阅读历史
```
POST /api/user/history/{bookId}/{epsId}?page={page}

请求头:
Authorization: Bearer {token}

参数:
- bookId: 漫画ID
- epsId: 章节ID
- page: 当前页码（默认0）

响应:
{
    "code": 200,
    "message": "记录成功"
}
```

## 错误处理

### 常见错误

#### 401 未授权
```json
{
    "code": 401,
    "message": "Invalid token"
}
```

解决方案：
- 检查 token 是否过期
- 重新登录获取新 token

#### 404 未找到
```json
{
    "code": 404,
    "message": "Resource not found"
}
```

解决方案：
- 检查请求路径
- 检查资源ID是否正确

#### 429 请求过频繁
```json
{
    "code": 429,
    "message": "Rate limit exceeded"
}
```

解决方案：
- 减少请求频率
- 实现请求缓存

## 速率限制

- **限制**: 每分钟 60 个请求
- **响应头**: 包含 `X-RateLimit-*` 信息
- **超限**: 返回 429 状态码

## 请求头要求

所有请求都应包含以下请求头：

```
User-Agent: JMComic Android Client
Accept: application/json
Content-Type: application/json
```

## 认证

### Cookie 认证

登录成功后，API 会返回 Cookie，后续请求需要携带：

```
Cookie: session_id=...; path=/
```

### Token 认证

某些 API 端点需要 Bearer token：

```
Authorization: Bearer {token}
```

## 图片 URL 处理

所有图片 URL 都是完整的绝对路径，可以直接在 Coil 中使用：

```kotlin
AsyncImage(
    model = "https://image.jmcomic.com/book/123/page.jpg",
    contentDescription = null
)
```

## 分页

所有列表 API 都支持分页：

```
GET /api/books?offset=0&limit=20

// 获取第二页
GET /api/books?offset=20&limit=20
```

## 性能建议

1. **缓存**: 缓存漫画详情和分类列表
2. **预加载**: 预加载可能被请求的数据
3. **压缩**: 图片应该使用压缩格式
4. **超时**: 设置合理的请求超时（30秒）

---

更新时间: 2024-01-01
API 版本: 1.0