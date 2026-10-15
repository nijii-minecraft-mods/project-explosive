package com.project.explosive.util;

import com.project.explosive.entity.ModEntities;
import com.project.explosive.entity.MultiBlockEntity;
import java.util.ArrayDeque;
import java.util.ArrayList;
import java.util.Collections;
import java.util.Deque;
import java.util.HashMap;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Map.Entry;
import java.util.Set;
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
import net.minecraft.util.math.Direction;
import net.minecraft.util.math.MathHelper;
import net.minecraft.util.math.Vec3d;
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

        double safeRadius = Math.min(radius, 42.0);
        int maxSafeChunks = Math.min(maxChunks, 8);

        int baseX = MathHelper.floor(cx);
        int baseY = MathHelper.floor(cy);
        int baseZ = MathHelper.floor(cz);
        int r = MathHelper.ceil(safeRadius);
        double maxDepth = Math.max(2.0, safeRadius * Math.min(depthRatio, 0.40));
        double r2 = safeRadius * safeRadius;
        Random rnd = level.getRandom();

        List<BlockPos> fallingBlockSpawns = new ArrayList<>();
        Map<BlockPos, BlockState> candidateStates = new HashMap<>();
        Map<BlockPos, BlockState> ejectaCandidateBlocks = new HashMap<>();
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
                    } else if (wy >= topY - 2 && ejectaCandidateBlocks.size() < 400 && rnd.nextFloat() < 0.35F) {
                        ejectaCandidateBlocks.put(mutablePos.toImmutable(), state);
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

        // 1. Launch volcanic shower of physical FallingBlockEntity debris that land as real solid blocks
        Collections.shuffle(fallingBlockSpawns, new java.util.Random(rnd.nextLong()));
        for (BlockPos pos : fallingBlockSpawns) {
            BlockState spawnState = candidateStates.getOrDefault(pos, Blocks.DIRT.getDefaultState());
            FallingBlockEntity falling = FallingBlockEntity.spawnFromBlock(level, pos, spawnState);
            if (falling != null) {
                falling.dropItem = false;

                double dx = (double) pos.getX() + 0.5 - cx;
                double dz = (double) pos.getZ() + 0.5 - cz;
                double dist2D = Math.max(0.5, Math.sqrt(dx * dx + dz * dz));
                double out = (0.55 + rnd.nextDouble() * 0.95) * forceScale;
                double up = (0.75 + rnd.nextDouble() * 1.35) * forceScale;

                falling.setVelocity(
                        (dx / dist2D) * out + (rnd.nextDouble() - 0.5) * 0.2,
                        up,
                        (dz / dist2D) * out + (rnd.nextDouble() - 0.5) * 0.2
                );
                falling.velocityModified = true;

                // Safety cleanup only if stuck in void/boundary after 300 ticks (15s)
                DelayedTaskScheduler.schedule(level, 300L, () -> {
                    if (!falling.isRemoved()) {
                        falling.discard();
                    }
                });
            }
        }

        // 2. Launch physics MultiBlock debris clusters (large tumbling rock/dirt chunks that land as rubble)
        if (!ejectaCandidateBlocks.isEmpty() && maxSafeChunks > 0) {
            Set<BlockPos> remaining = new HashSet<>(ejectaCandidateBlocks.keySet());
            List<BlockPos> seeds = new ArrayList<>(ejectaCandidateBlocks.keySet());
            Collections.shuffle(seeds, new java.util.Random(rnd.nextLong()));
            List<CraterUtil.Cluster> clusters = new ArrayList<>();

            for (BlockPos seed : seeds) {
                if (clusters.size() >= maxSafeChunks) {
                    break;
                }
                if (remaining.contains(seed)) {
                    int target = 8 + rnd.nextInt(12);
                    clusters.add(growIrregularCluster(seed, remaining, ejectaCandidateBlocks, target, cx, cz, rnd));
                }
            }

            for (CraterUtil.Cluster c : clusters) {
                launchCluster(level, c, cx, cz, safeRadius, forceScale);
            }
        }

        // Erupting earth sound effects
        SoundUtil.playAt(level, cx, cy, cz, SoundEvents.ENTITY_GENERIC_EXPLODE.value(), SoundCategory.BLOCKS, 4.0F, 0.8F);
        SoundUtil.playAt(level, cx, cy, cz, SoundEvents.BLOCK_STONE_BREAK, SoundCategory.BLOCKS, 3.5F, 0.7F);
    }

    private static CraterUtil.Cluster growIrregularCluster(
            BlockPos seed, Set<BlockPos> remaining, Map<BlockPos, BlockState> all, int target, double cx, double cz, Random rnd
    ) {
        CraterUtil.Cluster c = new CraterUtil.Cluster();
        Deque<BlockPos> frontier = new ArrayDeque<>();
        remaining.remove(seed);
        c.add(seed, all.get(seed), dist(seed, cx, cz));
        frontier.add(seed);
        Direction[] dirs = Direction.values();

        while (!frontier.isEmpty() && c.blocks.size() < target) {
            BlockPos cur = frontier.poll();
            shuffle(dirs, rnd);

            for (Direction d : dirs) {
                if (c.blocks.size() >= target) {
                    break;
                }
                BlockPos n = cur.offset(d);
                if (remaining.contains(n) && rnd.nextFloat() < 0.82F) {
                    remaining.remove(n);
                    c.add(n, all.get(n), dist(n, cx, cz));
                    frontier.add(n);
                }
            }
        }

        return c;
    }

    private static void launchCluster(ServerWorld level, CraterUtil.Cluster c, double cx, double cz, double radius, double forceScale) {
        if (!c.blocks.isEmpty() && c.min != null) {
            BlockPos min = c.min;
            int minX = min.getX();
            int minY = min.getY();
            int minZ = min.getZ();
            Map<BlockPos, BlockState> rel = new HashMap<>(c.blocks.size());
            int sumX = 0;
            int sumY = 0;
            int sumZ = 0;

            for (Entry<BlockPos, BlockState> e : c.blocks.entrySet()) {
                BlockPos rp = e.getKey().subtract(min);
                rel.put(rp, e.getValue());
                sumX += rp.getX();
                sumY += rp.getY();
                sumZ += rp.getZ();
            }

            int n = rel.size();
            BlockPos centerOffset = new BlockPos(sumX / n, sumY / n, sumZ / n);
            MultiBlockEntity ent = new MultiBlockEntity(ModEntities.MULTI_BLOCK.get(), level);
            ent.setPosition((double) minX, (double) minY, (double) minZ);
            ent.setBlocks(rel, centerOffset);
            double ccx = (double) minX + 0.5;
            double ccz = (double) minZ + 0.5;
            double invRadius = 1.0 / radius;
            double prox = Math.max(0.0, 1.0 - c.minDist * invRadius);
            double dirX = ccx - cx;
            double dirZ = ccz - cz;
            double dlSq = dirX * dirX + dirZ * dirZ;
            if (dlSq < 1.0E-4) {
                double a = level.getRandom().nextDouble() * Math.PI * 2.0;
                dirX = Math.cos(a);
                dirZ = Math.sin(a);
            } else {
                double invDl = 1.0 / Math.sqrt(dlSq);
                dirX *= invDl;
                dirZ *= invDl;
            }

            double out = (0.35 + prox * 0.85) * forceScale;
            double up = (0.65 + prox * 1.3) * forceScale;
            Random rnd = level.getRandom();
            ent.setVelocity(new Vec3d(
                    dirX * out + (rnd.nextDouble() - 0.5) * 0.2,
                    up + rnd.nextDouble() * 0.25,
                    dirZ * out + (rnd.nextDouble() - 0.5) * 0.2
            ));
            ent.setNoGravity(false);
            level.spawnEntity(ent);
        }
    }

    private static double dist(BlockPos p, double cx, double cz) {
        double dx = (double) p.getX() + 0.5 - cx;
        double dz = (double) p.getZ() + 0.5 - cz;
        return Math.sqrt(dx * dx + dz * dz);
    }

    private static void shuffle(Direction[] arr, Random rnd) {
        for (int i = arr.length - 1; i > 0; i--) {
            int j = rnd.nextInt(i + 1);
            Direction tmp = arr[i];
            arr[i] = arr[j];
            arr[j] = tmp;
        }
    }

    private static final class Cluster {
        final Map<BlockPos, BlockState> blocks = new HashMap<>();
        BlockPos min;
        double minDist = Double.MAX_VALUE;

        Cluster() {
        }

        void add(BlockPos p, BlockState s, double d) {
            this.blocks.put(p, s);
            if (this.min == null) {
                this.min = p;
            } else {
                this.min = new BlockPos(
                        Math.min(this.min.getX(), p.getX()),
                        Math.min(this.min.getY(), p.getY()),
                        Math.min(this.min.getZ(), p.getZ())
                );
            }

            if (d < this.minDist) {
                this.minDist = d;
            }
        }
    }
}
