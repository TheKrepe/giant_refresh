#!/usr/bin/env python3
"""Prints the newest NeoForge / Fabric toolchain versions for the targeted Minecraft version
next to the ones the project uses, as `[VERSIONS] name current latest` lines. Informational only."""
import json
import re
import urllib.request
import xml.etree.ElementTree as ET


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


def report(name, current, latest):
    flag = 'UPDATE' if latest and latest != current else 'ok'
    print(f'[VERSIONS] {name} current={current} latest={latest} {flag}')


fab = props('fabric/gradle.properties')
neo = props('neoforge/gradle.properties')
mc = neo['minecraft_version']

def safe(fn):
    try:
        return fn()
    except Exception as e:  # noqa: BLE001
        return f'error:{e.__class__.__name__}'

neo_all = safe(lambda: maven_versions('https://maven.neoforged.net/releases/net/neoforged/neoforge/maven-metadata.xml'))
neo_mc = [v for v in neo_all if isinstance(neo_all, list) and v.startswith(mc + '.')] if isinstance(neo_all, list) else []
report('neoforge', neo['neo_version'], neo_mc[-1] if neo_mc else neo_all)

mdg = safe(lambda: maven_versions('https://maven.neoforged.net/releases/net/neoforged/moddev/net.neoforged.moddev.gradle.plugin/maven-metadata.xml'))
mdg_rel = [v for v in mdg if re.fullmatch(r'[\d.]+', v)] if isinstance(mdg, list) else []
report('moddevgradle', neo['moddevgradle_version'], mdg_rel[-1] if mdg_rel else mdg)

loader = safe(lambda: json.loads(get('https://meta.fabricmc.net/v2/versions/loader')))
loader_stable = [l['version'] for l in loader if l.get('stable')] if isinstance(loader, list) else []
report('fabric_loader', fab['loader_version'], loader_stable[0] if loader_stable else loader)

api = safe(lambda: maven_versions('https://maven.fabricmc.net/net/fabricmc/fabric-api/fabric-api/maven-metadata.xml'))
api_mc = [v for v in api if v.endswith('+' + mc)] if isinstance(api, list) else []
report('fabric_api', fab['fabric_api_version'], api_mc[-1] if api_mc else api)

loom = safe(lambda: maven_versions('https://maven.fabricmc.net/net/fabricmc/fabric-loom/net.fabricmc.fabric-loom.gradle.plugin/maven-metadata.xml'))
loom_list = [v for v in loom if not v.endswith('-SNAPSHOT')] if isinstance(loom, list) else []
report('fabric_loom', fab['loom_version'], loom_list[-1] if loom_list else loom)

games = safe(lambda: json.loads(get('https://meta.fabricmc.net/v2/versions/game')))
if isinstance(games, list):
    newer = [g['version'] for g in games[:8]]
    print(f'[VERSIONS] minecraft_recent {" ".join(newer)}')
