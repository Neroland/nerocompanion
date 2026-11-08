package za.co.neroland.nerocompanion;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import za.co.neroland.nerocompanion.config.NeroCompanionConfig;
import za.co.neroland.nerocompanion.economy.CompanionEconomy;
import za.co.neroland.nerocompanion.network.CompanionNetwork;
import za.co.neroland.nerocompanion.platform.Services;
import za.co.neroland.nerocompanion.progression.QuestHooks;
import za.co.neroland.nerocompanion.privacy.CompanionErasure;
import za.co.neroland.nerocompanion.recovery.Recovery;
import za.co.neroland.nerocompanion.salvage.Salvage;

import za.co.neroland.nerocompanion.charge.HandCrankChargerBlock;
import za.co.neroland.nerocompanion.chip.ChipRules;
import za.co.neroland.nerocompanion.fetch.FetchTargets;
import za.co.neroland.nerocompanion.hud.HudBoard;
import za.co.neroland.nerocompanion.network.ServerActions;
import za.co.neroland.nerocompanion.registry.ModBlockEntities;
import za.co.neroland.nerocompanion.registry.ModBlocks;
import za.co.neroland.nerocompanion.registry.ModCompanionContent;
import za.co.neroland.nerocompanion.registry.ModCreativeTab;
import za.co.neroland.nerocompanion.registry.ModMenuTypes;
import za.co.neroland.nerocompanion.scheduler.CompanionIndex;
import za.co.neroland.nerocompanion.registry.ModDataComponents;
import za.co.neroland.nerocompanion.registry.ModEntities;
import za.co.neroland.nerocompanion.registry.ModItems;
import za.co.neroland.nerocompanion.telemetry.NeroCompanionTelemetry;

/**
 * Loader-agnostic entry point for NeroCompanion. Each loader entry point
 * (Fabric / Forge / NeoForge) calls {@link #init()} once during mod
 * construction. This is a barebones skeleton — no content is registered yet;
 * add shared blocks, items and systems here and reach loader-specific
 * behaviour through a platform seam.
 */
public final class NeroCompanionCommon {

    public static final String MOD_ID = "nerocompanion";
    public static final Logger LOGGER = LoggerFactory.getLogger("NeroCompanion");

    private NeroCompanionCommon() {
    }

    /** Called once per loader during mod construction. The numbered order is a contract. */
    public static void init() {
        LOGGER.info("[NeroCompanion] common init");

        // 0. Platform seams, resolved here during construction and never lazily on a tick path — a
        //    late ServiceLoader read can throw ServiceConfigurationError out of gameplay code.
        Services.init();

        // 1. Config first: everything below reads it, including telemetry's opt-out flag.
        NeroCompanionConfig.init();

        // 2. Anonymous, NeroCompanion-only crash reporting. Must follow the config registration and
        //    precede the rest of init so early failures are still reported. On by default (opt-out via
        //    telemetryEnabled=false); stays inert while the build's DSN is the placeholder
        //    (see NeroCompanionTelemetry's PLACEHOLDER_DSN guard).
        NeroCompanionTelemetry.init();

        // 3. Registries: entities + attributes, blocks, items, block entities, menus, sounds, particles,
        //    loot modifiers (each stage adds its own), then the creative tab that lists them. Data components
        //    first (items reference them), entity types before their attributes (Fabric applies both eagerly).
        ModDataComponents.init();
        ModEntities.init();
        ModEntities.registerEntitySupport();
        ModBlocks.init();
        ModItems.init();
        ModBlockEntities.init();
        ModMenuTypes.init();
        za.co.neroland.nerocompanion.registry.ModSounds.init();
        za.co.neroland.nerocompanion.registry.ModParticles.init();
        ModCreativeTab.init();
        // Per-type work goals, job codecs, Remote block actions and personality hooks (Stage 3 packages).
        ModCompanionContent.init();

        // 4. Data stores and their one PlayerDataErasure registration: Recovery Ledger, Salvage Hold, Capsule Vault,
        //    incubator store (each backed up after erasing), NeroCreatures pets and session memory; the player's
        //    loaded and parked companions are unbound to ownerless Capsules first.
        CompanionErasure.init();

        // 5. Compat detection. Every sibling is optional and reached only through its façade.
        //    The NeroEconomy bridge claims here, before the first server starts. Authority providers (NeroColonies,
        //    NeroSecurity, NeroFactions) are registered only by a façade whose sibling is loaded (decision 020).
        CompanionEconomy.init();
        za.co.neroland.nerocompanion.compat.nerocolonies.NeroColoniesCompat.init();
        za.co.neroland.nerocompanion.compat.nerosecurity.NeroSecurityCompat.init();
        za.co.neroland.nerocompanion.compat.nerofactions.NeroFactionsCompat.init();
        za.co.neroland.nerocompanion.compat.nerologistics.NeroLogisticsCompat.init();
        za.co.neroland.nerocompanion.compat.nerospace.NerospaceCompat.init();

        // 6. Network: every nerocompanion:main payload is declared before a loader builds its channel.
        CompanionNetwork.init();

        // 7. Quest hook listener (Core progression events only; no quest-mod dependency). Each loader feeds
        //    QuestHooks.tick from the server tick so the listener can find the online player to notify.
        QuestHooks.init();
        //    The milestone gates' bond listener (the other milestones are opened inline by the code that earns them).
        za.co.neroland.nerocompanion.progression.CompanionProgress.init();

        // 8. NeroLink module, last and isolated: a link failure must never stop the mod loading (Stage 8).
        try {
            initLink();
        } catch (RuntimeException | LinkageError failure) {
            LOGGER.warn("[NeroCompanion] NeroLink module not registered ({})", failure.getClass().getName());
        }
    }

    /** Each loader calls this from its server-stopped event: release every static reference to that server. */
    public static void onServerStopped() {
        za.co.neroland.nerocompanion.entity.type.CompanionTypes.serverStopped();
        CompanionIndex.serverStopped();
        ChipRules.serverStopped();
        HudBoard.serverStopped();
        FetchTargets.serverStopped();
        HandCrankChargerBlock.serverStopped();
        ServerActions.serverStopped();
        za.co.neroland.nerocompanion.compat.creatures.CreaturePets.clear();
        za.co.neroland.nerocompanion.incubation.Incubators.serverStopped();
        Salvage.serverStopped();
        Recovery.serverStopped();
        za.co.neroland.nerocompanion.privacy.CompanionPrivacy.COOLDOWN.clear();
        za.co.neroland.nerocompanion.mining.BotFocus.serverStopped();
        za.co.neroland.nerocompanion.mining.MiningCorners.serverStopped();
        za.co.neroland.nerocompanion.look.LookService.serverStopped();
        za.co.neroland.nerocompanion.command.GalleryCommands.serverStopped();
        za.co.neroland.nerocompanion.link.CompanionLinkModule.serverStopped();
    }

    /**
     * Each loader calls this from its server tick (post): the HUD strip's once-a-second sync, fresh deaths for
     * salvage, and the Recovery Ledger's lost and retention sweeps.
     */
    public static void onServerTick(net.minecraft.server.MinecraftServer server) {
        HudBoard.forServer(server).tick(server);
        Salvage.tick(server);
        Recovery.tick(server);
        za.co.neroland.nerocompanion.look.LookService.tick(server);
        za.co.neroland.nerocompanion.link.CompanionLinkModule.tick(server);
    }

    /** Each loader calls this when a player joins: their own HUD list. */
    public static void onPlayerJoin(net.minecraft.server.level.ServerPlayer player) {
        net.minecraft.server.MinecraftServer server = player.level().getServer();
        if (server != null) {
            HudBoard.forServer(server).send(player);
            // Time offline never counts toward "lost"; the owner's companions wake at their next evaluation.
            Recovery.ownerJoined(player);
            // The privacy activity clock (inactive-owner retention) restarts for players NeroCompanion knows.
            za.co.neroland.nerocompanion.privacy.CompanionPrivacy.playerJoined(player);
            // The public skin and hat catalogue (definitions only), so this client can draw every companion's look.
            za.co.neroland.nerocompanion.look.LookService.sendCatalogue(player);
        }
    }

    private static void initLink() {
        za.co.neroland.nerocompanion.link.CompanionLinkModule.init();
    }
}
