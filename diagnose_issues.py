#!/usr/bin/env python3
"""
诊断脚本：分析两个已知问题
1. 图片解密条纹问题
2. 首页详情页数据为空问题
"""

import hashlib
import json
import sys

# ============ 问题1：图片解密算法验证 ============

def get_num_reference(scramble_id, aid, filename):
    """原项目 get_num 参考实现"""
    scramble_id = int(scramble_id)
    aid = int(aid)
    if aid < scramble_id:
        return 0
    if aid < 268850:
        return 10
    x = 10 if aid < 421926 else 8
    s = f"{aid}{filename}"
    s = s.encode()
    md5_hex = hashlib.md5(s).hexdigest()
    return (ord(md5_hex[-1]) % x) * 2 + 2

def get_num_from_url_reference(scramble_id, url):
    """从 URL 提取 aid 和 filename"""
    # URL 格式: https://cdn.../media/photos/{aid}/{filename}.ext
    parts = url.split('/')
    aid = parts[-2] if len(parts) > 1 else "0"
    filename_with_ext = parts[-1] if parts else ""
    filename = filename_with_ext.split('.')[0] if filename_with_ext else ""
    
    return get_num_reference(scramble_id, aid, filename)

def analyze_descramble_logic():
    """分析解密逻辑"""
    print("=== 问题1：图片解密条纹分析 ===\n")
    
    # 测试用例：验证 ImageDescrambler.getNum() 的正确性
    test_cases = [
        # (scramble_id, aid, filename, expected_num)
        (220980, 100, "00001", 0),       # aid < scramble_id → num=0 (不解密)
        (220980, 300000, "00001", 10),   # aid >= scramble_id, aid < 268850 → num=10
        (220980, 300000, "00001", 10),   # 重复测试
        (220980, 400000, "00001", None), # aid >= 268850, 需要计算
    ]
    
    print("测试 get_num() 逻辑：")
    for scramble_id, aid, filename, expected in test_cases:
        actual = get_num_reference(scramble_id, aid, filename)
        status = "✓" if expected is None or actual == expected else "✗"
        print(f"  {status} scramble_id={scramble_id}, aid={aid}, filename={filename}")
        print(f"     expected={expected}, actual={actual}")
    
    # 关键问题：ImageDescrambler.getNumFromUrl() 中提取 aid 和 filename 的逻辑
    print("\n测试 getNumFromUrl()：")
    test_urls = [
        "https://cdn-msp.jmapiproxy3.cc/media/photos/1441531/00001.webp",
        "https://cdn-msp.jmapiproxy3.cc/media/photos/123/image.jpg",
    ]
    
    for url in test_urls:
        num = get_num_from_url_reference(220980, url)
        print(f"  URL: {url}")
        print(f"    → num={num}\n")
    
    # 关键问题：当 scrambleId=0 时的行为
    print("关键问题：scrambleId=0 时的行为")
    print(f"  get_num(scrambleId=0, aid=1441531, filename='00001') = {get_num_reference(0, 1441531, '00001')}")
    print(f"  get_num(scrambleId=0, aid=100, filename='00001') = {get_num_reference(0, 100, '00001')}")
    print("\n  分析：")
    print("    - 当 scrambleId=0 时，aid < scrambleId 总是 False")
    print("    - 因此 get_num(0, X, Y) 会返回 10（如果 X<268850）或计算值")
    print("    - 这会导致所有旧章节都被解密，可能导致条纹\n")

# ============ 问题2：首页详情页数据为空分析 ============

def analyze_detail_loading():
    """分析详情页数据加载问题"""
    print("=== 问题2：首页详情页数据为空分析 ===\n")
    
    print("可能的原因：")
    print("1. BookDetail 序列化问题")
    print("   - SafeStringSerializer 可能未正确应用到 likes/totalViews")
    print("   - description 字段可能为 null")
    print("   - 这些字段的 JSON 值可能是 int/string/null 的混合\n")
    
    print("2. 导航时的数据重置")
    print("   - bookViewModel.getBookDetail(id) 前，_bookDetail.value = null")
    print("   - 但 BookDetailScreen 中的 key(id) 应该强制重组")
    print("   - 问题：如果 key(id) 没有生效，可能显示旧数据\n")
    
    print("3. API 响应差异")
    print("   - 首页的搜索列表可能没有返回 likes/totalViews 等详细字段")
    print("   - 从首页进入详情页时，需要重新请求 /album API")
    print("   - 如果 /album API 的响应与 /latest API 不同，可能导致显示问题\n")
    
    # 示例：SafeStringSerializer 的问题
    print("SafeStringSerializer 兼容性测试：")
    example_values = [
        ('0', "string '0'"),
        (0, "int 0"),
        ('null', "JSON null"),
        ('100', "string '100'"),
        (100, "int 100"),
    ]
    
    for val, desc in example_values:
        # 模拟 SafeStringSerializer 的行为
        if val is None or val == 'null':
            result = ""
        elif isinstance(val, str):
            result = val
        else:
            result = str(val)
        print(f"  {desc:20} → '{result}'")
    
    print("\n关键问题：")
    print("  JSON 解析时，如果 likes='0' 但 API 返回的是 null，")
    print("  SafeStringSerializer.deserialize() 应该返回 ''，")
    print("  但后续的 detail.totalLikes: Int get() = likes.toIntOrNull() ?: 0")
    print("  会将 '' 转为 0，这是正确的。\n")

# ============ 根本原因总结 ============

def summarize_fixes():
    """总结修复方案"""
    print("=== 修复方案总结 ===\n")
    
    print("问题1：图片解密条纹")
    print("  根因：ImageDescrambler.descramble() 或 getNum() 实现有误")
    print("  修复步骤：")
    print("    1. 验证 getNum() 逻辑是否正确")
    print("    2. 验证 getNumFromUrl() 中 aid 提取是否正确")
    print("    3. 验证 descramble() 中的分段映射是否正确")
    print("    4. 确保 scrambleId=0 时返回 num=0（不解密）\n")
    
    print("问题2：首页详情页数据为空")
    print("  根因：可能是序列化问题或 UI 缓存问题")
    print("  修复步骤：")
    print("    1. 检查 API 返回的 /album 响应格式")
    print("    2. 验证 SafeStringSerializer 是否应用到所有需要的字段")
    print("    3. 验证 BookDetailScreen 中的 key(id) 是否生效")
    print("    4. 添加日志输出，追踪数据加载过程\n")

if __name__ == "__main__":
    analyze_descramble_logic()
    analyze_detail_loading()
    summarize_fixes()
