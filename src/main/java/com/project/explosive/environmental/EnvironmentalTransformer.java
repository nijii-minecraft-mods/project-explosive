package com.project.explosive.environmental;

import com.project.explosive.block.ModBlocks;
import com.project.explosive.block.RadioactiveAshBlock;
import com.project.explosive.worldgen.ModBiomes;
import java.util.HashMap;
import java.util.HashSet;
import java.util.Map;
import java.util.Map.Entry;
import java.util.Optional;
import java.util.Set;
import net.fabricmc.fabric.api.event.lifecycle.v1.ServerTickEvents;
import net.minecraft.block.Block;
import net.minecraft.block.BlockState;
import net.minecraft.block.Blocks;
import net.minecraft.block.LeavesBlock;
import net.minecraft.block.PillarBlock;
import net.minecraft.entity.Entity;
import net.minecraft.entity.LivingEntity;
import net.minecraft.entity.effect.StatusEffectInstance;
import net.minecraft.entity.effect.StatusEffects;
import net.minecraft.entity.player.PlayerEntity;
import net.minecraft.network.packet.s2c.play.ChunkDataS2CPacket;
import net.minecraft.particle.ParticleTypes;
import net.minecraft.registry.Registry;
import net.minecraft.registry.RegistryKey;
import net.minecraft.registry.RegistryKeys;
import net.minecraft.registry.entry.RegistryEntry;
import net.minecraft.registry.tag.BlockTags;
import net.minecraft.server.network.ServerPlayerEntity;
import net.minecraft.server.world.ServerWorld;
import net.minecraft.util.math.BlockPos;
import net.minecraft.util.math.Direction.Axis;
import net.minecraft.util.math.random.Random;
import net.minecraft.world.World;
import net.minecraft.world.biome.Biome;
import net.minecraft.world.biome.BiomeKeys;
import net.minecraft.world.chunk.ChunkSection;
import net.minecraft.world.chunk.PalettedContainer;
import net.minecraft.world.chunk.WorldChunk;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

public class EnvironmentalTransformer {
    private static final Logger LOGGER = LoggerFactory.getLogger(EnvironmentalTransformer.class);
    private static final Map<BlockPos, EnvironmentalTransformer.ContaminationData> contaminatedAreas = new HashMap<>();

    public static void init() {
        ServerTickEvents.END_WORLD_TICK.register(EnvironmentalTransformer::onWorldTick);
    }

    public static void contaminateArea(BlockPos centerPos, ServerWorld level, double intensity) {
        if (EnvironmentalConfig.isBlockContaminationEnabled()) {
            if (contaminatedAreas.size() >= 50) {
                contaminatedAreas.entrySet().removeIf(e -> !e.getValue().isStillContaminated());
            }
            contaminatedAreas.put(centerPos, new EnvironmentalTransformer.ContaminationData(centerPos));
            int radius = Math.min((int) (intensity * 0.5), 36);
            int rSq = radius * radius;
            net.minecraft.util.math.random.Random random = level.getRandom();
            BlockPos.Mutable mutablePos = new BlockPos.Mutable();

            for (int x = -radius; x <= radius; x++) {
                int wx = centerPos.getX() + x;
                for (int z = -radius; z <= radius; z++) {
                    int wz = centerPos.getZ() + z;
                    double distSq = (double) (x * x + z * z);
                    if (distSq > rSq) {
                        continue;
                    }
                    if (!level.isChunkLoaded(wx >> 4, wz >> 4)) {
                        continue;
                    }

                    int surfaceY = level.getTopY(net.minecraft.world.Heightmap.Type.MOTION_BLOCKING, wx, wz);
                    for (int y = surfaceY - 2; y <= surfaceY; y++) {
                        mutablePos.set(wx, y, wz);
                        double distance = Math.sqrt(distSq + (double) ((y - centerPos.getY()) * (y - centerPos.getY())));
                        double contaminationProbability = (1.0 - distance / (double) radius) * Math.min(1.0, intensity * 0.05);
                        contaminationProbability *= EnvironmentalConfig.getContaminationSpreadRate();
                        if (random.nextDouble() < contaminationProbability) {
                            scheduleBlockTransformation(mutablePos, level);
                        }
                    }
                }
            }
        }
    }

    private static void scheduleBlockTransformation(BlockPos pos, ServerWorld level) {
        if (!level.isChunkLoaded(pos.getX() >> 4, pos.getZ() >> 4)) {
            return;
        }
        BlockState currentState = level.getBlockState(pos);
        BlockState newState = getDeadBlockVariant(currentState);
        if (newState != currentState) {
            level.setBlockState(pos, newState, 18);
            if (EnvironmentalConfig.areRadioactiveParticlesEnabled() && level.getRandom().nextInt(EnvironmentalConfig.getRadioactiveParticleDensity()) == 0) {
                level.spawnParticles(ParticleTypes.ASH, (double) pos.getX() + 0.5, (double) pos.getY() + 0.5, (double) pos.getZ() + 0.5, 3, 0.3, 0.3, 0.3, 0.01);
            }
        }
    }

    private static BlockState getDeadBlockVariant(BlockState originalState) {
        Block originalBlock = originalState.getBlock();
        if (originalBlock == Blocks.DIRT
                || originalBlock == Blocks.COARSE_DIRT
                || originalBlock == Blocks.ROOTED_DIRT
                || originalBlock == Blocks.GRASS_BLOCK
                || originalBlock == Blocks.MYCELIUM
                || originalBlock == Blocks.PODZOL) {
            return ModBlocks.DEAD_DIRT.get().getDefaultState();
        } else if (originalBlock == Blocks.OAK_LOG
                || originalBlock == Blocks.OAK_WOOD
                || originalBlock == Blocks.STRIPPED_OAK_LOG
                || originalBlock == Blocks.STRIPPED_OAK_WOOD) {
            return ModBlocks.DEAD_OAK_LOG.get().getDefaultState();
        } else if (originalBlock == Blocks.OAK_PLANKS) {
            return ModBlocks.DEAD_OAK_PLANKS.get().getDefaultState();
        } else {
            return originalState;
        }
    }

    private static boolean isInNuclearWasteland(World level, BlockPos pos) {
        try {
            return level.getBiome(pos).matchesKey(ModBiomes.NUCLEAR_WASTELAND);
        } catch (Exception var3) {
            return false;
        }
    }

    public static void applyRadiationEffectToEntity(Entity entity) {
        if (entity instanceof PlayerEntity player) {
            BlockPos playerPos = player.getBlockPos();
            double contaminationLevel = getContaminationLevelAtPosition(playerPos, player.getWorld());
            if (isInNuclearWasteland(player.getWorld(), playerPos)) {
                contaminationLevel = Math.max(contaminationLevel, 0.7);
            }

            if (contaminationLevel > 0.3) {
                int duration = (int) (contaminationLevel * 100.0);
                int amplifier = (int) (contaminationLevel * 3.0);
                player.addStatusEffect(new StatusEffectInstance(StatusEffects.WEAKNESS, duration, Math.min(amplifier, 2)));
                if (contaminationLevel > 0.6) {
                    player.addStatusEffect(new StatusEffectInstance(StatusEffects.HUNGER, duration, Math.min(amplifier - 1, 1)));
                }

                if (contaminationLevel > 0.8) {
                    player.addStatusEffect(new StatusEffectInstance(StatusEffects.POISON, duration / 2, Math.min(amplifier - 2, 1)));
                }
            }
        }
    }

    public static double getContaminationLevelAtPosition(BlockPos pos, World level) {
        double maxContamination = 0.0;

        for (Entry<BlockPos, EnvironmentalTransformer.ContaminationData> entry : contaminatedAreas.entrySet()) {
            EnvironmentalTransformer.ContaminationData data = entry.getValue();
            if (data.isStillContaminated()) {
                double distance = Math.sqrt(
                        Math.pow((double) (pos.getX() - data.centerPos.getX()), 2.0)
                                + Math.pow((double) (pos.getY() - data.centerPos.getY()), 2.0)
                                + Math.pow((double) (pos.getZ() - data.centerPos.getZ()), 2.0)
                );
                double contaminationAtPos = data.getContaminationLevel() / (1.0 + distance * 0.05);
                maxContamination = Math.max(maxContamination, contaminationAtPos);
            }
        }

        if (isInNuclearWasteland(level, pos)) {
            maxContamination = Math.max(maxContamination, 0.4);
        }

        return Math.min(maxContamination, 1.0);
    }

    public static void onWorldTick(ServerWorld serverLevel) {
        contaminatedAreas.entrySet().removeIf(entry -> !entry.getValue().isStillContaminated());

        for (EnvironmentalTransformer.ContaminationData data : contaminatedAreas.values()) {
            data.tick();
        }

        if (serverLevel.getTime() % 40L == 0L) {
            for (ServerPlayerEntity player : serverLevel.getPlayers()) {
                BlockPos entityPos = player.getBlockPos();
                double contaminationLevel = getContaminationLevelAtPosition(entityPos, serverLevel);
                if (contaminationLevel > 0.3) {
                    applyRadiationEffectToEntity(player);
                }
            }
        }
    }

    public static void decontaminateArea(BlockPos centerPos, double radius) {
        contaminatedAreas.entrySet()
                .removeIf(
                        entry -> {
                            BlockPos sourcePos = entry.getKey();
                            double distance = Math.sqrt(
                                    Math.pow((double) (centerPos.getX() - sourcePos.getX()), 2.0)
                                            + Math.pow((double) (centerPos.getY() - sourcePos.getY()), 2.0)
                                            + Math.pow((double) (centerPos.getZ() - sourcePos.getZ()), 2.0)
                            );
                            return distance <= radius;
                        }
                );
    }

    public static void applyWastelandBiome(ServerWorld level, BlockPos center, int radius) {
        LOGGER.debug("applyWastelandBiome radius={}", radius);
        Registry<Biome> biomeRegistry = level.getRegistryManager().get(RegistryKeys.BIOME);
        RegistryKey<Biome> customKey = ModBiomes.NUCLEAR_WASTELAND;
        Optional<RegistryEntry.Reference<Biome>> optionalBiome = biomeRegistry.getEntry(customKey);
        RegistryEntry<Biome> wastelandBiome;
        if (optionalBiome.isPresent()) {
            wastelandBiome = optionalBiome.get();
            LOGGER.debug("Wasteland biome: custom nuclear_wasteland");
        } else {
            wastelandBiome = biomeRegistry.getEntry(BiomeKeys.SOUL_SAND_VALLEY).orElseThrow();
            LOGGER.debug("Wasteland biome: fallback SOUL_SAND_VALLEY");
        }

        Set<WorldChunk> affectedChunks = new HashSet<>();
        int biomesChanged = 0;

        for (int x = -radius; x <= radius; x += 4) {
            for (int z = -radius; z <= radius; z += 4) {
                double distSq = (double) (x * x + z * z);
                if (!(distSq > (double) (radius * radius))) {
                    int worldX = center.getX() + x;
                    int worldZ = center.getZ() + z;
                    WorldChunk chunk = level.getWorldChunk(new BlockPos(worldX, center.getY(), worldZ));
                    affectedChunks.add(chunk);

                    for (int y = center.getY() - 64; y <= center.getY() + 128; y += 4) {
                        int sectionIndex = level.getSectionIndex(y);
                        if (sectionIndex >= 0 && sectionIndex < chunk.getSectionArray().length) {
                            ChunkSection section = chunk.getSection(sectionIndex);
                            int localX = (worldX & 15) >> 2;
                            int localY = (y & 15) >> 2;
                            int localZ = (worldZ & 15) >> 2;

                            try {
                                if (section.getBiomeContainer() instanceof PalettedContainer<RegistryEntry<Biome>> biomes) {
                                    biomes.set(localX, localY, localZ, wastelandBiome);
                                    biomesChanged++;
                                }
                            } catch (Exception ignored) {
                            }
                        }
                    }
                }
            }
        }

        LOGGER.debug("Wasteland biome paint: {} cells, {} chunks", biomesChanged, affectedChunks.size());

        for (WorldChunk chunk : affectedChunks) {
            chunk.setNeedsSaving(true);
            ChunkDataS2CPacket packet = new ChunkDataS2CPacket(chunk, level.getLightingProvider(), null, null);
            for (ServerPlayerEntity player : level.getChunkManager().chunkLoadingManager.getPlayersWatchingChunk(chunk.getPos(), false)) {
                player.networkHandler.sendPacket(packet);
            }
        }
    }

    public static void transformTerrainAtPosition(ServerWorld level, BlockPos pos) {
        BlockState state = level.getBlockState(pos);
        Block block = state.getBlock();
        Random random = level.getRandom();
        if (state.isIn(BlockTags.LEAVES)) {
            if (random.nextFloat() < 0.8F) {
                level.setBlockState(pos, Blocks.AIR.getDefaultState(), 3);
            } else {
                BlockState oakLeaves = Blocks.OAK_LEAVES.getDefaultState();
                if (state.contains(LeavesBlock.PERSISTENT)) {
                    oakLeaves = oakLeaves.with(LeavesBlock.PERSISTENT, true);
                }
                level.setBlockState(pos, oakLeaves, 3);
            }
        } else if (state.isIn(BlockTags.LOGS)) {
            try {
                level.setBlockState(
                        pos,
                        ModBlocks.DEAD_OAK_LOG.get()
                                .getDefaultState()
                                .with(PillarBlock.AXIS, state.get(PillarBlock.AXIS)),
                        3
                );
            } catch (Exception var6) {
                level.setBlockState(pos, ModBlocks.DEAD_OAK_LOG.get().getDefaultState(), 3);
            }
        } else if (state.isIn(BlockTags.FLOWERS)
                || state.isIn(BlockTags.TALL_FLOWERS)
                || block == Blocks.SHORT_GRASS
                || block == Blocks.TALL_GRASS
                || block == Blocks.FERN
                || block == Blocks.LARGE_FERN
                || block == Blocks.DEAD_BUSH
                || block == Blocks.SWEET_BERRY_BUSH) {
            level.setBlockState(pos, Blocks.AIR.getDefaultState(), 3);
        } else if (block == Blocks.WHEAT || block == Blocks.CARROTS || block == Blocks.POTATOES || block == Blocks.BEETROOTS) {
            level.setBlockState(pos, Blocks.AIR.getDefaultState(), 3);
        } else if (block == Blocks.SNOW || block == Blocks.SNOW_BLOCK || block == Blocks.POWDER_SNOW) {
            level.setBlockState(pos, Blocks.AIR.getDefaultState(), 3);
        } else if (block == Blocks.ICE || block == Blocks.PACKED_ICE || block == Blocks.BLUE_ICE) {
            level.setBlockState(pos, Blocks.WATER.getDefaultState(), 3);
        } else if (block == Blocks.DIRT
                || block == Blocks.ROOTED_DIRT
                || block == Blocks.GRASS_BLOCK
                || block == Blocks.MYCELIUM
                || block == Blocks.PODZOL
                || block == Blocks.COARSE_DIRT
                || block == Blocks.GRAVEL) {
            float r = random.nextFloat();
            if (r < 0.6F) {
                level.setBlockState(pos, ModBlocks.DEAD_DIRT.get().getDefaultState(), 3);
            } else if (r < 0.7F) {
                level.setBlockState(pos, Blocks.COARSE_DIRT.getDefaultState(), 3);
            } else if (r < 0.75F) {
                level.setBlockState(pos, Blocks.BASALT.getDefaultState(), 3);
            } else if (r < 0.8F) {
                level.setBlockState(pos, Blocks.BLACKSTONE.getDefaultState(), 3);
            } else if (r < 0.85F) {
                level.setBlockState(pos, Blocks.MAGMA_BLOCK.getDefaultState(), 3);
            } else if (r < 0.9F && level.getBlockState(pos.up()).isAir()) {
                level.setBlockState(pos.up(), ModBlocks.DEBRIS.get().getDefaultState(), 3);
                level.setBlockState(pos, ModBlocks.DEAD_DIRT.get().getDefaultState(), 3);
            } else if (level.getBlockState(pos.up()).isAir()) {
                level.setBlockState(
                        pos, ModBlocks.RADIOACTIVE_ASH.get().getDefaultState().with(RadioactiveAshBlock.LAYERS, 1 + random.nextInt(3)), 3
                );
            }

            if (random.nextFloat() < 0.02F && level.getBlockState(pos.up()).isAir()) {
                level.setBlockState(pos.up(), Blocks.FIRE.getDefaultState(), 3);
            }
        } else if (block == Blocks.SAND || block == Blocks.RED_SAND) {
            float rx = random.nextFloat();
            if (rx < 0.4F) {
                level.setBlockState(pos, Blocks.GLASS.getDefaultState(), 3);
            } else if (rx < 0.7F) {
                level.setBlockState(pos, Blocks.RED_SAND.getDefaultState(), 3);
            } else if (rx < 0.85F) {
                level.setBlockState(pos, Blocks.ORANGE_STAINED_GLASS.getDefaultState(), 3);
            } else {
                level.setBlockState(pos, Blocks.SANDSTONE.getDefaultState(), 3);
            }
        } else if (block == Blocks.STONE || block == Blocks.ANDESITE || block == Blocks.GRANITE || block == Blocks.DIORITE) {
            float rx = random.nextFloat();
            if (rx < 0.4F) {
                level.setBlockState(pos, Blocks.COBBLESTONE.getDefaultState(), 3);
            } else if (rx < 0.6F) {
                level.setBlockState(pos, Blocks.GRAVEL.getDefaultState(), 3);
            } else if (rx < 0.7F) {
                level.setBlockState(pos, Blocks.BLACKSTONE.getDefaultState(), 3);
            }
        } else if (block == Blocks.WATER) {
            if (random.nextFloat() < 0.8F) {
                level.setBlockState(pos, Blocks.AIR.getDefaultState(), 3);
            }
        }
    }

    public static class ContaminationData {
        private double contaminationLevel = 1.0;
        private int ticksSinceContamination = 0;
        private final BlockPos centerPos;

        public ContaminationData(BlockPos centerPos) {
            this.centerPos = centerPos;
        }

        public void tick() {
            this.ticksSinceContamination++;
            if (this.ticksSinceContamination > 2400) {
                this.contaminationLevel *= 0.999;
            }
        }

        public boolean isStillContaminated() {
            return this.contaminationLevel > 0.05;
        }

        public double getContaminationLevel() {
            return this.contaminationLevel;
        }

        public int getTicksSinceContamination() {
            return this.ticksSinceContamination;
        }
    }
}
