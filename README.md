# ⚔️ Sable Mob Interactions
### *Give Any Mob a Reason to Attack Your Ships*

[![Version](https://img.shields.io/badge/version-1.0.0-blue.svg)](https://github.com/eviesaurr/sable-mob-interactions)
[![Minecraft](https://img.shields.io/badge/Minecraft-1.21.1-green.svg)](https://minecraft.net)
[![NeoForge](https://img.shields.io/badge/NeoForge-21.1-purple.svg)](https://neoforged.net)
[![Sable](https://img.shields.io/badge/Sable-required-red.svg)](https://modrinth.com/mod/sable)

---

## Table of Contents
- [Overview](#overview)
- [Features at a Glance](#features-at-a-glance)
- [How It Works](#how-it-works)
- [Configuration](#configuration)
- [Building Against This Mod](#building-against-this-mod)
- [Requires](#requires)

---

## Overview

Sable sub-levels power ships, airships, rovers, and other vehicles across the Create Aeronautics and Create Submarine ecosystem, but until now, nothing in the world could actually threaten them. Hostile mobs walked straight past a ship under attack, arrows sailed clean through hulls, and a besieged base could park its fleet in open water without a second thought.

Sable Mob Interactions changes that. It gives vanilla mobs, and any other mod's mobs, the ability to genuinely see, target, and damage Sable sub-levels, without requiring that mod's author to know Sable even exists.

This isn't a per-mob content pack. It's a generic interaction layer: any hostile mob that reaches the fallback tier gets a real, persistent target on a sub-level block, and its own native attack goals (melee, custom weapon systems, whatever the original author wrote) fire naturally against it, using the mob's real animations, sounds, and behavior.

---

## Features at a Glance

- **Vanilla mobs, properly tuned.** Skeletons fire real arrows. Drowned throw tridents if they're holding one. Zombies and most other melee mobs punch through hulls. Creepers explode near your vessel. Guardians charge a beam. Endermen make off with your décor.
- **Any mod's mobs, automatically.** A generic fallback goal gives any other hostile mob a real target and steps back, letting its own attack AI (melee, ranged, or fully custom weapon logic) do the rest, with zero code from that mod's author.
- **Any mod's projectiles, automatically.** Arrows, tridents, bullets, cannonballs, anything extending vanilla's `Projectile` class damages sub-level blocks correctly on impact, regardless of which mod fired them.
- **Ship-aware targeting.** Sub-levels only become valid targets if they're built from real propulsion/structural parts (configurable block tag) and have a player nearby, so idle player builds and abandoned test rigs aren't at risk by default.
- **Optional deep compatibility.** If Create: Big Cannons is installed, its own block armor/hardness values scale how many hits a block takes to break.
- **Fully configurable.** Search radius, attack range, fire rate, hit thresholds, creeper griefing, Enderman theft, ship-detection strictness, and more, all editable from the in-game config screen.

---

## How It Works

Sub-level blocks don't live at the position you see them at. Sable stores each sub-level's real block data in an isolated plot elsewhere in the world, and only visually poses it at its current in-game location. Every interaction this mod adds (finding a nearby block, aiming a projectile, breaking through a hull) correctly transforms between that raw storage space and the sub-level's posed, visual position, so mobs and projectiles hit exactly where they appear to be aiming.

Damage is tracked per block via a shared hit-counter, driving Minecraft's own crack-progress overlay before the block actually breaks, so combat against a ship looks and feels like combat against any other structure.

---

## Configuration

Accessible in-game via Mods → Sable Mob Interactions → Config, or by editing `sablemobinteractions-common.toml` directly. Includes:

- Per-mob search radius, attack range, and fire cooldown
- Base hits required to break a block, with optional hardness scaling
- Player-presence range required before a sub-level becomes targetable
- Toggle to require ship-essential blocks before a sub-level is targetable at all
- Toggle for creeper griefing and Enderman theft independently
- Mod namespaces and block tags for extending what counts as stealable or ship-essential

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

Everything else is optional. Create, Create Aeronautics, Create Submarine, and Create: Big Cannons are only used to enhance specific behaviors when present, and the mod works fully without them.