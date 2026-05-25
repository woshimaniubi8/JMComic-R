#!/usr/bin/env python3
"""
深入诊断：验证 ImageDescrambler.getNum() 的正确性
"""

import hashlib

def get_num_detailed(scramble_id, aid, filename):
    """带详细输出的 get_num 实现"""
    scramble_id = int(scramble_id)
    aid = int(aid)
    
    print(f"\nget_num(scramble_id={scramble_id}, aid={aid}, filename={filename}):")
    
    if aid < scramble_id:
        print(f"  aid < scramble_id: {aid} < {scramble_id} → num = 0")
        return 0
    
    if aid < 268850:
        print(f"  aid < 268850: {aid} < 268850 → num = 10")
        return 10
    
    # 计算复杂的情况
    x = 10 if aid < 421926 else 8
    print(f"  aid >= 268850 and aid < 421926: x = {x}")
    
    s = f"{aid}{filename}"
    md5_hex = hashlib.md5(s.encode()).hexdigest()
    print(f"  MD5('{aid}{filename}') = {md5_hex}")
    
    last_char = md5_hex[-1]
    last_code = ord(last_char)
    mod_result = last_code % x
    num = mod_result * 2 + 2
    
    print(f"  last_char = '{last_char}', ord = {last_code}")
    print(f"  {last_code} % {x} = {mod_result}")
    print(f"  {mod_result} * 2 + 2 = {num}")
    
    return num

# 测试之前失败的用例
print("=== 分析失败的测试用例 ===")
result = get_num_detailed(220980, 300000, "00001")
print(f"Result: {result}")

# 验证 aid < 268850 的情况
print("\n=== 验证 aid < 268850 的情况 ===")
result = get_num_detailed(220980, 100, "00001")
print(f"Result: {result}")

# 验证边界情况
print("\n=== 验证边界情况 ===")
result = get_num_detailed(220980, 268849, "00001")
print(f"Result: {result}")

result = get_num_detailed(220980, 268850, "00001")
print(f"Result: {result}")

# 现在分析 getNumFromUrl 的问题
print("\n\n=== 分析 getNumFromUrl 的 URL 解析 ===")

def analyze_url_parsing(url):
    """分析 URL 解析逻辑"""
    print(f"\nURL: {url}")
    parts = url.split('/')
    print(f"  parts = {parts}")
    print(f"  parts[-2] (aid) = {parts[-2]}")
    print(f"  parts[-1] (filename+ext) = {parts[-1]}")
    
    aid = parts[-2]
    filename_with_ext = parts[-1]
    filename = filename_with_ext.split('.')[0]
    
    print(f"  aid = {aid}")
    print(f"  filename = {filename}")
    
    return aid, filename

# 测试 URL 解析
test_urls = [
    "https://cdn-msp.jmapiproxy3.cc/media/photos/1441531/00001.webp",
    "https://cdn.../media/photos/123/image.jpg",
]

for url in test_urls:
    aid, filename = analyze_url_parsing(url)
    if aid.isdigit():
        num = get_num_detailed(220980, int(aid), filename)
        print(f"  → num = {num}")

# 关键问题：Android 实现中是否正确处理了 scrambleId=0？
print("\n\n=== 关键问题：scrambleId=0 的处理 ===")
print("问题：当 getChapterViewTemplate() 失败或解析失败时，")
print("      scrambleId 默认设为 0 或 220980？")
print()
print("当前逻辑：")
print("  1. BookRepository.getChapterViewTemplate() 失败时返回 ScrambleData(scrambleId='220980')")
print("  2. BookViewModel 中将其转为 Int: scrambleId.toIntOrNull() ?: 220980")
print()
print("所以 scrambleId 永远不应该为 0，最小值是 220980。")
print()
print("但是！问题可能出在：")
print("  - ReaderScreen 初始化时 scrambleId=0（未等待获取）")
print("  - StateFlow 更新时 UI 没有及时重新组合")
print()

# 最后分析：descramble() 算法的正确性
print("\n=== 分析 descramble() 算法 ===")
print("descramble(src, num) 的作用是：")
print("  将高度为 h 的图片分成 num 段")
print("  每段高度 = h / num（向下取整）")
print("  剩余像素 over = h % num 加到第一段")
print()
print("分段映射（从下到上重新排列）：")
print("  原始：[seg0, seg1, ..., segN-1]")
print("  解密后：[segN-1, ..., seg1, seg0]")
print()
print("关键：")
print("  - 当 num=0 时，应该返回原图（不解密）")
print("  - 当 num=1 时，分段数为 1，结果应该还是原图")
print("  - 对于 num >= 2，需要正确计算每段的源位置和目标位置")
