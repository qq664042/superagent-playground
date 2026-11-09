'use strict';

const test = require('node:test');
const assert = require('node:assert/strict');

const {
  CalcError,
  evaluate,
  formatNumber,
} = require('../src/evaluator');

function assertCalcError(fn, messagePattern) {
  assert.throws(fn, (error) => {
    assert.ok(error instanceof CalcError, `应抛出 CalcError，实际抛出 ${error}`);
    assert.match(error.message, messagePattern);
    return true;
  });
}

test('运算符优先级与结合性', () => {
  assert.equal(evaluate('1+2*3'), 7);
  assert.equal(evaluate('2+3*4-5'), 9);
  assert.equal(evaluate('10-4/2'), 8);
  assert.equal(evaluate('2*3+4*5'), 26);
  assert.equal(evaluate('10-3-2'), 5, '减法应从左到右结合');
  assert.equal(evaluate('8/4/2'), 1, '除法应从左到右结合');
});

test('小括号改变运算顺序', () => {
  assert.equal(evaluate('(1+2)*3'), 9);
  assert.equal(evaluate('2*(3+(4-1))'), 12);
  assert.equal(evaluate('((1+2)*(3+4))'), 21);
  assert.equal(evaluate('(8-2)/(1+2)'), 2);
  assert.equal(evaluate('((((5))))'), 5);
});

test('一元正负号', () => {
  assert.equal(evaluate('-3+2'), -1);
  assert.equal(evaluate('2*-3'), -6);
  assert.equal(evaluate('-(1+2)'), -3);
  assert.equal(evaluate('3--2'), 5);
  assert.equal(evaluate('+4-+1'), 3);
  assert.equal(evaluate('(-2)*(-3)'), 6);
  assert.equal(evaluate('--2'), 2);
});

test('小数与空白字符', () => {
  assert.equal(evaluate('1.5+2.25'), 3.75);
  assert.equal(evaluate('.5+.5'), 1);
  assert.equal(evaluate(' 1 + 2 * 3 '), 7);
  assert.equal(evaluate('\t(1 + 2)\n'), 3);
});

test('除法与除零错误', () => {
  assert.equal(evaluate('7/2'), 3.5);
  assert.equal(evaluate('6/3'), 2);
  assertCalcError(() => evaluate('1/0'), /^除数不能为 0$/);
  assertCalcError(() => evaluate('(1+2)/(3-3)'), /^除数不能为 0$/);
  assertCalcError(() => evaluate('2/-0'), /^除数不能为 0$/);
});

test('非法表达式给出清晰的中文错误', () => {
  const cases = [
    ['', /表达式不能为空/],
    ['   ', /表达式不能为空/],
    ['1+', /表达式不完整/],
    ['*2', /运算符“\*”前缺少数字或括号/],
    ['1+*2', /运算符“\*”前缺少数字或括号/],
    ['(1+2', /缺少右括号/],
    ['(', /缺少右括号/],
    ['1+2)', /多余的右括号/],
    ['()', /括号内缺少表达式/],
    ['(1+)', /括号内缺少表达式/],
    ['1 2', /缺少运算符/],
    ['2(3+4)', /缺少运算符/],
    ['1..2', /数字格式错误/],
    ['2&3', /非法字符：“&”/],
    ['hello', /非法字符：“h”/],
  ];

  for (const [expression, pattern] of cases) {
    test(`非法表达式 ${JSON.stringify(expression)}`, () => {
      assertCalcError(() => evaluate(expression), pattern);
    });
  }
});

test('结果格式化', () => {
  assert.equal(formatNumber(7), '7');
  assert.equal(formatNumber(-3.5), '-3.5');
  assert.equal(formatNumber(999999999999999), '999999999999999', '安全整数不应被舍入');
  assert.equal(formatNumber(0.1 + 0.2), '0.3', '应消除二进制浮点尾差');
  assert.equal(formatNumber(0.1 + 0.7), '0.8', '应消除二进制浮点尾差');
  assert.equal(formatNumber(-0), '0');
  assert.equal(formatNumber(1 / 3), '0.333333333333333');
});

test('结果超出可表示范围时报错', () => {
  assertCalcError(() => evaluate('9'.repeat(400)), /超出可表示范围/);
});
