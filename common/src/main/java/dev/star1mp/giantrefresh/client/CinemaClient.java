package dev.star1mp.giantrefresh.client;

import dev.star1mp.giantrefresh.GiantRefresh;
import dev.star1mp.giantrefresh.cinema.Cam;
import dev.star1mp.giantrefresh.cinema.Cinema;
import dev.star1mp.giantrefresh.cinema.CinemaClientBridge;
import java.io.File;
import java.io.FileWriter;
import java.io.IOException;
import java.io.PrintWriter;
import java.util.ArrayList;
import java.util.List;
import java.util.Locale;
import net.minecraft.client.DeltaTracker;
import net.minecraft.client.Minecraft;
import net.minecraft.client.resources.sounds.SoundInstance;
import net.minecraft.client.sounds.SoundManager;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.phys.Vec3;

/** Client half of the trailer recorder: camera, frame capture and a log of every sound played. */
public final class CinemaClient {
    private static long readySince;
    private static int nextFrame;
    private static int lastFile = -1;
    private static final List<String> frameMap = new ArrayList<>();
    private static PrintWriter soundLog;
    private static double lastT = -1;
    private static long stopAt;
    private static final java.util.concurrent.atomic.AtomicInteger pending = new java.util.concurrent.atomic.AtomicInteger();
    private static float partial;

    static {
        CinemaClientBridge.resolver = (id, dy) -> {
            Minecraft mc = Minecraft.getInstance();
            if (mc.level == null) {
                return null;
            }
            Entity e = mc.level.getEntity(id);
            return e == null ? null : e.getPosition(partial).add(0, dy, 0);
        };
    }

    private CinemaClient() {}

    /** Shot time in ticks (with the partial tick), or -1 before the recording. */
    private static double time(Minecraft mc) {
        if (Cinema.startTick < 0 || mc.level == null) {
            return -1;
        }
        return (mc.level.getGameTime() - Cinema.startTick) + partial;
    }

    /** Camera pose for this frame, or null (not recording / nothing staged yet). */
    public static Cam.Pose camera(DeltaTracker dt) {
        Minecraft mc = Minecraft.getInstance();
        partial = dt.getGameTimeDeltaPartialTick(true);
        if (Cinema.shot == null || Cinema.phase < 2) {
            return null;
        }
        hideHud(mc);
        if (Cinema.phase == 2 && readySince == 0) {
            readySince = System.nanoTime();
        }
        if (Cinema.phase == 2 && (System.nanoTime() - readySince) / 1e9 > Double.parseDouble(System.getProperty("giantrefresh.cinema.warmup", "25"))) {
            Cinema.clientReady = true;
        }
        double t = time(mc);
        lastT = t;
        Cam.Pose p = Cinema.shot.cam.pose(Math.max(0, t) / 20.0);
        int fov = Math.round(p.fov());
        if (mc.options.fov().get() != fov) {
            mc.options.fov().set(fov);
        }
        return p;
    }

    /** End of a rendered frame: keep it if the game clock has reached the next 1/FPS step. */
    public static void afterFrame() {
        Minecraft mc = Minecraft.getInstance();
        if (Cinema.phase < 3 || Cinema.shot == null) {
            return;
        }
        if (stopAt != 0) {
            if (System.nanoTime() > stopAt && pending.get() == 0) {
                GiantRefresh.LOGGER.info("[CINEMA] done");
                mc.stop();
            }
            return;
        }
        double t = lastT;
        if (t < 0) {
            return;
        }
        int total = (int) Math.round(Cinema.shot.duration * Cinema.FPS);
        double step = 20.0 / Cinema.FPS;
        if (t + 1e-6 < nextFrame * step) {
            return;
        }
        int file = nextFrame;
        String name = String.format(Locale.ROOT, "cine_%05d.png", file);
        pending.incrementAndGet();
        net.minecraft.client.Screenshot.grab(mc.gameDirectory, name, mc.gameRenderer.mainRenderTarget(), 1, msg -> pending.decrementAndGet());
        while (nextFrame * step <= t + 1e-6 && nextFrame <= total) {
            frameMap.add(nextFrame + " " + file + " " + String.format(Locale.ROOT, "%.4f", t));
            nextFrame++;
        }
        if (nextFrame > total) {
            try (PrintWriter w = new PrintWriter(new FileWriter(new File(mc.gameDirectory, "cine_frames.txt")))) {
                frameMap.forEach(w::println);
            } catch (IOException e) {
                GiantRefresh.LOGGER.error("[CINEMA] frame map", e);
            }
            if (soundLog != null) {
                soundLog.flush();
            }
            stopAt = System.nanoTime() + 4_000_000_000L;
        }
    }

    /** Every sound the game plays during the recording, for the sound mix. */
    public static void onSound(SoundManager manager, SoundInstance s) {
        Minecraft mc = Minecraft.getInstance();
        double t = time(mc);
        if (Cinema.phase < 3 || t < 0 || stopAt != 0) {
            return;
        }
        try {
            s.resolve(manager);
            if (s.getSound() == null) {
                return;
            }
            if (soundLog == null) {
                soundLog = new PrintWriter(new FileWriter(new File(mc.gameDirectory, "cine_sounds.jsonl")));
            }
            Vec3 cam = Cinema.shot.cam.pose(Math.max(0, t) / 20.0).pos();
            soundLog.println(String.format(Locale.ROOT,
                    "{\"t\":%.4f,\"event\":\"%s\",\"file\":\"%s\",\"src\":\"%s\",\"vol\":%.3f,\"pitch\":%.3f,\"x\":%.2f,\"y\":%.2f,\"z\":%.2f,\"att\":\"%s\",\"rel\":%s,\"dist\":%d,\"cx\":%.2f,\"cy\":%.2f,\"cz\":%.2f}",
                    t / 20.0, s.getIdentifier(), s.getSound().getLocation(), s.getSource().getName(), s.getVolume(), s.getPitch(),
                    s.getX(), s.getY(), s.getZ(), s.getAttenuation(), s.isRelative(), s.getSound().getAttenuationDistance(), cam.x, cam.y, cam.z));
        } catch (IOException | RuntimeException e) {
            GiantRefresh.LOGGER.warn("[CINEMA] sound log: {}", e.toString());
        }
    }

    private static void hideHud(Minecraft mc) {
        if (!mc.gui.hud.isHidden()) {
            mc.gui.hud.toggle();
        }
    }
}
