package za.co.neroland.nerocompanion.authority;

/**
 * The answer to "may this companion do that?". {@code reasonKey} is a lang key, never text, and never carries
 * player data, so a denial is safe to log or show.
 */
public record Decision(boolean allowed, String reasonKey) {

    public static final Decision ALLOW = new Decision(true, "authority.nerocompanion.allowed");

    public static Decision deny(String reason) {
        return new Decision(false, "authority.nerocompanion." + reason);
    }

    public boolean denied() {
        return !this.allowed;
    }
}
