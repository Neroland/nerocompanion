package za.co.neroland.nerocompanion.look;

/** A look is a whole-body skin or a hat. */
public enum LookKind {
    SKIN("skins"),
    HAT("hats");

    private final String directory;

    LookKind(String directory) {
        this.directory = directory;
    }

    /** The data directory under {@code data/<namespace>/}. */
    public String directory() {
        return this.directory;
    }

    /** The lang prefix of a look's name: {@code skin} / {@code hat}. */
    public String langPrefix() {
        return this == SKIN ? "skin" : "hat";
    }
}
