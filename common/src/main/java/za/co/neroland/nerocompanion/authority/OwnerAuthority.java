package za.co.neroland.nerocompanion.authority;

import java.util.List;
import java.util.concurrent.CopyOnWriteArrayList;

import net.minecraft.core.BlockPos;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.level.block.state.BlockState;

import za.co.neroland.nerocompanion.NeroCompanionCommon;
import za.co.neroland.nerocompanion.config.NeroCompanionConfig;
import za.co.neroland.nerocompanion.entity.CompanionEntity;

/**
 * A bot acts with its owner's authority - exactly, never more. Server only. Every check is
 * evaluated as the owner resolved from the companion's stored owner UUID; the chain itself is
 * {@link AuthorityChain}, and claim/access systems plug in as {@link AuthorityProvider}s (Stage 6 facades).
 */
public final class OwnerAuthority {

    private static final List<AuthorityProvider> PROVIDERS = new CopyOnWriteArrayList<>();

    private OwnerAuthority() {
    }

    /** Adds a provider. Compat facades call this only when their sibling mod is loaded. */
    public static void registerProvider(AuthorityProvider provider) {
        PROVIDERS.add(provider);
    }

    /** How many providers are registered (0 on a Core-only server; the façade tests read it). */
    public static int providerCount() {
        return PROVIDERS.size();
    }

    public static Decision mayBreak(CompanionEntity bot, ServerLevel level, BlockPos pos) {
        return decide(AuthorityAction.BREAK, bot, level, pos, null);
    }

    /** Placing a block (the Lamp Chip's light); {@code state} is what would be placed. */
    public static Decision mayPlace(CompanionEntity bot, ServerLevel level, BlockPos pos, BlockState state) {
        return decide(AuthorityAction.PLACE, bot, level, pos, null);
    }

    public static Decision mayOpen(CompanionEntity bot, ServerLevel level, BlockPos containerPos) {
        return decide(AuthorityAction.OPEN, bot, level, containerPos, null);
    }

    public static Decision mayAttack(CompanionEntity bot, Entity target) {
        if (!(bot.level() instanceof ServerLevel level)) {
            return Decision.deny("client");
        }
        return decide(AuthorityAction.ATTACK, bot, level, target.blockPosition(), target);
    }

    public static Decision mayEnter(CompanionEntity bot, ServerLevel level, BlockPos pos) {
        return decide(AuthorityAction.ENTER, bot, level, pos, null);
    }

    /**
     * Whether {@code player} themself may use the block at {@code pos} (setting a Scrap Egg on a Pad or Bay): not a
     * spectator or in adventure mode, not spawn-protected, inside the world border, and every claim provider allows
     * them to place there. A provider that throws denies, as for bots.
     */
    public static Decision playerMayUse(ServerPlayer player, ServerLevel level, BlockPos pos) {
        if (player.isSpectator() || !player.mayBuild()) {
            return Decision.deny("cannot_build");
        }
        if (level.getServer().isUnderSpawnProtection(level, pos, player)) {
            return Decision.deny("spawn_protection");
        }
        if (!level.getWorldBorder().isWithinBounds(pos) || !level.mayInteract(player, pos)) {
            return Decision.deny("no_interact");
        }
        AuthorityQuery query = new AuthorityQuery(AuthorityAction.PLACE, player.getUUID(), level, pos, null);
        for (AuthorityProvider provider : PROVIDERS) {
            Decision answer;
            try {
                answer = provider.check(query);
            } catch (RuntimeException | LinkageError failure) {
                return Decision.deny("provider_error");
            }
            if (answer == null || answer.denied()) {
                return answer == null ? Decision.deny("provider") : answer;
            }
        }
        return Decision.ALLOW;
    }

    private static Decision decide(AuthorityAction action, CompanionEntity bot, ServerLevel level, BlockPos pos,
            Entity target) {
        if (bot.ownerId() == null) {
            return Decision.deny("owner_offline");
        }
        AuthorityFacts facts = new VanillaAuthorityFacts(bot, level, pos, target);
        AuthorityQuery query = new AuthorityQuery(action, bot.ownerId(), level, pos, target);
        return AuthorityChain.decide(action, facts, NeroCompanionConfig.authorityRules(), PROVIDERS, query,
                message -> NeroCompanionCommon.LOGGER.warn("[NeroCompanion] authority provider {}", message));
    }
}
