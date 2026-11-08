package za.co.neroland.nerocompanion.authority;

import java.util.List;
import java.util.Set;
import java.util.concurrent.ConcurrentHashMap;
import java.util.function.Consumer;

/**
 * The owner-authority decision chain, pure: first denial wins, every step must allow.
 *
 * <ol>
 *   <li>Owner online and in the bot's dimension (every action).</li>
 *   <li>Vanilla, as the owner: game mode, build permission, spawn protection, world border, {@code mayInteract},
 *       unbreakable blocks, {@code #nerocompanion:unmineable}, block entities, container locks; and for attacks
 *       the target rules (never the owner, allies, anything owned, villagers or golems; players only with
 *       {@code pvpBots} and the owner's own PvP permission, never creative/spectator players).</li>
 *   <li>Core progression gate for the current mode, if the type names one.</li>
 *   <li>Authority providers - deny only; a throwing provider denies.</li>
 * </ol>
 */
public final class AuthorityChain {

    private static final Set<String> WARNED = ConcurrentHashMap.newKeySet();

    private AuthorityChain() {
    }

    public static Decision decide(AuthorityAction action, AuthorityFacts facts, AuthorityRules rules,
            List<AuthorityProvider> providers, AuthorityQuery query, Consumer<String> warnOnce) {
        Decision vanilla = vanilla(action, facts, rules);
        if (vanilla.denied()) {
            return vanilla;
        }
        if (!facts.gateOpen()) {
            return Decision.deny("gate");
        }
        for (AuthorityProvider provider : providers) {
            Decision answer;
            try {
                answer = provider.check(query);
            } catch (RuntimeException | LinkageError failure) {
                String key = provider.getClass().getName();
                if (WARNED.add(key)) {
                    warnOnce.accept(key + " failed (" + failure.getClass().getSimpleName() + "); denying");
                }
                return Decision.deny("provider_error");
            }
            if (answer == null || answer.denied()) {
                return answer == null ? Decision.deny("provider") : answer;
            }
        }
        return Decision.ALLOW;
    }

    static Decision vanilla(AuthorityAction action, AuthorityFacts facts, AuthorityRules rules) {
        if (!facts.ownerOnline()) {
            return Decision.deny("owner_offline");
        }
        if (!facts.ownerSameDimension()) {
            return Decision.deny("other_dimension");
        }
        if (facts.ownerMode() == AuthorityFacts.OwnerMode.SPECTATOR) {
            return Decision.deny("spectator");
        }
        return switch (action) {
            case BREAK -> breakRules(facts, rules);
            case PLACE -> placeRules(facts);
            case OPEN -> openRules(facts);
            case ATTACK -> attackRules(facts, rules);
            case ENTER -> facts.withinWorldBorder() ? Decision.ALLOW : Decision.deny("world_border");
        };
    }

    private static Decision buildRules(AuthorityFacts facts) {
        if (facts.ownerMode() == AuthorityFacts.OwnerMode.ADVENTURE || !facts.ownerMayBuild()) {
            return Decision.deny("cannot_build");
        }
        if (facts.spawnProtected()) {
            return Decision.deny("spawn_protection");
        }
        if (!facts.withinWorldBorder()) {
            return Decision.deny("world_border");
        }
        if (!facts.ownerMayInteract()) {
            return Decision.deny("no_interact");
        }
        return Decision.ALLOW;
    }

    private static Decision breakRules(AuthorityFacts facts, AuthorityRules rules) {
        if (!rules.miningEnabled()) {
            return Decision.deny("mining_disabled");
        }
        Decision build = buildRules(facts);
        if (build.denied()) {
            return build;
        }
        if (facts.destroySpeed() < 0.0F) {
            return Decision.deny("unbreakable");
        }
        if (facts.unmineable()) {
            return Decision.deny("unmineable");
        }
        if (facts.hasBlockEntity()) {
            return Decision.deny("block_entity");
        }
        return Decision.ALLOW;
    }

    private static Decision placeRules(AuthorityFacts facts) {
        return buildRules(facts);
    }

    private static Decision openRules(AuthorityFacts facts) {
        if (!facts.withinWorldBorder()) {
            return Decision.deny("world_border");
        }
        if (!facts.ownerMayInteract()) {
            return Decision.deny("no_interact");
        }
        if (facts.containerLocked()) {
            return Decision.deny("locked");
        }
        return Decision.ALLOW;
    }

    private static Decision attackRules(AuthorityFacts facts, AuthorityRules rules) {
        if (!rules.combatEnabled()) {
            return Decision.deny("combat_disabled");
        }
        return switch (facts.targetKind()) {
            case NONE -> Decision.deny("no_target");
            case OWNER -> Decision.deny("target_owner");
            case ALLY -> Decision.deny("target_ally");
            case OWNED_BY_SOMEONE -> Decision.deny("target_owned");
            case AGGRESSOR_COMPANION -> {
                // 002 addendum: with pvpBots on, a companion that attacked the owner or the bot loses its companion
                // immunity for the defender - and only for it; every PvP condition is then judged on its owner.
                if (!rules.pvpBots()) {
                    yield Decision.deny("target_owned");
                }
                if (facts.targetPlayerInvulnerable()) {
                    yield Decision.deny("target_invulnerable");
                }
                if (!facts.ownerCanHarmTarget()) {
                    yield Decision.deny("pvp_off");
                }
                yield Decision.ALLOW;
            }
            case PROTECTED_MOB -> Decision.deny("target_protected");
            case PLAYER -> {
                if (!rules.pvpBots()) {
                    yield Decision.deny("pvp_off");
                }
                if (facts.targetPlayerInvulnerable()) {
                    yield Decision.deny("target_invulnerable");
                }
                if (!facts.ownerCanHarmTarget()) {
                    yield Decision.deny("pvp_off");
                }
                yield Decision.ALLOW;
            }
            case OTHER -> Decision.ALLOW;
        };
    }
}
