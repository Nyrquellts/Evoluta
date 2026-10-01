package com.nyr.evoluta.client.particle;

import net.minecraft.client.particle.Particle;
import net.minecraft.client.particle.ParticleFactory;
import net.minecraft.client.particle.ParticleTextureSheet;
import net.minecraft.client.particle.SpriteBillboardParticle;
import net.minecraft.client.particle.SpriteProvider;
import net.minecraft.client.render.LightmapTextureManager;
import net.minecraft.client.world.ClientWorld;
import net.minecraft.particle.SimpleParticleType;
import net.minecraft.util.math.MathHelper;

/**
 * One class for every aura particle; a {@link Style} sets its colour, glow, size, life and how it moves. They glow
 * on their own (a minimum light level), which is what keeps an aura readable across a dark cave.
 */
public final class AuraParticle extends SpriteBillboardParticle {
	enum Motion {
		/** Circles its spawn point; the spawn velocity carries (radius, rise per tick, turn per tick). */
		ORBIT,
		/** Drifts up from its spawn velocity, wavering. */
		RISE,
		/** Falls under gravity and dies where it lands. */
		FALL,
		/** Coasts on its spawn velocity, slowing down. */
		DRIFT,
		/**
		 * Flies in to its spawn point from the offset its spawn velocity gives, faster as it arrives, the way vanilla's
		 * enchanting glyphs fly into the table's book.
		 */
		SEEK
	}

	/**
	 * @param glow         minimum block light the particle is drawn with, 0 to 15
	 * @param endRed       colour it shifts toward over its life (the same as the start for a steady colour)
	 */
	record Style(Motion motion, float red, float green, float blue, float endRed, float endGreen, float endBlue, float alpha,
			float minSize, float maxSize, int minLife, int maxLife, int glow, boolean animated) {
	}

	static final Style FROST_MIST = new Style(Motion.ORBIT, 0.78F, 0.93F, 1.0F, 0.9F, 0.98F, 1.0F, 0.55F, 0.2F, 0.34F, 26, 40, 13, true);
	static final Style FROST_BREATH = new Style(Motion.DRIFT, 0.86F, 0.96F, 1.0F, 0.95F, 1.0F, 1.0F, 0.6F, 0.16F, 0.26F, 14, 22, 12, true);
	static final Style EMBER = new Style(Motion.RISE, 1.0F, 0.72F, 0.25F, 1.0F, 0.2F, 0.04F, 1.0F, 0.06F, 0.12F, 16, 30, 15, true);
	static final Style ACID_DRIP = new Style(Motion.FALL, 0.5F, 1.0F, 0.25F, 0.3F, 0.85F, 0.12F, 0.95F, 0.08F, 0.12F, 24, 34, 11, false);
	static final Style TOXIC_HAZE = new Style(Motion.ORBIT, 0.45F, 0.85F, 0.25F, 0.3F, 0.6F, 0.15F, 0.32F, 0.26F, 0.42F, 30, 46, 8, true);
	/** Enchanting glyphs in a graft's element, burning at full brightness as they fly in to its bearer. */
	static final Style GLYPH_IGNITED = new Style(Motion.SEEK, 1.0F, 0.82F, 0.4F, 1.0F, 0.36F, 0.04F, 1.0F, 0.06F, 0.11F, 30, 42, 15, false);
	static final Style GLYPH_FROST = new Style(Motion.SEEK, 0.8F, 0.97F, 1.0F, 0.3F, 0.75F, 1.0F, 1.0F, 0.06F, 0.11F, 30, 42, 15, false);
	static final Style GLYPH_TOXIC = new Style(Motion.SEEK, 0.9F, 1.0F, 0.5F, 0.4F, 0.95F, 0.08F, 1.0F, 0.06F, 0.11F, 30, 42, 15, false);

	private final SpriteProvider sprites;
	private final Style style;
	private final double centerX;
	private final double centerZ;
	private final double startY;
	private double angle;
	private double radius;
	private double rise;
	private double turn;
	private double offsetX;
	private double offsetY;
	private double offsetZ;

	AuraParticle(ClientWorld world, double x, double y, double z, double vx, double vy, double vz, SpriteProvider sprites, Style style) {
		super(world, x, y, z);
		this.sprites = sprites;
		this.style = style;
		this.maxAge = style.minLife() + this.random.nextInt(style.maxLife() - style.minLife() + 1);
		this.scale = style.minSize() + this.random.nextFloat() * (style.maxSize() - style.minSize());
		this.setColor(style.red(), style.green(), style.blue());
		this.alpha = style.alpha();
		this.collidesWithWorld = style.motion() == Motion.FALL;
		this.gravityStrength = style.motion() == Motion.FALL ? 0.7F : 0.0F;
		this.velocityMultiplier = style.motion() == Motion.DRIFT ? 0.9F : 0.96F;
		this.startY = y;
		if (style.motion() == Motion.SEEK) {
			this.centerX = x;
			this.centerZ = z;
			this.offsetX = vx;
			this.offsetY = vy;
			this.offsetZ = vz;
			this.velocityX = 0;
			this.velocityY = 0;
			this.velocityZ = 0;
			this.setPos(x + vx, y + vy, z + vz);
			this.prevPosX = this.x;
			this.prevPosY = this.y;
			this.prevPosZ = this.z;
		} else if (style.motion() == Motion.ORBIT) {
			this.centerX = x;
			this.centerZ = z;
			this.radius = vx;
			this.rise = vy;
			this.turn = vz;
			this.angle = this.random.nextDouble() * Math.PI * 2;
			this.velocityX = 0;
			this.velocityY = 0;
			this.velocityZ = 0;
			this.orbit();
			this.prevPosX = this.x;
			this.prevPosY = this.y;
			this.prevPosZ = this.z;
		} else {
			this.centerX = x;
			this.centerZ = z;
			this.velocityX = vx;
			this.velocityY = vy;
			this.velocityZ = vz;
		}
		if (style.animated()) {
			this.setSpriteForAge(sprites);
		} else {
			this.setSprite(sprites);
		}
	}

	@Override
	public void tick() {
		if (this.style.motion() == Motion.SEEK) {
			this.prevPosX = this.x;
			this.prevPosY = this.y;
			this.prevPosZ = this.z;
			if (this.age++ >= this.maxAge) {
				this.markDead();
				return;
			}
			float left = 1.0F - this.age / (float) this.maxAge;
			float arrived = (1.0F - left) * (1.0F - left);
			arrived *= arrived;
			this.setPos(this.centerX + this.offsetX * left, this.startY + this.offsetY * left - arrived * 0.3F, this.centerZ + this.offsetZ * left);
		} else if (this.style.motion() == Motion.ORBIT) {
			this.prevPosX = this.x;
			this.prevPosY = this.y;
			this.prevPosZ = this.z;
			if (this.age++ >= this.maxAge) {
				this.markDead();
				return;
			}
			this.angle += this.turn;
			this.radius *= 1.01;
			this.orbit();
		} else {
			if (this.style.motion() == Motion.RISE) {
				this.velocityX += (this.random.nextFloat() - 0.5F) * 0.006F;
				this.velocityZ += (this.random.nextFloat() - 0.5F) * 0.006F;
			}
			super.tick();
			if (this.style.motion() == Motion.FALL && this.onGround) {
				this.markDead();
				return;
			}
		}
		float life = MathHelper.clamp(this.age / (float) this.maxAge, 0.0F, 1.0F);
		this.setColor(MathHelper.lerp(life, this.style.red(), this.style.endRed()),
				MathHelper.lerp(life, this.style.green(), this.style.endGreen()),
				MathHelper.lerp(life, this.style.blue(), this.style.endBlue()));
		float fade = 1.0F - life * life;
		// embers crackle: a random flicker on top of the fade
		this.alpha = this.style.alpha() * fade * (this.style.motion() == Motion.RISE ? 0.65F + this.random.nextFloat() * 0.35F : 1.0F);
		if (this.style.animated()) {
			this.setSpriteForAge(this.sprites);
		}
	}

	private void orbit() {
		this.setPos(this.centerX + Math.cos(this.angle) * this.radius, this.y + this.rise, this.centerZ + Math.sin(this.angle) * this.radius);
	}

	@Override
	public ParticleTextureSheet getType() {
		return ParticleTextureSheet.PARTICLE_SHEET_TRANSLUCENT;
	}

	@Override
	protected int getBrightness(float tint) {
		int light = super.getBrightness(tint);
		int block = Math.max(LightmapTextureManager.getBlockLightCoordinates(light), this.style.glow());
		return LightmapTextureManager.pack(block, LightmapTextureManager.getSkyLightCoordinates(light));
	}

	/** Creates the particles of one style, drawing the sprites listed in that particle's JSON. */
	public static final class Factory implements ParticleFactory<SimpleParticleType> {
		private final SpriteProvider sprites;
		private final Style style;

		Factory(SpriteProvider sprites, Style style) {
			this.sprites = sprites;
			this.style = style;
		}

		@Override
		public Particle createParticle(SimpleParticleType type, ClientWorld world, double x, double y, double z, double vx, double vy, double vz) {
			return new AuraParticle(world, x, y, z, vx, vy, vz, this.sprites, this.style);
		}
	}
}
