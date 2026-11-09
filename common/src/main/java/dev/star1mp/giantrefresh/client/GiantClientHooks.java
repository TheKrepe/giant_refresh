package dev.star1mp.giantrefresh.client;

import dev.star1mp.giantrefresh.config.GRConfig;
import net.minecraft.client.Minecraft;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.entity.player.Player;

/**
 * Client-only reactions to Giant events. Only ever called from client-side code paths,
 * so the dedicated server never loads this class.
 */
public final class GiantClientHooks {
    /** 0..1 — decays over time; the camera offset grows with its square. */
    private static float trauma;
    private static long lastNanos = System.nanoTime();

    /** CI render test: {@code -Dgiantrefresh.clientsmoketest=true}. */
    public static final boolean CLIENT_TEST = Boolean.getBoolean("giantrefresh.clientsmoketest");
    private static long worldStart;
    private static int shots;
    private static boolean stopped;
    private static int cameraSet;

    private GiantClientHooks() {}

    public static void onImpact(Entity source, float strength) {
        GRConfig cfg = GRConfig.get();
        if (!cfg.screenShake || cfg.screenShakeStrength <= 0) {
            return;
        }
        Player player = Minecraft.getInstance().player;
        if (player == null || player.getVehicle() == source) {
            return;
        }
        double dist = Math.sqrt(player.distanceToSqr(source));
        double falloff = 1.0 - dist / 42.0;
        if (falloff <= 0) {
            return;
        }
        trauma = Math.min(1.0F, trauma + (float) (strength * falloff * falloff));
    }

    /** Returns {yaw, pitch} offsets in degrees for this frame and decays the shake. */
    public static float[] cameraShake() {
        if (CLIENT_TEST) {
            clientTestFrame();
        }
        long now = System.nanoTime();
        float dt = Math.min(0.1F, (now - lastNanos) / 1.0E9F);
        lastNanos = now;
        if (trauma <= 0.0F) {
            return null;
        }
        trauma = Math.max(0.0F, trauma - dt * 1.4F);
        float amount = trauma * trauma * (float) GRConfig.get().screenShakeStrength;
        double t = now / 1.0E9;
        float yaw = (float) (Math.sin(t * 37.0) * 0.6 + Math.sin(t * 23.0 + 1.3) * 0.4) * 2.2F * amount;
        float pitch = (float) (Math.sin(t * 41.0 + 0.7) * 0.6 + Math.sin(t * 29.0 + 2.1) * 0.4) * 2.6F * amount;
        return new float[] {yaw, pitch};
    }

    private static void clientTestFrame() {
        Minecraft mc = Minecraft.getInstance();
        if (stopped || mc.player == null || mc.level == null) {
            return;
        }
        long now = System.nanoTime();
        if (worldStart == 0) {
            worldStart = now;
        }
        double seconds = (now - worldStart) / 1.0E9;
        double[] at = {35.0, 40.0, 45.0, 50.0, 55.0, 60.0, 65.0, 70.0};
        if (cameraSet < at.length && seconds > at[cameraSet] - 3.0) {
            final int shot = cameraSet++;
            var server = mc.getSingleplayerServer();
            if (server != null) {
                server.execute(() -> dev.star1mp.giantrefresh.entity.GiantSmokeTest.showcaseCamera(server.overworld(), shot));
            }
        }
        if (shots < at.length && seconds > at[shots]) {
            for (net.minecraft.world.entity.Entity e : mc.level.entitiesForRendering()) {
                if (e instanceof dev.star1mp.giantrefresh.entity.GiantEntity g) {
                    for (net.minecraft.world.entity.Entity p : g.getPassengers()) {
                        dev.star1mp.giantrefresh.GiantRefresh.LOGGER.info("[CLIENTTEST] client rider {} dy={} sleep={} pose={} giantY={} riderY={}",
                                p.getType().getDescriptionId(), p.getY() - g.getY(), g.getSleepAmount(1.0F), g.getPose(), g.getY(), p.getY());
                    }
                }
            }
            dev.star1mp.giantrefresh.GiantRefresh.LOGGER.info("[CLIENTTEST] camera player y={}", mc.player.getY());
            String name = "giant_test_" + shots + ".png";
            net.minecraft.client.Screenshot.grab(mc.gameDirectory, name, mc.gameRenderer.mainRenderTarget(), 1,
                    msg -> dev.star1mp.giantrefresh.GiantRefresh.LOGGER.info("[CLIENTTEST] screenshot {}", name));
            shots++;
        } else if (shots >= at.length && seconds > 74.0) {
            dev.star1mp.giantrefresh.GiantRefresh.LOGGER.info("[CLIENTTEST] done, stopping client");
            stopped = true;
            mc.stop();
        }
    }
}
