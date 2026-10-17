/*
 * OpenRTL-Header-Begin
 * OpenRTL — Open-source hardware accelerators.
 * Organization: https://github.com/OpenRTL-Lab
 * Website: https://openrtl.top/
 *
 * Project: Goldilocks Merkle Hardware Accelerator
 * Chisel hardware for Poseidon2 leaf hashing, Merkle trees and authentication paths.
 * Repository: https://github.com/OpenRTL-Lab/merkle-goldilocks
 * File: operators/merkle/sim/driver.cpp
 *
 * SPDX-FileCopyrightText: 2026 Goldilocks Merkle contributors
 * Copyright applies to original project contributions only.
 * SPDX-License-Identifier: MIT
 * See LICENSE and applicable third-party notices.
 * OpenRTL-Header-End
 */

#include "VMerkleSimTop.h"
#include "verilated.h"
#include <cstdio>
#include <memory>
#include <string>
int main(int argc,char**argv){
  if(argc!=2)return 2;unsigned mode=std::stoul(argv[1]);
  auto context=std::make_unique<VerilatedContext>();context->commandArgs(argc,argv);
  auto dut=std::make_unique<VMerkleSimTop>(context.get());
  auto tick=[&](){dut->clock=0;dut->eval();context->timeInc(1);dut->clock=1;dut->eval();context->timeInc(1);};
  dut->reset=1;dut->io_start=0;dut->io_mode=mode;for(unsigned i=0;i<4;i++)tick();
  dut->reset=0;dut->io_start=1;tick();dut->io_start=0;
  uint64_t timeout=UINT64_C(10000000000);while(!dut->io_done&&timeout--)tick();
  if(!dut->io_done)return 2;
  std::printf("RESULT pass=%u checked=%llu mismatches=%llu cycles=%llu reset_recovery=%u\n",
    dut->io_pass,(unsigned long long)dut->io_checked,(unsigned long long)dut->io_mismatches,
    (unsigned long long)dut->io_cycles,dut->io_resets);
  dut->final();return dut->io_pass?0:1;
}
