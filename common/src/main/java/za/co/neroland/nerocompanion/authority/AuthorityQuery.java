package za.co.neroland.nerocompanion.authority;

import java.util.UUID;

import net.minecraft.core.BlockPos;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.world.entity.Entity;

import org.jetbrains.annotations.Nullable;

/**
 * What a provider is asked about. {@code level}, {@code pos} and {@code target} are null where the action has
 * none (and in unit tests).
 */
public record AuthorityQuery(AuthorityAction action, UUID owner, @Nullable ServerLevel level,
        @Nullable BlockPos pos, @Nullable Entity target) {
}
