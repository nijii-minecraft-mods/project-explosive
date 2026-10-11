package com.project.explosive.block;

import eu.pb4.polymer.core.api.block.PolymerBlock;
import net.minecraft.block.Block;
import net.minecraft.block.BlockState;
import net.minecraft.server.network.ServerPlayerEntity;
import org.jetbrains.annotations.Nullable;

public class ModOreBlock extends Block implements PolymerBlock {
    private final Block polymerBlock;

    public ModOreBlock(Settings settings, Block polymerBlock) {
        super(settings);
        this.polymerBlock = polymerBlock;
    }

    @Override
    public Block getPolymerReplacement(@Nullable ServerPlayerEntity player) {
        return this.polymerBlock;
    }

    @Override
    public BlockState getPolymerBlockState(BlockState state) {
        return this.polymerBlock.getDefaultState();
    }

    @Override
    public BlockState getPolymerBlockState(BlockState state, @Nullable ServerPlayerEntity player) {
        return this.polymerBlock.getDefaultState();
    }
}
