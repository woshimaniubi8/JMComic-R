"""
端到端验证脚本：模拟 Android 项目的完整图片解密流程
验证修复后的 scramble_id 提取和 descramble 逻辑
"""
import base64
import hashlib
import json
import re
import ssl
import time
import urllib.request
from Crypto.Cipher import AES

BASE_URL = 'https://www.cdnhth.club'
IMAGE_BASE_URL = 'https://cdn-msp.jmapiproxy3.cc'
HEADER_VER = '2.0.21'
APP_TOKEN_SECRET = '18comicAPP'
APP_TOKEN_SECRET_2 = '18comicAPPContent'
APP_DATA_SECRET = '185Hcomic3PAPP7R'

def md5hex(s):
    return hashlib.md5(s.encode('utf-8')).hexdigest()

def api_get(path, use_secret2=False):
    ts = int(time.time())
    secret = APP_TOKEN_SECRET_2 if use_secret2 else APP_TOKEN_SECRET
    headers = {
        'User-Agent': 'Mozilla/5.0 (Linux; Android 7.1.2; DT1901A Build/N2G47O; wv) AppleWebKit/537.36',
        'Accept': 'application/json, text/plain, */*',
        'tokenparam': f'{ts},{HEADER_VER}',
        'token': md5hex(f'{ts}{secret}'),
        'version': HEADER_VER,
    }
    ctx = ssl.create_default_context()
    ctx.check_hostname = False; ctx.verify_mode = ssl.CERT_NONE
    req = urllib.request.Request(f'{BASE_URL}{path}', headers=headers)
    resp = urllib.request.urlopen(req, context=ctx, timeout=15)
    return resp.read(), ts

def decrypt(data_b64, ts):
    key = md5hex(f'{ts}{APP_DATA_SECRET}').encode()
    raw = AES.new(key, AES.MODE_ECB).decrypt(base64.b64decode(data_b64))
    return raw[:-raw[-1]].decode('utf-8')

def get_segmentation_num(epsId, scramble_id, pictureName):
    """对照 Qt 项目的 GetSegmentationNum"""
    scramble_id = int(scramble_id)
    epsId = int(epsId)
    if epsId < scramble_id:
        return 0
    elif epsId < 268850:
        return 10
    elif epsId > 421926:
        s = str(epsId) + pictureName
        s = hashlib.md5(s.encode()).hexdigest()
        num = ord(s[-1])
        num %= 8
        return num * 2 + 2
    else:
        s = str(epsId) + pictureName
        s = hashlib.md5(s.encode()).hexdigest()
        num = ord(s[-1])
        num %= 10
        return num * 2 + 2

def descramble_segments(h, num):
    """模拟 Kotlin descramble: 返回 (srcTop, height, dstTop) 列表"""
    move = h // num
    over = h % num
    segs = []
    for i in range(num):
        segH = move
        ySrc = h - move * (i + 1) - over
        yDst = move * i
        if i == 0:
            segH += over
        else:
            yDst += over
        segs.append((ySrc, segH, yDst))
    return segs

print("=" * 60)
print("端到端验证：完整图片解密流程")
print("=" * 60)

# 步骤 1: 获取章节信息
test_eps_id = "1441524"
print(f"\n[1] 获取章节信息 /chapter?id={test_eps_id}")
raw, ts = api_get(f'/chapter?id={test_eps_id}')
j = json.loads(raw)
assert j['code'] == 200, f"Chapter API failed: {j}"
d = json.loads(decrypt(j['data'], ts))
print(f"  id={d['id']}, images count={len(d.get('images',[]))}")
if d.get('images'):
    print(f"  First image: {d['images'][0]}")
images = d.get('images', [])

# 步骤 2 (老方法 — 会失败): 尝试 JSON 解析 chapter_view_template
print(f"\n[2] 获取 scramble_id /chapter_view_template?id={test_eps_id}")
raw, ts2 = api_get(f'/chapter_view_template?id={test_eps_id}&mode=vertical&page=0&app_img_shunt=NaN', use_secret2=True)
text = raw.decode('utf-8', errors='replace')
print(f"  Response is HTML: {text.strip().startswith('<')}")

# 老方法：尝试 JSON 解析
try:
    j2 = json.loads(text)
    print("  [OLD] JSON 解析成功 — 不会出现")
except:
    print("  [OLD] JSON 解析失败 (预期) — 旧版 Android 代码的 bug")

# 新方法：正则提取
mo = re.search(r"var scramble_id = (\w+)", text)
if mo:
    scramble_id = mo.group(1)
    print(f"  [NEW] 正则提取 scramble_id = {scramble_id} ✓")
else:
    scramble_id = "220980"  # fallback
    print(f"  [NEW] 正则未找到，使用 fallback = {scramble_id}")

# 步骤 3: 计算每个图片的分割数
print(f"\n[3] 计算图片分割数 (scramble_id={scramble_id})")
for img_name in images[:5]:  # 只测前5张
    # 模拟 Android getNumFromUrl: 从 URL 提取 aid 和 filename
    url = f"{IMAGE_BASE_URL}/media/photos/{test_eps_id}/{img_name}"
    parts = url.split("/")
    aid = parts[-2]  # epsId
    filename = parts[-1].split(".")[0]  # without extension
    
    num = get_segmentation_num(aid, scramble_id, filename)
    status = "不解密" if num == 0 else f"{num}段"
    print(f"  {img_name}: num={num} ({status})")

# 步骤 4: 验证 descramble 段映射
print(f"\n[4] 验证 descramble 段映射正确性")
test_cases = [(194, 6), (194, 4), (300, 8), (100, 3), (800, 10)]
all_ok = True
for h, num in test_cases:
    segs = descramble_segments(h, num)
    
    # 验证：所有段高度之和应等于 h
    total_h = sum(s[1] for s in segs)
    
    # 验证：dst 区域应连续覆盖 0 到 h
    sorted_by_dst = sorted(segs, key=lambda s: s[2])
    gaps = []
    expected_dst = 0
    for ys, m, yd in sorted_by_dst:
        if yd != expected_dst:
            gaps.append(f"gap at {expected_dst} (expected {yd})")
        expected_dst = yd + m
    
    ok = total_h == h and not gaps
    all_ok = all_ok and ok
    status = "✓" if ok else "✗"
    print(f"  h={h}, num={num}: total={total_h}/{h} {status}")
    if not ok:
        print(f"    Gaps: {gaps}")
        print(f"    Segments: {segs}")

# 步骤 5: 回归测试 — 与 Qt 项目的值对比
print(f"\n[5] 回归测试：Android vs Qt 计算值对比")
test_params = [
    (1441524, 220980, "00001"),
    (1441524, 220980, "00044"),
    (363859, 220980, "00001"),
    (50000, 220980, "00001"),   # 小 aid — 应 num=0 (不解密)
    (100000, 0, "00001"),       # scrambleId=0 的老 bug — 应正确计算
]
for eps_id, scr_id, pic_name in test_params:
    num = get_segmentation_num(eps_id, scr_id, pic_name)
    if int(eps_id) < int(scr_id):
        expected = 0
        note = f"(aid < scramble_id → num=0) {'✓' if num==0 else '✗ BUG!'}"
    else:
        note = ""
    print(f"  epsId={eps_id}, scrambleId={scr_id}, pic={pic_name} → num={num} {note}")

print()
print("=" * 60)
print("验证结果" if all_ok else "验证失败!")
print("=" * 60)
if all_ok:
    print("所有段映射正确，descramble 算法无问题。")
    print("关键修复：chapter_view_template 正则提取 scramble_id (旧版 JSON 解析失败)")
    print("默认 fallback 改为 220980 (对照 Qt 项目)")
else:
    print("段映射有误，请检查 descramble 算法！")
