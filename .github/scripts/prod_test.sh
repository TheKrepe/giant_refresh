#!/usr/bin/env bash
# Production smoke test: installs a real dedicated server for one exact Minecraft version and
# loader, drops the built mod jar in, and runs the Giant Refresh self test (-Dgiantrefresh.smoketest).
#   prod_test.sh <fabric|neoforge> <minecraft version> <mod jar>
set -u
LOADER=$1; MC=$2; JAR=$(realpath "$3")
WORK=prod-$LOADER-$MC
rm -rf "$WORK"; mkdir -p "$WORK/mods"; cd "$WORK"
echo "eula=true" > eula.txt
printf "level-seed=giantrefresh\nview-distance=5\nsimulation-distance=5\nspawn-protection=0\nonline-mode=false\nsync-chunk-writes=false\n" > server.properties
cp "$JAR" mods/
UA='User-Agent: giant-refresh-ci'
OPTS='-Dgiantrefresh.smoketest=true -Xmx3G'

if [ "$LOADER" = fabric ]; then
  LDR=$(curl -fsS -H "$UA" https://meta.fabricmc.net/v2/versions/loader | python3 -c "import json,sys;print([l['version'] for l in json.load(sys.stdin) if l['stable']][0])")
  INST=$(curl -fsS -H "$UA" https://meta.fabricmc.net/v2/versions/installer | python3 -c "import json,sys;print([l['version'] for l in json.load(sys.stdin) if l['stable']][0])")
  curl -fsS -H "$UA" -o server.jar "https://meta.fabricmc.net/v2/versions/loader/$MC/$LDR/$INST/server/jar" || { echo "no Fabric server for $MC"; exit 1; }
  API=$(curl -fsS -H "$UA" "https://api.modrinth.com/v2/project/fabric-api/version?loaders=%5B%22fabric%22%5D&game_versions=%5B%22$MC%22%5D" \
        | python3 -c "import json,sys;v=json.load(sys.stdin)[0];f=[x for x in v['files'] if x['primary']] or v['files'];print(v['version_number'], f[0]['url'])")
  echo "[PROD] Fabric loader $LDR, Fabric API ${API%% *}"
  curl -fsSL -H "$UA" -o mods/fabric-api.jar "${API##* }"
  timeout 1200 java $OPTS -jar server.jar nogui > server.log 2>&1
else
  PREFIX=$(python3 -c "
p='$MC'.split('.')
print(f'{p[1]}.{p[2] if len(p)>2 else 0}.' if p[0]=='1' else ('$MC' if len(p)>2 else '$MC.0') + '.')")
  NEO=$(curl -fsS -H "$UA" https://maven.neoforged.net/releases/net/neoforged/neoforge/maven-metadata.xml | python3 -c "
import sys,re,xml.etree.ElementTree as ET
vs=[v.text for v in ET.fromstring(sys.stdin.read()).iter('version') if v.text.startswith('$PREFIX')]
key=lambda v:[int(x) for x in re.findall(r'\d+',v.split('-')[0])]
st=[v for v in vs if 'beta' not in v and 'alpha' not in v]
print(max(st or vs,key=key) if vs else '')")
  [ -n "$NEO" ] || { echo "no NeoForge for $MC (prefix $PREFIX)"; exit 1; }
  echo "[PROD] NeoForge $NEO"
  curl -fsSL -H "$UA" -o installer.jar "https://maven.neoforged.net/releases/net/neoforged/neoforge/$NEO/neoforge-$NEO-installer.jar"
  java -jar installer.jar --install-server . > install.log 2>&1 || { tail -30 install.log; exit 1; }
  echo "$OPTS" > user_jvm_args.txt
  timeout 1200 bash run.sh nogui > server.log 2>&1
fi

grep -E "\[SMOKETEST\]|Mixin|mixin|ERROR|Exception|Caused by|giant_refresh" server.log | grep -vE "JAVA_TOOL_OPTIONS" | head -200
# data-pack / registry problems for our namespace must not appear at all
if grep -E "ERROR|WARN" server.log | grep -qiE "giant_refresh|giantrefresh"; then
  echo "[PROD] FAIL: errors or warnings mentioning giant_refresh"
  grep -E "ERROR|WARN" server.log | grep -iE "giant_refresh|giantrefresh" | head -40
  exit 1
fi
grep -q "\[SMOKETEST\].*-> PASS" server.log && echo "[PROD] PASS $LOADER $MC" || { echo "[PROD] FAIL $LOADER $MC"; tail -60 server.log; exit 1; }
