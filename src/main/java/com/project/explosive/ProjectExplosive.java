package com.project.explosive;

import com.project.explosive.block.ModBlocks;
import com.project.explosive.entity.ModEntities;
import com.project.explosive.command.ModCommands;
import com.project.explosive.environmental.EnvironmentalTransformer;
import com.project.explosive.environmental.ExplosionItemSuppressor;
import com.project.explosive.environmental.RadiationManager;
import com.project.explosive.network.RadiationSyncPacket;
import com.project.explosive.item.ModItems;
import com.project.explosive.particle.ModParticles;
import com.project.explosive.sound.ModSounds;
import com.project.explosive.util.BombChunkTickets;
import com.project.explosive.util.DelayedTaskScheduler;
import com.project.explosive.util.FirePropagationManager;
import eu.pb4.polymer.resourcepack.api.PolymerResourcePackUtils;
import net.fabricmc.api.ModInitializer;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

public class ProjectExplosive implements ModInitializer {
    public static final String MOD_ID = "projectexplosive";
    public static final Logger LOGGER = LoggerFactory.getLogger(MOD_ID);

    @Override
    public void onInitialize() {
        LOGGER.info("Initializing Project: Explosive Fabric mod");

        PolymerResourcePackUtils.addModAssets(MOD_ID);
        PolymerResourcePackUtils.markAsRequired();

        ModSounds.init();
        ModParticles.init();
        ModBlocks.init();
        ModItems.init();
        ModEntities.init();
        RadiationSyncPacket.init();

        DelayedTaskScheduler.init();
        BombChunkTickets.init();
        FirePropagationManager.init();
        RadiationManager.init();
        ExplosionItemSuppressor.init();
        EnvironmentalTransformer.init();
        ModCommands.init();
    }
}
