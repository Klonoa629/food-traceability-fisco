"""通过正式 API 创建独立演示数据；默认只打印计划，不发送请求。"""
import argparse
import hashlib
import json
import os
import re
import sys
from urllib.request import Request, urlopen
from urllib.parse import urlsplit


class Api:
    def __init__(self, base_url, run_id):
        self.base_url = base_url.rstrip('/')
        self.run_id = run_id

    def call(self, method, path, data=None, token=None, step=None, content_type=None):
        headers = {'Accept': 'application/json'}
        if token:
            headers['Authorization'] = 'Bearer ' + token
        if step:
            headers['Idempotency-Key'] = hashlib.sha256(
                f'demo:{self.run_id}:{step}'.encode()).hexdigest()
        if data is not None:
            headers['Content-Type'] = content_type or 'application/json'
            if not isinstance(data, bytes):
                data = json.dumps(data, ensure_ascii=False).encode('utf-8')
        request = Request(self.base_url + path, data=data, headers=headers, method=method)
        with urlopen(request, timeout=40) as response:
            result = json.load(response)
        if result.get('code') != 0:
            raise RuntimeError(f'{path}: code={result.get("code")} {result.get("message")}')
        return result.get('data')

    def login(self, username, password):
        return self.call('POST', '/api/auth/login', {'username': username, 'password': password})

    def report(self, token):
        # 明确为模拟数据，绝不冒充真实检测报告。
        content = f'演示报告 {self.run_id}：模拟检测数据，仅用于软件功能演示。'.encode('utf-8')
        boundary = 'foodtrace-demo-boundary'
        body = (f'--{boundary}\r\nContent-Disposition: form-data; name="file"; '
                'filename="demo-report.txt"\r\nContent-Type: text/plain\r\n\r\n').encode()
        body += content + f'\r\n--{boundary}--\r\n'.encode()
        info = self.call('POST', '/api/storage/upload', body, token,
                         content_type='multipart/form-data; boundary=' + boundary)
        if info['sha256'] != hashlib.sha256(content).hexdigest():
            raise RuntimeError('上传报告摘要不符')
        return 'file:sha256:' + info['sha256']


def seed(api, regulator_name, regulator_password, password):
    regulator = api.login(regulator_name, regulator_password)
    if not regulator['user']['regulator']:
        raise RuntimeError('当前账户不是监管，请先完成首次监管引导')
    admin = regulator['token']
    existing = {user['username']: user for user in api.call('GET', '/api/admin/users', token=admin)}
    actors = {}
    for role, name in enumerate(('基地', '加工厂', '质检', '物流', '仓库', '零售'), 1):
        username = f'demo_{api.run_id}_{role}'
        user = existing.get(username)
        if user is not None and (user['role'] != role or user['status'] == 2 or user['regulator']):
            raise RuntimeError(f'{username} 已被其他角色或已吊销账户占用，请换 run-id')
        user_id = user['id'] if user else api.call('POST', '/api/auth/register', {
            'username': username, 'password': password, 'orgName': f'演示{name}-{api.run_id}', 'role': role})
        api.call('POST', f'/api/admin/users/{user_id}/approve', {'role': role}, admin, f'approve-{role}')
        actors[role] = api.login(username, password)

    report = api.report(actors[3]['token'])

    def write(role, path, data, step):
        return api.call('POST', path, data, actors[role]['token'], step)

    def register(suffix):
        return write(1, '/api/products', {
            'name': '演示草莓-' + suffix, 'batchNo': f'DEMO-{api.run_id}-{suffix}',
            'description': '模拟数据，仅用于演示', 'location': '演示基地'}, 'register-' + suffix)

    def handover(product_id, current, target, prefix):
        write(current, f'/api/products/{product_id}/handover',
              {'nextHolder': actors[target]['user']['chainAddress']}, f'{prefix}-handover-{target}')

    sale = register('SALE')
    product_id = sale['id']
    for role in range(2, 7):
        handover(product_id, role - 1, role, 'sale')
        if role == 3:
            write(role, f'/api/products/{product_id}/inspect',
                  {'qualified': True, 'reportHash': report}, 'sale-inspect')
        else:
            write(role, f'/api/products/{product_id}/records', {
                'stage': role - 1, 'description': '演示流转记录', 'location': '演示机构'}, f'sale-record-{role}')

    failed = register('FAILED')
    handover(failed['id'], 1, 3, 'failed')
    write(3, f'/api/products/{failed["id"]}/inspect',
          {'qualified': False, 'reportHash': report}, 'failed-inspect')

    recalled = register('RECALL')
    api.call('POST', f'/api/admin/products/{recalled["id"]}/recall',
             {'reason': '模拟监管召回'}, admin, 'regulator-recall')
    outcomes = []
    for product, stage in ((sale, 5), (failed, 6), (recalled, 6)):
        actual = api.call('GET', f'/api/products/{product["id"]}', token=admin)
        if actual['stage'] != stage or actual['recalled'] != (stage == 6):
            raise RuntimeError('演示产品终态不符合预期：' + actual['batchNo'])
        outcomes.append({'id': actual['id'], 'batchNo': actual['batchNo'], 'stage': actual['stage']})
    return outcomes


def main():
    parser = argparse.ArgumentParser(description=__doc__)
    parser.add_argument('--base-url', default='http://localhost:8080')
    parser.add_argument('--run-id', required=True, help='1–12 位字母、数字或下划线；重试保持不变')
    parser.add_argument('--apply', action='store_true', help='实际创建账号、发放角色并写链')
    args = parser.parse_args()
    if not re.fullmatch(r'[A-Za-z0-9_]{1,12}', args.run_id):
        parser.error('run-id 格式不正确')
    url = urlsplit(args.base_url)
    if url.scheme not in ('http', 'https') or not url.netloc or url.username or url.query or url.fragment:
        parser.error('base-url 必须是不含凭据、查询串和片段的 HTTP(S) 地址')
    if not args.apply:
        print(f'计划：在 {args.base_url} 创建 demo_{args.run_id}_1 至 _6 六个机构，'
              f'建立 DEMO-{args.run_id}-SALE / FAILED / RECALL 三条演示链路。未发送请求。')
        return
    names = ('DEMO_REGULATOR_USERNAME', 'DEMO_REGULATOR_PASSWORD', 'DEMO_PASSWORD')
    if any(not os.environ.get(name) for name in names):
        parser.error('请设置 ' + ', '.join(names))
    password = os.environ['DEMO_PASSWORD']
    if not (8 <= len(password) <= 64 and re.search('[A-Za-z]', password) and re.search('[0-9]', password)):
        parser.error('DEMO_PASSWORD 须为 8–64 位并同时含字母和数字')
    result = seed(Api(args.base_url, args.run_id), *(os.environ[name] for name in names))
    print(json.dumps({'run_id': args.run_id, 'products': result}, ensure_ascii=False, indent=2))


if __name__ == '__main__':
    try:
        main()
    except Exception as error:
        print(f'演示中止：{error}\n网络中断或 503 时保留原 run-id 与参数重试，不要换标识重复写链。', file=sys.stderr)
        sys.exit(1)
