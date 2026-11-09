'use strict';

const test = require('node:test');
const assert = require('node:assert/strict');
const path = require('node:path');
const { spawnSync } = require('node:child_process');

const BIN = path.join(__dirname, '..', 'bin', 'calc.js');

function run(args, input) {
  return spawnSync(process.execPath, [BIN, ...args], {
    encoding: 'utf8',
    input,
    timeout: 5000,
  });
}

test('命令行参数：一次性求值', () => {
  const result = run(['1+2*3']);

  assert.equal(result.status, 0);
  assert.equal(result.stdout, '7\n');
  assert.equal(result.stderr, '');
});

test('命令行参数：支持括号、空格和拆分传入的参数', () => {
  assert.equal(run(['(1 + 2) * 3']).stdout, '9\n');
  assert.equal(run(['1', '+', '2']).stdout, '3\n');
});

test('命令行参数：除零时输出中文错误并以非零码退出', () => {
  const result = run(['1/0']);

  assert.equal(result.status, 1);
  assert.equal(result.stdout, '');
  assert.match(result.stderr, /^错误：除数不能为 0\n$/);
});

test('命令行参数：非法表达式输出中文错误并以非零码退出', () => {
  const result = run(['1+']);

  assert.equal(result.status, 1);
  assert.equal(result.stdout, '');
  assert.match(result.stderr, /^错误：表达式不完整.*\n$/);
});

test('--help 输出用法并以 0 退出', () => {
  const result = run(['--help']);

  assert.equal(result.status, 0);
  assert.match(result.stdout, /用法/);
  assert.match(result.stdout, /calc/);
});

test('无参数时进入 REPL，可连续求值且出错后继续', () => {
  const result = run([], '1+2\n(1+2)*3\n1/0\n@\nexit\n');

  assert.equal(result.status, 0);
  assert.equal(result.stdout, '3\n9\n');
  assert.match(result.stderr, /错误：除数不能为 0/);
  assert.match(result.stderr, /错误：表达式中包含非法字符：“@”/);
});

test('REPL 支持 help 命令且空行不产生输出', () => {
  const result = run([], '\nhelp\nexit\n');

  assert.equal(result.status, 0);
  assert.equal(result.stdout.startsWith('\n') || result.stdout.startsWith('简易'), true);
  assert.match(result.stdout, /用法/);
});
