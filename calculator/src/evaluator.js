'use strict';

/**
 * 简易四则运算求值器。
 *
 * 支持 + - * /、小括号、一元正负号、整数与小数。
 * 使用递归下降解析，优先级与结合性遵循常规数学规则：
 *   表达式 -> 项 (('+' | '-') 项)*
 *   项     -> 一元 (('*' | '/') 一元)*
 *   一元   -> ('+' | '-') 一元 | 基本
 *   基本   -> 数字 | '(' 表达式 ')'
 */

class CalcError extends Error {
  constructor(message) {
    super(message);
    this.name = 'CalcError';
  }
}

// 数字：12、3.14、.5；不接受指数形式或结尾带小数点的写法。
const NUMBER_PATTERN = /^(?:\d+(?:\.\d+)?|\.\d+)/;

const SPACE_PATTERN = /\s/;

/**
 * 将输入字符串切分为记号（token）。
 * @param {string} input
 * @returns {Array<{type: string, value: string|number, position: number}>}
 */
function tokenize(input) {
  const tokens = [];
  let index = 0;

  while (index < input.length) {
    const char = input[index];

    if (SPACE_PATTERN.test(char)) {
      index += 1;
      continue;
    }

    if (char === '+' || char === '-' || char === '*' || char === '/') {
      tokens.push({ type: 'operator', value: char, position: index });
      index += 1;
      continue;
    }

    if (char === '(') {
      tokens.push({ type: 'lparen', value: char, position: index });
      index += 1;
      continue;
    }

    if (char === ')') {
      tokens.push({ type: 'rparen', value: char, position: index });
      index += 1;
      continue;
    }

    if (char === '.' || (char >= '0' && char <= '9')) {
      const match = NUMBER_PATTERN.exec(input.slice(index));
      if (!match) {
        throw new CalcError(`数字格式错误：位置 ${index + 1} 附近的“${char}”`);
      }
      tokens.push({
        type: 'number',
        value: Number(match[0]),
        raw: match[0],
        position: index,
      });
      index += match[0].length;
      continue;
    }

    throw new CalcError(`表达式中包含非法字符：“${char}”（位置 ${index + 1}）`);
  }

  return tokens;
}

class Parser {
  constructor(tokens) {
    this.tokens = tokens;
    this.index = 0;
  }

  peek() {
    return this.tokens[this.index] ?? null;
  }

  consume() {
    const token = this.peek();
    if (token) {
      this.index += 1;
    }
    return token;
  }

  parseExpression() {
    let value = this.parseTerm();

    for (;;) {
      const token = this.peek();
      if (
        !token ||
        token.type !== 'operator' ||
        (token.value !== '+' && token.value !== '-')
      ) {
        break;
      }
      this.consume();
      const right = this.parseTerm();
      value = token.value === '+' ? value + right : value - right;
    }

    return value;
  }

  parseTerm() {
    let value = this.parseUnary();

    for (;;) {
      const token = this.peek();
      if (
        !token ||
        token.type !== 'operator' ||
        (token.value !== '*' && token.value !== '/')
      ) {
        break;
      }
      this.consume();
      const right = this.parseUnary();

      if (token.value === '*') {
        value *= right;
      } else {
        if (right === 0) {
          throw new CalcError('除数不能为 0');
        }
        value /= right;
      }
    }

    return value;
  }

  parseUnary() {
    const token = this.peek();
    if (
      token &&
      token.type === 'operator' &&
      (token.value === '+' || token.value === '-')
    ) {
      this.consume();
      const value = this.parseUnary();
      return token.value === '-' ? -value : value;
    }
    return this.parsePrimary();
  }

  parsePrimary() {
    const token = this.peek();

    if (!token) {
      throw new CalcError('表达式不完整：缺少数字或括号');
    }

    if (token.type === 'number') {
      this.consume();
      return token.value;
    }

    if (token.type === 'lparen') {
      this.consume();
      const next = this.peek();

      if (!next) {
        throw new CalcError('括号不匹配：缺少右括号“)”');
      }
      if (next.type === 'rparen') {
        throw new CalcError('表达式不完整：括号内缺少表达式');
      }

      const value = this.parseExpression();
      const closing = this.peek();

      if (!closing) {
        throw new CalcError('括号不匹配：缺少右括号“)”');
      }
      if (closing.type !== 'rparen') {
        throw new CalcError(
          `表达式语法错误：位置 ${closing.position + 1} 处缺少右括号“)”或运算符`
        );
      }
      this.consume();
      return value;
    }

    if (token.type === 'rparen') {
      throw new CalcError('表达式不完整：括号内缺少表达式');
    }

    throw new CalcError(`运算符“${token.value}”前缺少数字或括号`);
  }

  expectEnd() {
    const token = this.peek();
    if (!token) {
      return;
    }

    if (token.type === 'rparen') {
      throw new CalcError('括号不匹配：多余的右括号“)”');
    }
    if (token.type === 'number') {
      throw new CalcError(`表达式语法错误：数字“${token.raw}”前缺少运算符`);
    }
    if (token.type === 'lparen') {
      throw new CalcError(
        `表达式语法错误：位置 ${token.position + 1} 处的“（”前缺少运算符`
      );
    }
    throw new CalcError(
      `表达式语法错误：位置 ${token.position + 1} 处出现多余的“${token.value}”`
    );
  }
}

/**
 * 计算一个算术表达式的值。
 * @param {string} input 表达式文本
 * @returns {number} 计算结果
 * @throws {CalcError} 表达式非法或出现除零等计算错误
 */
function evaluate(input) {
  if (typeof input !== 'string') {
    throw new CalcError('表达式必须是字符串');
  }

  const tokens = tokenize(input);
  if (tokens.length === 0) {
    throw new CalcError('表达式不能为空');
  }

  const parser = new Parser(tokens);
  const value = parser.parseExpression();
  parser.expectEnd();

  if (Number.isNaN(value)) {
    throw new CalcError('计算结果无效（不是有效数字）');
  }
  if (!Number.isFinite(value)) {
    throw new CalcError('计算结果超出可表示范围');
  }

  return value;
}

/**
 * 将计算结果格式化为适合终端展示的字符串。
 * 安全整数直接按原值显示；二进制浮点会产生类似
 * 0.1 + 0.2 = 0.30000000000000004 的尾差，因此小数保留 15 位有效数字，
 * 并消除 -0。
 * @param {number} value
 * @returns {string}
 */
function formatNumber(value) {
  if (Object.is(value, -0)) {
    return '0';
  }
  if (Number.isSafeInteger(value)) {
    return String(value);
  }
  return String(Number(value.toPrecision(15)));
}

module.exports = {
  CalcError,
  evaluate,
  formatNumber,
  tokenize,
};
