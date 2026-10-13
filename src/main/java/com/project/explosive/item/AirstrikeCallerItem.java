package com.project.explosive.item;

import com.project.explosive.entity.ModEntities;
import com.project.explosive.util.BombChunkTickets;
import com.project.explosive.util.BombTargeting;
import com.project.explosive.util.DelayedTaskScheduler;
import com.project.explosive.util.SoundUtil;
import eu.pb4.polymer.core.api.item.PolymerItem;
import eu.pb4.polymer.resourcepack.api.PolymerModelData;
import eu.pb4.polymer.resourcepack.api.PolymerResourcePackUtils;
import net.minecraft.entity.Entity;
import net.minecraft.entity.TntEntity;
import net.minecraft.entity.player.PlayerEntity;
import net.minecraft.item.Item;
import net.minecraft.item.ItemStack;
import net.minecraft.item.Items;
import net.minecraft.server.network.ServerPlayerEntity;
import net.minecraft.server.world.ServerWorld;
import net.minecraft.sound.SoundCategory;
import net.minecraft.sound.SoundEvents;
import net.minecraft.text.Text;
import net.minecraft.util.Formatting;
import net.minecraft.util.Hand;
import net.minecraft.util.Identifier;
import net.minecraft.util.TypedActionResult;
import net.minecraft.util.hit.BlockHitResult;
import net.minecraft.util.hit.HitResult;
import net.minecraft.util.math.BlockPos;
import net.minecraft.util.math.MathHelper;
import net.minecraft.world.Heightmap;
import net.minecraft.world.World;
import org.jetbrains.annotations.Nullable;

public class AirstrikeCallerItem extends Item implements PolymerItem {
    private final PolymerModelData modelData;

    public AirstrikeCallerItem(Settings settings) {
        super(settings);
        this.modelData = PolymerResourcePackUtils.requestModel(Items.SPYGLASS, Identifier.of("projectexplosive", "item/airstrike_caller"));
    }

    @Override
    public TypedActionResult<ItemStack> use(World world, PlayerEntity player, Hand hand) {
        ItemStack itemStack = player.getStackInHand(hand);

        if (!world.isClient() && world instanceof ServerWorld serverWorld) {
            BlockPos ground = BombTargeting.findGroundTarget(serverWorld, player, 256.0);
            BlockPos targetPos;
            if (ground != null) {
                targetPos = ground.up();
            } else {
                HitResult hitResult = player.raycast(128.0, 0.0F, false);
                targetPos = hitResult.getType() == HitResult.Type.BLOCK
                        ? ((BlockHitResult) hitResult).getBlockPos().up()
                        : player.getBlockPos();
            }

            // Target lock sound
            SoundUtil.playAt(
                    serverWorld,
                    player.getX(),
                    player.getY(),
                    player.getZ(),
                    SoundEvents.BLOCK_NOTE_BLOCK_BELL.value(),
                    SoundCategory.PLAYERS,
                    1.2F,
                    2.0F
            );
            SoundUtil.playAt(
                    serverWorld,
                    player.getX(),
                    player.getY(),
                    player.getZ(),
                    SoundEvents.BLOCK_BEACON_ACTIVATE,
                    SoundCategory.PLAYERS,
                    0.8F,
                    1.5F
            );

            long delay = 0L;
            for (int i = 0; i < 5; i++) {
                final BlockPos tp = targetPos;
                DelayedTaskScheduler.schedule(serverWorld, delay, () -> spawnBombAtPosition(serverWorld, tp, player));
                delay += (long) (20 + serverWorld.getRandom().nextInt(41));
            }

            if (!player.getAbilities().creativeMode) {
                itemStack.decrement(1);
            }

            player.sendMessage(Text.literal("Airstrike inbound! Coordinates: (" + targetPos.getX() + ", " + targetPos.getZ() + ")").formatted(Formatting.RED), true);
        }

        return TypedActionResult.success(itemStack, world.isClient());
    }

    private static void spawnBombAtPosition(ServerWorld serverWorld, BlockPos targetPos, PlayerEntity player) {
        double offsetX = (serverWorld.getRandom().nextDouble() - 0.5) * 80.0;
        double offsetZ = (serverWorld.getRandom().nextDouble() - 0.5) * 80.0;
        int gx = MathHelper.floor((double) targetPos.getX() + offsetX);
        int gz = MathHelper.floor((double) targetPos.getZ() + offsetZ);
        int cx = gx >> 4;
        int cz = gz >> 4;
        serverWorld.getChunk(cx, cz);
        BombChunkTickets.forceTemporary(serverWorld, new BlockPos(gx, 0, gz), 1, 200);

        int groundY = serverWorld.getTopY(Heightmap.Type.MOTION_BLOCKING_NO_LEAVES, gx, gz);
        if (groundY <= serverWorld.getBottomY()) {
            groundY = 64;
        }

        double bombY = (double) (groundY + 200);
        double spawnX = (double) targetPos.getX() + offsetX + 0.5;
        double spawnZ = (double) targetPos.getZ() + offsetZ + 0.5;

        if (ModEntities.SMALL_BOMB != null && ModEntities.SMALL_BOMB.get() != null) {
            Entity bomb = ModEntities.SMALL_BOMB.get().create(serverWorld);
            if (bomb != null) {
                bomb.setPosition(spawnX, bombY, spawnZ);
                bomb.setVelocity(0.0, -0.5, 0.0);
                serverWorld.spawnEntity(bomb);
                return;
            }
        }

        TntEntity tnt = new TntEntity(serverWorld, spawnX, bombY, spawnZ, player);
        tnt.setVelocity(0.0, -0.5, 0.0);
        serverWorld.spawnEntity(tnt);
    }

    @Override
    public Item getPolymerItem(ItemStack itemStack, @Nullable ServerPlayerEntity player) {
        return Items.SPYGLASS;
    }

    @Override
    public int getPolymerCustomModelData(ItemStack itemStack, @Nullable ServerPlayerEntity player) {
        return this.modelData != null ? this.modelData.value() : -1;
    }
}
