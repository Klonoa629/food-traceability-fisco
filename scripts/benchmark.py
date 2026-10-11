"""只读 JSON API 延迟测量；结果写到标准输出，不修改服务或配置。"""
import argparse
from collections import Counter
from concurrent.futures import ThreadPoolExecutor
from datetime import datetime, timezone
import json
import math
import os
import time
from urllib.error import HTTPError
from urllib.request import Request, urlopen
from urllib.parse import urlsplit


def sample(url, timeout, token):
    start = time.perf_counter()
    headers = {'Accept': 'application/json'}
    if token:
        headers['Authorization'] = 'Bearer ' + token
    try:
        with urlopen(Request(url, headers=headers, method='GET'), timeout=timeout) as response:
            data = json.load(response)
        # 本项目业务错误仍可能使用 HTTP 200，必须同时检查业务码。
        if not isinstance(data, dict):
            code = 'invalid_response'
        elif 'code' in data:
            code = 'ok' if data['code'] == 0 else f'business_{data["code"]}'
        else:
            code = 'ok' if data.get('status') == 'UP' else 'invalid_response'
    except HTTPError as error:
        code = f'http_{error.code}'
    except Exception as error:
        code = type(error).__name__
    return {'ms': (time.perf_counter() - start) * 1000, 'result': code}


def summarize(samples, elapsed):
    latencies = sorted(item['ms'] for item in samples if item['result'] == 'ok')
    def percentile(p):
        return round(latencies[max(0, math.ceil(len(latencies) * p) - 1)], 3) if latencies else None
    return {
        'requests': len(samples), 'successes': len(latencies),
        'failures': dict(Counter(item['result'] for item in samples if item['result'] != 'ok')),
        'elapsed_seconds': round(elapsed, 3),
        'success_requests_per_second': round(len(latencies) / elapsed, 3) if elapsed else 0,
        'success_latency_ms': {'p50': percentile(.5), 'p95': percentile(.95), 'p99': percentile(.99)},
    }


def main():
    parser = argparse.ArgumentParser(description=__doc__)
    parser.add_argument('--url', required=True, help='完整的只读 JSON API 地址')
    parser.add_argument('--requests', type=int, default=20)
    parser.add_argument('--concurrency', type=int, default=1)
    parser.add_argument('--warmup', type=int, default=1)
    parser.add_argument('--timeout', type=float, default=10)
    args = parser.parse_args()
    url = urlsplit(args.url)
    if url.scheme not in ('http', 'https') or not url.netloc or url.username or url.fragment:
        parser.error('仅支持不含凭据和片段的 HTTP(S) 地址')
    if not (1 <= args.requests <= 10000 and 1 <= args.concurrency <= 32 and 0 <= args.warmup <= 100
            and 0 < args.timeout <= 120):
        parser.error('requests 1–10000，concurrency 1–32，warmup 0–100，timeout (0,120]')
    token = os.environ.get('BENCHMARK_TOKEN')
    for _ in range(args.warmup):
        result = sample(args.url, args.timeout, token)
        if result['result'] != 'ok':
            parser.error('预热失败：' + result['result'])
    start = time.perf_counter()
    with ThreadPoolExecutor(max_workers=args.concurrency) as pool:
        samples = list(pool.map(lambda _: sample(args.url, args.timeout, token), range(args.requests)))
    report = summarize(samples, time.perf_counter() - start)
    report.update({'measured_at_utc': datetime.now(timezone.utc).isoformat(), 'url': args.url,
                   'concurrency': args.concurrency, 'warmup': args.warmup})
    print(json.dumps(report, ensure_ascii=False, indent=2))
    return 1 if report['failures'] else 0


if __name__ == '__main__':
    raise SystemExit(main())
