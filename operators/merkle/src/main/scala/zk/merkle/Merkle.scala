/*
 * OpenRTL-Header-Begin
 * OpenRTL — Open-source hardware accelerators.
 * Organization: https://github.com/OpenRTL-Lab
 * Website: https://openrtl.top/
 *
 * Project: Goldilocks Merkle Hardware Accelerator
 * Chisel hardware for Poseidon2 leaf hashing, Merkle trees and authentication paths.
 * Repository: https://github.com/OpenRTL-Lab/merkle-goldilocks
 * File: operators/merkle/src/main/scala/zk/merkle/Merkle.scala
 *
 * SPDX-FileCopyrightText: 2026 Goldilocks Merkle contributors
 * Copyright applies to original project contributions only.
 * SPDX-License-Identifier: MIT
 * See LICENSE and applicable third-party notices.
 * OpenRTL-Header-End
 */

package zk.merkle

import chisel3._
import chisel3.util._
import zk.poseidon2.{GoldilocksPoseidon2, PoseidonConfig, Parameters, Field}

case class MerkleConfig(maxLeaves: Int = 65536, contexts: Int = 16) {
  require(maxLeaves >= 2 && maxLeaves <= 65536 && (maxLeaves & (maxLeaves-1)) == 0)
  require(contexts == 16)
  val addressBits = log2Ceil(maxLeaves)
}
class MerkleTask extends Bundle {
  val tag = UInt(32.W); val mode = UInt(2.W)
  val leafCount = UInt(32.W); val leafWidth = UInt(4.W); val leafIndex = UInt(32.W)
  val expectedRoot = Vec(4, UInt(64.W))
}
class MerkleData extends Bundle {
  val word = UInt(64.W); val kind = Bool(); val last = Bool()
}
class MerkleResult extends Bundle {
  val tag = UInt(32.W); val level = UInt(5.W); val index = UInt(32.W)
  val digest = Vec(4, UInt(64.W)); val root = Bool(); val last = Bool()
}
class MerkleCompletion extends Bundle {
  val tag = UInt(32.W); val status = UInt(2.W); val matches = Bool()
  val rootDigest = Vec(4, UInt(64.W))
}

/** 双银行 1R1W 同步 SRAM；单树活跃，层内多上下文并发。 */
class GoldilocksMerkle(val cfg: MerkleConfig) extends Module {
  val io = IO(new Bundle {
    val task = Flipped(Decoupled(new MerkleTask))
    val data = Flipped(Decoupled(new MerkleData))
    val result = Decoupled(new MerkleResult)
    val completion = Decoupled(new MerkleCompletion)
  })
  // 产品边界不可按某个验证向量的常量专门化；保留完整原生端口及内部依赖。
  dontTouch(io)
  val idle :: prepare :: leaf :: parents :: proofInput :: proofWait :: finalResult :: finish :: Nil = Enum(8)
  val state = RegInit(idle)
  val task = Reg(new MerkleTask)
  val status = RegInit(0.U(2.W))
  val count = Reg(UInt(32.W)); val issued = RegInit(0.U(32.W)); val received = RegInit(0.U(32.W))
  val level = RegInit(0.U(5.W)); val height = Reg(UInt(5.W)); val proofLevel = RegInit(0.U(5.W))
  val frameSize = Reg(UInt(32.W)); val consumed = RegInit(0.U(32.W)); val fill = RegInit(0.U(4.W))
  val buffer = Reg(Vec(12, UInt(64.W)))
  val pending = RegInit(false.B)
  val sourceBank = RegInit(false.B)
  val root = RegInit(VecInit(Seq.fill(4)(0.U(64.W))))
  val bank0 = SyncReadMem(cfg.maxLeaves, UInt(256.W))
  val bank1 = SyncReadMem(cfg.maxLeaves, UInt(256.W))
  val hash = Module(new GoldilocksPoseidon2(PoseidonConfig(cfg.contexts)))
  hash.io.enable := true.B
  hash.io.request.valid := pending
  hash.io.request.bits.tag := issued
  hash.io.request.bits.words := buffer
  hash.io.response.ready := false.B
  io.task.ready := state === idle
  io.data.ready := (state === leaf && task.leafWidth =/= 0.U && !pending && issued < count) ||
    (state === proofInput && !pending)
  // 非穿透两记录FIFO：外部ready不得组合反馈到SRAM写使能或32位计数器。
  val results = Reg(Vec(2, new MerkleResult))
  val resultCount = RegInit(0.U(2.W))
  val resultRead = RegInit(false.B); val resultWrite = RegInit(false.B)
  val resultEnqueue = Wire(Decoupled(new MerkleResult))
  resultEnqueue.ready := resultCount =/= 2.U
  resultEnqueue.valid := false.B
  resultEnqueue.bits.tag := task.tag
  resultEnqueue.bits.level := level
  resultEnqueue.bits.index := received
  resultEnqueue.bits.digest := VecInit(hash.io.response.bits.words.take(4))
  resultEnqueue.bits.root := count === 1.U && task.mode =/= 2.U
  resultEnqueue.bits.last := received === count-1.U && (task.mode === 2.U || count === 1.U)
  io.result.valid := resultCount =/= 0.U
  io.result.bits := results(resultRead)
  when(resultEnqueue.fire) { results(resultWrite) := resultEnqueue.bits; resultWrite := !resultWrite }
  when(io.result.fire) { resultRead := !resultRead }
  when(resultEnqueue.fire =/= io.result.fire) {
    resultCount := Mux(resultEnqueue.fire, resultCount+1.U, resultCount-1.U)
  }
  io.completion.valid := state === finish && resultCount === 0.U
  io.completion.bits.tag := task.tag
  io.completion.bits.status := status
  io.completion.bits.rootDigest := root
  io.completion.bits.matches := task.mode === 1.U && status === 0.U && root.asUInt === task.expectedRoot.asUInt
  when(io.completion.fire) { state := idle }

  val readPhase = RegInit(0.U(3.W))
  val readEnable = state === parents && !pending && issued < count && (readPhase === 0.U || readPhase === 2.U)
  val readAddress = ((issued << 1) + Mux(readPhase === 2.U, 1.U, 0.U))(cfg.addressBits-1,0)
  val q0 = bank0.read(readAddress, readEnable && !sourceBank)
  val q1 = bank1.read(readAddress, readEnable && sourceBank)
  val q = Mux(sourceBank, q1, q0).asTypeOf(Vec(4,UInt(64.W)))
  // 每银行只能有一个write调用，否则Chisel会生成两个写端口，即使条件互斥。
  val writeBank1 = state === parents && !sourceBank
  when(hash.io.response.fire && task.mode =/= 1.U) {
    val data = VecInit(hash.io.response.bits.words.take(4)).asUInt
    when(writeBank1) { bank1.write(received(cfg.addressBits-1,0), data) }
    .otherwise { bank0.write(received(cfg.addressBits-1,0), data) }
  }
  when(state === parents && !pending && issued < count) {
    switch(readPhase) {
      is(0.U) { readPhase := 1.U }
      is(1.U) { for(i <- 0 until 4) { buffer(i) := q(i) }; readPhase := 2.U }
      is(2.U) { readPhase := 3.U }
      is(3.U) {
        for(i <- 0 until 4) { buffer(i+4) := q(i) }
        for(i <- 8 until 12) { buffer(i) := 0.U }
        pending := true.B; readPhase := 0.U
      }
    }
  }
  when(io.task.fire) {
    task := io.task.bits; status := 0.U; root := VecInit(Seq.fill(4)(0.U(64.W)))
    consumed := 0.U; fill := 0.U; pending := false.B; sourceBank := false.B
    level := 0.U; issued := 0.U; received := 0.U; proofLevel := 0.U; readPhase := 0.U
    state := prepare
  }
  when(state === prepare) {
    count := Mux(task.mode === 1.U, 1.U, task.leafCount)
    val h = PriorityEncoder(task.leafCount)
    height := h
    frameSize := Mux(task.mode === 1.U,
      task.leafWidth + (h << 2), task.leafCount * task.leafWidth)
    val n = task.leafCount
    val valid = n =/= 0.U && n <= cfg.maxLeaves.U && (n & (n-1.U)) === 0.U &&
      task.leafWidth <= 8.U && task.mode =/= 3.U &&
      (task.mode =/= 1.U || task.leafIndex < n)
    state := Mux(valid, leaf, finish)
    when(!valid) { status := 1.U }
    for(i <- 0 until 12) { buffer(i) := Mux(task.leafWidth === i.U, 1.U, 0.U) }
  }
  when(io.data.fire) {
    consumed := Field.plus(consumed, 1.U, 32)
    val noncanonical = io.data.bits.word >= Parameters.P.U(64.W)
    val expectedKind = state === proofInput
    when(noncanonical) { status := 3.U }
    .elsewhen((io.data.bits.kind =/= expectedKind || io.data.bits.last =/= (consumed === frameSize-1.U)) && status === 0.U) {
      status := 2.U
    }
    val canonical = Mux(noncanonical, 0.U, io.data.bits.word)
    when(state === leaf) {
      buffer(fill) := canonical
      when(fill === task.leafWidth-1.U) { pending := true.B; fill := 0.U }
      .otherwise { fill := fill+1.U }
    }.otherwise {
      val right = task.leafIndex(proofLevel)
      for(i <- 0 until 4) {
        when(fill === i.U) { when(right) { buffer(i) := canonical }.otherwise { buffer(i+4) := canonical } }
      }
      when(fill === 3.U) { pending := true.B; fill := 0.U; state := proofWait }
      .otherwise { fill := fill+1.U }
    }
  }
  when(state === leaf && task.leafWidth === 0.U && !pending && issued < count) { pending := true.B }
  when(hash.io.request.fire) {
    pending := false.B; issued := Field.plus(issued, 1.U, 32)
    when(state === leaf) {
      for(i <- 0 until 12) { buffer(i) := Mux(task.leafWidth === i.U, 1.U, 0.U) }
    }
  }
  val stream = state === parents || (state === leaf && (task.mode === 2.U || (task.mode === 0.U && count === 1.U)))
  when(stream) {
    resultEnqueue.valid := hash.io.response.valid
    hash.io.response.ready := resultEnqueue.ready
  }.elsewhen(state === leaf || state === proofWait) { hash.io.response.ready := true.B }
  when(hash.io.response.fire) {
    assert(hash.io.response.bits.tag === received, "hash response order")
    val digest = VecInit(hash.io.response.bits.words.take(4))
    received := Field.plus(received, 1.U, 32)
    when(task.mode === 1.U) {
      root := digest
      when((state === leaf && height === 0.U) || (state === proofWait && proofLevel === height-1.U)) {
        level := height; state := finalResult
      }.otherwise {
        val nextLevel = Mux(state === leaf, 0.U, proofLevel+1.U)
        proofLevel := nextLevel
        for(i <- 0 until 12) { buffer(i) := 0.U }
        for(i <- 0 until 4) {
          when(task.leafIndex(nextLevel)) { buffer(i+4) := digest(i) }
          .otherwise { buffer(i) := digest(i) }
        }
        issued := 0.U; received := 0.U; state := proofInput
      }
    }.otherwise {
      when(received === count-1.U) {
        when(task.mode === 2.U || count === 1.U) {
          when(task.mode =/= 2.U) { root := digest }
          state := finish
        }.otherwise {
          count := count >> 1; level := level+1.U
          issued := 0.U; received := 0.U; pending := false.B; readPhase := 0.U
          when(state === parents) { sourceBank := !sourceBank }
          state := parents
        }
      }
    }
  }
  when(state === finalResult) {
    resultEnqueue.valid := true.B; resultEnqueue.bits.digest := root
    resultEnqueue.bits.level := height; resultEnqueue.bits.index := 0.U
    resultEnqueue.bits.root := true.B; resultEnqueue.bits.last := true.B
    when(resultEnqueue.fire) { state := finish }
  }
  chisel3.layer.block(chisel3.layers.Verification) {
    val clocks = RegInit(0.U(64.W)); val requests = RegInit(0.U(64.W))
    val reads = RegInit(0.U(64.W)); val writes = RegInit(0.U(64.W))
    val occupied = RegInit(0.U(64.W))
    val inflight = issued =/= received
    val write0 = hash.io.response.fire && task.mode =/= 1.U && !writeBank1
    val write1 = hash.io.response.fire && task.mode =/= 1.U && writeBank1
    when(readEnable) { reads := reads+1.U }
    when(write0 || write1) { writes := writes+1.U }
    when(inflight) { occupied := occupied+1.U }
    when(io.task.fire || (hash.io.response.fire && received === count-1.U)) {
      clocks := 0.U; requests := 0.U; reads := 0.U; writes := 0.U; occupied := 0.U
    }
    .otherwise { when(state =/= idle && state =/= finish) { clocks := clocks+1.U } }
    when(hash.io.request.fire) { requests := requests+1.U }
    when(hash.io.response.fire && received === count-1.U) {
      val effectiveLevel = Mux(state === proofWait, proofLevel+1.U, level)
      printf(p"LAYER tag=${task.tag} level=${effectiveLevel} clocks=${clocks+1.U} hashes=${requests} reads=${reads} writes=${writes+(write0||write1)} occupied=${occupied+inflight}\n")
    }
  }
}
