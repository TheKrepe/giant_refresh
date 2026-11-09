# Giant Refresh

**Author:** Star_1mp · **Minecraft:** 26.3 · **Loaders:** Fabric & NeoForge

Giant Refresh turns the forgotten, unused Giant into a real inhabitant of the world — a rare,
huge and dangerous mob that still looks like something Mojang could have added.

## The Giant
- ~12 blocks tall, 400 HP, slow and heavy. Every step thumps, kicks up dirt and lightly shakes the camera.
- **Three telegraphed attacks** (each has a clear wind-up so you can dodge):
  - *Slam* — raises both fists overhead, trembles, then crashes them down in front of it.
  - *Stomp* — lifts a leg for a long moment, then sends a shockwave through the ground.
  - *Scoop* — a low sweep that flings small creatures and players into the air.
- Breaks leaves, flowers, tall grass, fences, wooden doors and trapdoors in its way (respects `mobGriefing`).
- Crushes plants and trampled crops underfoot, can squash small animals, leaves coarse-dirt footprints.

## The harness hat & riders
Many Giants wear a **harness hat** — the Happy Ghast harness in one of the 16 vanilla colours, with the
same goggles (up when empty, pulled down when ridden). The hat is a mobile fortress for 2–3 hostile riders:
zombies (jump down to fight nearby enemies), skeletons (shoot from above), husks (do not burn by day),
drowned with tridents and witches. Kill the riders first — the fight gets much easier.

## In the world
- **Footprint trails** generate in plains, forests, meadows, savannas and taigas. Prints get fresher and
  closer together along the trail; rare *camps* end with a **sleeping Giant** next to its lair.
- Sleeping Giants wake up when you sprint nearby, come too close or attack them — sneak past!
- Rare natural spawns in the same biomes (at night; daytime Giants are found napping), sometimes with a small zombie horde.
- Zombies gather around Giants, skeletons stay close for cover, iron golems fight them, villagers flee.
- A vanilla `minecraft:giant` (e.g. from `/summon`) is automatically replaced with the new Giant.

## Loot
- Lots of rotten flesh.
- **Giant Tooth** (rare) → brew *Awkward Potion + Giant Tooth* = **Potion of Growth**
  (redstone extends it, gunpowder / dragon's breath work as usual). You smoothly grow to about 5 blocks
  tall, move a bit slower but hit harder and reach further; you shrink back just as smoothly.
- **Giant Head** (rare, guaranteed from a charged creeper) — a huge decorative head block.

## Config
`config/giant_refresh.json` — Giant stats, block breaking, footprints, spawn rates, hat/rider chances,
mob behaviour, screen shake and Potion of Growth strength.

## Building
Each loader is its own Gradle build sharing `common/`:
```
cd fabric && gradle build      # -> fabric/build/libs
cd neoforge && gradle build    # -> neoforge/build/libs
```
GitHub Actions builds both on every push and uploads them as one zip artifact.
