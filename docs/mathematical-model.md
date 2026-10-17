<!--
OpenRTL-Header-Begin
OpenRTL — Open-source hardware accelerators.
Organization: https://github.com/OpenRTL-Lab
Website: https://openrtl.top/

Project: Goldilocks Merkle Hardware Accelerator
Chisel hardware for Poseidon2 leaf hashing, Merkle trees and authentication paths.
Repository: https://github.com/OpenRTL-Lab/merkle-goldilocks
File: docs/mathematical-model.md

SPDX-FileCopyrightText: 2026 Goldilocks Merkle contributors
Copyright applies to original project contributions only.
SPDX-License-Identifier: MIT
See LICENSE and applicable third-party notices.
OpenRTL-Header-End
-->

# 数学模型

[English](mathematical-model.en.md)

本文严格定义电路的固定域、Poseidon2置换、叶摘要、有序父压缩、三种任务以及输出规则；全部常量随源码提供，C与独立Python参考实现同一模型，集成方可据此核对协议兼容性。

## 域与置换

所有运算在 $\mathbb{F}_p$ 中，$p=2^{64}-2^{32}+1$；外部元素使用满足 $0\le x<p$ 的64位无符号整数编码。状态宽度12，非线性 $x\mapsto x^7$，前4个完整轮、22个部分轮、后4个完整轮；部分轮只对坐标0加常量及求七次幂。

外部矩阵的4×4基础矩阵为：

$$
B=\begin{bmatrix}2&3&1&1\\1&2&3&1\\1&1&2&3\\3&1&1&2\end{bmatrix},\qquad
E=\begin{bmatrix}2B&B&B\\B&2B&B\\B&B&2B\end{bmatrix}.
$$

内部线性层定义为 $I(x)_i=\sum_{j=0}^{11}x_j+d_i x_i$，其中 $d_i$ 为参数文件 `MATRIX_DIAG_12_GOLDILOCKS` 的第i项。它是全1矩阵加指定对角增量，不能将 $d_i$ 再解释为完整矩阵的对角项。

令 $x^{(0)}=Ex$，轮数 $r=0,\ldots,29$：

$$
x^{(r+1)}=\begin{cases}
E\big((x_i^{(r)}+c_{r,i})^7\big)_{i=0}^{11},& r<4\lor r\ge26,\\
I\big((x_0^{(r)}+a_{r-4})^7,x_1^{(r)},\ldots,x_{11}^{(r)}\big),&4\le r<26.
\end{cases}
\qquad P(x)=x^{(30)}.
$$

首4轮常量来自 `GOLDILOCKS_POSEIDON2_RC_12_EXTERNAL_INITIAL`，后4轮来自 `GOLDILOCKS_POSEIDON2_RC_12_EXTERNAL_FINAL`，部分轮来自 `GOLDILOCKS_POSEIDON2_RC_12_INTERNAL`。全部数值、对角增量和已知答案在[固定参数](../operators/merkle/vendor/poseidon2/reference/parameters.json)，硬件常量在[置换参数源码](../operators/merkle/vendor/poseidon2/src/main/scala/zk/poseidon2/Parameters.scala)。这些参数来自 Plonky3 的 Goldilocks 12元素默认置换，外部层对应 MDSMat4；第三方来源见[第三方声明](../THIRD_PARTY.md)。

## 叶与父节点

记 $\pi_4(x)=(x_0,x_1,x_2,x_3)$。对于 $L\in\{0,\ldots,8\}$ 个元素的叶：

$$
H(w_0,\ldots,w_{L-1})=\pi_4P(w_0,\ldots,w_{L-1},1,\underbrace{0,\ldots,0}_{11-L}).
$$

对于四元素摘要 $u,v$，父压缩为：

$$
C(u,v)=\pi_4P(u_0,u_1,u_2,u_3,v_0,v_1,v_2,v_3,0,0,0,0).
$$

左右顺序固定，不按数值排序。叶带结束标记，父压缩不带该标记；本模型不把任意8元素叶哈希等同于两个摘要的父压缩。

## 三种任务

叶数 $N=2^k$，$1\le N\le\mathrm{maxLeaves}$，每任务叶宽L相同。

BUILD：$a_{0,i}=H(w_i)$，$a_{\ell+1,i}=C(a_{\ell,2i},a_{\ell,2i+1})$。根为 $a_{k,0}$；输出层1到k的全部内部摘要，同层按索引递增，根为最后输出。N=1时直接输出层0的唯一叶摘要作为根。计算总量为 $2N-1$ 次P。

LEAF_HASH：输出 $H(w_i)$，$i=0,\ldots,N-1$，层号0。completion的根字段全0，matches=0；输出最后一条last=1，所有root标志为0。计算量为N次P。

VERIFY：输入叶值、索引q、从叶到根的兄弟摘要 $s_0,\ldots,s_{k-1}$ 和期望根R，令 $z_0=H(w)$：

$$
z_{\ell+1}=\begin{cases}C(z_\ell,s_\ell),&((q\gg\ell)\mathbin{\&}1)=0,\\C(s_\ell,z_\ell),&((q\gg\ell)\mathbin{\&}1)=1.\end{cases}
$$

输出计算得到的 $z_k$；完整比较四元素后报告matches。证明不匹配仍是正常完成（status=0、matches=0），计算量为k+1次P。N=1时兄弟序列为空。tag仅随任务返回，不参加哈希。

## 编码与错误

数据通道顺序、域元素边界、结果有效性及status含义见[接口说明](interface.md)。这里只定义上述有限域和固定编码的二叉树，不隐含字节流哈希、任意长度sponge或其他Poseidon参数集。
