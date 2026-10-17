<!--
OpenRTL-Header-Begin
OpenRTL — Open-source hardware accelerators.
Organization: https://github.com/OpenRTL-Lab
Website: https://openrtl.top/

Project: Goldilocks Merkle Hardware Accelerator
Chisel hardware for Poseidon2 leaf hashing, Merkle trees and authentication paths.
Repository: https://github.com/OpenRTL-Lab/merkle-goldilocks
File: docs/toolchain.en.md

SPDX-FileCopyrightText: 2026 Goldilocks Merkle contributors
Copyright applies to original project contributions only.
SPDX-License-Identifier: MIT
See LICENSE and applicable third-party notices.
OpenRTL-Header-End
-->

# Toolchain

[简体中文](toolchain.md)

This document lists the open-source tool versions, dependency resolution, and optional environment variables used for standalone generation and simulation; the build fixes Scala, Chisel, and plugin versions so users can recreate the product and tests in their own environment.

| Tool | Version | Purpose |
| --- | --- | --- |
| Mill | 1.1.7 | Build and Chisel generation |
| Scala | 2.13.18 | Design language |
| Chisel and compiler plugin | 7.13.0 | Circuit description |
| CIRCT firtool | 1.149.0 | SV lowering and splitting |
| OpenJDK | 21.0.11 | JVM |
| Python | 3.12 | Vectors and performance scripts |
| Verilator | 5.050 | Lint and assertion-enabled RTL simulation |
| GCC/G++ | 16.2.0 | C reference and C++ model compilation |

Install these tools and make java, mill, python3, verilator, cc, and g++ available on PATH. Mill resolves Scala/Chisel dependencies from public Maven repositories; the first run requires network access or a user-prepared cache. Chisel resolves its matching public firtool release. Tool binaries and machine-specific launchers are not distributed.

MILL can specify a launcher command, for example `MILL='bash mill-launcher'`; CC selects the C compiler. Users choose cache locations and can set TMPDIR in restricted environments. Commands are in [Reproduction](reproduction.en.md).
