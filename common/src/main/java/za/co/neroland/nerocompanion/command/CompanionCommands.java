package za.co.neroland.nerocompanion.command;

import java.util.Locale;
import java.util.function.Predicate;

import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.UUID;
import java.util.concurrent.ConcurrentHashMap;

import com.mojang.brigadier.CommandDispatcher;
import com.mojang.brigadier.arguments.IntegerArgumentType;
import com.mojang.brigadier.arguments.StringArgumentType;
import com.mojang.brigadier.builder.LiteralArgumentBuilder;
import com.mojang.brigadier.builder.RequiredArgumentBuilder;

import net.minecraft.commands.CommandSourceStack;
import net.minecraft.commands.Commands;
import net.minecraft.core.BlockPos;
import net.minecraft.network.chat.Component;
import net.minecraft.resources.Identifier;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.level.entity.EntityTypeTest;

import za.co.neroland.nerocompanion.NeroCompanionCommon;
import za.co.neroland.nerocompanion.budget.BudgetProbe;
import za.co.neroland.nerocompanion.config.NeroCompanionConfig;
import za.co.neroland.nerocompanion.entity.CompanionEntity;
import za.co.neroland.nerocompanion.entity.type.CompanionType;
import za.co.neroland.nerocompanion.entity.type.CompanionTypes;
import za.co.neroland.nerocompanion.entity.type.ValidationIssue;
import za.co.neroland.nerocompanion.item.CompanionDeployer;
import za.co.neroland.nerocompanion.network.Feedbacks;
import za.co.neroland.nerocompanion.network.payload.Payloads;
import za.co.neroland.nerocompanion.privacy.CompanionExport;
import za.co.neroland.nerocompanion.privacy.CompanionPrivacy;
import za.co.neroland.nerocompanion.privacy.PrivacyPrefs;
import za.co.neroland.nerocompanion.recovery.LedgerEntry;
import za.co.neroland.nerocompanion.recovery.RecoverMatch;
import za.co.neroland.nerocompanion.recovery.Recovery;
import za.co.neroland.nerocompanion.registry.ModEntities;

/**
 * The {@code /nscomp} command root. Subcommands are added by the stages that need them:
 * <ul>
 *   <li>{@code /nscomp export} (every player) - writes everything NeroCompanion holds about the caller to one JSON
 *       file in the world folder (rate-limited by {@code privacy.exportCooldownSeconds}).</li>
 *   <li>{@code /nscomp privacy [names|chatter|push] [on|off]} (every player) - shows or changes the caller's own
 *       privacy settings: names hidden from other players, chatter, push notifications.</li>
 *   <li>{@code /nscomp budget} (operators) - the companion tick cost measured by {@link BudgetProbe}.</li>
 *   <li>{@code /nscomp types} (operators) - loaded companion types and the validation issues of the last load.</li>
 *   <li>{@code /nscomp debug spawn <n> [type|mixed]} / {@code /nscomp debug clear} (operators in creative) - the
 *       tick-cost probe world: spawns companions owned by the caller (bypassing the caps; personal drones by
 *       default, {@code mixed} cycles all seven types), and removes the ones it spawned again.</li>
 *   <li>{@code /nscomp gallery [clear]} (operators in creative, nobody else within 96 blocks) - the showcase stage
 *       with every type, skin and hat ({@link GalleryCommands}).</li>
 *   <li>{@code /nscomp recover [name] [confirm]} (every player) - reclaims one of the caller's own lost companions as
 *       a Capsule (by name or type; with no name: the only one, or a list). Same rules and cost as a Bay's Reclaim;
 *       when a cost applies it is stated first and the command needs {@code confirm}.</li>
 *   <li>{@code /nscomp admin release <player> <name|type|id>} (operators) - forces a companion that is stuck in the
 *       ledger (a parked companion whose Bay is gone, a Capsule nobody has) to lost, so its owner can reclaim it.
 *       {@code player} is an online player's name or a UUID.</li>
 * </ul>
 */
public final class CompanionCommands {

    public static final String ROOT = "nscomp";

    private CompanionCommands() { }

    public static void register(CommandDispatcher<CommandSourceStack> dispatcher) {
        dispatcher.register(LiteralArgumentBuilder.<CommandSourceStack>literal(ROOT)
                .then(LiteralArgumentBuilder.<CommandSourceStack>literal("export")
                        .executes(context -> export(context.getSource())))
                .then(privacyTree())
                .then(LiteralArgumentBuilder.<CommandSourceStack>literal("recover")
                        .executes(context -> recover(context.getSource(), ""))
                        .then(RequiredArgumentBuilder.<CommandSourceStack, String>argument("name",
                                        StringArgumentType.greedyString())
                                .executes(context -> recover(context.getSource(),
                                        StringArgumentType.getString(context, "name")))))
                .then(LiteralArgumentBuilder.<CommandSourceStack>literal("admin")
                        .requires(operator())
                        .then(LiteralArgumentBuilder.<CommandSourceStack>literal("release")
                                .then(RequiredArgumentBuilder.<CommandSourceStack, String>argument("player",
                                                StringArgumentType.word())
                                        .then(RequiredArgumentBuilder.<CommandSourceStack, String>argument("companion",
                                                        StringArgumentType.greedyString())
                                                .executes(context -> adminRelease(context.getSource(),
                                                        StringArgumentType.getString(context, "player"),
                                                        StringArgumentType.getString(context, "companion")))))))
                .then(LiteralArgumentBuilder.<CommandSourceStack>literal("budget")
                        .requires(operator())
                        .executes(context -> budget(context.getSource())))
                .then(LiteralArgumentBuilder.<CommandSourceStack>literal("gallery")
                        .requires(operator())
                        .executes(context -> GalleryCommands.build(context.getSource()))
                        .then(LiteralArgumentBuilder.<CommandSourceStack>literal("clear")
                                .executes(context -> GalleryCommands.clear(context.getSource()))))
                .then(LiteralArgumentBuilder.<CommandSourceStack>literal("types")
                        .requires(operator())
                        .executes(context -> types(context.getSource())))
                .then(LiteralArgumentBuilder.<CommandSourceStack>literal("debug")
                        .requires(operator())
                        .then(LiteralArgumentBuilder.<CommandSourceStack>literal("spawn")
                                .then(RequiredArgumentBuilder.<CommandSourceStack, Integer>argument("count",
                                                IntegerArgumentType.integer(1, 256))
                                        .executes(context -> debugSpawn(context.getSource(),
                                                IntegerArgumentType.getInteger(context, "count"), "personal_drone"))
                                        .then(RequiredArgumentBuilder.<CommandSourceStack, String>argument("type",
                                                        StringArgumentType.word())
                                                .suggests((context, builder) -> {
                                                    builder.suggest(MIXED);
                                                    ModEntities.COMPANION_TYPES.forEach(t -> builder.suggest(t.getPath()));
                                                    return builder.buildFuture();
                                                })
                                                .executes(context -> debugSpawn(context.getSource(),
                                                        IntegerArgumentType.getInteger(context, "count"),
                                                        StringArgumentType.getString(context, "type"))))))
                        .then(LiteralArgumentBuilder.<CommandSourceStack>literal("clear")
                                .executes(context -> debugClear(context.getSource())))));
    }

    /** {@code /nscomp privacy [names|chatter|push] [on|off]} - every player, for themselves. */
    private static LiteralArgumentBuilder<CommandSourceStack> privacyTree() {
        LiteralArgumentBuilder<CommandSourceStack> privacy = LiteralArgumentBuilder.<CommandSourceStack>literal(
                "privacy").executes(context -> privacyStatus(context.getSource()));
        for (PrivacyPrefs.Setting setting : PrivacyPrefs.Setting.values()) {
            privacy.then(LiteralArgumentBuilder.<CommandSourceStack>literal(setting.id())
                    .executes(context -> privacyStatus(context.getSource()))
                    .then(LiteralArgumentBuilder.<CommandSourceStack>literal("on")
                            .executes(context -> privacySet(context.getSource(), setting, false)))
                    .then(LiteralArgumentBuilder.<CommandSourceStack>literal("off")
                            .executes(context -> privacySet(context.getSource(), setting, true))));
        }
        return privacy;
    }

    private static int privacyStatus(CommandSourceStack source) {
        ServerPlayer player = source.getPlayer();
        if (player == null) {
            source.sendFailure(Component.translatable("command.nerocompanion.players_only"));
            return 0;
        }
        PrivacyPrefs prefs = CompanionPrivacy.prefs(source.getServer());
        for (PrivacyPrefs.Setting setting : PrivacyPrefs.Setting.values()) {
            boolean off = prefs.isOff(player.getUUID(), setting);
            source.sendSuccess(() -> Component.translatable("command.nerocompanion.privacy.status",
                    Component.translatable("command.nerocompanion.privacy.setting." + setting.id()),
                    Component.translatable(off ? "command.nerocompanion.privacy.off"
                            : "command.nerocompanion.privacy.on")), false);
        }
        if (!NeroCompanionConfig.allowNames()) {
            source.sendSuccess(() -> Component.translatable("command.nerocompanion.privacy.server_hides_names"),
                    false);
        }
        return 1;
    }

    private static int privacySet(CommandSourceStack source, PrivacyPrefs.Setting setting, boolean off) {
        ServerPlayer player = source.getPlayer();
        if (player == null) {
            source.sendFailure(Component.translatable("command.nerocompanion.players_only"));
            return 0;
        }
        CompanionPrivacy.set(source.getServer(), player.getUUID(), setting, off);
        source.sendSuccess(() -> Component.translatable("command.nerocompanion.privacy.set",
                Component.translatable("command.nerocompanion.privacy.setting." + setting.id()),
                Component.translatable(off ? "command.nerocompanion.privacy.off"
                        : "command.nerocompanion.privacy.on")), false);
        return 1;
    }

    /**
     * {@code /nscomp export}: the caller's own data, one file, rate-limited. The reply tells only the caller the path
     * of their own file (named after their own UUID); nothing is logged.
     */
    private static int export(CommandSourceStack source) {
        ServerPlayer player = source.getPlayer();
        if (player == null) {
            source.sendFailure(Component.translatable("command.nerocompanion.players_only"));
            return 0;
        }
        CompanionPrivacy.ExportResult result = CompanionPrivacy.export(source.getServer(), player.getUUID());
        if (result instanceof CompanionPrivacy.ExportResult.Written written) {
            source.sendSuccess(() -> Component.translatable("command.nerocompanion.export.done",
                    written.relativePath(), Integer.toString(CompanionExport.RETENTION_DAYS)), false);
            return 1;
        }
        if (result instanceof CompanionPrivacy.ExportResult.Cooldown cooldown) {
            source.sendFailure(Component.translatable("command.nerocompanion.export.cooldown",
                    Long.toString(cooldown.secondsLeft())));
            return 0;
        }
        source.sendFailure(Component.translatable("command.nerocompanion.export.failed"));
        return 0;
    }

    private static int types(CommandSourceStack source) {
        Map<Identifier, CompanionType> types = CompanionTypes.forServer(source.getServer());
        List<ValidationIssue> issues = CompanionTypes.validationIssues();
        source.sendSuccess(() -> Component.translatable("command.nerocompanion.types.header", types.size(),
                issues.size()), false);
        for (CompanionType type : types.values()) {
            source.sendSuccess(() -> Component.translatable("command.nerocompanion.types.entry",
                    type.id().toString(), type.locomotion().id(), type.energy().capacity(),
                    type.modes().size(), type.chipSlots()), false);
        }
        for (ValidationIssue issue : issues) {
            source.sendSuccess(() -> Component.translatable("command.nerocompanion.types.issue", issue.describe()),
                    false);
        }
        return types.size();
    }

    /** The debug commands need a player in creative (they spawn and remove companions owned by the caller). */
    private static ServerPlayer creativePlayer(CommandSourceStack source) {
        ServerPlayer player = source.getPlayer();
        if (player == null || !player.isCreative()) {
            source.sendFailure(Component.translatable("command.nerocompanion.debug.creative_only"));
            return null;
        }
        return player;
    }

    /** Entity UUIDs of companions the debug command spawned this server run (what {@code debug clear} removes). */
    private static final Set<UUID> DEBUG_SPAWNED = ConcurrentHashMap.newKeySet();

    /** The {@code debug spawn} type word that cycles every companion type. */
    static final String MIXED = "mixed";

    private static int debugSpawn(CommandSourceStack source, int requested, String typeWord) {
        ServerPlayer player = creativePlayer(source);
        if (player == null) {
            return 0;
        }
        List<Identifier> types;
        if (MIXED.equals(typeWord)) {
            types = ModEntities.COMPANION_TYPES;
        } else {
            Identifier type = Identifier.fromNamespaceAndPath(NeroCompanionCommon.MOD_ID, typeWord);
            if (!ModEntities.COMPANION_TYPES.contains(type)) {
                source.sendFailure(Component.translatable("command.nerocompanion.debug.unknown_type", typeWord));
                return 0;
            }
            types = List.of(type);
        }
        int count = Math.min(requested, NeroCompanionConfig.debugMaxSpawn());
        ServerLevel level = player.level();
        int spawned = 0;
        for (int i = 0; i < count; i++) {
            BlockPos pos = player.blockPosition().offset((i % 8) - 4, 2 + (i / 64), ((i / 8) % 8) - 4);
            Identifier type = types.get(i % types.size());
            CompanionEntity companion = CompanionDeployer.deploy(level, player, pos,
                    CompanionDeployer.freshRecord(level, type, player), true);
            if (companion != null) {
                DEBUG_SPAWNED.add(companion.getUUID());
                spawned++;
            }
        }
        int done = spawned;
        source.sendSuccess(() -> Component.translatable("command.nerocompanion.debug.spawned", done), false);
        return done;
    }

    private static int debugClear(CommandSourceStack source) {
        ServerPlayer player = creativePlayer(source);
        if (player == null) {
            return 0;
        }
        ServerLevel level = player.level();
        List<? extends CompanionEntity> mine = level.getEntities(EntityTypeTest.forClass(CompanionEntity.class),
                companion -> companion.isOwnedBy(player) && DEBUG_SPAWNED.contains(companion.getUUID()));
        for (CompanionEntity companion : mine) {
            DEBUG_SPAWNED.remove(companion.getUUID());
            if (companion.companionId() != null) {
                // Removed on purpose: not lost, not reclaimable.
                Recovery.forget(source.getServer(), companion.companionId());
            }
            companion.discard();
        }
        int removed = mine.size();
        source.sendSuccess(() -> Component.translatable("command.nerocompanion.debug.cleared", removed), false);
        return removed;
    }

    /**
     * {@code /nscomp recover [name]}: the caller's own lost companions only. Matches the name (case-insensitive), else
     * the type path; with no name and one lost companion, that one; otherwise lists them (type and name, never where).
     */
    private static int recover(CommandSourceStack source, String rawQuery) {
        ServerPlayer player = source.getPlayer();
        if (player == null) {
            source.sendFailure(Component.translatable("command.nerocompanion.recover.players_only"));
            return 0;
        }
        String query = rawQuery.strip();
        boolean confirmed = query.equals("confirm") || query.endsWith(" confirm");
        if (confirmed) {
            query = query.substring(0, query.length() - "confirm".length()).strip();
        }
        List<LedgerEntry> lost = Recovery.book(source.getServer()).lostOf(player.getUUID());
        if (lost.isEmpty()) {
            source.sendFailure(Component.translatable("command.nerocompanion.recover.none"));
            return 0;
        }
        long cost = Recovery.reclaimCost();
        List<LedgerEntry> matches = RecoverMatch.select(lost, query);
        if (matches.size() != 1) {
            source.sendSuccess(() -> Component.translatable(matches.isEmpty()
                    ? "command.nerocompanion.recover.no_match" : "command.nerocompanion.recover.which"), false);
            for (LedgerEntry entry : matches.isEmpty() ? lost : matches) {
                source.sendSuccess(() -> entryLine(entry), false);
            }
            if (cost > 0L) {
                source.sendSuccess(() -> Component.translatable("command.nerocompanion.recover.cost", cost), false);
            }
            return 0;
        }
        if (cost > 0L && !confirmed) {
            // Say what it costs before charging.
            LedgerEntry entry = matches.get(0);
            String again = query.isEmpty() ? "confirm" : query + " confirm";
            source.sendSuccess(() -> Component.translatable("command.nerocompanion.recover.confirm", entryLine(entry),
                    cost, again), false);
            return 0;
        }
        Payloads.Feedback result = Recovery.reclaim(player, matches.get(0).companionId());
        if (result.ok()) {
            source.sendSuccess(() -> Feedbacks.component(result), false);
            return 1;
        }
        source.sendFailure(Feedbacks.component(result));
        return 0;
    }

    private static Component entryLine(LedgerEntry entry) {
        return Component.translatable("command.nerocompanion.recover.entry",
                Component.translatable("entity." + entry.snapshot().type().getNamespace() + "."
                        + entry.snapshot().type().getPath()),
                entry.snapshot().name().orElse("-"));
    }

    /** {@code /nscomp admin release <player> <name|type|id>}: one of that player's companions, any state, to LOST. */
    private static int adminRelease(CommandSourceStack source, String playerWord, String companionWord) {
        UUID owner = null;
        try {
            owner = UUID.fromString(playerWord);
        } catch (IllegalArgumentException notUuid) {
            ServerPlayer online = source.getServer().getPlayerList().getPlayerByName(playerWord);
            if (online != null) {
                owner = online.getUUID();
            }
        }
        if (owner == null) {
            source.sendFailure(Component.translatable("command.nerocompanion.admin.no_player", playerWord));
            return 0;
        }
        List<LedgerEntry> candidates = new java.util.ArrayList<>();
        for (LedgerEntry entry : Recovery.book(source.getServer()).entriesOf(owner)) {
            if (!entry.is(LedgerEntry.State.LOST)) {
                candidates.add(entry);
            }
        }
        List<LedgerEntry> matches = RecoverMatch.select(candidates, companionWord);
        if (matches.isEmpty()) {
            for (LedgerEntry entry : candidates) {
                if (entry.companionId().toString().equalsIgnoreCase(companionWord.strip())) {
                    matches = List.of(entry);
                }
            }
        }
        if (matches.size() != 1) {
            source.sendFailure(Component.translatable(matches.isEmpty() ? "command.nerocompanion.admin.no_match"
                    : "command.nerocompanion.admin.ambiguous"));
            return 0;
        }
        LedgerEntry entry = matches.get(0);
        if (!Recovery.release(source.getServer(), entry.companionId())) {
            source.sendFailure(Component.translatable("command.nerocompanion.admin.no_match"));
            return 0;
        }
        source.sendSuccess(() -> Component.translatable("command.nerocompanion.admin.released", entryLine(entry),
                entry.state().id()), false); // not broadcast: the line carries a player-written name
        return 1;
    }

    private static int budget(CommandSourceStack source) {
        BudgetProbe.Report report = BudgetProbe.lastReport();
        source.sendSuccess(() -> Component.translatable("command.nerocompanion.budget",
                String.format(Locale.ROOT, "%.3f", report.totalMsPerTick()),
                String.format(Locale.ROOT, "%.4f", report.msPerCompanionTick()),
                report.companionTicks(), report.serverTicks()), false);
        return 1;
    }

    static Predicate<CommandSourceStack> operator() {
        //? if >=1.21.11 {
        return Commands.hasPermission(Commands.LEVEL_GAMEMASTERS);
        //?} else {
        /*return source -> source.hasPermission(Commands.LEVEL_GAMEMASTERS);
        *///?}
    }
}
