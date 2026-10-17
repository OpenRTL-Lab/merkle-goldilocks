# OpenRTL-Header-Begin
# OpenRTL — Open-source hardware accelerators.
# Organization: https://github.com/OpenRTL-Lab
# Website: https://openrtl.top/
#
# Project: Goldilocks Merkle Hardware Accelerator
# Chisel hardware for Poseidon2 leaf hashing, Merkle trees and authentication paths.
# Repository: https://github.com/OpenRTL-Lab/merkle-goldilocks
# File: operators/merkle/tools/reference.py
#
# SPDX-FileCopyrightText: 2026 Goldilocks Merkle contributors
# Copyright applies to original project contributions only.
# SPDX-License-Identifier: MIT
# See LICENSE and applicable third-party notices.
# OpenRTL-Header-End

"""独立大整数参考及 C 向量生成；只有仿真模型使用除余运算。"""
import ctypes,json,random,struct
from pathlib import Path
P=0xffffffff00000001
OP=Path(__file__).resolve().parents[1]
PARAM=json.loads((OP/'vendor/poseidon2/reference/parameters.json').read_text())
def numbers(key):return [int(x,16) for x in PARAM[key]]
EXT=numbers('GOLDILOCKS_POSEIDON2_RC_12_EXTERNAL_INITIAL')+numbers('GOLDILOCKS_POSEIDON2_RC_12_EXTERNAL_FINAL')
INT=numbers('GOLDILOCKS_POSEIDON2_RC_12_INTERNAL');DIAG=numbers('MATRIX_DIAG_12_GOLDILOCKS')
M=((2,3,1,1),(1,2,3,1),(1,1,2,3),(3,1,1,2))
def external(s):return [sum((2 if i//4==j//4 else 1)*M[i%4][j%4]*s[j] for j in range(12))%P for i in range(12)]
def permutation(s):
    s=external(s)
    for r in range(30):
        if r<4 or r>=26:
            row=r if r<4 else r-22
            s=external([pow((x+EXT[row*12+i])%P,7,P) for i,x in enumerate(s)])
        else:
            s[0]=pow((s[0]+INT[r-4])%P,7,P);total=sum(s)%P
            s=[(total+x*d)%P for x,d in zip(s,DIAG)]
    return s
def leaf(words):return permutation(list(words)+[1]+[0]*(11-len(words)))[:4]
def node(left,right):return permutation(list(left)+list(right)+[0]*4)[:4]
def cmodel(path):
    lib=ctypes.CDLL(str(path)); ptr=ctypes.POINTER(ctypes.c_uint64)
    lib.merkle_permutation.argtypes=[ptr];lib.merkle_leaf.argtypes=[ptr,ctypes.c_uint,ptr]
    lib.merkle_node.argtypes=[ptr,ptr,ptr];lib.merkle_tree.argtypes=[ptr,ctypes.c_uint,ctypes.c_uint,ptr]
    return lib
def array(values):return (ctypes.c_uint64*len(values))(*values)
def crosscheck(lib):
    rng=random.Random(20261011);checks=0
    assert permutation(list(range(12)))==numbers('kat')
    for s in [[0]*12,[P-1]*12,list(range(12))]+[[rng.randrange(P) for _ in range(12)] for _ in range(24)]:
        a=array(s);lib.merkle_permutation(a);assert list(a)==permutation(s);checks+=12
    for n in (1,2,4,16):
        for w in (0,1,7,8):
            inputs=[rng.randrange(P) for _ in range(n*w)]; tree=(ctypes.c_uint64*(4*(2*n-1)))()
            lib.merkle_tree(array(inputs),n,w,tree)
            layer=[leaf(inputs[i*w:(i+1)*w]) for i in range(n)];expected=sum(layer,[])
            while len(layer)>1:
                layer=[node(layer[i],layer[i+1]) for i in range(0,len(layer),2)];expected+=sum(layer,[])
            assert list(tree)==expected;checks+=len(expected)
    assert leaf(list(range(8)))!=node(list(range(4)),list(range(4,8)))
    return dict(pass_=True,checked_words=checks,kat=True,python_independent=True)

def generate(lib,out,n,width,tasks,suite,max_leaves,deep_proof=False,task_mode=0):
    rng=random.Random(0x09cafe);jobs=[]
    def add(mode,n,w,index,words,results,root,status=0,match=0,mutate=-1):
        number=len(jobs);prefix=out/f'job{number}'
        prefix.with_suffix('.input.bin').write_bytes(struct.pack('<'+'Q'*len(words),*words))
        prefix.with_suffix('.expected.bin').write_bytes(b''.join(struct.pack('<4I4Q',*r) for r in results))
        jobs.append([mode,n,w,index,status,match,mutate,len(words),len(results),*root])
    def make(n,w,seed=0):
        words=[(0 if seed==1 else P-1 if seed==2 else rng.randrange(P)) for _ in range(n*w)]
        t=(ctypes.c_uint64*(4*(2*n-1)))();lib.merkle_tree(array(words),n,w,t);return words,list(t)
    def records(t,n,mode):
        rs=[];offset=0 if mode==2 or n==1 else n;level=0 if offset==0 else 1;count=n if offset==0 else n//2
        while True:
            for i in range(count):rs.append([level,i,int(mode!=2 and count==1),int((mode==2 or count==1) and i==count-1),*t[4*(offset+i):4*(offset+i+1)]])
            if mode==2 or count==1:break
            offset+=count;count//=2;level+=1
        return rs
    for k in range(tasks):
        words,t=make(n,width,k%3);add(task_mode,n,width,0,words,records(t,n,task_mode),t[-4:] if task_mode==0 else [0]*4)
    if deep_proof:
        assert tasks==0 and n==65536 and width==8 and not suite
        words,t=make(n,width);level=n.bit_length()-1
        for index in (0,0x5555,n-1):
            proof=words[index*width:(index+1)*width];offset=0;count=n;ix=index
            while count>1:
                proof+=t[4*(offset+(ix^1)):4*(offset+(ix^1)+1)];offset+=count;count//=2;ix//=2
            add(1,n,width,index,proof,[[level,0,1,1,*t[-4:]]],t[-4:],match=1)
            wrong=t[-4:];wrong[0]^=1
            add(1,n,width,index,proof,[[level,0,1,1,*t[-4:]]],wrong)
            variants=[]
            damaged=proof[:];damaged[-1]^=1;variants.append((index,damaged))
            variants.append((index^(1<<(level-1)),proof))
            swapped=proof[:-8]+proof[-4:]+proof[-8:-4];variants.append((index,swapped))
            for ix,frame in variants:
                current=leaf(frame[:width])
                for l in range(level):
                    sibling=frame[width+4*l:width+4*l+4]
                    current=node(sibling,current) if (ix>>l)&1 else node(current,sibling)
                assert current!=t[-4:]
                add(1,n,width,ix,frame,[[level,0,1,1,*current]],t[-4:])
    if suite:
        for size in (1,2,4,16):
            if size>n:continue
            for w in (0,1,7,8):
                words,t=make(size,w)
                add(0,size,w,0,words,records(t,size,0),t[-4:])
                add(2,size,w,0,words,records(t,size,2),[0]*4)
                for index in sorted(set([0,size//2,size-1])):
                    proof=words[index*w:(index+1)*w];offset=0;count=size;ix=index
                    while count>1:
                        proof+=t[4*(offset+(ix^1)):4*(offset+(ix^1)+1)];offset+=count;count//=2;ix//=2
                    level=size.bit_length()-1;result=[[level,0,1,1,*t[-4:]]]
                    add(1,size,w,index,proof,result,t[-4:],match=1)
                    wrongroot=t[-4:];wrongroot[0]^=1
                    add(1,size,w,index,proof,result,wrongroot,match=0)
                    if size>1:
                        corrupted=proof[:];corrupted[w]^=1
                        current=leaf(corrupted[:w])
                        for l in range(level):
                            sib=corrupted[w+4*l:w+4*l+4]
                            current=node(sib,current) if (index>>l)&1 else node(current,sib)
                        add(1,size,w,index,corrupted,[[level,0,1,1,*current]],t[-4:],match=0)
                        if w:
                            current=leaf(proof[:w]);wrongindex=index^1
                            for l in range(level):
                                sib=proof[w+4*l:w+4*l+4]
                                current=node(sib,current) if (wrongindex>>l)&1 else node(current,sib)
                            assert current!=t[-4:]
                            add(1,size,w,wrongindex,proof,[[level,0,1,1,*current]],t[-4:],match=0)
                            if level>=2:
                                swapped=proof[:w]+proof[w+4:w+8]+proof[w:w+4]+proof[w+8:]
                                current=leaf(swapped[:w])
                                for l in range(level):
                                    sib=swapped[w+4*l:w+4*l+4]
                                    current=node(sib,current) if (index>>l)&1 else node(current,sib)
                                assert current!=t[-4:]
                                add(1,size,w,index,swapped,[[level,0,1,1,*current]],t[-4:],match=0)
        words,t=make(2,1)
        for mutation in (0,1,2):
            corrupted=words[:];status=2
            if mutation==2:corrupted[0]=P;status=3;good=words[:];good[0]=0;tt=(ctypes.c_uint64*12)();lib.merkle_tree(array(good),2,1,tt);t2=list(tt)
            else:t2=t
            add(0,2,1,0,corrupted,records(t2,2,0),t2[-4:],status=status,mutate=mutation)
        for mode,size,w,index in ((3,2,1,0),(0,0,1,0),(0,3,1,0),(0,max_leaves*2,1,0),(0,2,9,0),(1,2,1,2)):
            add(mode,size,w,index,[],[],[0]*4,status=1)
    (out/'jobs.txt').write_text('\n'.join(' '.join(str(x) for x in row) for row in jobs)+'\n')
    with (out/'input.hex').open('w') as ih,(out/'expected.hex').open('w') as eh:
        for number in range(len(jobs)):
            raw=(out/f'job{number}.input.bin').read_bytes()
            for (word,) in struct.iter_unpack('<Q',raw):ih.write(f'{word:016x}\n')
            raw=(out/f'job{number}.expected.bin').read_bytes()
            for offset in range(0,len(raw),48):eh.write(f'{int.from_bytes(raw[offset:offset+48],"little"):096x}\n')
        if not sum(row[7] for row in jobs):ih.write('0\n')
        if not sum(row[8] for row in jobs):eh.write('0\n')
    return dict(jobs=len(jobs),build_tasks=tasks,n=n,leaf_width=width)
