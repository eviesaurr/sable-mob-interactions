package com.eviesaurr.sablemobinteractions.worldgen.vehicle;

import com.mojang.serialization.Codec;
import com.mojang.serialization.codecs.RecordCodecBuilder;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.util.RandomSource;

import java.util.List;

public record PilotEntry(ResourceLocation entity, int weight) {

    public static final Codec<PilotEntry> CODEC = RecordCodecBuilder.create(instance -> instance.group(
            ResourceLocation.CODEC.fieldOf("entity").forGetter(PilotEntry::entity),
            Codec.intRange(1, 100000).optionalFieldOf("weight", 1).forGetter(PilotEntry::weight)
    ).apply(instance, PilotEntry::new));

    public static List<PilotEntry> defaults() {
        return List.of(new PilotEntry(ResourceLocation.withDefaultNamespace("zombie"), 1));
    }

    public static ResourceLocation pick(List<PilotEntry> pool, RandomSource random) {
        int total = 0;
        for (PilotEntry entry : pool) {
            total += entry.weight();
        }
        if (total <= 0) {
            return ResourceLocation.withDefaultNamespace("zombie");
        }
        int roll = random.nextInt(total);
        for (PilotEntry entry : pool) {
            roll -= entry.weight();
            if (roll < 0) {
                return entry.entity();
            }
        }
        return pool.get(0).entity();
    }
}