import base64,hashlib,json,time,urllib.request,ssl
from Crypto.Cipher import AES
def md5(s): return hashlib.md5(s.encode()).hexdigest()
ts=int(time.time())
t,tp=md5(f"{ts}18comicAPP"),f"{ts},2.0.21"
h={"User-Agent":"Mozilla/5.0","tokenparam":tp,"token":t,"version":"2.0.21"}
ctx=ssl.create_default_context();ctx.check_hostname=False;ctx.verify_mode=False

url="https://www.cdnhth.club/chapter?id=1441524"
r=urllib.request.urlopen(urllib.request.Request(url,headers=h),context=ctx,timeout=10)
j=json.loads(r.read())
if j.get("code")==200:
    k=md5(f"{ts}185Hcomic3PAPP7R").encode()
    raw=AES.new(k,AES.MODE_ECB).decrypt(base64.b64decode(j["data"]))
    d=json.loads(raw[:-raw[-1]].decode())
    bid = d["id"]
    imgs = d.get("images",[])
    print(f"id={bid}({type(bid).__name__})")
    print(f"images count={len(imgs)}")
    print(f"first 3: {imgs[:3]}")
    if imgs:
        print(f"URL: https://cdn-msp.jmapiproxy3.cc/media/photos/{bid}/{imgs[0]}")
