<!--
OpenRTL-Header-Begin
OpenRTL — Open-source hardware accelerators.
Organization: https://github.com/OpenRTL-Lab
Website: https://openrtl.top/

Project: Goldilocks Merkle Hardware Accelerator
Chisel hardware for Poseidon2 leaf hashing, Merkle trees and authentication paths.
Repository: https://github.com/OpenRTL-Lab/merkle-goldilocks
File: docs/applications.en.md

SPDX-FileCopyrightText: 2026 Goldilocks Merkle contributors
Copyright applies to original project contributions only.
SPDX-License-Identifier: MIT
See LICENSE and applicable third-party notices.
OpenRTL-Header-End
-->

# Applications

[简体中文](applications.md)

This accelerator combines Goldilocks leaf hashing, binary Merkle-tree construction, and authentication-path verification into a native-interface IP, allowing hardware to handle commitment construction and query authentication in zero-knowledge proofs and other authenticated structures using the same hashing rules.

BUILD constructs one tree, VERIFY checks one supplied authentication path, and LEAF_HASH returns only leaf digests. A digest is four 64-bit Goldilocks field elements, not four bytes; matching leaf encoding and child order are required.

| Application | Hardware operation | System integration |
| --- | --- | --- |
| STARK/FRI polynomial and trace commitments | BUILD on ordered leaf data | Define leaf ordering, extension-field encoding, and commitment protocol |
| Proof queries and authentication paths | VERIFY using leaf values, index, and siblings | Extract proof fields and supply the expected root |
| Batched leaf digests and preprocessing | LEAF_HASH with independent digest outputs | Schedule jobs, collect digests, and compose higher-level operations |
| Authenticated databases and other binary trees | Construct roots/internal nodes and verify paths | Manage versions, path storage, and updates |

These are integration examples rather than an exhaustive list. A protocol must use the matching 12-element permutation parameters, four-element digests, leaf termination marker, and ordered parent compression; the name “Poseidon2” alone does not imply interchangeability. See [Mathematical model](mathematical-model.en.md) and [Interface](interface.en.md).

BUILD supplies internal nodes and the root; LEAF_HASH supplies leaf digests. Users can retain these outputs to construct paths. VERIFY consumes an existing path rather than searching for or generating a proof. Wider-leaf encodings, multi-tree composition, transcript domain separation, and other protocol operations are composed by the integrating system.
