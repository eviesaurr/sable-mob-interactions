# Sable Mob Interactions
### *Give Any Mob a Reason to Attack Your Ships, and Let Them Build a Wasteland of Their Own*

[![Version](https://img.shields.io/badge/version-1.1.0-blue.svg)](https://github.com/eviesaurr/sable-mob-interactions)
[![Minecraft](https://img.shields.io/badge/Minecraft-1.21.1-green.svg)](https://minecraft.net)
[![NeoForge](https://img.shields.io/badge/NeoForge-21.1-purple.svg)](https://neoforged.net)
[![Sable](https://img.shields.io/badge/Sable-required-red.svg)](https://modrinth.com/mod/sable)

---

## Table of Contents
- [Overview](#overview)
- [Features at a Glance](#features-at-a-glance)
- [How It Works](#how-it-works)
- [Mob-Driven Vehicles](#mob-driven-vehicles)
- [Spawn Nodes](#spawn-nodes)
- [Custom Cars](#custom-cars)
- [Vehicle Tiers](#vehicle-tiers)
- [Driving Goals](#driving-goals)
- [Commands](#commands)
- [Configuration](#configuration)
- [Building Against This Mod](#building-against-this-mod)
- [Requires](#requires)

---

## Overview

Sable sub-levels power ships, airships, rovers, and other vehicles across the Create Aeronautics and Create Submarine ecosystem, but until now, nothing in the world could actually threaten them. Hostile mobs walked straight past a ship under attack, arrows sailed clean through hulls, and a besieged base could park its fleet in open water without a second thought.

Sable Mob Interactions changes that. It gives vanilla mobs, and any other mod's mobs, the ability to genuinely see, target, and damage Sable sub-levels, without requiring that mod's author to know Sable even exists.

This isn't a per-mob content pack. It's a generic interaction layer: any hostile mob that reaches the fallback tier gets a real, persistent target on a sub-level block, and its own native attack goals (melee, custom weapon systems, whatever the original author wrote) fire naturally against it, using the mob's real animations, sounds, and behavior.

As of 1.1.0 the mobs can fight back on wheels. Custom vehicles spawn out in the world as physics sub-levels, piloted by mobs sitting in Create seats, driving around, chasing players and hunting down anything in their way.

---

## Features at a Glance

- **Vanilla mobs, properly tuned.** Skeletons fire real arrows. Drowned throw tridents if they're holding one. Zombies and most other melee mobs punch through hulls. Creepers explode near your vessel. Guardians charge a beam. Endermen make off with your décor.
- **Any mod's mobs, automatically.** A generic fallback goal gives any other hostile mob a real target and steps back, letting its own attack AI (melee, ranged, or fully custom weapon logic) do the rest, with zero code from that mod's author.
- **Any mod's projectiles, automatically.** Arrows, tridents, bullets, cannonballs, anything extending vanilla's `Projectile` class damages sub-level blocks correctly on impact, regardless of which mod fired them.
- **Mob-driven vehicles.** Cars spawn as physics sub-levels with a mob at the wheel, driven through the Mob Transmitter block.
- **Spawn nodes.** Road structures generate in open biomes on a levelled pad and fill themselves with cars.
- **Bring your own cars.** Drop `.nbt` vehicle files into your world's folder and they join the spawn pool for the tier you choose.
- **Smart drivers.** Pilots chase, wander, avoid obstacles and each other, steer clear of water and cliffs, slow down for corners, and right themselves when flipped.
- **Ship-aware targeting.** Sub-levels only become valid targets if they're built from real propulsion/structural parts (configurable block tag) and have a player nearby, so idle player builds and abandoned test rigs aren't at risk by default.
- **Optional deep compatibility.** If Create: Big Cannons is installed, its own block armor/hardness values scale how many hits a block takes to break.
- **Fully configurable.** Search radius, attack range, fire rate, hit thresholds, creeper griefing, Enderman theft, ship-detection strictness, vehicle tiers, driving goals, and more.

---

## How It Works

Sub-level blocks don't live at the position you see them at. Sable stores each sub-level's real block data in an isolated plot elsewhere in the world, and only visually poses it at its current in-game location. Every interaction this mod adds (finding a nearby block, aiming a projectile, breaking through a hull) correctly transforms between that raw storage space and the sub-level's posed, visual position, so mobs and projectiles hit exactly where they appear to be aiming.

Damage is tracked per block via a shared hit-counter, driving Minecraft's own crack-progress overlay before the block actually breaks, so combat against a ship looks and feels like combat against any other structure.

---

## Mob-Driven Vehicles

Vehicles are ordinary structure templates of a Sable sub-level. When one is needed, the mod places the template in the world, triggers the physics assembler inside it, finds the Create seat, and seats a pilot there. The pilot drives through a Mob Transmitter, which turns the mob's decisions into steering and throttle on the vehicle.

- Cars calibrate their own steering and throttle direction, so most builds drive correctly without setup.
- Each tier has its own `max_speed`.
- Pilots respawn if you leave the area or reload the world, and a car whose pilot is killed is left behind as a wreck.
- Cars that never manage to assemble are turned into wrecks instead of leaving loose blocks around.

---

## Spawn Nodes

Each tier has a node: a road structure that generates in the world and spawns cars around itself when a player gets near.

- Nodes sit on a levelled pad with a naturally tapered edge, so they stay clear of trees and hills.
- The pad matches its surroundings: sandstone and sand in deserts, packed mud, red sand and terracotta in badlands, snow in snowy biomes, gravel and stone on stony shores, and dirt and grass everywhere else.
- Nodes only generate in open biomes (plains, desert, savanna, badlands and similar). The list is a biome tag you can edit: `sablemobinteractions:vehicle_node_biomes`.
- Nodes follow each tier's ring (`min_radius` and `max_radius`) measured from the world origin, and are skipped on steep terrain and near water.
- Cars are placed only on flat, clear ground, never inside trees or each other.
- `/place structure` works for nodes and ignores the biome, ring, flatness and sea level checks, so you can place one anywhere for testing.

Nodes only appear in newly generated chunks, so existing worlds will see them as you explore.

---

## Custom Cars

Put your own `.nbt` car files in `saves/<world>/vehicles/<tier>`, for example `saves/<world>/vehicles/tier_1_scrap`. They join that tier's pool and are rescanned as files change.

- The built-in car is copied into the tier 1 folder the first time it is needed. Delete it from that folder if you don't want it in your pool.
- If a tier's folder is empty, the built-in car is installed again so the tier always has something to spawn.
- Cars need a Create seat for the pilot and a physics assembler block inside the structure.
- Redstone link frequencies are randomised for each spawned car, so two copies of a car never interfere with each other.
- Custom cars are not copied between worlds automatically.

---

## Vehicle Tiers

Tiers are JSON files in `data/sablemobinteractions/vehicle_tiers/`, one per tier (`tier_1_scrap` through `tier_5_boss` are included). A tier controls:

- The ring it spawns in (`min_radius`, `max_radius`) and how rare its sites are
- How many cars appear per site and how far apart they must be
- Top speed
- Which mobs pilot the cars
- Which driving goals the pilots use, in priority order

The `structures` list is for datapack-provided cars. Leave it as `[]` to use only the cars in the world's vehicles folder.

---

## Driving Goals

Goals run in the order listed in the tier JSON. The first goal that wants control steers the car.

| Goal | What it does |
| --- | --- |
| `chase` | Drives at a target player, circling and backing away when too close |
| `wander` | Drives around aimlessly when there is nothing to chase |
| `avoid` | Steers around obstacles and other vehicles |
| `avoid_water` | Steers away from deep water and lava |
| `avoid_cliffs` | Steers away from large drops |
| `slow_turn` | Slows down before sharp turns so cars are less likely to flip |
| `stay_upright` | Reduces speed when tilted, and rights a car that has flipped over and stopped |
| `unstick` | Reverses out of jams |

If you have your own tier JSON files from an older version, add `avoid_water`, `avoid_cliffs`, `slow_turn` and `stay_upright` before `unstick`.

---

## Commands

- `/sablemobinteractions vehicles` lists tracked vehicles, their state and their pilots
- `/sablemobinteractions customcars` lists the custom cars found for each tier
- `/sablemobinteractions clearvehicles` removes every car spawned by the mod, never vehicles built by players (needs SableCat)
- `/sablemobinteractions debugdrive <true|false>` prints what each pilot is thinking

---

## Configuration

Accessible in-game via Mods → Sable Mob Interactions → Config, or by editing `sablemobinteractions-common.toml` directly. Includes:

- Per-mob search radius, attack range, and fire cooldown
- Base hits required to break a block, with optional hardness scaling
- Player-presence range required before a sub-level becomes targetable
- Toggle to require ship-essential blocks before a sub-level is targetable at all
- Toggle for creeper griefing and Enderman theft independently
- Mod namespaces and block tags for extending what counts as stealable or ship-essential

Vehicle behaviour is configured per tier in the tier JSON files, and node biomes in the biome tag.

---

## Building Against This Mod

Want your own mob to have tighter, purpose-built control over how it attacks sub-levels, rather than relying on the generic fallback? Implement `SubLevelAttacker` on your mob's entity class:

```java
public interface SubLevelAttacker {
    void attackSubLevelBlock(Vec3 targetWorldPos, SubLevel subLevel, BlockPos localPos);
    default double getSubLevelAttackRange() { return 15.0; }
    default int getSubLevelAttackCooldownTicks() { return 40; }
}
```

Any mob implementing this interface is picked up automatically, no registration needed on your end.

---

## Requires

- [**Sable**](https://modrinth.com/mod/sable) (required)
- **SableCat** 1.0.3 or newer (required, it provides the lag fix and vehicle cleanup this mod depends on)
- **Create** (required, cars use Create seats)
- **Simulated** (required, the physics assembler that turns spawned cars into sub-levels)
- **Create Connected** (required, the built-in car uses its gearbox)
- **Offroad** (required, the built-in car uses its tires)

Everything else is optional. Create Aeronautics, Create Submarine, and Create: Big Cannons are only used to enhance specific behaviors when present, and the mod works fully without them.
