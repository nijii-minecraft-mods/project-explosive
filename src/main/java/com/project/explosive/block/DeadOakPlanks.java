package com.project.explosive.block;

import eu.pb4.polymer.core.api.block.PolymerBlock;
import net.minecraft.block.AbstractBlock;
import net.minecraft.block.Block;
import net.minecraft.block.BlockState;
import net.minecraft.block.Blocks;

public class DeadOakPlanks extends Block implements PolymerBlock {
    public DeadOakPlanks(AbstractBlock.Settings settings) {
        super(settings);
    }

    @Override
    public BlockState getPolymerBlockState(BlockState state) {
        return Blocks.OAK_PLANKS.getDefaultState();
    }
}
