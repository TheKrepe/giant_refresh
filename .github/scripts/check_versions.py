#!/usr/bin/env python3
"""Prints the newest NeoForge / Fabric toolchain versions for every supported Minecraft target
(versions/*.properties) next to the ones the project uses, as `[VERSIONS] ...` lines.
Informational only: it never changes anything. Exit code is always 0."""
import glob
import json
import os
import re
import urllib.request
import xml.etree.ElementTree as ET

ROOT = os.path.dirname(os.path.dirname(os.path.dirname(os.path.abspath(__file__))))


def props(path):
    out = {}
    for line in open(path, encoding='utf-8'):
        if '=' in line and not line.lstrip().startswith('#'):
            k, v = line.split('=', 1)
            out[k.strip()] = v.strip()
    return out


def get(url):
    req = urllib.request.Request(url, headers={'User-Agent': 'giant-refresh-ci'})
    with urllib.request.urlopen(req, timeout=30) as r:
        return r.read()


def maven_versions(url):
    root = ET.fromstring(get(url))
    return [v.text for v in root.iter('version')]


def safe(fn):
    try:
        return fn()
    except Exception as e:  # noqa: BLE001
        return f'error:{e.__class__.__name__}'


def vkey(v):
    return [int(x) for x in re.findall(r'\d+', v.split('-')[0].split('+')[0])]


def report(name, current, latest):
    flag = 'UPDATE' if isinstance(latest, str) and latest and not latest.startswith('error') and latest != current else 'ok'
    print(f'[VERSIONS] {name:<28} current={current:<22} latest={latest} {flag}')


def neo_prefix(mc):
    p = mc.split('.')
    if p[0] == '1':  # 1.21.10 -> 21.10.
        return f'{p[1]}.{p[2] if len(p) > 2 else 0}.'
    return mc + '.'  # 26.1.2 -> 26.1.2.


neo_all = safe(lambda: maven_versions('https://maven.neoforged.net/releases/net/neoforged/neoforge/maven-metadata.xml'))
api_all = safe(lambda: maven_versions('https://maven.fabricmc.net/net/fabricmc/fabric-api/fabric-api/maven-metadata.xml'))

targets = sorted(glob.glob(os.path.join(ROOT, 'versions', '*.properties')), key=lambda p: vkey(os.path.basename(p)), reverse=True)
for path in targets:
    t = os.path.basename(path)[:-len('.properties')]
    pr = props(path)
    mc = pr['minecraft_version']
    if isinstance(neo_all, list):
        cands = [v for v in neo_all if v.startswith(neo_prefix(mc))]
        stable = [v for v in cands if 'beta' not in v and 'alpha' not in v]
        pick = max(stable or cands, key=vkey) if cands else 'none'
    else:
        pick = neo_all
    report(f'{t} neoforge', pr['neo_version'], pick)
    if isinstance(api_all, list):
        cands = [v for v in api_all if v.endswith('+' + mc)]
        pick = max(cands, key=vkey) if cands else 'none'
    else:
        pick = api_all
    report(f'{t} fabric_api', pr['fabric_api_version'], pick)

shared = props(os.path.join(ROOT, 'versions', '26.3.properties'))
loader = safe(lambda: json.loads(get('https://meta.fabricmc.net/v2/versions/loader')))
loader_stable = [l['version'] for l in loader if l.get('stable')] if isinstance(loader, list) else []
report('fabric_loader (all)', shared['loader_version'], loader_stable[0] if loader_stable else loader)

fab = props(os.path.join(ROOT, 'fabric', 'gradle.properties'))
loom = safe(lambda: maven_versions('https://maven.fabricmc.net/net/fabricmc/fabric-loom/net.fabricmc.fabric-loom.gradle.plugin/maven-metadata.xml'))
loom_list = [v for v in loom if not v.endswith('-SNAPSHOT')] if isinstance(loom, list) else []
report('fabric_loom', fab['loom_version'], max(loom_list, key=vkey) if loom_list else loom)

neo = props(os.path.join(ROOT, 'neoforge', 'gradle.properties'))
mdg = safe(lambda: maven_versions('https://maven.neoforged.net/releases/net/neoforged/moddev/net.neoforged.moddev.gradle.plugin/maven-metadata.xml'))
mdg_rel = [v for v in mdg if re.fullmatch(r'[\d.]+', v)] if isinstance(mdg, list) else []
report('moddevgradle', neo['moddevgradle_version'], max(mdg_rel, key=vkey) if mdg_rel else mdg)

games = safe(lambda: json.loads(get('https://meta.fabricmc.net/v2/versions/game')))
if isinstance(games, list):
    print(f'[VERSIONS] minecraft_recent {" ".join(g["version"] for g in games[:8])}')
