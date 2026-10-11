package com.project.explosive.block;

import eu.pb4.polymer.core.api.block.PolymerBlock;
import net.minecraft.block.AbstractBlock;
import net.minecraft.block.BlockState;
import net.minecraft.block.Blocks;
import net.minecraft.block.PillarBlock;
import net.minecraft.util.math.Direction;

public class DeadOakLog extends PillarBlock implements PolymerBlock {
    public DeadOakLog(AbstractBlock.Settings settings) {
        super(settings);
        this.setDefaultState(this.stateManager.getDefaultState().with(AXIS, Direction.Axis.Y));
    }

    @Override
    public BlockState getPolymerBlockState(BlockState state) {
        return Blocks.STRIPPED_OAK_LOG.getDefaultState().with(AXIS, state.get(AXIS));
    }
}
