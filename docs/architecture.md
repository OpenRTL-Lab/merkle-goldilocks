<!--
OpenRTL-Header-Begin
OpenRTL — Open-source hardware accelerators.
Organization: https://github.com/OpenRTL-Lab
Website: https://openrtl.top/

Project: Goldilocks Merkle Hardware Accelerator
Chisel hardware for Poseidon2 leaf hashing, Merkle trees and authentication paths.
Repository: https://github.com/OpenRTL-Lab/merkle-goldilocks
File: docs/architecture.md

SPDX-FileCopyrightText: 2026 Goldilocks Merkle contributors
Copyright applies to original project contributions only.
SPDX-License-Identifier: MIT
See LICENSE and applicable third-party notices.
OpenRTL-Header-End
-->

# 架构说明

[English](architecture.en.md)

本设计以共享流水化模乘、16上下文交织的Poseidon2核和双银行同步存储实现三模式Merkle计算，电路与测试环境分离，产品不依赖FPGA原语；层内并发、寄存分段和结果FIFO共同支持可综合的原生接口结构。

N=2^k 为叶数，L=0..8 为每叶域元素数；摘要是四个规范无符号 Goldilocks 元素，0 ≤ x < p，p = 2^64 - 2^32 + 1。II 是算术发射间隔，不是完整任务间隔。ExtA/ExtB 为置换外部线性层的两个寄存分段，IntA/IntB 为内部线性层的两个分段，不是不同哈希算法。

BUILD逐层计算：先将N个叶摘要写入bank0，再从源bank同步读左右摘要、计算父节点并写目标bank；每层排空后交换源/目标角色，直到根。VERIFY在寄存器内将当前摘要与下一兄弟组成请求；LEAF_HASH复用叶阶段并流式输出摘要。

| 单元 | 结构 |
| --- | --- |
| 模乘 | 一条64位Goldilocks模乘流水线，11周期、II=1；前端含4个32×32乘法 |
| 请求入口与反馈 | 两级选择/加常量入口，反馈距离13周期 |
| 线性层 | ExtA/ExtB及IntA/IntB寄存分段，使用加法、移位和常量运算 |
| 调度 | 16个交织置换上下文，属于同一树的层内并发 |
| 存储 | 两组256位SyncReadMem，各maxLeaves行，典型总容量4 MiB |
| 结果队列 | 两条摘要记录的非穿透FIFO，隔离外部ready组合反馈 |

同一时间只有一个Merkle任务活跃。16上下文不是16棵树；认证路径逐层依赖当前摘要。每次置换使用472次有效模乘发射；外部线性层各9次，内部线性层各22次。测试环境在不新增产品端口的情况下计数有效工作，详情见[性能指标](performance.md)。

两bank用同步读和每bank单一写调用描述，产品不初始化SRAM。综合时可将行为存储映射至ASIC SRAM宏；FPGA集成方可使用对应工具的存储推断。随包[产品 SV](../rtl/v1.1.0/max65536-l8-ctx16/)仅含顶层、置换、模乘和两个存储模块。MerkleSimTop、向量ROM和仿真检查逻辑仅用于验证，不能作为产品综合顶层。
