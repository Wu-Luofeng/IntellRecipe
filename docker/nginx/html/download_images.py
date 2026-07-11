#!/usr/bin/env python3
"""
从百度图片搜索下载食材、商家、商品图片
"""
import os
import re
import time
import requests
import json

SAVE_DIR = os.path.join(os.path.dirname(os.path.abspath(__file__)), 'images')
os.makedirs(SAVE_DIR, exist_ok=True)

# 食材图片映射 (id -> 搜索关键词)
INGREDIENT_MAP = {
    1: "大米 食材",
    2: "小麦粉 面粉",
    3: "玉米 新鲜",
    4: "燕麦 燕麦片",
    5: "红薯",
    6: "土豆 马铃薯",
    7: "鸡胸肉 生",
    8: "猪肉 生鲜",
    9: "牛肉 生鲜",
    10: "羊肉 生鲜",
    11: "排骨 生鲜",
    12: "虾仁 生鲜",
    13: "鲤鱼 生鲜",
    14: "带鱼 海鲜",
    15: "鲫鱼 生鲜",
    16: "海带 鲜",
    17: "鸡蛋 食材",
    18: "鸭蛋 食材",
    19: "牛奶 纯牛奶",
    20: "酸奶",
    21: "奶酪 芝士",
    22: "黄豆 干豆",
    23: "绿豆 干豆",
    24: "红豆 干豆",
    25: "豆腐 嫩豆腐",
    26: "豆浆",
    27: "白菜 大白菜",
    28: "菠菜 蔬菜",
    29: "芹菜 蔬菜",
    30: "西红柿 番茄",
    31: "黄瓜 蔬菜",
    32: "胡萝卜 蔬菜",
    33: "洋葱 紫皮",
    34: "大蒜 蒜头",
    35: "生姜 老姜",
    36: "青椒 青辣椒",
    37: "茄子 蔬菜",
    38: "西兰花 蔬菜",
    39: "花菜 菜花",
    40: "蘑菇 鲜蘑菇",
    41: "木耳 水发",
    42: "苹果 水果",
    43: "香蕉 水果",
    44: "橙子 水果",
    45: "西瓜 水果",
    46: "葡萄 水果",
    47: "草莓 水果",
    48: "柠檬 水果",
    49: "花生 生花生",
    50: "核桃 干核桃",
}

# 商家图片映射 (id -> 搜索关键词)
MERCHANT_MAP = {
    1: "水果店 门面",
    2: "超市 杂货店门面",
    3: "蔬菜摊 菜市场",
    4: "肉铺 肉店",
    5: "海鲜店",
    6: "烘焙坊 面包店",
}

# 商品图片映射 (id -> 搜索关键词)
PRODUCT_MAP = {
    1: "红富士苹果",
    2: "香蕉 水果",
    3: "脐橙 橙子",
    4: "西瓜 水果",
    5: "阳光玫瑰葡萄",
    6: "草莓 水果",
    7: "柠檬 水果",
    8: "五常大米",
    9: "小麦粉 面粉",
    10: "燕麦片",
    11: "鸡蛋 鲜鸡蛋",
    12: "纯牛奶 箱装",
    13: "酸奶 杯装",
    14: "黄豆 袋装",
    15: "绿豆 袋装",
    16: "红豆 袋装",
    17: "大白菜 蔬菜",
    18: "菠菜 蔬菜",
    19: "芹菜 蔬菜",
    20: "西红柿 番茄",
    21: "黄瓜 蔬菜",
    22: "胡萝卜 蔬菜",
    23: "紫皮洋葱",
    24: "青椒 蔬菜",
    25: "长茄子",
    26: "西兰花 蔬菜",
    27: "土豆 马铃薯",
    28: "红薯 蔬菜",
    29: "鲜蘑菇",
    30: "水发木耳",
    31: "鸡胸肉 生鲜",
    32: "猪瘦肉 生鲜",
    33: "牛腱子肉 生鲜",
    34: "羊腿肉 生鲜",
    35: "猪排骨 生鲜",
    36: "北豆腐",
    37: "冷冻虾仁",
    38: "鲜活鲤鱼",
    39: "冰鲜带鱼",
    40: "鲜活鲫鱼",
    41: "鲜海带",
    42: "戚风蛋糕",
    43: "全麦吐司面包",
    44: "黄油曲奇饼干",
    45: "马苏里拉奶酪",
    46: "新鲜玉米",
}

HEADERS = {
    'User-Agent': 'Mozilla/5.0 (Windows NT 10.0; Win64; x64) AppleWebKit/537.36 (KHTML, like Gecko) Chrome/120.0.0.0 Safari/537.36',
    'Accept': 'text/html,application/xhtml+xml,application/xml;q=0.9,image/webp,*/*;q=0.8',
    'Accept-Language': 'zh-CN,zh;q=0.9,en;q=0.8',
    'Referer': 'https://image.baidu.com/',
}


def baidu_image_search(keyword, max_try=5):
    """从百度图片搜索获取图片URL"""
    url = "https://image.baidu.com/search/acjson"
    params = {
        'tn': 'resultjson_com',
        'logid': '1234567890',
        'ipn': 'rj',
        'ct': 201326592,
        'is': '',
        'fp': 'result',
        'fr': '',
        'word': keyword,
        'queryWord': keyword,
        'cl': 2,
        'lm': -1,
        'ie': 'utf-8',
        'oe': 'utf-8',
        'adpicid': '',
        'st': -1,
        'z': '',
        'ic': '',
        'hd': '',
        'latest': '',
        'copyright': '',
        'se': '',
        'tab': '',
        'width': '',
        'height': '',
        'face': 0,
        'istype': 2,
        'qc': '',
        'nc': 1,
        'expermode': '',
        'isAcoustic': '',
        'nojc': '',
        'isAsync': '',
        'pn': 0,
        'rn': 10,
        'gsm': '1e',
    }
    try:
        resp = requests.get(url, params=params, headers=HEADERS, timeout=10)
        resp.encoding = 'utf-8'
        # 清理控制字符，避免JSON解析失败
        text = resp.text
        text = re.sub(r'[\x00-\x1f\x7f]', '', text)
        data = json.loads(text)
        if 'data' in data and data['data']:
            for item in data['data']:
                if not item:
                    continue
                # 优先选择 thumbURL 或 middleURL
                img_url = item.get('thumbURL') or item.get('middleURL') or item.get('objURL')
                if img_url and img_url.startswith('http'):
                    return img_url
    except Exception as e:
        print(f"  搜索失败: {e}")
    return None


def download_image(url, filepath, timeout=15):
    """下载图片到文件"""
    try:
        resp = requests.get(url, headers=HEADERS, timeout=timeout, stream=True)
        if resp.status_code == 200:
            content_type = resp.headers.get('Content-Type', '')
            # 检查是否为图片
            if 'image' in content_type or len(resp.content) > 5000:
                with open(filepath, 'wb') as f:
                    f.write(resp.content)
                # 验证文件大小
                if os.path.getsize(filepath) > 3000:
                    return True
                else:
                    os.remove(filepath)
                    return False
    except Exception as e:
        print(f"  下载失败: {e}")
    return False


def process_images(name_map, prefix):
    """批量下载图片"""
    results = {}
    for item_id, keyword in name_map.items():
        filename = f"{prefix}_{item_id}.jpg"
        filepath = os.path.join(SAVE_DIR, filename)
        
        # 如果已存在且大小合理，跳过
        if os.path.exists(filepath) and os.path.getsize(filepath) > 3000:
            print(f"  [{prefix}_{item_id}] 已存在，跳过")
            results[item_id] = f"/images/{filename}"
            continue
        
        print(f"  [{prefix}_{item_id}] 搜索: {keyword}")
        img_url = baidu_image_search(keyword)
        if img_url:
            print(f"    下载: {img_url[:80]}...")
            if download_image(img_url, filepath):
                results[item_id] = f"/images/{filename}"
                print(f"    成功! ({os.path.getsize(filepath)} bytes)")
            else:
                print(f"    下载失败，尝试下一个搜索结果")
                # 重试一次
                img_url2 = baidu_image_search(keyword + " 图片")
                if img_url2 and img_url2 != img_url:
                    if download_image(img_url2, filepath):
                        results[item_id] = f"/images/{filename}"
                        print(f"    重试成功! ({os.path.getsize(filepath)} bytes)")
                        continue
                print(f"    最终失败")
        else:
            print(f"    未找到图片URL")
        
        time.sleep(0.5)  # 避免请求过快
    
    return results


def main():
    print("=" * 60)
    print("开始下载食材图片...")
    print("=" * 60)
    ingredient_results = process_images(INGREDIENT_MAP, "ingredient")
    
    print("\n" + "=" * 60)
    print("开始下载商家图片...")
    print("=" * 60)
    merchant_results = process_images(MERCHANT_MAP, "merchant")
    
    print("\n" + "=" * 60)
    print("开始下载商品图片...")
    print("=" * 60)
    product_results = process_images(PRODUCT_MAP, "product")
    
    # 生成SQL更新语句
    print("\n" + "=" * 60)
    print("生成SQL更新语句...")
    print("=" * 60)
    sql_lines = ["-- 更新食材图片", "SET NAMES utf8mb4;"]
    for item_id, img_path in ingredient_results.items():
        sql_lines.append(f"UPDATE ingredient SET image = '{img_path}' WHERE id = {item_id};")
    
    sql_lines.append("\n-- 更新商家图片")
    for item_id, img_path in merchant_results.items():
        sql_lines.append(f"UPDATE merchant SET image = '{img_path}' WHERE id = {item_id};")
    
    sql_lines.append("\n-- 更新商品图片")
    for item_id, img_path in product_results.items():
        sql_lines.append(f"UPDATE product SET image = '{img_path}' WHERE id = {item_id};")
    
    sql_file = os.path.join(SAVE_DIR, 'update_images.sql')
    with open(sql_file, 'w', encoding='utf-8') as f:
        f.write('\n'.join(sql_lines))
    
    print(f"\nSQL文件已生成: {sql_file}")
    print(f"成功下载: 食材 {len(ingredient_results)}/50, 商家 {len(merchant_results)}/6, 商品 {len(product_results)}/46")
    
    # 输出结果JSON
    result_json = {
        'ingredients': ingredient_results,
        'merchants': merchant_results,
        'products': product_results,
    }
    json_file = os.path.join(SAVE_DIR, 'image_results.json')
    with open(json_file, 'w', encoding='utf-8') as f:
        json.dump(result_json, f, ensure_ascii=False, indent=2)
    print(f"结果JSON已生成: {json_file}")


if __name__ == '__main__':
    main()