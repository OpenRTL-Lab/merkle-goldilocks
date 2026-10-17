<!--
OpenRTL-Header-Begin
OpenRTL — Open-source hardware accelerators.
Organization: https://github.com/OpenRTL-Lab
Website: https://openrtl.top/

Project: Goldilocks Merkle Hardware Accelerator
Chisel hardware for Poseidon2 leaf hashing, Merkle trees and authentication paths.
Repository: https://github.com/OpenRTL-Lab/merkle-goldilocks
File: docs/reproduction.md

SPDX-FileCopyrightText: 2026 Goldilocks Merkle contributors
Copyright applies to original project contributions only.
SPDX-License-Identifier: MIT
See LICENSE and applicable third-party notices.
OpenRTL-Header-End
-->

# 快速复现

[English](reproduction.en.md)

本文给出从开源源码重新生成产品SV、对照独立参考进行四模式回归、重测三类任务性能的完整步骤；验证同时覆盖新生成和随包SV，并检查它们的电路与周期结果一致。

## 获取版本快照

[Release v1.1.0](https://github.com/OpenRTL-Lab/merkle-goldilocks/releases/tag/v1.1.0)

```bash
git clone --branch v1.1.0 --depth 1 https://github.com/OpenRTL-Lab/merkle-goldilocks.git
cd merkle-goldilocks
```

版本标签标识包含来源声明和文档的当前源码树；Release 下载提供本版本硬件源码与随包 RTL。源码包与在线目录的注释及文档可能不同。RTL 集成使用产品文件清单，不把验证环境作为产品电路；支持配置、验证范围和测量条件见验证及综合文档。

## 准备

先按[工具链说明](toolchain.md)安装工具并配置PATH，之后在仓库根目录执行下列命令。输出仅使用build下的相对新目录，脚本不覆盖既有结果；需要重跑时更换--out。输入向量由C参考再生，不需要任何内部发布目录或综合工具。

`--max-leaves` 设置硬件叶容量，`--n N` 选测试每树叶数，`--width L` 选每叶域元素数；N16/L8 即16叶、每叶8元素。`--task-mode 0/1/2` 选择 BUILD/VERIFY/LEAF_HASH；`--modes` 则选择验证场景（0为理想时序），不是产品模式。`--tasks` 设置性能任务数，`--no-suite` 去掉默认边界套件，`--deep-proof` 即使在 `--tasks 0` 时仍加入路径变体；`--jobs` 只控制编译并行度。

## 默认功能回归

以下命令生成典型maxLeaves=65536产品，运行256个连续N16/L8建树任务及边界测试；分别对新生成与随包SV执行理想、协议停顿、三处复位恢复和参考负控，并输出周期统计：

```sh
python3 tools/verify.py --out build/verify --jobs 8
```

输出 `results.json` 的status=0表示整个验证通过。正常模式零不匹配；负控故意翻转一个参考字段，预期检出一次不匹配、仿真进程退出1，验证脚本将此视为成功检测。复位会重试被取消任务，检查计数可包含复位前已核对的输出。

C/Python交叉核对包括27组12元素置换、已知答案及16种小树/叶宽组合；默认443任务覆盖三模式、N1/2/4/16、L0/1/7/8、错误证明、坏参数、帧标记和非规范元素。所有参考、向量生成、ENV和驱动均在仓库中。

## 典型树性能

以下命令连续计算8棵65,536叶、每叶8元素的树，检查全部内部摘要，并独立量测7个完成间隔；性能仅使用理想通道：

```sh
python3 tools/verify.py --out build/typical --jobs 8 --n 65536 --width 8 --tasks 8 --no-suite --modes 0
```

## 深路径与叶哈希

以下命令检查16层认证路径的正确与错误变体，共15个任务，包含全部功能测试模式，并量测路径性能：

```sh
python3 tools/verify.py --out build/proof --jobs 8 --n 65536 --width 8 --tasks 0 --no-suite --deep-proof
```

以下命令连续运行256个N16/L8叶哈希任务，量测独立叶哈希吞吐：

```sh
python3 tools/verify.py --out build/leaf --jobs 8 --n 16 --width 8 --tasks 256 --no-suite --task-mode 2 --modes 0
```

## 仅生成与使用产品

只需要生成SV及参考环境时执行下列命令，不编译Verilator模型：

```sh
python3 tools/emit_sv.py --out build/emit --max-leaves 65536
```

生成的 `build/emit/rtl/product.f`列出产品闭包；仿真清单另为simulation.f。直接使用随包电路时，顶层是GoldilocksMerkle，清单为[产品文件清单](../rtl/v1.1.0/max65536-l8-ctx16/files.f)，从仓库根目录解析。无需把MerkleSimTop或仿真ROM交给综合工具。

可通过--max-leaves设置2..65536之间的2次幂，改变生成电路存储规模；运行时--n和--width选择该次测试任务，而非改变哈希参数。当前上下文固定16；小配置不与典型随包SV比较。--jobs只改变主机编译并行度，不改变硬件。

## 结果阅读

`reference-results.json`记录独立参考核对；`configuration.json`记录测试配置；`results.json`包含各模式结果、逐任务延迟、连续完成间隔、逐层计数及五类计算单元忙率。对应日志保存在本次输出目录。指标公式与数值见[性能指标](performance.md)，错误时先查看失败阶段日志。
