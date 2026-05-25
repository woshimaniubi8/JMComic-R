"""
验证脚本 2: 测试 /latest 和 /favorite 端点
用于调试首页 vs 收藏页的数据差异问题
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
print("测试 /latest 端点 (首页列表)")
print("=" * 60)
raw, ts = api_get('/latest?page=0')
j = json.loads(raw)
print(f"code={j.get('code')}")
if j.get('code') == 200 and isinstance(j.get('data'), str):
    d = json.loads(decrypt(j['data'], ts))
    print(f"Type: {type(d).__name__}, Length: {len(d) if isinstance(d, list) else 'N/A'}")
    if isinstance(d, list) and len(d) > 0:
        print(f"\nFirst book raw data:")
        first = d[0]
        for k, v in first.items():
            print(f"  {k}: {type(v).__name__} = {str(v)[:80]}")
        print(f"\nBook ID type check: id={first.get('id')} (type={type(first.get('id')).__name__})")

print()
print("=" * 60)
print("测试 /favorite 端点 (收藏列表)")
print("=" * 60)
raw, ts = api_get('/favorite?page=1&o=mr&folder_id=0')
j = json.loads(raw)
print(f"code={j.get('code')}")
if j.get('code') == 200 and isinstance(j.get('data'), str):
    d = json.loads(decrypt(j['data'], ts))
    print(f"Keys: {list(d.keys())}")
    book_list = d.get('list', [])
    print(f"List length: {len(book_list)}")
    if len(book_list) > 0:
        first = book_list[0]
        for k, v in first.items():
            print(f"  {k}: {type(v).__name__} = {str(v)[:80]}")
        print(f"\nBook ID type: id={first.get('id')} (type={type(first.get('id')).__name__})")

print()
print("=" * 60)
print("测试: id 字段类型对比")
print("=" * 60)
# 检查 latest 和 favorite 中 id 字段的类型是否一致
raw, ts = api_get('/latest?page=0')
j = json.loads(raw)
if j.get('code') == 200 and isinstance(j.get('data'), str):
    d = json.loads(decrypt(j['data'], ts))
    if isinstance(d, list):
        id_types = set()
        for item in d[:10]:
            id_types.add(type(item.get('id')).__name__)
        print(f"/latest id types: {id_types}")

# favorite 需要登录，可能会失败
print("\n注意: /favorite 需要登录状态才能返回数据")
print("如果未登录，收藏列表为空是正常的")
