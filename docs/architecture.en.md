<!--
OpenRTL-Header-Begin
OpenRTL — Open-source hardware accelerators.
Organization: https://github.com/OpenRTL-Lab
Website: https://openrtl.top/

Project: Goldilocks Merkle Hardware Accelerator
Chisel hardware for Poseidon2 leaf hashing, Merkle trees and authentication paths.
Repository: https://github.com/OpenRTL-Lab/merkle-goldilocks
File: docs/architecture.en.md

SPDX-FileCopyrightText: 2026 Goldilocks Merkle contributors
Copyright applies to original project contributions only.
SPDX-License-Identifier: MIT
See LICENSE and applicable third-party notices.
OpenRTL-Header-End
-->

# Architecture

[简体中文](architecture.md)

The design combines a shared pipelined multiplier, a 16-context interleaved Poseidon2 core, and two synchronous memory banks for three-mode Merkle computation; the product is separate from its test environment and uses no FPGA primitives, with registered stages and a result FIFO supporting synthesizable native interfaces.

N=2^k is leaf count and L=0..8 is field elements per leaf. A digest is four canonical unsigned Goldilocks elements, 0 <= x < p with p = 2^64 - 2^32 + 1. II is arithmetic initiation interval, not complete-task interval. ExtA/ExtB are the two registered segments of the external Poseidon2 linear layer; IntA/IntB are the corresponding internal-layer segments, not separate hash algorithms.

BUILD writes N leaf digests to bank0, reads ordered left/right pairs synchronously from the source bank, and writes parents to the destination bank. After each layer drains, bank roles alternate until the root is reached. VERIFY combines a current digest with its next sibling in registers. LEAF_HASH reuses the leaf stage and streams leaf digests.

| Unit | Organization |
| --- | --- |
| Multiplier | One 64-bit Goldilocks pipeline, 11-cycle latency and II=1; 4 front-end multiplications of 32×32 |
| Entry and feedback | Two selection/constant-addition stages, 13-cycle feedback distance |
| Linear layers | Registered ExtA/ExtB and IntA/IntB using additions, shifts, and constant arithmetic |
| Scheduling | 16 interleaved permutation contexts within a tree layer |
| Memory | Two 256-bit SyncReadMem banks, each maxLeaves entries; 4 MiB in the typical configuration |
| Result queue | Two-record non-fall-through FIFO isolating external ready feedback |

One Merkle task is active at a time. The 16 contexts do not mean 16 concurrent trees; authentication paths depend on each preceding digest. Each permutation issues 472 useful modular multiplications, nine operations in each external linear unit, and 22 in each internal linear unit. The simulation environment counts useful work without adding product ports; see [Performance](performance.en.md).

Memory uses synchronous reads and one write call per bank, with no product SRAM initialization. ASIC flows can map behavioral memory to SRAM macros, and FPGA integrators can use their tools' memory inference. [Product SV](../rtl/v1.1.0/max65536-l8-ctx16/) includes only the top, permutation, multiplier, and two memory modules. MerkleSimTop, vector ROMs, and simulation checks belong to verification and are not product synthesis tops.
