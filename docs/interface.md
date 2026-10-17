<!--
OpenRTL-Header-Begin
OpenRTL — Open-source hardware accelerators.
Organization: https://github.com/OpenRTL-Lab
Website: https://openrtl.top/

Project: Goldilocks Merkle Hardware Accelerator
Chisel hardware for Poseidon2 leaf hashing, Merkle trees and authentication paths.
Repository: https://github.com/OpenRTL-Lab/merkle-goldilocks
File: docs/interface.md

SPDX-FileCopyrightText: 2026 Goldilocks Merkle contributors
Copyright applies to original project contributions only.
SPDX-License-Identifier: MIT
See LICENSE and applicable third-party notices.
OpenRTL-Header-End
-->

# 接口说明

[English](interface.en.md)

产品顶层GoldilocksMerkle提供单时钟、同步高有效复位以及task/data/result/completion四个原生valid/ready通道，支持三种任务与固定帧数据；本文定义每个字段、输入顺序、完成语义和错误处理，便于直接集成。

令 N=leafCount=2^k，L=leafWidth（每叶0..8个域元素）。maxLeaves 是生成容量（2..65,536之间的2的幂，随包65,536），运行时 N 可为1；k 是路径深度，不是叶宽。每个摘要含四个规范无符号域字 0 ≤ x < p，p = 2^64 - 2^32 + 1。BUILD 构建一棵树，VERIFY 核验一条路径，LEAF_HASH 只哈希 N 个叶而不构建父节点。

## 通道与字段

| 通道 | 方向 | 字段 |
| --- | --- | --- |
| task | 输入 | tag:32；mode:2；leafCount:32；leafWidth:4；leafIndex:32；expectedRoot:4×64 |
| data | 输入 | word:64；kind:1；last:1 |
| result | 输出 | tag:32；level:5；index:32；digest:4×64；root:1；last:1 |
| completion | 输出 | tag:32；status:2；matches:1；rootDigest:4×64 |

在上升沿valid与ready同时为1时传输。发送方在valid=1、ready=0时保持valid及所有payload稳定。一次只接受一个任务，前一completion握手后可接受下一task。tag原样返回，不参加计算。

mode=0 BUILD；1 VERIFY；2 LEAF_HASH；3非法。leafCount是1到maxLeaves之间的2次幂；leafWidth是0..8。VERIFY要求leafIndex<leafCount；其他模式忽略leafIndex和expectedRoot。

## 输入与输出

BUILD/LEAF_HASH按叶索引递增发送N×L个word，每叶元素按坐标递增，kind=0，只有整任务最后一拍last=1。L=0时无需data，叶编码仍有结束标记。

VERIFY先发L个叶元素（kind=0），再发k个兄弟摘要（kind=1），每摘要顺序0..3，从最低层至最高层；总L+4k拍，只有最后一拍last=1。N=1、L=0时无需data。

BUILD输出层1..k全部内部节点，同层index递增，最终根root=1、last=1；N=1输出层0唯一叶根。LEAF_HASH输出N条层0摘要，root=0，最后一条last=1。VERIFY仅输出计算得到的根，level=k、index=0、root=last=1。

completion在全部result被接收后有效。BUILD返回根、matches=0；LEAF_HASH返回全0根、matches=0；VERIFY返回计算根和单bit matches：只有四个元素均等于 expectedRoot 时为1。具体运算见[数学模型](mathematical-model.md)。

## 状态与复位

| status | 定义 | 消费行为 |
| --- | --- | --- |
| 0 | 正常完成 | VERIFY不匹配用matches=0表达，不是接口错误 |
| 1 | 非法模式、尺寸、叶宽或VERIFY索引 | 不消费data，不输出result，根全0 |
| 2 | kind或last与固定帧不符 | 按预定帧长度消费并排空，结果须依据completion丢弃 |
| 3 | word非规范，即word≥p | 将该元素替代为0以排空预定帧，结果须丢弃；优先于status2 |

输入帧长度由task确定，提前last不能缩短帧。缺失数据时等待发送方；需要取消时使用reset。同步reset取消在途任务并清除控制有效状态，不清空SRAM；新任务会覆盖其有效存储范围。集成方以status=0确认成功任务，不提交错误任务的暂存结果。result具备两记录非穿透FIFO，背压不会形成外部ready到SRAM写使能的组合通路。
