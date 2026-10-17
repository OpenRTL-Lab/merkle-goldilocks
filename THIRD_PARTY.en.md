<!--
OpenRTL-Header-Begin
OpenRTL — Open-source hardware accelerators.
Organization: https://github.com/OpenRTL-Lab
Website: https://openrtl.top/

Project: Goldilocks Merkle Hardware Accelerator
Chisel hardware for Poseidon2 leaf hashing, Merkle trees and authentication paths.
Repository: https://github.com/OpenRTL-Lab/merkle-goldilocks
File: THIRD_PARTY.en.md

SPDX-FileCopyrightText: 2026 Goldilocks Merkle contributors
Copyright applies to original project contributions only.
SPDX-License-Identifier: MIT
See LICENSE and applicable third-party notices.
OpenRTL-Header-End
-->

# Third-party notices

[简体中文](THIRD_PARTY.md)

The project publishes its hardware and simulation code under MIT and preserves the source attribution and original MIT license for Plonky3's public Poseidon2 parameters; third-party parameters, tools, and professional synthesis environments remain subject to their respective licenses.

Fixed Goldilocks 12-element round constants, internal diagonal increments, and the known-answer vector originate from [Plonky3](https://github.com/Plonky3/Plonky3). This repository chooses MIT from its MIT OR Apache-2.0 dual license; original copyright and license text are in [Plonky3 MIT license](operators/merkle/vendor/poseidon2/reference/LICENSE-MIT). The hardware pipelines and Merkle wrapper are project implementations, not hardware directly supplied by upstream.

Third-party values are included unchanged in [Fixed parameters](operators/merkle/vendor/poseidon2/reference/parameters.json), and circuit rules are specified in [Mathematical model](docs/mathematical-model.en.md). Open-source tools are not distributed; see [Toolchain](docs/toolchain.en.md). TSMC and Synopsys-related information is provided only as a written results summary; see [Publication boundary](docs/publication-boundary.en.md).
