import urllib.request
import json
import time

url = 'https://api.github.com/repos/BSFrameWorks5253/Plusewave/actions/jobs/125098789728'
req = urllib.request.Request(url, headers={'User-Agent': 'PulseWave-Monitor/1.0'})

try:
    with urllib.request.urlopen(req) as resp:
        data = json.loads(resp.read().decode())
        print(f"Status: {data.get('status')}, Conclusion: {data.get('conclusion')}")
        for s in data.get('steps', []):
            print(f"Step {s['number']:2d}: {s['name']:40s} | {s['status']:12s} | {str(s.get('conclusion'))}")
except Exception as e:
    print("Error:", e)
