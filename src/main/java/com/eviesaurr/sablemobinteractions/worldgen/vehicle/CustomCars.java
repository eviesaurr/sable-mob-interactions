package com.eviesaurr.sablemobinteractions.worldgen.vehicle;

import com.eviesaurr.sablemobinteractions.SableMobInteractions;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.nbt.NbtAccounter;
import net.minecraft.nbt.NbtIo;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.server.MinecraftServer;
import net.minecraft.world.level.storage.LevelResource;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.stream.Stream;

public final class CustomCars {

    private static final String PREFIX = "custom/";

    private static volatile Map<ResourceLocation, CompoundTag> cars = Map.of();
    private static volatile Map<String, List<ResourceLocation>> byFolder = Map.of();
    private static volatile String signature = "";

    private CustomCars() {
    }

    public static Path root(MinecraftServer server) {
        return server.getWorldPath(LevelResource.ROOT).resolve("vehicles");
    }

    public static String folderName(ResourceLocation tierId) {
        return tierId.getPath();
    }

    public static void clear() {
        cars = Map.of();
        byFolder = Map.of();
        signature = "";
    }

    public static CompoundTag get(ResourceLocation id) {
        return cars.get(id);
    }

    public static List<ResourceLocation> idsFor(ResourceLocation tierId) {
        List<ResourceLocation> result = new ArrayList<>();
        String full = tierId.getPath().toLowerCase(Locale.ROOT);
        String shortName = tierId.getPath().replaceFirst("^tier_\\d+_", "").toLowerCase(Locale.ROOT);
        for (Map.Entry<String, List<ResourceLocation>> entry : byFolder.entrySet()) {
            if (entry.getKey().equals(full) || entry.getKey().equals(shortName)) {
                result.addAll(entry.getValue());
            }
        }
        return result;
    }

    private static String computeSignature(MinecraftServer server) {
        StringBuilder builder = new StringBuilder();
        Path root = root(server);
        if (!Files.isDirectory(root)) {
            return "";
        }
        try (Stream<Path> dirs = Files.list(root)) {
            for (Path dir : (Iterable<Path>) dirs.filter(Files::isDirectory).sorted()::iterator) {
                try (Stream<Path> files = Files.list(dir)) {
                    for (Path file : (Iterable<Path>) files.sorted()::iterator) {
                        builder.append(dir.getFileName()).append('/').append(file.getFileName()).append(':')
                                .append(Files.size(file)).append(':').append(Files.getLastModifiedTime(file).toMillis()).append(';');
                    }
                }
            }
        } catch (IOException e) {
            return signature;
        }
        return builder.toString();
    }

    public static void refreshIfChanged(MinecraftServer server, Iterable<ResourceLocation> tierIds) {
        if (!computeSignature(server).equals(signature)) {
            scan(server, tierIds);
        }
    }

    public static List<String> rescanAndDescribe(MinecraftServer server, Iterable<ResourceLocation> tierIds) {
        scan(server, tierIds);
        List<String> lines = new ArrayList<>();
        lines.add("Custom car root: " + root(server).toAbsolutePath());
        List<String> expected = new ArrayList<>();
        for (ResourceLocation tierId : tierIds) {
            expected.add(tierId.getPath());
        }
        java.util.Collections.sort(expected);
        lines.add("Tiers loaded: " + (expected.isEmpty() ? "none" : String.join(", ", expected)));
        if (byFolder.isEmpty()) {
            lines.add("No .nbt files found in any tier folder");
        }
        for (Map.Entry<String, List<ResourceLocation>> entry : byFolder.entrySet()) {
            lines.add("Folder " + entry.getKey() + ": " + entry.getValue().size() + " car(s)");
        }
        return lines;
    }

    public static void scan(MinecraftServer server, Iterable<ResourceLocation> tierIds) {
        Path root = root(server);
        try {
            Files.createDirectories(root);
            for (ResourceLocation tierId : tierIds) {
                Files.createDirectories(root.resolve(folderName(tierId)));
            }
        } catch (IOException e) {
            SableMobInteractions.LOGGER.error("Could not create custom car folders in {}", root, e);
            return;
        }

        SableMobInteractions.LOGGER.info("Scanning custom cars in {} for tiers {}", root.toAbsolutePath().normalize(), tierIds);
        Map<ResourceLocation, CompoundTag> loaded = new HashMap<>();
        Map<String, List<ResourceLocation>> folders = new HashMap<>();

        try (Stream<Path> dirs = Files.list(root)) {
            for (Path dir : (Iterable<Path>) dirs.filter(Files::isDirectory)::iterator) {
                String folder = dir.getFileName().toString().toLowerCase(Locale.ROOT);
                try (Stream<Path> files = Files.list(dir)) {
                    for (Path file : (Iterable<Path>) files::iterator) {
                        String rawName = file.getFileName().toString();
                        if (Files.isDirectory(file) || !rawName.toLowerCase(Locale.ROOT).endsWith(".nbt")) {
                            SableMobInteractions.LOGGER.warn("Ignoring '{}' in custom car folder '{}': only .nbt files are loaded", rawName, folder);
                            continue;
                        }
                        String name = rawName;
                        name = name.substring(0, name.length() - 4).toLowerCase(Locale.ROOT).replaceAll("[^a-z0-9_./-]", "_");
                        ResourceLocation id = ResourceLocation.fromNamespaceAndPath(SableMobInteractions.MOD_ID, PREFIX + folder + "/" + name);
                        try {
                            loaded.put(id, NbtIo.readCompressed(file, NbtAccounter.unlimitedHeap()));
                            folders.computeIfAbsent(folder, k -> new ArrayList<>()).add(id);
                        } catch (IOException e) {
                            SableMobInteractions.LOGGER.error("Could not read custom car {}", file, e);
                        }
                    }
                }
            }
        } catch (IOException e) {
            SableMobInteractions.LOGGER.error("Could not scan custom cars in {}", root, e);
            return;
        }

        signature = computeSignature(server);
        java.util.Set<String> known = new java.util.HashSet<>();
        for (ResourceLocation tierId : tierIds) {
            known.add(tierId.getPath().toLowerCase(Locale.ROOT));
            known.add(tierId.getPath().replaceFirst("^tier_\\d+_", "").toLowerCase(Locale.ROOT));
        }
        for (Map.Entry<String, List<ResourceLocation>> entry : folders.entrySet()) {
            SableMobInteractions.LOGGER.info("Custom cars in folder '{}': {}", entry.getKey(), entry.getValue().size());
            if (!known.contains(entry.getKey())) {
                SableMobInteractions.LOGGER.warn("Custom car folder '{}' does not match any tier, its cars will never spawn", entry.getKey());
            }
        }
        cars = Map.copyOf(loaded);
        byFolder = Map.copyOf(folders);
        SableMobInteractions.LOGGER.info("Loaded {} custom car(s) from {}", loaded.size(), root);
    }
}