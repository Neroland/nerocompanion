package za.co.neroland.nerocompanion.authority;

/**
 * The configurable part of the authority rules (server-authoritative config. Everything else in
 * the chain is fixed.
 */
public record AuthorityRules(boolean miningEnabled, boolean combatEnabled, boolean pvpBots) {

    public static final AuthorityRules DEFAULTS = new AuthorityRules(true, true, false);
}
