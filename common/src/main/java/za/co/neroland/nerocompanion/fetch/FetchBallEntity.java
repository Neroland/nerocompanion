package za.co.neroland.nerocompanion.fetch;

import java.util.UUID;

import net.minecraft.server.level.ServerLevel;
import net.minecraft.world.entity.EntityType;
import net.minecraft.world.entity.item.ItemEntity;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.entity.projectile.throwableitemprojectile.ThrowableItemProjectile;
import net.minecraft.world.item.Item;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.level.Level;
import net.minecraft.world.phys.HitResult;

import org.jetbrains.annotations.Nullable;

import za.co.neroland.nerocompanion.registry.ModEntities;
import za.co.neroland.nerocompanion.registry.ModItems;

/**
 * A thrown Fetch Ball. It harms nothing; where it lands it becomes the ball item again, and the thrower's UUID is
 * recorded server-side for that item ({@link FetchTargets}) so only the thrower's own pet fetches it. The thrower
 * is held in memory while in flight (a ball whose chunk unloads mid-flight lands unclaimed).
 */
public class FetchBallEntity extends ThrowableItemProjectile {

    @Nullable
    private UUID throwerId;

    public FetchBallEntity(EntityType<? extends FetchBallEntity> type, Level level) {
        super(type, level);
    }

    /** A ball leaving {@code player}'s hand. */
    public static FetchBallEntity thrown(ServerLevel level, Player player, ItemStack stack) {
        FetchBallEntity ball = new FetchBallEntity(ModEntities.FETCH_BALL.get(), level);
        ball.setPos(player.getX(), player.getEyeY() - 0.1D, player.getZ());
        ball.setOwner(player);
        ball.setItem(stack);
        ball.throwerId = player.getUUID();
        ball.shootFromRotation(player, player.getXRot(), player.getYRot(), 0.0F, 1.1F, 1.0F);
        return ball;
    }

    @Override
    protected Item getDefaultItem() {
        return ModItems.FETCH_BALL.get();
    }

    @Nullable
    public UUID throwerId() {
        return this.throwerId;
    }

    @Override
    protected void onHit(HitResult result) {
        super.onHit(result);
        if (this.level() instanceof ServerLevel server && !this.isRemoved()) {
            ItemEntity item = new ItemEntity(server, this.getX(), this.getY(), this.getZ(), this.getItem().copy());
            item.setPickUpDelay(10);
            server.addFreshEntity(item);
            if (this.throwerId != null) {
                FetchTargets.landed(item.getUUID(), this.throwerId, server.getGameTime());
            }
            this.discard();
        }
    }
}
