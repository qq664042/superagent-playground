# superagent-playground

dsh-superagent agent 的演练场。在 Issue 里 @qq664042 下达任务即可。

## 安装说明

按以下三步即可在本地获取仓库并预览文档。

### 1. 克隆仓库

```bash
git clone https://github.com/qq664042/superagent-playground.git
cd superagent-playground
```

### 2. 安装依赖

仓库中的[命令行计算器](#命令行计算器)位于 `calculator/` 目录，使用 Node.js 实现，零第三方依赖，无需执行 `npm install`。若后续其他项目在根目录添加了 `package.json`，再在仓库根目录执行：

```bash
npm install
```

### 3. 本地运行

本仓库没有需要构建或启动的应用程序，直接阅读 `README.md` 即可。如需通过本地服务器查看文件，可在仓库根目录执行：

```bash
python3 -m http.server 8000
```

然后访问 <http://localhost:8000/README.md>。

## 命令行计算器

`calculator/` 是一个零第三方依赖的 Node.js 命令行计算器（需要 Node.js 20 或以上），支持：

- 四则运算 `+ - * /`，优先级正确，支持小括号嵌套；
- 一元正负号，如 `-3`、`2*-3`、`-(1+2)`；
- 整数与小数，忽略表达式中的空白字符；
- 命令行参数一次性求值，或无参数进入交互式 REPL；
- 除零、非法表达式等情况给出清晰的中文错误提示；
- 使用 Node.js 内置 `node:test` 的单元测试与命令行集成测试。

连续的正负号按一元符号解析，这是设计行为：`1++2` 等价于 `1 + (+2)`，结果为 `3`；`3--2` 等价于 `3 - (-2)`，结果为 `5`。

计算器放在独立的 `calculator/` 子目录中，自带 `package.json`、入口和测试，避免与仓库后续其他演练项目互相干扰。

### 快速开始

在仓库根目录直接运行：

```bash
$ node calculator/bin/calc.js "1+2*3"
7
$ node calculator/bin/calc.js "(10-4)/3"
2
$ node calculator/bin/calc.js "2*-3"
-6
```

也可以先进入 `calculator/` 目录：

```bash
$ cd calculator
$ node bin/calc.js "1+2*3"
7
```

如果希望像需求示例那样直接使用 `calc` 命令，可以在 `calculator/` 目录执行 `npm link`（或 `npm install -g .`）把可执行文件链接到全局：

```bash
$ cd calculator
$ npm link
$ calc "(1+2)*3"
9
```

### 交互式 REPL

不带参数运行时进入 REPL：

```text
$ node calculator/bin/calc.js
简易命令行计算器：输入表达式求值，输入 exit 退出，输入 help 查看帮助。
> 1+2*3
7
> (10-4)/3
2
> 1/0
错误：除数不能为 0
> 1+
错误：表达式不完整：缺少数字或括号
> exit

再见！
```

REPL 中出错后会继续等待下一次输入；输入 `exit`、`quit` 或 `退出` 结束，输入 `help` 查看用法。

### 错误提示与退出码

一次性求值时，表达式非法或出现除零会向标准错误输出中文提示，并以退出码 `1` 结束：

```bash
$ node calculator/bin/calc.js "1/0"
错误：除数不能为 0
$ echo $?
1
$ node calculator/bin/calc.js "1+"
错误：表达式不完整：缺少数字或括号
$ echo $?
1
```

常见错误提示包括：

| 场景 | 提示 |
| --- | --- |
| 空表达式 | `表达式不能为空` |
| 非法字符 | `表达式中包含非法字符：“@”` |
| 括号不匹配 | `括号不匹配：缺少右括号“)”` |
| 除零 | `除数不能为 0` |
| 结果溢出 | `计算结果超出可表示范围` |

计算结果显示时，安全整数按原值输出，小数保留 15 位有效数字，以避免 `0.1 + 0.2` 这类二进制浮点尾差。

### 运行测试

```bash
$ cd calculator
$ npm test
# 或者直接使用 Node.js 内置测试运行器
$ node --test
```

测试覆盖运算符优先级、括号嵌套、一元正负号、小数、除零、非法表达式，以及命令行退出码和 REPL 行为。

### 目录结构

```text
calculator/
├── bin/calc.js              # 命令行入口：参数求值 + REPL
├── src/evaluator.js         # 词法分析、递归下降解析与求值
├── test/evaluator.test.js   # 求值器单元测试
├── test/cli.test.js         # 命令行与 REPL 集成测试
└── package.json             # 零第三方依赖，npm start / npm test
```
