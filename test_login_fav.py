"""
测试登录流程获取 AVS token 并验证 /favorite API
"""
import base64, hashlib, json, time, urllib.request, ssl, http.cookiejar
from Crypto.Cipher import AES

BASE = "https://www.cdnhth.club"
VER = "2.0.21"
S1 = "18comicAPP"
S_DATA = "185Hcomic3PAPP7R"

def md5(s): return hashlib.md5(s.encode()).hexdigest()
def hdr(ts):
    t, tp = md5(f"{ts}{S1}"), f"{ts},{VER}"
    return {"User-Agent":"Mozilla/5.0 (Linux; Android 7.1.2)","tokenparam":tp,"token":t,"version":VER,
            "Accept":"application/json"}

ctx = ssl.create_default_context(); ctx.check_hostname = False; ctx.verify_mode = False

# 使用 cookie jar 保存登录后的 cookies
cj = http.cookiejar.CookieJar()
opener = urllib.request.build_opener(urllib.request.HTTPCookieProcessor(cj))

def api_post(path, data_dict, ts=None):
    if ts is None: ts = int(time.time())
    url = f"{BASE}{path}"
    body = urllib.parse.urlencode(data_dict).encode()
    req = urllib.request.Request(url, data=body, headers={**hdr(ts), "Content-Type":"application/x-www-form-urlencoded"})
    resp = opener.open(req, context=ctx, timeout=15)
    return json.loads(resp.read()), ts

def api_get(path, ts=None):
    if ts is None: ts = int(time.time())
    req = urllib.request.Request(f"{BASE}{path}", headers=hdr(ts))
    resp = opener.open(req, context=ctx, timeout=15)
    return json.loads(resp.read()), ts

print("Step 1: 访问登录页获取初始 Cookie...")
try:
    opener.open(urllib.request.Request(f"{BASE}/login", headers={"User-Agent":"Mozilla/5.0"}), context=ctx, timeout=10)
    print(f"  Cookies after login page: {dict((c.name, c.value[:10]+'...') for c in cj)}")
except Exception as e:
    print(f"  Login page: {e}")

print("\nStep 2: 登录获取 AVS token")
# 注意：这里需要真实的用户名密码！用测试账号
USERNAME = "test1234"
PASSWORD = "test1234"

try:
    j, ts = api_post("/login", {"username": USERNAME, "password": PASSWORD})
    if j.get("code") == 200 and isinstance(j.get("data"), str):
        k = md5(f"{ts}{S_DATA}").encode()
        raw = AES.new(k, AES.MODE_ECB).decrypt(base64.b64decode(j["data"]))
        d = json.loads(raw[:-raw[-1]].decode())
        print(f"  Login success!")
        print(f"  uid={d.get('uid')}, username={d.get('username')}")
        print(f"  s(AVS)={d.get('s','')[:20]}...")
        avs_token = d.get("s", "")
        print(f"  Cookies: {dict((c.name, c.value[:15]+'...') for c in cj)}")
        
        if avs_token:
            print(f"\nStep 3: 测试 /favorite API (带 AVS cookie)")
            j2, ts2 = api_get("/favorite?page=1&o=mr&folder_id=0")
            code = j2.get("code")
            print(f"  code={code}")
            if code == 200:
                k = md5(f"{ts2}{S_DATA}").encode()
                raw = AES.new(k, AES.MODE_ECB).decrypt(base64.b64decode(j2["data"]))
                d = json.loads(raw[:-raw[-1]].decode())
                print(f"  favorites count={len(d.get('list',[]))} total={d.get('total')}")
            else:
                print(f"  error: {j2.get('errorMsg','')}")
    else:
        print(f"  Login failed: code={j.get('code')} msg={j.get('errorMsg','')}")
except Exception as e:
    print(f"  ERROR: {e}")

print("\nDone. 如果登录失败，请用真实账号测试。")
