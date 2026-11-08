package za.co.neroland.nerocompanion.client;

import java.io.File;
import java.util.ArrayDeque;
import java.util.ArrayList;
import java.util.Deque;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

import com.mojang.blaze3d.pipeline.RenderTarget;
import com.mojang.brigadier.Command;
import com.mojang.brigadier.CommandDispatcher;
import com.mojang.brigadier.arguments.StringArgumentType;
import com.mojang.brigadier.builder.LiteralArgumentBuilder;
import com.mojang.brigadier.builder.RequiredArgumentBuilder;

import net.minecraft.client.CloudStatus;
import net.minecraft.client.Minecraft;
import net.minecraft.client.Screenshot;
import net.minecraft.client.player.LocalPlayer;
import net.minecraft.core.registries.BuiltInRegistries;
import net.minecraft.network.chat.Component;
import net.minecraft.resources.Identifier;
import net.minecraft.world.item.Item;
import net.minecraft.world.phys.Vec3;

import za.co.neroland.nerocompanion.NeroCompanionCommon;
import za.co.neroland.nerocompanion.client.look.ClientLooks;
import za.co.neroland.nerocompanion.command.GalleryCommands;
import za.co.neroland.nerocompanion.command.GalleryLayout;
import za.co.neroland.nerocompanion.look.LookKind;
import za.co.neroland.nerocompanion.registry.ModEntities;

/**
 * CLIENT, creative-debug: an automated, reproducible screenshot pass over the {@code /nerocompanion gallery} stage (ported
 * from the family's capture harness).
 *
 * <p>{@code /nerocompanion gallery} builds the stage server-side; this harness drives the camera, hides every overlay and
 * writes one PNG per shot via {@link Screenshot#grab} to {@code <run dir>/screenshots/nerocompanion/}, so the
 * captures can be re-rendered identically after a model or texture change.
 *
 * <p><b>Cross-loader.</b> Vanilla client classes only. Each loader wires {@link #registerClientCommands} from its
 * client-command hook and {@link #tick()} from its client tick. The root is {@code /nerocompanion_capture}, never
 * {@code /nerocompanion}: a client root sharing a server root's name shadows the server subtree (Fabric runs a
 * client root that matches and never forwards it; Forge and NeoForge merge both trees into one suggestion tree).
 *
 * <ul>
 *   <li>{@code /nerocompanion_capture capture [time]} - teleports to the fixed origin {@value #ORIGIN_X}/{@value #ORIGIN_Y}/
 *       {@value #ORIGIN_Z}, rebuilds the stage ({@code /nerocompanion gallery clear}, then {@code /nerocompanion gallery}), freezes
 *       time and weather, hides clouds and the HUD, then shoots the framed list.</li>
 *   <li>{@code /nerocompanion_capture capture shot <name>} - grabs the current view once, overlays hidden.</li>
 * </ul>
 *
 * <p><b>Test world only.</b> The commands it sends need cheats, and they change the world: the daylight and weather
 * cycle game rules are switched off, the time is set and the weather cleared, and none of that is put back when the
 * run ends (the Server-Admin wiki page lists them). The client options it touches - HUD, clouds, view bobbing,
 * flight - are restored.
 */
public final class GalleryCaptureHarness {

    /** Ticks to let chunks/entities settle and a fresh frame render before grabbing. */
    private static final int SETTLE_TICKS = 12;

    /** Ticks to wait after teleporting, before building, for the destination chunks to load. */
    private static final int BUILD_WARMUP_TICKS = 120;

    /**
     * Ticks to wait after the build commands before the cleanup sweep, so the companions settle into STAY and any
     * dropped items land before the sweep.
     */
    private static final int CLEANUP_TICKS = 60;

    /** The fixed staging origin. The gallery builds at the player's feet (east and south of it). */
    private static final int ORIGIN_X = 0;
    private static final int ORIGIN_Y = 100;
    private static final int ORIGIN_Z = 0;

    /**
     * One framed capture. {@code setup} runs before the warmup (teleport, pin the environment);
     * {@code build} runs after it, once the chunks are loaded (clear, build);
     * {@code cleanup} runs after a further {@link #CLEANUP_TICKS} pause (sweep dropped items off the
     * stage); {@code camera}/{@code target} are the pose, and a {@code null} pose means "keep the
     * current view".
     */
    private record Shot(String name, List<String> setup, List<String> build, List<String> cleanup,
            int warmup, Vec3 camera, Vec3 target) {
    }

    private enum Phase { MOVE, WARMUP, CLEANUP, SETTLE, SHOOT }

    private static final Deque<Shot> QUEUE = new ArrayDeque<>();
    private static boolean running;
    private static boolean hudWasHidden;
    private static boolean bobViewWas;
    private static boolean flyingWas;
    private static CloudStatus cloudsWere;
    private static Phase phase = Phase.MOVE;
    private static int warmup;
    private static int cleanupWait;
    private static int settle;
    private static Shot current;

    // The HUD-hide flag and the main render target both DIVERGE across 26.1.2 ↔ 26.2, so they are
    // resolved reflectively rather than referenced at compile time (the same approach the sibling
    // mods use for version-divergent client symbols). 26.1.x: Options#hideGui (boolean) +
    // Minecraft#getMainRenderTarget(). 26.2: Gui#hud → Hud#isHidden()/toggle() +
    // GameRenderer#mainRenderTarget().
    private static boolean hudResolved;
    private static java.lang.reflect.Field optHideGuiField;     // 26.1.x
    private static java.lang.reflect.Field guiHudField;         // 26.2
    private static java.lang.reflect.Method hudIsHiddenMethod;  // 26.2
    private static java.lang.reflect.Method hudToggleMethod;    // 26.2
    private static boolean rtResolved;
    private static java.lang.reflect.Method mcGetRenderTarget;  // 26.1.x
    private static java.lang.reflect.Method grMainRenderTarget; // 26.2

    /** The client command root. Distinct from the server's {@code nerocompanion} root on purpose. */
    public static final String ROOT = "nerocompanion_capture";

    private GalleryCaptureHarness() {
    }

    // ------------------------------------------------------------------------------------------------
    // Command registration (generic in the Brigadier source type — the bodies never read the source).
    // ------------------------------------------------------------------------------------------------

    /**
     * Registers the client-side {@code /nerocompanion_capture capture …} tree. Generic in {@code S} so the same
     * method serves NeoForge/Forge ({@code CommandSourceStack}) and Fabric
     * ({@code FabricClientCommandSource}); every body drives the local client only.
     *
     * <p>The root must NOT be {@code nerocompanion} — see the class docs (it would shadow the server
     * tree, and {@code /nerocompanion gallery} would stop working).
     */
    public static <S> void registerClientCommands(CommandDispatcher<S> dispatcher) {
        dispatcher.register(
                LiteralArgumentBuilder.<S>literal(ROOT)
                        .then(LiteralArgumentBuilder.<S>literal("capture")
                                .executes(ctx -> startCapture("noon"))
                                .then(LiteralArgumentBuilder.<S>literal("shot")
                                        .then(RequiredArgumentBuilder.<S, String>argument("name",
                                                        StringArgumentType.word())
                                                .executes(ctx -> shotHere(
                                                        StringArgumentType.getString(ctx, "name")))))
                                .then(RequiredArgumentBuilder.<S, String>argument("time",
                                                StringArgumentType.word())
                                        .executes(ctx -> startCapture(
                                                StringArgumentType.getString(ctx, "time"))))));
    }

    // ------------------------------------------------------------------------------------------------
    // Run starters.
    // ------------------------------------------------------------------------------------------------

    private static int startCapture(String time) {
        Minecraft mc = Minecraft.getInstance();
        if (mc.player == null || mc.level == null) {
            return 0;
        }
        if (running) {
            chat(mc, Component.literal("Gallery capture already running."));
            return 0;
        }
        QUEUE.clear();
        QUEUE.addAll(galleryQueue(time));
        begin(mc);
        chat(mc, Component.literal("Gallery capture: rebuilding the scene, time="
                + time + ", " + QUEUE.size() + " shots to screenshots/nerocompanion/ (overlays hidden)."));
        return Command.SINGLE_SUCCESS;
    }

    private static int shotHere(String name) {
        Minecraft mc = Minecraft.getInstance();
        if (mc.player == null || mc.level == null || running) {
            return 0;
        }
        QUEUE.clear();
        QUEUE.add(new Shot(name, List.of(), List.of(), List.of(), 0, null, null)); // current view, as-is
        begin(mc);
        return Command.SINGLE_SUCCESS;
    }

    /** The shot list, with the one-time rebuild and environment pin attached to shot 0. */
    private static List<Shot> galleryQueue(String time) {
        List<Shot> shots = new ArrayList<>(buildShots(ORIGIN_X, ORIGIN_Y, ORIGIN_Z));
        // SETUP (pre-warmup) only teleports and pins the environment; BUILD (post-warmup, chunks loaded) rebuilds
        // the stage from scratch, so a rerun never stacks a second set of companions. Server commands: cheats.
        List<String> setup = List.of(
                "tp @s " + (ORIGIN_X + 0.5) + " " + ORIGIN_Y + " " + (ORIGIN_Z + 0.5),
                //? if >=26.1 {
                "gamerule advance_time false",
                "gamerule advance_weather false",
                //?} else {
                /*"gamerule doDaylightCycle false",
                "gamerule doWeatherCycle false",
                *///?}
                "time set " + time,
                "weather clear");
        List<String> build = List.of(
                GalleryCommands.ROOT + " gallery clear",
                GalleryCommands.ROOT + " gallery");
        Shot first = shots.get(0);
        shots.set(0, new Shot(first.name(), setup, build, groundClear(), BUILD_WARMUP_TICKS,
                first.camera(), first.target()));
        return shots;
    }

    private static void begin(Minecraft mc) {
        // screenshots/ is created by Screenshot.grab; the nerocompanion/ subfolder is not.
        new File(mc.gameDirectory, "screenshots/nerocompanion").mkdirs();
        hudWasHidden = isHudHidden(mc);
        setHudHidden(mc, true);
        cloudsWere = mc.options.cloudStatus().get();
        mc.options.cloudStatus().set(CloudStatus.OFF); // clouds scroll with game time → freeze them out
        bobViewWas = mc.options.bobView().get();
        mc.options.bobView().set(false); // kill view-bob so a pinned pose renders the exact same frame
        // Fly, so the camera hovers at the teleport spot instead of falling through the sky while the
        // scene is being built underneath it.
        flyingWas = mc.player != null && mc.player.getAbilities().flying;
        setFlying(mc, true);
        running = true;
        phase = Phase.MOVE;
        current = null;
    }

    /** Toggle creative flight so the camera hovers (no gravity drift) for the duration of a run. */
    private static void setFlying(Minecraft mc, boolean fly) {
        if (mc.player == null) {
            return;
        }
        mc.player.getAbilities().flying = fly && mc.player.getAbilities().mayfly;
        mc.player.onUpdateAbilities();
    }

    // ------------------------------------------------------------------------------------------------
    // Per-client-tick state machine (driven from each loader's client-tick hook).
    // ------------------------------------------------------------------------------------------------

    /** Called once per client tick from each loader's own hook. A no-op unless a run is live. */
    public static void tick() {
        if (!running) {
            return;
        }
        Minecraft mc = Minecraft.getInstance();
        LocalPlayer player = mc.player;
        if (player == null) {
            finish(mc);
            return;
        }

        switch (phase) {
            case MOVE -> {
                final Shot shot = QUEUE.poll();
                current = shot;
                if (shot == null) {
                    finish(mc);
                    return;
                }
                for (String cmd : shot.setup()) { // pre-warmup: teleport + pin time/weather
                    player.connection.sendCommand(cmd);
                }
                warmup = shot.warmup();
                phase = Phase.WARMUP;
            }
            case WARMUP -> {
                final Shot shot = current;
                if (shot == null) {
                    phase = Phase.MOVE;
                    return;
                }
                holdStill(mc, player); // keep flying + motionless so the camera does not drift
                if (--warmup <= 0) { // chunks are loaded → safe to clear / build / strip
                    for (String cmd : shot.build()) {
                        player.connection.sendCommand(cmd);
                    }
                    cleanupWait = shot.cleanup().isEmpty() ? 0 : CLEANUP_TICKS;
                    phase = Phase.CLEANUP;
                }
            }
            case CLEANUP -> {
                final Shot shot = current;
                if (shot == null) {
                    phase = Phase.MOVE;
                    return;
                }
                holdStill(mc, player); // stay aloft while the dropped items settle
                if (--cleanupWait <= 0) {
                    for (String cmd : shot.cleanup()) {
                        player.connection.sendCommand(cmd);
                    }
                    applyPose(player, shot);
                    settle = SETTLE_TICKS;
                    phase = Phase.SETTLE;
                }
            }
            case SETTLE -> {
                final Shot shot = current;
                if (shot == null) {
                    phase = Phase.MOVE;
                    return;
                }
                applyPose(player, shot); // re-pin every tick so gravity cannot drift the camera
                if (--settle <= 0) {
                    phase = Phase.SHOOT;
                }
            }
            case SHOOT -> {
                final Shot shot = current;
                if (shot == null) {
                    phase = Phase.MOVE;
                    return;
                }
                grab(mc, shot.name());
                phase = Phase.MOVE;
            }
        }
    }

    /** Keep the player flying + motionless during a wait so gravity does not drift the camera. */
    private static void holdStill(Minecraft mc, LocalPlayer player) {
        if (!player.getAbilities().flying && player.getAbilities().mayfly) {
            setFlying(mc, true); // re-assert if the server toggled it off
        }
        player.setDeltaMovement(Vec3.ZERO);
    }

    /** Snap the player (the render camera) to the shot's pose, holding it still. */
    private static void applyPose(LocalPlayer player, Shot shot) {
        if (shot == null) {
            return;
        }
        Vec3 cam = shot.camera();
        Vec3 tgt = shot.target();
        if (cam == null || tgt == null) {
            return; // "keep the current view" shot
        }
        double dx = tgt.x - cam.x;
        double dy = tgt.y - cam.y;
        double dz = tgt.z - cam.z;
        double horiz = Math.sqrt(dx * dx + dz * dz);
        float yaw = (float) (Math.toDegrees(Math.atan2(dz, dx)) - 90.0);
        float pitch = (float) (-Math.toDegrees(Math.atan2(dy, horiz)));
        player.snapTo(cam.x, cam.y, cam.z, yaw, pitch);
        player.setDeltaMovement(Vec3.ZERO);
    }

    private static void grab(Minecraft mc, String name) {
        RenderTarget target = mainRenderTarget(mc);
        if (target == null) {
            if (mc.player != null) {
                chat(mc, Component.literal("Capture: could not resolve the render target."));
            }
            return;
        }
        //? if >=1.21.6 {
        Screenshot.grab(mc.gameDirectory, "nerocompanion/" + name + ".png", target, 1,
        //?} else {
        /*Screenshot.grab(mc.gameDirectory, "nerocompanion/" + name + ".png", target,
        *///?}
                msg -> {
                    if (mc.player != null) {
                        chat(mc, msg);
                    }
                });
    }

    private static void finish(Minecraft mc) {
        setHudHidden(mc, hudWasHidden);
        if (cloudsWere != null) {
            mc.options.cloudStatus().set(cloudsWere);
        }
        mc.options.bobView().set(bobViewWas);
        setFlying(mc, flyingWas);
        running = false;
        current = null;
        QUEUE.clear();
        if (mc.player != null) {
            chat(mc, 
                    Component.literal("Capture done — see screenshots/nerocompanion/."));
        }
    }

    // --- HUD hide + render target (version-divergent → reflective) ----------------------------------

    private static void resolveHud() {
        if (hudResolved) {
            return;
        }
        hudResolved = true;
        try { // 26.1.x: Options#hideGui boolean field
            optHideGuiField = net.minecraft.client.Options.class.getDeclaredField("hideGui");
            optHideGuiField.setAccessible(true);
        } catch (ReflectiveOperationException | RuntimeException e) {
            optHideGuiField = null;
        }
        if (optHideGuiField == null) {
            try { // 26.2: Gui#hud → Hud#isHidden()/toggle()
                guiHudField = net.minecraft.client.gui.Gui.class.getDeclaredField("hud");
                guiHudField.setAccessible(true);
                Class<?> hudType = guiHudField.getType();
                hudIsHiddenMethod = hudType.getMethod("isHidden");
                hudToggleMethod = hudType.getMethod("toggle");
            } catch (ReflectiveOperationException | RuntimeException e) {
                guiHudField = null;
                hudIsHiddenMethod = null;
                hudToggleMethod = null;
            }
        }
    }

    private static boolean isHudHidden(Minecraft mc) {
        resolveHud();
        try {
            if (optHideGuiField != null) {
                return optHideGuiField.getBoolean(mc.options);
            }
            if (guiHudField != null && hudIsHiddenMethod != null) {
                Object hud = guiHudField.get(mc.gui);
                return hud != null && Boolean.TRUE.equals(hudIsHiddenMethod.invoke(hud));
            }
        } catch (ReflectiveOperationException | RuntimeException e) {
            // fall through to "not hidden"
        }
        return false;
    }

    private static void setHudHidden(Minecraft mc, boolean hidden) {
        resolveHud();
        try {
            if (optHideGuiField != null) {
                optHideGuiField.setBoolean(mc.options, hidden);
                return;
            }
            if (guiHudField != null && hudIsHiddenMethod != null && hudToggleMethod != null) {
                Object hud = guiHudField.get(mc.gui);
                if (hud != null && !Boolean.valueOf(hidden).equals(hudIsHiddenMethod.invoke(hud))) {
                    hudToggleMethod.invoke(hud); // toggle only when not already in the wanted state
                }
            }
        } catch (ReflectiveOperationException | RuntimeException e) {
            // best-effort; a missing flag just means the HUD shows in the shot
        }
    }

    /**
     * The main framebuffer: 26.1.x {@code Minecraft#getMainRenderTarget()}, 26.2
     * {@code GameRenderer#mainRenderTarget()}.
     */
    private static RenderTarget mainRenderTarget(Minecraft mc) {
        if (!rtResolved) {
            rtResolved = true;
            try {
                mcGetRenderTarget = Minecraft.class.getMethod("getMainRenderTarget");
            } catch (NoSuchMethodException | RuntimeException e) {
                mcGetRenderTarget = null;
            }
            if (mcGetRenderTarget == null) {
                try {
                    grMainRenderTarget = mc.gameRenderer.getClass().getMethod("mainRenderTarget");
                } catch (ReflectiveOperationException | RuntimeException e) {
                    grMainRenderTarget = null;
                }
            }
        }
        try {
            if (mcGetRenderTarget != null) {
                return (RenderTarget) mcGetRenderTarget.invoke(mc);
            }
            if (grMainRenderTarget != null) {
                return (RenderTarget) grMainRenderTarget.invoke(mc.gameRenderer);
            }
        } catch (ReflectiveOperationException | RuntimeException e) {
            // fall through
        }
        return null;
    }

    // ------------------------------------------------------------------------------------------------
    // The shot list, framed from the same command/GalleryLayout plan the server builds (worked out here from the
    // synced look catalogue and the item registry, so the two cannot drift). Each companion row is shot in pieces
    // of SHOT_COLUMNS from north of the row, between it and the row before; each item-wall group is shot square on,
    // then the whole wall and an overview.
    // ------------------------------------------------------------------------------------------------

    /** Companions per row shot. */
    private static final int SHOT_COLUMNS = 4;

    /** The plan as this client sees it: the types, the synced looks and every item in the namespace. */
    private static GalleryLayout.Plan clientPlan() {
        List<Identifier> items = new ArrayList<>();
        for (Item item : BuiltInRegistries.ITEM) {
            Identifier id = BuiltInRegistries.ITEM.getKey(item);
            if (id != null && NeroCompanionCommon.MOD_ID.equals(id.getNamespace())) {
                items.add(id);
            }
        }
        return GalleryLayout.plan(
                GalleryLayout.displays(ModEntities.COMPANION_TYPES, ClientLooks.all(LookKind.SKIN),
                        ClientLooks.all(LookKind.HAT)),
                GalleryLayout.wallItems(items, ModEntities.COMPANION_TYPES));
    }

    private static List<Shot> buildShots(int ox, int oy, int oz) {
        GalleryLayout.Plan plan = clientPlan();
        List<Shot> shots = new ArrayList<>();
        Map<String, Integer> perSection = new HashMap<>();
        for (GalleryLayout.Row row : plan.rows()) {
            int index = perSection.merge(row.section(), 1, Integer::sum);
            for (int first = 0; first < row.count(); first += SHOT_COLUMNS) {
                int last = Math.min(row.count(), first + SHOT_COLUMNS) - 1;
                double x = ox + GalleryLayout.FIRST_X + (first + last) * GalleryLayout.STEP / 2.0 + 0.5;
                double z = oz + row.dz() + 0.5;
                shots.add(shot("companion_" + row.section() + "_" + index + "_" + (first / SHOT_COLUMNS + 1),
                        x, oy + 2.2, z - 4.0, x, oy + 0.6, z));
            }
        }
        double chargersZ = oz + GalleryLayout.FIRST_ROW_Z + GalleryLayout.ROW_STEP + 0.5;
        shots.add(shot("companion_chargers", ox + 5.0, oy + 1.8, chargersZ, ox + GalleryLayout.CHARGER_X + 0.5,
                oy + 0.4, chargersZ));
        double wallFace = oz + plan.wallZ() + 0.5;
        double wallMid = oy + 1.0 + GalleryLayout.WALL_ROWS / 2.0;
        for (GalleryLayout.Label label : plan.labels()) {
            double x = ox + label.dx() + label.columns() / 2.0;
            double distance = Math.max(4.5, label.columns() / 2.2 + 2.0);
            shots.add(shot("items_" + label.group(), x, wallMid + 0.5, wallFace - distance, x, wallMid - 0.5,
                    wallFace));
        }
        double wallX = ox + plan.length() / 2.0;
        shots.add(shot("items_wall", wallX, wallMid + 2.0, wallFace - Math.max(6.0, plan.length() / 2.2 + 2.0),
                wallX, wallMid - 0.5, wallFace));
        shots.add(shot("companion_overview", ox - 6.0, oy + Math.max(14.0, plan.depth() * 0.45), oz - 6.0,
                ox + plan.length() / 2.0, oy, oz + plan.depth() / 2.0));
        return shots;
    }

    /** A camera-only shot (no setup/build/cleanup — the rebuild rides on shot 0). */
    private static Shot shot(String name, double cx, double cy, double cz,
            double tx, double ty, double tz) {
        return new Shot(name, List.of(), List.of(), List.of(), 0,
                new Vec3(cx, cy, cz), new Vec3(tx, ty, tz));
    }

    /** Sweep dropped items + XP-orb litter off the stage (run after a {@link #CLEANUP_TICKS} pause). */
    private static List<String> groundClear() {
        return List.of("kill @e[type=item]", "kill @e[type=experience_orb]");
    }

    /** A local chat line to the capturing player; LocalPlayer.sendSystemMessage exists only from 26.1. */
    private static void chat(Minecraft mc, Component message) {
        //? if >=26.1 {
        mc.player.sendSystemMessage(message);
        //?} else {
        /*mc.player.displayClientMessage(message, false);
        *///?}
    }
}
