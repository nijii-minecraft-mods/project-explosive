package com.project.explosive.item;

import com.project.explosive.sound.ModSounds;
import com.project.explosive.util.BombChunkTickets;
import com.project.explosive.util.BombTargeting;
import eu.pb4.polymer.core.api.item.PolymerItem;
import eu.pb4.polymer.resourcepack.api.PolymerModelData;
import eu.pb4.polymer.resourcepack.api.PolymerResourcePackUtils;
import net.minecraft.component.DataComponentTypes;
import net.minecraft.component.type.NbtComponent;
import net.minecraft.entity.Entity;
import net.minecraft.entity.TntEntity;
import net.minecraft.entity.player.PlayerEntity;
import net.minecraft.item.Item;
import net.minecraft.item.ItemStack;
import net.minecraft.item.Items;
import net.minecraft.item.tooltip.TooltipType;
import net.minecraft.server.network.ServerPlayerEntity;
import net.minecraft.server.world.ServerWorld;
import net.minecraft.sound.SoundCategory;
import net.minecraft.sound.SoundEvents;
import net.minecraft.text.Text;
import net.minecraft.util.Formatting;
import net.minecraft.util.Hand;
import net.minecraft.util.Identifier;
import net.minecraft.util.TypedActionResult;
import net.minecraft.util.hit.HitResult;
import net.minecraft.util.math.BlockPos;
import net.minecraft.util.math.MathHelper;
import net.minecraft.util.math.Vec3d;
import net.minecraft.world.Heightmap;
import net.minecraft.world.World;
import org.jetbrains.annotations.Nullable;

import java.util.List;
import java.util.function.Consumer;

public class ICBMItem extends Item implements PolymerItem {
    private static final String[] EXPLOSION_TYPES = new String[]{"small", "big", "nuclear"};
    private static final String[] WARHEAD_LABELS = new String[]{"Small", "Big", "Nuclear"};

    @FunctionalInterface
    public interface ICBMLauncher {
        Entity launch(ServerWorld world, BlockPos spawnPos, BlockPos targetPos, String explosionType);
    }

    public static ICBMLauncher LAUNCHER = null;

    private final PolymerModelData modelData;
    private final PolymerModelData modelDataSmall;
    private final PolymerModelData modelDataBig;
    private final PolymerModelData modelDataNuclear;

    public ICBMItem(Settings settings) {
        super(settings);
        this.modelData = PolymerResourcePackUtils.requestModel(Items.FIREWORK_ROCKET, Identifier.of("projectexplosive", "item/icbm"));
        this.modelDataSmall = PolymerResourcePackUtils.requestModel(Items.FIREWORK_ROCKET, Identifier.of("projectexplosive", "item/icbm_small"));
        this.modelDataBig = PolymerResourcePackUtils.requestModel(Items.FIREWORK_ROCKET, Identifier.of("projectexplosive", "item/icbm_big"));
        this.modelDataNuclear = PolymerResourcePackUtils.requestModel(Items.FIREWORK_ROCKET, Identifier.of("projectexplosive", "item/icbm_nuclear"));
    }

    @Override
    public TypedActionResult<ItemStack> use(World world, PlayerEntity player, Hand hand) {
        ItemStack itemStack = player.getStackInHand(hand);

        if (player.isSneaking()) {
            if (!world.isClient()) {
                String currentType = getExplosionType(itemStack);
                int currentIndex = 0;
                for (int i = 0; i < EXPLOSION_TYPES.length; i++) {
                    if (EXPLOSION_TYPES[i].equals(currentType)) {
                        currentIndex = i;
                        break;
                    }
                }
                String nextType = EXPLOSION_TYPES[(currentIndex + 1) % EXPLOSION_TYPES.length];
                NbtComponent.set(DataComponentTypes.CUSTOM_DATA, itemStack, tag -> tag.putString("ExplosionType", nextType));
                world.playSound(null, player.getX(), player.getY(), player.getZ(), SoundEvents.UI_BUTTON_CLICK.value(), SoundCategory.PLAYERS, 0.8F, 1.2F);
                player.sendMessage(Text.literal("Warhead: " + warheadLabel(nextType)).formatted(Formatting.YELLOW), true);
            }
            return TypedActionResult.success(itemStack, world.isClient());
        }

        if (!world.isClient() && world instanceof ServerWorld serverWorld) {
            BlockPos targetPos = BombTargeting.findGroundTarget(serverWorld, player, 384.0);
            if (targetPos == null) {
                HitResult hitResult = player.raycast(256.0, 0.0F, false);
                targetPos = BlockPos.ofFloored(hitResult.getPos());
            }

            String explosionType = getExplosionType(itemStack);
            double angle = world.random.nextDouble() * 2.0 * Math.PI;
            double distance = 96.0 + world.random.nextDouble() * 64.0;
            double spawnX = player.getX() + Math.cos(angle) * distance;
            double spawnZ = player.getZ() + Math.sin(angle) * distance;
            int cx = (int) spawnX >> 4;
            int cz = (int) spawnZ >> 4;
            if (!serverWorld.isChunkLoaded(cx, cz)) {
                spawnX = player.getX() + Math.cos(angle) * 32.0;
                spawnZ = player.getZ() + Math.sin(angle) * 32.0;
            }
            BombChunkTickets.forceTemporary(serverWorld, new BlockPos((int) spawnX, 0, (int) spawnZ), 1, 200);

            int spawnY = serverWorld.getTopY(Heightmap.Type.MOTION_BLOCKING_NO_LEAVES, (int) spawnX, (int) spawnZ);
            if (spawnY <= serverWorld.getBottomY()) {
                spawnY = 64;
            }

            BlockPos spawnPos = new BlockPos((int) spawnX, spawnY + 1, (int) spawnZ);

            if (LAUNCHER != null) {
                Entity icbm = LAUNCHER.launch(serverWorld, spawnPos, targetPos, explosionType);
                if (icbm != null) {
                    serverWorld.spawnEntity(icbm);
                }
            } else {
                world.playSound(null, player.getX(), player.getY(), player.getZ(), ModSounds.ICBM_IGNITION, SoundCategory.BLOCKS, 2.0F, 1.0F);
                TntEntity fallbackTnt = new TntEntity(serverWorld, (double) targetPos.getX() + 0.5, (double) targetPos.getY() + 100.0, (double) targetPos.getZ() + 0.5, player);
                fallbackTnt.setVelocity(0.0, -1.0, 0.0);
                serverWorld.spawnEntity(fallbackTnt);
            }

            player.sendMessage(Text.literal("ICBM deployed to (" + targetPos.getX() + ", " + targetPos.getY() + ", " + targetPos.getZ() + ")").formatted(Formatting.RED), true);

            if (!player.getAbilities().creativeMode) {
                itemStack.decrement(1);
            }
        }

        return TypedActionResult.success(itemStack, world.isClient());
    }

    private static String getExplosionType(ItemStack stack) {
        NbtComponent customData = stack.getOrDefault(DataComponentTypes.CUSTOM_DATA, NbtComponent.DEFAULT);
        String type = customData.copyNbt().getString("ExplosionType");
        return type.isEmpty() ? "small" : type;
    }

    private static String warheadLabel(String type) {
        for (int i = 0; i < EXPLOSION_TYPES.length; i++) {
            if (EXPLOSION_TYPES[i].equals(type)) {
                return WARHEAD_LABELS[i];
            }
        }
        return "Small";
    }

    @Override
    public void appendTooltip(ItemStack stack, TooltipContext context, List<Text> tooltip, TooltipType type) {
        super.appendTooltip(stack, context, tooltip, type);
        String explosionType = getExplosionType(stack);
        tooltip.add(Text.literal("Type: " + warheadLabel(explosionType)).formatted(Formatting.GRAY));
        tooltip.add(Text.literal("Shift + click — cycle").formatted(Formatting.DARK_GRAY));
    }

    @Override
    public Item getPolymerItem(ItemStack itemStack, @Nullable ServerPlayerEntity player) {
        return Items.FIREWORK_ROCKET;
    }

    @Override
    public int getPolymerCustomModelData(ItemStack itemStack, @Nullable ServerPlayerEntity player) {
        String type = getExplosionType(itemStack);
        PolymerModelData selected = switch (type) {
            case "small" -> this.modelDataSmall != null ? this.modelDataSmall : this.modelData;
            case "big" -> this.modelDataBig != null ? this.modelDataBig : this.modelData;
            case "nuclear" -> this.modelDataNuclear != null ? this.modelDataNuclear : this.modelData;
            default -> this.modelData;
        };
        return selected != null ? selected.value() : -1;
    }
}
