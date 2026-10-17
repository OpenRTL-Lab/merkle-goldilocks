<!--
OpenRTL-Header-Begin
OpenRTL — Open-source hardware accelerators.
Organization: https://github.com/OpenRTL-Lab
Website: https://openrtl.top/

Project: Goldilocks Merkle Hardware Accelerator
Chisel hardware for Poseidon2 leaf hashing, Merkle trees and authentication paths.
Repository: https://github.com/OpenRTL-Lab/merkle-goldilocks
File: docs/publication-boundary.md

SPDX-FileCopyrightText: 2026 Goldilocks Merkle contributors
Copyright applies to original project contributions only.
SPDX-License-Identifier: MIT
See LICENSE and applicable third-party notices.
OpenRTL-Header-End
-->

# 公开边界

[English](publication-boundary.en.md)

本仓库公开可独立复现的Merkle硬件设计与仿真链路，并以文字表格提供已完成的专业综合数据；第三方受许可限制的综合环境和内部记录不属于公开载荷，用户可用自身工艺与工具继续集成。

公开内容包括Chisel、固定参数、产品SV、生成脚本、工具版本、C/Python参考、向量生成、Chisel测试环境、C++驱动、功能及性能验证入口、中英文说明和MIT许可。

综合信息只在[综合摘要](synthesis-summary.md)转述，包括工艺类别、角、DC版本、约束、setup、逻辑深度和面积。抱歉，由于第三方授权及保密限制，无法提供工艺库、SRAM宏模型、原始综合脚本、原始报告、映射网表、DDC或其他综合快照。

本仓库交付独立原生接口IP；不包含SoC、AXI等协议适配器、整套证明系统或上板工程。仿真向量初始化只属于测试环境，产品清单明确分离存储及计算模块。性能按照[性能指标](performance.md)的理想原生接口定义测量，具体系统条件由集成方评估。
