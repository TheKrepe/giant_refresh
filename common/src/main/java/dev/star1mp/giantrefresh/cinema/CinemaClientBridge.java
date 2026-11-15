package dev.star1mp.giantrefresh.cinema;

import java.util.function.BiFunction;
import net.minecraft.world.phys.Vec3;

/** Lets common code ask the client for an entity's interpolated position (set by the client). */
public final class CinemaClientBridge {
    public static volatile BiFunction<Integer, Double, Vec3> resolver;

    private CinemaClientBridge() {}

    static Vec3 entityPos(int id, double dy, Vec3 fallback) {
        BiFunction<Integer, Double, Vec3> r = resolver;
        Vec3 v = r != null ? r.apply(id, dy) : null;
        return v != null ? v : fallback;
    }
}
