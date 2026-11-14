#!/usr/bin/env python3
"""Multi-version source preprocessor for Giant Refresh.

The sources in common/, fabric/ and neoforge/ are written for the newest Minecraft version
(the "dev" version), so they compile as they are. Code that differs between versions is
wrapped in conditional blocks; the other variants sit in comments:

    //#if MC >= 26.3
    newCode();
    //#elif MC >= 1.21.9
    //$$ middleCode();
    //#else
    //$$ oldCode();
    //#endif

Conditions: MC <op> <version> joined with && / ||, ops: >= > <= < == !=;
also FABRIC / NEOFORGE (the loader being built).

For a target, this script writes a processed copy of every source set to build/gen/<target>/:
the active branch is uncommented, the inactive ones are commented out ("//$$ "). Token
replacements from versions/<target>.properties (replace.<regex>=<text>) are applied after that,
and resource overlays from versions/overlays/<folder>/ are copied on top (a folder name is a
condition such as "lt-26.3" or "lt-1.21.11"; a file named "<name>.delete" removes <name>).

    python3 tools/preprocess.py <target> [--check]
"""
import os
import re
import shutil
import sys

ROOT = os.path.dirname(os.path.dirname(os.path.abspath(__file__)))
SOURCE_SETS = [
    ('common/src/main', 'common'),
    ('fabric/src/main', 'fabric'),
    ('neoforge/src/main', 'neoforge'),
]
TEXT_EXT = ('.java', '.json', '.toml', '.mcmeta', '.txt', '.properties', '.cfg')


def vtuple(v):
    parts = [int(p) for p in re.findall(r'\d+', v)]
    while len(parts) < 3:
        parts.append(0)
    return tuple(parts[:3])


def load_props(target):
    path = os.path.join(ROOT, 'versions', f'{target}.properties')
    props = {}
    with open(path, encoding='utf-8') as f:
        for line in f:
            line = line.strip()
            if not line or line.startswith('#') or '=' not in line:
                continue
            k, v = line.split('=', 1)
            props[k.strip()] = v.strip()
    return props


def make_eval(mc, loader):
    mcv = vtuple(mc)

    def atom(expr):
        expr = expr.strip()
        if expr in ('FABRIC', 'NEOFORGE'):
            return loader is None or loader == expr.lower()
        if expr.startswith('!'):
            return not atom(expr[1:])
        m = re.fullmatch(r'MC\s*(>=|<=|==|!=|>|<)\s*([\d.]+)', expr)
        if not m:
            raise ValueError(f'bad condition: {expr!r}')
        op, ver = m.group(1), vtuple(m.group(2))
        return {'>=': mcv >= ver, '<=': mcv <= ver, '==': mcv == ver, '!=': mcv != ver,
                '>': mcv > ver, '<': mcv < ver}[op]

    def evaluate(cond):
        return any(all(atom(a) for a in part.split('&&')) for part in cond.split('||'))

    return evaluate


DIRECTIVE = re.compile(r'^(\s*)//#(if|elif|else|endif)\b(.*)$')
COMMENTED = re.compile(r'^(\s*)//\$\$ ?(.*)$')


def process_java(text, evaluate, path):
    out = []
    stack = []  # each: [taken_any, active_now, parent_active]
    for no, line in enumerate(text.split('\n'), 1):
        m = DIRECTIVE.match(line)
        if m:
            kind, rest = m.group(2), m.group(3)
            parent = all(s[1] for s in stack) if stack else True
            if kind == 'if':
                val = evaluate(rest)
                stack.append([val, val, parent])
            elif kind == 'elif':
                if not stack:
                    raise SyntaxError(f'{path}:{no}: #elif without #if')
                s = stack[-1]
                val = (not s[0]) and evaluate(rest)
                s[1] = val
                s[0] = s[0] or val
            elif kind == 'else':
                if not stack:
                    raise SyntaxError(f'{path}:{no}: #else without #if')
                s = stack[-1]
                s[1] = not s[0]
                s[0] = True
            else:
                if not stack:
                    raise SyntaxError(f'{path}:{no}: #endif without #if')
                stack.pop()
            out.append(line)
            continue
        if not stack:
            out.append(line)
            continue
        active = all(s[1] for s in stack)
        cm = COMMENTED.match(line)
        if active:
            out.append(cm.group(1) + cm.group(2) if cm else line)
        else:
            if cm or not line.strip():
                out.append(line)
            else:
                ind = re.match(r'^(\s*)', line).group(1)
                out.append(f'{ind}//$$ {line[len(ind):]}')
    if stack:
        raise SyntaxError(f'{path}: unterminated #if')
    return '\n'.join(out)


def overlay_matches(name, mc):
    """Overlay folder names: 'lt-26.3', 'ge-1.21.9', 'lt-26.3_ge-1.21.9', 'eq-26.2' ..."""
    mcv = vtuple(mc)
    for cond in name.split('_'):
        op, _, ver = cond.partition('-')
        v = vtuple(ver)
        ok = {'lt': mcv < v, 'le': mcv <= v, 'gt': mcv > v, 'ge': mcv >= v, 'eq': mcv == v, 'ne': mcv != v}.get(op)
        if ok is None:
            raise ValueError(f'bad overlay folder {name!r}')
        if not ok:
            return False
    return True


def main():
    target = sys.argv[1]
    props = load_props(target)
    mc = props['minecraft_version']
    replaces = [(re.compile(k[len('replace.'):]), v) for k, v in props.items() if k.startswith('replace.')]
    gen = os.path.join(ROOT, 'build', 'gen', target)
    if os.path.isdir(gen):
        shutil.rmtree(gen)
    for src_rel, name in SOURCE_SETS:
        loader = None if name == 'common' else name
        evaluate = make_eval(mc, loader)
        src = os.path.join(ROOT, src_rel)
        dst = os.path.join(gen, name)
        for dirpath, _, files in os.walk(src):
            for fn in files:
                sp = os.path.join(dirpath, fn)
                rel = os.path.relpath(sp, src)
                dp = os.path.join(dst, rel)
                os.makedirs(os.path.dirname(dp), exist_ok=True)
                if fn.endswith('.java'):
                    with open(sp, encoding='utf-8') as f:
                        text = process_java(f.read(), evaluate, sp)
                    for rx, rep in replaces:
                        text = rx.sub(rep, text)
                    with open(dp, 'w', encoding='utf-8') as f:
                        f.write(text)
                else:
                    shutil.copy2(sp, dp)
    # resource overlays (apply in name order; later folders win)
    ov_root = os.path.join(ROOT, 'versions', 'overlays')
    if os.path.isdir(ov_root):
        for folder in sorted(os.listdir(ov_root)):
            if not overlay_matches(folder, mc):
                continue
            base = os.path.join(ov_root, folder)
            for dirpath, _, files in os.walk(base):
                for fn in files:
                    sp = os.path.join(dirpath, fn)
                    rel = os.path.relpath(sp, base)  # e.g. common/resources/data/...
                    dp = os.path.join(gen, rel)
                    if fn.endswith('.delete'):
                        victim = dp[:-len('.delete')]
                        if os.path.isdir(victim):
                            shutil.rmtree(victim)
                        elif os.path.exists(victim):
                            os.remove(victim)
                        continue
                    os.makedirs(os.path.dirname(dp), exist_ok=True)
                    shutil.copy2(sp, dp)
    print(f'preprocessed {target} (MC {mc}) -> {os.path.relpath(gen, ROOT)}')


if __name__ == '__main__':
    main()
