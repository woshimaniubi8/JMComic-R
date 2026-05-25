#!/usr/bin/env python3
"""
关键诊断：尝试从 API 获取 scramble_image 的 JavaScript 实现
并测试 Android descramble 算法

根据日志分析：
1. aid=1439589, scramble_id=220980
2. aid > scramble_id → 需要解密
3. 图片格式：webp
4. CDN: jmapiproxy3.cc (Android) vs jmdanjonproxy.xyz (模板)
"""

import hashlib
import requests
import re

BASE_URL = "https://www.cdnhth.club"

# ============================================================
# 1. 尝试获取 jquery.photo-0.5.js
# ============================================================
print("=== 尝试获取 scramble_image 实现 ===")
try:
    resp = requests.get(f"{BASE_URL}/templates/frontend/airav/js/jquery.photo-0.5.js?v=2025122901", 
                       timeout=10,
                       headers={"User-Agent": "Mozilla/5.0"})
    if resp.status_code == 200:
        # 搜索 scramble_image 函数定义
        text = resp.text
        # 查找关键函数
        patterns = [
            r'function scramble_image[^}]*\}',
            r'scramble_image\s*=\s*function[^}]*\}',
        ]
        for pattern in patterns:
            matches = re.findall(pattern, text, re.DOTALL)
            if matches:
                print(f"Found scramble_image with pattern {pattern}:")
                for m in matches[:3]:
                    print(f"  {m[:500]}...")
                    print()
        
        # 也搜索 get_num / aid / scramble_id
        for keyword in ['get_num', 'scramble_id', 'aid', 'move', 'over', 'segment']:
            found = [line for line in text.split('\n') if keyword in line.lower()]
            if found:
                print(f"Lines containing '{keyword}' (first 5):")
                for line in found[:5]:
                    print(f"  {line.strip()[:200]}")
        print(f"\nTotal JS file size: {len(text)} chars")
    else:
        print(f"Failed to fetch: HTTP {resp.status_code}")
except Exception as e:
    print(f"Error: {e}")

# ============================================================
# 2. 使用 Python 实现 Android 的 getNum 和 descramble
# ============================================================
print("\n\n=== 计算本测试章节的解密参数 ===")
aid = 1439589
scramble_id = 220980

def get_num(scramble_id, aid, filename):
    if aid < scramble_id:
        return 0
    if aid < 268850:
        return 10
    x = 10 if aid < 421926 else 8
    s = f"{aid}{filename}"
    md5_hex = hashlib.md5(s.encode()).hexdigest()
    return (ord(md5_hex[-1]) % x) * 2 + 2

# 测试多个图片
for pic_idx in [1, 2, 3, 10, 47]:
    filename = f"{pic_idx:05d}"
    num = get_num(scramble_id, aid, filename)
    print(f"  {filename}: num={num}")

# ============================================================
# 3. 模拟 descramble 的段映射（用 ASCII 图说明问题）
# ============================================================
print("\n\n=== descramble 段映射模拟 ===")

def descramble_map(h, num):
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
        segs.append((ySrc, segH, yDst, i))
    return sorted(segs, key=lambda s: s[0]), segs

num = 8
h = 800
by_src, by_dst = descramble_map(h, num)

print(f"h={h}, num={num}, move={h//num}, over={h%num}")
print("\n按照源位置排列（从上到下读取原图）：")
for ys, sh, yd, idx in by_src:
    bar = "█" * (sh // 20)
    print(f"  src[{ys:4d}:{ys+sh:4d}] → dst[{yd:4d}:{yd+sh:4d}] [{bar}]")

print("\n按照目标位置排列（从上到下生成新图）：")
for ys, sh, yd, idx in by_dst:
    bar = "█" * (sh // 20)
    print(f"  dst[{yd:4d}:{yd+sh:4d}] ← src[{ys:4d}:{ys+sh:4d}] [{bar}]")

# ============================================================
# 4. 检查 Android 代码中的关键问题
# ============================================================
print("\n\n=== 关键问题检查 ===")
print("""
1. getNumFromUrl 中的 aid 提取：
   URL: https://cdn-msp.jmapiproxy3.cc/media/photos/1439589/00001.webp
   parts[-2] = "1439589" → aid=1439589 ✓

2. scrambleId 是否正确传递：
   - BookViewModel._scrambleId 默认值: 220980 ✓（已修复）
   - ReaderScreen 接收 scrambleId 参数 ✓
   - DescrambleUrlTransformation 使用 scrambleId ✓

3. Coil 的变换时机：
   - ImageRequest.transformations() 在加载后、显示前调用
   - 每次显示新页面时创建新的 ImageRequest
   - cacheKey 包含 scrambleId，不同 scrambleId 会创建不同的缓存键

4. 可能的问题：
   - 如果 Coil 缓存了错误的变换结果，需要清除缓存
   - 如果图片在 scrambleId 更新前就被加载并缓存了
""")

# ============================================================
# 5. 推荐修复方案
# ============================================================
print("\n\n=== 推荐修复方案 ===")
print("""
方案A：添加更多日志到 ImageDescrambler
  - 在 getNumFromUrl 中打印 aid, filename, num
  - 在 descramble 中打印 num, w, h, move, over
  
方案B：检查是否是 .webp 格式问题
  - Android BitmapFactory 可能对 webp 处理有差异
  - 尝试先将 webp 转为普通 bitmap 再解密

方案C：尝试备选 descramble 算法
  - 当前算法是"从底到顶"逆序排列
  - 可能需要"从顶到底"或其他排列方式

方案D：清除 Coil 图片缓存
  - 在 getEpisodeDetail 中清除旧的图片缓存
  - 防止使用缓存的错误变换结果

方案E：使用 aid 而非 epsId 作为 URL 路径中的 aid
  - 当前使用 epsId 构建 URL 并从 URL 提取 aid
  - 需要确认 aid 应该是 series_id 还是 epsId
""")
