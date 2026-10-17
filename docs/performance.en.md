<!--
OpenRTL-Header-Begin
OpenRTL — Open-source hardware accelerators.
Organization: https://github.com/OpenRTL-Lab
Website: https://openrtl.top/

Project: Goldilocks Merkle Hardware Accelerator
Chisel hardware for Poseidon2 leaf hashing, Merkle trees and authentication paths.
Repository: https://github.com/OpenRTL-Lab/merkle-goldilocks
File: docs/performance.en.md

SPDX-FileCopyrightText: 2026 Goldilocks Merkle contributors
Copyright applies to original project contributions only.
SPDX-License-Identifier: MIT
See LICENSE and applicable third-party notices.
OpenRTL-Header-End
-->

# Performance

[简体中文](performance.md)

The configuration passes functional and cycle-performance tests for all three modes with ideal native interfaces: a typical 65,536-leaf tree takes 70,281,230 cycles, with a successive completion interval of 70,281,231 cycles, or approximately 7.114 trees/s at 500 MHz; this document independently defines latency, sustained throughput, and useful utilization with reproducible measurements.

N is leaves per tree, L is field elements per leaf, and ctx16 means 16 in-flight permutation states sharing one core within a task. BUILD measures complete trees, VERIFY measures paths, and LEAF_HASH measures batches of N leaf hashes. The 500 MHz conversions are not board or silicon measurements.

## Conditions and definitions

The product has maxLeaves=65536 and ctx16 with one clock. Input remains available when transferable; result and completion are always ready. Internal framing, synchronous memory waits, permutation feedback, and layer draining remain in the model. Only ideal-interface performance is measured, without scanning external bandwidth or latency; protocol stalls are functional tests, not performance samples. Host simulation time is not circuit latency.

Let A be the task-transfer cycle and C the completion-transfer cycle. Latency is $D=C-A$; inclusive counting gives D+1. For M successive tasks completing at $C_0,\ldots,C_{M-1}$, sustained throughput in tasks/cycle is:

$$
T=\frac{M-1}{C_{M-1}-C_0}.
$$

This uses an independently measured completion window, not the reciprocal of latency. At frequency f, latency in seconds is D/f and throughput is fT. BUILD handles one tree at a time; its 16 contexts operate within a layer. Each mode has its own task unit.

## Mode results

| Mode and input | Consecutive tasks | Latency/cycles | Completion interval/cycles | Tasks/cycle | At 500 MHz |
| --- | ---: | ---: | ---: | ---: | --- |
| BUILD N65536/L8 | 8 | 70,281,230 | 70,281,231 | 1.4228549867e-8 | 140.562460 ms; 7.114274934 trees/s |
| BUILD N16/L8 | 256 | 43,022 | 43,023 | 2.3243381447e-5 | 86.044 µs; 11,621.691 trees/s |
| VERIFY N65536/L8, 16-level path | 15 | 145,613 | 145,614 | 6.86747153433e-6 | 291.226 µs; 3,433.735767 paths/s |
| LEAF_HASH N16/L8 | 256 | 8,707 | 8,708 | 1.14836931557e-4 | 17.414 µs; 57,418.465779 tasks/s |

The large-tree test measures seven completion intervals. Each tree performs 65,536 leaf hashes and 65,535 parent compressions, or 131,071 permutations. Derived rates are 0.000932482244086 leaves/cycle and 0.00186495025962 permutations/cycle, approximately 466,241 leaves/s and 932,475 permutations/s at 500 MHz. Each LEAF_HASH task contains 16 leaves, so its task rate is not its single-leaf rate.

The 500 MHz conversion uses the product's DC setup-gate frequency. Seconds and per-second rates are calculated from measured cycles, not host elapsed time; see [Synthesis summary](synthesis-summary.en.md).

## Useful compute utilization

ExtA/ExtB name the two registered segments of the external linear layer; IntA/IntB name those of the internal linear layer. Each row counts valid launches at that segment, not a separate permutation core.

Useful work counts cycles accepting valid compute tokens, not switching of invalid data; idle cycles equal the task window minus active cycles. Simulation observes layer counters without adding product probe ports. Each permutation issues 472 multiplications, nine operations in each ExtA/B unit, and 22 in each IntA/B unit; the script checks these identities and memory accesses.

The first typical-tree window is 70,281,230 cycles:

| Unit | Active cycles | Idle cycles | Utilization |
| --- | ---: | ---: | ---: |
| Shared multiplier | 61,865,512 | 8,415,718 | 88.025654% |
| ExtA | 1,179,639 | 69,101,591 | 1.678455% |
| ExtB | 1,179,639 | 69,101,591 | 1.678455% |
| IntA | 2,883,562 | 67,397,668 | 4.102891% |
| IntB | 2,883,562 | 67,397,668 | 4.102891% |

Full large-tree layers achieve approximately 88.06% multiplier utilization; layer-tail draining is included in the task window. The first N16/L8 LEAF_HASH task achieves 86.734811%, and the first 16-level VERIFY task 5.510497%. VERIFY depends on the preceding digest at each level and is evaluated with its own path latency and throughput.

Layer zero hashes leaves; each subsequent layer halves the parent count. The typical tree performs 131,070 SRAM reads and 131,071 writes:

| Level | Permutations | Window cycles | Multiplier active | Utilization | SRAM reads | SRAM writes |
| --- | ---: | ---: | ---: | ---: | ---: | ---: |
| 0 | 65,536 | 35,127,425 | 30,932,992 | 88.059378% | 0 | 65,536 |
| 1 | 32,768 | 17,563,712 | 15,466,496 | 88.059381% | 65,536 | 32,768 |
| 2 | 16,384 | 8,781,888 | 7,733,248 | 88.059060% | 32,768 | 16,384 |
| 3 | 8,192 | 4,390,976 | 3,866,624 | 88.058418% | 16,384 | 8,192 |
| 4 | 4,096 | 2,195,520 | 1,933,312 | 88.057135% | 8,192 | 4,096 |
| 5 | 2,048 | 1,097,792 | 966,656 | 88.054568% | 4,096 | 2,048 |
| 6 | 1,024 | 548,928 | 483,328 | 88.049435% | 2,048 | 1,024 |
| 7 | 512 | 274,496 | 241,664 | 88.039170% | 1,024 | 512 |
| 8 | 256 | 137,280 | 120,832 | 88.018648% | 512 | 256 |
| 9 | 128 | 68,672 | 60,416 | 87.977633% | 256 | 128 |
| 10 | 64 | 34,368 | 30,208 | 87.895717% | 128 | 64 |
| 11 | 32 | 17,216 | 15,104 | 87.732342% | 64 | 32 |
| 12 | 16 | 8,640 | 7,552 | 87.407407% | 32 | 16 |
| 13 | 8 | 8,600 | 3,776 | 43.906977% | 16 | 8 |
| 14 | 4 | 8,580 | 1,888 | 22.004662% | 8 | 4 |
| 15 | 2 | 8,570 | 944 | 11.015169% | 4 | 2 |
| 16 | 1 | 8,565 | 472 | 5.510800% | 2 | 1 |

## Correctness and reruns

Eight typical trees check 2,097,120 64-bit output fields with zero mismatches, including every internal digest rather than roots alone. The default 443-task regression passes normal modes and three reset recoveries, and detects exactly one deliberately corrupted reference field. Fifteen deep-path tasks cover correct/incorrect roots, sibling changes, index changes, and sibling-level swaps. Consecutive leaf hashing checks 16,384 fields.

Run the four tests in [Reproduction](reproduction.en.md) and inspect `performance.task_latency_cycles`, `completion_intervals_cycles`, `task_rate_per_clock`, `unit_measurements`, and `layers` in results.json. Active/idle cycles for all five units at every layer can be regenerated. Reference checks and configuration have separate JSON files; machine-specific raw logs are not included in the distributed package.
