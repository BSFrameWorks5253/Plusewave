import ctypes
from ctypes import wintypes
import urllib.request
import json

advapi32 = ctypes.windll.advapi32
CRED_TYPE_GENERIC = 1

class CREDENTIAL(ctypes.Structure):
    _fields_ = [
        ('Flags', wintypes.DWORD),
        ('Type', wintypes.DWORD),
        ('TargetName', wintypes.LPWSTR),
        ('Comment', wintypes.LPWSTR),
        ('LastWritten', wintypes.FILETIME),
        ('CredentialBlobSize', wintypes.DWORD),
        ('CredentialBlob', ctypes.POINTER(ctypes.c_byte)),
        ('Persist', wintypes.DWORD),
        ('AttributeCount', wintypes.DWORD),
        ('Attributes', ctypes.c_void_p),
        ('TargetAlias', wintypes.LPWSTR),
        ('UserName', wintypes.LPWSTR),
    ]

target = 'git:https://github.com'
p_cred = ctypes.POINTER(CREDENTIAL)()
res = advapi32.CredReadW(target, CRED_TYPE_GENERIC, 0, ctypes.byref(p_cred))

token = None
if res:
    cred = p_cred.contents
    token = bytes(cred.CredentialBlob[:cred.CredentialBlobSize]).decode('utf-16le').strip()
    advapi32.CredFree(p_cred)

headers = {
    'Authorization': f'token {token}',
    'User-Agent': 'Mozilla/5.0',
    'Accept': 'application/vnd.github.v3+json'
}

url = 'https://api.github.com/repos/BSFrameWorks5253/Plusewave/actions/runs'
req = urllib.request.Request(url, headers=headers)
with urllib.request.urlopen(req) as resp:
    data = json.loads(resp.read().decode())
    print('Recent workflow runs:')
    for r in data.get('workflow_runs', [])[:5]:
        print(f"- Run #{r['run_number']}: {r['name']} | SHA: {r['head_sha'][:7]} | Status: {r['status']} | URL: {r['html_url']}")
