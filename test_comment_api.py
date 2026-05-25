#!/usr/bin/env python3
"""测试评论API - 漫画1425086 — 含解密"""
import requests, hashlib, base64, json, re, time
from Crypto.Cipher import AES

BASE = "https://www.cdnhth.club"
TIMESTAMP = str(int(time.time()))
SECRET = "18comicAPP"
DATA_SECRET = "185Hcomic3PAPP7R"
TOKEN = hashlib.md5(f"{TIMESTAMP}{SECRET}".encode()).hexdigest()

headers = {
    "User-Agent": "Mozilla/5.0",
    "tokenparam": f"{TIMESTAMP},2.0.21",
    "token": TOKEN, "version": "2.0.21"
}

def decrypt_aes(data_b64, ts):
    key = hashlib.md5(f"{ts}{DATA_SECRET}".encode()).hexdigest().encode()
    cipher = AES.new(key, AES.MODE_ECB)
    raw = base64.b64decode(data_b64)
    dec = cipher.decrypt(raw)
    # PKCS5 unpad
    pad_len = dec[-1]
    return dec[:-pad_len].decode("utf-8", errors="replace")

# 1. 测试评论API
print("=== 评论API (/forum) ===")
r = requests.get(f"{BASE}/forum", params={"mode": "manhua", "aid": "1425086", "page": "1"}, headers=headers)
if r.status_code == 200:
    data = r.json()
    if "data" in data and isinstance(data["data"], str):
        dec = decrypt_aes(data["data"], TIMESTAMP)
        print(f"Decrypted ({len(dec)} chars):")
        parsed = json.loads(dec)
        if isinstance(parsed, list):
            print(f"Array of {len(parsed)} items")
            if len(parsed) > 0:
                print("\nFirst comment raw JSON:")
                print(json.dumps(parsed[0], ensure_ascii=False, indent=2)[:1500])
                # Check key fields
                keys = parsed[0].keys()
                print(f"\nAll fields: {list(keys)}")
        elif isinstance(parsed, dict):
            print(f"Dict with keys: {list(parsed.keys())}")
            # Check list field
            for k in ["list", "comments", "data"]:
                if k in parsed:
                    items = parsed[k]
                    if isinstance(items, list) and len(items) > 0:
                        print(f"\nFirst item from '{k}':")
                        print(json.dumps(items[0], ensure_ascii=False, indent=2)[:1500])
                        print(f"\nFields: {list(items[0].keys())}")

# 2. 测试album
print("\n\n=== /album API (解密后) ===")
r2 = requests.get(f"{BASE}/album/1425086", headers=headers)
if r2.status_code == 200:
    data = r2.json()
    if "data" in data and isinstance(data["data"], str):
        dec = decrypt_aes(data["data"], TIMESTAMP)
        parsed = json.loads(dec)
        # Check relevant fields
        for k in ["likes", "total_views", "description", "author", "name", "tags", "id", "series"]:
            v = parsed.get(k, "N/A")
            v_str = str(v)[:80] if v is not None else "null"
            print(f"  {k}: {v_str}")
