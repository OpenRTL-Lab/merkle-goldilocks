/*
 * OpenRTL-Header-Begin
 * OpenRTL — Open-source hardware accelerators.
 * Organization: https://github.com/OpenRTL-Lab
 * Website: https://openrtl.top/
 *
 * Project: Goldilocks Merkle Hardware Accelerator
 * Chisel hardware for Poseidon2 leaf hashing, Merkle trees and authentication paths.
 * Repository: https://github.com/OpenRTL-Lab/merkle-goldilocks
 * File: operators/merkle/cmodel/reference.c
 *
 * SPDX-FileCopyrightText: 2026 Goldilocks Merkle contributors
 * Copyright applies to original project contributions only.
 * SPDX-License-Identifier: MIT
 * See LICENSE and applicable third-party notices.
 * OpenRTL-Header-End
 */

#include <stdint.h>
#include <string.h>
#include "../vendor/poseidon2/cmodel/parameters.h"
static const uint64_t P=UINT64_C(0xffffffff00000001);
static uint64_t add(uint64_t a,uint64_t b){return (uint64_t)(((__uint128_t)a+b)%P);}
static uint64_t mul(uint64_t a,uint64_t b){return (uint64_t)(((__uint128_t)a*b)%P);}
static uint64_t power7(uint64_t x){uint64_t a=mul(x,x),b=mul(a,x);return mul(b,mul(a,a));}
static void external(uint64_t s[12]){
  /* 直接矩阵点积，独立于 RTL 的分解加法链。 */
  const unsigned m[4][4]={{2,3,1,1},{1,2,3,1},{1,1,2,3},{3,1,1,2}};
  uint64_t v[12]={0},sum[4]={0};
  for(unsigned b=0;b<3;b++)for(unsigned i=0;i<4;i++)for(unsigned j=0;j<4;j++)
    v[4*b+i]=add(v[4*b+i],mul(m[i][j],s[4*b+j]));
  for(unsigned i=0;i<12;i++)sum[i%4]=add(sum[i%4],v[i]);
  for(unsigned i=0;i<12;i++)s[i]=add(v[i],sum[i%4]);
}
void merkle_permutation(uint64_t s[12]){
  external(s);
  for(unsigned r=0;r<30;r++){
    if(r<4||r>=26){for(unsigned i=0;i<12;i++)s[i]=power7(add(s[i],RC[r][i]));external(s);}
    else{ s[0]=power7(add(s[0],RC[r][0]));uint64_t sum=0;
      for(unsigned i=0;i<12;i++)sum=add(sum,s[i]);
      for(unsigned i=0;i<12;i++)s[i]=add(sum,mul(s[i],DIAG[i])); }
  }
}
void merkle_leaf(const uint64_t *in,unsigned width,uint64_t out[4]){
  uint64_t s[12]={0};for(unsigned i=0;i<width;i++)s[i]=in[i];s[width]=1;
  merkle_permutation(s);memcpy(out,s,4*sizeof(uint64_t));
}
void merkle_node(const uint64_t left[4],const uint64_t right[4],uint64_t out[4]){
  uint64_t s[12]={0};memcpy(s,left,4*sizeof(uint64_t));memcpy(s+4,right,4*sizeof(uint64_t));
  merkle_permutation(s);memcpy(out,s,4*sizeof(uint64_t));
}
/* 输出完整的逐层树，共 2N-1 个摘要；先 N 个叶，再每层父节点。 */
void merkle_tree(const uint64_t *in,unsigned n,unsigned width,uint64_t *tree){
  for(unsigned i=0;i<n;i++)merkle_leaf(in+i*width,width,tree+4*i);
  unsigned source=0,dest=n,count=n;
  while(count>1){for(unsigned i=0;i<count/2;i++)merkle_node(tree+4*(source+2*i),tree+4*(source+2*i+1),tree+4*(dest+i));
    source=dest;count/=2;dest+=count;}
}
