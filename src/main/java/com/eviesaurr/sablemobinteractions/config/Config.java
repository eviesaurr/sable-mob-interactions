package com.eviesaurr.sablemobinteractions.config;

import net.neoforged.neoforge.common.ModConfigSpec;

import java.util.List;

public class Config {
    public static final ModConfigSpec SPEC;

    public static final ModConfigSpec.ConfigValue<List<? extends String>> ENDERMAN_STEAL_MOD_NAMESPACES;

    public static final ModConfigSpec.DoubleValue ZOMBIE_SEARCH_RADIUS;
    public static final ModConfigSpec.DoubleValue ZOMBIE_ATTACK_RANGE;

    public static final ModConfigSpec.DoubleValue SKELETON_SEARCH_RADIUS;
    public static final ModConfigSpec.DoubleValue SKELETON_ATTACK_RANGE;
    public static final ModConfigSpec.IntValue SKELETON_FIRE_COOLDOWN_TICKS;

    public static final ModConfigSpec.BooleanValue ALLOW_CREEPER_GRIEFING;

    public static final ModConfigSpec.DoubleValue GENERIC_MONSTER_SEARCH_RADIUS;
    public static final ModConfigSpec.DoubleValue GENERIC_MONSTER_ATTACK_RANGE;

    public static final ModConfigSpec.BooleanValue ALLOW_ENDERMAN_STEALING;

    public static final ModConfigSpec.BooleanValue SCALE_HITS_BY_HARDNESS;
    public static final ModConfigSpec.IntValue BASE_HITS_TO_BREAK;
    public static final ModConfigSpec.DoubleValue PLAYER_PRESENCE_RANGE;
    public static final ModConfigSpec.BooleanValue REQUIRE_SHIP_ESSENTIAL_BLOCKS;

    static {
        ModConfigSpec.Builder builder = new ModConfigSpec.Builder();

        builder.push("zombie");
        builder.translation("sablemobinteractions.configuration.zombie");
        ZOMBIE_SEARCH_RADIUS = builder
                .translation("sablemobinteractions.configuration.zombie.searchRadius")
                .defineInRange("searchRadius", 32.0, 1.0, 128.0);
        ZOMBIE_ATTACK_RANGE = builder
                .translation("sablemobinteractions.configuration.zombie.attackRange")
                .defineInRange("attackRange", 1.5, 0.5, 8.0);
        builder.pop();

        builder.push("skeleton");
        builder.translation("sablemobinteractions.configuration.skeleton");

        SKELETON_SEARCH_RADIUS = builder
                .translation("sablemobinteractions.configuration.skeleton.searchRadius")
                .defineInRange("searchRadius", 32.0, 1.0, 128.0);

        SKELETON_ATTACK_RANGE = builder
                .translation("sablemobinteractions.configuration.skeleton.attackRange")
                .defineInRange("attackRange", 15.0, 1.0, 64.0);

        SKELETON_FIRE_COOLDOWN_TICKS = builder
                .translation("sablemobinteractions.configuration.skeleton.fireCooldownTicks")
                .defineInRange("fireCooldownTicks", 40, 5, 200);
        builder.pop();

        builder.push("creeper");
        builder.translation("sablemobinteractions.configuration.creeper");
        ALLOW_CREEPER_GRIEFING = builder
                .translation("sablemobinteractions.configuration.creeper.allowGriefing")
                .comment("If true, creepers will target and explode near sub-level blocks. Turn off if you don't want creepers able to damage your ships/vehicles at all.")
                .define("allowGriefing", true);
        builder.pop();

        builder.push("generic_monster");
        builder.translation("sablemobinteractions.configuration.generic_monster");

        GENERIC_MONSTER_SEARCH_RADIUS = builder
                .translation("sablemobinteractions.configuration.generic_monster.searchRadius")
                .defineInRange("searchRadius", 32.0, 1.0, 128.0);

        GENERIC_MONSTER_ATTACK_RANGE = builder
                .translation("sablemobinteractions.configuration.generic_monster.attackRange")
                .defineInRange("attackRange", 2.5, 0.5, 8.0);
        builder.pop();

        builder.push("enderman_steal");
        builder.translation("sablemobinteractions.configuration.enderman_steal");
        ALLOW_ENDERMAN_STEALING = builder
                .translation("sablemobinteractions.configuration.enderman_steal.allowStealing")
                .comment("If true, Endermen can steal sub-level blocks. Turn off to disable this entirely.")
                .define("allowStealing", false);
        ENDERMAN_STEAL_MOD_NAMESPACES = builder
                .translation("sablemobinteractions.configuration.enderman_steal.allowedModNamespaces")
                .comment("Mod IDs whose blocks Endermen are allowed to steal from sub-levels entirely, in addition to the enderman_stealable block tag.")
                .defineList("allowedModNamespaces", () -> List.of("create_aeronautics"), o -> o instanceof String);
        builder.pop();

        builder.push("block_breaking");

        builder.translation("sablemobinteractions.configuration.block_breaking");
        BASE_HITS_TO_BREAK = builder
                .translation("sablemobinteractions.configuration.block_breaking.baseHitsToBreak")
                .comment("Base number of hits needed to break a sub-level block, before any hardness scaling is applied.")
                .defineInRange("baseHitsToBreak", 16, 1, 200);

        SCALE_HITS_BY_HARDNESS = builder
                .translation("sablemobinteractions.configuration.block_breaking.scaleHitsByHardness")
                .comment("If true and Create: Big Cannons is installed, blocks with higher armor hardness/toughness take proportionally more hits to break.")
                .define("scaleHitsByHardness", true);
        builder.pop();


        builder.push("general");
        builder.translation("sablemobinteractions.configuration.general");
        PLAYER_PRESENCE_RANGE = builder
                .translation("sablemobinteractions.configuration.general.playerPresenceRange")
                .comment("A sub-level is only a valid target if a player is within this many blocks of it. Set to 0 to disable this check entirely.")
                .defineInRange("playerPresenceRange", 15.0, 0.0, 512.0);
        REQUIRE_SHIP_ESSENTIAL_BLOCKS = builder
                .translation("sablemobinteractions.configuration.general.requireShipEssentialBlocks")
                .comment("If true, a sub-level must contain at least one block from the ship_essential_blocks tag (see ship_essential_blocks.json) to be a valid target. Turn off if you only use base Sable without ship-part mods like Aeronautics, Track-work or Submarine, so any sub-level can be targeted.")
                .define("requireShipEssentialBlocks", true);
        builder.pop();

        SPEC = builder.build();
    }
}