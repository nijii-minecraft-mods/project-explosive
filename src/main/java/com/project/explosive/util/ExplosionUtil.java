package com.project.explosive.util;

import com.project.explosive.block.ModBlocks;
import java.util.Set;
import net.minecraft.block.Block;
import net.minecraft.block.BlockState;
import net.minecraft.block.Blocks;
import net.minecraft.entity.Entity;
import net.minecraft.entity.FallingBlockEntity;
import net.minecraft.entity.LivingEntity;
import net.minecraft.entity.damage.DamageSource;
import net.minecraft.registry.tag.BlockTags;
import net.minecraft.server.world.ServerWorld;
import net.minecraft.util.math.BlockPos;
import net.minecraft.util.math.Box;
import net.minecraft.util.math.Vec3d;
import net.minecraft.world.World;

public final class ExplosionUtil {
    private ExplosionUtil() {
    }

    public static boolean isSolidBlock(World level, BlockPos pos) {
        BlockState state = level.getBlockState(pos);
        return !state.isAir() && state.blocksMovement() && !state.isOf(Blocks.BEDROCK);
    }

    public static BlockState getDeadBlockVariant(BlockState originalState) {
        String blockName = originalState.getBlock().toString().toLowerCase();
        if (blockName.contains("grass")
                || blockName.contains("dirt")
                || blockName.contains("podzol")
                || blockName.contains("mycelium")
                || blockName.contains("farmland")
                || blockName.contains("path")) {
            return ModBlocks.DEAD_DIRT.get().getDefaultState();
        } else if (blockName.contains("log") || blockName.contains("wood") || blockName.contains("stem")) {
            return ModBlocks.DEAD_OAK_LOG.get().getDefaultState();
        } else if (blockName.contains("planks")) {
            return ModBlocks.DEAD_OAK_PLANKS.get().getDefaultState();
        } else if (blockName.contains("stone") && !blockName.contains("sand")) {
            return Blocks.COBBLESTONE.getDefaultState();
        } else {
            return originalState;
        }
    }

    public static boolean transformBlockToDeadBlock(ServerWorld level, double x, double y, double z, float probability) {
        if (level.getRandom().nextFloat() > probability) {
            return false;
        } else {
            BlockPos pos = BlockPos.ofFloored(x, y, z);
            BlockState currentState = level.getBlockState(pos);
            if (!currentState.isAir() && currentState.getFluidState().isEmpty() && !currentState.isOf(Blocks.BEDROCK)) {
                BlockState deadVariant = getDeadBlockVariant(currentState);
                if (deadVariant != currentState) {
                    level.setBlockState(pos, deadVariant, 3);
                    return true;
                }
            }
            return false;
        }
    }

    public static FallingBlockEntity transformBlockToFallingBlock(ServerWorld level, double x, double y, double z, float probability) {
        if (level.getRandom().nextFloat() > probability) {
            return null;
        } else {
            BlockPos pos = BlockPos.ofFloored(x, y, z);
            BlockState state = level.getBlockState(pos);
            if (!state.isAir() && state.getFluidState().isEmpty() && !state.isOf(Blocks.BEDROCK) && !(state.getHardness(level, pos) < 0.0F)) {
                level.setBlockState(pos, Blocks.AIR.getDefaultState(), 3);
                FallingBlockEntity fallingBlock = FallingBlockEntity.spawnFromBlock(level, pos, state);
                if (fallingBlock != null) {
                    double vx = (level.getRandom().nextDouble() - 0.5) * 0.3;
                    double vy = level.getRandom().nextDouble() * 0.5 + 0.2;
                    double vz = (level.getRandom().nextDouble() - 0.5) * 0.3;
                    fallingBlock.setVelocity(vx, vy, vz);
                }
                return fallingBlock;
            } else {
                return null;
            }
        }
    }

    public static void damageEntitiesAt(
            ServerWorld level,
            Vec3d position,
            float baseDamage,
            BlockPos originPos,
            double maxDistance,
            Set<Integer> entitiesHit,
            DamageSource damageSource,
            boolean setFire
    ) {
        Box damageBox = new Box(position.x - 3.0, position.y - 3.0, position.z - 3.0, position.x + 3.0, position.y + 3.0, position.z + 3.0);

        for (Entity entity : level.getOtherEntities(null, damageBox)) {
            if (entitiesHit == null || !entitiesHit.contains(entity.getId())) {
                if (entitiesHit != null) {
                    entitiesHit.add(entity.getId());
                }

                double dx = entity.getX() - ((double) originPos.getX() + 0.5);
                double dy = entity.getY() - ((double) originPos.getY() + 0.5);
                double dz = entity.getZ() - ((double) originPos.getZ() + 0.5);
                double distanceFromOrigin = Math.sqrt(dx * dx + dy * dy + dz * dz);
                float distanceFactor = (float) Math.max(0.2, 1.0 - distanceFromOrigin / maxDistance);
                float finalDamage = baseDamage * distanceFactor;
                if (entity instanceof LivingEntity livingEntity) {
                    livingEntity.damage(damageSource, finalDamage);
                    if (setFire) {
                        int fireTicks = (int) (200.0F * distanceFactor);
                        if (fireTicks > livingEntity.getFireTicks()) {
                            livingEntity.setFireTicks(fireTicks);
                        }
                    }
                }

                if (distanceFromOrigin > 0.001) {
                    Vec3d direction = new Vec3d(dx, dy, dz).normalize();
                    double knockbackStrength = 1.5 * (double) distanceFactor;
                    entity.setVelocity(
                            entity.getVelocity().add(direction.x * knockbackStrength, direction.y * knockbackStrength * 0.5 + 0.3, direction.z * knockbackStrength)
                    );
                }
            }
        }
    }

    public static boolean isVegetation(BlockState state) {
        if (state.isIn(BlockTags.FLOWERS)
                || state.isIn(BlockTags.SAPLINGS)
                || state.isIn(BlockTags.SMALL_FLOWERS)
                || state.isIn(BlockTags.TALL_FLOWERS)) {
            return true;
        } else {
            String blockId = state.getBlock().toString().toLowerCase();
            return blockId.contains("flower")
                    || blockId.contains("sapling")
                    || blockId.contains("dead_bush")
                    || blockId.contains("seagrass")
                    || blockId.contains("mushroom")
                    || blockId.contains("fungus")
                    || blockId.contains("fern")
                    || blockId.contains("bush");
        }
    }

    public static void destroyVegetationInArea(ServerWorld level, double centerX, double centerY, double centerZ, int radius) {
        BlockPos.Mutable pos = new BlockPos.Mutable();
        for (int x = -radius; x <= radius; x += 2) {
            for (int z = -radius; z <= radius; z += 2) {
                if (x * x + z * z <= radius * radius) {
                    for (int y = -1; y <= 2; y++) {
                        pos.set((int) (centerX + (double) x), (int) (centerY + (double) y), (int) (centerZ + (double) z));
                        BlockState state = level.getBlockState(pos);
                        if (state.isOf(Blocks.SHORT_GRASS) || isVegetation(state)) {
                            level.setBlockState(pos, Blocks.AIR.getDefaultState(), 2);
                        }
                    }
                }
            }
        }
    }
}
