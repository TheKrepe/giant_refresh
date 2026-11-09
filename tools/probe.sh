#!/usr/bin/env bash
set -u
MC=${MC:-26.3}
O=out2; mkdir -p $O W
cd W
curl -sL https://piston-meta.mojang.com/mc/game/version_manifest_v2.json -o manifest.json
URL=$(python3 -c "import json;m=json.load(open('manifest.json'));print([v['url'] for v in m['versions'] if v['id']=='$MC'][0])")
curl -sL "$URL" -o version.json
CL=$(python3 -c "import json;print(json.load(open('version.json'))['downloads']['client']['url'])")
curl -sL "$CL" -o mc.jar
mkdir ex && cd ex && unzip -q ../mc.jar && cd ..
# data + json assets
(cd ex && zip -qr ../../$O/vanilla_data.zip data -x 'data/minecraft/structure/*' && zip -qr ../../$O/vanilla_assets_json.zip assets -i '*.json' -x 'assets/minecraft/lang/*' && zip -q ../../$O/vanilla_assets_json.zip assets/minecraft/lang/en_us.json assets/minecraft/lang/ru_ru.json)
(cd ex && zip -qr ../../$O/vanilla_textures_sel.zip assets/minecraft/textures/entity/zombie assets/minecraft/textures/entity/ghast assets/minecraft/textures/entity/equipment assets/minecraft/textures/item assets/minecraft/textures/block assets/minecraft/textures/mob_effect assets/minecraft/textures/particle)
# full javap -p index
find ex -name '*.class' | sed 's|^ex/||; s|\.class$||; s|/|.|g' | sort > classes.txt
split -l 400 classes.txt part_
for p in part_*; do javap -cp mc.jar -p $(cat $p) 2>/dev/null; done | gzip -9 > ../$O/mc_javap_all.txt.gz
# neoforge
NV=$(curl -s https://maven.neoforged.net/releases/net/neoforged/neoforge/maven-metadata.xml | grep -oE "<version>$MC[^<]*" | sed 's/<version>//' | tail -1)
echo $NV > ../$O/neoforge_version.txt
curl -sL "https://maven.neoforged.net/releases/net/neoforged/neoforge/$NV/neoforge-$NV-universal.jar" -o nf.jar
mkdir nfx && (cd nfx && unzip -q ../nf.jar)
find nfx -name '*.class' | sed 's|^nfx/||; s|\.class$||; s|/|.|g' | sort > nfc.txt
split -l 400 nfc.txt nfp_
for p in nfp_*; do javap -cp nf.jar -p $(cat $p) 2>/dev/null; done | gzip -9 > ../$O/neoforge_javap_all.txt.gz
(cd nfx && zip -qr ../../$O/neoforge_resources.zip . -x '*.class')
# fabric api
FAPI=$(curl -s "https://api.modrinth.com/v2/project/fabric-api/version?game_versions=%5B%22$MC%22%5D" | python3 -c "import json,sys;d=json.load(sys.stdin);print(d[0]['files'][0]['url'])")
mkdir fapi && cd fapi && curl -sL "$FAPI" -o fapi.jar && unzip -q fapi.jar 'META-INF/jars/*'
for j in META-INF/jars/*.jar; do
  cls=$(unzip -Z1 $j | grep '\.class$' | grep -v '/impl/' | grep -v '/mixin/' | sed 's|\.class$||; s|/|.|g')
  [ -n "$cls" ] && javap -cp $j -p $cls 2>/dev/null
done | gzip -9 > ../../$O/fabric_api_javap_all.txt.gz
cd ..
# neoforge mdk / example gradle for 26.3? fetch MDK template build files
curl -sL https://raw.githubusercontent.com/NeoForgeMDKs/MDK-26.3-ModDevGradle/main/build.gradle -o ../$O/nf_mdk_build.gradle
curl -sL https://raw.githubusercontent.com/NeoForgeMDKs/MDK-26.3-ModDevGradle/main/gradle.properties -o ../$O/nf_mdk_gradle.properties
curl -sL https://raw.githubusercontent.com/NeoForgeMDKs/MDK-26.3-ModDevGradle/main/settings.gradle -o ../$O/nf_mdk_settings.gradle
curl -sL https://raw.githubusercontent.com/NeoForgeMDKs/MDK-26.3-ModDevGradle/main/src/main/templates/META-INF/neoforge.mods.toml -o ../$O/nf_mdk_mods.toml
curl -sL https://raw.githubusercontent.com/FabricMC/fabric-example-mod/26.3/build.gradle -o ../$O/fabric_example_build.gradle
curl -sL https://raw.githubusercontent.com/FabricMC/fabric-example-mod/26.3/gradle.properties -o ../$O/fabric_example_gradle.properties
curl -sL https://raw.githubusercontent.com/FabricMC/fabric-example-mod/26.3/src/main/resources/fabric.mod.json -o ../$O/fabric_example_mod.json
curl -sL https://raw.githubusercontent.com/FabricMC/fabric-example-mod/26.3/gradle/wrapper/gradle-wrapper.properties -o ../$O/fabric_example_wrapper.properties
curl -s https://api.github.com/repos/FabricMC/fabric-example-mod/branches | head -c 3000 > ../$O/fabric_example_branches.json
curl -s https://maven.fabricmc.net/net/fabricmc/fabric-loom/maven-metadata.xml > ../$O/fabric_loom_meta.xml
curl -s https://maven.neoforged.net/releases/net/neoforged/moddev/net.neoforged.moddev.gradle.plugin/maven-metadata.xml > ../$O/mdg_meta.xml
curl -s https://maven.architectury.dev/dev/architectury/loom/dev.architectury.loom.gradle.plugin/maven-metadata.xml > ../$O/arch_loom_meta.xml
curl -s https://maven.architectury.dev/architectury-plugin/architectury-plugin.gradle.plugin/maven-metadata.xml > ../$O/arch_plugin_meta.xml
cd ..
ls -la $O
