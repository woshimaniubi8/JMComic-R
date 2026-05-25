#!/usr/bin/env python3
"""
验证首页与详情页数据加载差异
"""

import requests
import json
import re
from datetime import datetime

# API 基础配置
BASE_URL = "https://www.cdnhth.club/"
HEADERS = {
    "User-Agent": "Mozilla/5.0 (Windows NT 10.0; Win64; x64) AppleWebKit/537.36"
}

def get_token_header(timestamp, secret):
    """生成 token header"""
    import hashlib
    token = hashlib.md5(f"{timestamp}{secret}".encode()).hexdigest()
    return token

def test_latest_api():
    """测试 /latest API (首页用)"""
    print("=== 测试 /latest API ===")
    timestamp = str(int(datetime.now().timestamp()))
    secret = "18comicAPP"
    token = get_token_header(timestamp, secret)
    
    headers = HEADERS.copy()
    headers.update({
        "tokenparam": f"{timestamp},2.0.21",
        "token": token,
        "version": "2.0.21"
    })
    
    try:
        response = requests.get(f"{BASE_URL}api/latest", headers=headers, params={"page": 0}, timeout=10)
        print(f"Status: {response.status_code}")
        if response.status_code == 200:
            data = response.json()
            print(f"Keys in response: {list(data.keys())}")
            if "data" in data:
                print(f"  data type: {type(data['data'])}")
                print(f"  data length: {len(data['data']) if isinstance(data['data'], (list, str)) else 'N/A'}")
                if isinstance(data['data'], str) and len(data['data']) < 200:
                    print(f"  data sample: {data['data']}")
    except Exception as e:
        print(f"Error: {e}")

def test_album_api(book_id="1441531"):
    """测试 /album API (详情页用)"""
    print(f"\n=== 测试 /album API (book_id={book_id}) ===")
    timestamp = str(int(datetime.now().timestamp()))
    secret = "18comicAPP"
    token = get_token_header(timestamp, secret)
    
    headers = HEADERS.copy()
    headers.update({
        "tokenparam": f"{timestamp},2.0.21",
        "token": token,
        "version": "2.0.21"
    })
    
    try:
        response = requests.get(f"{BASE_URL}api/album/{book_id}", headers=headers, timeout=10)
        print(f"Status: {response.status_code}")
        if response.status_code == 200:
            data = response.json()
            print(f"Response keys: {list(data.keys())}")
            
            # 检查 data 字段
            if "data" in data and isinstance(data['data'], str):
                # 通常 data 是加密的
                print(f"  data is encrypted string, length: {len(data['data'])}")
            elif "data" in data:
                # 尝试打印 JSON 结构
                import json
                if isinstance(data['data'], dict):
                    print(f"  Decrypted data keys: {list(data['data'].keys())}")
                    # 检查关键字段
                    for key in ['name', 'likes', 'total_views', 'description']:
                        if key in data['data']:
                            val = data['data'][key]
                            print(f"    {key}: {val} (type: {type(val).__name__})")
    except Exception as e:
        print(f"Error: {e}")

def test_chapter_api(eps_id="1441531"):
    """测试 /chapter API"""
    print(f"\n=== 测试 /chapter API (eps_id={eps_id}) ===")
    timestamp = str(int(datetime.now().timestamp()))
    secret = "18comicAPP"
    token = get_token_header(timestamp, secret)
    
    headers = HEADERS.copy()
    headers.update({
        "tokenparam": f"{timestamp},2.0.21",
        "token": token,
        "version": "2.0.21"
    })
    
    try:
        response = requests.get(f"{BASE_URL}api/chapter/{eps_id}", headers=headers, timeout=10)
        print(f"Status: {response.status_code}")
        if response.status_code == 200:
            data = response.json()
            print(f"Response keys: {list(data.keys())}")
            if "data" in data and isinstance(data['data'], str):
                print(f"  data is encrypted string, length: {len(data['data'])}")
    except Exception as e:
        print(f"Error: {e}")

def test_chapter_view_template(eps_id="1441531"):
    """测试 /chapter_view_template API"""
    print(f"\n=== 测试 /chapter_view_template API (eps_id={eps_id}) ===")
    timestamp = str(int(datetime.now().timestamp()))
    secret = "18comicAPPContent"  # 注意：使用不同的 secret
    token = get_token_header(timestamp, secret)
    
    headers = HEADERS.copy()
    headers.update({
        "tokenparam": f"{timestamp},2.0.21",
        "token": token,
        "version": "2.0.21"
    })
    
    try:
        response = requests.get(f"{BASE_URL}api/chapter_view_template", 
                               headers=headers, 
                               params={"chapter_id": eps_id}, 
                               timeout=10)
        print(f"Status: {response.status_code}")
        print(f"Content-Type: {response.headers.get('Content-Type', 'N/A')}")
        print(f"Response length: {len(response.text)}")
        
        # 尝试提取 scramble_id
        if "scramble_id" in response.text:
            match = re.search(r'var scramble_id = (\d+)', response.text)
            if match:
                print(f"  Extracted scramble_id: {match.group(1)}")
            else:
                print(f"  Contains 'scramble_id' but regex didn't match")
                # 打印前 500 字符
                print(f"  Sample: {response.text[:500]}")
        else:
            print(f"  No 'scramble_id' found in response")
            print(f"  Sample: {response.text[:200]}")
    except Exception as e:
        print(f"Error: {e}")

if __name__ == "__main__":
    print("API 测试脚本 - 验证首页与详情页数据格式\n")
    print("注意：这个脚本需要有效的网络连接和有效的 API token")
    print("如果测试失败，请检查网络连接和 API endpoint 状态\n")
    
    # 运行测试
    test_latest_api()
    test_album_api()
    test_chapter_api()
    test_chapter_view_template()
    
    print("\n=== 总结 ===")
    print("如果上面的测试失败，可能的原因：")
    print("1. 网络连接问题")
    print("2. API endpoint 更改")
    print("3. Token 签名有误")
    print("4. API 服务器返回 403/401（需要有效的认证）")
