"""
完整 API 验证脚本 — 逐一测试所有端点，打印完整解密数据
"""
import base64, hashlib, json, time, urllib.request, ssl
from Crypto.Cipher import AES

BASE = "https://www.cdnhth.club"
IMG_CDN = "https://cdn-msp.jmapiproxy3.cc"
VER = "2.0.21"
S1 = "18comicAPP"
S2 = "18comicAPPContent"
S_DATA = "185Hcomic3PAPP7R"

def md5(s): return hashlib.md5(s.encode()).hexdigest()
def hdr(ts, secret=S1):
    t, tp = md5(f"{ts}{secret}"), f"{ts},{VER}"
    return {"User-Agent":"Mozilla/5.0 (Linux; Android 7.1.2)","tokenparam":tp,"token":t,"version":VER,
            "Accept":"application/json, text/plain, */*"}

ctx = ssl.create_default_context(); ctx.check_hostname = False; ctx.verify_mode = False

def api_get(path, ts=None):
    if ts is None: ts = int(time.time())
    req = urllib.request.Request(f"{BASE}{path}", headers=hdr(ts))
    resp = urllib.request.urlopen(req, context=ctx, timeout=15)
    j = json.loads(resp.read())
    if j.get("code") == 200 and isinstance(j.get("data"), str):
        k = md5(f"{ts}{S_DATA}").encode()
        raw = AES.new(k, AES.MODE_ECB).decrypt(base64.b64decode(j["data"]))
        j["_decrypted"] = json.loads(raw[:-raw[-1]].decode())
    return j, ts

def api_get_scramble(path, ts=None):
    """chapter_view_template uses different token secret"""
    if ts is None: ts = int(time.time())
    req = urllib.request.Request(f"{BASE}{path}", headers=hdr(ts, S2))
    resp = urllib.request.urlopen(req, context=ctx, timeout=15)
    return resp.read().decode("utf-8", errors="replace"), ts

print("="*60)
print("1. /album  API 完整字段")
print("="*60)
for bid in ["1441531", "363859", "410702"]:
    try:
        j, ts = api_get(f"/album?id={bid}")
        d = j.get("_decrypted", {})
        print(f"\n[{bid}] id={d.get('id')} type={type(d.get('id')).__name__}")
        print(f"  name={str(d.get('name',''))[:60]}")
        print(f"  author={d.get('author')} type={type(d.get('author')).__name__}")
        print(f"  description={repr(d.get('description',''))[:60]}")
        print(f"  total_views={d.get('total_views')} type={type(d.get('total_views')).__name__}")
        print(f"  likes={d.get('likes')} type={type(d.get('likes')).__name__}")
        print(f"  comment_total={d.get('comment_total')} type={type(d.get('comment_total')).__name__}")
        print(f"  series_id={d.get('series_id')} type={type(d.get('series_id')).__name__}")
        print(f"  series={d.get('series')} (len={len(d.get('series',[]))})")
        print(f"  tags={d.get('tags')}")
        print(f"  is_favorite={d.get('is_favorite')}")
        print(f"  images (first 3)={d.get('images',[])[:3]}")
    except Exception as e:
        print(f"  ERROR: {e}")

print("\n" + "="*60)
print("2. /chapter API 完整字段")
print("="*60)
for bid in ["1441531", "363859"]:
    try:
        j, ts = api_get(f"/chapter?id={bid}")
        d = j.get("_decrypted", {})
        imgs = d.get("images", [])
        print(f"\n[{bid}] id={d.get('id')} type={type(d.get('id')).__name__}")
        print(f"  name={str(d.get('name',''))[:60]}")
        print(f"  series_id={d.get('series_id')} type={type(d.get('series_id')).__name__}")
        print(f"  images={len(imgs)} images: {imgs[:5]}...")
        if imgs:
            test_url = f"{IMG_CDN}/media/photos/{d['id']}/{imgs[0]}"
            try:
                r = urllib.request.urlopen(urllib.request.Request(test_url, 
                    headers={"User-Agent":"Mozilla/5.0","Referer":BASE+"/"}), context=ctx, timeout=10)
                print(f"  image test: HTTP {r.status}, size={len(r.read())}")
            except Exception as e:
                print(f"  image test: {e}")
    except Exception as e:
        print(f"  ERROR: {e}")

print("\n" + "="*60)
print("3. /chapter_view_template (scramble_id)")
print("="*60)
for bid in ["1441531"]:
    try:
        html, ts = api_get_scramble(f"/chapter_view_template?id={bid}&mode=vertical&page=0&app_img_shunt=NaN")
        import re
        mo = re.search(r"(?<=var scramble_id = )\w+", html)
        sid = int(mo.group()) if mo else "NOT FOUND"
        print(f"[{bid}] scramble_id={sid} (searched in {len(html)} bytes of HTML)")
        # also try parsing as JSON
        try:
            j = json.loads(html)
            if j.get("code") == 200 and isinstance(j.get("data"), str):
                k = md5(f"{ts}{S_DATA}").encode()
                raw = AES.new(k, AES.MODE_ECB).decrypt(base64.b64decode(j["data"]))
                d = json.loads(raw[:-raw[-1]].decode())
                print(f"  JSON data: {json.dumps(d, ensure_ascii=False)[:300]}")
        except:
            pass
    except Exception as e:
        print(f"  ERROR: {e}")

print("\n" + "="*60)
print("4. /favorite API (no auth — expected 401)")
print("="*60)
try:
    j, ts = api_get("/favorite?page=1&o=mr&folder_id=0")
    print(f"  code={j.get('code')} msg={j.get('errorMsg','')}")
except Exception as e:
    print(f"  ERROR: {e}")

print("\nDone.")
