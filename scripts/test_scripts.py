"""脚本离线回归，不访问真实链、数据库或存储。"""
import io
import json
import unittest
from unittest.mock import patch

import benchmark
import demo


class ScriptTest(unittest.TestCase):
    def test_benchmark_excludes_errors_from_latency(self):
        result = benchmark.summarize([
            {'ms': 10, 'result': 'ok'}, {'ms': 20, 'result': 'ok'},
            {'ms': 1, 'result': 'business_429'}], 1)
        self.assertEqual(result['successes'], 2)
        self.assertEqual(result['failures'], {'business_429': 1})
        self.assertEqual(result['success_latency_ms']['p95'], 20)

    def test_all_errors_have_no_success_percentile(self):
        result = benchmark.summarize([{'ms': 1, 'result': 'http_500'}], 1)
        self.assertIsNone(result['success_latency_ms']['p99'])

    def test_business_error_with_http_200_is_not_success(self):
        with patch('benchmark.urlopen', return_value=io.BytesIO(b'{"code":429}')):
            self.assertEqual(benchmark.sample('http://localhost/api', 1, None)['result'], 'business_429')

    def test_demo_dry_run_does_not_access_network(self):
        with patch('sys.argv', ['demo.py', '--run-id', 'offline']), patch('demo.urlopen') as request:
            with patch('sys.stdout', new=io.StringIO()) as output:
                demo.main()
                self.assertIn('未发送请求', output.getvalue())
            request.assert_not_called()

    def test_demo_reuses_key_but_different_runs_are_isolated(self):
        keys = []
        def response(request, **kwargs):
            keys.append(request.get_header('Idempotency-key'))
            return io.BytesIO(b'{"code":0,"data":1}')
        with patch('demo.urlopen', side_effect=response):
            api = demo.Api('http://localhost', 'run1')
            api.call('POST', '/api/products', {}, step='register')
            api.call('POST', '/api/products', {}, step='register')
            demo.Api('http://localhost', 'run2').call('POST', '/api/products', {}, step='register')
        self.assertEqual(keys[0], keys[1])
        self.assertNotEqual(keys[0], keys[2])

    def test_demo_stops_on_pending_business_response(self):
        with patch('demo.urlopen', return_value=io.BytesIO(b'{"code":503,"message":"pending"}')):
            with self.assertRaisesRegex(RuntimeError, '503'):
                demo.Api('http://localhost', 'run1').call('POST', '/api/products', {})

    def test_demo_workflow_creates_three_terminal_scenarios(self):
        class FakeApi:
            run_id = 'offline'
            def __init__(self):
                self.users = []
                self.products = {}
                self.steps = {}

            def login(self, name, password):
                return {'token': name, 'user': {'regulator': name == 'regulator', 'chainAddress': name}}

            def report(self, token):
                return 'file:sha256:' + 'a' * 64

            def call(self, method, path, data=None, token=None, step=None):
                if step in self.steps:
                    return self.steps[step]
                if path == '/api/admin/users':
                    return self.users
                if path == '/api/auth/register':
                    user = {**data, 'id': len(self.users) + 1, 'status': 0, 'regulator': False}
                    self.users.append(user)
                    return user['id']
                if path.endswith('/approve'):
                    result = None
                elif path == '/api/products':
                    result = {**data, 'id': len(self.products) + 1, 'stage': 0, 'recalled': False}
                    self.products[result['id']] = dict(result)
                else:
                    product_id = int(path.split('/')[4 if '/admin/' in path else 3])
                    product = self.products[product_id]
                    if method == 'GET':
                        return product
                    if path.endswith('/records'):
                        product['stage'] = data['stage']
                    elif path.endswith('/inspect'):
                        product['stage'] = 2 if data['qualified'] else 6
                        product['recalled'] = not data['qualified']
                    elif path.endswith('/recall'):
                        product.update(stage=6, recalled=True)
                    result = dict(product)
                if step:
                    self.steps[step] = result
                return result

        api = FakeApi()
        first = demo.seed(api, 'regulator', 'password123', 'password123')
        self.assertEqual([row['stage'] for row in first], [5, 6, 6])
        self.assertEqual(demo.seed(api, 'regulator', 'password123', 'password123'), first)
        self.assertEqual(len(api.products), 3)
        self.assertEqual(len(api.users), 6)


if __name__ == '__main__':
    unittest.main()
