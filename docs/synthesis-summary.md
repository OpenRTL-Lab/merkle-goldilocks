<!--
OpenRTL-Header-Begin
OpenRTL — Open-source hardware accelerators.
Organization: https://github.com/OpenRTL-Lab
Website: https://openrtl.top/

Project: Goldilocks Merkle Hardware Accelerator
Chisel hardware for Poseidon2 leaf hashing, Merkle trees and authentication paths.
Repository: https://github.com/OpenRTL-Lab/merkle-goldilocks
File: docs/synthesis-summary.md

SPDX-FileCopyrightText: 2026 Goldilocks Merkle contributors
Copyright applies to original project contributions only.
SPDX-License-Identifier: MIT
See LICENSE and applicable third-party notices.
OpenRTL-Header-End
-->

# 综合摘要

[English](synthesis-summary.en.md)

GoldilocksMerkle典型配置已通过TSMC 28 nm HPC+最差角、500 MHz、10%时钟不确定度的Design Compiler setup门禁：WNS为正、TNS及违例端点均为0，99.345%的统计路径严格少于50级Z/ZN逻辑；本文转述综合条件、面积与路径计数结果。

L 为每叶域元素数，maxLeaves 为叶数上限；ctx16 表示一颗置换核交织16个状态，不是16棵树。WNS 是最差 setup 裕量，TNS 是负 setup 裕量之和。

## 条件

| 项目 | 条件 |
| --- | --- |
| 顶层/配置 | GoldilocksMerkle；maxLeaves65536、L≤8、ctx16 |
| 综合工具 | Synopsys Design Compiler NXT X-2025.06-SP3 |
| 工艺/存储 | TSMC 28 nm HPC+标准单元及SRAM宏 |
| 最差角 | SSG、0.81 V、−40 °C |
| 时钟 | 单时钟2.0 ns，即500 MHz |
| Setup不确定度 | 0.2 ns，周期的10% |
| 时钟/输入转换 | 0.1 ns / 0.125 ns |
| 数据最大转换 | 0.25 ns |
| I/O延迟 | 最大1.0 ns，最小0 ns |
| 输出负载 | 0.02 pF |
| 最大扇出 | 32 |
| 综合线负载模型 | ZeroWireload |
| 复位 | 同步复位，纳入时序检查 |

## Setup与逻辑级数

| 指标 | 结果 |
| --- | ---: |
| WNS | +0.000012517 ns |
| TNS | 0 ns |
| Setup违例端点 | 0 |
| 统计路径/受约束端点 | 77,436 |
| 严格少于50级的路径 | 76,929，99.3452658% |
| 恰好50级 | 104 |
| 大于50级 | 403 |
| 最大Z/ZN级数 | 64，3条路径 |

路径覆盖内部75,537条、内部到输出622条、输入到内部1,277条；按每个受约束端点的最差setup路径统计，计数口径不是枚举设计中所有可能的拓扑路径。仅报告路径数据段中组合单元的Z或ZN输出脚位算一级；寄存器Q、SRAM输出、时钟路径和报告行数不代替逻辑级数。Setup达标与级数分布分别核对。

## 映射面积

| 互斥面积类别 | µm² |
| --- | ---: |
| SRAM宏 | 18,847,284.000 |
| 组合逻辑，不含缓冲器／反相器 | 71,954.541 |
| 缓冲器／反相器 | 5,182.142 |
| 时序单元 | 54,641.958 |
| 总cell面积 | 18,979,062.641 |

总映射cell面积18.979063 mm²，非存储逻辑合计0.131779 mm²。512个1024×64 SRAM宏组成两个65,536×256银行，共4 MiB；SRAM宏占映射cell面积99.3057%。这些数值是映射cell面积，按宏和标准单元求和，不把父模块与子模块面积重复相加，也不把它定义为包含布线与留白的die面积。

## 信息提供方式

公开SV与源码对应本配置，综合结果由我们依据专业流程的输出转述。抱歉，由于第三方授权及保密限制，无法提供工艺库、宏模型、原始DC Tcl、报告或综合快照。开源RTL与仿真可独立复现；综合环境由使用者自行准备，边界见[公开边界](publication-boundary.md)。
