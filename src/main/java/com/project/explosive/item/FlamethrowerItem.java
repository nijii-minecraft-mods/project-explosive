package com.project.explosive.item;

import com.project.explosive.particle.ModParticles;
import com.project.explosive.util.FirePropagationManager;
import com.project.explosive.util.ParticleUtil;
import com.project.explosive.util.SoundUtil;
import eu.pb4.polymer.core.api.item.PolymerItem;
import eu.pb4.polymer.resourcepack.api.PolymerModelData;
import eu.pb4.polymer.resourcepack.api.PolymerResourcePackUtils;
import net.minecraft.block.Blocks;
import net.minecraft.entity.Entity;
import net.minecraft.entity.LivingEntity;
import net.minecraft.entity.player.PlayerEntity;
import net.minecraft.item.Item;
import net.minecraft.item.ItemStack;
import net.minecraft.item.Items;
import net.minecraft.particle.ParticleTypes;
import net.minecraft.server.network.ServerPlayerEntity;
import net.minecraft.server.world.ServerWorld;
import net.minecraft.sound.SoundCategory;
import net.minecraft.sound.SoundEvents;
import net.minecraft.util.ActionResult;
import net.minecraft.util.Hand;
import net.minecraft.util.Identifier;
import net.minecraft.util.TypedActionResult;
import net.minecraft.util.UseAction;
import net.minecraft.util.math.BlockPos;
import net.minecraft.util.math.Box;
import net.minecraft.util.math.Direction;
import net.minecraft.util.math.Vec3d;
import net.minecraft.world.World;
import org.jetbrains.annotations.Nullable;

public class FlamethrowerItem extends Item implements PolymerItem {
    private static final int MAX_USE_DURATION = 72000;
    private final PolymerModelData modelData;

    public FlamethrowerItem(Settings settings) {
        super(settings);
        this.modelData = PolymerResourcePackUtils.requestModel(Items.BLAZE_ROD, Identifier.of("projectexplosive", "item/flamethrower"));
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
            world.playSound(
                    null,
                    player.getX(),
                    player.getY(),
                    player.getZ(),
                    SoundEvents.ITEM_FLINTANDSTEEL_USE,
                    SoundCategory.PLAYERS,
                    1.2F,
                    1.0F
            );
        }
        return new TypedActionResult<>(ActionResult.SUCCESS, stack);
    }

    @Override
    public void usageTick(World world, LivingEntity user, ItemStack stack, int remainingUseTicks) {
        if (!world.isClient() && user instanceof ServerPlayerEntity player) {
            ServerWorld serverWorld = (ServerWorld) world;
            int ticksUsed = this.getMaxUseTime(stack, user) - remainingUseTicks;

            if (ticksUsed % 2 == 0) {
                // Continuous roaring audio
                if (ticksUsed % 4 == 0) {
                    BlockPos bp = player.getBlockPos();
                    world.playSound(
                            null,
                            (double) bp.getX() + 0.5,
                            (double) bp.getY() + 1.0,
                            (double) bp.getZ() + 0.5,
                            SoundEvents.ENTITY_BLAZE_SHOOT,
                            SoundCategory.PLAYERS,
                            2.0F,
                            0.75F + world.random.nextFloat() * 0.35F
                    );
                    world.playSound(
                            null,
                            (double) bp.getX() + 0.5,
                            (double) bp.getY() + 1.0,
                            (double) bp.getZ() + 0.5,
                            SoundEvents.ITEM_FIRECHARGE_USE,
                            SoundCategory.PLAYERS,
                            1.5F,
                            1.1F + world.random.nextFloat() * 0.4F
                    );
                }

                Vec3d lookVec = player.getRotationVector();
                Vec3d muzzle = player.getEyePos().add(lookVec.multiply(0.8));
                double fuelSpeed = 1.5;
                double gravity = 0.02;
                int maxSteps = 32;
                int numStreams = 3;

                for (int stream = 0; stream < numStreams; stream++) {
                    double jitterX = (world.random.nextDouble() - 0.5) * 0.12;
                    double jitterY = (world.random.nextDouble() - 0.5) * 0.08;
                    double jitterZ = (world.random.nextDouble() - 0.5) * 0.12;
                    double vx = lookVec.x * fuelSpeed + jitterX;
                    double vy = lookVec.y * fuelSpeed + jitterY;
                    double vz = lookVec.z * fuelSpeed + jitterZ;
                    double cx = muzzle.x;
                    double cy = muzzle.y;
                    double cz = muzzle.z;

                    for (int step = 0; step < maxSteps; step++) {
                        vy -= gravity;
                        vx *= 0.97;
                        vy *= 0.98;
                        vz *= 0.97;
                        vx += (world.random.nextDouble() - 0.5) * 0.02;
                        vz += (world.random.nextDouble() - 0.5) * 0.02;
                        cx += vx;
                        cy += vy;
                        cz += vz;

                        double distFromMuzzle = Math.sqrt(
                                (cx - muzzle.x) * (cx - muzzle.x) + (cy - muzzle.y) * (cy - muzzle.y) + (cz - muzzle.z) * (cz - muzzle.z)
                        );
                        double coneRadius = 0.08 + distFromMuzzle * 0.04;

                        // 1. Core expanding flame jet particles
                        if (step % 2 == 0) {
                            serverWorld.spawnParticles(ParticleTypes.FLAME, cx, cy, cz, 2, coneRadius, coneRadius, coneRadius, 0.02);
                            serverWorld.spawnParticles(ParticleTypes.SMALL_FLAME, cx, cy, cz, 1, coneRadius * 0.7, coneRadius * 0.7, coneRadius * 0.7, 0.01);
                        }

                        // Crackling burning droplets (Lava particles)
                        if (world.random.nextFloat() < 0.22F) {
                            serverWorld.spawnParticles(ParticleTypes.LAVA, cx, cy, cz, 1, 0.04, 0.04, 0.04, 0.0);
                        }

                        // Billowing dark smoke
                        if (distFromMuzzle > 4.0 && world.random.nextFloat() < 0.25F) {
                            serverWorld.spawnParticles(ParticleTypes.SMOKE, cx, cy + 0.1, cz, 1, coneRadius * 0.5, 0.02, coneRadius * 0.5, 0.01);
                        }

                        // 2. Real fire ignition on surface impact
                        BlockPos blockAt = BlockPos.ofFloored(cx, cy, cz);
                        if (!serverWorld.getBlockState(blockAt).isAir() && !serverWorld.getBlockState(blockAt).getFluidState().isStill()) {
                            if (stream == 0 && ticksUsed % 3 == 0) {
                                BlockPos firePos = blockAt.up();
                                if (serverWorld.getBlockState(firePos).isAir() && Blocks.FIRE.getDefaultState().canPlaceAt(serverWorld, firePos)) {
                                    serverWorld.setBlockState(firePos, Blocks.FIRE.getDefaultState(), 3);
                                } else {
                                    for (Direction dir : Direction.Type.HORIZONTAL) {
                                        BlockPos side = blockAt.offset(dir);
                                        if (serverWorld.getBlockState(side).isAir() && Blocks.FIRE.getDefaultState().canPlaceAt(serverWorld, side)) {
                                            serverWorld.setBlockState(side, Blocks.FIRE.getDefaultState(), 3);
                                            break;
                                        }
                                    }
                                }
                                FirePropagationManager.getInstance().startFirePropagation(serverWorld, cx, cy + 0.5, cz, 20);
                            }

                            // Fiery impact splash
                            serverWorld.spawnParticles(ParticleTypes.LAVA, cx, cy + 0.2, cz, 3, 0.15, 0.15, 0.15, 0.05);
                            serverWorld.spawnParticles(ParticleTypes.FLAME, cx, cy + 0.2, cz, 6, 0.25, 0.2, 0.25, 0.06);
                            break;
                        }

                        // 3. Ground brushfire ignition under stream
                        if (step % 6 == 0 && world.random.nextFloat() < 0.3F) {
                            BlockPos downPos = blockAt.down();
                            BlockPos atPos = blockAt;
                            if (serverWorld.getBlockState(atPos).isAir() && !serverWorld.getBlockState(downPos).isAir()) {
                                if (Blocks.FIRE.getDefaultState().canPlaceAt(serverWorld, atPos)) {
                                    serverWorld.setBlockState(atPos, Blocks.FIRE.getDefaultState(), 3);
                                }
                            }
                        }

                        // 4. Entity roasting and knockback
                        if (step % 3 == 0) {
                            Box hitBox = new Box(cx - 0.7, cy - 0.7, cz - 0.7, cx + 0.7, cy + 0.7, cz + 0.7);
                            for (LivingEntity target : serverWorld.getEntitiesByClass(LivingEntity.class, hitBox, e -> e != player)) {
                                target.setFireTicks(Math.max(target.getFireTicks(), 140));
                                target.damage(player.getDamageSources().onFire(), 5.0F);
                                target.addVelocity(lookVec.x * 0.15, 0.06, lookVec.z * 0.15);
                            }
                        }
                    }
                }

                if (!player.getAbilities().creativeMode && ticksUsed % 20 == 0) {
                    stack.damage(1, player, LivingEntity.getSlotForHand(player.getActiveHand()));
                }
            }
        }
    }

    @Override
    public Item getPolymerItem(ItemStack itemStack, @Nullable ServerPlayerEntity player) {
        return Items.BLAZE_ROD;
    }

    @Override
    public int getPolymerCustomModelData(ItemStack itemStack, @Nullable ServerPlayerEntity player) {
        return this.modelData != null ? this.modelData.value() : -1;
    }
}
