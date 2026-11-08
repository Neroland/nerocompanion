package za.co.neroland.nerocompanion.client.particle;

import net.minecraft.client.multiplayer.ClientLevel;
import net.minecraft.client.particle.Particle;
import net.minecraft.client.particle.ParticleProvider;
//? if >=1.21.9 {
import net.minecraft.client.particle.SingleQuadParticle;
//?} else {
/*import net.minecraft.client.particle.ParticleRenderType;
import net.minecraft.client.particle.TextureSheetParticle;
*///?}
import net.minecraft.client.particle.SpriteSet;
import net.minecraft.core.particles.SimpleParticleType;
import net.minecraft.util.RandomSource;

/**
 * The five companion particles ({@code 010} section 5.3), one class tuned per {@link Kind}: lifetime, motion,
 * size, whether it is full-bright, and how it steps through its sprite frames. Sprites carry their own colours. The
 * spawner passes the initial velocity. Client only.
 */
//? if >=1.21.9 {
public class CompanionParticle extends SingleQuadParticle {
//?} else {
/*public class CompanionParticle extends TextureSheetParticle {
*///?}

    public enum Kind {
        /** Rises 0.05/tick with gravity -0.002, 12 ticks, full-bright. */
        CHARGE_SPARK(12, 0.06F, true),
        /** Drifts up-right, grows 0.5 -> 1.0, 40 ticks, 60 % alpha. */
        SLEEP_Z(40, 0.10F, false),
        /** A ring growing over its 4 frames, 10 ticks, full-bright, still. */
        SCOUT_PING(10, 0.25F, true),
        /** Floats up 0.04/tick, 20 ticks, full-bright. */
        BOND_HEART(20, 0.12F, true),
        /** A soft puff that slows and settles, 16 ticks. */
        SCRAP_PUFF(16, 0.15F, false);

        final int life;
        final float size;
        final boolean emissive;

        Kind(int life, float size, boolean emissive) {
            this.life = life;
            this.size = size;
            this.emissive = emissive;
        }
    }

    private final SpriteSet sprites;
    private final Kind kind;
    private final float baseSize;

    protected CompanionParticle(ClientLevel level, double x, double y, double z, double xd, double yd, double zd,
            SpriteSet sprites, Kind kind) {
        //? if >=1.21.9 {
        super(level, x, y, z, sprites.first());
        //?} else {
        /*super(level, x, y, z);
        *///?}
        this.sprites = sprites;
        this.kind = kind;
        this.hasPhysics = false;
        this.friction = 0.96F;
        this.lifetime = kind.life;
        this.baseSize = kind.size;
        this.quadSize = kind.size;
        switch (kind) {
            case CHARGE_SPARK -> {
                this.xd = xd;
                this.yd = yd == 0.0D ? 0.05D : yd;
                this.zd = zd;
                this.gravity = 0.05F;
            }
            case SLEEP_Z -> {
                this.xd = 0.008D;
                this.yd = 0.02D;
                this.zd = 0.0D;
                this.gravity = 0.0F;
                this.friction = 1.0F;
                this.setAlpha(0.6F);
            }
            case SCOUT_PING -> {
                this.xd = 0.0D;
                this.yd = 0.0D;
                this.zd = 0.0D;
                this.gravity = 0.0F;
            }
            case BOND_HEART -> {
                this.xd = 0.0D;
                this.yd = 0.04D;
                this.zd = 0.0D;
                this.gravity = 0.0F;
                this.friction = 1.0F;
            }
            case SCRAP_PUFF -> {
                this.xd = xd;
                this.yd = yd;
                this.zd = zd;
                this.gravity = 0.0F;
                this.friction = 0.85F;
            }
        }
        this.setSpriteFromAge(sprites);
    }

    //? if >=1.21.9 {
    @Override
    public SingleQuadParticle.Layer getLayer() {
        return SingleQuadParticle.Layer.TRANSLUCENT;
    }
    //?} else {
    /*@Override
    public ParticleRenderType getRenderType() {
        return ParticleRenderType.PARTICLE_SHEET_TRANSLUCENT;
    }
    *///?}

    @Override
    public void tick() {
        super.tick();
        if (this.removed) {
            return;
        }
        float t = this.age / (float) Math.max(1, this.lifetime);
        if (this.kind == Kind.SLEEP_Z) {
            this.quadSize = this.baseSize * (0.5F + 0.5F * t);
            this.setAlpha(0.6F * (1.0F - Math.max(0.0F, t - 0.7F) / 0.3F));
        } else if (this.kind == Kind.SCRAP_PUFF || this.kind == Kind.BOND_HEART) {
            this.setAlpha(1.0F - Math.max(0.0F, t - 0.6F) / 0.4F);
        }
        this.setSpriteFromAge(this.sprites);
    }

    @Override
    //? if >=26.1 {
    protected int getLightCoords(float partialTick) {
        return this.kind.emissive ? 0xF000F0 : super.getLightCoords(partialTick);
    }
    //?} else {
    /*protected int getLightColor(float partialTick) {
        return this.kind.emissive ? 0xF000F0 : super.getLightColor(partialTick);
    }
    *///?}

    /** One provider per type, created by each loader with the type's sprite set. */
    public static final class Provider implements ParticleProvider<SimpleParticleType> {
        private final SpriteSet sprites;
        private final Kind kind;

        public Provider(SpriteSet sprites, Kind kind) {
            this.sprites = sprites;
            this.kind = kind;
        }

        @Override
        @org.jetbrains.annotations.Nullable
        //? if >=1.21.9 {
        public Particle createParticle(SimpleParticleType options, ClientLevel level, double x, double y, double z,
                double xd, double yd, double zd, RandomSource random) {
        //?} else {
        /*public Particle createParticle(SimpleParticleType options, ClientLevel level, double x, double y, double z,
                double xd, double yd, double zd) {
        *///?}
            return new CompanionParticle(level, x, y, z, xd, yd, zd, this.sprites, this.kind);
        }
    }
}
