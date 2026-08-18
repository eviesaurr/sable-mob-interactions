package com.eviesaurr.sablemobinteractions.api;

import dev.ryanhcode.sable.sublevel.SubLevel;
import net.minecraft.core.BlockPos;
import net.minecraft.world.phys.Vec3;

public interface SubLevelAttacker {
    void attackSubLevelBlock(Vec3 targetWorldPos, SubLevel subLevel, BlockPos localPos);

    default double getSubLevelAttackRange() {
        return 15.0;
    }

    default int getSubLevelAttackCooldownTicks() {
        return 40;
    }
}