/*
 * OpenRTL-Header-Begin
 * OpenRTL — Open-source hardware accelerators.
 * Organization: https://github.com/OpenRTL-Lab
 * Website: https://openrtl.top/
 *
 * Project: Goldilocks Merkle Hardware Accelerator
 * Chisel hardware for Poseidon2 leaf hashing, Merkle trees and authentication paths.
 * Repository: https://github.com/OpenRTL-Lab/merkle-goldilocks
 * File: operators/merkle/vendor/poseidon2/src/main/scala/zk/poseidon2/Arithmetic.scala
 *
 * SPDX-FileCopyrightText: 2026 Goldilocks Merkle contributors
 * Copyright applies to original project contributions only.
 * SPDX-License-Identifier: MIT
 * See LICENSE and applicable third-party notices.
 * OpenRTL-Header-End
 */

package zk.poseidon2

import chisel3._
import chisel3.util._


object PoseidonGoldilocksMul {
  val Latency = 11
}

/** Standard-cell-oriented arithmetic. Four 32x32 products, not FPGA DSP limbs.
  * Fixed eleven-cycle latency and II=1; physical frequency belongs to DC owner.
  */
class PoseidonGoldilocksMul extends Module {
  val io = IO(new Bundle {
    val a = Input(UInt(64.W))
    val b = Input(UInt(64.W))
    val product = Output(UInt(64.W))
  })
  private val p = BigInt("ffffffff00000001", 16).U(66.W)
  private val p0 = RegNext(io.a(31, 0) * io.b(31, 0))
  private val p1 = RegNext(io.a(31, 0) * io.b(63, 32))
  private val p2 = RegNext(io.a(63, 32) * io.b(31, 0))
  private val p3 = RegNext(io.a(63, 32) * io.b(63, 32))
  private val outerLow = RegNext(p0)
  private val cross = RegNext(Field.prefix(p1,p2))
  private val outerHigh = RegNext(p3)
  private val low = RegNext(Field.prefix(outerLow,Cat(cross(31, 0), 0.U(32.W))))
  private val crossHigh = RegNext(cross(64, 32))
  private val highDelayed = RegNext(outerHigh)
  private val productLow = RegNext(low(63, 0))
  private val productHigh = RegNext(Field.plus(Field.plus(Cat(0.U(1.W), highDelayed),
    Cat(0.U(32.W), crossHigh),65),low(64).asUInt,65))
  private val t0 = RegNext(productLow(31, 0))
  private val highSum = RegNext(Field.prefix(productLow(63, 32),productHigh(31, 0),n=32))
  private val subtrahend = RegNext(Field.prefix(productHigh(31, 0),productHigh(63, 32),n=32))
  private val positive = RegNext(Field.plus(Cat(0.U(1.W), highSum, t0),p,66))
  private val delayedSub = RegNext(subtrahend)
  private val candidate = RegNext(Field.minus(positive,Cat(0.U(33.W), delayedSub),66))
  private val candidateDelay = RegNext(candidate)
  private val negativeP = ((BigInt(1)<<66)-BigInt("ffffffff00000001",16)).U(66.W)
  private val firstSubtract = RegNext(Field.prefix(candidate,negativeP,n=66)(66))
  private val once = RegNext(Mux(firstSubtract, Field.plus(candidateDelay,negativeP,66), candidateDelay))
  private val onceDelay = RegNext(once)
  private val secondSubtract = RegNext(Field.prefix(once,negativeP,n=66)(66))
  private val twice = RegNext(Mux(secondSubtract, Field.plus(onceDelay,negativeP,66), onceDelay))
  io.product := twice(63, 0)
}
