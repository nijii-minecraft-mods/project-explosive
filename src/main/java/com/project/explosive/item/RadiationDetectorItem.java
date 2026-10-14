package com.project.explosive.item;

import com.project.explosive.environmental.RadiationManager;
import com.project.explosive.sound.ModSounds;
import com.project.explosive.util.SoundUtil;
import eu.pb4.polymer.core.api.item.PolymerItem;
import eu.pb4.polymer.resourcepack.api.PolymerModelData;
import eu.pb4.polymer.resourcepack.api.PolymerResourcePackUtils;
import java.util.UUID;
import net.minecraft.entity.Entity;
import net.minecraft.item.Item;
import net.minecraft.item.ItemStack;
import net.minecraft.item.Items;
import net.minecraft.server.network.ServerPlayerEntity;
import net.minecraft.sound.SoundCategory;
import net.minecraft.sound.SoundEvent;
import net.minecraft.text.Text;
import net.minecraft.util.Formatting;
import net.minecraft.util.Identifier;
import net.minecraft.world.World;
import org.jetbrains.annotations.Nullable;

public class RadiationDetectorItem extends Item implements PolymerItem {
    private final PolymerModelData modelData;

    public RadiationDetectorItem(Settings settings) {
        super(settings);
        this.modelData = PolymerResourcePackUtils.requestModel(Items.COMPASS, Identifier.of("projectexplosive", "item/radiation_detector"));
    }

    private static final java.util.Map<UUID, Double> LAST_MEASURED_RAD = new java.util.concurrent.ConcurrentHashMap<>();

    @Override
    public void inventoryTick(ItemStack stack, World world, Entity entity, int slot, boolean selected) {
        if (!world.isClient() && entity instanceof ServerPlayerEntity player) {
            boolean isHeld = player.getMainHandStack() == stack || player.getOffHandStack() == stack;
            if (!isHeld) {
                return;
            }

            double totalRadiation;
            if (player.age % 4 == 0 || !LAST_MEASURED_RAD.containsKey(player.getUuid())) {
                double ambientRad = RadiationManager.getRadiationAtPosition(world, player.getBlockPos());
                double ashRad = RadiationManager.getRadiationFromNearbyAsh(world, player.getBlockPos(), 5);
                totalRadiation = ambientRad + ashRad;
                LAST_MEASURED_RAD.put(player.getUuid(), totalRadiation);

                double msvh = totalRadiation * 10.0;
                Formatting color = Formatting.GREEN;
                if (msvh >= 50.0) {
                    color = Formatting.DARK_RED;
                } else if (msvh >= 20.0) {
                    color = Formatting.RED;
                } else if (msvh >= 5.0) {
                    color = Formatting.GOLD;
                } else if (msvh >= 0.5) {
                    color = Formatting.YELLOW;
                }

                String display = String.format("☢ Radiation: %.1f mSv/h", msvh);
                player.sendMessage(Text.literal(display).formatted(color), true);
            } else {
                totalRadiation = LAST_MEASURED_RAD.getOrDefault(player.getUuid(), 0.0);
            }

            // Geiger clicks
            double clickChance = 0.005; // Base background click chance
            if (totalRadiation > 0.01) {
                clickChance = Math.min(1.0, 0.05 + totalRadiation * 0.15);
            }

            if (world.getRandom().nextDouble() < clickChance) {
                SoundEvent clickSound;
                int r = world.getRandom().nextInt(3);
                if (r == 0) {
                    clickSound = ModSounds.GEIGER_CLICK_1;
                } else if (r == 1) {
                    clickSound = ModSounds.GEIGER_CLICK_2;
                } else {
                    clickSound = ModSounds.GEIGER_CLICK_3;
                }

                SoundUtil.playNotify(player, clickSound, SoundCategory.PLAYERS, 0.7F, 0.9F + world.getRandom().nextFloat() * 0.2F);
            }
        }
    }

    @Override
    public Item getPolymerItem(ItemStack itemStack, @Nullable ServerPlayerEntity player) {
        return Items.COMPASS;
    }

    @Override
    public int getPolymerCustomModelData(ItemStack itemStack, @Nullable ServerPlayerEntity player) {
        return this.modelData != null ? this.modelData.value() : -1;
    }
}
