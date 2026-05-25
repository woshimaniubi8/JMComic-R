import base64, hashlib, json, time, urllib.request, ssl
from Crypto.Cipher import AES

def md5hex(s): return hashlib.md5(s.encode('utf-8')).hexdigest()

BASE_URL = 'https://www.cdnhth.club'
HEADER_VER = '2.0.21'
APP_TOKEN_SECRET = '18comicAPP'
APP_DATA_SECRET = '185Hcomic3PAPP7R'

ts = int(time.time())
tokenparam = f'{ts},{HEADER_VER}'
token = md5hex(f'{ts}{APP_TOKEN_SECRET}')

headers = {
    'User-Agent': 'Mozilla/5.0 (Linux; Android 7.1.2; DT1901A Build/N2G47O; wv) AppleWebKit/537.36',
    'Accept': 'application/json, text/plain, */*',
    'tokenparam': tokenparam, 'token': token, 'version': HEADER_VER,
}

ctx = ssl.create_default_context()
ctx.check_hostname = False
ctx.verify_mode = ssl.CERT_NONE

for bid in ['1441531', '363859']:
    url = f'{BASE_URL}/album?id={bid}'
    req = urllib.request.Request(url, headers=headers)
    resp = urllib.request.urlopen(req, context=ctx, timeout=10)
    j = json.loads(resp.read())
    if j.get('code') == 200 and isinstance(j.get('data'), str):
        key_str = md5hex(f'{ts}{APP_DATA_SECRET}').encode()
        cipher_bytes = base64.b64decode(j['data'])
        cipher = AES.new(key_str, AES.MODE_ECB)
        decrypted = cipher.decrypt(cipher_bytes)
        pad_len = decrypted[-1]
        data = json.loads(decrypted[:-pad_len].decode())
        print(f'=== Book {bid} ===')
        for k, v in data.items():
            t = type(v).__name__
            if isinstance(v, list) and v:
                t += f'[{type(v[0]).__name__}]'
            v_str = str(v)[:120]
            print(f'  {k}: {t} = {v_str}')
        print()
