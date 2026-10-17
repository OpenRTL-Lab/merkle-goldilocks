#!/usr/bin/env python3
# OpenRTL-Header-Begin
# OpenRTL — Open-source hardware accelerators.
# Organization: https://github.com/OpenRTL-Lab
# Website: https://openrtl.top/
#
# Project: Goldilocks Merkle Hardware Accelerator
# Chisel hardware for Poseidon2 leaf hashing, Merkle trees and authentication paths.
# Repository: https://github.com/OpenRTL-Lab/merkle-goldilocks
# File: tools/verify.py
#
# SPDX-FileCopyrightText: 2026 Goldilocks Merkle contributors
# Copyright applies to original project contributions only.
# SPDX-License-Identifier: MIT
# See LICENSE and applicable third-party notices.
# OpenRTL-Header-End

"""独立参考、新生成与随包SV、协议/复位/负控及周期性能验证。"""
import argparse
import json
import os
from pathlib import Path
import re
from concurrent.futures import ThreadPoolExecutor

from emit_sv import ROOT,OP,BUNDLED,arguments,generate,run,code,product_closure,simulation_list

def rows(log,prefix):
    return [dict((k,int(v)) for k,v in re.findall(r'(\w+)=\s*(\d+)',line)) for line in log.splitlines() if line.startswith(prefix+' ')]

def metrics(log,tasks):
    records=rows(log,'TASK'); groups=rows(log,'HASH_GROUP'); layers=rows(log,'LAYER')
    assert len(groups)==len(layers)
    units=('mul','extA','extB','intA','intB')
    for l,g in zip(layers,groups):
        for u,c in zip(units,(472,9,9,22,22)): assert g[u]==l['hashes']*c
        l['units']={u:dict(active=g[u],idle=l['clocks']-g[u],busy_percent=100*g[u]/l['clocks']) for u in units}
    measured=[]
    for t in records:
        subset=[l for l in layers if l['tag']==t['id']]
        hashes=0 if t['status']==1 else 2*t['n']-1 if t['mode']==0 else t['n'] if t['mode']==2 else t['n'].bit_length()
        assert sum(l['hashes'] for l in subset)==hashes
        clocks=t['complete']-t['accept']
        active={u:sum(l['units'][u]['active'] for l in subset) for u in units}
        m=dict(id=t['id'],mode=t['mode'],cycles=clocks,hashes=hashes,reads=sum(l['reads'] for l in subset),writes=sum(l['writes'] for l in subset),units={u:dict(active=v,idle=clocks-v,busy_percent=100*v/clocks) for u,v in active.items()})
        assert m['reads']==(2*(t['n']-1) if t['mode']==0 and t['status']!=1 else 0)
        assert m['writes']==(hashes if t['mode'] in (0,2) and t['status']!=1 else 0)
        measured.append(m)
    steady=records[:tasks] if tasks else records
    intervals=[b['complete']-a['complete'] for a,b in zip(steady,steady[1:])]
    return dict(tasks=records,task_latency_cycles=[t['complete']-t['accept'] for t in steady],completion_intervals_cycles=intervals,task_rate_per_clock=(len(intervals)/sum(intervals) if intervals else None),layers=layers,unit_measurements=measured)

def verify_binary(rtl,out,jobs,modes,bundled=False):
    label='bundled' if bundled else 'generated'
    filelist=out/(label+'.f'); simulation_list(rtl,filelist,bundled)
    obj=out/(label+'-obj')
    run(['verilator','-O3','--assert','--cc','--exe','--build','-j',jobs,'-DENABLE_INITIAL_MEM_','--top-module','MerkleSimTop','-Mdir',obj.resolve(),'-CFLAGS','-O2 -std=c++17','-f',filelist,(ROOT/OP/'sim/driver.cpp')],out,label+'-compile')
    results={}
    for mode in modes:
        name=('ideal','protocol','reset','negative')[mode]
        run([obj/'VMerkleSimTop',mode],out,label+'-'+name,1 if mode==3 else 0)
        log=(out/(label+'-'+name+'.log')).read_text()
        result=rows(log,'RESULT'); assert len(result)==1
        result=result[0]
        assert result['mismatches']==(1 if mode==3 else 0)
        assert result['pass']==(0 if mode==3 else 1)
        if mode==2: assert result['reset_recovery']==3
        results[name]=result
    return results

def main():
    p=argparse.ArgumentParser(description=__doc__); arguments(p)
    p.set_defaults(out='build/verify')
    p.add_argument('--jobs',type=int,default=2)
    p.add_argument('--modes',default='0,1,2,3')
    a=p.parse_args(); modes=[int(m) for m in a.modes.split(',')]
    assert 1<=a.jobs<=64 and 0 in modes and all(m in (0,1,2,3) for m in modes)
    os.environ['CCACHE_DISABLE']='1'
    rtl,config=generate(a.out,a.max_leaves,a.n,a.width,a.tasks,not a.no_suite,a.deep_proof,a.task_mode)
    out=Path(a.out)
    report=dict(status=1,configuration=config,ideal_interfaces=True,dc_run=False)
    try:
        run(['verilator','--lint-only','--assert','--top-module','GoldilocksMerkle','-f',rtl/'product.f'],out,'generated-lint')
        if a.max_leaves==65536:
            originals={p.name:p for p in BUNDLED.glob('*.sv')}
            emitted={p.name:p for p in product_closure(rtl)}
            assert originals.keys()==emitted.keys(),'产品模块集合不同'
            for name in originals:
                assert re.sub(r'\s+','',code(originals[name].read_text()))==re.sub(r'\s+','',code(emitted[name].read_text())),'产品不同：'+name
            run(['verilator','--lint-only','--assert','--top-module','GoldilocksMerkle','-f',BUNDLED/'files.f'],out,'bundled-lint')
            # 两份模型只读相同向量，独立日志/编译目录；并行执行不改变电路周期。
            simulation_list(rtl,out/'generated.f')
            simulation_list(rtl,out/'bundled.f',True)
            with ThreadPoolExecutor(max_workers=2) as pool:
                new=pool.submit(verify_binary,rtl,out,max(1,a.jobs//2),modes)
                old=pool.submit(verify_binary,rtl,out,max(1,a.jobs//2),modes,True)
                generated=new.result();report['bundled_tests']=old.result()
            assert report['bundled_tests']==generated,'两份产品结果不同'
            assert (out/'generated-ideal.log').read_text()==(out/'bundled-ideal.log').read_text(),'两份产品周期不同'
            report['bundled_matches_generated']=True
        else:
            generated=verify_binary(rtl,out,a.jobs,modes)
        assert all(r['checked']==generated['ideal']['checked'] for name,r in generated.items() if name!='reset')
        assert len(rows((out/'generated-ideal.log').read_text(),'TASK'))==config['vectors']['jobs']
        report['generated_tests']=generated
        report['performance']=metrics((out/'generated-ideal.log').read_text(),a.tasks)
        report['status']=0
    finally:
        (out/'results.json').write_text(json.dumps(report,ensure_ascii=False,indent=2)+'\n')
    print(json.dumps({'status':0,'jobs':config['vectors']['jobs'],'checked':generated['ideal']['checked'],'latency_cycles':report['performance']['task_latency_cycles'][:3],'task_rate_per_clock':report['performance']['task_rate_per_clock']},ensure_ascii=False))

if __name__=='__main__': main()
