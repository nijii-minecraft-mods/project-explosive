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
import net.minecraft.server.world.ServerWorld;
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

        int baseX = MathHelper.floor(cx);
        int baseZ = MathHelper.floor(cz);
        int r = MathHelper.ceil(radius);
        double maxDepth = radius * depthRatio;
        double r2 = radius * radius;
        double invR2 = 1.0 / r2;
        Random rnd = level.getRandom();
        Map<BlockPos, BlockState> craterBlocks = new HashMap<>(r * r);
        BlockPos.Mutable mutablePos = new BlockPos.Mutable();

        for (int dx = -r; dx <= r; dx++) {
            double dxSq = (double) dx * (double) dx;
            if (!(dxSq > r2)) {
                int maxDz = (int) Math.sqrt(r2 - dxSq);
                int wx = baseX + dx;

                for (int dz = -maxDz; dz <= maxDz; dz++) {
                    double distSq = dxSq + (double) dz * (double) dz;
                    if (!(distSq > r2)) {
                        int wz = baseZ + dz;
                        int colDepth = (int) Math.round(maxDepth * (1.0 - distSq * invR2));
                        if (colDepth >= 1) {
                            int surfaceY = level.getTopY(Heightmap.Type.MOTION_BLOCKING_NO_LEAVES, wx, wz) - 1;
                            mutablePos.set(wx, surfaceY + 1, wz);
                            if (level.getBlockState(mutablePos).getBlock() instanceof PlantBlock) {
                                level.setBlockState(mutablePos, Blocks.AIR.getDefaultState(), 18);
                                mutablePos.set(wx, surfaceY + 2, wz);
                                if (level.getBlockState(mutablePos).getBlock() instanceof PlantBlock) {
                                    level.setBlockState(mutablePos, Blocks.AIR.getDefaultState(), 18);
                                }
                            }

                            int bottomY = surfaceY - colDepth;

                            for (int wy = bottomY; wy <= surfaceY; wy++) {
                                mutablePos.set(wx, wy, wz);
                                BlockState state = level.getBlockState(mutablePos);
                                if (!state.isAir()
                                        && !state.isOf(Blocks.BEDROCK)
                                        && state.getFluidState().isEmpty()
                                        && !(state.getHardness(level, mutablePos) < 0.0F)) {
                                    craterBlocks.put(mutablePos.toImmutable(), state);
                                }
                            }
                        }
                    }
                }
            }
        }

        if (!craterBlocks.isEmpty()) {
            Set<BlockPos> remaining = new HashSet<>(craterBlocks.keySet());
            List<BlockPos> seeds = new ArrayList<>(craterBlocks.keySet());
            Collections.shuffle(seeds, new java.util.Random(rnd.nextLong()));
            List<CraterUtil.Cluster> clusters = new ArrayList<>();

            for (BlockPos seed : seeds) {
                if (clusters.size() >= maxChunks) {
                    break;
                }

                if (remaining.contains(seed)) {
                    int target = 14 + rnd.nextInt(17);
                    clusters.add(growIrregularCluster(seed, remaining, craterBlocks, target, cx, cz, rnd));
                }
            }

            BlockState air = Blocks.AIR.getDefaultState();
            for (BlockPos pos : craterBlocks.keySet()) {
                level.setBlockState(pos, air, 18);
            }

            for (CraterUtil.Cluster c : clusters) {
                launchCluster(level, c, cx, cz, radius, forceScale);
            }
        }
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

            double out = (0.25 + prox * 0.65) * forceScale;
            double up = (0.55 + prox * 1.1) * forceScale;
            Random rnd = level.getRandom();
            ent.setVelocity(new Vec3d(
                    dirX * out + (rnd.nextDouble() - 0.5) * 0.15,
                    up + rnd.nextDouble() * 0.2,
                    dirZ * out + (rnd.nextDouble() - 0.5) * 0.15
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

    private static void shuffle(Direction[] a, Random rnd) {
        for (int i = a.length - 1; i > 0; i--) {
            int j = rnd.nextInt(i + 1);
            Direction t = a[i];
            a[i] = a[j];
            a[j] = t;
        }
    }

    private static final class Cluster {
        final Map<BlockPos, BlockState> blocks = new HashMap<>();
        BlockPos min = null;
        double minDist = Double.MAX_VALUE;

        void add(BlockPos pos, BlockState state, double dist) {
            BlockPos p = pos.toImmutable();
            this.blocks.put(p, state);
            this.minDist = Math.min(this.minDist, dist);
            if (this.min == null) {
                this.min = p;
            } else {
                this.min = new BlockPos(Math.min(this.min.getX(), p.getX()), Math.min(this.min.getY(), p.getY()), Math.min(this.min.getZ(), p.getZ()));
            }
        }
    }
}
