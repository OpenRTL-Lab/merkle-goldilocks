<!--
OpenRTL-Header-Begin
OpenRTL — Open-source hardware accelerators.
Organization: https://github.com/OpenRTL-Lab
Website: https://openrtl.top/

Project: Goldilocks Merkle Hardware Accelerator
Chisel hardware for Poseidon2 leaf hashing, Merkle trees and authentication paths.
Repository: https://github.com/OpenRTL-Lab/merkle-goldilocks
File: docs/publication-boundary.en.md

SPDX-FileCopyrightText: 2026 Goldilocks Merkle contributors
Copyright applies to original project contributions only.
SPDX-License-Identifier: MIT
See LICENSE and applicable third-party notices.
OpenRTL-Header-End
-->

# Publication boundary

[简体中文](publication-boundary.md)

This repository publishes an independently reproducible Merkle hardware design and simulation flow, with written tables summarizing completed professional synthesis results; licensed third-party synthesis environments and internal records are excluded, while users can continue integration with their own processes and tools.

Published materials include Chisel, fixed parameters, product SV, generation scripts, tool versions, C/Python references, vector generation, the Chisel test environment, C++ driver, functional/performance verification, bilingual documentation, and the MIT license.

[Synthesis summary](synthesis-summary.en.md) reports process category, corner, DC version, constraints, setup results, logic depth, and area. We apologize that third-party licensing and confidentiality restrictions prevent distribution of process libraries, SRAM macro models, original synthesis scripts and reports, mapped netlists, DDC, or other synthesis snapshots.

The deliverable is a standalone native-interface IP, not a SoC, an AXI or other protocol adapter, an entire proof system, or a board project. Vector initialization belongs only to the test environment; the product file list separately identifies memory and computation modules. Performance uses the ideal native-interface definitions in [Performance](performance.en.md); integrators evaluate their system conditions.
