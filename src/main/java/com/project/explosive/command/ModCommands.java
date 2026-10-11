package com.project.explosive.command;

import com.mojang.brigadier.arguments.DoubleArgumentType;
import com.mojang.brigadier.arguments.IntegerArgumentType;
import com.project.explosive.entity.BigBomb;
import com.project.explosive.entity.ModEntities;
import com.project.explosive.entity.MultiBlockEntity;
import com.project.explosive.entity.NuclearBomb;
import com.project.explosive.entity.SmallBomb;
import com.project.explosive.environmental.RadiationManager;
import com.project.explosive.util.BlockClusterUtil;
import com.project.explosive.util.BombChunkTickets;
import com.project.explosive.util.FirePropagationManager;
import net.fabricmc.fabric.api.command.v2.CommandRegistrationCallback;
import net.minecraft.command.argument.BlockPosArgumentType;
import net.minecraft.command.argument.Vec3ArgumentType;
import net.minecraft.entity.Entity;
import net.minecraft.server.command.CommandManager;
import net.minecraft.server.command.ServerCommandSource;
import net.minecraft.server.network.ServerPlayerEntity;
import net.minecraft.server.world.ServerWorld;
import net.minecraft.text.Text;
import net.minecraft.util.Formatting;
import net.minecraft.util.math.BlockPos;
import net.minecraft.util.math.MathHelper;
import net.minecraft.util.math.Vec3d;

public class ModCommands {
    public static void init() {
        CommandRegistrationCallback.EVENT.register((dispatcher, registryAccess, environment) -> {
            // /custombomb <scale> [pos]
            dispatcher.register(
                    CommandManager.literal("custombomb")
                            .requires(source -> source.hasPermissionLevel(2))
                            .then(CommandManager.argument("scale", IntegerArgumentType.integer(1, 10))
                                    .executes(ctx -> {
                                        Vec3d p = ctx.getSource().getPosition();
                                        return runCustomBomb(ctx.getSource(), IntegerArgumentType.getInteger(ctx, "scale"), p.x, p.y, p.z);
                                    })
                                    .then(CommandManager.argument("pos", Vec3ArgumentType.vec3())
                                            .executes(ctx -> {
                                                Vec3d p = Vec3ArgumentType.getVec3(ctx, "pos");
                                                return runCustomBomb(ctx.getSource(), IntegerArgumentType.getInteger(ctx, "scale"), p.x, p.y, p.z);
                                            })
                                    )
                            )
            );

            // /radiation [clear|sources]
            dispatcher.register(
                    CommandManager.literal("radiation")
                            .executes(ctx -> {
                                ServerPlayerEntity player = ctx.getSource().getPlayerOrThrow();
                                double acuteDose = RadiationManager.getPlayerRadiationData(player).getAcuteDose();
                                Formatting color = acuteDose < 0.5 ? Formatting.GREEN :
                                        (acuteDose < 2.0 ? Formatting.YELLOW : (acuteDose < 6.0 ? Formatting.RED : Formatting.DARK_RED));
                                ctx.getSource().sendFeedback(() -> Text.literal(String.format("Current Radiation Dose: %.2f Sv", acuteDose)).formatted(color), false);
                                return (int) acuteDose;
                            })
                            .then(CommandManager.literal("clear")
                                    .requires(source -> source.hasPermissionLevel(2))
                                    .executes(ctx -> {
                                        RadiationManager.clearAllRadiation();
                                        ctx.getSource().sendFeedback(() -> Text.literal("Cleared all radiation sources and player doses").formatted(Formatting.GREEN), true);
                                        return 1;
                                    })
                            )
                            .then(CommandManager.literal("sources")
                                    .requires(source -> source.hasPermissionLevel(2))
                                    .executes(ctx -> {
                                        int count = RadiationManager.getActiveSourceCount();
                                        ctx.getSource().sendFeedback(() -> Text.literal("Active radiation sources: " + count).formatted(Formatting.YELLOW), false);
                                        return count;
                                    })
                            )
            );

            // /ignitefire [pos] & /stopfire [pos] [radius]
            dispatcher.register(
                    CommandManager.literal("ignitefire")
                            .requires(source -> source.hasPermissionLevel(2))
                            .executes(ctx -> {
                                Vec3d p = ctx.getSource().getPosition();
                                return executeIgnite(ctx.getSource(), p.x, p.y, p.z);
                            })
                            .then(CommandManager.argument("pos", Vec3ArgumentType.vec3())
                                    .executes(ctx -> {
                                        Vec3d p = Vec3ArgumentType.getVec3(ctx, "pos");
                                        return executeIgnite(ctx.getSource(), p.x, p.y, p.z);
                                    })
                            )
            );

            dispatcher.register(
                    CommandManager.literal("stopfire")
                            .requires(source -> source.hasPermissionLevel(2))
                            .executes(ctx -> {
                                int count = FirePropagationManager.getInstance().getActiveSystemCount();
                                FirePropagationManager.getInstance().stopAllFirePropagations();
                                ctx.getSource().sendFeedback(() -> Text.literal(String.format("Stopped %d active fire propagation system(s)", count)).formatted(Formatting.AQUA), true);
                                return 1;
                            })
                            .then(CommandManager.argument("pos", Vec3ArgumentType.vec3())
                                    .then(CommandManager.argument("radius", DoubleArgumentType.doubleArg(1.0, 100.0))
                                            .executes(ctx -> {
                                                Vec3d pos = Vec3ArgumentType.getVec3(ctx, "pos");
                                                double radius = DoubleArgumentType.getDouble(ctx, "radius");
                                                int extinguished = FirePropagationManager.getInstance().extinguishFiresInRadius(ctx.getSource().getWorld(), pos.x, pos.y, pos.z, radius);
                                                ctx.getSource().sendFeedback(() -> Text.literal(String.format("Extinguished %d fire cell(s) within %.1f blocks", extinguished, radius)).formatted(Formatting.AQUA), true);
                                                return 1;
                                            })
                                    )
                            )
            );

            // /testmultiblock <pos> [maxBlocks]
            dispatcher.register(
                    CommandManager.literal("testmultiblock")
                            .requires(source -> source.hasPermissionLevel(2))
                            .then(CommandManager.argument("pos", BlockPosArgumentType.blockPos())
                                    .then(CommandManager.argument("maxBlocks", IntegerArgumentType.integer(1, 100))
                                            .executes(ctx -> {
                                                BlockPos pos = BlockPosArgumentType.getLoadedBlockPos(ctx, "pos");
                                                int maxBlocks = IntegerArgumentType.getInteger(ctx, "maxBlocks");
                                                ServerWorld world = ctx.getSource().getWorld();
                                                BlockClusterUtil.ClusterData clusterData = BlockClusterUtil.collectBlockCluster(world, pos, maxBlocks);
                                                if (clusterData != null && !clusterData.getBlocks().isEmpty()) {
                                                    for (BlockPos relativePos : clusterData.getBlocks().keySet()) {
                                                        BlockPos realPos = pos.add(relativePos);
                                                        world.removeBlock(realPos, false);
                                                    }
                                                    MultiBlockEntity multiBlockEntity = new MultiBlockEntity(ModEntities.RAW_MULTI_BLOCK, world);
                                                    multiBlockEntity.setPosition((double) pos.getX() + 0.5, (double) pos.getY() + 0.5, (double) pos.getZ() + 0.5);
                                                    multiBlockEntity.setBlocks(clusterData.getBlocks(), clusterData.getCenterOffset());
                                                    world.spawnEntity(multiBlockEntity);
                                                    ctx.getSource().sendFeedback(() -> Text.literal("Created MultiBlockEntity with " + clusterData.getBlocks().size() + " blocks").formatted(Formatting.GREEN), true);
                                                    return 1;
                                                } else {
                                                    ctx.getSource().sendError(Text.literal("No block cluster found at position"));
                                                    return 0;
                                                }
                                            })
                                    )
                            )
            );
        });
    }

    private static int runCustomBomb(ServerCommandSource source, int scale, double x, double y, double z) {
        ServerWorld level = source.getWorld();
        int s = MathHelper.clamp(scale, 1, 10);
        double spawnY = y + 30.0;
        BombChunkTickets.forceTemporary(level, BlockPos.ofFloored(x, y, z), 2, 600);
        Entity bomb;
        if (s <= 4) {
            SmallBomb b = new SmallBomb(level, x, spawnY, z, false);
            b.setBlastScale((float) s * 0.13333334F);
            bomb = b;
        } else if (s <= 7) {
            BigBomb b = new BigBomb(level, x, spawnY, z, false);
            b.setBlastScale((float) s * 0.14F);
            bomb = b;
        } else {
            NuclearBomb b = new NuclearBomb(level, x, spawnY, z, false);
            b.setBlastScale((float) s * 0.1F);
            bomb = b;
        }

        level.spawnEntity(bomb);
        source.sendFeedback(() -> Text.literal(String.format("Custom bomb (scale %d) dropped at (%.1f, %.1f, %.1f)", scale, x, y, z)).formatted(Formatting.GOLD), true);
        return 1;
    }

    private static int executeIgnite(ServerCommandSource source, double x, double y, double z) {
        ServerWorld serverLevel = source.getWorld();
        FirePropagationManager.getInstance().startFirePropagation(serverLevel, x, y, z);
        source.sendFeedback(() -> Text.literal(String.format("Fire propagation started at (%.1f, %.1f, %.1f)", x, y, z)).formatted(Formatting.GOLD), true);
        return 1;
    }
}
