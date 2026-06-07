#!/usr/bin/env python3
"""测试评论API - 漫画JM1071655 — 解密并分析数据结构"""
import requests, hashlib, base64, json, time
from Crypto.Cipher import AES

BASE = "https://www.cdnhth.club"
DATA_SECRET = "185Hcomic3PAPP7R"

def make_headers():
    ts = str(int(time.time()))
    secret = "18comicAPP"
    token = hashlib.md5(f"{ts}{secret}".encode()).hexdigest()
    return {
        "User-Agent": "Mozilla/5.0 (Linux; Android 7.1.2; DT1901A Build/N2G47O; wv) AppleWebKit/537.36 (KHTML, like Gecko) Version/4.0 Chrome/86.0.4240.198 Mobile Safari/537.36",
        "tokenparam": f"{ts},2.0.21",
        "token": token,
        "version": "2.0.21"
    }, ts

def decrypt_aes(data_b64, ts):
    key = hashlib.md5(f"{ts}{DATA_SECRET}".encode()).hexdigest().encode()
    cipher = AES.new(key, AES.MODE_ECB)
    raw = base64.b64decode(data_b64)
    dec = cipher.decrypt(raw)
    pad_len = dec[-1]
    return dec[:-pad_len].decode("utf-8", errors="replace")

def try_decrypt(data_str, timestamps):
    """用多个时间戳尝试解密"""
    for ts in timestamps:
        try:
            return decrypt_aes(data_str, ts)
        except:
            pass
    return None

def collect_timestamps(count=5):
    """生成最近几个时间戳（秒级），应对并发请求导致的时间戳不匹配"""
    now = int(time.time())
    return [str(now - i) for i in range(count)]

aid = "1071655"

# 测试 page=0 和 page=1
for page in ["0", "1"]:
    print(f"\n{'='*60}")
    print(f"=== Forum API page={page} ===")
    print('='*60)
    
    # 先做一次请求让服务器记录时间戳
    headers_warm, _ = make_headers()
    requests.get(f"{BASE}/album/{aid}", headers=headers_warm)
    
    # 正式请求评论
    headers_req, ts_req = make_headers()
    r = requests.get(f"{BASE}/forum", params={"mode": "manhua", "aid": aid, "page": page}, headers=headers_req)
    
    if r.status_code != 200:
        print(f"HTTP {r.status_code}: {r.text[:200]}")
        continue
    
    resp = r.json()
    print(f"Response keys: {list(resp.keys())}")
    print(f"code={resp.get('code')}, message={resp.get('message', '')[:100]}")
    
    data = resp.get("data")
    print(f"data type: {type(data).__name__}")
    
    if isinstance(data, str):
        # 用多个时间戳尝试解密（类似项目的 recentTimestamps）
        timestamps = [ts_req] + collect_timestamps(5)
        decrypted = try_decrypt(data, timestamps)
        
        if decrypted:
            print(f"\nDecrypted ({len(decrypted)} chars) — first 500:")
            print(decrypted[:500])
            parsed = json.loads(decrypted)
            print(f"\nParsed type: {type(parsed).__name__}")
            
            if isinstance(parsed, dict):
                print(f"Dict keys: {list(parsed.keys())}")
                for k, v in parsed.items():
                    if isinstance(v, list):
                        print(f"  {k}: list[{len(v)}]")
                        if len(v) > 0:
                            print(f"    First item keys: {list(v[0].keys()) if isinstance(v[0], dict) else type(v[0]).__name__}")
                            print(f"    First item: {json.dumps(v[0], ensure_ascii=False)[:500]}")
                            if len(v) > 1:
                                print(f"    Last item addtime: {v[-1].get('addtime', 'N/A')}")
                    elif isinstance(v, dict):
                        print(f"  {k}: dict({json.dumps(v, ensure_ascii=False)[:200]})")
                    else:
                        print(f"  {k}: {v}")
                # 如果是 dict 且包含 list，显示 list 中所有评论的时间
                for k, v in parsed.items():
                    if isinstance(v, list) and len(v) > 0:
                        print(f"\n  All comment times from '{k}':")
                        for i, item in enumerate(v):
                            at = item.get('addtime', 'N/A') if isinstance(item, dict) else 'N/A'
                            print(f"    [{i}] addtime={at}")
            elif isinstance(parsed, list):
                print(f"Array of {len(parsed)} items")
                if len(parsed) > 0:
                    print(f"First item keys: {list(parsed[0].keys()) if isinstance(parsed[0], dict) else type(parsed[0]).__name__}")
                    print(f"First item: {json.dumps(parsed[0], ensure_ascii=False)[:500]}")
                    print(f"\n  All items addtime:")
                    for i, item in enumerate(parsed):
                        at = item.get('addtime', 'N/A') if isinstance(item, dict) else 'N/A'
                        print(f"    [{i}] addtime={at}")
        else:
            print("Decryption FAILED with all timestamps")
            print(f"Raw data (first 200): {data[:200]}")
    elif isinstance(data, list):
        print(f"List of {len(data)} items")
        if len(data) > 0:
            print(f"First item: {json.dumps(data[0], ensure_ascii=False)[:500]}")
    else:
        print(f"Raw data: {str(data)[:300]}")
