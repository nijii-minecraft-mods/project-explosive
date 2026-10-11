package com.project.explosive.util;

import java.util.ArrayList;
import java.util.Iterator;
import java.util.List;
import net.fabricmc.fabric.api.event.lifecycle.v1.ServerLifecycleEvents;
import net.fabricmc.fabric.api.event.lifecycle.v1.ServerTickEvents;
import net.minecraft.server.MinecraftServer;
import net.minecraft.server.world.ServerWorld;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

public final class DelayedTaskScheduler {
    private static final Logger LOGGER = LoggerFactory.getLogger(DelayedTaskScheduler.class);
    private static final List<DelayedTaskScheduler.Task> TASKS = new ArrayList<>();

    private DelayedTaskScheduler() {
    }

    public static void init() {
        ServerTickEvents.END_SERVER_TICK.register(DelayedTaskScheduler::onServerTick);
        ServerLifecycleEvents.SERVER_STOPPING.register(DelayedTaskScheduler::onServerStopping);
    }

    public static void schedule(ServerWorld level, long delayTicks, Runnable action) {
        TASKS.add(new DelayedTaskScheduler.Task(level, level.getTime() + Math.max(0L, delayTicks), action));
    }

    private static void onServerTick(MinecraftServer server) {
        if (!TASKS.isEmpty()) {
            List<DelayedTaskScheduler.Task> due = new ArrayList<>();
            Iterator<DelayedTaskScheduler.Task> it = TASKS.iterator();

            while (it.hasNext()) {
                DelayedTaskScheduler.Task t = it.next();
                if (t.level().getServer() == null) {
                    it.remove();
                } else if (t.level().getTime() >= t.fireGameTime()) {
                    it.remove();
                    due.add(t);
                }
            }

            for (DelayedTaskScheduler.Task t : due) {
                try {
                    t.action().run();
                } catch (Throwable e) {
                    LOGGER.error("Scheduled explosive task threw", e);
                }
            }
        }
    }

    private static void onServerStopping(MinecraftServer server) {
        TASKS.clear();
    }

    private record Task(ServerWorld level, long fireGameTime, Runnable action) {
    }
}
