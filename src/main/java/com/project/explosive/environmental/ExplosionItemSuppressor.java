package com.project.explosive.environmental;

import java.util.ArrayList;
import java.util.Iterator;
import java.util.List;
import net.fabricmc.fabric.api.event.lifecycle.v1.ServerEntityEvents;
import net.minecraft.entity.ItemEntity;
import net.minecraft.world.World;

public final class ExplosionItemSuppressor {
    private static final List<ExplosionItemSuppressor.Zone> ZONES = new ArrayList<>();

    private ExplosionItemSuppressor() {
    }

    public static void init() {
        ServerEntityEvents.ENTITY_LOAD.register((entity, world) -> {
            if (!world.isClient() && !ZONES.isEmpty() && entity instanceof ItemEntity item) {
                long gameTime = world.getTime();
                double ex = item.getX();
                double ey = item.getY();
                double ez = item.getZ();
                boolean suppress = false;
                Iterator<ExplosionItemSuppressor.Zone> it = ZONES.iterator();

                while (it.hasNext()) {
                    ExplosionItemSuppressor.Zone z = it.next();
                    if (gameTime > z.expiryGameTime) {
                        it.remove();
                    } else {
                        double dx = ex - z.x;
                        double dy = ey - z.y;
                        double dz = ez - z.z;
                        if (dx * dx + dy * dy + dz * dz <= z.radiusSq) {
                            suppress = true;
                        }
                    }
                }

                if (suppress) {
                    item.discard();
                }
            }
        });
    }

    public static void register(World level, double x, double y, double z, double radius, int durationTicks) {
        if (!level.isClient()) {
            ZONES.add(new ExplosionItemSuppressor.Zone(x, y, z, radius, level.getTime() + (long) durationTicks));
        }
    }

    private static final class Zone {
        final double x;
        final double y;
        final double z;
        final double radiusSq;
        final long expiryGameTime;

        Zone(double x, double y, double z, double radius, long expiry) {
            this.x = x;
            this.y = y;
            this.z = z;
            this.radiusSq = radius * radius;
            this.expiryGameTime = expiry;
        }
    }
}
