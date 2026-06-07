"""测试评论API分页 - 确认不同page值返回的数据"""
import requests, hashlib, base64, json, time
from Crypto.Cipher import AES

BASE = "https://www.cdnhth.club"
DATA_SECRET = "185Hcomic3PAPP7R"

def decrypt_aes(data_b64, ts):
    key = hashlib.md5(f"{ts}{DATA_SECRET}".encode()).hexdigest().encode()
    cipher = AES.new(key, AES.MODE_ECB)
    raw = base64.b64decode(data_b64)
    dec = cipher.decrypt(raw)
    pad_len = dec[-1]
    return dec[:-pad_len].decode("utf-8", errors="replace")

def try_decrypt(data_b64):
    """用最近几个时间戳尝试解密，验证JSON可解析"""
    now = int(time.time())
    for offset in range(10):
        ts = str(now - offset)
        try:
            dec = decrypt_aes(data_b64, ts)
            # 验证解密结果是否为有效JSON
            json.loads(dec)
            return dec
        except:
            continue
    return None

def get_comments(page):
    ts = str(int(time.time()))
    secret = "18comicAPP"
    token = hashlib.md5(f"{ts}{secret}".encode()).hexdigest()
    headers = {
        "User-Agent": "Mozilla/5.0",
        "tokenparam": f"{ts},2.0.21",
        "token": token,
        "version": "2.0.21"
    }
    r = requests.get(f"{BASE}/forum",
        params={"mode": "manhua", "aid": "1071655", "page": page},
        headers=headers)
    data = r.json().get("data", "")
    if isinstance(data, str):
        dec = try_decrypt(data)
        if dec:
            return json.loads(dec)
    return None

for page in ["0", "1", "2", "3", "5", "10"]:
    parsed = get_comments(page)
    if parsed:
        items = parsed.get("list", [])
        if items:
            cids = [i.get("CID","?") for i in items]
            times = [i.get("addtime","?") for i in items]
            print(f"page={page}: {len(items)} items, CIDs={cids[0]}..{cids[-1]}, dates={times[0]}..{times[-1]}, total={parsed.get('total')}")
        else:
            print(f"page={page}: empty list, total={parsed.get('total')}")
    else:
        print(f"page={page}: FAILED to parse")
