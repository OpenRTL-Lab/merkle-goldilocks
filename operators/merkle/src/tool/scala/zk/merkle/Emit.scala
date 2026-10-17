/*
 * OpenRTL-Header-Begin
 * OpenRTL — Open-source hardware accelerators.
 * Organization: https://github.com/OpenRTL-Lab
 * Website: https://openrtl.top/
 *
 * Project: Goldilocks Merkle Hardware Accelerator
 * Chisel hardware for Poseidon2 leaf hashing, Merkle trees and authentication paths.
 * Repository: https://github.com/OpenRTL-Lab/merkle-goldilocks
 * File: operators/merkle/src/tool/scala/zk/merkle/Emit.scala
 *
 * SPDX-FileCopyrightText: 2026 Goldilocks Merkle contributors
 * Copyright applies to original project contributions only.
 * SPDX-License-Identifier: MIT
 * See LICENSE and applicable third-party notices.
 * OpenRTL-Header-End
 */

package zk.merkle
import circt.stage.ChiselStage
import java.nio.file.{Files, Paths}
object Emit extends App {
  require(args.length == 3)
  val path = Paths.get(args(0)).normalize()
  require(!path.isAbsolute && path.startsWith(Paths.get("build")) && !Files.exists(path))
  Files.createDirectories(path)
  ChiselStage.emitSystemVerilog(new MerkleSimTop(MerkleConfig(args(1).toInt),args(2)),
    firtoolOpts=Array("-O=release", "-disable-all-randomization", "--lowering-options=disallowLocalVariables",
      "-split-verilog", s"-o=$path"))
}
