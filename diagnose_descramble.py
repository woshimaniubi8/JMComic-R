#!/usr/bin/env python3
"""
深入诊断脚本：分析 scramble_image 的实际实现

策略：从 chapter_view_template HTML 响应中，分析 JS 加载的脚本文件
"""

import hashlib
import re
import json

# ============================================================
# 1. 分析 Android 当前的 descramble 实现
# ============================================================

def android_get_num(scramble_id, aid, filename):
    """Android ImageDescrambler.getNum — 当前实现"""
    if aid < scramble_id:
        return 0
    if aid < 268850:
        return 10
    x = 10 if aid < 421926 else 8
    s = f"{aid}{filename}"
    md5_hex = hashlib.md5(s.encode()).hexdigest()
    return (ord(md5_hex[-1]) % x) * 2 + 2

def android_descramble_segments(h, num):
    """Android ImageDescrambler.descramble 的分段映射"""
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

# ============================================================
# 2. 从模板 JS 代码推断 scramble_image 算法
# 关键线索：scramble_image(imgObj[0], aid, scramble_id, false, speed)
# 函数签名：scramble_image(img, aid, scramble_id, is_xxx, speed)
# 
# 从 jquery.photo-0.5.js 可以推断：
# - aid 用于计算分段数
# - scramble_id 用于判断是否需要 descramble
# - 图片在 canvas 中重新排列
# ============================================================

print("=== 深入分析 descramble 算法 ===\n")

# 从日志中提取的信息
aid = 1439589
scramble_id = 220980

print(f"章节 ID: {aid}")
print(f"scramble_id: {scramble_id}")
print(f"aid > scramble_id? {aid > scramble_id}")

if aid > scramble_id:
    print("→ 需要解密\n")
else:
    print("→ 不需要解密 (aid <= scramble_id)\n")

# 计算几个用例的分段数
print("=== 计算分割数 ===")
test_pics = ["00001", "00005", "00010", "00047"]
for pic in test_pics:
    num = android_get_num(scramble_id, aid, pic)
    print(f"  pic={pic}, num={num}")

# 验证 descramble 是否正确
print("\n=== 验证 descramble 分段映射 (模拟 h=800, num 从计算结果) ===")
h = 800
num1 = android_get_num(scramble_id, aid, "00001")
print(f"\nnum={num1}, h={h}:")
segs = android_descramble_segments(h, num1)
for i, (ys, sh, yd) in enumerate(segs):
    print(f"  seg[{i}]: src[{ys}:{ys+sh}] → dst[{yd}:{yd+sh}]")

# ============================================================
# 3. 关键发现：descramble 算法可能的 bug
# ============================================================

print("\n\n=== 关键分析：descramble 可能的错误 ===")
print("""
当前 Android descramble 算法：
  将图片分成 num 段，从底到顶重新排列
  - i=0: 取最底部一段（含 over），放到最顶部
  - i=1: 取倒数第二段，放到第二位置
  - ...

这可能与 JS scramble_image 算法不同。

从 JavaScript 代码中可以看到：
  `if (parseInt(aid) > parseInt(scramble_id))`

JS 中的 scramble_image 函数（来自 jquery.photo-0.5.js）是闭源的。
我们需要验证 Android 实现是否与 JS 实现一致。

可能的问题：
1. 分割方式不同（从上到下 vs 从下到上）
2. 第一段含 over 的方式不同
3. num 的计算方式不同
4. aid 的提取方式不同（从 URL vs 从 API 响应）
""")

# ============================================================
# 4. 测试不同的 descramble 策略
# ============================================================

def alt_descramble_segments_v1(h, num):
    """备选算法1：从上到下重新排列（而非从下到上）"""
    move = h // num
    over = h % num
    segs = []
    for i in range(num):
        segH = move
        ySrc = move * i  # 从顶部开始取
        yDst = h - move * (i + 1) - over  # 放到从底部开始的位置
        if i == num - 1:  # 最后一段含 over
            segH += over
        if i > 0:
            yDst += over
        segs.append((ySrc, segH, yDst))
    return segs

def alt_descramble_segments_v2(h, num):
    """备选算法2：over 加到所有段均匀分布（而非只在第一段）"""
    move = h // num
    over = h % num
    segs = []
    ySrc = 0
    yDst = 0
    for i in range(num):
        segH = move + (1 if i < over else 0)  # over 均匀分布
        segs.append((ySrc, segH, yDst))
        ySrc += segH
        yDst += segH
    # 反转 dst 位置
    segs_rev = []
    cum_dst = 0
    for i in range(num - 1, -1, -1):
        ys, sh, _ = segs[i]
        segs_rev.append((ys, sh, cum_dst))
        cum_dst += sh
    return segs_rev

print("=== 备选 descramble 算法测试 ===")
h = 800
num = 6
print(f"\n当前算法 (底→顶):")
for ys, sh, yd in android_descramble_segments(h, num):
    print(f"  src[{ys}:{ys+sh}] → dst[{yd}:{yd+sh}]")

print(f"\n备选算法1 (顶→底):")
for ys, sh, yd in alt_descramble_segments_v1(h, num):
    print(f"  src[{ys}:{ys+sh}] → dst[{yd}:{yd+sh}]")

print(f"\n备选算法2 (均匀over + 反转):")
for ys, sh, yd in alt_descramble_segments_v2(h, num):
    print(f"  src[{ys}:{ys+sh}] → dst[{yd}:{yd+sh}]")
