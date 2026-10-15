package com.project.explosive.util;

import com.project.explosive.util.DelayedTaskScheduler;
import java.util.ArrayList;
import java.util.Collections;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import net.minecraft.block.BlockState;
import net.minecraft.block.Blocks;
import net.minecraft.block.PlantBlock;
import net.minecraft.entity.FallingBlockEntity;
import net.minecraft.particle.BlockStateParticleEffect;
import net.minecraft.particle.ParticleTypes;
import net.minecraft.server.world.ServerWorld;
import net.minecraft.sound.SoundCategory;
import net.minecraft.sound.SoundEvents;
import net.minecraft.util.math.BlockPos;
import net.minecraft.util.math.MathHelper;
import net.minecraft.util.math.random.Random;
import net.minecraft.world.Heightmap;

public final class CraterUtil {
    private CraterUtil() {
    }

    public static void digCraterWithEjecta(
            ServerWorld level, double cx, double cy, double cz, double radius, double depthRatio, int maxChunks, double forceScale
    ) {
        if (radius < 1.0) {
            return;
        }

        // Clamp radius to a safe maximum to prevent server freeze
        double safeRadius = Math.min(radius, 42.0);

        int baseX = MathHelper.floor(cx);
        int baseY = MathHelper.floor(cy);
        int baseZ = MathHelper.floor(cz);
        int r = MathHelper.ceil(safeRadius);
        // Wide dish shape: shallower depth ratio
        double maxDepth = Math.max(2.0, safeRadius * Math.min(depthRatio, 0.40));
        double r2 = safeRadius * safeRadius;
        Random rnd = level.getRandom();

        List<BlockPos> fallingBlockSpawns = new ArrayList<>();
        Map<BlockPos, BlockState> candidateStates = new HashMap<>();
        BlockPos.Mutable mutablePos = new BlockPos.Mutable();
        BlockState air = Blocks.AIR.getDefaultState();

        int maxFlyingBlocks = Math.min(100, (int) (safeRadius * 3.0));
        int flyingBlocksCount = 0;

        for (int dx = -r; dx <= r; dx++) {
            double dxSq = (double) dx * (double) dx;
            if (dxSq > r2) {
                continue;
            }
            int maxDz = (int) Math.sqrt(r2 - dxSq);
            int wx = baseX + dx;

            for (int dz = -maxDz; dz <= maxDz; dz++) {
                double distSq = dxSq + (double) dz * (double) dz;
                if (distSq > r2) {
                    continue;
                }
                int wz = baseZ + dz;
                if (!level.isChunkLoaded(wx >> 4, wz >> 4)) {
                    continue;
                }

                // Smooth paraboloid bowl for a realistic wide explosion dish
                double normDist = Math.sqrt(distSq) / safeRadius;
                int colDepth = Math.max(1, (int) Math.round(maxDepth * (1.0 - normDist * normDist)));

                int surfaceY = level.getTopY(Heightmap.Type.MOTION_BLOCKING_NO_LEAVES, wx, wz) - 1;
                int bottomY = Math.max(level.getBottomY() + 1, Math.min(surfaceY - colDepth, baseY - colDepth));
                int topY = Math.min(surfaceY, Math.max(baseY + (int) (safeRadius * 0.35), surfaceY));

                // Clear plants on surface
                mutablePos.set(wx, surfaceY + 1, wz);
                if (level.getBlockState(mutablePos).getBlock() instanceof PlantBlock) {
                    level.setBlockState(mutablePos, air, 18);
                }

                for (int wy = bottomY; wy <= topY; wy++) {
                    mutablePos.set(wx, wy, wz);
                    BlockState state = level.getBlockState(mutablePos);
                    if (state.isAir() || state.isOf(Blocks.BEDROCK)) {
                        continue;
                    }

                    double dy = (double) (wy - baseY);
                    double dist3D = Math.sqrt(distSq + dy * dy);

                    // Ejecta: candidate blocks in outer rim shoot outward as real FallingBlockEntity
                    if (flyingBlocksCount < maxFlyingBlocks && dist3D >= safeRadius * 0.35 && rnd.nextFloat() < 0.35F) {
                        fallingBlockSpawns.add(mutablePos.toImmutable());
                        candidateStates.put(mutablePos.toImmutable(), state);
                        flyingBlocksCount++;
                    }

                    // Block break particle burst
                    if (rnd.nextFloat() < 0.06F) {
                        level.spawnParticles(
                                new BlockStateParticleEffect(ParticleTypes.BLOCK, state),
                                (double) wx + 0.5, (double) wy + 0.5, (double) wz + 0.5,
                                3, 0.25, 0.25, 0.25, 0.15
                        );
                    }

                    // Shatter and remove all blocks: dirt, grass, stone, ores, sand, deepslate, wood, etc.
                    level.setBlockState(mutablePos, air, 18);
                }
            }
        }

        // Launch volcanic shower of physical FallingBlockEntity debris
        Collections.shuffle(fallingBlockSpawns, new java.util.Random(rnd.nextLong()));
        for (BlockPos pos : fallingBlockSpawns) {
            BlockState spawnState = candidateStates.getOrDefault(pos, Blocks.DIRT.getDefaultState());
            FallingBlockEntity falling = FallingBlockEntity.spawnFromBlock(level, pos, spawnState);
            if (falling != null) {
                falling.dropItem = false;
                // Shatter on impact: NEVER places solid blocks, never rubberbands or pushes the player!
                falling.setDestroyedOnLanding();

                double dx = (double) pos.getX() + 0.5 - cx;
                double dz = (double) pos.getZ() + 0.5 - cz;
                double dist2D = Math.max(0.5, Math.sqrt(dx * dx + dz * dz));
                double out = (0.45 + rnd.nextDouble() * 0.85) * forceScale;
                double up = (0.65 + rnd.nextDouble() * 1.1) * forceScale;

                falling.setVelocity(
                        (dx / dist2D) * out + (rnd.nextDouble() - 0.5) * 0.2,
                        up,
                        (dz / dist2D) * out + (rnd.nextDouble() - 0.5) * 0.2
                );
                falling.velocityModified = true;

                // Auto-cleanup after 35 ticks (1.75s) so zero debris entities linger in the world
                DelayedTaskScheduler.schedule(level, 35L, () -> {
                    if (!falling.isRemoved()) {
                        falling.discard();
                    }
                });
            }
        }

        // Erupting earth sound effects
        SoundUtil.playAt(level, cx, cy, cz, SoundEvents.ENTITY_GENERIC_EXPLODE.value(), SoundCategory.BLOCKS, 4.0F, 0.8F);
        SoundUtil.playAt(level, cx, cy, cz, SoundEvents.BLOCK_STONE_BREAK, SoundCategory.BLOCKS, 3.5F, 0.7F);
    }
}
