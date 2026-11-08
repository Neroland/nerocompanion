package za.co.neroland.nerocompanion.gametest;

import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.List;
import java.util.Locale;
import java.util.function.Predicate;

import com.mojang.blaze3d.platform.InputConstants;

import net.fabricmc.fabric.api.client.gametest.v1.FabricClientGameTest;
import net.fabricmc.fabric.api.client.gametest.v1.TestInput;
import net.fabricmc.fabric.api.client.gametest.v1.context.ClientGameTestContext;
import net.fabricmc.fabric.api.client.gametest.v1.context.TestServerContext;
import net.fabricmc.fabric.api.client.gametest.v1.context.TestSingleplayerContext;
import net.fabricmc.loader.api.FabricLoader;
import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.screens.Screen;
import net.minecraft.client.gui.screens.worldselection.WorldCreationUiState;
import net.minecraft.core.BlockPos;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.phys.Vec3;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import za.co.neroland.nerocompanion.client.ClientState;
import za.co.neroland.nerocompanion.client.screen.CompanionBayScreen;
import za.co.neroland.nerocompanion.client.screen.CompanionScreen;
import za.co.neroland.nerocompanion.client.screen.RemoteRadialScreen;
import za.co.neroland.nerocompanion.client.screen.SkinStudioScreen;
import za.co.neroland.nerocompanion.entity.CompanionEntity;
import za.co.neroland.nerocompanion.network.payload.Payloads;

/**
 * UI audit of NeroCompanion driven through real simulated input (key mappings, mouse buttons and cursor moves go
 * through Minecraft's own KeyboardHandler / MouseHandler, exactly like a player's). Test-only: lives in the
 * {@code gametest} source set and never ships in the mod jar.
 *
 * <p>Every step is logged to {@code <run dir>/screenshots/ui-audit-report.txt} and gets a screenshot. A step that
 * fails is recorded and the run continues, so one broken screen does not hide the rest.
 *
 * <p>Privacy: the log records screen classes, modes, looks and counts only - never a player name or UUID.
 */
@SuppressWarnings("UnstableApiUsage")
public class NeroCompanionUiClientGameTest implements FabricClientGameTest {

    private static final Logger LOG = LoggerFactory.getLogger("NeroCompanionUiAudit");
    private static final int LEFT = InputConstants.MOUSE_BUTTON_LEFT;
    private static final String[] MODES = {"FOLLOW", "STAY", "GUARD", "MINE", "CARRY", "REPAIR", "SCOUT", "SLEEP"};

    private final List<String> report = new ArrayList<>();
    private int shot;
    private TestServerContext server;

    @Override
    public void runTest(ClientGameTestContext ctx) {
        TestInput in = ctx.getInput();
        in.resizeWindow(1280, 720);
        try (TestSingleplayerContext sp = ctx.worldBuilder().adjustSettings(s -> {
            s.setGameMode(WorldCreationUiState.SelectedGameMode.CREATIVE);
            s.setAllowCommands(true);
        }).create()) {
            sp.getConnection().waitForChunksRender();
            TestServerContext server = sp.getServer();
            this.server = server;
            this.log("env", "MOUSE_BUTTON_LEFT=" + InputConstants.MOUSE_BUTTON_LEFT + " MOUSE_BUTTON_RIGHT="
                    + InputConstants.MOUSE_BUTTON_RIGHT + " guiScale=" + ctx.computeOnClient(mc -> mc.getWindow().getGuiScale()));

            this.section("setup+deploy", () -> this.setupAndDeploy(ctx, in, server));
            this.section("radial", () -> this.radial(ctx, in));
            this.section("companion screen", () -> this.companionScreen(ctx, in));
            this.section("bay", () -> this.bay(ctx, in, server));
            this.section("recall via radial", () -> this.recall(ctx, in));
            this.section("gallery", () -> this.gallery(ctx, in, server));
        } finally {
            this.writeReport();
        }
    }

    // =============================================================================================================
    // Sections
    // =============================================================================================================

    private void setupAndDeploy(ClientGameTestContext ctx, TestInput in, TestServerContext server) {
        server.runCommand("give @a nerocompanion:capsule[nerocompanion:blueprint=\"nerocompanion:personal_drone\"]");
        server.runCommand("give @a nerocompanion:remote");
        server.runCommand("give @a nerocompanion:companion_bay");
        ctx.waitTicks(5);
        in.pressKey(o -> o.keyHotbarSlots[0]);
        BlockPos feet = ctx.computeOnClient(mc -> mc.player.blockPosition());
        BlockPos ground = feet.offset(0, -1, 3);
        in.lookAt(ground);
        ctx.waitTicks(2);
        this.log("deploy", "hand=" + this.handItem(ctx) + " looking at ground block 3 south; pressing use");
        in.pressKey(o -> o.keyUse);
        ctx.waitTicks(30);
        int companions = this.serverCompanionCount(ctx);
        int hud = ctx.computeOnClient(mc -> ClientState.hud().size());
        this.check("deploy", companions == 1 && hud == 1, "server companions=" + companions + " hud entries=" + hud
                + " hand after=" + this.handItem(ctx));
        in.lookAt(ctx.computeOnClient(mc -> mc.player.getYRot()), 0.0F);
        ctx.waitTicks(5);
        this.shot(ctx, "deploy_world_hud");
        in.pressKey(o -> o.keyHotbarSlots[1]);
        ctx.waitTicks(3);
        this.log("hud", "hand=" + this.handItem(ctx) + " mode=" + this.hudMode(ctx));
        this.shot(ctx, "hud_remote_in_hand");
    }

    private void radial(ClientGameTestContext ctx, TestInput in) {
        // 1. Hold the use key with the Remote: the radial should open after 4 ticks.
        boolean open = this.openRadial(ctx, in, false);
        this.check("radial.open_by_hold", open, "screen while holding use=" + this.screenName(ctx));
        this.shot(ctx, "radial_open_holding_use");
        // 2. Release the use key with the cursor on the centre (nothing selected): the radial must stay open.
        in.releaseKey(o -> o.keyUse);
        ctx.waitTicks(3);
        this.check("radial.release_nothing_keeps_open", this.isScreen(ctx, RemoteRadialScreen.class),
                "screen after releasing use on centre=" + this.screenName(ctx) + " mode=" + this.hudMode(ctx));
        this.shot(ctx, "radial_after_release_on_centre");

        // 3. Left-click each wedge (radial reopened each time). Wedge 0 is FOLLOW; finish on STAY so the companion
        //    stays put for the screens below.
        int[] order = {2, 3, 4, 5, 6, 7, 0, 1};
        for (int i : order) {
            if (!this.isScreen(ctx, RemoteRadialScreen.class)) {
                this.openRadialAndRelease(ctx, in);
            }
            String before = this.hudMode(ctx);
            this.moveToRadial(ctx, in, i);
            this.shot(ctx, "radial_hover_wedge" + (i + 1) + "_" + MODES[i]);
            in.pressMouse(LEFT);
            ctx.waitTicks(25);
            String after = this.hudMode(ctx);
            String screen = this.screenName(ctx);
            boolean allowed = this.radialAllows(ctx, i);
            boolean ok = allowed ? !"RemoteRadialScreen".equals(screen) && after.equals(MODES[i])
                    : "RemoteRadialScreen".equals(screen) && after.equals(before);
            this.check("radial.leftclick_wedge" + (i + 1) + "_" + MODES[i], ok,
                    "allowed for this companion=" + allowed + "; mode " + before + " -> " + after
                            + ", screen after click=" + screen
                            + " (allowed: closes + SetMode; denied: stays open, red reason line)");
            this.shot(ctx, "radial_after_click_wedge" + (i + 1) + "_" + MODES[i]);
            if (this.isScreen(ctx, RemoteRadialScreen.class)) {
                in.pressKey(InputConstants.KEY_ESCAPE);
                ctx.waitTicks(2);
            }
        }

        // 4. Hub ("Come here") by left click.
        this.openRadialAndRelease(ctx, in);
        String before = this.hudMode(ctx);
        this.moveToGui(ctx, in, 0, 0);
        in.pressMouse(LEFT);
        ctx.waitTicks(12);
        this.check("radial.leftclick_hub", !this.isScreen(ctx, RemoteRadialScreen.class),
                "mode " + before + " -> " + this.hudMode(ctx) + ", screen=" + this.screenName(ctx));
        this.shot(ctx, "radial_after_click_hub");

        // 5. Hold, aim at CARRY (wedge 5, allowed for a Helper Drone) while still holding, release: confirms CARRY.
        this.openRadial(ctx, in, false);
        this.moveToRadial(ctx, in, 4);
        this.shot(ctx, "radial_hold_aim_carry");
        String screenAtRelease = this.screenName(ctx);
        in.releaseKey(o -> o.keyUse);
        ctx.waitTicks(30);
        this.check("radial.hold_aim_release", !this.isScreen(ctx, RemoteRadialScreen.class) && "CARRY".equals(this.serverMode()),
                "screen before release=" + screenAtRelease + "; after release on CARRY: screen=" + this.screenName(ctx)
                        + " hud mode=" + this.hudMode(ctx) + " server mode=" + this.serverMode());
        this.shot(ctx, "radial_after_hold_aim_release");
        this.escapeAll(ctx, in);

        // 6. Number key 2 (STAY).
        this.openRadialAndRelease(ctx, in);
        in.pressKey(InputConstants.KEY_2);
        ctx.waitTicks(12);
        this.check("radial.number_key_2", !this.isScreen(ctx, RemoteRadialScreen.class),
                "screen=" + this.screenName(ctx) + " mode=" + this.hudMode(ctx));
        this.escapeAll(ctx, in);

        // 7. Click on empty space closes without sending.
        this.openRadialAndRelease(ctx, in);
        before = this.hudMode(ctx);
        this.moveToGui(ctx, in, 150, -100);
        in.pressMouse(LEFT);
        ctx.waitTicks(8);
        this.check("radial.leftclick_empty_closes", !this.isScreen(ctx, RemoteRadialScreen.class),
                "screen=" + this.screenName(ctx) + " mode " + before + " -> " + this.hudMode(ctx));
        this.escapeAll(ctx, in);
        // Leave the companion on STAY for the screens.
        this.openRadialAndRelease(ctx, in);
        in.pressKey(InputConstants.KEY_2);
        ctx.waitTicks(10);
        this.escapeAll(ctx, in);
        this.log("radial", "final mode=" + this.hudMode(ctx));
    }

    private void companionScreen(ClientGameTestContext ctx, TestInput in) {
        in.pressKey(o -> o.keyHotbarSlots[1]);
        this.lookAtCompanion(ctx, in);
        boolean onCompanion = ctx.computeOnClient(mc -> mc.crosshairPickEntity instanceof CompanionEntity);
        this.log("companion.open", "crosshair on companion=" + onCompanion + "; tapping use with the Remote");
        in.pressKey(o -> o.keyUse);
        boolean open = this.awaitScreen(ctx, CompanionScreen.class, 40);
        this.check("companion.open_by_remote_tap", open, "screen=" + this.screenName(ctx));
        if (!open) {
            this.escapeAll(ctx, in);
            this.log("companion.open", "falling back to the K key");
            in.pressKey(InputConstants.KEY_K);
            open = this.awaitScreen(ctx, CompanionScreen.class, 40);
            this.check("companion.open_by_K", open, "screen=" + this.screenName(ctx));
        }
        if (!open) {
            return;
        }
        ctx.waitTicks(5);
        this.shot(ctx, "companion_screen");

        // Modes (8 buttons, 2 columns x 4 rows at x 132/186, y 40/58/76/94, 52x16)
        int[] modeX = {132, 186};
        int[] modeY = {40, 58, 76, 94};
        for (int i = 0; i < 8; i++) {
            String before = this.snapMode(ctx);
            this.clickPanel(ctx, in, 248, 150, modeX[i % 2] + 26, modeY[i / 2] + 8);
            ctx.waitTicks(12);
            boolean allowed = this.screenAllows(ctx, i);
            String after = this.snapMode(ctx);
            this.check("companion.mode_" + MODES[i], this.isScreen(ctx, CompanionScreen.class)
                    && (allowed ? after.equals(MODES[i]) : after.equals(before)),
                    "allowed=" + allowed + " snapshot mode " + before + " -> " + after
                            + " (allowed: mode changes; denied: unchanged + red footer)");
            this.shot(ctx, "companion_click_mode_" + MODES[i]);
        }
        // Back to STAY so it stays near.
        this.clickPanel(ctx, in, 248, 150, modeX[1] + 26, modeY[0] + 8);
        ctx.waitTicks(10);

        // Rename: click the name box, select all, type, save tick.
        String nameBefore = ctx.computeOnClient(mc -> ClientState.companion() == null ? "?" : ClientState.companion().name());
        this.clickPanel(ctx, in, 248, 150, 8 + 52, 32 + 7);
        in.holdControl();
        in.pressKey(InputConstants.KEY_A);
        in.releaseControl();
        in.typeChars("Bolt");
        ctx.waitTicks(2);
        this.shot(ctx, "companion_name_typed");
        this.clickPanel(ctx, in, 248, 150, 114 + 7, 32 + 7);
        ctx.waitTicks(12);
        String nameAfter = ctx.computeOnClient(mc -> ClientState.companion() == null ? "?" : ClientState.companion().name());
        this.check("companion.rename", "Bolt".equals(nameAfter), "name changed=" + !nameBefore.equals(nameAfter)
                + " (now 'Bolt'=" + "Bolt".equals(nameAfter) + ")");
        this.shot(ctx, "companion_after_rename");

        // Chip slot 0 with an empty-of-chip hand: expect the grey "hold a chip" hint.
        int slots = ctx.computeOnClient(mc -> ClientState.companion() == null ? 0 : Math.min(3, ClientState.companion().chipSlots()));
        if (slots > 0) {
            this.clickPanel(ctx, in, 248, 150, 208 - 20 * (slots - 1) + 9, 112 + 9);
            ctx.waitTicks(5);
            this.log("companion.chip_slot", "clicked chip slot 1 of " + slots + " holding the Remote (expect hint)");
            this.shot(ctx, "companion_click_chip_slot");
        } else {
            this.log("companion.chip_slot", "no chip slots on this type");
        }

        // Skin Studio
        this.clickPanel(ctx, in, 248, 150, 8 + 38, 98 + 8);
        boolean studio = this.awaitScreen(ctx, SkinStudioScreen.class, 10);
        this.check("studio.open_by_button", studio, "screen=" + this.screenName(ctx));
        if (studio) {
            this.skinStudio(ctx, in);
        }

        // Close (X) button
        if (!this.isScreen(ctx, CompanionScreen.class)) {
            this.escapeAll(ctx, in);
            this.lookAtCompanion(ctx, in);
            in.pressKey(InputConstants.KEY_K);
            this.awaitScreen(ctx, CompanionScreen.class, 40);
        }
        if (this.isScreen(ctx, CompanionScreen.class)) {
            this.clickPanel(ctx, in, 248, 150, 248 - 11 + 5, 5 + 5);
            ctx.waitTicks(5);
            this.check("companion.close_button", this.screenName(ctx).equals("none"), "screen=" + this.screenName(ctx));
        }
        this.escapeAll(ctx, in);
    }

    private void skinStudio(ClientGameTestContext ctx, TestInput in) {
        ctx.waitTicks(10);
        this.log("studio", "unlocked skins=" + this.unlocked(ctx, false) + " unlocked hats=" + this.unlocked(ctx, true)
               );
        this.shot(ctx, "studio_skins_tab");
        int[] cardX = {116, 168, 220};
        int[] cardY = {48, 100};
        // Skins: select each visible card, then try Apply on the second card.
        for (int c = 1; c < 3; c++) {
            this.clickPanel(ctx, in, 276, 210, cardX[c] + 24, cardY[0] + 24);
            ctx.waitTicks(3);
            this.shot(ctx, "studio_skins_card" + (c + 1));
        }
        this.clickPanel(ctx, in, 276, 210, cardX[1] + 24, cardY[0] + 24);
        String skinBefore = this.look(ctx, false);
        this.clickPanel(ctx, in, 276, 210, 204 + 32, 178 + 8);
        ctx.waitTicks(15);
        this.check("studio.apply_skin_card2", !skinBefore.equals(this.look(ctx, false)),
                "skin " + skinBefore + " -> " + this.look(ctx, false) + " (server entity skin=" + this.serverLook(ctx, false)
                        + "); if card 2 is locked Apply is disabled by design");
        this.shot(ctx, "studio_skins_after_apply");

        // Hats tab
        this.clickPanel(ctx, in, 276, 210, 168 + 24, 30 + 7);
        ctx.waitTicks(3);
        this.shot(ctx, "studio_hats_tab");
        for (int c = 1; c < 3; c++) {
            this.clickPanel(ctx, in, 276, 210, cardX[c] + 24, cardY[0] + 24);
            ctx.waitTicks(3);
            this.shot(ctx, "studio_hats_card" + (c + 1));
        }
        this.clickPanel(ctx, in, 276, 210, cardX[1] + 24, cardY[0] + 24);
        String hatBefore = this.look(ctx, true);
        this.clickPanel(ctx, in, 276, 210, 204 + 32, 178 + 8);
        ctx.waitTicks(15);
        this.check("studio.apply_hat_card2", !hatBefore.equals(this.look(ctx, true)),
                "hat " + hatBefore + " -> " + this.look(ctx, true) + " (server entity hat=" + this.serverLook(ctx, true) + ")");
        this.shot(ctx, "studio_hats_after_apply");
        // Back
        this.clickPanel(ctx, in, 276, 210, 8 + 24, 178 + 8);
        ctx.waitTicks(5);
        this.check("studio.back_button", this.isScreen(ctx, CompanionScreen.class), "screen=" + this.screenName(ctx));
        // Show the companion wearing its look in the world.
        this.escapeAll(ctx, in);
        this.lookAtCompanion(ctx, in);
        ctx.waitTicks(5);
        this.shot(ctx, "world_companion_with_look");
    }

    private void bay(ClientGameTestContext ctx, TestInput in, TestServerContext server) {
        this.escapeAll(ctx, in);
        in.pressKey(o -> o.keyHotbarSlots[2]);
        BlockPos feet = ctx.computeOnClient(mc -> mc.player.blockPosition());
        BlockPos ground = feet.offset(2, -1, 0);
        in.lookAt(ground);
        ctx.waitTicks(2);
        in.pressKey(o -> o.keyUse);
        ctx.waitTicks(5);
        BlockPos bayPos = ground.above();
        String placed = ctx.computeOnClient(mc -> mc.level.getBlockState(bayPos).getBlock().getDescriptionId());
        this.log("bay.place", "block at target=" + placed);
        in.pressKey(o -> o.keyHotbarSlots[3]);
        in.lookAt(bayPos);
        ctx.waitTicks(2);
        in.pressKey(o -> o.keyUse);
        boolean open = this.awaitScreen(ctx, CompanionBayScreen.class, 40);
        this.check("bay.open", open, "screen=" + this.screenName(ctx));
        if (!open) {
            return;
        }
        ctx.waitTicks(10);
        this.shot(ctx, "bay_screen");
        int occupiedBefore = this.bayOccupied(ctx);
        this.clickPanel(ctx, in, 248, 196, 8 + 112 - 10 - 32 + 16, 130 + 3 + 1 + 6);
        ctx.waitTicks(20);
        this.check("bay.park_first_nearby", this.bayOccupied(ctx) > occupiedBefore,
                "occupied slots " + occupiedBefore + " -> " + this.bayOccupied(ctx));
        this.shot(ctx, "bay_after_park");
        int occ = this.bayOccupied(ctx);
        this.clickPanel(ctx, in, 248, 196, 8 + 24, 74 + 6);
        ctx.waitTicks(20);
        this.check("bay.unpark_slot1", this.bayOccupied(ctx) < occ, "occupied slots " + occ + " -> " + this.bayOccupied(ctx));
        this.shot(ctx, "bay_after_unpark");
        this.escapeAll(ctx, in);
        ctx.waitTicks(10);
        this.shot(ctx, "world_hud_after_bay");
    }

    private void recall(ClientGameTestContext ctx, TestInput in) {
        this.escapeAll(ctx, in);
        in.pressKey(o -> o.keyHotbarSlots[1]);
        this.openRadialAndRelease(ctx, in);
        this.moveToGui(ctx, in, 0, 65);
        this.shot(ctx, "radial_hover_recall");
        int before = this.serverCompanionCount(ctx);
        in.pressMouse(LEFT);
        ctx.waitTicks(20);
        this.check("radial.leftclick_recall", this.serverCompanionCount(ctx) < before,
                "loaded companions " + before + " -> " + this.serverCompanionCount(ctx) + " screen=" + this.screenName(ctx));
        this.escapeAll(ctx, in);
        this.shot(ctx, "after_recall");
        if (this.serverCompanionCount(ctx) > 0) {
            // Recall did not happen through the UI; use the radial's number key 0 so the gallery has room.
            this.openRadialAndRelease(ctx, in);
            in.pressKey(InputConstants.KEY_0);
            ctx.waitTicks(20);
            this.escapeAll(ctx, in);
            this.log("recall", "after key 0: loaded companions=" + this.serverCompanionCount(ctx));
        }
    }

    private void gallery(ClientGameTestContext ctx, TestInput in, TestServerContext server) {
        this.escapeAll(ctx, in);
        server.runCommand("tp @a 400 -60 400 -45 15");
        ctx.waitTicks(40);
        in.pressKey(o -> o.keyCommand);
        ctx.waitTicks(3);
        in.typeChars("nerocompanion gallery");
        ctx.waitTicks(2);
        in.pressKey(InputConstants.KEY_RETURN);
        ctx.waitTicks(100);
        int companions = this.serverCompanionCount(ctx);
        this.check("gallery.built", companions > 1, "loaded companions after /nerocompanion gallery=" + companions);
        in.lookAt(-45.0F, 15.0F);
        ctx.waitTicks(10);
        this.shot(ctx, "gallery_view_se");
        in.lookAt(-20.0F, 25.0F);
        ctx.waitTicks(5);
        this.shot(ctx, "gallery_view_2");
    }

    // =============================================================================================================
    // Input helpers (all through TestInput, i.e. Minecraft's own input handlers)
    // =============================================================================================================

    /** Holds the use key with the Remote until the radial opens (or 20 ticks pass). The key stays held. */
    private boolean openRadial(ClientGameTestContext ctx, TestInput in, boolean ignored) {
        this.escapeAll(ctx, in);
        in.lookAt(ctx.computeOnClient(mc -> mc.player.getYRot()), -30.0F);
        in.holdKey(o -> o.keyUse);
        return this.awaitScreen(ctx, RemoteRadialScreen.class, 20);
    }

    private void openRadialAndRelease(ClientGameTestContext ctx, TestInput in) {
        this.openRadial(ctx, in, false);
        in.releaseKey(o -> o.keyUse);
        ctx.waitTicks(2);
    }

    private void moveToRadial(ClientGameTestContext ctx, TestInput in, int wedge) {
        double rad = Math.toRadians(wedge * 45.0D);
        this.moveToGui(ctx, in, Math.sin(rad) * 28.0D, -Math.cos(rad) * 28.0D);
    }

    /** Moves the cursor to (dx, dy) GUI px from the centre of the screen. */
    private void moveToGui(ClientGameTestContext ctx, TestInput in, double dx, double dy) {
        int gw = ctx.computeOnClient(mc -> mc.getWindow().getGuiScaledWidth());
        int gh = ctx.computeOnClient(mc -> mc.getWindow().getGuiScaledHeight());
        this.cursorAtGui(ctx, in, gw / 2 + dx, gh / 2 + dy);
    }

    private void cursorAtGui(ClientGameTestContext ctx, TestInput in, double gx, double gy) {
        double sx = ctx.computeOnClient(mc -> (double) mc.getWindow().getScreenWidth() / mc.getWindow().getGuiScaledWidth());
        double sy = ctx.computeOnClient(mc -> (double) mc.getWindow().getScreenHeight() / mc.getWindow().getGuiScaledHeight());
        in.setCursorPos(gx * sx + sx / 2.0D, gy * sy + sy / 2.0D);
        ctx.waitTick();
    }

    /** Left-clicks at (px, py) inside a centred panel of {@code w x h} (the CompanionScreenBase layout rule). */
    private void clickPanel(ClientGameTestContext ctx, TestInput in, int w, int h, int px, int py) {
        int gw = ctx.computeOnClient(mc -> mc.getWindow().getGuiScaledWidth());
        int gh = ctx.computeOnClient(mc -> mc.getWindow().getGuiScaledHeight());
        int left = (gw - w) / 2;
        int top = Math.max(2, (gh - h) / 2);
        this.cursorAtGui(ctx, in, left + px, top + py);
        in.pressMouse(LEFT);
        ctx.waitTick();
    }

    private void escapeAll(ClientGameTestContext ctx, TestInput in) {
        for (int i = 0; i < 4 && !this.screenName(ctx).equals("none"); i++) {
            in.pressKey(InputConstants.KEY_ESCAPE);
            ctx.waitTicks(2);
        }
    }

    private void lookAtCompanion(ClientGameTestContext ctx, TestInput in) {
        float[] angles = ctx.computeOnClient(mc -> {
            Entity target = null;
            for (Entity e : mc.level.entitiesForRendering()) {
                if (e instanceof CompanionEntity) {
                    target = e;
                }
            }
            if (target == null) {
                return null;
            }
            Vec3 eye = mc.player.getEyePosition();
            Vec3 to = target.getBoundingBox().getCenter().subtract(eye);
            float yaw = (float) Math.toDegrees(Math.atan2(-to.x, to.z));
            float pitch = (float) -Math.toDegrees(Math.atan2(to.y, Math.sqrt(to.x * to.x + to.z * to.z)));
            return new float[] {yaw, pitch};
        });
        if (angles == null) {
            this.log("look", "no companion entity on the client");
            return;
        }
        in.lookAt(angles[0], angles[1]);
        ctx.waitTicks(3);
    }

    private boolean awaitScreen(ClientGameTestContext ctx, Class<? extends Screen> type, int ticks) {
        return this.await(ctx, mc -> type.isInstance(screen(mc)), ticks);
    }

    private boolean await(ClientGameTestContext ctx, Predicate<Minecraft> predicate, int ticks) {
        for (int i = 0; i < ticks; i++) {
            if (ctx.computeOnClient(predicate::test)) {
                return true;
            }
            ctx.waitTick();
        }
        return ctx.computeOnClient(predicate::test);
    }

    // =============================================================================================================
    // State probes (no personal data)
    // =============================================================================================================

    private static Screen screen(Minecraft mc) {
        return mc.gui.screen();
    }

    private boolean isScreen(ClientGameTestContext ctx, Class<? extends Screen> type) {
        return ctx.computeOnClient(mc -> type.isInstance(screen(mc)));
    }

    private String screenName(ClientGameTestContext ctx) {
        return ctx.computeOnClient(mc -> screen(mc) == null ? "none" : screen(mc).getClass().getSimpleName());
    }

    private String handItem(ClientGameTestContext ctx) {
        return ctx.computeOnClient(mc -> mc.player.getMainHandItem().getItem().getDescriptionId());
    }

    private String hudMode(ClientGameTestContext ctx) {
        return ctx.computeOnClient(mc -> {
            Payloads.HudEntry e = ClientState.highlightedEntry();
            return e == null ? "no-entry" : e.mode().name();
        });
    }

    private boolean radialAllows(ClientGameTestContext ctx, int mode) {
        return ctx.computeOnClient(mc -> {
            Payloads.HudEntry e = ClientState.highlightedEntry();
            return e != null && e.supports(za.co.neroland.nerocompanion.entity.CompanionMode.values()[mode]);
        });
    }

    private boolean screenAllows(ClientGameTestContext ctx, int mode) {
        return ctx.computeOnClient(mc -> {
            Payloads.CompanionSnapshot s = ClientState.companion();
            if (s == null) {
                return false;
            }
            int state = s.modeState(za.co.neroland.nerocompanion.entity.CompanionMode.values()[mode]).state();
            return state != Payloads.ModeState.TYPE_CANT && state != Payloads.ModeState.NEEDS_CHIP;
        });
    }

    private String snapMode(ClientGameTestContext ctx) {
        return ctx.computeOnClient(mc -> ClientState.companion() == null ? "no-snapshot" : ClientState.companion().mode().name());
    }

    private String look(ClientGameTestContext ctx, boolean hat) {
        return ctx.computeOnClient(mc -> {
            Payloads.CompanionSnapshot s = ClientState.companion();
            if (s == null) {
                return "no-snapshot";
            }
            return (hat ? s.hat() : s.skin()).map(Object::toString).orElse("none");
        });
    }

    private String unlocked(ClientGameTestContext ctx, boolean hats) {
        return ctx.computeOnClient(mc -> {
            Payloads.CompanionSnapshot s = ClientState.companion();
            return s == null ? "no-snapshot" : String.valueOf(hats ? s.unlockedHats() : s.unlockedSkins());
        });
    }

    private String serverLook(ClientGameTestContext ctx, boolean hat) {
        return this.server.computeOnServer(server -> {
            for (Entity e : server.overworld().getAllEntities()) {
                if (e instanceof CompanionEntity c) {
                    return (hat ? c.hat() : c.skin()).map(Object::toString).orElse("none");
                }
            }
            return "no-entity";
        });
    }

    private String serverMode() {
        return this.server.computeOnServer(server -> {
            for (Entity e : server.overworld().getAllEntities()) {
                if (e instanceof CompanionEntity c) {
                    return c.mode().name();
                }
            }
            return "no-entity";
        });
    }

    private int serverCompanionCount(ClientGameTestContext ctx) {
        return this.server.computeOnServer(server -> {
            int n = 0;
            for (Entity e : server.overworld().getAllEntities()) {
                if (e instanceof CompanionEntity) {
                    n++;
                }
            }
            return n;
        });
    }

    private int bayOccupied(ClientGameTestContext ctx) {
        return ctx.computeOnClient(mc -> {
            Payloads.BaySnapshot b = ClientState.bay();
            if (b == null) {
                return -1;
            }
            int n = 0;
            for (Payloads.BaySlotView v : b.slots()) {
                if (v.kind() != Payloads.BaySlotView.EMPTY) {
                    n++;
                }
            }
            return n;
        });
    }

    // =============================================================================================================
    // Report
    // =============================================================================================================

    private void section(String name, Runnable body) {
        this.log("section", "=== " + name);
        try {
            body.run();
        } catch (Throwable t) {
            this.report.add(String.format(Locale.ROOT, "ERROR   %-36s %s", name, t));
            LOG.error("[ui-audit] section {} threw", name, t);
        }
    }

    private void check(String step, boolean pass, String detail) {
        String line = String.format(Locale.ROOT, "%s %-36s %s", pass ? "PASS   " : "FAIL   ", step, detail);
        this.report.add(line);
        LOG.info("[ui-audit] {}", line);
    }

    private void log(String step, String detail) {
        String line = String.format(Locale.ROOT, "INFO    %-36s %s", step, detail);
        this.report.add(line);
        LOG.info("[ui-audit] {}", line);
    }

    private void shot(ClientGameTestContext ctx, String name) {
        this.shot++;
        String file = String.format(Locale.ROOT, "%02d_%s", this.shot, name);
        Path path = ctx.takeScreenshot(file);
        this.report.add(String.format(Locale.ROOT, "SHOT    %-36s %s", file, path.getFileName()));
    }

    private void writeReport() {
        Path dir = FabricLoader.getInstance().getGameDir().resolve("screenshots");
        try {
            Files.createDirectories(dir);
            Files.write(dir.resolve("ui-audit-report.txt"), this.report, StandardCharsets.UTF_8);
        } catch (IOException e) {
            LOG.error("[ui-audit] could not write the report", e);
        }
    }
}
