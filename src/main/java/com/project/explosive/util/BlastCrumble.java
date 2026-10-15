package com.project.explosive.util;

import com.project.explosive.particle.ModParticles;
import it.unimi.dsi.fastutil.longs.LongArrayFIFOQueue;
import it.unimi.dsi.fastutil.longs.LongArrayList;
import it.unimi.dsi.fastutil.longs.LongIterator;
import it.unimi.dsi.fastutil.longs.LongOpenHashSet;
import net.minecraft.block.Block;
import net.minecraft.block.BlockState;
import net.minecraft.block.Blocks;
import net.minecraft.entity.FallingBlockEntity;
import net.minecraft.server.world.ServerWorld;
import net.minecraft.util.math.BlockPos;
import net.minecraft.util.math.Direction;
import net.minecraft.util.math.MathHelper;

public final class BlastCrumble {
    private static final int FAST = 18;
    private static final int SEVER_SCAN_HEIGHT = 96;
    private static final int SEVER_AIR_STOP = 10;
    private static final int SEVER_FALL_CAP = 80;
    private static final int SEVER_BLOCK_CAP = 600;
    private static final int SEVER_PASS_DELAY = 2;

    private BlastCrumble() {
    }

    /**
     * Determines if a block is solid, structural, or terrain that is breakable by explosions
     * and subject to structural collapse physics when anchor/foundation is removed.
     * All normal blocks (dirt, grass, stone, ores, deepslate, sand, wood, etc.) return true.
     */
    public static boolean isStructural(BlockState s) {
        if (s == null || s.isAir()) {
            return false;
        }
        Block b = s.getBlock();
        return b != Blocks.BEDROCK && b != Blocks.END_PORTAL && b != Blocks.END_PORTAL_FRAME && b != Blocks.BARRIER;
    }

    public static void blastAndCrumble(ServerWorld level, double cx, double cy, double cz, double radius) {
        if (radius < 1.0) {
            return;
        }

        double safeRadius = Math.min(radius, 40.0);
        int r = MathHelper.ceil(safeRadius);
        double r2 = safeRadius * safeRadius;
        int bx = MathHelper.floor(cx);
        int by = MathHelper.floor(cy);
        int bz = MathHelper.floor(cz);
        BlockPos.Mutable p = new BlockPos.Mutable();
        BlockState air = Blocks.AIR.getDefaultState();
        int dustBudget = 64;

        for (int dx = -r; dx <= r; dx++) {
            double dxSq = (double) dx * (double) dx;
            if (dxSq > r2) {
                continue;
            }
            int wx = bx + dx;

            for (int dz = -r; dz <= r; dz++) {
                double hSq = dxSq + (double) dz * (double) dz;
                if (hSq > r2) {
                    continue;
                }
                int wz = bz + dz;
                if (!level.isChunkLoaded(wx >> 4, wz >> 4)) {
                    continue;
                }
                int dyMax = (int) Math.sqrt(r2 - hSq);

                for (int dy = -dyMax; dy <= dyMax; dy++) {
                    int wy = by + dy;
                    p.set(wx, wy, wz);
                    BlockState s = level.getBlockState(p);
                    if (isStructural(s) && !(s.getHardness(level, p) < 0.0F)) {
                        if (dustBudget > 0 && level.getRandom().nextFloat() < 0.22F) {
                            double ox = dx == 0 && dz == 0 ? 0.0 : (double) dx / Math.max(1.0, Math.sqrt(hSq));
                            double oz = dz == 0 && dz == 0 ? 0.0 : (double) dz / Math.max(1.0, Math.sqrt(hSq));
                            ParticleUtil.spawnLongDistanceParticles(
                                    level,
                                    ModParticles.SMOKE_PARTICLE.get(),
                                    (double) wx + 0.5,
                                    (double) wy + 0.5,
                                    (double) wz + 0.5,
                                    1,
                                    ox * 0.2,
                                    0.06 + level.getRandom().nextDouble() * 0.08,
                                    oz * 0.2
                            );
                            dustBudget--;
                        }

                        // Shatter block completely: dirt, grass, stone, ores, deepslate, sand, wood, etc.
                        level.setBlockState(p, air, 18);
                    }
                }
            }
        }

        scheduleCrumble(level, bx, by, bz, r + 4, 10);
        DelayedTaskScheduler.schedule(level, 2L, () -> collapseSevered(level, bx, by, bz, r));
    }

    private static void collapseSevered(ServerWorld level, int cx, int cy, int cz, int rad) {
        int hr = Math.min(rad + 3, 24);
        int yMin = Math.max(level.getBottomY(), cy - rad);
        int yTop = Math.min(level.getTopY() - 1, cy + Math.min(rad * 2, 48));
        LongOpenHashSet structural = new LongOpenHashSet(4096);
        BlockPos.Mutable p = new BlockPos.Mutable();

        for (int dx = -hr; dx <= hr; dx++) {
            int wx = cx + dx;

            for (int dz = -hr; dz <= hr; dz++) {
                int wz = cz + dz;
                if (!level.isChunkLoaded(wx >> 4, wz >> 4)) {
                    continue;
                }
                int airRun = 0;

                for (int wy = yMin; wy <= yTop; wy++) {
                    p.set(wx, wy, wz);
                    if (isStructural(level.getBlockState(p))) {
                        structural.add(BlockPos.asLong(wx, wy, wz));
                        airRun = 0;
                    } else if (++airRun >= 10 && wy > cy + rad) {
                        break;
                    }
                }
            }
        }

        if (!structural.isEmpty()) {
            LongOpenHashSet supported = new LongOpenHashSet(structural.size());
            LongArrayFIFOQueue queue = new LongArrayFIFOQueue();
            LongIterator it = structural.iterator();

            while (it.hasNext()) {
                long key = it.nextLong();
                int x = BlockPos.unpackLongX(key);
                int y = BlockPos.unpackLongY(key);
                int z = BlockPos.unpackLongZ(key);
                boolean anchored;
                if (Math.abs(x - cx) == hr || Math.abs(z - cz) == hr || y == yMin) {
                    anchored = true;
                } else if (!structural.contains(BlockPos.asLong(x, y - 1, z))) {
                    p.set(x, y - 1, z);
                    BlockState below = level.getBlockState(p);
                    anchored = !below.isAir() && below.isSideSolidFullSquare(level, p, Direction.UP);
                } else {
                    anchored = false;
                }

                if (anchored && supported.add(key)) {
                    queue.enqueue(key);
                }
            }

            while (!queue.isEmpty()) {
                long keyx = queue.dequeueLong();
                int xx = BlockPos.unpackLongX(keyx);
                int yx = BlockPos.unpackLongY(keyx);
                int zx = BlockPos.unpackLongZ(keyx);

                for (Direction d : Direction.values()) {
                    long n = BlockPos.asLong(xx + d.getOffsetX(), yx + d.getOffsetY(), zx + d.getOffsetZ());
                    if (structural.contains(n) && supported.add(n)) {
                        queue.enqueue(n);
                    }
                }
            }

            if (supported.size() != structural.size()) {
                LongArrayList severed = new LongArrayList(structural.size() - supported.size());
                LongIterator itx = structural.iterator();

                while (itx.hasNext()) {
                    long keyx = itx.nextLong();
                    if (!supported.contains(keyx)) {
                        severed.add(keyx);
                    }
                }

                severed.sort((a, b) -> Integer.compare(BlockPos.unpackLongY(a), BlockPos.unpackLongY(b)));
                DelayedTaskScheduler.schedule(level, 5L, () -> severedPass(level, severed, 0));
            }
        }
    }

    private static void severedPass(ServerWorld level, LongArrayList severed, int index) {
        BlockPos.Mutable p = new BlockPos.Mutable();
        BlockPos.Mutable q = new BlockPos.Mutable();
        BlockState air = Blocks.AIR.getDefaultState();
        int falls = 0;
        int processed = 0;
        int dustBudget = 24;

        while (index < severed.size() && processed < SEVER_BLOCK_CAP) {
            long key = severed.getLong(index++);
            int x = BlockPos.unpackLongX(key);
            int y = BlockPos.unpackLongY(key);
            int z = BlockPos.unpackLongZ(key);
            p.set(x, y, z);
            BlockState s = level.getBlockState(p);
            if (isStructural(s) && !(s.getHardness(level, p) < 0.0F)) {
                processed++;
                if (falls < SEVER_FALL_CAP && hasAirNeighbor(level, q, x, y, z)) {
                    FallingBlockEntity falling = FallingBlockEntity.spawnFromBlock(level, p.toImmutable(), s);
                    falling.dropItem = false;
                    falls++;
                } else {
                    if (dustBudget > 0 && level.getRandom().nextFloat() < 0.15F) {
                        ParticleUtil.spawnLongDistanceParticles(
                                level,
                                ModParticles.SMOKE_PARTICLE.get(),
                                (double) x + 0.5,
                                (double) y + 0.5,
                                (double) z + 0.5,
                                1,
                                (level.getRandom().nextDouble() - 0.5) * 0.15,
                                0.04 + level.getRandom().nextDouble() * 0.05,
                                (level.getRandom().nextDouble() - 0.5) * 0.15
                        );
                        dustBudget--;
                    }

                    level.setBlockState(p, air, 18);
                }
            }
        }

        if (index < severed.size()) {
            int next = index;
            DelayedTaskScheduler.schedule(level, 2L, () -> severedPass(level, severed, next));
        }
    }

    private static boolean hasAirNeighbor(ServerWorld level, BlockPos.Mutable q, int x, int y, int z) {
        for (Direction d : Direction.values()) {
            q.set(x + d.getOffsetX(), y + d.getOffsetY(), z + d.getOffsetZ());
            if (level.getBlockState(q).isAir()) {
                return true;
            }
        }
        return false;
    }

    private static void scheduleCrumble(ServerWorld level, int cx, int cy, int cz, int rad, int passesLeft) {
        if (passesLeft > 0) {
            DelayedTaskScheduler.schedule(level, 3L, () -> crumblePass(level, cx, cy, cz, rad, passesLeft));
        }
    }

    private static void crumblePass(ServerWorld level, int cx, int cy, int cz, int rad, int passesLeft) {
        int falls = 0;
        boolean any = false;
        BlockPos.Mutable p = new BlockPos.Mutable();
        BlockPos.Mutable q = new BlockPos.Mutable();

        label46:
        for (int wy = cy - rad; wy <= cy + rad; wy++) {
            for (int dx = -rad; dx <= rad; dx++) {
                int wx = cx + dx;

                for (int dz = -rad; dz <= rad; dz++) {
                    int wz = cz + dz;
                    p.set(wx, wy, wz);
                    BlockState s = level.getBlockState(p);
                    if (isStructural(s)) {
                        q.set(wx, wy - 1, wz);
                        BlockState below = level.getBlockState(q);
                        if (!below.isSideSolidFullSquare(level, q, Direction.UP) && !isStructural(below) && horizontalSupports(level, q, wx, wy, wz) < 2) {
                            FallingBlockEntity falling = FallingBlockEntity.spawnFromBlock(level, p.toImmutable(), s);
                            falling.dropItem = false;
                            any = true;
                            if (++falls >= 64) {
                                break label46;
                            }
                        }
                    }
                }
            }
        }

        if (any) {
            scheduleCrumble(level, cx, cy, cz, rad, passesLeft - 1);
        }
    }

    private static int horizontalSupports(ServerWorld level, BlockPos.Mutable q, int wx, int wy, int wz) {
        int n = 0;
        if (isStructural(level.getBlockState(q.set(wx + 1, wy, wz)))) {
            n++;
        }
        if (isStructural(level.getBlockState(q.set(wx - 1, wy, wz)))) {
            n++;
        }
        if (isStructural(level.getBlockState(q.set(wx, wy, wz + 1)))) {
            n++;
        }
        if (isStructural(level.getBlockState(q.set(wx, wy, wz - 1)))) {
            n++;
        }
        return n;
    }
}
