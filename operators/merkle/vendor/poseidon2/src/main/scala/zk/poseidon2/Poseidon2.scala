/*
 * OpenRTL-Header-Begin
 * OpenRTL — Open-source hardware accelerators.
 * Organization: https://github.com/OpenRTL-Lab
 * Website: https://openrtl.top/
 *
 * Project: Goldilocks Merkle Hardware Accelerator
 * Chisel hardware for Poseidon2 leaf hashing, Merkle trees and authentication paths.
 * Repository: https://github.com/OpenRTL-Lab/merkle-goldilocks
 * File: operators/merkle/vendor/poseidon2/src/main/scala/zk/poseidon2/Poseidon2.scala
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

case class PoseidonConfig(contexts: Int = 16) {
  require(Set(16, 32).contains(contexts))
  val indexBits = log2Ceil(contexts)
}
class NativeState extends Bundle {
  val tag = UInt(32.W)
  val words = Vec(12, UInt(64.W))
}
object Field {
  // 显式Kogge–Stone布尔网络，返回carry和sum。禁止串行宽进位链。
  def prefix(a: UInt, b: UInt, cin: Bool = false.B, n: Int = 64): UInt = {
    require(n > 0)
    val original = width(a,n) ^ width(b,n)
    var prop = original
    var gen = width(a,n) & width(b,n)
    for (level <- 0 until log2Ceil(n)) {
      val distance = 1 << level
      val pp = prop; val gg = gen
      // 向量位运算表达同一布尔网络，移位是编译期常量连线，不是桶形移位器。
      gen = gg | (pp & (gg << distance)(n-1,0))
      prop = (pp & (pp << distance)(n-1,0)) | (pp & ((BigInt(1)<<distance)-1).U(n.W))
    }
    val carry = gen | (prop & Fill(n,cin))
    Cat(carry(n-1), original ^ Cat(carry(n-2,0),cin))
  }
  def width(a: UInt,n: Int): UInt = { val w = Wire(UInt(n.W)); w := a; w }
  def plus(a: UInt,b: UInt,n: Int): UInt = prefix(width(a,n),width(b,n),n=n)(n-1,0)
  def minus(a: UInt,b: UInt,n: Int): UInt = prefix(width(a,n),~width(b,n),true.B,n)(n-1,0)
  def add(a: UInt, b: UInt): UInt = {
    val s = prefix(a,b)
    val d = prefix(s, ((BigInt(1)<<65)-Parameters.P).U(65.W),n=65)
    Mux(d(65), d(63,0), s(63,0))
  }
  def neg(a: UInt): UInt = Mux(a === 0.U, 0.U(64.W),minus(Parameters.P.U(64.W),a,64))
  def half(a: UInt): UInt = {
    val s = prefix(Cat(0.U(1.W), a),Mux(a(0),Parameters.P.U(65.W),0.U(65.W)),n=65)
    s(64, 1)
  }
  def sum(xs: Seq[UInt]): UInt =
    if (xs.size == 1) xs.head else sum(xs.grouped(2).map(x =>
      if(x.size == 2) add(x(0),x(1)) else x.head).toSeq)
  def mat4(x: Seq[UInt]): Seq[UInt] = {
    val a = add(x(0),x(1)); val b = add(x(2),x(3))
    val s = add(a,b); val u = add(s,x(1)); val v = add(s,x(3))
    Seq(add(u,a),add(u,add(x(2),x(2))),add(v,b),add(v,add(x(0),x(0))))
  }
  def diagonal(x: Seq[UInt]): Seq[UInt] = Seq(
    neg(add(x(0),x(0))),x(1),add(x(2),x(2)),half(x(3)),
    add(add(x(4),x(4)),x(4)),add(add(x(5),x(5)),add(x(5),x(5))),
    neg(half(x(6))),neg(add(add(x(7),x(7)),x(7))),
    neg(add(add(x(8),x(8)),add(x(8),x(8)))),
    half(half(x(9))),neg(half(half(x(10)))),half(half(half(x(11)))))
}

class GoldilocksPoseidon2(val cfg: PoseidonConfig) extends Module {
  val io = IO(new Bundle {
    val enable = Input(Bool())
    val request = Flipped(Decoupled(new NativeState))
    val response = Decoupled(new NativeState)
    val busy = Output(Bool())
  })
  val Idle = 0.U(4.W); val ExtA = 1.U(4.W); val ExtB = 2.U(4.W)
  val Square = 3.U(4.W); val Cube = 4.U(4.W); val Fourth = 5.U(4.W)
  val Seventh = 6.U(4.W); val IntA = 7.U(4.W); val IntB = 8.U(4.W)
  val Done = 9.U(4.W)
  val slot = RegInit(0.U(cfg.indexBits.W))
  val phase = RegInit(VecInit(Seq.fill(cfg.contexts)(Idle)))
  val states = Reg(Vec(cfg.contexts, Vec(12, UInt(64.W))))
  val tags = Reg(Vec(cfg.contexts, UInt(32.W)))
  val rounds = Reg(Vec(cfg.contexts, UInt(5.W)))
  val word = Reg(Vec(cfg.contexts, UInt(4.W)))
  val initial = Reg(Vec(cfg.contexts, Bool()))
  val base = Reg(Vec(cfg.contexts, UInt(64.W)))
  val x2 = Reg(Vec(cfg.contexts, UInt(64.W)))
  val x3 = Reg(Vec(cfg.contexts, UInt(64.W)))
  val x4 = Reg(Vec(cfg.contexts, UInt(64.W)))
  val total = Reg(Vec(cfg.contexts, UInt(64.W)))
  val st = states(slot)
  val ph = phase(slot)
  val full = rounds(slot) < 4.U || rounds(slot) >= 26.U
  val rc = VecInit(Parameters.RC.map(row => VecInit(row.map(_.U(64.W)))))
  val mul = Module(new PoseidonGoldilocksMul)
  val issue = ph >= Square && ph <= Seventh
  // 第一寄存边界仅选择上下文/常数；第二边界规范加轮常数并选择乘法操作数。
  val selectWord = RegNext(st(word(slot)))
  val selectRC = RegNext(rc(rounds(slot))(word(slot)))
  val selectX2 = RegNext(x2(slot)); val selectX3 = RegNext(x3(slot))
  val selectX4 = RegNext(x4(slot)); val selectBase = RegNext(base(slot))
  val selectPhase = RegNext(ph); val selectSlot = RegNext(slot)
  val selectValid = RegNext(issue, false.B)
  val z = Field.add(selectWord,selectRC)
  mul.io.a := RegNext(MuxLookup(selectPhase, 0.U(64.W))(Seq(Square -> z,
    Cube -> selectX2,Fourth -> selectX2,Seventh -> selectX3)))
  mul.io.b := RegNext(MuxLookup(selectPhase, 0.U(64.W))(Seq(Square -> z,
    Cube -> selectBase,Fourth -> selectX2,Seventh -> selectX4)))
  val feedbackLatency = PoseidonGoldilocksMul.Latency + 2
  require(feedbackLatency + 1 < cfg.contexts)
  val resultValid = ShiftRegister(issue, feedbackLatency, false.B, true.B)
  val resultSlot = ShiftRegister(slot, feedbackLatency)
  val resultWord = ShiftRegister(word(slot), feedbackLatency)
  val resultPhase = ShiftRegister(ph, feedbackLatency)
  io.request.ready := io.enable && ph === Idle
  io.response.valid := ph === Done
  io.response.bits.tag := tags(slot)
  io.response.bits.words := st
  io.busy := phase.map(_ =/= Idle).reduce(_ || _)
  // 背压期间锁定响应及tag，其他在途乘法仍完成写回。
  when(!(io.response.valid && !io.response.ready)) { slot := slot + 1.U }
  // 所有线性路径先捕获选择结果，之后每寄存级至多一个规范模运算。
  def pipe(xs: Seq[UInt]): Seq[UInt] = RegNext(VecInit(xs)).toSeq
  val linearInput = pipe(st.toSeq)
  val capturedTotal = RegNext(total(slot))
  val m4 = linearInput.grouped(4).flatMap { x =>
    val one = pipe(Seq(Field.add(x(0),x(1)),Field.add(x(2),x(3)),
      Field.add(x(0),x(0)),Field.add(x(2),x(2)),x(1),x(3)))
    val two = pipe(Seq(Field.add(one(0),one(1))) ++ one)
    val three = pipe(Seq(Field.add(two(0),two(5)),Field.add(two(0),two(6)),
      two(1),two(2),two(3),two(4)))
    pipe(Seq(Field.add(three(0),three(2)),Field.add(three(0),three(5)),
      Field.add(three(1),three(3)),Field.add(three(1),three(4))))
  }.toSeq
  val extPairs = pipe((0 until 4).map(k => Field.add(linearInput(k),linearInput(4+k))) ++ linearInput)
  val extSums = pipe((0 until 4).map(k => Field.add(extPairs(k),extPairs(4+8+k))) ++ extPairs.drop(4))
  val extOut = pipe((0 until 12).map(i => Field.add(extSums(4+i),extSums(i%4))))
  var sumTree = linearInput
  for(stage <- 0 until 4) {
    sumTree = pipe(sumTree.grouped(2).map(v => if(v.size==2) Field.add(v(0),v(1)) else v.head).toSeq)
  }
  val diagOne = pipe((0 until 12).map(i => if(Set(3,6,9,10,11).contains(i)) Field.half(linearInput(i))
    else if(i==1) linearInput(i) else Field.add(linearInput(i),linearInput(i))) ++ linearInput)
  val diagTwo = pipe((0 until 12).map { i =>
    if(Set(0,6).contains(i)) Field.neg(diagOne(i))
    else if(Set(4,7).contains(i)) Field.add(diagOne(i),diagOne(12+i))
    else if(Set(5,8).contains(i)) Field.add(diagOne(i),diagOne(i))
    else if(Set(9,10,11).contains(i)) Field.half(diagOne(i)) else diagOne(i)
  })
  val diagThree = pipe((0 until 12).map(i => if(Set(7,8,10).contains(i)) Field.neg(diagTwo(i))
    else if(i==11) Field.half(diagTwo(i)) else diagTwo(i)))
  val alignedTotal = ShiftRegister(capturedTotal,3)
  val intOut = pipe(diagThree.map(v => Field.add(alignedTotal,v)))
  val linearSlot = ShiftRegister(slot,5)
  val extASlot = linearSlot; val extAValid = ShiftRegister(ph===ExtA,5,false.B,true.B)
  val extBSlot = ShiftRegister(slot,4); val extBValid = ShiftRegister(ph===ExtB,4,false.B,true.B)
  val intAValid = ShiftRegister(ph===IntA,5,false.B,true.B)
  val intBValid = ShiftRegister(ph===IntB,5,false.B,true.B)
  def advance(c: Int): Unit = {
    when(rounds(c) === 29.U) { phase(c) := Done }
    .otherwise { rounds(c) := rounds(c) + 1.U; word(c) := 0.U; phase(c) := Square }
  }
  for(c <- 0 until cfg.contexts) {
    when(slot === c.U) {
      switch(ph) {
        is(Idle) { when(io.request.fire) {
          for(i <- 0 until 12) {
            assert(io.request.bits.words(i) < Parameters.P.U(64.W))
            states(c)(i) := io.request.bits.words(i)
          }
          tags(c) := io.request.bits.tag; rounds(c) := 0.U; word(c) := 0.U
          initial(c) := true.B; phase(c) := ExtA
        }}
        is(ExtA) { phase(c) := ExtB }
        is(ExtB) {
          when(initial(c)) { initial(c) := false.B; phase(c) := Square }
          .otherwise { advance(c) }
        }
        is(Square) { phase(c) := Cube }
        is(Cube) { phase(c) := Fourth }
        is(Fourth) { phase(c) := Seventh }
        is(Seventh) {
          when(full && word(c) =/= 11.U) { word(c) := word(c) + 1.U; phase(c) := Square }
          .otherwise { phase(c) := Mux(full, ExtA, IntA) }
        }
        is(IntA) { phase(c) := IntB }
        is(IntB) { advance(c) }
        is(Done) { when(io.response.fire) { phase(c) := Idle } }
      }
    }
    when(selectValid && selectPhase === Square && selectSlot === c.U) { base(c) := z }
    when(extAValid && extASlot===c.U) { states(c) := VecInit(m4) }
    when(extBValid && extBSlot===c.U) { states(c) := VecInit(extOut) }
    when(intAValid && linearSlot===c.U) { total(c) := sumTree.head }
    when(intBValid && linearSlot===c.U) { states(c) := VecInit(intOut) }
    when(resultValid && resultSlot === c.U) {
      assert(slot =/= c.U, "feedback must precede context reuse")
      switch(resultPhase) {
        is(Square) { x2(c) := mul.io.product }
        is(Cube) { x3(c) := mul.io.product }
        is(Fourth) { x4(c) := mul.io.product }
        is(Seventh) { states(c)(resultWord) := mul.io.product }
      }
    }
  }
  // Merkle 只调整仿真统计：大树不逐次打印乘法。计数器属于可抽取验证层。
  chisel3.layer.block(chisel3.layers.Verification) {
    val active = RegInit(false.B)
    val clocks = RegInit(0.U(64.W)); val issues = RegInit(0.U(64.W))
    val ea = RegInit(0.U(64.W)); val eb = RegInit(0.U(64.W))
    val ia = RegInit(0.U(64.W)); val ib = RegInit(0.U(64.W))
    when(!active && io.request.fire) {
      active := true.B; clocks := 1.U; issues := 0.U
      ea := 0.U; eb := 0.U; ia := 0.U; ib := 0.U
    }.elsewhen(active) {
      clocks := clocks + 1.U
      when(issue) { issues := issues + 1.U }
      when(ph === ExtA) { ea := ea + 1.U }
      when(ph === ExtB) { eb := eb + 1.U }
      when(ph === IntA) { ia := ia + 1.U }
      when(ph === IntB) { ib := ib + 1.U }
      when(!io.busy && !io.request.fire) {
        printf(p"HASH_GROUP clocks=${clocks} mul=${issues} extA=${ea} extB=${eb} intA=${ia} intB=${ib}\n")
        active := false.B
      }
    }
  }
}
