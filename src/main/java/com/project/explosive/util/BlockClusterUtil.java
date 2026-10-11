package com.project.explosive.util;

import java.util.HashMap;
import java.util.HashSet;
import java.util.LinkedList;
import java.util.Map;
import java.util.Queue;
import java.util.Set;
import net.minecraft.block.BlockState;
import net.minecraft.block.Blocks;
import net.minecraft.util.math.BlockPos;
import net.minecraft.util.math.Direction;
import net.minecraft.world.World;

public class BlockClusterUtil {
    public static BlockClusterUtil.ClusterData collectBlockCluster(World level, BlockPos startPos, int maxBlocks) {
        if (level.isClient()) {
            return null;
        }

        BlockState startState = level.getBlockState(startPos);
        if (startState.isOf(Blocks.BEDROCK)) {
            return null;
        }

        Map<BlockPos, BlockState> cluster = new HashMap<>();
        Queue<BlockPos> toCheck = new LinkedList<>();
        Set<BlockPos> checked = new HashSet<>();
        toCheck.add(startPos);
        checked.add(startPos);

        while (!toCheck.isEmpty() && cluster.size() < maxBlocks) {
            BlockPos current = toCheck.poll();
            BlockState state = level.getBlockState(current);
            if (!state.isAir() && !state.isOf(Blocks.BEDROCK) && state.getHardness(level, current) >= 0.0F) {
                cluster.put(current.subtract(startPos), state);

                for (Direction dir : Direction.values()) {
                    BlockPos neighbor = current.offset(dir);
                    if (!checked.contains(neighbor)) {
                        BlockState neighborState = level.getBlockState(neighbor);
                        if (!neighborState.isOf(Blocks.BEDROCK)) {
                            checked.add(neighbor);
                            toCheck.add(neighbor);
                        }
                    }
                }
            }
        }

        if (!cluster.isEmpty()) {
            BlockPos centerOffset = calculateCenterOffset(cluster.keySet());
            return new BlockClusterUtil.ClusterData(cluster, centerOffset);
        } else {
            return null;
        }
    }

    private static BlockPos calculateCenterOffset(Set<BlockPos> positions) {
        if (positions.isEmpty()) {
            return BlockPos.ORIGIN;
        }

        int minX = Integer.MAX_VALUE;
        int minY = Integer.MAX_VALUE;
        int minZ = Integer.MAX_VALUE;
        int maxX = Integer.MIN_VALUE;
        int maxY = Integer.MIN_VALUE;
        int maxZ = Integer.MIN_VALUE;

        for (BlockPos pos : positions) {
            minX = Math.min(minX, pos.getX());
            minY = Math.min(minY, pos.getY());
            minZ = Math.min(minZ, pos.getZ());
            maxX = Math.max(maxX, pos.getX());
            maxY = Math.max(maxY, pos.getY());
            maxZ = Math.max(maxZ, pos.getZ());
        }

        return new BlockPos((minX + maxX) / 2, (minY + maxY) / 2, (minZ + maxZ) / 2);
    }

    public static class ClusterData {
        private final Map<BlockPos, BlockState> blocks;
        private final BlockPos centerOffset;

        public ClusterData(Map<BlockPos, BlockState> blocks, BlockPos centerOffset) {
            this.blocks = blocks;
            this.centerOffset = centerOffset;
        }

        public Map<BlockPos, BlockState> getBlocks() {
            return this.blocks;
        }

        public BlockPos getCenterOffset() {
            return this.centerOffset;
        }
    }
}
