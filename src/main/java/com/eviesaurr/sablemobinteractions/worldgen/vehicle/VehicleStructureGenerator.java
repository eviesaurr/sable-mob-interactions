package com.eviesaurr.sablemobinteractions.worldgen.vehicle;

import net.minecraft.world.item.Items;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.entity.Mob;
import net.minecraft.world.entity.EntityType;
import net.minecraft.world.entity.EquipmentSlot;
import net.minecraft.world.effect.MobEffects;
import net.minecraft.world.effect.MobEffectInstance;
import com.eviesaurr.sablemobinteractions.driving.PilotController;
import com.eviesaurr.sablemobinteractions.SableMobInteractions;
import com.eviesaurr.sablemobinteractions.sublevel.SubLevelInteraction;
import com.simibubi.create.content.contraptions.actors.seat.SeatBlock;
import com.simibubi.create.content.contraptions.actors.seat.SeatEntity;
import dev.ryanhcode.sable.api.sublevel.ServerSubLevelContainer;
import dev.ryanhcode.sable.sublevel.SubLevel;
import net.minecraft.world.level.block.entity.BlockEntity;
import net.minecraft.core.BlockPos;
import net.minecraft.core.registries.BuiltInRegistries;
import net.minecraft.core.registries.Registries;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.nbt.ListTag;
import net.minecraft.nbt.Tag;
import net.minecraft.util.Mth;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.phys.AABB;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.server.MinecraftServer;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.util.RandomSource;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.block.Mirror;
import net.minecraft.world.level.block.Rotation;
import net.minecraft.world.level.levelgen.Heightmap;
import net.minecraft.world.level.levelgen.structure.BoundingBox;
import net.minecraft.world.level.levelgen.structure.templatesystem.StructurePlaceSettings;
import net.minecraft.world.level.levelgen.structure.templatesystem.StructureTemplate;
import net.minecraft.world.phys.Vec3;
import net.neoforged.bus.api.SubscribeEvent;
import net.neoforged.fml.common.EventBusSubscriber;
import net.neoforged.neoforge.event.tick.ServerTickEvent;

import java.util.ArrayList;
import java.util.Collection;
import java.util.HashMap;
import java.util.HashSet;
import java.util.UUID;
import java.util.Iterator;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.Set;

@EventBusSubscriber(modid = SableMobInteractions.MOD_ID)
public class VehicleStructureGenerator {

    private static final int TICK_INTERVAL = 20;
    private static final int MAINTAIN_EVERY = 2;
    private static final int MAX_ATTEMPTS_PER_PASS = 16;
    private static final int DESPAWN_RANGE = 128;
    private static final int MISSING_PASSES_BEFORE_REMOVAL = 10;
    private static final float WRECK_BREAK_CHANCE = 0.4f;
    private static final int ACTIVATION_RANGE = 80;
    private static final int MAX_SPAWNS_PER_PASS = 3;
    private static final int MAX_ASSEMBLE_ATTEMPTS = 30;
    private static final int MAX_PILOT_ATTEMPTS = 12;
    private static final int PILOT_LOST_PASSES = 10;

    public static int[] clearRecords(ServerLevel level) {
        VehicleSpawnData data = VehicleSpawnData.get(level);
        ServerSubLevelContainer container = ServerSubLevelContainer.getContainer(level);
        Set<UUID> loaded = new HashSet<>();
        if (container != null) {
            for (var subLevel : container.getAllSubLevels()) {
                loaded.add(subLevel.getUniqueId());
            }
        }

        MinecraftServer server = level.getServer();
        int purged = 0;
        int queued = 0;
        Iterator<VehicleSpawnData.ActiveVehicle> iterator = data.active.iterator();
        while (iterator.hasNext()) {
            VehicleSpawnData.ActiveVehicle vehicle = iterator.next();
            discardPilot(level, vehicle);
            if (vehicle.subLevelId == null) {
                iterator.remove();
                continue;
            }
            if (loaded.contains(vehicle.subLevelId)) {
                server.getCommands().performPrefixedCommand(server.createCommandSourceStack().withLevel(level),
                        "sablecat forcepurge " + vehicle.subLevelId + " confirm");
                iterator.remove();
                purged++;
            } else {
                vehicle.purgeOnLoad = true;
                queued++;
            }
        }
        data.pending.clear();
        data.setDirty();
        return new int[]{purged, queued};
    }

    public static List<String> describe(ServerLevel level) {
        VehicleSpawnData data = VehicleSpawnData.get(level);
        ServerSubLevelContainer container = ServerSubLevelContainer.getContainer(level);
        Set<UUID> loaded = new HashSet<>();
        if (container != null) {
            for (var subLevel : container.getAllSubLevels()) {
                loaded.add(subLevel.getUniqueId());
            }
        }
        List<String> lines = new ArrayList<>();
        lines.add("Vehicle records: " + data.active.size() + ", pending sites: " + data.pending.size()
                + ", loaded sub-levels: " + loaded.size());
        for (VehicleSpawnData.ActiveVehicle vehicle : data.active) {
            boolean isLoaded = vehicle.subLevelId != null && loaded.contains(vehicle.subLevelId);
            lines.add(vehicle.tier.getPath() + " " + vehicle.structure.getPath() + " at " + vehicle.pos.toShortString()
                    + " | site " + vehicle.siteKey + " | " + (vehicle.assembled ? "assembled" : "not assembled")
                    + " | " + (isLoaded ? "loaded" : "NOT loaded") + " | pilot " + (vehicle.pilotDead ? "dead" : vehicle.pilotId == null ? "none" : "ok")
                    + " | missing passes " + vehicle.missingPasses);
        }
        return lines;
    }

    public static void onPilotDeath(ServerLevel level, UUID pilotId) {
        VehicleSpawnData data = VehicleSpawnData.get(level);
        for (VehicleSpawnData.ActiveVehicle vehicle : data.active) {
            if (pilotId.equals(vehicle.pilotId)) {
                vehicle.pilotDead = true;
                data.setDirty();
                return;
            }
        }
    }

    private static int tickCounter = 0;
    private static int passCounter = 0;

    @SubscribeEvent
    public static void onServerTick(ServerTickEvent.Post event) {
        tickCounter++;
        if (tickCounter < TICK_INTERVAL) {
            return;
        }
        tickCounter = 0;
        passCounter++;
        boolean slowPass = passCounter % MAINTAIN_EVERY == 0;

        MinecraftServer server = event.getServer();
        for (ServerLevel level : server.getAllLevels()) {
            VehicleSpawnData data = VehicleSpawnData.get(level);
            assembleAndResolvePending(level, data);
            if (slowPass) {
                CustomCars.refreshIfChanged(level.getServer(), VehicleTierManager.getTiers().keySet());
                maintainVehicles(level, data);
            }
            populateSites(level, data);
        }
    }

    private static void assembleAndResolvePending(ServerLevel level, VehicleSpawnData data) {
        Iterator<VehicleSpawnData.ActiveVehicle> iterator = data.active.iterator();
        while (iterator.hasNext()) {
            VehicleSpawnData.ActiveVehicle vehicle = iterator.next();
            if (vehicle.subLevelId != null) {
                continue;
            }

            Vec3 center = Vec3.atCenterOf(vehicle.pos)
                    .add(vehicle.sizeX / 2.0, vehicle.sizeY / 2.0, vehicle.sizeZ / 2.0);
            var found = SubLevelInteraction.findAt(level, center);
            if (found.isPresent()) {
                UUID foundId = found.get().subLevel().getUniqueId();
                boolean taken = false;
                for (VehicleSpawnData.ActiveVehicle other : data.active) {
                    if (other != vehicle && foundId.equals(other.subLevelId)) {
                        taken = true;
                        break;
                    }
                }
                if (!taken) {
                    vehicle.subLevelId = foundId;
                    vehicle.assembled = true;
                    data.setDirty();
                    continue;
                }
            }

            if (vehicle.assembleAttempts >= MAX_ASSEMBLE_ATTEMPTS) {
                makeWreck(level, vehicle);
                iterator.remove();
                data.setDirty();
                continue;
            }

            BlockPos from = vehicle.pos;
            BlockPos to = vehicle.pos.offset(vehicle.sizeX - 1, vehicle.sizeY - 1, vehicle.sizeZ - 1);

            BlockEntity assembler = null;
            for (BlockPos scan : BlockPos.betweenClosed(from, to)) {
                BlockEntity candidate = level.getBlockEntity(scan);
                if (candidate != null && ASSEMBLER_CLASS != null && ASSEMBLER_CLASS.isInstance(candidate)) {
                    assembler = candidate;
                    break;
                }
            }

            vehicle.assembleAttempts++;
            if (assembler != null) {
                try {
                    ASSEMBLE_METHOD.invoke(assembler);
                } catch (Throwable t) {
                    SableMobInteractions.LOGGER.warn("Could not trigger the physics assembler at {}", assembler.getBlockPos(), t);
                }
            }
            if (vehicle.assembleAttempts >= MAX_ASSEMBLE_ATTEMPTS) {
                SableMobInteractions.LOGGER.warn(
                        "Vehicle {} at {} never assembled into a sub-level after {} attempts, turning it into a wreck",
                        vehicle.structure, vehicle.pos, vehicle.assembleAttempts);
            }
            data.setDirty();
        }
    }

    private static void equipPilot(Mob pilot) {
        EntityType<?> type = pilot.getType();
        if (type == EntityType.SKELETON) {
            pilot.setItemSlot(EquipmentSlot.MAINHAND, new ItemStack(Items.BOW));
        } else if (type == EntityType.VINDICATOR) {
            pilot.setItemSlot(EquipmentSlot.MAINHAND, new ItemStack(Items.IRON_AXE));
        } else if (type == EntityType.PILLAGER) {
            pilot.setItemSlot(EquipmentSlot.MAINHAND, new ItemStack(Items.CROSSBOW));
        }
    }

    private static UUID spawnPilot(ServerLevel level, VehicleSpawnData.ActiveVehicle vehicle, SubLevel subLevel) {
        BlockPos seatPos = findSeat(level, subLevel);
        if (seatPos == null) {
            SableMobInteractions.LOGGER.warn("Vehicle {} has no Create seat block, spawning without a pilot", vehicle.structure);
            return null;
        }

        VehicleTier tier = VehicleTierManager.getTiers().get(vehicle.tier);
        List<PilotEntry> pool = tier != null ? tier.pilots() : PilotEntry.defaults();
        ResourceLocation pilotType = PilotEntry.pick(pool, level.getRandom());
        Entity created = BuiltInRegistries.ENTITY_TYPE.getOptional(pilotType).map(type -> type.create(level)).orElse(null);
        if (!(created instanceof Mob pilot)) {
            SableMobInteractions.LOGGER.warn("Pilot type {} is not a mob, skipping", pilotType);
            return null;
        }

        SeatEntity seat = new SeatEntity(level);
        seat.setPos(seatPos.getX() + 0.5, seatPos.getY(), seatPos.getZ() + 0.5);

        Vec3 world = SubLevelInteraction.localToWorld(subLevel, seatPos);
        pilot.setPos(world.x, world.y, world.z);
        pilot.setPersistenceRequired();
        pilot.setNoAi(true);
        pilot.addEffect(new MobEffectInstance(MobEffects.FIRE_RESISTANCE, MobEffectInstance.INFINITE_DURATION, 0, false, false));
        equipPilot(pilot);
        pilot.getPersistentData().putString(PilotController.TIER_KEY, vehicle.tier.toString());
        pilot.startRiding(seat, true);

        level.addFreshEntityWithPassengers(seat);

        SableMobInteractions.LOGGER.info("Pilot {} spawned in seat at {} (riding: {})",
                pilot.getUUID(), seatPos, pilot.isPassenger());
        return pilot.getUUID();
    }

    private static List<String> itemPool;

    private static String randomItemId(RandomSource random, Collection<String> taken) {
        if (itemPool == null) {
            itemPool = new ArrayList<>();
            for (ResourceLocation id : BuiltInRegistries.ITEM.keySet()) {
                if (!id.toString().equals("minecraft:air")) {
                    itemPool.add(id.toString());
                }
            }
        }
        String pick;
        do {
            pick = itemPool.get(random.nextInt(itemPool.size()));
        } while (taken.contains(pick));
        return pick;
    }

    private static void remapFrequency(CompoundTag parent, String key, Map<String, String> mapping, RandomSource random) {
        if (!parent.contains(key, Tag.TAG_COMPOUND)) {
            return;
        }
        CompoundTag item = parent.getCompound(key);
        String id = item.getString("id");
        if (id.isEmpty()) {
            return;
        }
        String replacement = mapping.get(id);
        if (replacement == null) {
            replacement = randomItemId(random, mapping.values());
            mapping.put(id, replacement);
        }
        item.putString("id", replacement);
        item.remove("components");
    }

    private static StructureTemplate withUniqueFrequencies(ServerLevel level, CompoundTag tag) {
        ListTag blocks = tag.getList("blocks", Tag.TAG_COMPOUND);
        Map<String, String> mapping = new HashMap<>();
        RandomSource random = level.getRandom();

        for (int i = 0; i < blocks.size(); i++) {
            CompoundTag block = blocks.getCompound(i);
            if (!block.contains("nbt", Tag.TAG_COMPOUND)) {
                continue;
            }
            CompoundTag nbt = block.getCompound("nbt");
            remapFrequency(nbt, "FrequencyFirst", mapping, random);
            remapFrequency(nbt, "FrequencyLast", mapping, random);
            if (nbt.contains("ChannelFrequencies", Tag.TAG_COMPOUND)) {
                CompoundTag channels = nbt.getCompound("ChannelFrequencies");
                for (String direction : channels.getAllKeys()) {
                    CompoundTag pair = channels.getCompound(direction);
                    remapFrequency(pair, "First", mapping, random);
                    remapFrequency(pair, "Last", mapping, random);
                }
            }
        }

        StructureTemplate copy = new StructureTemplate();
        copy.load(level.holderLookup(Registries.BLOCK), tag);
        return copy;
    }

    private static BlockPos findSeat(ServerLevel level, SubLevel subLevel) {
        AABB bounds = subLevel.getPlot().getBoundingBox().toAABB();
        for (BlockPos pos : BlockPos.betweenClosed(
                Mth.floor(bounds.minX), Mth.floor(bounds.minY), Mth.floor(bounds.minZ),
                Mth.ceil(bounds.maxX), Mth.ceil(bounds.maxY), Mth.ceil(bounds.maxZ))) {
            if (level.getBlockState(pos).getBlock() instanceof SeatBlock) {
                return pos.immutable();
            }
        }
        return null;
    }

    private static void discardPilot(ServerLevel level, VehicleSpawnData.ActiveVehicle vehicle) {
        if (vehicle.pilotId == null) {
            return;
        }
        Entity pilot = level.getEntity(vehicle.pilotId);
        if (pilot != null) {
            pilot.discard();
        }
    }

    private static void maintainVehicles(ServerLevel level, VehicleSpawnData data) {
        ServerSubLevelContainer container = ServerSubLevelContainer.getContainer(level);
        Map<UUID, SubLevel> loaded = new HashMap<>();
        if (container != null) {
            for (var subLevel : container.getAllSubLevels()) {
                loaded.put(subLevel.getUniqueId(), subLevel);
            }
        }

        List<ServerPlayer> players = level.players();
        Iterator<VehicleSpawnData.ActiveVehicle> iterator = data.active.iterator();
        while (iterator.hasNext()) {
            VehicleSpawnData.ActiveVehicle vehicle = iterator.next();
            if (vehicle.subLevelId == null) {
                continue;
            }

            SubLevel subLevel = loaded.get(vehicle.subLevelId);
            if (subLevel == null) {
                if (level.hasChunk(vehicle.pos.getX() >> 4, vehicle.pos.getZ() >> 4)) {
                    vehicle.missingPasses++;
                    if (vehicle.missingPasses >= MISSING_PASSES_BEFORE_REMOVAL) {
                        SableMobInteractions.LOGGER.info("Vehicle {} at {} has no loaded sub-level, removing its record", vehicle.structure, vehicle.pos);
                        discardPilot(level, vehicle);
                        iterator.remove();
                        data.setDirty();
                    }
                }
                continue;
            }

            vehicle.missingPasses = 0;

            if (vehicle.purgeOnLoad) {
                discardPilot(level, vehicle);
                MinecraftServer purgeServer = level.getServer();
                purgeServer.getCommands().performPrefixedCommand(purgeServer.createCommandSourceStack().withLevel(level),
                        "sablecat forcepurge " + vehicle.subLevelId + " confirm");
                iterator.remove();
                data.setDirty();
                continue;
            }

            if (!vehicle.pilotHandled && !vehicle.pilotDead) {
                vehicle.pilotAttempts++;
                UUID pilotId = spawnPilot(level, vehicle, subLevel);
                if (pilotId != null || vehicle.pilotAttempts >= MAX_PILOT_ATTEMPTS) {
                    vehicle.pilotId = pilotId;
                    vehicle.pilotHandled = true;
                    data.setDirty();
                }
            }
            if (vehicle.pilotHandled && !vehicle.pilotDead) {
                BlockPos seatPos = findSeat(level, subLevel);
                boolean seatLoaded = seatPos != null && level.hasChunk(seatPos.getX() >> 4, seatPos.getZ() >> 4);
                Entity existing = vehicle.pilotId == null ? null : level.getEntity(vehicle.pilotId);
                if (existing != null && existing.isAlive() && existing.isPassenger()) {
                    vehicle.pilotLostPasses = 0;
                } else if (seatLoaded && ++vehicle.pilotLostPasses >= PILOT_LOST_PASSES) {
                    if (existing != null) {
                        existing.discard();
                    }
                    vehicle.pilotId = null;
                    vehicle.pilotHandled = false;
                    vehicle.pilotAttempts = 0;
                    vehicle.pilotLostPasses = 0;
                    data.setDirty();
                    SableMobInteractions.LOGGER.info("Pilot of {} is missing, respawning", vehicle.structure);
                }
            }
            var position = subLevel.logicalPose().position();
            vehicle.pos = BlockPos.containing(position.x(), position.y(), position.z());

            VehicleTier tier = VehicleTierManager.getTiers().get(vehicle.tier);
            if (tier == null || tier.despawnAfterTicks() <= 0) {
                continue;
            }

            boolean seen = false;
            for (ServerPlayer player : players) {
                if (player.blockPosition().closerThan(vehicle.pos, DESPAWN_RANGE)) {
                    seen = true;
                    break;
                }
            }

            if (seen) {
                vehicle.unseenTicks = 0;
                continue;
            }

            vehicle.unseenTicks += TICK_INTERVAL;
            if (vehicle.unseenTicks < tier.despawnAfterTicks()) {
                continue;
            }

            discardPilot(level, vehicle);
            MinecraftServer server = level.getServer();
            server.getCommands().performPrefixedCommand(
                    server.createCommandSourceStack(),
                    "sablecat forcepurge " + vehicle.subLevelId + " confirm");
            iterator.remove();
            data.setDirty();
        }
    }

    private static void makeWreck(ServerLevel level, VehicleSpawnData.ActiveVehicle vehicle) {
        BlockPos from = vehicle.pos;
        BlockPos to = vehicle.pos.offset(vehicle.sizeX - 1, vehicle.sizeY - 1, vehicle.sizeZ - 1);
        AABB area = new AABB(from.getX(), from.getY(), from.getZ(), to.getX() + 1, to.getY() + 1, to.getZ() + 1);

        for (Entity entity : level.getEntities((Entity) null, area,
                e -> BuiltInRegistries.ENTITY_TYPE.getKey(e.getType()).toString().equals("simulated:honey_glue"))) {
            entity.discard();
        }

        RandomSource random = level.getRandom();
        for (BlockPos pos : BlockPos.betweenClosed(from, to)) {
            if (level.getBlockState(pos).isAir()) {
                continue;
            }
            if (pos.getY() < level.getHeight(Heightmap.Types.WORLD_SURFACE_WG, pos.getX(), pos.getZ())) {
                continue;
            }
            if (random.nextFloat() < WRECK_BREAK_CHANCE) {
                level.destroyBlock(pos.immutable(), false);
            }
        }
    }

    private static void populateSites(ServerLevel level, VehicleSpawnData data) {
        BlockPos spawn = level.getSharedSpawnPos();
        Set<String> nearby = new HashSet<>();

        boolean overworld = level.dimension() == Level.OVERWORLD;

        for (ServerPlayer player : level.players()) {
            for (VehicleSpawnData.Node node : data.nodes) {
                VehicleTier nodeTier = VehicleTierManager.getTiers().get(node.tier);
                if (nodeTier == null) {
                    continue;
                }
                double dx = node.pos.getX() - player.getX();
                double dz = node.pos.getZ() - player.getZ();
                if (dx * dx + dz * dz > (double) ACTIVATION_RANGE * ACTIVATION_RANGE) {
                    continue;
                }

                String key = node.key();
                nearby.add(key);
                if (data.countForSite(key) == 0 && !data.pending.containsKey(key)) {
                    data.pending.put(key, new VehicleSpawnData.PendingSite(
                            key, node.tier, node.pos.getX(), node.pos.getZ(), true, nodeTier.carsPerSite()));
                    data.setDirty();
                }
            }

            if (!overworld) {
                continue;
            }

            for (Map.Entry<ResourceLocation, VehicleTier> entry : VehicleTierManager.getTiers().entrySet()) {
                VehicleTier tier = entry.getValue();
                int spacing = Math.max(1, tier.siteSpacing());
                int minCellX = Math.floorDiv(player.getBlockX() - ACTIVATION_RANGE, spacing);
                int maxCellX = Math.floorDiv(player.getBlockX() + ACTIVATION_RANGE, spacing);
                int minCellZ = Math.floorDiv(player.getBlockZ() - ACTIVATION_RANGE, spacing);
                int maxCellZ = Math.floorDiv(player.getBlockZ() + ACTIVATION_RANGE, spacing);

                for (int cellX = minCellX; cellX <= maxCellX; cellX++) {
                    for (int cellZ = minCellZ; cellZ <= maxCellZ; cellZ++) {
                        Optional<BlockPos> site = siteCenter(level, spawn, entry.getKey(), tier, cellX, cellZ);
                        if (site.isEmpty()) {
                            continue;
                        }
                        double dx = site.get().getX() - player.getX();
                        double dz = site.get().getZ() - player.getZ();
                        if (dx * dx + dz * dz > (double) ACTIVATION_RANGE * ACTIVATION_RANGE) {
                            continue;
                        }

                        String key = siteKey(entry.getKey(), cellX, cellZ);
                        nearby.add(key);
                        if (data.countForSite(key) == 0 && !data.pending.containsKey(key)) {
                            data.pending.put(key, new VehicleSpawnData.PendingSite(
                                    key, entry.getKey(), cellX, cellZ, false, tier.carsPerSite()));
                            data.setDirty();
                        }
                    }
                }
            }
        }

        int budget = MAX_SPAWNS_PER_PASS;
        int attempts = MAX_ATTEMPTS_PER_PASS;
        Iterator<VehicleSpawnData.PendingSite> iterator = data.pending.values().iterator();
        while (iterator.hasNext()) {
            VehicleSpawnData.PendingSite pending = iterator.next();
            VehicleTier tier = VehicleTierManager.getTiers().get(pending.tier);
            Optional<BlockPos> site;
            if (tier == null) {
                site = Optional.empty();
            } else if (pending.node) {
                site = Optional.of(new BlockPos(pending.cellX, 0, pending.cellZ));
            } else {
                site = siteCenter(level, spawn, pending.tier, tier, pending.cellX, pending.cellZ);
            }

            if (!nearby.contains(pending.key) || site.isEmpty()) {
                iterator.remove();
                data.setDirty();
                continue;
            }

            while (pending.remaining > 0 && budget > 0 && attempts > 0) {
                attempts--;
                if (spawnCar(level, data, pending, tier, site.get())) {
                    budget--;
                    pending.remaining--;
                    data.setDirty();
                }
            }

            if (pending.remaining <= 0) {
                iterator.remove();
                data.setDirty();
            }
        }
    }

    private static Optional<BlockPos> siteCenter(ServerLevel level, BlockPos spawn, ResourceLocation tierId,
                                                 VehicleTier tier, int cellX, int cellZ) {
        int spacing = Math.max(1, tier.siteSpacing());
        long seed = level.getSeed()
                ^ ((long) tierId.hashCode() * 341873128712L)
                ^ ((long) cellX * 132897987541L)
                ^ ((long) cellZ * 42317861L);
        RandomSource random = RandomSource.create(seed);

        if (random.nextDouble() >= tier.siteChance()) {
            return Optional.empty();
        }

        int x = cellX * spacing + random.nextInt(spacing);
        int z = cellZ * spacing + random.nextInt(spacing);

        double dx = x - spawn.getX();
        double dz = z - spawn.getZ();
        double dist = Math.sqrt(dx * dx + dz * dz);
        if (dist < tier.minRadius() || dist > tier.maxRadius()) {
            return Optional.empty();
        }

        return Optional.of(new BlockPos(x, 0, z));
    }

    private static String siteKey(ResourceLocation tierId, int cellX, int cellZ) {
        return tierId + "|" + cellX + "|" + cellZ;
    }

    private static boolean spawnCar(ServerLevel level, VehicleSpawnData data, VehicleSpawnData.PendingSite pending,
                                    VehicleTier tier, BlockPos site) {
        RandomSource random = level.getRandom();
        double angle = random.nextDouble() * Math.PI * 2;
        double dist = Math.sqrt(random.nextDouble()) * (pending.node ? Math.min(tier.siteRadius(), NODE_CAR_RADIUS) : tier.siteRadius());
        int x = site.getX() + (int) Math.round(Math.cos(angle) * dist);
        int z = site.getZ() + (int) Math.round(Math.sin(angle) * dist);

        if (!level.hasChunk(x >> 4, z >> 4)) {
            return false;
        }

        for (VehicleSpawnData.ActiveVehicle existing : data.active) {
            double dx = existing.pos.getX() - x;
            double dz = existing.pos.getZ() - z;
            if (dx * dx + dz * dz < (double) tier.minSpacing() * tier.minSpacing()) {
                return false;
            }
        }

        List<ResourceLocation> pool = new ArrayList<>(tier.structures());
        pool.addAll(CustomCars.idsFor(pending.tier));
        if (pool.isEmpty()) {
            return false;
        }
        ResourceLocation structureId = pool.get(random.nextInt(pool.size()));
        boolean placed = place(level, data, pending, tier, structureId, x, z);
        if (placed) {
            SableMobInteractions.LOGGER.debug("Placed car {} at {} {}", structureId, x, z);
        }
        return placed;
    }

    private static boolean place(ServerLevel level, VehicleSpawnData data, VehicleSpawnData.PendingSite pending,
                                 VehicleTier tier, ResourceLocation structureId, int x, int z) {
        StructureTemplate structureTemplate;
        CompoundTag custom = CustomCars.get(structureId);
        if (custom != null) {
            structureTemplate = withUniqueFrequencies(level, custom.copy());
        } else {
            Optional<StructureTemplate> template = level.getStructureManager().get(structureId);
            if (template.isEmpty()) {
                SableMobInteractions.LOGGER.warn("Vehicle structure {} not found, skipping spawn", structureId);
                return false;
            }
            structureTemplate = withUniqueFrequencies(level, template.get().save(new CompoundTag()));
        }

        StructurePlaceSettings settings = new StructurePlaceSettings()
                .setRotation(Rotation.getRandom(level.getRandom()))
                .setMirror(Mirror.NONE)
                .setIgnoreEntities(false);

        BoundingBox probe = structureTemplate.getBoundingBox(settings, new BlockPos(x, 0, z));
        int shiftX = x - (probe.minX() + probe.maxX()) / 2;
        int shiftZ = z - (probe.minZ() + probe.maxZ()) / 2;

        if (tier.placement() != VehicleTier.PlacementMode.FIXED_Y
                && !isFlatClearGround(level, x, z, Math.max(probe.getXSpan(), probe.getZSpan()) / 2)) {
            if (GROUND_REJECTIONS.incrementAndGet() <= 30) {
                SableMobInteractions.LOGGER.info("Car spot at {} {} rejected: slope, trees or water", x, z);
            }
            return false;
        }

        int y = tier.placement() == VehicleTier.PlacementMode.FIXED_Y
                ? tier.fixedY()
                : level.getHeight(Heightmap.Types.MOTION_BLOCKING_NO_LEAVES, x, z);
        BlockPos placePos = new BlockPos(x + shiftX, y, z + shiftZ);
        BoundingBox box = structureTemplate.getBoundingBox(settings, placePos);

        for (int cx = box.minX() >> 4; cx <= box.maxX() >> 4; cx++) {
            for (int cz = box.minZ() >> 4; cz <= box.maxZ() >> 4; cz++) {
                if (!level.hasChunk(cx, cz)) {
                    return false;
                }
            }
        }

        if (!isClearOfBlocks(level, box)) {
            return false;
        }

        structureTemplate.placeInWorld(level, placePos, placePos, settings, level.getRandom(), 2);

        data.active.add(new VehicleSpawnData.ActiveVehicle(pending.tier, structureId, pending.key,
                new BlockPos(box.minX(), box.minY(), box.minZ()),
                box.getXSpan(), box.getYSpan(), box.getZSpan(), false, 0, null, 0));
        data.setDirty();
        return true;
    }

    private static final int SPAWN_MARGIN = 2;

    private static final Class<?> ASSEMBLER_CLASS;
    private static final java.lang.reflect.Method ASSEMBLE_METHOD;

    static {
        Class<?> assemblerClass = null;
        java.lang.reflect.Method assembleMethod = null;
        try {
            assemblerClass = Class.forName("dev.simulated_team.simulated.content.blocks.physics_assembler.PhysicsAssemblerBlockEntity");
            assembleMethod = assemblerClass.getMethod("assembleOrDisassemble");
        } catch (Throwable t) {
            SableMobInteractions.LOGGER.warn("Physics assembler class not found, vehicles will not assemble", t);
            assemblerClass = null;
        }
        ASSEMBLER_CLASS = assemblerClass;
        ASSEMBLE_METHOD = assembleMethod;
    }

    private static boolean isClearOfBlocks(ServerLevel level, BoundingBox box) {
        for (int cx = (box.minX() - SPAWN_MARGIN) >> 4; cx <= (box.maxX() + SPAWN_MARGIN) >> 4; cx++) {
            for (int cz = (box.minZ() - SPAWN_MARGIN) >> 4; cz <= (box.maxZ() + SPAWN_MARGIN) >> 4; cz++) {
                if (!level.hasChunk(cx, cz)) {
                    return false;
                }
            }
        }
        BlockPos.MutableBlockPos cursor = new BlockPos.MutableBlockPos();
        for (int x = box.minX() - SPAWN_MARGIN; x <= box.maxX() + SPAWN_MARGIN; x++) {
            for (int z = box.minZ() - SPAWN_MARGIN; z <= box.maxZ() + SPAWN_MARGIN; z++) {
                for (int y = box.minY(); y <= box.maxY(); y++) {
                    var state = level.getBlockState(cursor.set(x, y, z));
                    if (!state.isAir() && !state.canBeReplaced()) {
                        return false;
                    }
                }
            }
        }
        return true;
    }

    private static final int MAX_GROUND_VARIATION = 3;
    private static final int NODE_CAR_RADIUS = 14;
    private static final java.util.concurrent.atomic.AtomicInteger GROUND_REJECTIONS = new java.util.concurrent.atomic.AtomicInteger();

    private static boolean isFlatClearGround(ServerLevel level, int x, int z, int halfExtent) {
        int step = Math.max(1, halfExtent / 2);
        int lowest = Integer.MAX_VALUE;
        int highest = Integer.MIN_VALUE;
        for (int dx = -halfExtent; dx <= halfExtent; dx += step) {
            for (int dz = -halfExtent; dz <= halfExtent; dz += step) {
                int sx = x + dx;
                int sz = z + dz;
                if (!level.hasChunk(sx >> 4, sz >> 4)) {
                    return false;
                }
                int ground = level.getHeight(Heightmap.Types.MOTION_BLOCKING_NO_LEAVES, sx, sz);
                int top = level.getHeight(Heightmap.Types.WORLD_SURFACE, sx, sz);
                if (top > ground && level.getBlockState(new BlockPos(sx, top - 1, sz)).is(net.minecraft.tags.BlockTags.LEAVES)) {
                    return false;
                }
                BlockPos below = new BlockPos(sx, ground - 1, sz);
                if (level.getBlockState(below).is(net.minecraft.tags.BlockTags.LOGS)
                        || !level.getFluidState(below).isEmpty()
                        || !level.getFluidState(below.above()).isEmpty()) {
                    return false;
                }
                lowest = Math.min(lowest, ground);
                highest = Math.max(highest, ground);
            }
        }
        return highest - lowest <= MAX_GROUND_VARIATION;
    }
}