#!/usr/bin/env bash
set -u
MC=${MC:-26.3}
O=out; mkdir -p $O/javap $O/javapc $O/data $O/assets W
cd W
curl -sL https://piston-meta.mojang.com/mc/game/version_manifest_v2.json -o manifest.json
python3 - "$MC" <<'PY' > ../$O/mc_versions.txt
import json,sys
m=json.load(open('manifest.json'))
print('latest',m['latest'])
for v in m['versions'][:60]:
    print(v['id'],v['type'],v['releaseTime'])
url=[v['url'] for v in m['versions'] if v['id']==sys.argv[1]][0]
open('vurl','w').write(url)
PY
curl -sL "$(cat vurl)" -o version.json
python3 - <<'PY' >> ../$O/mc_versions.txt
import json
v=json.load(open('version.json'))
print('java',v['javaVersion'])
print('client',v['downloads']['client']['url'])
open('curl_client','w').write(v['downloads']['client']['url'])
open('assetidx','w').write(v['assetIndex']['url'])
for l in v['libraries']:
    if 'lwjgl' in l['name'] and ':natives' not in l['name']: print(l['name'])
PY
curl -sL "$(cat curl_client)" -o mc.jar
unzip -Z1 mc.jar > ../$O/mc_files.txt
grep '\.class$' ../$O/mc_files.txt | sed 's/\.class$//' > mc_classes.txt

# assets index & sounds.json
curl -sL "$(cat assetidx)" -o assets.json
python3 - <<'PY' > ../$O/assets/sound_files.txt
import json
o=json.load(open('assets.json'))['objects']
for k,v in sorted(o.items()):
    if k.endswith('.ogg') and any(s in k for s in ['zombie','ravager','warden','golem','step','giant','ghast','sniffer','explode','hoglin','zoglin','camel','wind_charge','mace']):
        print(k)
h=o['minecraft/sounds.json']['hash']
open('sjhash','w').write(h)
PY
H=$(cat sjhash); curl -sL "https://resources.download.minecraft.net/${H:0:2}/$H" -o sounds.json
python3 - <<'PY' > ../$O/assets/sounds_subset.json
import json
s=json.load(open('sounds.json'))
print(json.dumps({k:v for k,v in s.items() if any(x in k for x in ['zombie','ravager','iron_golem','happy_ghast','warden.step','generic.explode','mace','ghast'])},indent=1))
PY

# extract data/assets samples
mkdir -p ex && cd ex && unzip -q ../mc.jar 'data/*' 'assets/*' 'version.json' 'pack.mcmeta' 2>/dev/null; cd ..
cp ex/version.json ../$O/ 2>/dev/null
for f in $(grep -E 'loot_table/entities/(zombie|giant|wither_skeleton|husk)\.json|worldgen/feature/(disk_grass|disk_clay|ore_iron|forest_rock|patch_grass|fallen_oak_tree|desert_well|ice_spike|pile_hay)\.json|worldgen/placed_feature/(disk_grass|forest_rock|desert_well|ice_spike|patch_grass_plain)\.json|recipe/(cake|torch|player_head|skull_banner_pattern)\.json|tags/block/(leaves|flowers|small_flowers|replaceable_by_trees|wooden_fences|crops|sword_efficient|enchantment/.*)\.json|tags/entity_type/(undead|zombies|sensitive_to_smite)\.json|tags/worldgen/biome/is_forest\.json|items/(zombie_spawn_egg|zombie_head|potion|rotten_flesh)\.json|models/item/(zombie_spawn_egg|template_skull|zombie_head|rotten_flesh)\.json|blockstates/(zombie_head|carved_pumpkin|skeleton_skull)\.json|models/block/(skull|carved_pumpkin|orientable)\.json|lang/en_us\.json|pack\.mcmeta|neoforge' ../$O/mc_files.txt); do
  mkdir -p ../$O/data/$(dirname $f); cp ex/$f ../$O/data/$f 2>/dev/null
done
cp ex/pack.mcmeta ../$O/data/ 2>/dev/null
ls ex/data/minecraft/ > ../$O/data/data_dirs.txt
ls ex/data/minecraft/worldgen/ >> ../$O/data/data_dirs.txt
# textures (for reference/derivation)
for f in $(grep -E 'textures/entity/(zombie|happy_ghast|ghast|skeleton|husk|drowned)|textures/item/(zombie_spawn_egg|rotten_flesh|potion|bone|ghast_tear|spawn_egg|flint)|textures/block/(coarse_dirt|dirt|rooted_dirt|podzol|mud|packed_mud|dirt_path)|textures/entity/(skeleton|zombie)/' ../$O/mc_files.txt | grep png); do
  mkdir -p ../$O/assets/$(dirname $f); cp ex/$f ../$O/assets/$f
done
python3 - <<'PY' > ../$O/assets/lang_subset.txt
import json
l=json.load(open('ex/assets/minecraft/lang/en_us.json'))
for k,v in l.items():
    if any(x in k for x in ['giant','harness','happy_ghast','potion.effect','effect.minecraft','zombie_head','spawn_egg']): print(k,'=',v)
PY

JP=javap
dump(){ # $1 simple-name regex, $2 mode
  for c in $(grep -E "/($1)$" mc_classes.txt); do
    n=$(echo $c | tr / .)
    if [ "$2" = c ]; then $JP -cp mc.jar -c -p "$n" > ../$O/javapc/$n.txt 2>&1; else $JP -cp mc.jar -p "$n" > ../$O/javap/$n.txt 2>&1; fi
  done
}
P='Giant|Zombie|Husk|Drowned|Skeleton|AbstractSkeleton|Witch|HappyGhast|IronGolem|Ravager|Monster|Mob|PathfinderMob|LivingEntity|Entity|EntityType|EntityType\$Builder|EntityTypes|EntityDimensions|Attributes|AttributeSupplier|AttributeSupplier\$Builder|AttributeModifier|AttributeInstance|SpawnPlacements|SpawnPlacementTypes|Goal|MeleeAttackGoal|NearestAttackableTargetGoal|HurtByTargetGoal|LookAtPlayerGoal|RandomLookAroundGoal|WaterAvoidingRandomStrollGoal|MoveThroughVillageGoal|AvoidEntityGoal|FollowMobGoal|GoalSelector|TargetingConditions|MobEffect|MobEffects|MobEffectInstance|MobEffectCategory|Potion|Potions|PotionBrewing|PotionBrewing\$Builder|Item|Item\$Properties|Items|BlockItem|SpawnEggItem|DataComponents|Block|Blocks|BlockBehaviour\$Properties|HorizontalDirectionalBlock|SkullBlock|AbstractSkullBlock|WallSkullBlock|SoundEvent|SoundEvents|SoundType|Registry|Registries|BuiltInRegistries|Identifier|ResourceKey|Holder|CreativeModeTab|CreativeModeTab\$Builder|CreativeModeTabs|Feature|Features|FeatureType|FeatureTypes|PlacedFeature|WorldGenLevel|WorldGenRegion|BlockTags|EntityTypeTags|TagKey|DamageSource|DamageSources|ServerLevel|Level|ServerLevelAccessor|SpawnGroupData|EntitySpawnReason|DifficultyInstance|BlockParticleOption|ParticleTypes|ValueInput|ValueOutput|SynchedEntityData|SynchedEntityData\$Builder|EntityDataSerializers|EntityDataAccessor|DyeColor|VillagerHostilesSensor|Villager|AbstractVillager|Camera|GameRenderer|ExplosionInteraction|GameRules|BlockPos|Vec3|AABB|Mth|PathNavigation|GroundPathNavigation|MoveControl|LookControl|BodyRotationControl|ProjectileUtil|Projectile|ThrownTrident|ThrownSplashPotion|Arrow|RangedAttackMob|CrossbowAttackMob|ItemEntity|EquipmentSlot|Difficulty|LootTable|BuiltInLootTables|AnimationState|WalkAnimationState|ClientboundSetEntityMotionPacket|Brain|MemoryModuleType|SensorType|Sensor|NearestVisibleLivingEntitySensor|Mob\$|ResourceLocation|ConversionParams|Heightmap|Heightmap\$Types|RandomSource|SimpleWeightedRandomList|WeightedList|MobCategory|BiomeTags|Biomes|Biome|MobSpawnSettings|MobSpawnSettings\$SpawnerData|GenerationStep|GenerationStep\$Decoration|BlockState|BlockStateProperties|StateDefinition|StateDefinition\$Builder|BlockPlaceContext|LevelReader|LevelAccessor|BlockGetter|Rotation|Mirror|Direction|VoxelShape|Shapes|MapColor|NoteBlockInstrument|PushReaction|ItemStack|InteractionHand|Player|ServerPlayer|LightningBolt|Creeper|Spider|Zoglin|Warden|Ghast|AbstractIllager'
dump "$P" p
D='Giant|GiantMobRenderer|GiantZombieModel|ZombieModel|AbstractZombieModel|ZombieRenderer|AbstractZombieRenderer|HumanoidModel|HumanoidMobRenderer|MobRenderer|LivingEntityRenderer|EntityRenderer|RenderLayer|HappyGhastHarnessModel|HappyGhastRenderer|HappyGhastModel|IronGolemModel|IronGolemRenderer|IronGolemRenderState|ZombieRenderState|HumanoidRenderState|LivingEntityRenderState|EntityRenderState|ArmedEntityRenderState|RavagerModel|RavagerRenderer|RavagerRenderState|EntityModel|Model|ModelPart|PartPose|CubeListBuilder|CubeDeformation|MeshDefinition|PartDefinition|LayerDefinition|ModelLayerLocation|ModelLayers|EntityRendererProvider|EntityRendererProvider\$Context|EntityRenderers|LayerDefinitions|SimpleEquipmentLayer|SaddleLayer|RenderLayerParent|PoseStack|SubmitNodeCollector|OrderedSubmitNodeCollector|RenderTypes|RenderType|OverlayTexture|LightTexture|SkullBlockRenderer|SkullModel|SkullModelBase|IronGolem|Ravager|VillagerHostilesSensor|Potions|MobEffects|Zombie|HappyGhast|Feature|DiskFeature|BlockBlobFeature|IceSpikeFeature|DesertWellFeature|FallenTreeFeature|SpawnPlacements|SkullBlock|AbstractSkullBlock|CarvedPumpkinBlock|AttackDamageMobEffect|MobEffect|Camera|CameraType|MeleeAttackGoal|Witch|Drowned|Husk|Skeleton|AbstractSkeleton|ZombieAttackGoal|QuadrupedModel|WardenModel|WardenRenderer|CamelModel|SpawnEggItem|PotionBrewing|PotionBrewing\$Builder|ColorRGBA|ARGB|Mob|Creeper'
dump "$D" c
cd ..
# greps for patterns
cd W
gp(){ $JP -cp mc.jar -c -p "$1" 2>/dev/null | grep -n -B${3:-25} -A${4:-5} -E "$2" | head -${5:-200}; }
{ echo "== Items ZOMBIE_SPAWN_EGG/ ZOMBIE_HEAD"; gp net.minecraft.world.item.Items 'ZOMBIE_SPAWN_EGG|ZOMBIE_HEAD|ROTTEN_FLESH:' 30 3 200
  echo "== Blocks ZOMBIE_HEAD"; gp net.minecraft.world.level.block.Blocks 'ZOMBIE_HEAD|CARVED_PUMPKIN:' 30 3 200
  echo "== EntityType GIANT"; for c in $(grep -E '/EntityType(s)?$' mc_classes.txt); do gp $(echo $c|tr / .) 'GIANT|HAPPY_GHAST:' 25 3 150; done
  echo "== LayerDefinitions"; for c in $(grep -E '/LayerDefinitions$' mc_classes.txt); do gp $(echo $c|tr / .) 'GIANT|HAPPY_GHAST' 12 3 150; done
  echo "== EntityRenderers"; for c in $(grep -E '/EntityRenderers$' mc_classes.txt); do gp $(echo $c|tr / .) 'GIANT|HAPPY_GHAST' 8 3 80; done
  echo "== Registries FEATURE"; for c in $(grep -E '/Registries$' mc_classes.txt); do $JP -cp mc.jar -p $(echo $c|tr / .) | grep -iE 'feature|potion|effect|sound|entity_type|creative|item|block;' ; done
  echo "== BuiltInRegistries"; for c in $(grep -E '/BuiltInRegistries$' mc_classes.txt); do $JP -cp mc.jar -p $(echo $c|tr / .) | grep -iE 'feature|potion|effect|sound|entity|creative|ITEM|BLOCK' ; done
} > ../$O/greps.txt 2>&1
grep -iE 'harness|giant|happy_?ghast' mc_classes.txt > ../$O/class_hits.txt
grep -E 'levelgen/feature/[A-Za-z]+$' mc_classes.txt >> ../$O/class_hits.txt
cd ..

# ---- loader versions ----
{
echo "== fabric loader"; curl -s https://meta.fabricmc.net/v2/versions/loader | python3 -c "import json,sys;print([x['version'] for x in json.load(sys.stdin)[:8]])"
echo "== fabric game"; curl -s https://meta.fabricmc.net/v2/versions/game | python3 -c "import json,sys;print([x['version'] for x in json.load(sys.stdin)[:15]])"
echo "== fabric api"; curl -s "https://api.modrinth.com/v2/project/fabric-api/version?game_versions=%5B%22$MC%22%5D" | python3 -c "import json,sys;d=json.load(sys.stdin);[print(x['version_number'],x['date_published'],[f['url'] for f in x['files']][0]) for x in d[:6]]"
echo "== neoforge"; curl -s https://maven.neoforged.net/releases/net/neoforged/neoforge/maven-metadata.xml | grep -oE "<version>$MC[^<]*" | tail -15
echo "== arch loom"; curl -s https://maven.architectury.dev/dev/architectury/loom/dev.architectury.loom.gradle.plugin/maven-metadata.xml | grep -oE '<version>[^<]*' | tail -12
curl -s https://maven.architectury.dev/dev/architectury/architectury-loom/maven-metadata.xml | grep -oE '<(latest|release)>[^<]*'
echo "== arch plugin"; curl -s https://maven.architectury.dev/architectury-plugin/architectury-plugin.gradle.plugin/maven-metadata.xml | grep -oE '<version>[^<]*' | tail -8
curl -s https://maven.architectury.dev/architectury-plugin/architectury-plugin/maven-metadata.xml | grep -oE '<version>[^<]*' | tail -5
echo "== fabric loom"; curl -s https://maven.fabricmc.net/net/fabricmc/fabric-loom/maven-metadata.xml | grep -oE '<version>[^<]*' | tail -10
echo "== moddevgradle"; curl -s https://maven.neoforged.net/releases/net/neoforged/moddev/net.neoforged.moddev.gradle.plugin/maven-metadata.xml | grep -oE '<version>[^<]*' | tail -8
echo "== gradle"; curl -s https://services.gradle.org/versions/current | head -c 300
} > $O/versions.txt 2>&1

# fabric api jar
FAPI=$(curl -s "https://api.modrinth.com/v2/project/fabric-api/version?game_versions=%5B%22$MC%22%5D" | python3 -c "import json,sys;d=json.load(sys.stdin);print(d[0]['files'][0]['url'])")
mkdir -p W/fapi && cd W/fapi && curl -sL "$FAPI" -o fapi.jar && unzip -q -o fapi.jar 'META-INF/jars/*' && ls META-INF/jars > ../../$O/fabric_api_modules.txt
for j in META-INF/jars/*.jar; do unzip -Z1 $j | grep '\.class$' | sed "s|^|$(basename $j): |"; done > ../../$O/fabric_api_classes.txt
for pat in 'FabricDefaultAttributeRegistry|BiomeModifications|BiomeSelectors|BiomeModificationContext|EntityRendererRegistry|EntityModelLayerRegistry|FabricItemGroup|ItemGroupEvents|CreativeModeTabEvents|ServerEntityEvents|FabricBrewingRecipeRegistryBuilder|FabricItemGroupEntries|ModelLayerRegistry|EntityRenderers|EntityModelLayers|FabricEntityTypeBuilder|FabricEntityType|SpawnRestriction|BiomeModification$|ModifyEntriesEvent|BuildCallback'; do :; done
grep -E '/(FabricDefaultAttributeRegistry|BiomeModifications|BiomeSelectors|EntityRendererRegistry|EntityModelLayerRegistry|FabricItemGroup|ItemGroupEvents|CreativeModeTabEvents|ServerEntityEvents|FabricBrewingRecipeRegistryBuilder|FabricItemGroupEntries|ModelLayerRegistry|FabricEntityTypeBuilder|FabricEntityType|EntityRendererRegistry|FabricSpawnRestriction|SpawnRestrictionAccessor)(\$[A-Za-z]+)?\.class' ../../$O/fabric_api_classes.txt | while IFS=': ' read jar cls; do
  n=$(echo ${cls%.class} | tr / .); javap -cp META-INF/jars/$jar -p "$n" >> ../../$O/fabric_api_javap.txt 2>&1; done
cd ../..

# neoforge jar
NV=$(curl -s https://maven.neoforged.net/releases/net/neoforged/neoforge/maven-metadata.xml | grep -oE "<version>$MC[^<]*" | sed 's/<version>//' | tail -1)
echo "picked neoforge $NV" >> $O/versions.txt
mkdir -p W/nf && cd W/nf && curl -sL "https://maven.neoforged.net/releases/net/neoforged/neoforge/$NV/neoforge-$NV-universal.jar" -o nf.jar
unzip -Z1 nf.jar | grep '\.class$' | sed 's/\.class$//' > ../../$O/neoforge_classes.txt
for c in $(grep -E '/(RegisterEvent|EntityAttributeCreationEvent|RegisterSpawnPlacementsEvent|BuildCreativeModeTabContentsEvent|RegisterBrewingRecipesEvent|EntityRenderersEvent(\$[A-Za-z]+)?|EntityJoinLevelEvent|Mod|IEventBus|ModContainer|DeferredRegister|FMLEnvironment|Dist|EventBusSubscriber|RegisterEvent\$RegisterHelper|FMLClientSetupEvent|FMLCommonSetupEvent|NeoForge|ModifiableBiomeInfo|BiomeModifiers|NeoForgeRegistries|ClientHooks|ViewportEvent(\$[A-Za-z]+)?|SubscribeEvent)$' ../../$O/neoforge_classes.txt); do
  javap -cp nf.jar -p $(echo $c | tr / .) >> ../../$O/neoforge_javap.txt 2>&1; done
unzip -Z1 nf.jar | grep -vE '\.class$' > ../../$O/neoforge_files.txt
unzip -p nf.jar META-INF/neoforge.mods.toml > ../../$O/neoforge.mods.toml 2>/dev/null
cd ../..
du -sh $O
