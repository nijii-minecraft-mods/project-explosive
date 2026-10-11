package com.project.explosive.block;

import eu.pb4.polymer.core.api.block.PolymerBlock;
import net.minecraft.block.AbstractBlock;
import net.minecraft.block.Block;
import net.minecraft.block.BlockState;
import net.minecraft.block.Blocks;

public class DeadDirt extends Block implements PolymerBlock {
    public DeadDirt(AbstractBlock.Settings settings) {
        super(settings);
    }

    @Override
    public BlockState getPolymerBlockState(BlockState state) {
        return Blocks.COARSE_DIRT.getDefaultState();
    }
}
