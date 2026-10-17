#!/usr/bin/env python3
# OpenRTL-Header-Begin
# OpenRTL — Open-source hardware accelerators.
# Organization: https://github.com/OpenRTL-Lab
# Website: https://openrtl.top/
#
# Project: Goldilocks Merkle Hardware Accelerator
# Chisel hardware for Poseidon2 leaf hashing, Merkle trees and authentication paths.
# Repository: https://github.com/OpenRTL-Lab/merkle-goldilocks
# File: tools/public_audit.py
#
# SPDX-FileCopyrightText: 2026 Goldilocks Merkle contributors
# Copyright applies to original project contributions only.
# SPDX-License-Identifier: MIT
# See LICENSE and applicable third-party notices.
# OpenRTL-Header-End

"""只读审查公开白名单、双语链接与敏感内容；不自动修改文件。"""
from pathlib import Path
import posixpath,re,sys
from urllib.parse import unquote,urlsplit

ROOT=Path(__file__).resolve().parents[1]
IGNORED={'.git','.internal','.cache','build','out','__pycache__'}
DOCS={'applications','architecture','interface','toolchain','reproduction','performance','mathematical-model','synthesis-summary','publication-boundary'}
RTL={'GoldilocksMerkle.sv','GoldilocksPoseidon2.sv','PoseidonGoldilocksMul.sv','bank0_65536x256.sv','bank1_65536x256.sv'}
OP='operators/merkle/'
VENDOR=OP+'vendor/poseidon2/'
PREFIX='rtl/v1.1.0/max65536-l8-ctx16/'
EXPECTED={'.gitignore','LICENSE','VERSION','build.mill','README.md','README.zh-CN.md','THIRD_PARTY.md','THIRD_PARTY.en.md'}
EXPECTED|={'docs/'+n+s for n in DOCS for s in ('.md','.en.md')}
EXPECTED|={'tools/'+n for n in ('emit_sv.py','verify.py','public_audit.py')}
EXPECTED|={OP+'src/main/scala/zk/merkle/'+n for n in ('Merkle.scala','MerkleSim.scala')}
EXPECTED|={OP+n for n in ('src/tool/scala/zk/merkle/Emit.scala','cmodel/reference.c','sim/driver.cpp','tools/reference.py')}
EXPECTED|={VENDOR+'src/main/scala/zk/poseidon2/'+n for n in ('Arithmetic.scala','Parameters.scala','Poseidon2.scala')}
EXPECTED|={VENDOR+n for n in ('cmodel/parameters.h','reference/parameters.json','reference/LICENSE-MIT')}
EXPECTED|={PREFIX+n for n in RTL|{'files.f'}}
EXPECTED |= {'rtl/v1.1.0/max65536-l8-ctx16/files.f.license', 'docs/journal.md', 'operators/merkle/vendor/poseidon2/cmodel/parameters.h.license', 'VERSION.license', 'docs/source-headers.md', 'tools/source_headers.py', 'docs/journal.en.md', 'docs/source-headers.en.md', 'operators/merkle/vendor/poseidon2/reference/parameters.json.license', 'tools/test_source_headers.py'}
PATTERNS=[
 ('机器绝对路径',r'\x2f(?:home|Users|root|opt|mnt|media|tmp|var|work|workspace|private|[A-Za-z0-9_.-]+-data)\x2f'),
 ('Windows路径',r'\b[A-Z]:\\'),
 ('内部追溯值',r'\b[0-9a-fA-F]{40,64}\b'),
 ('内部追溯字段',r'\b(?:sha(?:1|256|512)|source[_-]commit|provenance[_-]id|runner[_-]digest)\b'),
 ('疑似凭证',r'(?:gh[pousr]_[A-Za-z0-9]{20,}|github_pat_[A-Za-z0-9_]{20,}|AKIA[A-Z0-9]{16})'),
 ('私钥',r'BEGIN (?:RSA |EC |OPENSSH )?PRIVATE KEY'),
 ('IP地址',r'\b(?:\d{1,3}\.){3}\d{1,3}\b'),
 ('内部运行身份',r'\b(?:v\d{2}-[0-9a-f]{12}|agent-[0-9a-f]{16,})\b'),
 ('源码定位',r'\.scala:\d+:\d+'),
 ('不兼容数学宏',r'\\'+'operator'+'name'+r'\b')]

def sensitive(text):
    return [label for label,pattern in PATTERNS if re.search(pattern,text)]

def audit(root=ROOT):
    failures=[];files=set();targets=set();docs={};labels={}
    def visit(folder):
        for p in folder.iterdir():
            rel=p.relative_to(root)
            if rel.parts[0] in IGNORED or '__pycache__' in rel.parts: continue
            name=rel.as_posix();targets.add(name)
            if p.is_symlink():failures.append(name+'：禁止符号链接');continue
            if p.is_dir():visit(p);continue
            files.add(name)
            if name not in EXPECTED:failures.append(name+'：白名单外文件')
            try:text=p.read_text()
            except (UnicodeError,OSError):failures.append(name+'：非文本');continue
            if '\x00' in text:failures.append(name+'：二进制字节')
            failures.extend(name+'：'+label for label in sensitive(text))
            if name.endswith('.md'):docs[name]=text
    visit(root)
    failures.extend(n+'：缺少文件' for n in sorted(EXPECTED-files))
    for name,text in docs.items():
        other=('README.zh-CN.md' if name=='README.md' else 'README.md' if name=='README.zh-CN.md'
               else name[:-6]+'.md' if name.endswith('.en.md') else name[:-3]+'.en.md')
        english=name=='README.md' or name.endswith('.en.md')
        if other not in docs: failures.append(name+'：缺少配对文档')
        parts=re.split(r'\n\s*\n',re.sub(r'\A<!--\nOpenRTL-Header-Begin.*?-->\n\n', '', text, count=1, flags=re.S).strip())
        if len(parts)<2 or not parts[0].startswith('# ') or re.match(r'(?:#|\||```|[-*] |\d+\. )',parts[1]):failures.append(name+'：缺少首段摘要')
        for label,url in re.findall(r'(?<!!)\[([^\[\]\n]+)\]\(([^\s)]+)\)',text):
            u=urlsplit(url)
            key=url if u.scheme or u.netloc else posixpath.normpath(posixpath.join(posixpath.dirname(name),unquote(u.path)))
            if not (u.scheme or u.netloc):
                if key not in targets:failures.append(name+'：失效链接'+url)
                if label==('简体中文' if english else 'English') and key==other:continue
                target_english=key=='README.md' or key.endswith('.en.md')
                if english and key.endswith('.md') and not target_english:failures.append(name+'：英文指向中文MD')
                if target_english and re.search('[\u4e00-\u9fff]',label):failures.append(name+'：英文入口文字错误')
            language='zh' if re.search('[\u4e00-\u9fff]',label) else 'en'
            prior=labels.setdefault((language,key),label)
            if prior!=label:failures.append(name+'：链接名称不一致：'+label+' / '+prior)
    if (root/PREFIX/'files.f').exists() and set((root/PREFIX/'files.f').read_text().splitlines())!={PREFIX+n for n in RTL}:failures.append('产品清单错误')
    return failures

def main():
    errors=audit()
    if errors:print('\n'.join(errors));return 1
    print(f'公开载荷审查通过：{len(EXPECTED)}份文本文件，双语与链接一致。');return 0

if __name__=='__main__':sys.exit(main())
