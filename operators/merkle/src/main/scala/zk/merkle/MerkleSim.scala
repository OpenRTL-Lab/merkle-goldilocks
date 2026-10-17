/*
 * OpenRTL-Header-Begin
 * OpenRTL — Open-source hardware accelerators.
 * Organization: https://github.com/OpenRTL-Lab
 * Website: https://openrtl.top/
 *
 * Project: Goldilocks Merkle Hardware Accelerator
 * Chisel hardware for Poseidon2 leaf hashing, Merkle trees and authentication paths.
 * Repository: https://github.com/OpenRTL-Lab/merkle-goldilocks
 * File: operators/merkle/src/main/scala/zk/merkle/MerkleSim.scala
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
import chisel3.util.experimental.loadMemoryFromFileInline
import java.nio.file.{Files,Paths}
import scala.jdk.CollectionConverters._

/** 仿真专用通道驱动、协议检查及C参考比较器，不进入产品闭包。 */
class MerkleSimTop(cfg: MerkleConfig,vectors: String) extends Module {
  val rows=Files.readAllLines(Paths.get(vectors,"jobs.txt")).asScala.map(_.split(" ").map(BigInt(_))).toSeq
  val count=rows.size;require(count>0)
  val inputStarts=rows.scanLeft(BigInt(0))((x,r)=>x+r(7)).dropRight(1)
  val outputStarts=rows.scanLeft(BigInt(0))((x,r)=>x+r(8)).dropRight(1)
  val io=IO(new Bundle {
    val start=Input(Bool());val mode=Input(UInt(2.W));val done=Output(Bool());val pass=Output(Bool())
    val checked=Output(UInt(64.W));val mismatches=Output(UInt(64.W));val cycles=Output(UInt(64.W));val resets=Output(UInt(2.W))
    // ENV保留完整ROM字宽，防止firtool压缩未使用的元数据填充位后改变readmem编码。
    val referenceRecord=Output(UInt(384.W))
  })
  val running=RegInit(false.B);val done=RegInit(false.B);val job=RegInit(0.U(log2Ceil(count+1).W))
  val accepted=RegInit(false.B);val position=RegInit(0.U(32.W));val outPosition=RegInit(0.U(32.W))
  val clocks=RegInit(0.U(64.W));val checked=RegInit(0.U(64.W));val mismatches=RegInit(0.U(64.W))
  val resets=RegInit(0.U(2.W));val resetThis=RegInit(false.B);val held=RegInit(false.B)
  val acceptAt=RegInit(0.U(64.W));val inputAt=RegInit(0.U(64.W));val firstAt=RegInit(0.U(64.W));val lastAt=RegInit(0.U(64.W))
  def col(i:Int,w:Int=32):UInt=VecInit(rows.map(_(i).U(w.W)))(job)
  val mode=col(0,2);val n=col(1);val width=col(2,4);val inputs=col(7);val outputs=col(8)
  val mutation=VecInit(rows.map(r=>(if(r(6)<0)255 else r(6).toInt).U(8.W)))(job)
  val inputStart=VecInit(inputStarts.map(_.U(32.W)))(job);val outStart=VecInit(outputStarts.map(_.U(32.W)))(job)
  val input=Mem(rows.map(_(7)).sum.toInt max 1,UInt(64.W))
  val expected=Mem(rows.map(_(8)).sum.toInt max 1,UInt(384.W))
  loadMemoryFromFileInline(input,Paths.get(vectors,"input.hex").toString)
  loadMemoryFromFileInline(expected,Paths.get(vectors,"expected.hex").toString)
  val abortAt=Mux(job===0.U,100.U,Mux(job===1.U,20000.U,40000.U))
  val abort=running && io.mode===2.U && job<3.U && accepted && !resetThis && clocks-acceptAt===abortAt
  val dut=withReset(reset.asBool || abort) { Module(new GoldilocksMerkle(cfg)) }
  io.done:=done;io.pass:=mismatches===0.U;io.checked:=checked;io.mismatches:=mismatches;io.cycles:=clocks;io.resets:=resets
  when(io.start && !running && !done) { running:=true.B }
  when(running) { clocks:=clocks+1.U }
  dut.io.task.valid:=running && !accepted && !abort
  dut.io.task.bits.tag:=job;dut.io.task.bits.mode:=mode;dut.io.task.bits.leafCount:=n
  dut.io.task.bits.leafWidth:=width;dut.io.task.bits.leafIndex:=col(3)
  for(i<-0 until 4) { dut.io.task.bits.expectedRoot(i):=col(9+i,64) }
  dut.io.data.valid:=running && accepted && position<inputs && !abort && (held || io.mode=/=1.U || clocks(2,0)=/=2.U)
  dut.io.data.bits.word:=input(inputStart+position)
  dut.io.data.bits.kind:=(mode===1.U && position>=width) ^ (mutation===1.U && position===0.U)
  dut.io.data.bits.last:=(position===inputs-1.U) ^ (mutation===0.U && position===0.U)
  dut.io.result.ready:=running && !abort && (io.mode=/=1.U || clocks(3,0)>=4.U)
  dut.io.completion.ready:=running && !abort && (io.mode=/=1.U || clocks(3,0)>=5.U)
  val record=expected(outStart+outPosition);val expectedRoot=Reg(Vec(4,UInt(64.W)));val errs=Wire(Vec(4,Bool()))
  io.referenceRecord:=record
  for(i<-0 until 4) {
    val word=record(191+64*i,128+64*i)
    val wanted=word ^ Mux(io.mode===3.U && job===0.U && outPosition===0.U && (i==0).B,1.U,0.U)
    errs(i):=dut.io.result.bits.digest(i)=/=wanted
  }
  when(dut.io.task.fire) { accepted:=true.B;acceptAt:=clocks }
  when(running && !abort) { held:=dut.io.data.valid && !dut.io.data.ready }
  when(dut.io.data.fire) { position:=position+1.U;inputAt:=clocks }
  when(dut.io.result.fire) {
    val r=dut.io.result.bits
    assert(outPosition<outputs,"extra output")
    assert(r.tag===job && r.level===record(31,0) && r.index===record(63,32) && r.root===record(64) && r.last===record(96),"result metadata")
    checked:=checked+4.U;mismatches:=mismatches+PopCount(errs)
    for(i<-0 until 4) { expectedRoot(i):=record(191+64*i,128+64*i) }
    when(outPosition===0.U) { firstAt:=clocks };lastAt:=clocks;outPosition:=outPosition+1.U
  }
  when(dut.io.completion.fire) {
    assert(dut.io.completion.bits.tag===job && dut.io.completion.bits.status===col(4,2) && dut.io.completion.bits.matches===col(5,1).asBool,"completion metadata")
    assert(position===inputs && outPosition===outputs,"frame accounting")
    for(i<-0 until 4) {
      val wanted=Mux(col(4,2)===1.U || mode===2.U,0.U,Mux(mode===1.U,expectedRoot(i),col(9+i,64)))
      assert(dut.io.completion.bits.rootDigest(i)===wanted,"completion root")
    }
    printf(p"TASK id=${job} mode=${mode} n=${n} width=${width} accept=${acceptAt} input=${inputAt} first=${firstAt} last=${lastAt} complete=${clocks} outputs=${outPosition} status=${col(4,2)} match=${col(5,1)}\n")
    when(job===(count-1).U) { running:=false.B;done:=true.B }.otherwise { job:=job+1.U }
    accepted:=false.B;position:=0.U;outPosition:=0.U;held:=false.B;resetThis:=false.B
    firstAt:=0.U;lastAt:=0.U;inputAt:=0.U
  }
  when(abort) {
    accepted:=false.B;position:=0.U;outPosition:=0.U;held:=false.B;resetThis:=true.B;resets:=resets+1.U
    firstAt:=0.U;lastAt:=0.U;inputAt:=0.U
  }
  def checkHold[T <: Data](valid:Bool,ready:Bool,bits:T):Unit={
    val stalled=RegNext(valid && !ready && !abort,false.B);val saved=RegEnable(bits.asUInt,valid && !ready)
    when(stalled && !abort) { assert(valid && bits.asUInt===saved,"output changed while stalled") }
  }
  checkHold(dut.io.result.valid,dut.io.result.ready,dut.io.result.bits)
  checkHold(dut.io.completion.valid,dut.io.completion.ready,dut.io.completion.bits)
}
