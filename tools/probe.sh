#!/usr/bin/env bash
# Reference dump for one Minecraft version: vanilla data/assets, javap of the mojmap MC (+NeoForge) jar,
# javap of Fabric API, and the versions of every loader artifact for that MC version.
set -u
MC=$1
O=$PWD/out; mkdir -p $O W; cd W
curl -sL https://piston-meta.mojang.com/mc/game/version_manifest_v2.json -o manifest.json
URL=$(python3 -c "import json;m=json.load(open('manifest.json'));print([v['url'] for v in m['versions'] if v['id']=='$MC'][0])")
curl -sL "$URL" -o version.json
cp version.json $O/version.json
CL=$(python3 -c "import json;print(json.load(open('version.json'))['downloads']['client']['url'])")
curl -sL "$CL" -o mc.jar
mkdir ex && (cd ex && unzip -q ../mc.jar -x '*.class')
(cd ex && zip -qr $O/vanilla_data.zip data -x 'data/minecraft/structure/*' && zip -qr $O/vanilla_assets_json.zip assets -i '*.json' -x 'assets/minecraft/lang/*' && zip -q $O/vanilla_assets_json.zip assets/minecraft/lang/en_us.json)
(cd ex && zip -qr $O/vanilla_textures_sel.zip assets/minecraft/textures/entity/equipment assets/minecraft/textures/entity/zombie assets/minecraft/textures/mob_effect 2>/dev/null; cp version.json $O/client_version_inside.json 2>/dev/null; true)

# NeoForge version for this MC
case $MC in
  26.*) PFX="$MC." ;;
  1.21.*) PFX="21.${MC#1.21.}." ;;
esac
curl -s https://maven.neoforged.net/releases/net/neoforged/neoforge/maven-metadata.xml -o nfmeta.xml
NV=$(grep -oE "<version>${PFX//./\\.}[^<]*" nfmeta.xml | sed 's/<version>//' | tail -1)
grep -oE "<version>${PFX//./\\.}[^<]*" nfmeta.xml | sed 's/<version>//' > $O/neoforge_versions.txt
echo "$NV" > $O/neoforge_version.txt

# MC (mojmap) + NeoForge merged jar via ModDevGradle
JV=25; case $MC in 1.21.*) JV=21;; esac
mkdir mdg && cd mdg
cat > settings.gradle <<S
pluginManagement { repositories { gradlePluginPortal(); maven { url = 'https://maven.neoforged.net/releases' } } }
rootProject.name = 'probe'
S
cat > build.gradle <<B
plugins { id 'java-library'; id 'net.neoforged.moddev' version '2.0.148' }
java.toolchain.languageVersion = JavaLanguageVersion.of($JV)
neoForge { version = '$NV' }
B
gradle --no-daemon -q createMinecraftArtifacts > $O/mdg_log.txt 2>&1 || gradle --no-daemon tasks --all >> $O/mdg_log.txt 2>&1
find . ~/.gradle -path '*moddev*' -name '*.jar' 2>/dev/null | head -50 >> $O/mdg_log.txt
MJ=$(find build -name '*.jar' 2>/dev/null | grep -v sources | xargs ls -S 2>/dev/null | head -1)
echo "merged jar: $MJ" >> $O/mdg_log.txt
if [ -n "$MJ" ]; then
  unzip -Z1 "$MJ" | grep '\.class$' | sed 's|\.class$||; s|/|.|g' | sort > ../cls.txt
  cd ..; split -l 400 cls.txt part_
  for p in part_*; do javap -cp "mdg/$MJ" -p $(cat $p) 2>/dev/null; done | gzip -9 > $O/mc_neoforge_javap_all.txt.gz
else
  cd ..
fi

# Fabric API (latest for this exact MC version) + loader
curl -s "https://api.modrinth.com/v2/project/fabric-api/version?game_versions=%5B%22$MC%22%5D" -o fapi.json
python3 -c "import json;d=json.load(open('fapi.json'));print('\n'.join(v['version_number'] for v in d))" > $O/fabric_api_versions.txt
FAPI=$(python3 -c "import json;d=json.load(open('fapi.json'));print(d[0]['files'][0]['url'])")
mkdir fapi && cd fapi && curl -sL "$FAPI" -o fapi.jar && unzip -q fapi.jar 'META-INF/jars/*'
for j in META-INF/jars/*.jar; do
  cls=$(unzip -Z1 $j | grep '\.class$' | grep -v '/impl/' | grep -v '/mixin/' | sed 's|\.class$||; s|/|.|g')
  [ -n "$cls" ] && javap -cp $j -p $cls 2>/dev/null
done | gzip -9 > $O/fabric_api_javap_all.txt.gz
cd ..
curl -s https://meta.fabricmc.net/v2/versions/loader | python3 -c "import json,sys;print([l['version'] for l in json.load(sys.stdin) if l['stable']][:3])" > $O/fabric_loader.txt
curl -s "https://meta.fabricmc.net/v2/versions/intermediary/$MC" > $O/intermediary.json
# example / MDK templates for this version
curl -s https://api.github.com/repos/FabricMC/fabric-example-mod/branches?per_page=100 | python3 -c "import json,sys;print('\n'.join(b['name'] for b in json.load(sys.stdin)))" > $O/fabric_example_branches.txt
for f in build.gradle gradle.properties gradle/wrapper/gradle-wrapper.properties src/main/resources/fabric.mod.json; do
  curl -sfL "https://raw.githubusercontent.com/FabricMC/fabric-example-mod/$MC/$f" -o "$O/fabric_example_$(basename $f)" || true
done
for f in build.gradle gradle.properties settings.gradle gradle/wrapper/gradle-wrapper.properties src/main/templates/META-INF/neoforge.mods.toml; do
  curl -sfL "https://raw.githubusercontent.com/NeoForgeMDKs/MDK-$MC-ModDevGradle/main/$f" -o "$O/nf_mdk_$(basename $f)" || true
done
ls -la $O
