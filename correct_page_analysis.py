#!/usr/bin/env python3
"""
关键纠正：重新解析 JS onImageLoaded 中的 page 值

之前我错误地认为 page = "page_0"
实际上：img.parentNode 是外层 div（id="00001.webp"），page = "00001.webp".split(".")[0] = "00001"
"""

import hashlib
import base64

# ============================================================
# 正确的 JS 逻辑
# ============================================================

"""
DOM 结构 (从模板 HTML):
  <div id="00001.webp" class="center scramble-page">    ← img.parentNode
    <div id="page_0" style="height:0"></div>
    <img id="album_photo_0" .../>                         ← img
  </div>

JS onImageLoaded:
  img = document.getElementById("album_photo_0")
  page = img.parentNode          → div#00001.webp
  page = page.id.split(".")      → ["00001", "webp"]
  page = page[0]                 → "00001"
  
  get_num(btoa(aid), btoa("00001"))
  
  Inside get_num:
    aid = atob(btoa(aid))       → "1441531"  
    page = atob(btoa("00001"))  → "00001"
    key = "1441531" + "00001"   → "144153100001"
"""

def js_get_num_correct(aid_int, filename_no_ext):
    """正确的 JS get_num — page = filename (e.g. "00001")"""
    # JS: key = aid + page, 其中 aid=atob(btoa(aid)), page=atob(btoa("00001"))
    key = f"{aid_int}{filename_no_ext}"
    
    md5_hex = hashlib.md5(key.encode()).hexdigest()
    last_hex = md5_hex[-1]
    charcode = ord(last_hex)
    
    if aid_int >= 268850 and aid_int <= 421925:
        charcode %= 10
    elif aid_int >= 421926:
        charcode %= 8
    
    return charcode * 2 + 2, md5_hex

def android_old_get_num(scramble_id, aid_int, filename_no_ext):
    """Android 旧 getNum — 实际是正确的！"""
    if aid_int < scramble_id:
        return 0
    if aid_int < 268850:
        return 10
    
    x = 10 if aid_int < 421926 else 8
    key = f"{aid_int}{filename_no_ext}"
    md5_hex = hashlib.md5(key.encode()).hexdigest()
    return (ord(md5_hex[-1]) % x) * 2 + 2, md5_hex

def android_wrong_get_num(aid_int, page_idx):
    """Android 错误的"修复" — 使用 page_N 格式"""
    key = f"{aid_int}page_{page_idx}"
    md5_hex = hashlib.md5(key.encode()).hexdigest()
    charcode = ord(md5_hex[-1])
    charcode = charcode % 10 if aid_int <= 421925 else charcode % 8
    return charcode * 2 + 2, md5_hex

# ============================================================
# 对比测试
# ============================================================

scramble_id = 220980
aid = 1441531

print("=== JS page 值纠正验证 ===\n")
print("JS 实际逻辑: img.parentNode.id = '00001.webp', page = '00001'")
print("key = aid + page = aid + '00001'")
print()

print(f"测试 aid={aid}, scramble_id={scramble_id}\n")

for pic_num in [1, 29, 30]:
    filename = f"{pic_num:05d}"
    page_idx = pic_num - 1
    
    js_num, js_md5 = js_get_num_correct(aid, filename)
    old_num, old_md5 = android_old_get_num(scramble_id, aid, filename)
    wrong_num, wrong_md5 = android_wrong_get_num(aid, page_idx)
    
    match_old = "✓ MATCH JS" if js_num == old_num else "✗"
    match_wrong = "✓" if js_num == wrong_num else "✗ WRONG!"
    
    print(f"图像 {filename} (pageIndex={page_idx}):")
    print(f"  JS correct:     key='{aid}{filename}' MD5={js_md5} → num={js_num}")
    print(f"  Android OLD:    key='{aid}{filename}' MD5={old_md5} → num={old_num} {match_old}")
    print(f"  Android WRONG:  key='{aid}page_{page_idx}' MD5={wrong_md5} → num={wrong_num} {match_wrong}")
    print()

# ============================================================
# 验证 descramble 算法也正确
# ============================================================
print("=== descramble 算法验证 ===")

# JS onImageLoaded:
# ctx.drawImage(img, 0, y, copyW, copyH, 0, py, copyW, copyH)
# 即: drawImage(src_img, srcX, srcY, srcW, srcH, dstX, dstY, dstW, dstH)
# srcX=0, srcY=y, srcW=copyW, srcH=copyH
# dstX=0, dstY=py, dstW=copyW, dstH=copyH
#
# Android:
# canvas.drawBitmap(src, srcRect, dstRect, null)
# srcRect = Rect(0, ySrc, w, ySrc+segH)
# dstRect = Rect(0, yDst, w, yDst+segH)

# 两者等价 ✓

print("descramble 段映射已在前一脚本中验证与 JS 完全一致 ✓")
print()

print("=== 结论 ===")
print("Android OLD getNum 实现是**正确的**！")
print("错误的修复需要回退：key 格式从 aid+page_N 改回 aid+filename")
print()
print("之前条纹问题的根因可能只是 scrambleId 初始值为 0 的问题（已修复）")
print("或者是其他原因（如 Coil 缓存、webp 解码差异等）")
