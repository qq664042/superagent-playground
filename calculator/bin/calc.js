#!/usr/bin/env node
'use strict';

const readline = require('node:readline');
const { CalcError, evaluate, formatNumber } = require('../src/evaluator');

const USAGE = [
  '简易命令行计算器（零第三方依赖）',
  '',
  '用法：',
  '  calc "<表达式>"       计算并输出结果，例如 calc "1+2*3"',
  '  calc                 启动交互式 REPL',
  '  calc -h, --help      显示本帮助',
  '',
  '支持的运算符：+ - * / 和小括号（），支持一元正负号。',
  '示例：calc "(1+2)*3"',
].join('\n');

const EXIT_COMMANDS = new Set(['exit', 'quit', '退出']);
const HELP_COMMANDS = new Set(['help', 'h', '帮助']);

function printError(error) {
  const message =
    error instanceof CalcError ? error.message : `发生未知错误：${error.message}`;
  process.stderr.write(`错误：${message}\n`);
}

function runOnce(expression) {
  try {
    process.stdout.write(`${formatNumber(evaluate(expression))}\n`);
  } catch (error) {
    printError(error);
    process.exitCode = 1;
  }
}

function startRepl() {
  // 标准输入是终端时才显示提示符和欢迎语，管道输入时只输出结果，方便脚本使用。
  const interactive = process.stdin.isTTY === true;
  const rl = readline.createInterface({
    input: process.stdin,
    output: process.stdout,
    terminal: interactive,
  });

  if (interactive) {
    process.stdout.write(
      '简易命令行计算器：输入表达式求值，输入 exit 退出，输入 help 查看帮助。\n'
    );
    rl.setPrompt('> ');
    rl.prompt();
  }

  rl.on('line', (line) => {
    const input = line.trim();

    if (input === '') {
      if (interactive) {
        rl.prompt();
      }
      return;
    }

    if (EXIT_COMMANDS.has(input)) {
      rl.close();
      return;
    }

    if (HELP_COMMANDS.has(input)) {
      process.stdout.write(`${USAGE}\n`);
    } else {
      try {
        process.stdout.write(`${formatNumber(evaluate(input))}\n`);
      } catch (error) {
        printError(error);
      }
    }

    if (interactive) {
      rl.prompt();
    }
  });

  if (interactive) {
    rl.on('close', () => {
      process.stdout.write('\n再见！\n');
    });
  }
}

function main(argv) {
  const args = argv.slice(2);

  if (args.length === 0) {
    startRepl();
    return;
  }

  if (args.length === 1 && (args[0] === '-h' || args[0] === '--help')) {
    process.stdout.write(`${USAGE}\n`);
    return;
  }

  // 依次拼接参数，既支持 calc "1+2*3"，也支持 calc 1 + 2 '*'
  runOnce(args.join(' '));
}

main(process.argv);
