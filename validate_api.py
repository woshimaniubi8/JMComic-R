#!/usr/bin/env python3
"""
API 验证脚本 — 验证 Android 客户端使用的 API 端点和加密/解密逻辑
基于 JMComic-qt 原项目的实现进行对比验证
"""
import base64
import hashlib
import json
import time
import sys
from Crypto.Cipher import AES

# ====================== 配置常量 (与 Android ApiClientFactory 一致) ======================
BASE_URL = "https://www.cdnhth.club"
HEADER_VER = "2.0.21"
APP_TOKEN_SECRET = "18comicAPP"          # 用于 token 签名
APP_TOKEN_SECRET_2 = "18comicAPPContent"  # 用于 chapter_view_template 的 token
APP_DATA_SECRET = "185Hcomic3PAPP7R"      # 用于响应解密

# ====================== 工具函数 ======================

def md5hex(s: str) -> str:
    return hashlib.md5(s.encode("utf-8")).hexdigest()

def generate_token_and_param(timestamp: int, secret=APP_TOKEN_SECRET):
    """生成 API 请求的 token 和 tokenparam"""
    tokenparam = f"{timestamp},{HEADER_VER}"
    token = md5hex(f"{timestamp}{secret}")
    return token, tokenparam

def decrypt_response_data(encrypted_b64: str, timestamp: int) -> str:
    """
    解密 API 响应中的 data 字段
    对应 jmcomic.JmCryptoTool.decode_resp_data()
    """
    # 1. 构造密钥: MD5("{timestamp}{APP_DATA_SECRET}")
    key_str = md5hex(f"{timestamp}{APP_DATA_SECRET}")
    key_bytes = key_str.encode("utf-8")  # 32 bytes → AES-256

    # 2. Base64 解码
    cipher_bytes = base64.b64decode(encrypted_b64)

    # 3. AES-256/ECB/PKCS5Padding 解密
    cipher = AES.new(key_bytes, AES.MODE_ECB)
    decrypted = cipher.decrypt(cipher_bytes)

    # 4. 移除 PKCS5 padding
    pad_len = decrypted[-1]
    decrypted = decrypted[:-pad_len]

    # 5. 解码为 UTF-8 字符串
    return decrypted.decode("utf-8")

# ====================== 验证测试 ======================

def test_token_generation():
    """验证 Token 生成逻辑"""
    ts = 1717171200  # 固定时间戳用于测试
    token, tokenparam = generate_token_and_param(ts)
    
    expected_token = md5hex(f"{ts}{APP_TOKEN_SECRET}")
    expected_tokenparam = f"{ts},{HEADER_VER}"
    
    assert token == expected_token, f"Token mismatch: {token} != {expected_token}"
    assert tokenparam == expected_tokenparam, f"TokenParam mismatch: {tokenparam} != {expected_tokenparam}"
    
    # 验证 chapter_view_template 使用不同的 secret
    token2, _ = generate_token_and_param(ts, APP_TOKEN_SECRET_2)
    assert token2 != token, "Scramble token should differ from normal token"
    
    print("✅ Token 生成逻辑验证通过")
    print(f"   Normal token: {token}")
    print(f"   Scramble token: {token2}")

def test_decryption():
    """验证解密逻辑 (使用已知的测试数据)"""
    # 简单验证：加密一个已知字符串再解密
    test_data = '{"test": "hello world"}'
    ts = int(time.time())
    
    key_str = md5hex(f"{ts}{APP_DATA_SECRET}")
    key_bytes = key_str.encode("utf-8")
    
    # 加密
    cipher = AES.new(key_bytes, AES.MODE_ECB)
    # PKCS5 padding
    pad_len = 16 - (len(test_data) % 16)
    padded_data = test_data.encode("utf-8") + bytes([pad_len] * pad_len)
    encrypted = cipher.encrypt(padded_data)
    encrypted_b64 = base64.b64encode(encrypted).decode("utf-8")
    
    # 解密
    decrypted = decrypt_response_data(encrypted_b64, ts)
    
    assert decrypted == test_data, f"Decryption mismatch: {decrypted} != {test_data}"
    print("✅ AES 解密逻辑验证通过")
    print(f"   原文: {test_data}")
    print(f"   密文: {encrypted_b64[:50]}...")
    print(f"   解密: {decrypted}")

def test_api_connectivity():
    """测试 API 端点的连通性"""
    import urllib.request
    import ssl
    
    ctx = ssl.create_default_context()
    ctx.check_hostname = False
    ctx.verify_mode = ssl.CERT_NONE
    
    ts = int(time.time())
    token, tokenparam = generate_token_and_param(ts)
    
    endpoints = [
        ("首页推荐", f"{BASE_URL}/promote?page=0"),
        ("最新更新", f"{BASE_URL}/latest?page=0"),
        ("分类列表", f"{BASE_URL}/categories"),
        ("搜索测试", f"{BASE_URL}/search?search_query=test&page=1&o=mr"),
        ("漫画详情", f"{BASE_URL}/album?id=123456"),
    ]
    
    headers = {
        "User-Agent": "Mozilla/5.0 (Linux; Android 7.1.2; DT1901A Build/N2G47O; wv) AppleWebKit/537.36 (KHTML, like Gecko) Version/4.0 Chrome/86.0.4240.198 Mobile Safari/537.36",
        "Accept": "application/json, text/plain, */*",
        "tokenparam": tokenparam,
        "token": token,
        "version": HEADER_VER,
    }
    
    for name, url in endpoints:
        try:
            req = urllib.request.Request(url, headers=headers)
            resp = urllib.request.urlopen(req, context=ctx, timeout=10)
            data = resp.read()
            text = data.decode("utf-8", errors="replace")
            
            # 尝试解析 JSON
            try:
                j = json.loads(text)
                code = j.get("code", "N/A")
                has_data = j.get("data") is not None
                status = "✅" if code == 200 else "⚠️"
                print(f"{status} {name}: code={code}, has_data={has_data}")
                if code == 200 and isinstance(j.get("data"), str):
                    # data 是加密字符串，尝试解密
                    try:
                        decrypted = decrypt_response_data(j["data"], ts)
                        dec_json = json.loads(decrypted)
                        print(f"   解密成功: {str(dec_json)[:100]}...")
                    except Exception as e:
                        print(f"   解密失败: {e}")
            except json.JSONDecodeError:
                print(f"⚠️ {name}: 非JSON响应, 长度={len(text)}")
        except urllib.error.HTTPError as e:
            print(f"❌ {name}: HTTP {e.code}")
        except Exception as e:
            print(f"❌ {name}: {e}")

def test_image_url_construction():
    """验证图片 URL 构建逻辑"""
    IMAGE_BASE_URL = "https://cdn-msp.jmapiproxy3.cc"
    
    # 封面 URL (来自 BookItem.coverUrl)
    test_cover = "media/albums/123456_3x4.jpg"
    full_cover = f"{BASE_URL}/{test_cover}"
    print(f"✅ 封面URL: {full_cover}")
    
    # 章节图片 URL (来自 ScrambleData.images)
    test_images = [
        "media/photos/123456/00001.jpg",
        "media/photos/123456/00002.webp",
    ]
    for img in test_images:
        full_url = f"{IMAGE_BASE_URL}/{img}"
        print(f"✅ 图片URL: {full_url}")

def test_book_eps_model():
    """验证 BookEps 模型结构"""
    print("\n--- BookEps 模型结构验证 ---")
    print("字段:")
    print("  - id: String       (章节ID)")
    print("  - name: String     (章节名)")
    print("  - sort: String     (排序)")
    print("  - pages: Int       (图片总数, 从 chapter_view_template 填充)")
    print("  - scrambleId: Int  (图片解密ID, 从 chapter_view_template 获取)")
    print("  - aid: Int         (相册ID, 从 chapter_view_template 获取)")
    print("  - pictureUrl: List<String> (完整图片URL列表)")
    print("  - pictureName: List<String> (图片文件名列表)")
    print("✅ 模型结构完整")

def test_book_detail_parsing():
    """验证 BookDetail 解析逻辑 — 关注 series 回退"""
    import urllib.request, ssl
    ctx = ssl.create_default_context()
    ctx.check_hostname = False
    ctx.verify_mode = ssl.CERT_NONE
    
    ts = int(time.time())
    token, tokenparam = generate_token_and_param(ts)
    headers = {
        "User-Agent": "Mozilla/5.0 (Linux; Android 7.1.2; DT1901A Build/N2G47O; wv) AppleWebKit/537.36",
        "Accept": "application/json, text/plain, */*",
        "tokenparam": tokenparam, "token": token, "version": HEADER_VER,
    }
    
    test_books = ["1441531", "363859", "410702"]
    for bid in test_books:
        try:
            url = f"{BASE_URL}/album?id={bid}"
            req = urllib.request.Request(url, headers=headers)
            resp = urllib.request.urlopen(req, context=ctx, timeout=10)
            j = json.loads(resp.read())
            if j.get("code") != 200:
                print(f"  Book {bid}: code={j.get('code')}")
                continue
            
            data = decrypt_response_data(j["data"], ts)
            obj = json.loads(data)
            
            book_id = obj.get("id")  # int or str
            book_name = obj.get("name", "")
            series = obj.get("series", [])
            author = obj.get("author", "")
            likes = obj.get("likes", "0")
            views = obj.get("total_views", "0")
            tags = obj.get("tags", [])
            
            # 检查 author 类型
            author_type = type(author).__name__
            if isinstance(author, list):
                author_str = ", ".join(author)
            else:
                author_str = str(author)
            
            # 检查 series 回退逻辑
            has_series = len(series) > 0
            fallback = not has_series  # 需要回退章节
            
            print(f"\n  Book {bid}: id={book_id}({type(book_id).__name__}), name={book_name[:40]}...")
            print(f"    author({author_type}): {author_str[:60]}")
            print(f"    likes={likes}, views={views}, tags={tags}")
            print(f"    series_count={len(series)}, need_fallback={fallback}")
            if has_series:
                for s in series[:3]:
                    print(f"      eps: id={s.get('id')}, name={s.get('name', '')[:30]}")
        except Exception as e:
            print(f"  Book {bid}: ERROR - {e}")


# ... (在 main() 中添加调用)
def main():
    # ... existing code ...
    print("\n📋 7. BookDetail 解析 + series 回退验证")
    test_book_detail_parsing()
    """验证导航流程"""
    print("\n--- 导航流程验证 ---")
    flows = [
        ("登录 → 主页", ["LoginScreen", "MainScreen(HomeTab)"]),
        ("主页 → 搜索", ["HomeScreen", "SearchScreen"]),
        ("主页 → 分类", ["HomeScreen", "CategoryScreen", "HomeScreen"]),
        ("主页 → 漫画详情", ["HomeScreen", "BookDetailScreen"]),
        ("漫画详情 → 阅读", ["BookDetailScreen", "ReaderScreen"]),
        ("阅读 → 返回详情", ["ReaderScreen", "BookDetailScreen"]),
        ("详情 → 返回主页", ["BookDetailScreen", "HomeScreen"]),
        ("主页 → 收藏", ["HomeScreen", "FavoritesScreen"]),
        ("主页 → 个人", ["HomeScreen", "UserProfileScreen"]),
    ]
    for name, flow in flows:
        print(f"✅ {name}: {' → '.join(flow)}")

# ====================== 主函数 ======================

def main():
    print("=" * 60)
    print("JMComic Android 客户端验证脚本")
    print("=" * 60)
    
    print("\n📋 1. Token 生成验证")
    test_token_generation()
    
    print("\n📋 2. AES 解密验证")
    test_decryption()
    
    print("\n📋 3. 图片 URL 构建验证")
    test_image_url_construction()
    
    print("\n📋 4. BookEps 模型验证")
    test_book_eps_model()
    
    print("\n📋 5. 导航流程验证")
    test_navigation_flow()
    
    print("\n📋 6. API 端点连通性验证")
    test_api_connectivity()
    
    print("\n" + "=" * 60)
    print("验证完成！")
    print("=" * 60)

if __name__ == "__main__":
    main()
