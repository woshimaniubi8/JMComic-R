"""测试 JM Comic API 返回数据结构"""
import sys, os, time, json, base64, hashlib
import requests

# 密钥
APP_TOKEN_SECRET = '18comicAPP'
APP_DATA_SECRET = '185Hcomic3PAPP7R'
APP_VERSION = '2.0.21'
BASE_URL = 'https://www.cdnhth.club'

def call_api(path, params=None):
    now = int(time.time())
    ts = str(now)
    token = hashlib.md5(f'{ts}{APP_TOKEN_SECRET}'.encode()).hexdigest()
    headers = {
        'User-Agent': 'Mozilla/5.0 (Linux; Android 7.1.2; DT1901A Build/N2G47O; wv) AppleWebKit/537.36',
        'token': token,
        'tokenparam': f'{ts},{APP_VERSION}',
        'version': APP_VERSION,
    }
    url = f'{BASE_URL}{path}'
    if params:
        url += '?' + '&'.join(f'{k}={v}' for k,v in params.items())
    print(f'\n=== {url} ===')
    resp = requests.get(url, headers=headers, timeout=30)
    print(f'Status: {resp.status_code}, Length: {len(resp.text)}')
    data = resp.json()
    print(f'code: {data.get("code")}')
    
    enc_data = data.get('data')
    if isinstance(enc_data, str) and len(enc_data) > 10:
        # decrypt
        key = hashlib.md5(f'{ts}{APP_DATA_SECRET}'.encode()).hexdigest()
        from Crypto.Cipher import AES
        cipher_bytes = base64.b64decode(enc_data)
        aes = AES.new(key.encode('utf-8'), AES.MODE_ECB)
        decrypted = aes.decrypt(cipher_bytes)
        decrypted = decrypted[:-decrypted[-1]]
        text = decrypted.decode('utf-8')
        obj = json.loads(text)
        if isinstance(obj, dict):
            print(f'Decrypted keys: {list(obj.keys())}')
        elif isinstance(obj, list):
            print(f'Decrypted list, count: {len(obj)}')
        return obj
    else:
        print(f'data type: {type(enc_data)}, raw: {str(enc_data)[:200]}')
        return data

# 1. 测试 /latest
print('=== Testing /latest ===')
obj = call_api('/latest', {'page': '0'})
if isinstance(obj, list):
    print(f'List count: {len(obj)}')
    if obj:
        item = obj[0]
        print(f'First item keys: {sorted(item.keys())}')
        for k, v in item.items():
            if isinstance(v, str) and len(v) > 60:
                v = v[:60] + '...'
            print(f'  {k}: {v}')
        # Check total via pagination or count
        print(f'Total items in page: {len(obj)}')
elif isinstance(obj, dict):
    print(f'total: {obj.get("total")}')
    content = obj.get('content', [])
    print(f'content count: {len(content)}')
    if content:
        item = content[0]
        print(f'First item keys: {sorted(item.keys())}')
        print(f'First item sample:')
        for k, v in item.items():
            if isinstance(v, str) and len(v) > 60:
                v = v[:60] + '...'
            print(f'  {k}: {v}')

# 2. 测试 /album
print('\n=== Testing /album?id=400222 ===')
obj = call_api('/album', {'id': '400222'})
if obj:
    print(f'Album keys: {sorted(obj.keys())}')
    for k in ['id', 'name', 'author', 'description', 'likes', 'total_views', 'series']:
        v = obj.get(k, 'N/A')
        if isinstance(v, str) and len(v) > 80:
            v = v[:80] + '...'
        print(f'  {k}: {v}')
    series = obj.get('series', [])
    if series:
        print(f'  Series count: {len(series)}')
        print(f'  First series: {json.dumps(series[0], ensure_ascii=False)[:200]}')

# 3. 测试 /categories
print('\n=== Testing /categories ===')
obj = call_api('/categories')
if obj:
    if isinstance(obj, list):
        print(f'Category count: {len(obj)}')
        if obj:
            print(f'First category: {json.dumps(obj[0], ensure_ascii=False)}')
    else:
        print(f'Category keys: {sorted(obj.keys()) if isinstance(obj, dict) else "n/a"}')
        content = obj.get('content', obj.get('categories', []))
        print(f'Category list count: {len(content) if content else 0}')
