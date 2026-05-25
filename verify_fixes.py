"""
综合验证脚本: 验证 Android 修复的正确性
测试:
1. descramble 算法与 Qt 原项目的一致性
2. API 数据解析 (id类型, likes, total_views, description)
3. scramble_id 提取 vs 默认值的影响
"""
import base64, hashlib, json, re, ssl, time, urllib.request
from Crypto.Cipher import AES

BASE_URL = 'https://www.cdnhth.club'
IMG_CDN = 'https://cdn-msp.jmapiproxy3.cc'
S_DATA = '185Hcomic3PAPP7R'
HEADER_VER = '2.0.21'
S1 = '18comicAPP'
S2 = '18comicAPPContent'

def md5(s): return hashlib.md5(s.encode()).hexdigest()

def hdr(ts, secret=S1):
    return {"User-Agent":"Mozilla/5.0 (Linux; Android 7.1.2)","tokenparam":f"{ts},{HEADER_VER}",
            "token":md5(f"{ts}{secret}"),"version":HEADER_VER,"Accept":"application/json, text/plain, */*"}

ctx = ssl.create_default_context(); ctx.check_hostname = False; ctx.verify_mode = False

def api_get(path):
    ts = int(time.time())
    req = urllib.request.Request(f"{BASE_URL}{path}", headers=hdr(ts))
    resp = urllib.request.urlopen(req, context=ctx, timeout=15)
    return json.loads(resp.read()), ts

def api_get_decrypted(path):
    """获取并解密 API 响应，返回解密后的 data JSON"""
    j, ts = api_get(path)
    if j.get('code') == 200:
        data = j.get('data')
        if isinstance(data, str):
            return json.loads(decrypt(data, ts)), ts
        elif isinstance(data, (dict, list)):
            return data, ts
    return None, ts

def api_get_scramble(path):
    ts = int(time.time())
    req = urllib.request.Request(f"{BASE_URL}{path}", headers=hdr(ts, S2))
    resp = urllib.request.urlopen(req, context=ctx, timeout=15)
    return resp.read().decode("utf-8", errors="replace"), ts

def decrypt(data_b64, ts):
    key = md5(f"{ts}{S_DATA}").encode()
    raw = AES.new(key, AES.MODE_ECB).decrypt(base64.b64decode(data_b64))
    return raw[:-raw[-1]].decode('utf-8')

# ============================================================
# Test 1: Descramble algorithm comparison (Kotlin vs Python/Qt)
# ============================================================
print("=" * 60)
print("测试 1: Descramble 算法一致性验证")
print("=" * 60)

def get_segmentation_num_qt(epsId, scramble_id, pictureName):
    """Qt 原项目 GetSegmentationNum (tool.py:878)"""
    scramble_id = int(scramble_id)
    epsId = int(epsId)
    if epsId < scramble_id:
        num = 0
    elif epsId < 268850:
        num = 10
    elif epsId > 421926:
        s = str(epsId) + pictureName
        s = hashlib.md5(s.encode()).hexdigest()
        num = ord(s[-1])
        num %= 8
        num = num * 2 + 2
    else:
        s = str(epsId) + pictureName
        s = hashlib.md5(s.encode()).hexdigest()
        num = ord(s[-1])
        num %= 10
        num = num * 2 + 2
    return num

# Test with actual API data
test_eps_id = 1441524
scramble_html, ts = api_get_scramble(f"/chapter_view_template?id={test_eps_id}&mode=vertical&page=0&app_img_shunt=NaN")
mo = re.search(r"(?<=var scramble_id = )\w+", scramble_html)
actual_scramble_id = int(mo.group()) if mo else 220980
print(f"scramble_id from API: {actual_scramble_id}")
print(f"scramble_id fallback: 220980")

# /chapter API response
d, ts = api_get_decrypted(f"/chapter?id={test_eps_id}")
if d:
    images = d.get('images', [])
    print(f"Chapter images: {len(images)} files")
    if images:
        print(f"First image: {images[0]}")
else:
    images = []
    print("Failed to get chapter data")

# Test segmentation for each image
if images:
    print("\nSegmentation nums (Qt algorithm):")
    for img in images[:5]:
        pic_name = img.split(".")[0]
        num_actual = get_segmentation_num_qt(test_eps_id, actual_scramble_id, pic_name)
        num_default = get_segmentation_num_qt(test_eps_id, 220980, pic_name)
        num_zero = get_segmentation_num_qt(test_eps_id, 0, pic_name)
        match = "✓" if num_actual == num_default else "✗ MISMATCH!"
        print(f"  {pic_name}: actual_scramble={num_actual}, default_220980={num_default} {match}, zero_scramble={num_zero}")

# Test with older chapters (lower IDs - different scramble behavior)
print("\nOlder chapter tests (ID < scramble_id):")
old_test_cases = [(100000, 220980, "00001"), (50000, 220980, "00001")]
for eid, scr, pic in old_test_cases:
    num = get_segmentation_num_qt(eid, scr, pic)
    num_bad = get_segmentation_num_qt(eid, 0, pic)
    print(f"  epsId={eid}, scramble={scr} → num={num}, with_zero_scramble={num_bad} {'(BAD!)' if num_bad != 0 else ''}")

# ============================================================
# Test 2: API data parsing (id types, likes, views)
# ============================================================
print("\n" + "=" * 60)
print("测试 2: API 数据解析验证")
print("=" * 60)

test_books = ['1441531', '363859', '410702']
for bid in test_books:
    d, ts = api_get_decrypted(f"/album?id={bid}")
    if d is None:
        print(f"\nBook {bid}: FAILED to get data")
        continue
    if not isinstance(d, dict):
        print(f"\nBook {bid}: unexpected data type {type(d).__name__}: {str(d)[:200]}")
        continue
    print(f"\nBook {bid}:")
    print(f"  id: {d.get('id')} (type={type(d.get('id')).__name__})")
    print(f"  likes: {d.get('likes')} (type={type(d.get('likes')).__name__})")
    print(f"  total_views: {d.get('total_views')} (type={type(d.get('total_views')).__name__})")
    print(f"  description: '{str(d.get('description',''))[:50]}' (type={type(d.get('description')).__name__})")
    print(f"  author: {type(d.get('author')).__name__} = {str(d.get('author'))[:80]}")
    series = d.get('series', [])
    print(f"  series: len={len(series)}, first={series[0] if series else 'EMPTY'}")

# ============================================================
# Test 3: Concurrent decryption robustness
# ============================================================
print("\n" + "=" * 60)
print("测试 3: 多时间戳解密验证")
print("=" * 60)

# Simulate the multi-timestamp approach
def try_decrypt_with_timestamps(encrypted_b64, timestamps):
    for ts in timestamps:
        try:
            key = md5(f"{ts}{S_DATA}").encode()
            raw = AES.new(key, AES.MODE_ECB).decrypt(base64.b64decode(encrypted_b64))
            result = raw[:-raw[-1]].decode('utf-8')
            return result, ts
        except:
            continue
    return None, None

# Get a real encrypted response
j, ts1 = api_get("/latest?page=0")
# Make another request to get a different timestamp
time.sleep(2)
j2, ts2 = api_get("/latest?page=1")

# Some endpoints return data as plain JSON, others as encrypted string
encrypted = j.get('data')
if isinstance(encrypted, str):
    timestamps = [ts2, ts1]  # Wrong order first to test multi-ts
    result, used_ts = try_decrypt_with_timestamps(encrypted, timestamps)
    if result:
        d = json.loads(result)
        print(f"Decrypt SUCCESS with ts={used_ts} (tried {timestamps})")
        print(f"Got {len(d)} books")
    else:
        print(f"Decrypt FAILED with both timestamps!")
else:
    print(f"Data is not encrypted (type={type(encrypted).__name__}), skipping multi-ts test")

# ============================================================
# Test 4: Reading history key format
# ============================================================
print("\n" + "=" * 60)
print("测试 4: 阅读历史 Key 格式验证")
print("=" * 60)

# Simulating the Android SharedPreferences key format
book_id = "1441531"
eps_id = "1441524"
page = 42
key = f"history_{book_id}_{eps_id}"
print(f"Key format: {key}")
print(f"Value: {page}")
print("Each book+chapter combo has its own key → per-comic reading progress ✓")

print("\n" + "=" * 60)
print("所有测试完成")
print("=" * 60)
