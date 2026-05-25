#!/usr/bin/env python3
"""
获取 scramble_image 和 get_num 的完整实现，并与 Android 对比
"""
import requests
import re
import hashlib
import base64

BASE_URL = "https://www.cdnhth.club"

print("=== 获取 jquery.photo-0.5.js ===")
try:
    resp = requests.get(f"{BASE_URL}/templates/frontend/airav/js/jquery.photo-0.5.js?v=2025122901",
                       timeout=10,
                       headers={"User-Agent": "Mozilla/5.0"})
    text = resp.text
    print(f"JS file size: {len(text)} chars\n")
    
    # 打印关键部分
    lines = text.split('\n')
    
    # 查找 get_num 函数
    print("=== get_num 函数 ===")
    in_func = False
    func_lines = []
    brace_count = 0
    for i, line in enumerate(lines):
        if 'function get_num' in line:
            in_func = True
            func_lines = [line]
            brace_count = line.count('{') - line.count('}')
            continue
        if in_func:
            func_lines.append(line)
            brace_count += line.count('{') - line.count('}')
            if brace_count <= 0 and len(func_lines) > 1:
                break
    
    if func_lines:
        for l in func_lines:
            print(f"  {l}")
    else:
        print("  NOT FOUND")
    
    # 打印 scramble_image 函数
    print("\n=== scramble_image 函数 ===")
    in_func = False
    func_lines = []
    brace_count = 0
    for i, line in enumerate(lines):
        if 'function scramble_image' in line:
            in_func = True
            func_lines = [line]
            brace_count = line.count('{') - line.count('}')
            continue
        if in_func:
            func_lines.append(line)
            brace_count += line.count('{') - line.count('}')
            if brace_count <= 0 and len(func_lines) > 1:
                break
    
    if func_lines:
        for l in func_lines[:100]:
            print(f"  {l}")
    else:
        print("  NOT FOUND")
    
    # 打印完整文件（如果小）
    if len(text) < 10000:
        print("\n=== 完整 JS 文件 ===")
        print(text)
    
except Exception as e:
    print(f"Error: {e}")

# ============================================================
# 对比分析
# ============================================================
print("\n\n=== 关键差异分析 ===")

# JS: get_num(window.btoa(aid), window.btoa(page))
# btoa() 是 Base64 编码

test_aid = 1439589
test_page = 1

# JS 的做法
js_aid_b64 = base64.b64encode(str(test_aid).encode()).decode()
js_page_b64 = base64.b64encode(str(test_page).encode()).decode()

print(f"JS get_num 参数:")
print(f"  btoa(aid={test_aid}) = '{js_aid_b64}'")
print(f"  btoa(page={test_page}) = '{js_page_b64}'")

# Android 的做法
android_s = f"{test_aid}{test_page:05d}"  # "143958900001"
print(f"\nAndroid getNum 参数:")
print(f"  s = '{android_s}'")

print(f"\n差异:")
print(f"  JS get_num 内部用 aid+00001? → btoa(aid)+'00001' = '{js_aid_b64}00001'")
print(f"  Android getNum 用 aid+filename → '{android_s}'")
print(f"  完全不同！")

# 如果 JS get_num 内部是：str = aid + page
# 其中 aid 和 page 都是 base64 编码的
js_s = js_aid_b64 + js_page_b64
print(f"\n如果 JS get_num 内部用 aid+page:")
print(f"  str = '{js_s}'")
js_md5 = hashlib.md5(js_s.encode()).hexdigest()
print(f"  MD5 = {js_md5}")

android_md5 = hashlib.md5(android_s.encode()).hexdigest()
print(f"\nAndroid:")
print(f"  str = '{android_s}'")
print(f"  MD5 = {android_md5}")
