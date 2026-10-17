<!--
OpenRTL-Header-Begin
OpenRTL — Open-source hardware accelerators.
Organization: https://github.com/OpenRTL-Lab
Website: https://openrtl.top/

Project: Goldilocks Merkle Hardware Accelerator
Chisel hardware for Poseidon2 leaf hashing, Merkle trees and authentication paths.
Repository: https://github.com/OpenRTL-Lab/merkle-goldilocks
File: docs/reproduction.en.md

SPDX-FileCopyrightText: 2026 Goldilocks Merkle contributors
Copyright applies to original project contributions only.
SPDX-License-Identifier: MIT
See LICENSE and applicable third-party notices.
OpenRTL-Header-End
-->

# Reproduction

[简体中文](reproduction.md)

This guide covers regenerating product SV, four-mode regression against independent references, and performance tests for all three task types; both regenerated and bundled SV are verified, including structural and cycle-result agreement.

## Obtain the release snapshot

[Release v1.1.0](https://github.com/OpenRTL-Lab/merkle-goldilocks/releases/tag/v1.1.0)

```bash
git clone --branch v1.1.0 --depth 1 https://github.com/OpenRTL-Lab/merkle-goldilocks.git
cd merkle-goldilocks
```

The version tag identifies the current source tree, including source notices and documentation. Release downloads provide hardware sources and bundled RTL for this version. Archive annotations and documentation can differ from the online tree. For RTL integration, use the product file list rather than verification environments; use the verification and synthesis documents for supported configurations and measurement conditions.

## Preparation

Install tools and configure PATH using [Toolchain](toolchain.en.md), then run commands from the repository root. Outputs must be new relative directories under build; scripts never overwrite existing evidence, so select a new --out for reruns. C-generated vectors require no internal release directory or synthesis tools.

`--max-leaves` sets hardware leaf capacity; `--n N` chooses leaves per test tree and `--width L` chooses field elements per leaf. N16/L8 means 16 leaves, eight elements each. `--task-mode 0/1/2` selects BUILD/VERIFY/LEAF_HASH. In contrast, `--modes` selects verification scenarios (0 is ideal timing), not the product mode. `--tasks` sets benchmark task count; `--no-suite` omits the default boundary suite, while `--deep-proof` adds the path variants even with `--tasks 0`. `--jobs` is compilation parallelism only.

## Default functional regression

The following command generates the typical maxLeaves=65536 product, runs 256 consecutive N16/L8 BUILD tasks plus boundary tests, and tests both regenerated and bundled products in ideal, protocol-stall, three-reset-recovery, and reference-negative-control modes, with cycle statistics:

```sh
python3 tools/verify.py --out build/verify --jobs 8
```

A status of zero in `results.json` means the verification passed. Normal modes require zero mismatches. The negative control deliberately flips one reference field and expects exactly one mismatch and simulator exit code 1, which the script recognizes as successful detection. Reset recovery retries cancelled tasks; checked counts can include outputs verified before reset.

C/Python cross-checks cover 27 twelve-element permutations, a known-answer vector, and 16 small-tree/leaf-width combinations. The default 443 tasks cover all three modes, N1/2/4/16, L0/1/7/8, erroneous proofs, invalid parameters, framing, and noncanonical elements. All references, vector generation, the environment, and driver are included.

## Typical-tree performance

The following command computes eight consecutive 65,536-leaf trees with eight elements per leaf, checks every internal digest, and independently measures seven completion intervals using ideal channels:

```sh
python3 tools/verify.py --out build/typical --jobs 8 --n 65536 --width 8 --tasks 8 --no-suite --modes 0
```

## Deep paths and leaf hashing

The following command tests 15 correct and incorrect 16-level path variants in every functional mode and measures path performance:

```sh
python3 tools/verify.py --out build/proof --jobs 8 --n 65536 --width 8 --tasks 0 --no-suite --deep-proof
```

The following command measures leaf-hashing throughput across 256 consecutive N16/L8 tasks:

```sh
python3 tools/verify.py --out build/leaf --jobs 8 --n 16 --width 8 --tasks 256 --no-suite --task-mode 2 --modes 0
```

## Generation only and product use

To generate SV and reference environments without compiling Verilator models, run:

```sh
python3 tools/emit_sv.py --out build/emit --max-leaves 65536
```

The generated `build/emit/rtl/product.f` lists the product closure; simulation.f is separate. The bundled product top is GoldilocksMerkle, with [Product file list](../rtl/v1.1.0/max65536-l8-ctx16/files.f) resolved from the repository root. Do not synthesize MerkleSimTop or vector ROMs as product hardware.

--max-leaves selects a power of two from 2 through 65536 and changes generated memory size. --n and --width choose runtime test inputs rather than hash parameters. Context count is fixed at 16; smaller generated products are not compared against the typical bundled SV. --jobs controls host compilation parallelism, not hardware.

## Reading results

`reference-results.json` records independent-reference checks, `configuration.json` records test configuration, and `results.json` includes each test mode, task latency, completion intervals, per-layer counts, and five compute-unit utilization measurements. Logs remain in the selected output directory. See [Performance](performance.en.md) for formulas and values; inspect the failed-stage log when troubleshooting.
