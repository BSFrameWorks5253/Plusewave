import urllib.request, json
from check_new_runs import headers

run_id = '40348082352'
url = f'https://api.github.com/repos/BSFrameWorks5253/Plusewave/actions/runs/{run_id}'
req = urllib.request.Request(url, headers=headers)
with urllib.request.urlopen(req) as resp:
    data = json.loads(resp.read().decode())
    print(f"Run #{data['run_number']}: {data['name']} | Status: {data['status']} | Conclusion: {data['conclusion']}")

jobs_url = f'https://api.github.com/repos/BSFrameWorks5253/Plusewave/actions/runs/{run_id}/jobs'
req2 = urllib.request.Request(jobs_url, headers=headers)
with urllib.request.urlopen(req2) as resp2:
    jobs_data = json.loads(resp2.read().decode())
    for j in jobs_data.get('jobs', []):
        print(f"  Job: {j['name']} | Status: {j['status']} | Conclusion: {j['conclusion']}")
        for s in j.get('steps', []):
            if s['status'] != 'pending':
                print(f"    - [{s['status']}] {s['name']}: {s['conclusion']}")
