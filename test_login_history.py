#!/usr/bin/env python3
"""测试登录、积分、历史API — 获取真实数据格式"""
import requests, hashlib, base64, json, re, time
from Crypto.Cipher import AES

BASE = "https://www.cdnhth.club"
TS = str(int(time.time()))
SECRET = "18comicAPP"
DATA_SECRET = "185Hcomic3PAPP7R"

def make_headers(secret=SECRET):
    token = hashlib.md5(f"{TS}{secret}".encode()).hexdigest()
    return {"User-Agent": "Mozilla/5.0", "tokenparam": f"{TS},2.0.21", "token": token, "version": "2.0.21"}

def decrypt(data_b64):
    key = hashlib.md5(f"{TS}{DATA_SECRET}".encode()).hexdigest().encode()
    cipher = AES.new(key, AES.MODE_ECB)
    raw = base64.b64decode(data_b64)
    dec = cipher.decrypt(raw)
    return dec[:-dec[-1]].decode("utf-8", errors="replace")

# Use your actual credentials here
USERNAME = input("用户名: ")
PASSWORD = input("密码: ")

# 1. Pre-login
print("\n=== 1. Pre-login ===")
r = requests.get(f"{BASE}/", headers=make_headers())
print(f"Status: {r.status_code}, cookies: {dict(r.cookies)}")

# 2. Login
print("\n=== 2. Login ===")
login_data = {"username": USERNAME, "password": PASSWORD}
r2 = requests.post(f"{BASE}/login", data=login_data, headers=make_headers())
print(f"Status: {r2.status_code}, cookies: {dict(r2.cookies)}")
if r2.status_code == 200:
    data = r2.json()
    if "data" in data and isinstance(data["data"], str):
        dec = decrypt(data["data"])
        parsed = json.loads(dec)
        print(f"\nLogin response keys: {list(parsed.keys())}")
        for k in ["uid", "username", "exp", "coin", "level_name", "level", "favorites"]:
            print(f"  {k}: {parsed.get(k, 'N/A')}")
        avs = r2.cookies.get("AVS", "")
        print(f"\nAVS cookie: {avs[:20]}...")

# 3. History
print("\n=== 3. History ===")
r3 = requests.get(f"{BASE}/watch_list", params={"page": 1}, headers=make_headers())
print(f"Status: {r3.status_code}")
if r3.status_code == 200:
    data = r3.json()
    print(f"Keys: {list(data.keys())}")
    if "data" in data and isinstance(data["data"], str):
        dec = decrypt(data["data"])
        parsed = json.loads(dec)
        if isinstance(parsed, dict):
            print(f"History keys: {list(parsed.keys())}")
            for k in parsed:
                v = parsed[k]
                if isinstance(v, list):
                    print(f"  {k}: list of {len(v)} items")
                    if v: print(f"    first: {json.dumps(v[0], ensure_ascii=False)[:200]}")
                else:
                    print(f"  {k}: {str(v)[:80]}")
