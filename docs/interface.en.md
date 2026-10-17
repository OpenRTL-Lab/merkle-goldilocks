<!--
OpenRTL-Header-Begin
OpenRTL — Open-source hardware accelerators.
Organization: https://github.com/OpenRTL-Lab
Website: https://openrtl.top/

Project: Goldilocks Merkle Hardware Accelerator
Chisel hardware for Poseidon2 leaf hashing, Merkle trees and authentication paths.
Repository: https://github.com/OpenRTL-Lab/merkle-goldilocks
File: docs/interface.en.md

SPDX-FileCopyrightText: 2026 Goldilocks Merkle contributors
Copyright applies to original project contributions only.
SPDX-License-Identifier: MIT
See LICENSE and applicable third-party notices.
OpenRTL-Header-End
-->

# Interface

[简体中文](interface.md)

GoldilocksMerkle exposes one clock, synchronous active-high reset, and four native valid/ready channels—task, data, result, and completion—for three task modes with fixed input frames; this document defines fields, ordering, completion semantics, and error handling for integration.

Let N=leafCount=2^k and L=leafWidth (0..8 field elements per leaf). maxLeaves is generated capacity (a power of two, 2..65,536; bundled 65,536); runtime N may be 1. k is path depth, not leaf width. Each digest contains four canonical unsigned field words 0 <= x < p, p = 2^64 - 2^32 + 1. BUILD constructs one tree, VERIFY checks one path, and LEAF_HASH hashes N leaves without building parents.

## Channels and fields

| Channel | Direction | Fields |
| --- | --- | --- |
| task | Input | tag:32; mode:2; leafCount:32; leafWidth:4; leafIndex:32; expectedRoot:4×64 |
| data | Input | word:64; kind:1; last:1 |
| result | Output | tag:32; level:5; index:32; digest:4×64; root:1; last:1 |
| completion | Output | tag:32; status:2; matches:1; rootDigest:4×64 |

A transfer occurs at a rising edge with valid and ready both high. While valid is high and ready low, the sender holds valid and every payload field stable. One task is active at a time; the next task is accepted after the previous completion transfer. Tags are returned unchanged and are not hashed.

Mode 0 is BUILD, 1 VERIFY, 2 LEAF_HASH, and 3 invalid. leafCount is a power of two from 1 through maxLeaves; leafWidth is 0..8. VERIFY requires leafIndex<leafCount. Other modes ignore leafIndex and expectedRoot.

## Frames and results

BUILD/LEAF_HASH consume N×L words in ascending leaf and element order with kind=0; last is asserted only on the final word of the whole task. L=0 requires no data, while leaf encoding retains its termination marker.

VERIFY consumes L leaf words (kind=0), then k four-element sibling digests (kind=1), ordered from the lowest to highest level and coordinate 0 through 3. Only the final word of the L+4k-word frame has last=1. N=1 and L=0 require no data.

BUILD emits all internal nodes at levels 1..k in ascending index order; the final root has root=last=1. N=1 emits the sole level-zero leaf as root. LEAF_HASH emits N level-zero digests with root=0 and last=1 only on the final record. VERIFY emits only the computed root with level=k, index=0, and root=last=1.

Completion becomes valid after all result records have transferred. BUILD returns its root with matches=0. LEAF_HASH returns a zero root with matches=0. VERIFY returns its computed root and the single-bit matches result: 1 only when all four elements equal expectedRoot. See [Mathematical model](mathematical-model.en.md).

## Status and reset

| Status | Meaning | Consumption |
| --- | --- | --- |
| 0 | Normal completion | A nonmatching VERIFY proof uses matches=0, not an interface error |
| 1 | Invalid mode, size, width, or VERIFY index | No data or result transfers; root=0 |
| 2 | Incorrect kind or last | Consume and drain the predetermined frame; discard outputs based on completion |
| 3 | Noncanonical word≥p | Substitute 0 to drain the frame; discard outputs; takes priority over status 2 |

The task determines frame length; an early last cannot shorten it. Missing input leaves the circuit waiting for the sender; reset cancels a task. Synchronous reset clears in-flight validity/control state, not SRAM contents; a new task overwrites the memory range it uses. Integrators commit only status=0 results. A two-record non-fall-through result FIFO prevents a combinational path from external ready to SRAM write enable.
