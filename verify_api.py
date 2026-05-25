"""
验证脚本：测试 JM API 端点并验证 descramble 逻辑
用于调试 OpenJM Android 项目的三个问题：
1. 图片解密后存在条纹
2. 首页漫画详情页数据显示异常
3. Tags 显示问题（UI层面）

用法：python verify_api.py
"""
import base64
import hashlib
import json
import re
import ssl
import time
import urllib.request
from Crypto.Cipher import AES

# === 配置 (与 Android 项目 ApiClientFactory 一致) ===
BASE_URL = 'https://www.cdnhth.club'
IMAGE_BASE_URL = 'https://cdn-msp.jmapiproxy3.cc'
HEADER_VER = '2.0.21'
APP_TOKEN_SECRET = '18comicAPP'
APP_TOKEN_SECRET_2 = '18comicAPPContent'
APP_DATA_SECRET = '185Hcomic3PAPP7R'

def md5hex(s):
    return hashlib.md5(s.encode('utf-8')).hexdigest()

def make_headers(use_secret2=False):
    ts = int(time.time())
    secret = APP_TOKEN_SECRET_2 if use_secret2 else APP_TOKEN_SECRET
    return {
        'User-Agent': 'Mozilla/5.0 (Linux; Android 7.1.2; DT1901A Build/N2G47O; wv) AppleWebKit/537.36',
        'Accept': 'application/json, text/plain, */*',
        'tokenparam': f'{ts},{HEADER_VER}',
        'token': md5hex(f'{ts}{secret}'),
        'version': HEADER_VER,
    }, ts

def api_get(path, use_secret2=False):
    headers, ts = make_headers(use_secret2)
    url = f'{BASE_URL}{path}'
    ctx = ssl.create_default_context()
    ctx.check_hostname = False
    ctx.verify_mode = ssl.CERT_NONE
    req = urllib.request.Request(url, headers=headers)
    resp = urllib.request.urlopen(req, context=ctx, timeout=15)
    raw = resp.read()
    return raw, ts, resp.status

def decrypt_data(encrypted_b64, ts):
    """解密 API 响应中的 data 字段"""
    key_str = md5hex(f'{ts}{APP_DATA_SECRET}').encode()
    cipher_bytes = base64.b64decode(encrypted_b64)
    cipher = AES.new(key_str, AES.MODE_ECB)
    decrypted = cipher.decrypt(cipher_bytes)
    # 去除 PKCS5 padding
    pad_len = decrypted[-1]
    return decrypted[:-pad_len].decode('utf-8')

def get_segmentation_num(epsId, scramble_id, pictureName):
    """对照 Qt 项目的 GetSegmentationNum"""
    scramble_id = int(scramble_id)
    epsId = int(epsId)
    if epsId < scramble_id:
        return 0
    elif epsId < 268850:
        return 10
    elif epsId > 421926:
        s = str(epsId) + pictureName
        s = hashlib.md5(s.encode()).hexdigest()
        num = ord(s[-1])
        num %= 8
        return num * 2 + 2
    else:
        s = str(epsId) + pictureName
        s = hashlib.md5(s.encode()).hexdigest()
        num = ord(s[-1])
        num %= 10
        return num * 2 + 2


print("=" * 60)
print("测试 1: /chapter_view_template 端点")
print("=" * 60)

# 使用已知的章节 ID 测试
test_eps_id = "1441524"
raw, ts, status = api_get(f'/chapter_view_template?id={test_eps_id}&mode=vertical&page=0&app_img_shunt=NaN', use_secret2=True)
text = raw.decode('utf-8', errors='replace')
print(f"HTTP Status: {status}")
print(f"Response length: {len(text)}")
print(f"Response first 500 chars:\n{text[:500]}")
print(f"\nIs JSON? {text.strip().startswith('{')}")

# 尝试用正则提取 scramble_id（Qt 项目的方式）
mo = re.search(r"(?<=var scramble_id = )\w+", text)
if mo:
    print(f"scramble_id (regex from HTML): {mo.group()}")
else:
    print("scramble_id NOT found via regex!")

# 尝试用 JSON 解析
try:
    j = json.loads(text)
    print(f"JSON parsed! code={j.get('code')}, has data={bool(j.get('data'))}")
    if j.get('code') == 200 and isinstance(j.get('data'), str):
        decrypted = decrypt_data(j['data'], ts)
        print(f"Decrypted data: {decrypted[:500]}")
        dj = json.loads(decrypted)
        print(f"Decrypted JSON keys: {list(dj.keys())}")
        for k, v in dj.items():
            print(f"  {k}: {type(v).__name__} = {str(v)[:200]}")
except Exception as e:
    print(f"JSON parse failed: {e}")
    print("→ 端点返回的是 HTML/JS，不是 JSON！Android 的 ScrambleData 解析会失败。")

print()
print("=" * 60)
print("测试 2: /album 端点 (漫画详情)")
print("=" * 60)

for bid in ['1441531', '363859']:
    raw, ts, status = api_get(f'/album?id={bid}')
    j = json.loads(raw)
    print(f"\nBook {bid}: code={j.get('code')}, status={status}")
    if j.get('code') == 200 and isinstance(j.get('data'), str):
        decrypted = decrypt_data(j['data'], ts)
        dj = json.loads(decrypted)
        print(f"  Keys: {list(dj.keys())}")
        for k in ['id', 'name', 'likes', 'total_views', 'description', 'tags', 'author', 'series', 'is_favorite']:
            v = dj.get(k)
            print(f"  {k}: {type(v).__name__} = {str(v)[:100] if v is not None else 'None'}")
        
        # 检查 description 字段
        desc = dj.get('description')
        print(f"\n  Description type: {type(desc).__name__}")
        print(f"  Description value: {str(desc)[:200]}")
        
        # 检查 likes 和 total_views 的类型
        print(f"  likes type: {type(dj.get('likes')).__name__} = {dj.get('likes')}")
        print(f"  total_views type: {type(dj.get('total_views')).__name__} = {dj.get('total_views')}")

print()
print("=" * 60)
print("测试 3: /chapter 端点 (章节详情)")
print("=" * 60)

raw, ts, status = api_get(f'/chapter?id={test_eps_id}')
j = json.loads(raw)
print(f"HTTP Status: {status}, code={j.get('code')}")
if j.get('code') == 200 and isinstance(j.get('data'), str):
    decrypted = decrypt_data(j['data'], ts)
    dj = json.loads(decrypted)
    print(f"Keys: {list(dj.keys())}")
    print(f"id: {dj.get('id')} (type={type(dj.get('id')).__name__})")
    print(f"series_id: {dj.get('series_id')} (type={type(dj.get('series_id')).__name__})")
    images = dj.get('images', [])
    print(f"images count: {len(images)}")
    if images:
        print(f"First image: {images[0]}")

print()
print("=" * 60)
print("测试 4: 验证 descramble 算法 (Kotlin vs Python)")
print("=" * 60)

# 模拟一个实际场景
test_cases = [
    # (epsId, scramble_id, pictureName/url)
    (1441524, 0, "00001"),       # scrambleId 为 0 (Android fallback 情况)
    (1441524, 220980, "00001"),  # 正常 scrambleId (Qt fallback)
]

for eps_id, scr_id, pic_name in test_cases:
    num = get_segmentation_num(eps_id, scr_id, pic_name)
    print(f"epsId={eps_id}, scrambleId={scr_id}, pic={pic_name} → num={num}")

# 对比: Android getNum 使用 aid=epsId (从 URL 提取)
# 测试 Android 路径 (getNumFromUrl → getNum)
# URL: https://cdn-msp.jmapiproxy3.cc/media/photos/1441524/00001.jpg
url = f"{IMAGE_BASE_URL}/media/photos/{test_eps_id}/00001.jpg"
parts = url.split("/")
aid_from_url = parts[-2]  # should be epsId
filename_from_url = parts[-1].split(".")[0]
print(f"\nFrom URL '{url}':")
print(f"  aid (from URL) = {aid_from_url}")  
print(f"  filename = {filename_from_url}")

# 测试不同 scrambleId 值的效果
for scr_id in [0, 220980, "0", "220980"]:
    num = get_segmentation_num(aid_from_url, scr_id, filename_from_url)
    print(f"  scrambleId={scr_id} → num={num}")

print()
print("=" * 60)
print("总结")
print("=" * 60)
print("""
如果 /chapter_view_template 返回 HTML 而非 JSON:
  → Android 的 ScrambleData JSON 解析必然失败
  → scrambleId 保持默认值 0
  → 所有图片用 scrambleId=0 计算分割数
  → 分割数可能不正确 → 图片出现条纹

修复方案:
1. 为 chapter_view_template 端点添加原始响应处理
2. 用正则提取 scramble_id (同 Qt 项目)
3. 失败时默认使用 220980 (而非 0)
""")
