"""
验证脚本: 检查 API 响应中 id 字段类型，调试首页详情页数据问题
"""
import base64, hashlib, json, ssl, time, urllib.request
from Crypto.Cipher import AES

BASE_URL = 'https://www.cdnhth.club'
APP_DATA_SECRET = '185Hcomic3PAPP7R'
HEADER_VER = '2.0.21'

def md5hex(s): return hashlib.md5(s.encode('utf-8')).hexdigest()

def api_get(path):
    ts = int(time.time())
    headers = {
        'User-Agent': 'Mozilla/5.0 (Linux; Android 7.1.2; DT1901A Build/N2G47O; wv) AppleWebKit/537.36',
        'Accept': 'application/json, text/plain, */*',
        'tokenparam': f'{ts},{HEADER_VER}',
        'token': md5hex(f'{ts}18comicAPP'),
        'version': HEADER_VER,
    }
    ctx = ssl.create_default_context()
    ctx.check_hostname = False; ctx.verify_mode = ssl.CERT_NONE
    req = urllib.request.Request(f'{BASE_URL}{path}', headers=headers)
    resp = urllib.request.urlopen(req, context=ctx, timeout=15)
    return resp.read(), ts

def decrypt(data_b64, ts):
    key = md5hex(f'{ts}{APP_DATA_SECRET}').encode()
    raw = AES.new(key, AES.MODE_ECB).decrypt(base64.b64decode(data_b64))
    return raw[:-raw[-1]].decode('utf-8')

print("=" * 60)
print("测试 /latest 端点 (首页列表) - id字段类型")
print("=" * 60)
raw, ts = api_get('/latest?page=0')
j = json.loads(raw)
print(f"code={j.get('code')}")
if j.get('code') == 200 and isinstance(j.get('data'), str):
    d = json.loads(decrypt(j['data'], ts))
    print(f"Type: {type(d).__name__}, Length: {len(d)}")
    if len(d) > 0:
        first = d[0]
        print(f"\nFirst book ALL fields:")
        for k, v in first.items():
            print(f"  {k}: {type(v).__name__} = {str(v)[:120]}")
        print(f"\n--- Key check ---")
        print(f"  id type: {type(first.get('id')).__name__}")
        print(f"  id value: {first.get('id')}")
        # Check if any books have id as int
        id_types = set()
        for item in d:
            id_types.add(type(item.get('id')).__name__)
        print(f"  All id types in list: {id_types}")

print()
print("=" * 60)
print("测试 /album 端点 (详情页) - id, likes, total_views 字段类型")
print("=" * 60)
for bid in ['1441531', '363859', '410702']:
    raw, ts = api_get(f'/album?id={bid}')
    j = json.loads(raw)
    print(f"\nBook {bid}: code={j.get('code')}")
    if j.get('code') == 200 and isinstance(j.get('data'), str):
        d = json.loads(decrypt(j['data'], ts))
        for k in ['id', 'name', 'likes', 'total_views', 'description', 'tags', 'author', 'series', 'is_favorite', 'comment_total']:
            v = d.get(k)
            print(f"  {k}: {type(v).__name__} = {str(v)[:120]}")

print()
print("=" * 60)
print("测试 /search 端点 - id字段类型")
print("=" * 60)
raw, ts = api_get('/search?search_query=test&o=mr&page=1')
j = json.loads(raw)
print(f"code={j.get('code')}")
if j.get('code') == 200 and isinstance(j.get('data'), str):
    d = json.loads(decrypt(j['data'], ts))
    content = d.get('content', [])
    if len(content) > 0:
        first = content[0]
        print(f"First book id: {first.get('id')} (type={type(first.get('id')).__name__})")
        id_types = set()
        for item in content:
            id_types.add(type(item.get('id')).__name__)
        print(f"All id types: {id_types}")

print()
print("=" * 60)
print("结论:")
print("如果 /latest 返回 id=int 而 BookItem.id 是 String, 会导致 id 为空字符串")
print("从而 getBookDetail('') 请求失败, 详情页数据为空")
print("=" * 60)
