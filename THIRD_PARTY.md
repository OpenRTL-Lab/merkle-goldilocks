<!--
OpenRTL-Header-Begin
OpenRTL — Open-source hardware accelerators.
Organization: https://github.com/OpenRTL-Lab
Website: https://openrtl.top/

Project: Goldilocks Merkle Hardware Accelerator
Chisel hardware for Poseidon2 leaf hashing, Merkle trees and authentication paths.
Repository: https://github.com/OpenRTL-Lab/merkle-goldilocks
File: THIRD_PARTY.md

SPDX-FileCopyrightText: 2026 Goldilocks Merkle contributors
Copyright applies to original project contributions only.
SPDX-License-Identifier: MIT
See LICENSE and applicable third-party notices.
OpenRTL-Header-End
-->

# 第三方声明

[English](THIRD_PARTY.en.md)

本项目采用MIT许可发布自主硬件及仿真代码，并保留Plonky3公开Poseidon2参数的来源和原始MIT许可；第三方参数、工具与专业综合环境分别遵循各自授权。

固定Goldilocks12元素置换轮常量、内部矩阵对角增量及已知答案来自[Plonky3](https://github.com/Plonky3/Plonky3)。其MIT OR Apache-2.0双许可中，本仓库选用MIT；原始版权与许可全文见[Plonky3 MIT许可证](operators/merkle/vendor/poseidon2/reference/LICENSE-MIT)。硬件流水线及Merkle封装为本项目实现，不声明为上游直接提供的硬件。

第三方参数原样包含在[固定参数](operators/merkle/vendor/poseidon2/reference/parameters.json)中，实际电路规则见[数学模型](docs/mathematical-model.md)。开源工具未随包分发，版本见[工具链说明](docs/toolchain.md)；TSMC与Synopsys相关内容只提供结果转述，见[公开边界](docs/publication-boundary.md)。
