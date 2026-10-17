<!--
OpenRTL-Header-Begin
OpenRTL — Open-source hardware accelerators.
Organization: https://github.com/OpenRTL-Lab
Website: https://openrtl.top/

Project: Goldilocks Merkle Hardware Accelerator
Chisel hardware for Poseidon2 leaf hashing, Merkle trees and authentication paths.
Repository: https://github.com/OpenRTL-Lab/merkle-goldilocks
File: docs/mathematical-model.en.md

SPDX-FileCopyrightText: 2026 Goldilocks Merkle contributors
Copyright applies to original project contributions only.
SPDX-License-Identifier: MIT
See LICENSE and applicable third-party notices.
OpenRTL-Header-End
-->

# Mathematical model

[简体中文](mathematical-model.md)

This document precisely defines the fixed field, Poseidon2 permutation, leaf digests, ordered parent compression, three task modes, and output rules; all constants are included, and the C and independent Python references implement the same model for compatibility checking.

## Field and permutation

All arithmetic is in $\mathbb{F}_p$, where $p=2^{64}-2^{32}+1$. External elements use canonical unsigned 64-bit encodings $0\le x<p$. The state has 12 elements and S-box $x\mapsto x^7$, with four initial full rounds, 22 partial rounds, and four final full rounds. Partial rounds add a constant and apply the S-box only to coordinate zero.

The external layer uses:

$$
B=\begin{bmatrix}2&3&1&1\\1&2&3&1\\1&1&2&3\\3&1&1&2\end{bmatrix},\qquad E=\begin{bmatrix}2B&B&B\\B&2B&B\\B&B&2B\end{bmatrix}.
$$

The internal layer is $I(x)_i=\sum_{j=0}^{11}x_j+d_i x_i$, with $d_i$ from `MATRIX_DIAG_12_GOLDILOCKS`. This is an all-ones matrix plus the specified diagonal increment; $d_i$ is not the complete matrix diagonal.

With $x^{(0)}=Ex$ and $r=0,\ldots,29$:

$$
x^{(r+1)}=\begin{cases}
E\big((x_i^{(r)}+c_{r,i})^7\big)_{i=0}^{11},&r<4\lor r\ge26,\\
I\big((x_0^{(r)}+a_{r-4})^7,x_1^{(r)},\ldots,x_{11}^{(r)}\big),&4\le r<26.
\end{cases}\qquad P(x)=x^{(30)}.
$$

Initial full-round constants are `GOLDILOCKS_POSEIDON2_RC_12_EXTERNAL_INITIAL`, final full-round constants are `GOLDILOCKS_POSEIDON2_RC_12_EXTERNAL_FINAL`, and partial-round constants are `GOLDILOCKS_POSEIDON2_RC_12_INTERNAL`. All values, diagonal increments, and the known-answer vector are in [Fixed parameters](../operators/merkle/vendor/poseidon2/reference/parameters.json); hardware constants are in [Permutation parameter source](../operators/merkle/vendor/poseidon2/src/main/scala/zk/poseidon2/Parameters.scala). They originate from Plonky3's default 12-element Goldilocks permutation, with the MDSMat4 external layer. See [Third-party notices](../THIRD_PARTY.en.md).

## Leaves and parents

Let $\pi_4(x)=(x_0,x_1,x_2,x_3)$. For $L\in\{0,\ldots,8\}$ leaf elements:

$$
H(w_0,\ldots,w_{L-1})=\pi_4P(w_0,\ldots,w_{L-1},1,\underbrace{0,\ldots,0}_{11-L}).
$$

For four-element digests u and v:

$$
C(u,v)=\pi_4P(u_0,u_1,u_2,u_3,v_0,v_1,v_2,v_3,0,0,0,0).
$$

Left/right order is fixed, without numerical sorting. Leaves include a termination marker; parent compression does not. Hashing an arbitrary eight-element leaf is therefore not defined as compressing two digests.

## Task modes

Let $N=2^k$ with $1\le N\le\mathrm{maxLeaves}$ and a common leaf width L per task.

BUILD computes $a_{0,i}=H(w_i)$ and $a_{\ell+1,i}=C(a_{\ell,2i},a_{\ell,2i+1})$, with root $a_{k,0}$. It emits all internal digests from level 1 through k in ascending index order within each level, ending with the root. For N=1, the sole level-zero leaf digest is emitted as the root. It performs $2N-1$ permutations.

LEAF_HASH emits $H(w_i)$ for $i=0,\ldots,N-1$ at level zero. The completion root is zero and matches=0; only the final output has last=1, and every root flag is zero. It performs N permutations.

VERIFY consumes leaf values, index q, siblings $s_0,\ldots,s_{k-1}$ ordered from leaf to root, and expected root R. With $z_0=H(w)$:

$$
z_{\ell+1}=\begin{cases}C(z_\ell,s_\ell),&((q\gg\ell)\mathbin{\&}1)=0,\\C(s_\ell,z_\ell),&((q\gg\ell)\mathbin{\&}1)=1.\end{cases}
$$

It emits the computed $z_k$ and reports matches after comparing all four elements. A nonmatching proof is a normal completion (status=0, matches=0). The task performs k+1 permutations; N=1 requires no siblings. The tag is returned as metadata and does not enter the hash.

## Encoding and errors

Channel ordering, field bounds, result validity, and status semantics are specified in [Interface](interface.en.md). This model defines a binary tree with the stated field and fixed encoding, not an implicit byte-stream hash, arbitrary-length sponge, or alternative Poseidon parameter set.
