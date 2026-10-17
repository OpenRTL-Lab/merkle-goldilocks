<!--
OpenRTL-Header-Begin
OpenRTL — Open-source hardware accelerators.
Organization: https://github.com/OpenRTL-Lab
Website: https://openrtl.top/

Project: Goldilocks Merkle Hardware Accelerator
Chisel hardware for Poseidon2 leaf hashing, Merkle trees and authentication paths.
Repository: https://github.com/OpenRTL-Lab/merkle-goldilocks
File: docs/applications.md

SPDX-FileCopyrightText: 2026 Goldilocks Merkle contributors
Copyright applies to original project contributions only.
SPDX-License-Identifier: MIT
See LICENSE and applicable third-party notices.
OpenRTL-Header-End
-->

# 应用场景

[English](applications.en.md)

本加速器将 Goldilocks 域上的叶哈希、二叉 Merkle 建树和认证路径核验组合为一个原生接口 IP，适合把承诺构建和查询认证的计算部分交给硬件；应用可以是零知识证明，也可以是使用相同哈希规则的其他认证数据结构。

BUILD 构建一棵树，VERIFY 核验一条已提供的认证路径，LEAF_HASH 只返回叶摘要。摘要为四个64位 Goldilocks 域元素，不是四字节；须匹配叶编码和左右子节点顺序。

| 场景 | 可交给硬件的部分 | 系统集成工作 |
| --- | --- | --- |
| STARK/FRI多项式与轨迹承诺 | 对已排列的叶数据运行BUILD | 定义叶排列、域扩展编码和承诺协议 |
| 证明查询、认证路径核验 | 对叶值、索引、兄弟摘要运行VERIFY | 提取证明字段并提供期望根 |
| 批量叶摘要、分片预处理 | 运行LEAF_HASH，输出独立叶摘要 | 分配任务、收集摘要、组合上层流程 |
| 认证数据库或其他二叉树 | 构建根和内部节点，验证成员路径 | 管理数据版本、路径存储与更新 |

这些是集成示例，不是封闭的适用系统名单。具体协议需采用本项目的12元素置换参数、四元素摘要、叶结束标记和有序父压缩规则；不能仅凭“Poseidon2”名称判定互换性。固定规则见[数学模型](mathematical-model.md)，字段和握手见[接口说明](interface.md)。

BUILD提供内部节点和根，LEAF_HASH提供叶摘要；用户可保存相应输出以生成路径。VERIFY接收既有路径，不在内部搜索或生成证明。宽叶编码、多棵树合并、transcript域分离及其他协议功能由系统按照其定义组合。
