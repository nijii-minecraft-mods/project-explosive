package com.project.explosive.item;

import com.project.explosive.entity.ModEntities;
import com.project.explosive.entity.NuclearBomb;
import com.project.explosive.sound.ModSounds;
import com.project.explosive.util.BombChunkTickets;
import com.project.explosive.util.BombTargeting;
import com.project.explosive.util.SoundUtil;
import eu.pb4.polymer.core.api.item.PolymerItem;
import eu.pb4.polymer.resourcepack.api.PolymerModelData;
import eu.pb4.polymer.resourcepack.api.PolymerResourcePackUtils;
import net.minecraft.component.DataComponentTypes;
import net.minecraft.component.type.NbtComponent;
import net.minecraft.entity.Entity;
import net.minecraft.entity.LivingEntity;
import net.minecraft.entity.player.PlayerEntity;
import net.minecraft.item.Item;
import net.minecraft.item.ItemStack;
import net.minecraft.item.ItemUsageContext;
import net.minecraft.item.Items;
import net.minecraft.server.network.ServerPlayerEntity;
import net.minecraft.server.world.ServerWorld;
import net.minecraft.sound.SoundCategory;
import net.minecraft.sound.SoundEvents;
import net.minecraft.text.Text;
import net.minecraft.util.ActionResult;
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

public class ICBMItem extends Item implements PolymerItem {
    public interface Launcher {
        Entity launch(ServerWorld world, BlockPos spawnPos, BlockPos targetPos, String explosionType, LivingEntity owner);
    }

    public static Launcher LAUNCHER = null;

    private static final String[] EXPLOSION_TYPES = new String[]{"small", "big", "nuclear"};
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
    public ActionResult useOnBlock(ItemUsageContext context) {
        return this.use(context.getWorld(), context.getPlayer(), context.getHand()).getResult();
    }

    @Override
    public TypedActionResult<ItemStack> use(World world, PlayerEntity player, Hand hand) {
        ItemStack itemStack = player.getStackInHand(hand);

        // Sneak + Right Click = Cycle Warhead Type
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
                player.getItemCooldownManager().set(this, 6);
            }
            return TypedActionResult.success(itemStack, world.isClient());
        }

        // Normal Right Click = Launch Missile towards crosshair
        if (!world.isClient() && world instanceof ServerWorld serverWorld) {
            Vec3d look = player.getRotationVector();
            BlockPos targetPos = BombTargeting.findGroundTarget(serverWorld, player, 384.0);
            if (targetPos == null) {
                HitResult hitResult = player.raycast(256.0, 0.0F, false);
                if (hitResult.getType() == HitResult.Type.BLOCK) {
                    targetPos = BlockPos.ofFloored(hitResult.getPos());
                } else {
                    Vec3d end = hitResult.getPos();
                    int topY = serverWorld.getTopY(Heightmap.Type.MOTION_BLOCKING, MathHelper.floor(end.x), MathHelper.floor(end.z));
                    if (topY <= serverWorld.getBottomY()) {
                        topY = 64;
                    }
                    targetPos = new BlockPos(MathHelper.floor(end.x), topY, MathHelper.floor(end.z));
                }
            }

            String explosionType = getExplosionType(itemStack);

            // Launch missile directly in front/above the player so the launch is completely visible
            Vec3d launchPos = player.getEyePos().add(look.multiply(2.0)).add(0.0, 0.5, 0.0);
            BlockPos spawnPos = BlockPos.ofFloored(launchPos);

            BombChunkTickets.forceTemporary(serverWorld, spawnPos, 2, 200);
            BombChunkTickets.forceTemporary(serverWorld, targetPos, 2, 400);

            if (LAUNCHER != null) {
                Entity icbm = LAUNCHER.launch(serverWorld, spawnPos, targetPos, explosionType, player);
                if (icbm != null) {
                    serverWorld.spawnEntity(icbm);
                }
            } else {
                SoundUtil.playAt(serverWorld, player.getX(), player.getY(), player.getZ(), ModSounds.ICBM_IGNITION, SoundCategory.BLOCKS, 2.0F, 1.0F);
                NuclearBomb fallback = new NuclearBomb(serverWorld, player);
                fallback.setPosition(spawnPos.getX() + 0.5, spawnPos.getY(), spawnPos.getZ() + 0.5);
                fallback.setVelocity(look.multiply(2.0));
                serverWorld.spawnEntity(fallback);
            }

            player.getItemCooldownManager().set(this, 50);

            player.sendMessage(Text.literal("🚀 ICBM launched! Target: (" + targetPos.getX() + ", " + targetPos.getY() + ", " + targetPos.getZ() + ")").formatted(Formatting.GREEN), true);

            if (!player.getAbilities().creativeMode) {
                itemStack.decrement(1);
            }
        }

        return TypedActionResult.success(itemStack, world.isClient());
    }

    private static String getExplosionType(ItemStack stack) {
        NbtComponent customData = stack.getOrDefault(DataComponentTypes.CUSTOM_DATA, NbtComponent.DEFAULT);
        if (customData.contains("ExplosionType")) {
            return customData.copyNbt().getString("ExplosionType");
        }
        return "nuclear";
    }

    private static String warheadLabel(String type) {
        return switch (type) {
            case "small" -> "Light (HE)";
            case "big" -> "Heavy (Bunker Buster)";
            case "nuclear" -> "Thermonuclear (Fat Man)";
            default -> type;
        };
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
