<!--
OpenRTL-Header-Begin
OpenRTL — Open-source hardware accelerators.
Organization: https://github.com/OpenRTL-Lab
Website: https://openrtl.top/

Project: Goldilocks Merkle Hardware Accelerator
Chisel hardware for Poseidon2 leaf hashing, Merkle trees and authentication paths.
Repository: https://github.com/OpenRTL-Lab/merkle-goldilocks
File: docs/toolchain.md

SPDX-FileCopyrightText: 2026 Goldilocks Merkle contributors
Copyright applies to original project contributions only.
SPDX-License-Identifier: MIT
See LICENSE and applicable third-party notices.
OpenRTL-Header-End
-->

# 工具链说明

[English](toolchain.en.md)

本文件列出独立电路生成与仿真复现使用的开源工具版本、依赖解析方式和可选环境变量；构建文件固定Scala、Chisel及插件，读者可在自己的计算环境重建同一产品与测试。

| 工具 | 版本 | 用途 |
| --- | --- | --- |
| Mill | 1.1.7 | 构建与Chisel生成 |
| Scala | 2.13.18 | 设计语言 |
| Chisel及编译插件 | 7.13.0 | 电路描述 |
| CIRCT firtool | 1.149.0 | SV降级与拆分 |
| OpenJDK | 21.0.11 | JVM |
| Python | 3.12 | 向量和性能脚本 |
| Verilator | 5.050 | lint、断言RTL仿真 |
| GCC/G++ | 16.2.0 | C参考与C++模型编译 |

安装上述工具并使java、mill、python3、verilator、cc和g++可从PATH调用。Mill解析公开Maven中的Scala/Chisel依赖，首次运行需要可用网络或用户自己的缓存；Chisel解析其对应的公开firtool发行版本。工具二进制和机器专用启动器不随包分发。

可用MILL指定Mill启动命令，例如 `MILL='bash mill-launcher'`；CC指定C编译器。缓存位置由用户环境选择；受限环境可自行配置TMPDIR。生成和仿真命令见[快速复现](reproduction.md)。
