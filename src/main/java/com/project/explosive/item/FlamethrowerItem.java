package com.project.explosive.item;

import com.project.explosive.particle.ModParticles;
import com.project.explosive.util.FirePropagationManager;
import com.project.explosive.util.ParticleUtil;
import com.project.explosive.util.SoundUtil;
import eu.pb4.polymer.core.api.item.PolymerItem;
import eu.pb4.polymer.resourcepack.api.PolymerModelData;
import eu.pb4.polymer.resourcepack.api.PolymerResourcePackUtils;
import net.minecraft.block.Blocks;
import net.minecraft.entity.EquipmentSlot;
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
import net.minecraft.util.Hand;
import net.minecraft.util.Identifier;
import net.minecraft.util.TypedActionResult;
import net.minecraft.util.UseAction;
import net.minecraft.util.math.BlockPos;
import net.minecraft.util.math.Box;
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
            BlockPos bp = player.getBlockPos();
            SoundUtil.playAt(
                    world,
                    (double) bp.getX() + 0.5,
                    (double) bp.getY() + 1.0,
                    (double) bp.getZ() + 0.5,
                    SoundEvents.ENTITY_BLAZE_SHOOT,
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
            if (ticksUsed % 3 == 0) {
                if (ticksUsed % 6 == 0) {
                    BlockPos bp = player.getBlockPos();
                    SoundUtil.playAt(
                            world,
                            (double) bp.getX() + 0.5,
                            (double) bp.getY() + 1.0,
                            (double) bp.getZ() + 0.5,
                            SoundEvents.ENTITY_BLAZE_SHOOT,
                            SoundCategory.PLAYERS,
                            2.0F,
                            0.8F + world.random.nextFloat() * 0.3F
                    );
                }

                Vec3d lookVec = player.getRotationVector();
                Vec3d muzzle = player.getEyePos().add(lookVec.multiply(0.8));
                double fuelSpeed = 1.4;
                double gravity = 0.025;
                int maxSteps = 30;
                int numStreams = 3;

                for (int stream = 0; stream < numStreams; stream++) {
                    double jitterX = (world.random.nextDouble() - 0.5) * 0.14;
                    double jitterY = (world.random.nextDouble() - 0.5) * 0.1;
                    double jitterZ = (world.random.nextDouble() - 0.5) * 0.14;
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

                        BlockPos blockAt = BlockPos.ofFloored(cx, cy, cz);
                        if (!serverWorld.getBlockState(blockAt).isAir() && !serverWorld.getBlockState(blockAt).getFluidState().isStill()) {
                            if (stream != 0) {
                                break;
                            }

                            if (ticksUsed % 6 == 0) {
                                FirePropagationManager.getInstance().startFirePropagation(serverWorld, cx, cy + 0.5, cz, 20);
                            }

                            for (int sp = 0; sp < 4; sp++) {
                                double splashAngle = world.random.nextDouble() * Math.PI * 2.0;
                                double splashSpeed = 0.05 + world.random.nextDouble() * 0.12;
                                ParticleUtil.spawnLongDistanceParticles(
                                        serverWorld,
                                        ModParticles.FIRE_FLAME_PARTICLE.get(),
                                        cx,
                                        cy + 0.2,
                                        cz,
                                        1,
                                        Math.cos(splashAngle) * splashSpeed,
                                        0.03 + world.random.nextDouble() * 0.06,
                                        Math.sin(splashAngle) * splashSpeed
                                );
                            }
                            break;
                        }

                        double distFromMuzzle = Math.sqrt(
                                (cx - muzzle.x) * (cx - muzzle.x) + (cy - muzzle.y) * (cy - muzzle.y) + (cz - muzzle.z) * (cz - muzzle.z)
                        );

                        if (step % 2 == 0) {
                            ParticleUtil.spawnLongDistanceParticles(
                                    serverWorld, ModParticles.FIRE_FLAME_PARTICLE.get(), cx, cy, cz, 1, vx * 0.15, vy * 0.15, vz * 0.15
                            );
                            serverWorld.spawnParticles(ParticleTypes.FLAME, cx, cy, cz, 1, vx * 0.05, vy * 0.05, vz * 0.05, 0.01);
                        }

                        if (step % 5 == 0) {
                            ParticleUtil.spawnLongDistanceParticles(
                                    serverWorld,
                                    ModParticles.SMALL_FIRE_PARTICLE.get(),
                                    cx + (world.random.nextDouble() - 0.5) * 0.4,
                                    cy + (world.random.nextDouble() - 0.5) * 0.4,
                                    cz + (world.random.nextDouble() - 0.5) * 0.4,
                                    1,
                                    vx * 0.1,
                                    vy * 0.1,
                                    vz * 0.1
                            );
                        }

                        if (distFromMuzzle > 10.0 && world.random.nextFloat() < 0.1F) {
                            ParticleUtil.spawnLongDistanceParticles(
                                    serverWorld, ModParticles.FIRE_SMOKE_PARTICLE.get(), cx, cy + 0.3, cz, 1, 0.0, 0.02, 0.0
                            );
                        }

                        if (distFromMuzzle > 5.0 && world.random.nextFloat() < 0.05F) {
                            ParticleUtil.spawnLongDistanceParticles(
                                    serverWorld,
                                    ModParticles.EMBER_PARTICLE.get(),
                                    cx,
                                    cy,
                                    cz,
                                    1,
                                    (world.random.nextDouble() - 0.5) * 0.05,
                                    -0.05,
                                    (world.random.nextDouble() - 0.5) * 0.05
                            );
                        }

                        if (step % 3 == 0) {
                            Box hitBox = new Box(cx - 0.5, cy - 0.5, cz - 0.5, cx + 0.5, cy + 0.5, cz + 0.5);
                            for (LivingEntity target : serverWorld.getEntitiesByClass(LivingEntity.class, hitBox, e -> e != player)) {
                                target.setOnFireFor(5.0F);
                                target.damage(player.getDamageSources().onFire(), 4.0F);
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
