package com.project.explosive.item;

import com.project.explosive.util.BombChunkTickets;
import com.project.explosive.util.BombTargeting;
import eu.pb4.polymer.core.api.item.PolymerItem;
import eu.pb4.polymer.resourcepack.api.PolymerModelData;
import eu.pb4.polymer.resourcepack.api.PolymerResourcePackUtils;
import net.minecraft.entity.Entity;
import net.minecraft.entity.EntityType;
import net.minecraft.entity.TntEntity;
import net.minecraft.entity.player.PlayerEntity;
import net.minecraft.item.Item;
import net.minecraft.item.ItemStack;
import net.minecraft.item.Items;
import net.minecraft.server.network.ServerPlayerEntity;
import net.minecraft.server.world.ServerWorld;
import net.minecraft.util.Hand;
import net.minecraft.util.Identifier;
import net.minecraft.util.TypedActionResult;
import net.minecraft.util.hit.HitResult;
import net.minecraft.util.math.BlockPos;
import net.minecraft.util.math.MathHelper;
import net.minecraft.util.math.Vec3d;
import net.minecraft.world.World;
import org.jetbrains.annotations.Nullable;

import java.util.function.Supplier;

public class BombItem extends Item implements PolymerItem {
    private final Supplier<EntityType<?>> entityTypeSupplier;
    private final BombEntityFactory entityFactory;
    private final PolymerModelData modelData;

    public BombItem(Supplier<EntityType<?>> entityTypeSupplier, String modelName, Settings settings) {
        this(entityTypeSupplier, null, modelName, settings);
    }

    public BombItem(Supplier<EntityType<?>> entityTypeSupplier, BombEntityFactory entityFactory, String modelName, Settings settings) {
        super(settings);
        this.entityTypeSupplier = entityTypeSupplier;
        this.entityFactory = entityFactory;
        this.modelData = PolymerResourcePackUtils.requestModel(Items.TNT, Identifier.of("projectexplosive", "item/" + modelName));
    }

    @Override
    public TypedActionResult<ItemStack> use(World world, PlayerEntity player, Hand hand) {
        ItemStack itemStack = player.getStackInHand(hand);
        if (!world.isClient() && world instanceof ServerWorld serverWorld) {
            Vec3d spawnPosition;
            Vec3d initialVelocity;

            if (player.isSneaking()) {
                // Throw forward
                Vec3d look = player.getRotationVector();
                spawnPosition = player.getEyePos().add(look.multiply(1.5));
                initialVelocity = look.multiply(1.2);
            } else {
                // High-altitude drop
                BlockPos groundHit = BombTargeting.findGroundTarget(serverWorld, player, 256.0);
                if (groundHit != null) {
                    spawnPosition = groundHit.toCenterPos().add(0.0, 150.0, 0.0);
                } else {
                    HitResult hitResult = player.raycast(128.0, 0.0F, false);
                    spawnPosition = hitResult.getPos().add(0.0, 150.0, 0.0);
                }
                initialVelocity = new Vec3d(0.0, -0.6, 0.0);

                int scx = MathHelper.floor(spawnPosition.x) >> 4;
                int scz = MathHelper.floor(spawnPosition.z) >> 4;
                if (!serverWorld.isChunkLoaded(scx, scz)) {
                    serverWorld.getChunk(scx, scz);
                }
                BombChunkTickets.forceTemporary(serverWorld, BlockPos.ofFloored(spawnPosition.x, 0, spawnPosition.z), 1, 200);
            }

            Entity entity = null;
            if (this.entityFactory != null) {
                entity = this.entityFactory.create(serverWorld, player);
            } else if (this.entityTypeSupplier != null && this.entityTypeSupplier.get() != null) {
                EntityType<?> type = this.entityTypeSupplier.get();
                entity = type.create(serverWorld);
            }

            if (entity != null) {
                entity.setPosition(spawnPosition.x, spawnPosition.y, spawnPosition.z);
                entity.setVelocity(initialVelocity);
                serverWorld.spawnEntity(entity);
            } else {
                TntEntity tnt = new TntEntity(serverWorld, spawnPosition.x, spawnPosition.y, spawnPosition.z, player);
                tnt.setVelocity(initialVelocity);
                serverWorld.spawnEntity(tnt);
            }

            if (!player.getAbilities().creativeMode) {
                itemStack.decrement(1);
            }
        }

        return TypedActionResult.success(itemStack, world.isClient());
    }

    @Override
    public Item getPolymerItem(ItemStack itemStack, @Nullable ServerPlayerEntity player) {
        return Items.TNT;
    }

    @Override
    public int getPolymerCustomModelData(ItemStack itemStack, @Nullable ServerPlayerEntity player) {
        return this.modelData != null ? this.modelData.value() : -1;
    }
}
