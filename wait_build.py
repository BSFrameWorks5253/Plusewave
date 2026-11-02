import urllib.request
import json
import time

url = 'https://api.github.com/repos/BSFrameWorks5253/Plusewave/actions/jobs/125098789728'

for i in range(12):
    time.sleep(10)
    try:
        req = urllib.request.Request(url, headers={'User-Agent': 'PulseWave-Monitor/1.0'})
        with urllib.request.urlopen(req) as resp:
            data = json.loads(resp.read().decode())
            status = data.get('status')
            conclusion = data.get('conclusion')
            print(f"[{i*10+10}s] Job status: {status}, conclusion: {conclusion}")
            if status == 'completed':
                for s in data.get('steps', []):
                    print(f"  Step {s['number']:2d}: {s['name']:40s} | {s['status']:10s} | {str(s.get('conclusion'))}")
                break
    except Exception as e:
        print("Error checking:", e)
