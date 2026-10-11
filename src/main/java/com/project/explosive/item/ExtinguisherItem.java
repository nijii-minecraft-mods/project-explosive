package com.project.explosive.item;

import com.project.explosive.block.RadioactiveAshBlock;
import com.project.explosive.util.FirePropagationManager;
import com.project.explosive.util.SoundUtil;
import eu.pb4.polymer.core.api.item.PolymerItem;
import eu.pb4.polymer.resourcepack.api.PolymerModelData;
import eu.pb4.polymer.resourcepack.api.PolymerResourcePackUtils;
import net.minecraft.block.AbstractFireBlock;
import net.minecraft.block.BlockState;
import net.minecraft.block.Blocks;
import net.minecraft.entity.EquipmentSlot;
import net.minecraft.entity.LivingEntity;
import net.minecraft.entity.player.PlayerEntity;
import net.minecraft.fluid.FluidState;
import net.minecraft.item.Item;
import net.minecraft.item.ItemStack;
import net.minecraft.item.Items;
import net.minecraft.particle.ParticleTypes;
import net.minecraft.registry.tag.FluidTags;
import net.minecraft.server.network.ServerPlayerEntity;
import net.minecraft.server.world.ServerWorld;
import net.minecraft.sound.SoundCategory;
import net.minecraft.sound.SoundEvents;
import net.minecraft.util.Hand;
import net.minecraft.util.Identifier;
import net.minecraft.util.TypedActionResult;
import net.minecraft.util.UseAction;
import net.minecraft.util.math.BlockPos;
import net.minecraft.util.math.Box;
import net.minecraft.util.math.Vec3d;
import net.minecraft.world.World;
import org.jetbrains.annotations.Nullable;

public class ExtinguisherItem extends Item implements PolymerItem {
    private static final int MAX_USE_DURATION = 72000;
    private final PolymerModelData modelData;

    public ExtinguisherItem(Settings settings) {
        super(settings);
        this.modelData = PolymerResourcePackUtils.requestModel(Items.IRON_HOE, Identifier.of("projectexplosive", "item/extinguisher"));
    }

    @Override
    public UseAction getUseAction(ItemStack stack) {
        return UseAction.BOW;
    }

    @Override
    public int getMaxUseTime(ItemStack stack, LivingEntity user) {
        return MAX_USE_DURATION;
    }

    @Override
    public TypedActionResult<ItemStack> use(World world, PlayerEntity player, Hand hand) {
        ItemStack stack = player.getStackInHand(hand);
        player.setCurrentHand(hand);
        if (!world.isClient()) {
            BlockPos bp = player.getBlockPos();
            SoundUtil.playAt(
                    world,
                    (double) bp.getX() + 0.5,
                    (double) bp.getY() + 1.0,
                    (double) bp.getZ() + 0.5,
                    SoundEvents.ITEM_BOTTLE_EMPTY,
                    SoundCategory.PLAYERS,
                    1.0F,
                    1.2F
            );
        }
        return TypedActionResult.consume(stack);
    }

    @Override
    public void usageTick(World world, LivingEntity user, ItemStack stack, int remainingUseTicks) {
        if (!world.isClient() && user instanceof PlayerEntity player && world instanceof ServerWorld serverWorld) {
            int ticksUsed = this.getMaxUseTime(stack, user) - remainingUseTicks;
            if (ticksUsed % 2 == 0) {
                if (ticksUsed % 5 == 0) {
                    BlockPos bp = player.getBlockPos();
                    SoundUtil.playAt(
                            world,
                            (double) bp.getX() + 0.5,
                            (double) bp.getY() + 1.0,
                            (double) bp.getZ() + 0.5,
                            SoundEvents.BLOCK_FIRE_EXTINGUISH,
                            SoundCategory.PLAYERS,
                            0.5F,
                            1.2F + world.random.nextFloat() * 0.4F
                    );
                }

                Vec3d lookVec = player.getRotationVector();
                Vec3d muzzle = player.getEyePos().add(lookVec.multiply(0.8));
                double foamSpeed = 1.6;
                double gravity = 0.04;
                int maxSteps = 25;
                int numStreams = 3;

                for (int stream = 0; stream < numStreams; stream++) {
                    double jitterX = (world.random.nextDouble() - 0.5) * 0.2;
                    double jitterY = (world.random.nextDouble() - 0.5) * 0.15;
                    double jitterZ = (world.random.nextDouble() - 0.5) * 0.2;
                    double vx = lookVec.x * foamSpeed + jitterX;
                    double vy = lookVec.y * foamSpeed + jitterY;
                    double vz = lookVec.z * foamSpeed + jitterZ;
                    double cx = muzzle.x;
                    double cy = muzzle.y;
                    double cz = muzzle.z;

                    for (int step = 0; step < maxSteps; step++) {
                        vy -= gravity;
                        vx *= 0.96;
                        vy *= 0.96;
                        vz *= 0.96;
                        cx += vx;
                        cy += vy;
                        cz += vz;

                        BlockPos blockAt = BlockPos.ofFloored(cx, cy, cz);

                        if (step % 2 == 0) {
                            FirePropagationManager.getInstance().extinguishFiresInRadius(serverWorld, cx, cy, cz, 3.0);
                            BlockState stateAt = serverWorld.getBlockState(blockAt);
                            if (stateAt.getBlock() instanceof AbstractFireBlock) {
                                serverWorld.removeBlock(blockAt, false);
                            }

                            // Cool lava
                            FluidState fluid = serverWorld.getFluidState(blockAt);
                            if (fluid.isIn(FluidTags.LAVA)) {
                                if (fluid.isStill()) {
                                    serverWorld.setBlockState(blockAt, Blocks.OBSIDIAN.getDefaultState());
                                } else {
                                    serverWorld.setBlockState(blockAt, Blocks.COBBLESTONE.getDefaultState());
                                }
                                serverWorld.playSound(null, blockAt, SoundEvents.BLOCK_LAVA_EXTINGUISH, SoundCategory.BLOCKS, 0.5F, 2.6F);
                            }

                            // Cool/clean radioactive ash
                            if (stateAt.getBlock() instanceof RadioactiveAshBlock) {
                                int layers = stateAt.get(RadioactiveAshBlock.LAYERS);
                                if (layers > 1) {
                                    serverWorld.setBlockState(blockAt, stateAt.with(RadioactiveAshBlock.LAYERS, layers - 1), 3);
                                } else {
                                    serverWorld.removeBlock(blockAt, false);
                                }
                            }
                        }

                        if (step % 3 == 0) {
                            Box hitBox = new Box(cx - 1.0, cy - 1.0, cz - 1.0, cx + 1.0, cy + 1.0, cz + 1.0);
                            for (LivingEntity target : serverWorld.getEntitiesByClass(LivingEntity.class, hitBox, e -> true)) {
                                target.extinguish();
                            }
                        }

                        if (!serverWorld.getBlockState(blockAt).isAir() && !serverWorld.getBlockState(blockAt).getFluidState().isStill()) {
                            if (stream == 0 && ticksUsed % 4 == 0) {
                                for (int sp = 0; sp < 6; sp++) {
                                    double splashAngle = world.random.nextDouble() * Math.PI * 2.0;
                                    double splashSpeed = 0.05 + world.random.nextDouble() * 0.1;
                                    serverWorld.spawnParticles(
                                            ParticleTypes.SPLASH,
                                            cx,
                                            cy + 0.2,
                                            cz,
                                            1,
                                            Math.cos(splashAngle) * splashSpeed,
                                            0.05,
                                            Math.sin(splashAngle) * splashSpeed,
                                            0.05
                                    );
                                }
                            }
                            break;
                        }

                        if (step % 3 == 0) {
                            serverWorld.spawnParticles(ParticleTypes.CLOUD, cx, cy, cz, 1, vx * 0.1, vy * 0.1, vz * 0.1, 0.02);
                        }

                        if (step % 4 == 0) {
                            serverWorld.spawnParticles(
                                    ParticleTypes.SPLASH,
                                    cx + (world.random.nextDouble() - 0.5) * 0.5,
                                    cy + (world.random.nextDouble() - 0.5) * 0.5,
                                    cz + (world.random.nextDouble() - 0.5) * 0.5,
                                    1,
                                    vx * 0.1,
                                    vy * 0.1,
                                    vz * 0.1,
                                    0.05
                            );
                        }
                    }
                }

                if (!player.getAbilities().creativeMode && ticksUsed % 15 == 0) {
                    stack.damage(1, player, LivingEntity.getSlotForHand(player.getActiveHand()));
                }
            }
        }
    }

    @Override
    public Item getPolymerItem(ItemStack itemStack, @Nullable ServerPlayerEntity player) {
        return Items.IRON_HOE;
    }

    @Override
    public int getPolymerCustomModelData(ItemStack itemStack, @Nullable ServerPlayerEntity player) {
        return this.modelData != null ? this.modelData.value() : -1;
    }
}
