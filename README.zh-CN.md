<!--
OpenRTL-Header-Begin
OpenRTL — Open-source hardware accelerators.
Organization: https://github.com/OpenRTL-Lab
Website: https://openrtl.top/

Project: Goldilocks Merkle Hardware Accelerator
Chisel hardware for Poseidon2 leaf hashing, Merkle trees and authentication paths.
Repository: https://github.com/OpenRTL-Lab/merkle-goldilocks
File: README.zh-CN.md

SPDX-FileCopyrightText: 2026 Goldilocks Merkle contributors
Copyright applies to original project contributions only.
SPDX-License-Identifier: MIT
See LICENSE and applicable third-party notices.
OpenRTL-Header-End
-->

# merkle-goldilocks 硬件加速器

[English](README.md)

本仓库提供使用 Chisel 开发的 Goldilocks Merkle 独立硬件加速器，集成叶哈希、二叉树构建和认证路径核验。它使用 12 元素 Goldilocks Poseidon2 置换生成四元素摘要，可通过原生接口集成到 ASIC 或 FPGA，用于采用相同哈希规则的零知识证明承诺、认证数据结构及其他 Merkle 计算。

采用 MIT 许可证，允许免费商用、修改和再分发，须保留版权及许可声明；第三方参数见[第三方声明](THIRD_PARTY.md)。

[Release v1.1.0](https://github.com/OpenRTL-Lab/merkle-goldilocks/releases/tag/v1.1.0) · [接口说明](docs/interface.md) · [快速复现](docs/reproduction.md) · [性能指标](docs/performance.md) · [综合摘要](docs/synthesis-summary.md)

## 计算与任务单位

摘要含四个 Goldilocks 域元素，不是四字节。每个元素是规范无符号 64 位值，满足 0 <= x < p，p = 2^64 - 2^32 + 1。任务采用 N = 2^k 个叶，每叶统一含 L 个域元素，L 为 0 至 8。16 个置换上下文是在同一任务内共用一颗 Poseidon2 核的在途状态，不是 16 棵并发树。

| 模式 | 任务输入与结果 |
| --- | --- |
| BUILD | 提供全部 N 个叶；接收从第 1 层至根、逐层输出的内部节点，每层按索引递增。该模式不流式输出叶摘要；N = 1 时输出唯一叶摘要作为根。 |
| VERIFY | 提供一个叶、其索引、从叶至根的兄弟摘要和期望根；接收计算得到的根及 matches 标志。格式合法但不匹配的路径以 status = 0、matches = 0 完成。 |
| LEAF_HASH | 提供 N 个叶；按顺序接收其 N 个叶摘要，不构建树。 |

叶哈希在叶元素后加域元素标记 1，再补零组成 12 元素状态，应用 Poseidon2 后取前四元素。父压缩则对左摘要、右摘要、四个零构成的状态进行置换，不加叶标记。子节点不排序；若目标协议使用不同叶编码，仅匹配置换参数并不足够。

## 配置速览

| 集成问题 | 发布的硬件 |
| --- | --- |
| 计算 | 二叉 Merkle 建树、认证路径验证和叶哈希 |
| 随包配置 | 最多 65,536 个叶，每叶 0..8 个域元素，16 个置换上下文 |
| 产品顶层 | `GoldilocksMerkle`；共享固定 Goldilocks Poseidon2 置换 |
| 原生边界 | 任务、有序叶/兄弟数据、带标签及层号/索引的摘要结果、完成通知 |
| 树结果 | BUILD 输出内部节点；VERIFY 输出计算得到的根；LEAF_HASH 流式输出叶摘要 |

## 快速开始

从 [Release v1.1.0](https://github.com/OpenRTL-Lab/merkle-goldilocks/releases/tag/v1.1.0) 下载源码包，或克隆当前发布快照：

```bash
git clone --branch v1.1.0 --depth 1 https://github.com/OpenRTL-Lab/merkle-goldilocks.git
cd merkle-goldilocks
```

按[工具链说明](docs/toolchain.md)安装准确依赖，然后在仓库根目录使用全新输出目录运行：

```bash
python3 tools/emit_sv.py --out build/emit --max-leaves 65536
```

该生成命令不是完整 RTL 回归。文档规定的验证入口是：

```bash
python3 tools/verify.py --out build/verify --jobs 8
```

预期结果、负控判读以及短功能验证与完整性能运行的区别见[快速复现](docs/reproduction.md)。

## 验证与集成边界

默认 443 任务回归对新生成及随包产品覆盖全部三模式、小树、空/边界叶宽、错误证明、坏帧和规范域元素检查，保留独立 C/Python 核对和故意注入的参考错误。另有命令逐个核对八棵完整 65,536 叶树的全部内部摘要，以及 15 个深认证路径变体；默认小树短负载不隐含这些覆盖。

叶编码、填充、子节点顺序和 Poseidon2 参数须与目标协议完全匹配。VERIFY 帧合法但根不匹配时报告 matches=0，而非接口错误。16 个上下文加速同一任务内的置换，不是 16 棵并发树。只有成功完成后才能提交输出；存储、证明选择、挑战调度和完整 STARK 证明器仍由外部负责。

### 正确理解性能

理想原生接口下，对每叶八元素的 65,536 叶树执行 BUILD，延迟为 70,281,230 周期；连续完整树的完成间隔为 70,281,231 周期，假定 500 MHz 约为 7.114 树/s。共享模乘有效发射忙率为 61,865,512 个发射周期 / 70,281,230 个任务窗口周期 = 88.026%。树率、叶率和置换率的单位不同；VERIFY 与 LEAF_HASH 须使用各自负载衡量。

这些是理想原生接口条件下记录的 RTL 周期测量。按 MHz 换算的时间/速率不是板级或硅后实测。Design Compiler 结果仅适用于文档规定的配置、库工艺角和约束，不代表布局布线签核或系统吞吐率。条件见[性能指标](docs/performance.md)与[综合摘要](docs/synthesis-summary.md)。

## 仓库内容

| 内容 | 入口 |
| --- | --- |
| Chisel 设计、仿真环境 | [设计源码](operators/merkle/src/main/scala/zk/merkle/) |
| 配套 Poseidon2 计算核与固定参数 | [置换源码](operators/merkle/vendor/poseidon2/) |
| 生成好的产品 SV | [产品 SV](rtl/v1.1.0/max65536-l8-ctx16/) |
| SV 生成、功能及性能验证入口 | [生成脚本](tools/emit_sv.py)、[验证脚本](tools/verify.py) |
| C 参考、独立 Python 参考、C++ 驱动 | [C 参考](operators/merkle/cmodel/)、[Python 参考](operators/merkle/tools/reference.py)、[仿真驱动](operators/merkle/sim/) |
| 原生通道与存储结构说明 | [接口说明](docs/interface.md)、[架构说明](docs/architecture.md) |
| 开源工具链版本 | [工具链说明](docs/toolchain.md) |

## 应用场景

它可以加速 Goldilocks 数据的叶摘要生成、二叉 Merkle 承诺树构建，以及给定叶值、兄弟摘要和索引的根核验。例如，可用于采用对应编码和压缩规则的 STARK/FRI 承诺与查询认证，也可用于其他以四个域元素为摘要的认证数据结构。用户可按协议组合多个实例；这些示例不限定全部用途。具体集成要求见[应用场景](docs/applications.md)。

## 性能指标

理想原生接口下，65,536 叶、每叶八元素的建树延迟为 70,281,230 周期，连续树完成间隔为 70,281,231 周期；按 500 MHz 换算约 7.114 树/s，模乘有效忙率为 88.026%。三种模式的指标定义、测法和结果见[性能指标](docs/performance.md)。

## 数学模型

电路使用固定 Goldilocks Poseidon2 参数，叶编码包含结束标记，父节点按左右顺序压缩；BUILD、VERIFY、LEAF_HASH 的精确定义见[数学模型](docs/mathematical-model.md)。

## 快速复现

开源流程可重新生成 SV，以 C/Python 参考检查结果，并对新生成与随包产品运行功能、协议、复位及性能测试；安装和命令见[快速复现](docs/reproduction.md)。

## 综合结果

本配置已通过 TSMC 28 nm HPC+ 最差角、500 MHz及10%时钟不确定度的 DC setup 门禁，TNS为0，99.345% 的端点最差 setup 路径严格少于 50 级；这里逐个计数据路径中名为 Z/ZN 的组合单元输出引脚，不计寄存器 Q、SRAM 输出、时钟或连线；条件、面积和统计口径见[综合摘要](docs/synthesis-summary.md)。

## 公开边界

公开内容覆盖 RTL 设计、生成工具、完整仿真链路及综合结果转述。抱歉，由于第三方授权及保密限制，无法公开工艺库、SRAM宏模型、DC原始脚本与报告或综合快照。本仓库提供原生接口硬件，不提供 SoC、协议系统封装或上板工程；详情见[公开边界](docs/publication-boundary.md)。

## OpenRTL与定制集成

通过[OpenRTL组织主页](https://github.com/OpenRTL-Lab)、[ZK加速器集合](https://github.com/OpenRTL-Lab/zk-accelerators)及[OpenRTL网站](https://openrtl.top/)查找相关模块与合作联系入口。需要定制数据通路、接口或目标平台适配时可联系维护者；MIT商用权不附加购买服务的要求，不要在公开Issue提交保密规格。

## 发布源码与集成

版本标签标识包含来源声明和文档的当前源码树；Release 下载提供本版本硬件源码与随包 RTL。源码包与在线目录的注释及文档可能不同。RTL 集成使用产品文件清单，不把验证环境作为产品电路；支持配置、验证范围和测量条件见验证及综合文档。
