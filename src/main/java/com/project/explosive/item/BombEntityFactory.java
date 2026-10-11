package com.project.explosive.item;

import net.minecraft.entity.Entity;
import net.minecraft.entity.player.PlayerEntity;
import net.minecraft.world.World;

@FunctionalInterface
public interface BombEntityFactory {
    Entity create(World world, PlayerEntity player);
}
