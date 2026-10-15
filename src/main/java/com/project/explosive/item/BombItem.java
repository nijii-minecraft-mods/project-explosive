package com.project.explosive.item;

import com.project.explosive.entity.BigBomb;
import com.project.explosive.entity.FireBomb;
import com.project.explosive.entity.ModEntities;
import com.project.explosive.entity.NuclearBomb;
import com.project.explosive.entity.SmallBomb;
import eu.pb4.polymer.core.api.item.PolymerItem;
import eu.pb4.polymer.resourcepack.api.PolymerModelData;
import eu.pb4.polymer.resourcepack.api.PolymerResourcePackUtils;
import net.minecraft.entity.Entity;
import net.minecraft.entity.EntityType;
import net.minecraft.entity.player.PlayerEntity;
import net.minecraft.item.Item;
import net.minecraft.item.ItemStack;
import net.minecraft.item.ItemUsageContext;
import net.minecraft.item.Items;
import net.minecraft.server.network.ServerPlayerEntity;
import net.minecraft.server.world.ServerWorld;
import net.minecraft.sound.SoundCategory;
import net.minecraft.sound.SoundEvents;
import net.minecraft.util.ActionResult;
import net.minecraft.util.Hand;
import net.minecraft.util.Identifier;
import net.minecraft.util.TypedActionResult;
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
        // Use non-block item (FIREWORK_STAR) to prevent vanilla client predicting TNT block placement
        this.modelData = PolymerResourcePackUtils.requestModel(Items.FIREWORK_STAR, Identifier.of("projectexplosive", "item/" + modelName));
    }

    @Override
    public ActionResult useOnBlock(ItemUsageContext context) {
        // Intercept right-clicks on blocks to throw the bomb forward instead of placing ghost blocks
        TypedActionResult<ItemStack> result = this.use(context.getWorld(), context.getPlayer(), context.getHand());
        return result.getResult();
    }

    @Override
    public TypedActionResult<ItemStack> use(World world, PlayerEntity player, Hand hand) {
        ItemStack itemStack = player.getStackInHand(hand);
        if (!world.isClient() && world instanceof ServerWorld serverWorld) {
            Vec3d look = player.getRotationVector();
            Vec3d spawnPosition = player.getEyePos().add(look.multiply(0.8));

            double throwSpeed = player.isSneaking() ? 0.65 : 1.4;
            Vec3d initialVelocity = look.multiply(throwSpeed).add(0.0, 0.12, 0.0);

            Entity entity = null;
            EntityType<?> type = this.entityTypeSupplier != null ? this.entityTypeSupplier.get() : null;

            if (type == ModEntities.SMALL_BOMB.get()) {
                SmallBomb b = new SmallBomb(serverWorld, player);
                b.setPosition(spawnPosition.x, spawnPosition.y, spawnPosition.z);
                b.setVelocity(initialVelocity);
                entity = b;
            } else if (type == ModEntities.BIG_BOMB.get()) {
                BigBomb b = new BigBomb(serverWorld, player);
                b.setPosition(spawnPosition.x, spawnPosition.y, spawnPosition.z);
                b.setVelocity(initialVelocity);
                entity = b;
            } else if (type == ModEntities.NUCLEAR_BOMB.get()) {
                NuclearBomb b = new NuclearBomb(serverWorld, player);
                b.setPosition(spawnPosition.x, spawnPosition.y, spawnPosition.z);
                b.setVelocity(initialVelocity);
                entity = b;
            } else if (type == ModEntities.FIRE_BOMB.get()) {
                FireBomb b = new FireBomb(serverWorld, player);
                b.setPosition(spawnPosition.x, spawnPosition.y, spawnPosition.z);
                b.setVelocity(initialVelocity);
                entity = b;
            } else if (this.entityFactory != null) {
                entity = this.entityFactory.create(serverWorld, player);
                if (entity != null) {
                    entity.setPosition(spawnPosition.x, spawnPosition.y, spawnPosition.z);
                    entity.setVelocity(initialVelocity);
                }
            }

            if (entity != null) {
                serverWorld.spawnEntity(entity);
            }

            world.playSound(
                    null,
                    player.getX(),
                    player.getY(),
                    player.getZ(),
                    SoundEvents.ENTITY_SNOWBALL_THROW,
                    SoundCategory.PLAYERS,
                    0.9F,
                    0.6F
            );

            if (!player.getAbilities().creativeMode) {
                itemStack.decrement(1);
            }

            player.getItemCooldownManager().set(this, 12);
        }

        return TypedActionResult.success(itemStack, world.isClient());
    }

    @Override
    public Item getPolymerItem(ItemStack itemStack, @Nullable ServerPlayerEntity player) {
        return Items.FIREWORK_STAR;
    }

    @Override
    public int getPolymerCustomModelData(ItemStack itemStack, @Nullable ServerPlayerEntity player) {
        return this.modelData != null ? this.modelData.value() : -1;
    }
}
