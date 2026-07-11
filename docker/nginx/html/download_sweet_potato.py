#!/usr/bin/env python3
import requests, re, json, os

SAVE_DIR = os.path.join(os.path.dirname(os.path.abspath(__file__)), 'images')
headers = {
    'User-Agent': 'Mozilla/5.0 (Windows NT 10.0; Win64; x64) AppleWebKit/537.36',
    'Referer': 'https://image.baidu.com/'
}
url = 'https://image.baidu.com/search/acjson'
params = {
    'tn': 'resultjson_com',
    'word': '地瓜 红薯',
    'queryWord': '地瓜 红薯',
    'cl': 2,
    'lm': -1,
    'ie': 'utf-8',
    'oe': 'utf-8',
    'pn': 0,
    'rn': 10
}
resp = requests.get(url, params=params, headers=headers, timeout=10)
text = re.sub(r'[\x00-\x1f\x7f]', '', resp.text)
data = json.loads(text)
for item in data.get('data', []):
    if not item:
        continue
    img_url = item.get('thumbURL') or item.get('middleURL')
    if img_url and img_url.startswith('http'):
        r = requests.get(img_url, headers=headers, timeout=15)
        if r.status_code == 200 and len(r.content) > 3000:
            filepath = os.path.join(SAVE_DIR, 'product_28.jpg')
            with open(filepath, 'wb') as f:
                f.write(r.content)
            print(f'成功: {len(r.content)} bytes -> {filepath}')
            break