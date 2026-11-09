#!/usr/bin/env python3
"""
Generates Giant Refresh data/asset JSON files and textures.

Usage: python3 tools/gen_resources.py <path-to-extracted-vanilla-assets/minecraft/textures>
Textures are derived from the vanilla zombie skin / spawn egg so the mod keeps a vanilla look.
"""
import json
import os
import random
import sys

from PIL import Image

ROOT = os.path.join(os.path.dirname(__file__), '..', 'common', 'src', 'main', 'resources')
NEO = os.path.join(os.path.dirname(__file__), '..', 'neoforge', 'src', 'main', 'resources')
NS = 'giant_refresh'


def write(path, obj, base=ROOT):
    full = os.path.join(base, path)
    os.makedirs(os.path.dirname(full), exist_ok=True)
    with open(full, 'w', encoding='utf-8') as f:
        json.dump(obj, f, indent=2, ensure_ascii=False)
        f.write('\n')


# ----------------------------------------------------------------------------- data
def gen_data():
    # --- loot table -----------------------------------------------------------
    write(f'data/{NS}/loot_table/entities/giant.json', {
        "type": "minecraft:entity",
        "pools": [
            {
                "rolls": 1,
                "entries": [{
                    "type": "minecraft:item",
                    "name": "minecraft:rotten_flesh",
                    "modifier": [
                        {"type": "minecraft:set_count",
                         "count": {"type": "minecraft:uniform", "min": 8, "max": 16}},
                        {"type": "minecraft:enchanted_count_increase",
                         "count": {"type": "minecraft:uniform", "min": 0.0, "max": 3.0},
                         "enchantment": "minecraft:looting"}
                    ]
                }]
            },
            {
                "rolls": 1,
                "condition": {"type": "minecraft:all_of", "terms": [
                    {"type": "minecraft:killed_by_player"},
                    {"type": "minecraft:random_chance_with_enchanted_bonus",
                     "enchanted_chance": {"type": "minecraft:linear", "base": 0.30, "per_level_above_first": 0.05},
                     "enchantment": "minecraft:looting",
                     "unenchanted_chance": 0.25}
                ]},
                "entries": [{"type": "minecraft:item", "name": f"{NS}:giant_tooth",
                             "modifier": [{"type": "minecraft:set_count",
                                           "count": {"type": "minecraft:uniform", "min": 1, "max": 2}}]}]
            },
            {
                "rolls": 1,
                "condition": {"type": "minecraft:all_of", "terms": [
                    {"type": "minecraft:killed_by_player"},
                    {"type": "minecraft:random_chance_with_enchanted_bonus",
                     "enchanted_chance": {"type": "minecraft:linear", "base": 0.13, "per_level_above_first": 0.03},
                     "enchantment": "minecraft:looting",
                     "unenchanted_chance": 0.10}
                ]},
                "entries": [{"type": "minecraft:item", "name": f"{NS}:giant_head"}]
            }
        ],
        "random_sequence": f"{NS}:entities/giant"
    })
    write(f'data/{NS}/loot_table/blocks/giant_head.json', {
        "type": "minecraft:block",
        "pools": [{
            "rolls": 1,
            "condition": {"type": "minecraft:survives_explosion"},
            "entries": [{"type": "minecraft:item", "name": f"{NS}:giant_head"}]
        }],
        "random_sequence": f"{NS}:blocks/giant_head"
    })

    # --- brewing ---------------------------------------------------------------
    def brew(name, in_item, in_potion, reagent, out_item, out_potion):
        write(f'data/{NS}/recipe/brewing/{name}.json', {
            "type": "minecraft:brewing",
            "input": {"item": in_item, "potion_contents": {"potions": in_potion}},
            "output": {"components": {"minecraft:potion_contents": {"potion": out_potion}}, "id": out_item},
            "reagent": {"item": reagent}
        })

    kinds = [('potion', 'minecraft:potion'), ('splash_potion', 'minecraft:splash_potion'),
             ('lingering_potion', 'minecraft:lingering_potion')]
    for short, item in kinds:
        brew(f'{short}_awkward_giant_tooth', item, 'minecraft:awkward', f'{NS}:giant_tooth', item, f'{NS}:growth')
        brew(f'{short}_growth_redstone', item, f'{NS}:growth', 'minecraft:redstone', item, f'{NS}:long_growth')
    for p in ('growth', 'long_growth'):
        brew(f'potion_{p}_gunpowder', 'minecraft:potion', f'{NS}:{p}', 'minecraft:gunpowder',
             'minecraft:splash_potion', f'{NS}:{p}')
        brew(f'splash_potion_{p}_dragon_breath', 'minecraft:splash_potion', f'{NS}:{p}', 'minecraft:dragon_breath',
             'minecraft:lingering_potion', f'{NS}:{p}')

    # --- worldgen --------------------------------------------------------------
    write(f'data/{NS}/worldgen/feature/giant_footprints.json', {
        "type": f"{NS}:giant_trail", "min_prints": 4, "max_prints": 7, "giant_chance": 0.0, "freshness": 0.35})
    write(f'data/{NS}/worldgen/feature/giant_camp.json', {
        "type": f"{NS}:giant_trail", "min_prints": 6, "max_prints": 9, "giant_chance": 1.0, "freshness": 1.0})
    for name, chance in (('giant_footprints', 70), ('giant_camp', 520)):
        write(f'data/{NS}/worldgen/placed_feature/{name}.json', {
            "feature": f"{NS}:{name}",
            "placement": [
                {"type": "minecraft:rarity_filter", "chance": chance},
                {"type": "minecraft:in_square"},
                {"type": "minecraft:heightmap", "heightmap": "MOTION_BLOCKING_NO_LEAVES"},
                {"type": "minecraft:biome"}
            ]
        })

    # NeoForge biome modifier (Fabric adds the same features in code)
    write(f'data/{NS}/neoforge/biome_modifier/giant_trails.json', {
        "type": "neoforge:add_features",
        "biomes": f"#{NS}:giant_habitat",
        "features": [f"{NS}:giant_footprints", f"{NS}:giant_camp"],
        "step": "vegetal_decoration"
    }, base=NEO)

    # --- tags ------------------------------------------------------------------
    crushable = ["#minecraft:small_flowers", "#minecraft:saplings", "#minecraft:crops",
                 "minecraft:short_grass", "minecraft:fern", "minecraft:dead_bush", "minecraft:tall_grass",
                 "minecraft:large_fern", "minecraft:sunflower", "minecraft:lilac", "minecraft:rose_bush",
                 "minecraft:peony", "minecraft:pitcher_plant", "minecraft:bush", "minecraft:firefly_bush",
                 "minecraft:leaf_litter", "minecraft:short_dry_grass", "minecraft:tall_dry_grass",
                 "minecraft:pink_petals", "minecraft:wildflowers", "minecraft:sweet_berry_bush",
                 "minecraft:brown_mushroom", "minecraft:red_mushroom"]
    write(f'data/{NS}/tags/block/giant_crushable.json', {"values": crushable})
    write(f'data/{NS}/tags/block/giant_breakable.json', {"values": [
        f"#{NS}:giant_crushable", "#minecraft:leaves", "#minecraft:wooden_fences", "#minecraft:fence_gates",
        "#minecraft:wooden_doors", "#minecraft:wooden_trapdoors", "#minecraft:wool_carpets",
        "minecraft:vine", "minecraft:cobweb", "minecraft:bamboo", "minecraft:sugar_cane",
        "minecraft:glow_lichen", "minecraft:hanging_roots"]})
    write(f'data/{NS}/tags/block/footprint_replaceable.json', {"values": [
        "minecraft:grass_block", "minecraft:dirt", "minecraft:podzol", "minecraft:coarse_dirt",
        "minecraft:mycelium", "minecraft:rooted_dirt"]})
    write(f'data/{NS}/tags/worldgen/biome/giant_habitat.json', {"values": [
        "minecraft:plains", "minecraft:sunflower_plains", "minecraft:meadow", "minecraft:savanna",
        "minecraft:savanna_plateau", "minecraft:forest", "minecraft:flower_forest", "minecraft:birch_forest",
        "minecraft:old_growth_birch_forest", "minecraft:dark_forest", "minecraft:taiga",
        "minecraft:old_growth_pine_taiga", "minecraft:old_growth_spruce_taiga"]})
    write(f'data/{NS}/tags/entity_type/giant_riders.json', {"values": [
        "minecraft:zombie", "minecraft:skeleton", "minecraft:husk", "minecraft:drowned", "minecraft:witch"]})
    for tag in ('undead', 'sensitive_to_smite'):
        write(f'data/minecraft/tags/entity_type/{tag}.json', {"replace": False, "values": [f"{NS}:giant"]})


# ----------------------------------------------------------------------------- assets (json)
def gen_assets_json():
    a = f'assets/{NS}'
    write(f'{a}/items/giant_tooth.json', {"model": {"type": "minecraft:model", "model": f"{NS}:item/giant_tooth"}})
    write(f'{a}/models/item/giant_tooth.json', {"parent": "minecraft:item/generated",
                                                 "textures": {"layer0": f"{NS}:item/giant_tooth"}})
    write(f'{a}/items/giant_spawn_egg.json', {"model": {"type": "minecraft:model", "model": f"{NS}:item/giant_spawn_egg"}})
    write(f'{a}/models/item/giant_spawn_egg.json', {"parent": "minecraft:item/generated",
                                                     "textures": {"layer0": f"{NS}:item/giant_spawn_egg"}})
    write(f'{a}/items/giant_head.json', {"model": {"type": "minecraft:model", "model": f"{NS}:block/giant_head"}})
    write(f'{a}/models/block/giant_head.json', {
        "parent": "minecraft:block/cube",
        "textures": {
            "particle": f"{NS}:block/giant_head_front",
            "north": f"{NS}:block/giant_head_front",
            "south": f"{NS}:block/giant_head_back",
            "east": f"{NS}:block/giant_head_right",
            "west": f"{NS}:block/giant_head_left",
            "up": f"{NS}:block/giant_head_top",
            "down": f"{NS}:block/giant_head_bottom"
        }
    })
    write(f'{a}/blockstates/giant_head.json', {"variants": {
        "facing=north": {"model": f"{NS}:block/giant_head"},
        "facing=east": {"model": f"{NS}:block/giant_head", "y": 90},
        "facing=south": {"model": f"{NS}:block/giant_head", "y": 180},
        "facing=west": {"model": f"{NS}:block/giant_head", "y": 270}}})

    def ev(sounds, subtitle, **extra):
        d = {"sounds": sounds}
        if subtitle:
            d["subtitle"] = subtitle
        d.update(extra)
        return d

    def s(name, pitch=1.0, volume=1.0, dist=None):
        d = {"name": name, "pitch": pitch, "volume": volume}
        if dist:
            d["attenuation_distance"] = dist
        return d

    sub = f'subtitles.{NS}.entity.giant.'
    write(f'{a}/sounds.json', {
        "entity.giant.ambient": ev([s(f"minecraft:mob/zombie/say{i}", 0.5, 1.0, 32) for i in (1, 2, 3)], sub + "ambient"),
        "entity.giant.hurt": ev([s(f"minecraft:mob/zombie/hurt{i}", 0.55, 1.0, 32) for i in (1, 2)], sub + "hurt"),
        "entity.giant.death": ev([s("minecraft:mob/zombie/death", 0.45, 1.0, 48)], sub + "death"),
        "entity.giant.step": ev([s(f"minecraft:mob/ravager/step{i}", 0.6, 1.0, 40) for i in (1, 2, 3, 4, 5)], sub + "step"),
        "entity.giant.stomp": ev([s("minecraft:item/mace/smash_ground_heavy", 0.7, 1.0, 64)], sub + "stomp"),
        "entity.giant.punch": ev([s("minecraft:mob/irongolem/throw", 0.55, 1.0, 48),
                                  s("minecraft:item/mace/smash_ground_heavy", 0.9, 0.8, 48)], sub + "punch"),
        "entity.giant.swing": ev([s("minecraft:mob/ravager/bite1", 0.5, 0.9, 32),
                                  s("minecraft:mob/ravager/bite2", 0.5, 0.9, 32)], sub + "swing"),
        "entity.giant.roar": ev([s(f"minecraft:mob/ravager/roar{i}", 0.6, 1.0, 64) for i in (1, 2, 3, 4)], sub + "roar"),
        "entity.giant.snore": ev([s(f"minecraft:mob/zombie/say{i}", 0.3, 0.45, 16) for i in (1, 2, 3)], sub + "snore"),
    })


LANG_EN = {
    "entity.giant_refresh.giant": "Giant",
    "item.giant_refresh.giant_tooth": "Giant Tooth",
    "item.giant_refresh.giant_spawn_egg": "Giant Spawn Egg",
    "block.giant_refresh.giant_head": "Giant Head",
    "effect.giant_refresh.growth": "Growth",
    "item.minecraft.potion.effect.growth": "Potion of Growth",
    "item.minecraft.splash_potion.effect.growth": "Splash Potion of Growth",
    "item.minecraft.lingering_potion.effect.growth": "Lingering Potion of Growth",
    "item.minecraft.tipped_arrow.effect.growth": "Arrow of Growth",
    "subtitles.giant_refresh.entity.giant.ambient": "Giant groans",
    "subtitles.giant_refresh.entity.giant.hurt": "Giant hurts",
    "subtitles.giant_refresh.entity.giant.death": "Giant dies",
    "subtitles.giant_refresh.entity.giant.step": "Heavy footsteps",
    "subtitles.giant_refresh.entity.giant.stomp": "Giant stomps",
    "subtitles.giant_refresh.entity.giant.punch": "Giant slams",
    "subtitles.giant_refresh.entity.giant.swing": "Giant swings",
    "subtitles.giant_refresh.entity.giant.roar": "Giant roars",
    "subtitles.giant_refresh.entity.giant.snore": "Giant snores",
}

LANG_RU = {
    "entity.giant_refresh.giant": "Гигант",
    "item.giant_refresh.giant_tooth": "Зуб Гиганта",
    "item.giant_refresh.giant_spawn_egg": "Яйцо призыва Гиганта",
    "block.giant_refresh.giant_head": "Голова Гиганта",
    "effect.giant_refresh.growth": "Увеличение",
    "item.minecraft.potion.effect.growth": "Зелье увеличения",
    "item.minecraft.splash_potion.effect.growth": "Взрывное зелье увеличения",
    "item.minecraft.lingering_potion.effect.growth": "Оседающее зелье увеличения",
    "item.minecraft.tipped_arrow.effect.growth": "Стрела увеличения",
    "subtitles.giant_refresh.entity.giant.ambient": "Гигант стонет",
    "subtitles.giant_refresh.entity.giant.hurt": "Гигант ранен",
    "subtitles.giant_refresh.entity.giant.death": "Гигант умирает",
    "subtitles.giant_refresh.entity.giant.step": "Тяжёлые шаги",
    "subtitles.giant_refresh.entity.giant.stomp": "Гигант топает",
    "subtitles.giant_refresh.entity.giant.punch": "Гигант бьёт",
    "subtitles.giant_refresh.entity.giant.swing": "Гигант замахивается",
    "subtitles.giant_refresh.entity.giant.roar": "Гигант ревёт",
    "subtitles.giant_refresh.entity.giant.snore": "Гигант храпит",
}


# ----------------------------------------------------------------------------- textures
def tex_path(rel):
    full = os.path.join(ROOT, f'assets/{NS}/textures', rel)
    os.makedirs(os.path.dirname(full), exist_ok=True)
    return full


def clamp(v):
    return max(0, min(255, int(v)))


def shade(c, f):
    return (clamp(c[0] * f), clamp(c[1] * f), clamp(c[2] * f), c[3])


def mix(c1, c2, t):
    return tuple(clamp(c1[i] * (1 - t) + c2[i] * t) for i in range(3)) + (c1[3],)


MOSS = [(58, 86, 38, 255), (72, 104, 44, 255), (49, 72, 33, 255), (86, 118, 52, 255)]
GRIME = (64, 56, 40, 255)


def gen_giant_skin(vanilla):
    rnd = random.Random(1337)
    src = Image.open(os.path.join(vanilla, 'entity/zombie/zombie.png')).convert('RGBA')
    img = src.copy()
    px = img.load()
    w, h = img.size

    def is_cloth(c):
        r, g, b, a = c
        if a == 0:
            return False
        cyan = b > 120 and g > 110 and r < 90
        purple = b > 110 and r > 50 and g < 90
        return cyan or purple

    # Moss & grime stains on the clothes, in small clusters (like the reference art).
    for _ in range(70):
        x0, y0 = rnd.randrange(0, 56), rnd.randrange(16, 32)
        for _ in range(rnd.randint(2, 6)):
            x, y = x0 + rnd.randint(-1, 1), y0 + rnd.randint(-1, 1)
            if 0 <= x < w and 0 <= y < h and is_cloth(px[x, y]):
                px[x, y] = mix(px[x, y], rnd.choice(MOSS), 0.75)
    for _ in range(40):
        x, y = rnd.randrange(0, 56), rnd.randrange(16, 32)
        if is_cloth(px[x, y]):
            px[x, y] = mix(px[x, y], GRIME, 0.35)

    # Torn trousers: lower part of both legs shows green skin with a ragged edge; boots kept.
    skin = [(px[x, y]) for x in range(8, 16) for y in range(8, 16) if px[x, y][3] and px[x, y][1] > px[x, y][2]]
    skin = skin or [(83, 120, 58, 255)]
    for x in range(0, 16):
        tear = 25 + rnd.randint(0, 2)
        for y in range(tear, 30):
            base = rnd.choice(skin)
            px[x, y] = shade(base, 0.92 + rnd.random() * 0.12)
        # a couple of dangling scraps
        if rnd.random() < 0.35:
            px[x, tear] = shade(px[x, tear - 1], 0.85)
    # darker, worn boots
    for x in range(0, 16):
        for y in range(30, 32):
            if px[x, y][3]:
                px[x, y] = shade(px[x, y], 0.8)
    # slightly older, mossier skin overall on head & arms
    for x in range(w):
        for y in range(h):
            c = px[x, y]
            if c[3] and not is_cloth(c) and c[1] > c[0] and c[1] > c[2] and rnd.random() < 0.08:
                px[x, y] = mix(c, rnd.choice(MOSS), 0.5)
    img.save(tex_path('entity/giant/giant.png'))
    return img


def gen_head_block(skin):
    faces = {
        'front': (8, 8), 'right': (0, 8), 'left': (16, 8), 'back': (24, 8), 'top': (8, 0), 'bottom': (16, 0)
    }
    for name, (u, v) in faces.items():
        face = skin.crop((u, v, u + 8, v + 8)).resize((16, 16), Image.NEAREST)
        face.save(tex_path(f'block/giant_head_{name}.png'))
    # mod icon: the face, large
    icon = skin.crop((8, 8, 16, 16)).resize((128, 128), Image.NEAREST)
    icon.save(os.path.join(ROOT, f'assets/{NS}/icon.png'))


def gen_spawn_egg(vanilla):
    src = Image.open(os.path.join(vanilla, 'item/zombie_spawn_egg.png')).convert('RGBA')
    px = src.load()
    out = src.copy()
    o = out.load()
    for x in range(16):
        for y in range(16):
            r, g, b, a = px[x, y]
            if not a:
                continue
            lum = (r + g + b) / 3
            if b > r and b >= g:  # cyan shell -> mossy green
                o[x, y] = shade((74, 108, 46, a), 0.55 + lum / 255 * 0.7)
            elif g > r and g > b:  # green spots -> harness brown
                o[x, y] = shade((120, 74, 40, a), 0.6 + lum / 255 * 0.6)
    out.save(tex_path('item/giant_spawn_egg.png'))


def draw(rows, palette, path):
    img = Image.new('RGBA', (len(rows[0]), len(rows)), (0, 0, 0, 0))
    p = img.load()
    for y, row in enumerate(rows):
        for x, ch in enumerate(row):
            if ch in palette:
                p[x, y] = palette[ch]
    img.save(path)


def gen_tooth():
    pal = {
        'o': (58, 44, 34, 255),     # outline
        'r': (126, 62, 52, 255),    # root / gum
        'R': (94, 44, 38, 255),
        'W': (242, 236, 210, 255),  # enamel highlight
        'w': (220, 210, 176, 255),
        's': (184, 170, 128, 255),  # shadow
        'S': (146, 132, 96, 255),
    }
    rows = [
        "................",
        "......oooo......",
        ".....orRrRo.....",
        "....orRrrRro....",
        "....oWwwwwso....",
        "....oWWwwwso....",
        "....oWwwwssSo...",
        "....oWwwwssSo...",
        ".....oWwwsSo....",
        ".....oWwwsSo....",
        ".....oWwssSo....",
        "......oWwsSo....",
        "......oWssSo....",
        ".......owSo.....",
        "........oo......",
        "................",
    ]
    draw(rows, pal, tex_path('item/giant_tooth.png'))


def gen_effect_icon():
    pal = {
        'o': (30, 46, 22, 255),
        'g': (126, 196, 82, 255),
        'G': (172, 230, 118, 255),
        'd': (82, 140, 52, 255),
    }
    rows = [
        "..................",
        "........oo........",
        ".......oGGo.......",
        "......oGGggo......",
        ".....oGGgggdo.....",
        "....oGGgggggdo....",
        "...oooogggdoooo...",
        "......oGggdo......",
        "......oGggdo......",
        "........oo........",
        ".......oGGo.......",
        "......oGGggo......",
        ".....oGGgggdo.....",
        "....oooGggdooo....",
        "......oGggdo......",
        "......oGggdo......",
        "......oooooo......",
        "..................",
    ]
    draw(rows, pal, tex_path('mob_effect/growth.png'))


def main():
    vanilla = sys.argv[1] if len(sys.argv) > 1 else None
    gen_data()
    gen_assets_json()
    write(f'assets/{NS}/lang/en_us.json', LANG_EN)
    write(f'assets/{NS}/lang/ru_ru.json', LANG_RU)
    # textures: tools/gen_giant_texture.py and tools/gen_items.py
    print('done')


if __name__ == '__main__':
    main()
