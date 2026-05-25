#!/usr/bin/env python3
"""
验证 1441531 章节的 get_num 值
并检查 Android descramble 算法是否与 JS 完全一致
"""

import hashlib
import base64
import json

# ============================================================
# JS 参考实现（逐行对照 jquery.photo-0.5.js）
# ============================================================

def js_get_num_ref(aid_int, page_idx):
    """JS get_num 完整参考实现"""
    # JS: get_num(window.btoa(aid), window.btoa(page))
    # JS: aid = window.atob(aid) → 解码
    # JS: page = window.atob(page) → 解码
    # JS: key = aid + page
    key = f"{aid_int}page_{page_idx}"
    
    # JS: key = md5(key)
    md5_hex = hashlib.md5(key.encode()).hexdigest()
    # JS: key = key.substr(-1) → 最后1个字符
    last_hex = md5_hex[-1]
    # JS: key = key.charCodeAt()
    charcode = ord(last_hex)
    
    # JS 条件判断
    if aid_int >= 268850 and aid_int <= 421925:
        charcode %= 10
    elif aid_int >= 421926:
        charcode %= 8
    # else: 不取模，默认 num=10（switch 不命中 0-9）
    
    # JS switch-case: num = charcode * 2 + 2
    num = charcode * 2 + 2
    
    return num, md5_hex, last_hex

def android_new_get_num(scramble_id, aid_int, page_idx):
    """Android 新 getNum — 应该与 JS 一致"""
    if aid_int < scramble_id:
        return 0, "", ""
    if aid_int < 268850:
        return 10, "", ""
    
    key = f"{aid_int}page_{page_idx}"
    md5_hex = hashlib.md5(key.encode()).hexdigest()
    last_hex = md5_hex[-1]
    charcode = ord(last_hex)
    
    charcode = charcode % 10 if aid_int <= 421925 else charcode % 8
    
    return charcode * 2 + 2, md5_hex, last_hex

def android_old_get_num(scramble_id, aid_int, filename):
    """Android 旧 getNum — 使用 aid+filename"""
    if aid_int < scramble_id:
        return 0
    if aid_int < 268850:
        return 10
    
    x = 10 if aid_int < 421926 else 8
    key = f"{aid_int}{filename}"
    md5_hex = hashlib.md5(key.encode()).hexdigest()
    last_hex = md5_hex[-1]
    charcode = ord(last_hex)
    return (charcode % x) * 2 + 2

# ============================================================
# 测试
# ============================================================

scramble_id = 220980
aid = 1441531

print(f"=== 验证 aid={aid}, scramble_id={scramble_id} ===\n")

# 从日志中看到的页面
test_pages = [28, 29]  # 00029.webp(pageIndex=28), 00030.webp(pageIndex=29)

for page_idx in test_pages:
    filename = f"{page_idx + 1:05d}"
    
    js_num, js_md5, js_last = js_get_num_ref(aid, page_idx)
    new_num, new_md5, new_last = android_new_get_num(scramble_id, aid, page_idx)
    old_num = android_old_get_num(scramble_id, aid, filename)
    
    match = "✓ MATCH" if js_num == new_num else "✗ MISMATCH"
    
    print(f"Page {page_idx} (file={filename}):")
    print(f"  JS key='{aid}page_{page_idx}'")
    print(f"  JS MD5={js_md5}, last_hex='{js_last}', ord={ord(js_last)}")
    print(f"  JS num={js_num}")
    print(f"  Android NEW num={new_num} {match}")
    print(f"  Android OLD num={old_num}")
    print()

# ============================================================
# 验证 descramble 段映射
# ============================================================

print("\n=== 验证 descramble 算法 (对照 JS onImageLoaded) ===\n")

def js_descramble_check(h, num):
    """JS onImageLoaded 的 descramble 逻辑"""
    remainder = h % num
    segs = []
    for i in range(num):
        copyH = h // num  # Math.floor(h / num)
        py = copyH * i     # 目标Y
        y = h - (copyH * (i + 1)) - remainder  # 源Y
        
        if i == 0:
            copyH += remainder
        else:
            py += remainder
        
        segs.append((y, copyH, py))
    
    # 按目标Y排序
    segs_by_dst = sorted(segs, key=lambda s: s[2])
    return segs, segs_by_dst

def android_descramble_check(h, num):
    """Android descramble 的逻辑"""
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
    
    segs_by_dst = sorted(segs, key=lambda s: s[2])
    return segs, segs_by_dst

# 测试日志中的实际尺寸
w, h = 1220, 1723
num = 16  # 从日志

js_segs, js_by_dst = js_descramble_check(h, num)
android_segs, android_by_dst = android_descramble_check(h, num)

# 比较
all_match = True
for i in range(num):
    js_ys, js_h, js_yd = js_by_dst[i]
    a_ys, a_h, a_yd = android_by_dst[i]
    if (js_ys, js_h, js_yd) != (a_ys, a_h, a_yd):
        all_match = False
        print(f"  seg[{i}]: JS=({js_ys},{js_h},{js_yd}) vs Android=({a_ys},{a_h},{a_yd})")
    else:
        print(f"  seg[{i}]: src[{js_ys}:{js_ys+js_h}]→dst[{js_yd}:{js_yd+js_h}] ✓")

print(f"\n  Descramble match: {'✓ ALL MATCH' if all_match else '✗ MISMATCH!'}")

# 检查总高度
total_js = sum(sh for _, sh, _ in js_segs)
total_a = sum(sh for _, sh, _ in android_segs)
print(f"  Total height: JS={total_js}, Android={total_a}, original={h}")

# ============================================================
# 额外检查：descramble 方向是否正确
# ============================================================
print("\n=== descramble 方向验证 ===")
print(f"h={h}, num={num}, move={h//num}, over={h%num}")
print()

# 原图最小段展示
print("原始（scrambled）图片从上到下的段：")
for ys, sh, yd in sorted(js_segs, key=lambda s: s[0]):
    print(f"  src[{ys}:{ys+sh}] (height={sh}) → dst[{yd}:{yd+sh}]")

print("\n解密后图片从上到下的段：")
for ys, sh, yd in js_by_dst:
    print(f"  dst[{yd}:{yd+sh}] ← src[{ys}:{ys+sh}] (height={sh})")
