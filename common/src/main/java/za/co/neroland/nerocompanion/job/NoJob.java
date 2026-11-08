package za.co.neroland.nerocompanion.job;

import net.minecraft.resources.Identifier;

/** The empty job ({@link CompanionJob#none()}). Never written: a record without a job has no job keys. */
enum NoJob implements CompanionJob {
    INSTANCE;

    static final Identifier ID = Identifier.fromNamespaceAndPath("nerocompanion", "none");

    @Override
    public Identifier jobType() {
        return ID;
    }

    @Override
    public boolean isNone() {
        return true;
    }
}
