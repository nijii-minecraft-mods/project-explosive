package com.project.explosive.item;

import eu.pb4.polymer.core.api.item.PolymerBlockItem;
import eu.pb4.polymer.resourcepack.api.PolymerModelData;
import eu.pb4.polymer.resourcepack.api.PolymerResourcePackUtils;
import net.minecraft.block.Block;
import net.minecraft.item.Item;
import net.minecraft.item.ItemStack;
import net.minecraft.server.network.ServerPlayerEntity;
import net.minecraft.util.Identifier;
import org.jetbrains.annotations.Nullable;

public class ModBlockItem extends PolymerBlockItem {
    private final PolymerModelData modelData;

    public ModBlockItem(Block block, Settings settings, Item polymerItem, String modelName) {
        super(block, settings, polymerItem);
        this.modelData = PolymerResourcePackUtils.requestModel(polymerItem, Identifier.of("projectexplosive", "item/" + modelName));
    }

    public ModBlockItem(Block block, Settings settings, Item polymerItem) {
        super(block, settings, polymerItem);
        this.modelData = null;
    }

    @Override
    public int getPolymerCustomModelData(ItemStack itemStack, @Nullable ServerPlayerEntity player) {
        return this.modelData != null ? this.modelData.value() : -1;
    }
}
