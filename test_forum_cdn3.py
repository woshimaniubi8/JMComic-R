"""测试分流3 (cdnhth.net) 的论坛API"""
import requests, hashlib, base64, json, time
from Crypto.Cipher import AES

DATA_SECRET = "185Hcomic3PAPP7R"

def try_decrypt_all(data_b64):
    """用最近10个时间戳尝试解密，验证JSON"""
    now = int(time.time())
    for offset in range(10):
        ts = str(now - offset)
        try:
            key = hashlib.md5(f"{ts}{DATA_SECRET}".encode()).hexdigest().encode()
            cipher = AES.new(key, AES.MODE_ECB)
            raw = base64.b64decode(data_b64)
            dec = cipher.decrypt(raw)
            pad_len = dec[-1]
            text = dec[:-pad_len].decode("utf-8", errors="replace")
            json.loads(text)
            return text, ts
        except:
            continue
    return None, None

def test_cdn(base_url, name):
    print(f"\n{'='*60}")
    print(f"=== {name}: {base_url} ===")
    print('='*60)
    
    for page in ["0", "1", "2"]:
        ts = str(int(time.time()))
        secret = "18comicAPP"
        token = hashlib.md5(f"{ts}{secret}".encode()).hexdigest()
        headers = {
            "User-Agent": "Mozilla/5.0",
            "tokenparam": f"{ts},2.0.21",
            "token": token,
            "version": "2.0.21"
        }
        r = requests.get(f"{base_url}/forum",
            params={"mode": "manhua", "aid": "1071655", "page": page},
            headers=headers)
        resp = r.json()
        data = resp.get("data", "")
        if isinstance(data, str):
            dec, used_ts = try_decrypt_all(data)
            if dec:
                parsed = json.loads(dec)
                items = parsed.get("list", [])
                times = [i.get("addtime","?") for i in items]
                cids = [i.get("CID","?") for i in items]
                print(f"  page={page}: {len(items)} items, CIDs={cids[0]}..{cids[-1]}, dates={times[0]}..{times[-1]}, total={parsed.get('total')}, ts_offset={int(time.time())-int(used_ts)}")
            else:
                print(f"  page={page}: DECRYPT FAILED")
        else:
            print(f"  page={page}: data is {type(data).__name__} = {str(data)[:100]}")

# Test both CDNs
test_cdn("https://www.cdnhth.club", "分流1")
test_cdn("https://www.cdnhth.net", "分流3")
