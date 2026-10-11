package com.project.explosive.block;

import com.project.explosive.environmental.RadiationManager;
import eu.pb4.polymer.core.api.block.PolymerBlock;
import net.minecraft.block.AbstractBlock;
import net.minecraft.block.Block;
import net.minecraft.block.BlockState;
import net.minecraft.block.Blocks;
import net.minecraft.block.LandingBlock;
import net.minecraft.block.ShapeContext;
import net.minecraft.entity.Entity;
import net.minecraft.entity.FallingBlockEntity;
import net.minecraft.entity.LivingEntity;
import net.minecraft.entity.ai.pathing.NavigationType;
import net.minecraft.entity.effect.StatusEffectInstance;
import net.minecraft.entity.effect.StatusEffects;
import net.minecraft.entity.player.PlayerEntity;
import net.minecraft.item.ItemPlacementContext;
import net.minecraft.particle.ParticleTypes;
import net.minecraft.server.world.ServerWorld;
import net.minecraft.state.StateManager;
import net.minecraft.state.property.IntProperty;
import net.minecraft.state.property.Properties;
import net.minecraft.util.math.BlockPos;
import net.minecraft.util.math.Direction;
import net.minecraft.util.math.Vec3d;
import net.minecraft.util.math.random.Random;
import net.minecraft.util.shape.VoxelShape;
import net.minecraft.util.shape.VoxelShapes;
import net.minecraft.world.BlockView;
import net.minecraft.world.World;
import net.minecraft.world.WorldAccess;
import net.minecraft.world.WorldView;

public class RadioactiveAshBlock extends Block implements LandingBlock, PolymerBlock {
    public static final int MAX_HEIGHT = 8;
    public static final IntProperty LAYERS = Properties.LAYERS;
    protected static final VoxelShape[] SHAPE_BY_LAYER = new VoxelShape[]{
            VoxelShapes.empty(),
            Block.createCuboidShape(0.0, 0.0, 0.0, 16.0, 2.0, 16.0),
            Block.createCuboidShape(0.0, 0.0, 0.0, 16.0, 4.0, 16.0),
            Block.createCuboidShape(0.0, 0.0, 0.0, 16.0, 6.0, 16.0),
            Block.createCuboidShape(0.0, 0.0, 0.0, 16.0, 8.0, 16.0),
            Block.createCuboidShape(0.0, 0.0, 0.0, 16.0, 10.0, 16.0),
            Block.createCuboidShape(0.0, 0.0, 0.0, 16.0, 12.0, 16.0),
            Block.createCuboidShape(0.0, 0.0, 0.0, 16.0, 14.0, 16.0),
            Block.createCuboidShape(0.0, 0.0, 0.0, 16.0, 16.0, 16.0)
    };

    public RadioactiveAshBlock(AbstractBlock.Settings settings) {
        super(settings);
        this.setDefaultState(this.stateManager.getDefaultState().with(LAYERS, 1));
    }

    @Override
    protected void appendProperties(StateManager.Builder<Block, BlockState> builder) {
        builder.add(LAYERS);
    }

    @Override
    protected VoxelShape getOutlineShape(BlockState state, BlockView world, BlockPos pos, ShapeContext context) {
        return SHAPE_BY_LAYER[state.get(LAYERS)];
    }

    @Override
    protected VoxelShape getCollisionShape(BlockState state, BlockView world, BlockPos pos, ShapeContext context) {
        int layers = state.get(LAYERS);
        return layers <= 2 ? VoxelShapes.empty() : SHAPE_BY_LAYER[layers - 2];
    }

    @Override
    protected VoxelShape getCullingShape(BlockState state, BlockView world, BlockPos pos) {
        return SHAPE_BY_LAYER[state.get(LAYERS)];
    }

    @Override
    protected VoxelShape getCameraCollisionShape(BlockState state, BlockView world, BlockPos pos, ShapeContext context) {
        return SHAPE_BY_LAYER[state.get(LAYERS)];
    }

    @Override
    protected boolean hasSidedTransparency(BlockState state) {
        return true;
    }

    @Override
    protected boolean canPlaceAt(BlockState state, WorldView world, BlockPos pos) {
        BlockState belowState = world.getBlockState(pos.down());
        return belowState.isOf(this) || Block.isFaceFullSquare(belowState.getCollisionShape(world, pos.down()), Direction.UP);
    }

    @Override
    protected BlockState getStateForNeighborUpdate(BlockState state, Direction direction, BlockState neighborState, WorldAccess world, BlockPos pos, BlockPos neighborPos) {
        if (direction == Direction.DOWN) {
            world.scheduleBlockTick(pos, this, 2);
        }
        return super.getStateForNeighborUpdate(state, direction, neighborState, world, pos, neighborPos);
    }

    @Override
    protected void onBlockAdded(BlockState state, World world, BlockPos pos, BlockState oldState, boolean notify) {
        world.scheduleBlockTick(pos, this, 2);
    }

    @Override
    protected void scheduledTick(BlockState state, ServerWorld world, BlockPos pos, Random random) {
        if (this.canFall(world, pos)) {
            FallingBlockEntity fallingBlock = FallingBlockEntity.spawnFromBlock(world, pos, state);
            fallingBlock.setHurtEntities(0.5F, 10);
        }
    }

    @Override
    protected void randomTick(BlockState state, ServerWorld world, BlockPos pos, Random random) {
        if (this.canFall(world, pos)) {
            world.scheduleBlockTick(pos, this, 2);
        } else {
            if (random.nextFloat() < 0.01F) {
                int layers = state.get(LAYERS);
                if (layers > 1) {
                    world.setBlockState(pos, state.with(LAYERS, layers - 1), 3);
                } else {
                    world.removeBlock(pos, false);
                }
            }
        }
    }

    private boolean canFall(World world, BlockPos pos) {
        BlockPos belowPos = pos.down();
        BlockState belowState = world.getBlockState(belowPos);
        return belowState.isAir() || belowState.isReplaceable() || belowState.getFluidState().isStill();
    }

    @Override
    protected boolean canReplace(BlockState state, ItemPlacementContext context) {
        int currentLayers = state.get(LAYERS);
        if (!context.getStack().isOf(this.asItem()) || currentLayers >= 8) {
            return currentLayers == 1;
        } else {
            return context.canReplaceExisting() ? context.getSide() == Direction.UP : true;
        }
    }

    @Override
    public BlockState getPlacementState(ItemPlacementContext context) {
        BlockState existingState = context.getWorld().getBlockState(context.getBlockPos());
        if (existingState.isOf(this)) {
            int currentLayers = existingState.get(LAYERS);
            return existingState.with(LAYERS, Math.min(8, currentLayers + 1));
        } else {
            return super.getPlacementState(context);
        }
    }

    @Override
    protected boolean canPathfindThrough(BlockState state, NavigationType type) {
        return type == NavigationType.LAND ? state.get(LAYERS) < 5 : false;
    }

    @Override
    protected void onEntityCollision(BlockState state, World world, BlockPos pos, Entity entity) {
        int layers = state.get(LAYERS);
        double slowFactor = 0.4 + 0.08 * (double) (8 - layers);
        Vec3d motion = entity.getVelocity();
        entity.setVelocity(motion.x * slowFactor, motion.y, motion.z * slowFactor);

        if (world.isClient && entity.getVelocity().horizontalLengthSquared() > 0.001) {
            Random random = world.getRandom();
            for (int i = 0; i < 2; i++) {
                double px = entity.getX() + (random.nextDouble() - 0.5) * 0.6;
                double py = (double) pos.getY() + (double) layers * 0.125 + 0.1;
                double pz = entity.getZ() + (random.nextDouble() - 0.5) * 0.6;
                world.addParticle(
                        ParticleTypes.SMOKE, px, py, pz,
                        (random.nextDouble() - 0.5) * 0.05,
                        random.nextDouble() * 0.05,
                        (random.nextDouble() - 0.5) * 0.05
                );
            }
        }

        if (!world.isClient && entity instanceof LivingEntity livingEntity) {
            if (livingEntity instanceof PlayerEntity player) {
                double dosePerTick = 0.001 * (double) layers;
                RadiationManager.PlayerRadiationData data = RadiationManager.getPlayerRadiationData(player);
                data.addDose(dosePerTick);
            } else if (world.getTime() % 40L == 0L) {
                float damage = 0.5F + (float) layers * 0.25F;
                livingEntity.damage(livingEntity.getDamageSources().magic(), damage);
                livingEntity.addStatusEffect(new StatusEffectInstance(StatusEffects.HUNGER, 100, 0, false, false));
                if (layers >= 3) {
                    livingEntity.addStatusEffect(new StatusEffectInstance(StatusEffects.WEAKNESS, 60, 0, false, false));
                }
                if (layers >= 5) {
                    livingEntity.addStatusEffect(new StatusEffectInstance(StatusEffects.WITHER, 40, 0, false, true));
                }
            }
        }
    }

    @Override
    public void onSteppedOn(World world, BlockPos pos, BlockState state, Entity entity) {
        super.onSteppedOn(world, pos, state, entity);
        if (!world.isClient() && entity instanceof LivingEntity livingEntity) {
            int layers = state.get(LAYERS);
            if (livingEntity instanceof PlayerEntity player) {
                RadiationManager.PlayerRadiationData data = RadiationManager.getPlayerRadiationData(player);
                data.addDose(0.002 * (double) layers);
            } else {
                livingEntity.damage(livingEntity.getDamageSources().magic(), 0.5F + (float) layers * 0.25F);
            }
        }
    }

    @Override
    public void randomDisplayTick(BlockState state, World world, BlockPos pos, Random random) {
        if (random.nextInt(5) == 0) {
            double px = (double) pos.getX() + random.nextDouble();
            double py = (double) pos.getY() + (double) state.get(LAYERS) * 0.125 + 0.05;
            double pz = (double) pos.getZ() + random.nextDouble();
            world.addParticle(ParticleTypes.SMOKE, px, py, pz, 0.0, 0.01, 0.0);
        }
    }

    @Override
    public BlockState getPolymerBlockState(BlockState state) {
        return Blocks.BASALT.getDefaultState();
    }

    public static boolean addAshLayer(World level, BlockPos pos) {
        BlockState existingState = level.getBlockState(pos);
        if (existingState.getBlock() instanceof RadioactiveAshBlock) {
            int currentLayers = existingState.get(LAYERS);
            if (currentLayers < 8) {
                level.setBlockState(pos, existingState.with(LAYERS, currentLayers + 1), 3);
                return true;
            } else {
                return false;
            }
        } else {
            if (existingState.isAir()) {
                BlockState belowState = level.getBlockState(pos.down());
                if (belowState.isSolid() || belowState.getBlock() instanceof RadioactiveAshBlock) {
                    level.setBlockState(pos, ModBlocks.RADIOACTIVE_ASH.get().getDefaultState(), 3);
                    return true;
                }
            }
            return false;
        }
    }
}
