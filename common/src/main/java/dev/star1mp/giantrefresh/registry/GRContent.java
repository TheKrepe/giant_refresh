package dev.star1mp.giantrefresh.registry;

import com.mojang.serialization.MapCodec;
import dev.star1mp.giantrefresh.GiantRefresh;
import dev.star1mp.giantrefresh.block.GiantHeadBlock;
import dev.star1mp.giantrefresh.effect.GrowthEffect;
import dev.star1mp.giantrefresh.entity.GiantEntity;
import dev.star1mp.giantrefresh.worldgen.GiantTrailFeature;
import java.util.ArrayList;
import java.util.Collections;
import java.util.List;
import java.util.function.Function;
import java.util.function.Supplier;
import net.minecraft.core.Holder;
import net.minecraft.core.Registry;
import net.minecraft.core.registries.BuiltInRegistries;
import net.minecraft.core.registries.Registries;
import net.minecraft.resources.Identifier;
import net.minecraft.resources.ResourceKey;
import net.minecraft.sounds.SoundEvent;
import net.minecraft.tags.TagKey;
import net.minecraft.world.effect.MobEffect;
import net.minecraft.world.effect.MobEffectInstance;
import net.minecraft.world.entity.EntityType;
import net.minecraft.world.entity.MobCategory;
import net.minecraft.world.item.BlockItem;
import net.minecraft.world.item.CreativeModeTab;
import net.minecraft.world.item.Item;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.Items;
import net.minecraft.world.item.Rarity;
import net.minecraft.world.item.SpawnEggItem;
import net.minecraft.world.item.alchemy.Potion;
import net.minecraft.world.level.biome.Biome;
import net.minecraft.world.level.block.Block;
import net.minecraft.world.level.block.SoundType;
import net.minecraft.world.level.block.state.BlockBehaviour;
import net.minecraft.world.level.block.state.properties.NoteBlockInstrument;
import net.minecraft.world.level.levelgen.feature.Feature;
import net.minecraft.world.level.material.MapColor;
import net.minecraft.world.level.material.PushReaction;

/** Every registry object of the mod, in registration order. */
public final class GRContent {
    private static final List<RegEntry<?>> ENTRIES = new ArrayList<>();

    /** Order in which loaders must register the registries (dependencies first). */
    public static final List<ResourceKey<? extends Registry<?>>> ORDER = List.of(
            Registries.SOUND_EVENT,
            Registries.BLOCK,
            Registries.ENTITY_TYPE,
            Registries.ITEM,
            Registries.MOB_EFFECT,
            Registries.POTION,
            Registries.FEATURE_TYPE);

    // ---------------------------------------------------------------- sounds
    public static final RegEntry<SoundEvent> GIANT_AMBIENT = sound("entity.giant.ambient");
    public static final RegEntry<SoundEvent> GIANT_HURT = sound("entity.giant.hurt");
    public static final RegEntry<SoundEvent> GIANT_DEATH = sound("entity.giant.death");
    public static final RegEntry<SoundEvent> GIANT_STEP = sound("entity.giant.step");
    public static final RegEntry<SoundEvent> GIANT_STOMP = sound("entity.giant.stomp");
    public static final RegEntry<SoundEvent> GIANT_PUNCH = sound("entity.giant.punch");
    public static final RegEntry<SoundEvent> GIANT_SWING = sound("entity.giant.swing");
    public static final RegEntry<SoundEvent> GIANT_ROAR = sound("entity.giant.roar");
    public static final RegEntry<SoundEvent> GIANT_SNORE = sound("entity.giant.snore");

    // ---------------------------------------------------------------- blocks
    public static final RegEntry<Block> GIANT_HEAD_BLOCK = add(Registries.BLOCK, "giant_head",
            () -> new GiantHeadBlock(BlockBehaviour.Properties.of()
                    .setId(key(Registries.BLOCK, "giant_head"))
                    .mapColor(MapColor.COLOR_GREEN)
                    .instrument(NoteBlockInstrument.ZOMBIE)
                    .strength(1.5F)
                    .sound(SoundType.MUD)
                    .pushReaction(PushReaction.POPPED)));

    // ---------------------------------------------------------------- entities
    public static final RegEntry<EntityType<GiantEntity>> GIANT = add(Registries.ENTITY_TYPE, "giant",
            () -> EntityType.Builder.<GiantEntity>of(GiantEntity::new, MobCategory.MONSTER)
                    .sized(3.6F, 12.0F)
                    .eyeHeight(10.44F)
                    .clientTrackingRange(12)
                    .notInPeaceful()
                    .build(key(Registries.ENTITY_TYPE, "giant")));

    // ---------------------------------------------------------------- items
    public static final RegEntry<Item> GIANT_TOOTH = item("giant_tooth",
            p -> new Item(p.rarity(Rarity.UNCOMMON)));
    public static final RegEntry<Item> GIANT_HEAD = item("giant_head",
            p -> new BlockItem(GIANT_HEAD_BLOCK.get(), p.rarity(Rarity.UNCOMMON).useBlockDescriptionPrefix()));
    public static final RegEntry<Item> GIANT_SPAWN_EGG = item("giant_spawn_egg",
            p -> new SpawnEggItem(p.spawnEgg(GIANT.get())));

    // ---------------------------------------------------------------- effects & potions
    public static final RegEntry<MobEffect> GROWTH = add(Registries.MOB_EFFECT, "growth", GrowthEffect::new);
    public static final RegEntry<Potion> GROWTH_POTION = add(Registries.POTION, "growth",
            () -> new Potion("growth", new MobEffectInstance(growthHolder(), 3600)));
    public static final RegEntry<Potion> LONG_GROWTH_POTION = add(Registries.POTION, "long_growth",
            () -> new Potion("growth", new MobEffectInstance(growthHolder(), 9600)));

    // ---------------------------------------------------------------- worldgen
    public static final RegEntry<MapCodec<? extends Feature>> GIANT_TRAIL_FEATURE = add(Registries.FEATURE_TYPE,
            "giant_trail", () -> GiantTrailFeature.CODEC);

    // ---------------------------------------------------------------- vanilla creative tabs
    public static final ResourceKey<CreativeModeTab> TAB_SPAWN_EGGS = vanillaTab("spawn_eggs");
    public static final ResourceKey<CreativeModeTab> TAB_INGREDIENTS = vanillaTab("ingredients");
    public static final ResourceKey<CreativeModeTab> TAB_FUNCTIONAL_BLOCKS = vanillaTab("functional_blocks");

    /**
     * Where each mod item goes in the vanilla creative tabs (the Potions of Growth and the
     * Arrows of Growth are added to Food &amp; Drinks / Combat automatically by vanilla).
     */
    public interface TabOutput {
        void insertAfter(Item anchor, ItemStack stack);
    }

    public static void fillCreativeTab(ResourceKey<CreativeModeTab> tab, TabOutput out) {
        if (tab.equals(TAB_SPAWN_EGGS)) {
            out.insertAfter(Items.GHAST_SPAWN_EGG, new ItemStack(GIANT_SPAWN_EGG.get()));
        } else if (tab.equals(TAB_INGREDIENTS)) {
            out.insertAfter(Items.GHAST_TEAR, new ItemStack(GIANT_TOOTH.get()));
        } else if (tab.equals(TAB_FUNCTIONAL_BLOCKS)) {
            out.insertAfter(Items.PIGLIN_HEAD, new ItemStack(GIANT_HEAD.get()));
        }
    }

    private static ResourceKey<CreativeModeTab> vanillaTab(String name) {
        return ResourceKey.create(Registries.CREATIVE_MODE_TAB, Identifier.withDefaultNamespace(name));
    }

    // ---------------------------------------------------------------- tags
    public static final TagKey<Block> GIANT_BREAKABLE = TagKey.create(Registries.BLOCK, GiantRefresh.id("giant_breakable"));
    public static final TagKey<Block> GIANT_CRUSHABLE = TagKey.create(Registries.BLOCK, GiantRefresh.id("giant_crushable"));
    public static final TagKey<Block> FOOTPRINT_REPLACEABLE = TagKey.create(Registries.BLOCK, GiantRefresh.id("footprint_replaceable"));
    public static final TagKey<Biome> GIANT_BIOMES = TagKey.create(Registries.BIOME, GiantRefresh.id("giant_habitat"));
    public static final TagKey<EntityType<?>> GIANT_RIDERS = TagKey.create(Registries.ENTITY_TYPE, GiantRefresh.id("giant_riders"));

    private static Holder<MobEffect> growthHolder;

    private GRContent() {}

    public static void bootstrap() {
        // Touching the class is enough: all static entries are now queued.
    }

    public static Holder<MobEffect> growthHolder() {
        if (growthHolder == null) {
            growthHolder = BuiltInRegistries.MOB_EFFECT.wrapAsHolder(GROWTH.get());
        }
        return growthHolder;
    }

    public static List<RegEntry<?>> entries() {
        return Collections.unmodifiableList(ENTRIES);
    }

    /** Entries of one registry, in declaration order. */
    public static List<RegEntry<?>> entries(ResourceKey<? extends Registry<?>> registry) {
        List<RegEntry<?>> out = new ArrayList<>();
        for (RegEntry<?> e : ENTRIES) {
            if (e.registry().equals(registry)) {
                out.add(e);
            }
        }
        return out;
    }

    // ---------------------------------------------------------------- helpers
    public static <T> ResourceKey<T> key(ResourceKey<? extends Registry<T>> registry, String name) {
        return ResourceKey.create(registry, GiantRefresh.id(name));
    }

    private static <T> RegEntry<T> add(ResourceKey<? extends Registry<? super T>> registry, String name, Supplier<? extends T> factory) {
        RegEntry<T> entry = new RegEntry<>(registry, GiantRefresh.id(name), factory);
        ENTRIES.add(entry);
        return entry;
    }

    private static RegEntry<SoundEvent> sound(String name) {
        Identifier id = GiantRefresh.id(name);
        return add(Registries.SOUND_EVENT, name, () -> SoundEvent.createVariableRangeEvent(id));
    }

    private static RegEntry<Item> item(String name, Function<Item.Properties, Item> factory) {
        return add(Registries.ITEM, name, () -> factory.apply(new Item.Properties().setId(key(Registries.ITEM, name))));
    }
}
