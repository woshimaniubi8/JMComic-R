#!/usr/bin/env python3
"""
验证 Android 和 JS 的 get_num 差异
"""
import hashlib
import base64

# JS 的实现（从 jquery.photo-0.5.js 提取）
def js_get_num(aid_str, page_str):
    """JS get_num — aid_str 和 page_str 是 btoa 编码的"""
    aid = base64.b64decode(aid_str).decode()
    page = base64.b64decode(page_str).decode()
    
    num = 10  # default
    key = aid + page
    key = hashlib.md5(key.encode()).hexdigest()
    key = key[-1]  # last char
    key = ord(key)  # charCode
    
    aid_int = int(aid)
    if aid_int >= 268850 and aid_int <= 421925:
        key = key % 10
    elif aid_int >= 421926:
        key = key % 8
    
    # switch-case
    num = key * 2 + 2
    return num

def android_get_num(scramble_id, aid, filename):
    """Android 当前 getNum — 使用 aid + filename"""
    aid = int(aid)
    if aid < scramble_id:
        return 0
    if aid < 268850:
        return 10
    x = 10 if aid < 421926 else 8
    s = f"{aid}{filename}"
    md5_hex = hashlib.md5(s.encode()).hexdigest()
    return (ord(md5_hex[-1]) % x) * 2 + 2

def js_get_num_direct(aid_int, page_idx):
    """JS get_num 直接用 aid_int 和 page_idx 值"""
    key = f"{aid_int}page_{page_idx}"
    md5_hex = hashlib.md5(key.encode()).hexdigest()
    key = ord(md5_hex[-1])
    
    if aid_int >= 268850 and aid_int <= 421925:
        key = key % 10
    elif aid_int >= 421926:
        key = key % 8
    
    return key * 2 + 2

# ============================================================
# 对比测试
# ============================================================
aid = 1439589
scramble_id = 220980

print("=== Android vs JS get_num 对比 ===\n")
print(f"aid={aid}, scramble_id={scramble_id}\n")

# JS: btoa("1439589") 和 btoa("page_0"), btoa("page_1"), etc.
for page_idx in range(5):
    page_str = f"page_{page_idx}"
    js_num = js_get_num(
        base64.b64encode(str(aid).encode()).decode(),
        base64.b64encode(page_str.encode()).decode()
    )
    
    # Android: filename
    filename = f"{page_idx + 1:05d}"
    android_num = android_get_num(scramble_id, aid, filename)
    
    # JS direct (我们的新实现)
    js_direct_num = js_get_num_direct(aid, page_idx)
    
    match_android = "MATCH" if js_num == android_num else "MISMATCH!"
    match_direct = "MATCH" if js_num == js_direct_num else "MISMATCH!"
    
    print(f"Page {page_idx} (file={filename}):")
    print(f"  JS get_num:   key='{aid}page_{page_idx}' → MD5 → num={js_num}")
    print(f"  Android getNum: key='{aid}{filename}' → MD5 → num={android_num}  ← {match_android}")
    print(f"  JS direct fix: key='{aid}page_{page_idx}' → MD5 → num={js_direct_num}  ← {match_direct}")
    print()

# 验证修复方案
print("=== 修复方案验证 ===\n")
print("需要修改 ImageDescrambler.getNum() 和 getNumFromUrl()：")
print("1. getNum 的参数改为 pageIndex（0-based）而非 filename")
print("2. key 改为 \"${aid}page_${pageIndex}\" 格式")
print("3. getNumFromUrl 从 filename 提取 page index: '00001' → 0, '00047' → 46")
print()

# 验证更多用例
print("=== 更多测试用例 ===")
test_aids = [100, 268850, 300000, 421926, 1439589]
for test_aid in test_aids:
    for page_idx in [0, 1, 4]:
        js_num = js_get_num_direct(test_aid, page_idx)
        print(f"  aid={test_aid}, page={page_idx}: num={js_num}")
