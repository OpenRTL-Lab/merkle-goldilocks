<!--
OpenRTL-Header-Begin
OpenRTL — Open-source hardware accelerators.
Organization: https://github.com/OpenRTL-Lab
Website: https://openrtl.top/

Project: Goldilocks Merkle Hardware Accelerator
Chisel hardware for Poseidon2 leaf hashing, Merkle trees and authentication paths.
Repository: https://github.com/OpenRTL-Lab/merkle-goldilocks
File: docs/synthesis-summary.en.md

SPDX-FileCopyrightText: 2026 Goldilocks Merkle contributors
Copyright applies to original project contributions only.
SPDX-License-Identifier: MIT
See LICENSE and applicable third-party notices.
OpenRTL-Header-End
-->

# Synthesis summary

[简体中文](synthesis-summary.md)

The typical GoldilocksMerkle configuration passed the Design Compiler setup gate at the TSMC 28 nm HPC+ worst corner, 500 MHz, and 10% clock uncertainty: positive WNS, zero TNS and violating endpoints, and 99.345% of counted paths strictly below 50 Z/ZN logic levels; this document reports synthesis conditions, mapped area, and path statistics.

L counts field elements per leaf; maxLeaves bounds leaf count. ctx16 means 16 interleaved states within one shared permutation core, not 16 trees. WNS is minimum setup slack and TNS sums negative setup slacks.

## Conditions

| Item | Condition |
| --- | --- |
| Top/configuration | GoldilocksMerkle; maxLeaves65536, L≤8, ctx16 |
| Tool | Synopsys Design Compiler NXT X-2025.06-SP3 |
| Process/memory | TSMC 28 nm HPC+ standard cells and SRAM macros |
| Worst corner | SSG, 0.81 V, −40 °C |
| Clock | Single 2.0 ns clock, 500 MHz |
| Setup uncertainty | 0.2 ns, 10% of period |
| Clock/input transition | 0.1 ns / 0.125 ns |
| Maximum data transition | 0.25 ns |
| I/O delay | Maximum 1.0 ns, minimum 0 ns |
| Output load | 0.02 pF |
| Maximum fanout | 32 |
| Synthesis wire-load model | ZeroWireload |
| Reset | Synchronous and included in timing checks |

## Setup and logic levels

| Metric | Result |
| --- | ---: |
| WNS | +0.000012517 ns |
| TNS | 0 ns |
| Setup-violating endpoints | 0 |
| Counted paths/constrained endpoints | 77,436 |
| Paths strictly below 50 levels | 76,929, or 99.3452658% |
| Exactly 50 levels | 104 |
| Above 50 levels | 403 |
| Maximum Z/ZN levels | 64 on 3 paths |

Coverage comprises 75,537 internal, 622 internal-to-output, and 1,277 input-to-internal paths. Counting uses the worst setup path to each constrained endpoint rather than enumerating every possible topological path. Only combinational Z or ZN output pins on the data path count as levels; register Q, SRAM outputs, clock paths, and report line counts do not substitute for logic depth. Setup and depth distribution are checked separately.

## Mapped area

| Mutually exclusive area class | µm² |
| --- | ---: |
| SRAM macros | 18,847,284.000 |
| Combinational logic excluding buffers/inverters | 71,954.541 |
| Buffers/inverters | 5,182.142 |
| Sequential cells | 54,641.958 |
| Total cell area | 18,979,062.641 |

Total mapped cell area is 18.979063 mm²; non-memory logic totals 0.131779 mm². The 512 1024×64 SRAM macros implement two 65,536×256 banks with 4 MiB capacity and represent 99.3057% of mapped cell area. These values sum macro and standard-cell areas without double-counting parent/child hierarchies; they are not defined as die area including routing and whitespace.

## Distribution

Published SV and sources correspond to this configuration, and the synthesis figures are our summary of a professional synthesis flow's outputs. We apologize that third-party licensing and confidentiality restrictions prevent distribution of process libraries, macro models, original DC Tcl, reports, or synthesis snapshots. RTL and simulation are independently reproducible; users supply their own synthesis environment. See [Publication boundary](publication-boundary.en.md).
