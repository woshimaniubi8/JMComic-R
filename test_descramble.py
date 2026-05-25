import hashlib

# 原项目 get_num
def get_num(scramble_id, aid, filename):
    scramble_id = int(scramble_id); aid = int(aid)
    if aid < scramble_id: return 0
    if aid < 268850: return 10
    x = 10 if aid < 421926 else 8
    s = f"{aid}{filename}"; s = s.encode()
    s = hashlib.md5(s).hexdigest()
    return (ord(s[-1]) % x) * 2 + 2

# 原项目 decode_and_save 的段映射
def descramble_map(h, num):
    move = h // num; over = h % num
    segs = []
    for i in range(num):
        m = move
        y_src = h - move * (i + 1) - over
        y_dst = move * i
        if i == 0: m += over
        else: y_dst += over
        segs.append((y_src, m, y_dst))
    return segs

# 测试
for h, num in [(194,6), (194,4), (300,8), (100,3)]:
    segs = descramble_map(h, num)
    ordered = sorted(segs, key=lambda s: s[2])
    print(f"\nh={h} num={num}:")
    for i, (ys, m, yd) in enumerate(ordered):
        print(f"  seg{i}: src[{ys}:{ys+m}] -> dst[{yd}:{yd+m}]")
    
    # 检查 dst 间隙
    gaps = []
    total = 0
    for ys, m, yd in ordered:
        total += m
    print(f"  total_height={total}/{h} {'OK' if total==h else 'MISMATCH!'}")

# 验证我的 Kotlin 代码的输出
print("\n=== Kotlin output for h=194 num=6 ===")
h=194;num=6;over=h%num;move=h//num
for i in range(num):
    segH = move
    ySrc = h - move*(i+1) - over
    yDst = move * i
    if i == 0: segH += over
    else: yDst += over
    print(f"  i={i}: createBitmap(src,0,{ySrc},w,{segH}) drawBitmap(seg,0,{yDst},null)")
