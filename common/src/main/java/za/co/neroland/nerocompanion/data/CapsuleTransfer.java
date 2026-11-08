package za.co.neroland.nerocompanion.data;

import java.util.Optional;
import java.util.UUID;

import za.co.neroland.nerocompanion.entity.CompanionRecord;

/**
 * What a Capsule deploys as (pure). The vault's private part (owner, home, job data) is merged back only when it
 * belongs to the player deploying; a Capsule that changed hands (dropped on death, traded, taken from a broken Bay)
 * deploys with the shareable part alone, so the new owner's companion never inherits the previous owner's base.
 */
public final class CapsuleTransfer {

    private CapsuleTransfer() {
    }

    public static CompanionRecord forDeploy(CompanionRecord shared, Optional<CapsuleVault.Entry> entry, UUID deployer) {
        if (entry.isPresent() && entry.get().owner().equals(deployer)) {
            return merge(shared, entry.get());
        }
        return shared.forNetwork();
    }

    public static CompanionRecord merge(CompanionRecord shared, CapsuleVault.Entry entry) {
        return shared.withPrivate(entry.owner(), entry.home(), entry.job());
    }
}
