<!--
OpenRTL-Header-Begin
OpenRTL — Open-source hardware accelerators.
Organization: https://github.com/OpenRTL-Lab
Website: https://openrtl.top/

Project: Goldilocks Merkle Hardware Accelerator
Chisel hardware for Poseidon2 leaf hashing, Merkle trees and authentication paths.
Repository: https://github.com/OpenRTL-Lab/merkle-goldilocks
File: README.md

SPDX-FileCopyrightText: 2026 Goldilocks Merkle contributors
Copyright applies to original project contributions only.
SPDX-License-Identifier: MIT
See LICENSE and applicable third-party notices.
OpenRTL-Header-End
-->

# Goldilocks Poseidon2 Merkle Tree Hardware Accelerator

[简体中文](README.zh-CN.md)

This repository provides a standalone Goldilocks Merkle hardware accelerator developed in Chisel, combining leaf hashing, binary-tree construction, and authentication-path verification. It uses a 12-element Goldilocks Poseidon2 permutation to produce four-element digests and can be integrated into ASIC or FPGA designs through native interfaces for zero-knowledge commitments, authenticated data structures, and other Merkle computations using the same hashing rules.

It is licensed under the MIT License, permitting free commercial use, modification, and redistribution, provided the copyright and license notices are retained; see [Third-party notices](THIRD_PARTY.en.md) for the third-party parameters.

[Release v1.1.0](https://github.com/OpenRTL-Lab/merkle-goldilocks/releases/tag/v1.1.0) · [Interface](docs/interface.en.md) · [Reproduction](docs/reproduction.en.md) · [Performance](docs/performance.en.md) · [Synthesis summary](docs/synthesis-summary.en.md)

## Computation and task units

A digest contains four Goldilocks field elements, not four bytes. Each element is a canonical unsigned 64-bit value 0 <= x < p for p = 2^64 - 2^32 + 1. A task uses N = 2^k leaves and a common width L of 0 through 8 field elements per leaf. The 16 permutation contexts are stored in-flight states sharing one Poseidon2 core inside that task, not 16 concurrent trees.

| Mode | Task input and result |
| --- | --- |
| BUILD | Supply all N leaves; receive internal nodes level by level from level 1 up to the root, in increasing index order within each level. Leaf digests are not streamed in this mode; for N = 1, the sole leaf digest is emitted as the root. |
| VERIFY | Supply one leaf, its index, siblings from leaf to root, and the expected root; receive the computed root and a matches flag. A well-formed but nonmatching path completes with status 0 and matches = 0. |
| LEAF_HASH | Supply N leaves; receive their N leaf digests in order, without constructing a tree. |

Leaf hashing appends a field-element marker 1 and zeros to form a 12-element state, applies Poseidon2, then takes the first four elements. Parent compression instead permutes left digest, right digest, four zeros, with no leaf marker. Child order is not sorted; matching only the permutation parameters is insufficient if the target protocol uses different leaf encoding.

## At a glance

| Integration question | Released hardware |
| --- | --- |
| Computation | Binary Merkle tree construction, authentication-path verification and leaf hashing |
| Bundled configuration | Up to 65,536 leaves, 0..8 field elements per leaf, 16 permutation contexts |
| Product top | `GoldilocksMerkle`; shared fixed Goldilocks Poseidon2 permutation |
| Native boundary | Task, ordered leaf/sibling data, tagged level/index digest results and completion |
| Tree results | BUILD emits internal nodes; VERIFY emits a computed root; LEAF_HASH streams leaf digests |

## Quick start

Download the source archive from [Release v1.1.0](https://github.com/OpenRTL-Lab/merkle-goldilocks/releases/tag/v1.1.0), or clone the current release snapshot:

```bash
git clone --branch v1.1.0 --depth 1 https://github.com/OpenRTL-Lab/merkle-goldilocks.git
cd merkle-goldilocks
```

Install the exact prerequisites in [Toolchain](docs/toolchain.en.md), then run from the repository root with a fresh output directory:

```bash
python3 tools/emit_sv.py --out build/emit --max-leaves 65536
```

This emission command is not a complete RTL regression. The documented verification entry is:

```bash
python3 tools/verify.py --out build/verify --jobs 8
```

Follow [Reproduction](docs/reproduction.en.md) for expected results, negative-control interpretation and the distinction between short functional and full performance runs.

## Verification and integration boundary

The default 443-task regression covers all three modes, small trees, empty/boundary leaf widths, incorrect proofs, malformed frames and canonical-field checks, on generated and bundled products. Independent C/Python checks and an intentional reference fault are retained. Separate commands check every internal digest of eight full 65,536-leaf trees and 15 deep authentication-path variants; these are not implied by the default short-tree workload.

Leaf encoding, padding, child order and Poseidon2 parameters must match the target protocol exactly. A valid VERIFY frame with a nonmatching root reports matches=0 rather than an interface error. The 16 contexts accelerate permutations inside one task, not 16 simultaneous trees. Commit outputs only after a successful completion; storage, proof selection, challenge scheduling and a complete STARK prover remain external.

### Reading performance correctly

With ideal native interfaces, BUILD of 65,536 leaves with eight elements each takes 70,281,230 cycles; successive complete trees finish 70,281,231 cycles apart, or approximately 7.114 trees/s at an assumed 500 MHz. Shared-multiplier useful-issue utilization is 61,865,512 issue cycles / 70,281,230 task-window cycles = 88.026%. A tree rate, a leaf rate and a permutation rate count different units; VERIFY and LEAF_HASH must use their own workloads.

These are documented RTL cycle measurements under ideal native interfaces. MHz-based time/rate conversions are not board or silicon measurements. The reported Design Compiler results apply only to the documented configuration, library corner and constraints; they do not establish place-and-route signoff or system throughput. See [Performance](docs/performance.en.md) and [Synthesis summary](docs/synthesis-summary.en.md) for conditions.

## Repository contents

| Contents | Entry |
| --- | --- |
| Chisel design and simulation environment | [Design sources](operators/merkle/src/main/scala/zk/merkle/) |
| Matching Poseidon2 core and fixed parameters | [Permutation sources](operators/merkle/vendor/poseidon2/) |
| Generated product SV | [Product SV](rtl/v1.1.0/max65536-l8-ctx16/) |
| SV generation, functional and performance verification | [Generation script](tools/emit_sv.py), [Verification script](tools/verify.py) |
| C reference, independent Python reference, C++ driver | [C reference](operators/merkle/cmodel/), [Python reference](operators/merkle/tools/reference.py), [Simulation driver](operators/merkle/sim/) |
| Native channels and memory organization | [Interface](docs/interface.en.md), [Architecture](docs/architecture.en.md) |
| Open-source toolchain versions | [Toolchain](docs/toolchain.en.md) |

## Applications

The accelerator computes leaf digests of Goldilocks data, constructs binary Merkle commitment trees, and verifies a root from a leaf, sibling digests, and an index. Examples include STARK/FRI commitments and query authentication using the matching encoding and compression rules, as well as other authenticated structures with four-field-element digests. Users can combine multiple instances to fit their protocols; these examples do not restrict its uses. Integration requirements are described in [Applications](docs/applications.en.md).

## Performance

With ideal native interfaces, a 65,536-leaf tree with eight elements per leaf has a latency of 70,281,230 cycles and a successive-tree completion interval of 70,281,231 cycles, corresponding to approximately 7.114 trees/s at 500 MHz; useful modular-multiplier utilization is 88.026%. Definitions, measurement methods, and results for all three modes are in [Performance](docs/performance.en.md).

## Mathematical model

The circuit uses fixed Goldilocks Poseidon2 parameters, a termination marker for leaf encoding, and ordered left/right node compression. Exact BUILD, VERIFY, and LEAF_HASH definitions are in [Mathematical model](docs/mathematical-model.en.md).

## Reproduction

The open-source flow regenerates SV, checks results against C/Python references, and runs functional, protocol, reset, and performance tests on both regenerated and bundled products. Installation and commands are in [Reproduction](docs/reproduction.en.md).

## Synthesis results

This configuration passed the DC setup gate at the TSMC 28 nm HPC+ worst corner, 500 MHz, and 10% clock uncertainty: TNS is zero, and 99.345% of counted worst setup paths per endpoint have strictly fewer than 50 Z/ZN logic levels. A level here counts a combinational data-path output pin named Z or ZN, excluding register Q, SRAM outputs, clocks and nets. Conditions, area, and counting methods are in [Synthesis summary](docs/synthesis-summary.en.md).

## Publication boundary

Published materials cover the RTL design, generation tools, complete simulation flow, and a written synthesis summary. We apologize that third-party licensing and confidentiality restrictions prevent us from distributing process libraries, SRAM macro models, original DC scripts and reports, or synthesis snapshots. This repository delivers native-interface hardware rather than a SoC, protocol-system adapter, or board project. See [Publication boundary](docs/publication-boundary.en.md).

## OpenRTL and custom integration

Browse [OpenRTL](https://github.com/OpenRTL-Lab), the [ZK accelerator catalogue](https://github.com/OpenRTL-Lab/zk-accelerators) and [OpenRTL website](https://openrtl.top/) for related modules and the cooperation/contact entry. Custom datapaths, interfaces and target-platform adaptation are optional services; MIT commercial use does not require buying a service. Do not submit confidential specifications in public issues.

## Release sources and integration

The version tag identifies the current source tree, including source notices and documentation. Release downloads provide hardware sources and bundled RTL for this version. Archive annotations and documentation can differ from the online tree. For RTL integration, use the product file list rather than verification environments; use the verification and synthesis documents for supported configurations and measurement conditions.
