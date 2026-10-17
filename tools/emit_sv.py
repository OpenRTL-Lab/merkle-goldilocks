#!/usr/bin/env python3
# OpenRTL-Header-Begin
# OpenRTL — Open-source hardware accelerators.
# Organization: https://github.com/OpenRTL-Lab
# Website: https://openrtl.top/
#
# Project: Goldilocks Merkle Hardware Accelerator
# Chisel hardware for Poseidon2 leaf hashing, Merkle trees and authentication paths.
# Repository: https://github.com/OpenRTL-Lab/merkle-goldilocks
# File: tools/emit_sv.py
#
# SPDX-FileCopyrightText: 2026 Goldilocks Merkle contributors
# Copyright applies to original project contributions only.
# SPDX-License-Identifier: MIT
# See LICENSE and applicable third-party notices.
# OpenRTL-Header-End

"""生成独立Merkle产品、参考向量与仿真环境。"""
from source_headers import stamp_generated
import argparse
import ctypes
import json
import os
from pathlib import Path
import re
import shlex
import subprocess
import sys

ROOT=Path(__file__).resolve().parents[1]
OP=Path('operators/merkle')
BUNDLED=Path('rtl/v1.1.0/max65536-l8-ctx16')
sys.path.insert(0,str(ROOT/OP/'tools'))
from reference import cmodel,crosscheck,generate as generate_vectors

def relative_build(value):
    path=Path(value)
    if path.is_absolute() or '..' in path.parts or not path.parts or path.parts[0]!='build':
        raise ValueError('输出必须是build内的相对目录')
    return path

def run(command,output,label,expected=0):
    with (output/(label+'.log')).open('x') as log:
        result=subprocess.run([str(x) for x in command],cwd=ROOT,stdout=log,stderr=subprocess.STDOUT)
    if result.returncode!=expected:
        raise RuntimeError(f'{label}失败，检查本次输出目录对应日志')

def code(text):
    return re.sub(r'/\*.*?\*/|//[^\n]*','',text,flags=re.S)

def product_closure(rtl):
    stamp_generated(rtl)
    selected=[]
    for path in sorted(rtl.glob('*.sv')):
        text=re.sub(r'[ \t]*//[^\n]*\.scala:\d+:\d+[^\n]*','',path.read_text())
        path.write_text(text)
        if path.name in ('GoldilocksMerkle.sv','GoldilocksPoseidon2.sv','PoseidonGoldilocksMul.sv') or path.name.startswith(('bank0_','bank1_')):
            selected.append(path)
    if len(selected)!=5: raise ValueError('产品闭包应为3个计算模块及2个存储模块')
    for path in selected:
        clean=re.sub(r'"(?:[^"\\]|\\.)*"','""',code(path.read_text()))
        assert not re.search(r'\$readmem|\b(?:DSP48\w*|RAMB\w*|URAM\w*|BUFG\w*)\b',clean)
    return selected

def simulation_list(rtl,filelist,bundled=False):
    paths=sorted(rtl.rglob('*.sv'))
    if bundled:
        selected={p for p in rtl.glob('*.sv') if p.name in ('GoldilocksMerkle.sv','GoldilocksPoseidon2.sv','PoseidonGoldilocksMul.sv') or p.name.startswith(('bank0_','bank1_'))}
        paths=[p for p in paths if p not in selected]+sorted(BUNDLED.glob('*.sv'))
    filelist.write_text(''.join('+incdir+'+str(p)+'\n' for p in sorted({p.parent for p in paths}))+''.join(str(p)+'\n' for p in paths))

def generate(out,max_leaves=65536,n=16,width=8,tasks=256,suite=True,deep_proof=False,task_mode=0):
    os.chdir(ROOT)
    out=relative_build(out)
    if out.exists(): raise ValueError('输出目录已存在，换新目录，不自动覆盖结果')
    if not (2<=max_leaves<=65536 and max_leaves&(max_leaves-1)==0 and 1<=n<=max_leaves and n&(n-1)==0 and 0<=width<=8 and 0<=tasks<=4096):
        raise ValueError('检查尺寸、叶宽及任务数范围')
    if deep_proof and (n!=65536 or width!=8 or tasks!=0 or suite):
        raise ValueError('深路径测试需要N65536/L8、零前置任务且关闭suite')
    out.mkdir(parents=True)
    run([os.environ.get('CC','cc'),'-std=c11','-O2','-Wall','-Wextra','-Werror','-shared','-fPIC',OP/'cmodel/reference.c','-o',out/'reference.so'],out,'c-compile')
    lib=cmodel((out/'reference.so').resolve())
    reference=crosscheck(lib)
    vectors=out/'vectors'; vectors.mkdir()
    summary=generate_vectors(lib,vectors,n,width,tasks,suite,max_leaves,deep_proof,task_mode)
    (out/'reference-results.json').write_text(json.dumps(reference,indent=2)+'\n')
    rtl=out/'rtl'
    run(shlex.split(os.environ.get('MILL','mill'))+['-i','merkle.tool.runMain','zk.merkle.Emit',rtl,max_leaves,vectors],out,'emit')
    selected=product_closure(rtl)
    (rtl/'product.f').write_text(''.join(str(p)+'\n' for p in selected))
    simulation_list(rtl,rtl/'simulation.f')
    config=dict(version='1.1.0',max_leaves=max_leaves,contexts=16,n=n,width=width,tasks=tasks,suite=suite,deep_proof=deep_proof,task_mode=task_mode,vectors=summary)
    (out/'configuration.json').write_text(json.dumps(config,indent=2)+'\n')
    return rtl,config

def arguments(parser):
    parser.add_argument('--out',default='build/emit')
    parser.add_argument('--max-leaves',type=int,default=65536)
    parser.add_argument('--n',type=int,default=16)
    parser.add_argument('--width',type=int,default=8)
    parser.add_argument('--tasks',type=int,default=256)
    parser.add_argument('--no-suite',action='store_true')
    parser.add_argument('--deep-proof',action='store_true')
    parser.add_argument('--task-mode',type=int,choices=(0,2),default=0)

if __name__=='__main__':
    parser=argparse.ArgumentParser(description=__doc__); arguments(parser); a=parser.parse_args()
    generate(a.out,a.max_leaves,a.n,a.width,a.tasks,not a.no_suite,a.deep_proof,a.task_mode)
    print('参考核对和生成完成；产品闭包见本次rtl/product.f')
