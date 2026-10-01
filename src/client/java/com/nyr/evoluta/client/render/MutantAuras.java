package com.nyr.evoluta.client.render;

import com.nyr.evoluta.common.mutation.Archetype;
import com.nyr.evoluta.common.mutation.MutationData;
import com.nyr.evoluta.common.mutation.Mutations;
import com.nyr.evoluta.common.mutation.Tier;
import com.nyr.evoluta.common.particle.EvolutaParticles;
import com.nyr.evoluta.common.tactics.Stalking;
import net.minecraft.block.BlockRenderType;
import net.minecraft.block.BlockState;
import net.minecraft.client.MinecraftClient;
import net.minecraft.client.world.ClientWorld;
import net.minecraft.entity.Entity;
import net.minecraft.entity.mob.MobEntity;
import net.minecraft.particle.BlockStateParticleEffect;
import net.minecraft.particle.ParticleEffect;
import net.minecraft.particle.ParticleTypes;
import net.minecraft.util.math.Vec3d;
import net.minecraft.util.math.random.Random;

/**
 * Element auras, thick enough to name the element from across a dark cave: every client tick, each mutant within
 * {@link #RANGE} blocks of the camera gives off its element's particles, more for higher tiers. Purely client-side:
 * the server sends nothing for them. Follows the Particles setting its own way (Decreased halves, Minimal keeps a
 * fifth) rather than vanilla's, which would hide every aura on Minimal and beyond 32 blocks.
 */
public final class MutantAuras {
	static final double RANGE = 40;
	/** Core aura particles per tick by tier; the fraction rolls. */
	private static final float[] PER_TICK = {0.8F, 1.8F, 3.2F};

	private MutantAuras() {
	}

	public static void tick(MinecraftClient client) {
		ClientWorld world = client.world;
		if (world == null || client.player == null || client.isPaused()) {
			return;
		}
		float density = switch (client.options.getParticles().getValue()) {
			case ALL -> 1.0F;
			case DECREASED -> 0.5F;
			case MINIMAL -> 0.2F;
		};
		Vec3d camera = client.gameRenderer.getCamera().getPos();
		for (Entity entity : world.getEntities()) {
			if (!(entity instanceof MobEntity mob) || !mob.isAlive() || mob.isInvisible()) {
				continue;
			}
			MutationData data = Mutations.get(mob);
			if (data != null && mob.squaredDistanceTo(camera) <= RANGE * RANGE) {
				emit(world, mob, data, density);
			}
		}
	}

	private static void emit(ClientWorld world, MobEntity mob, MutationData data, float density) {
		Random random = mob.getRandom();
		float budget = PER_TICK[data.getTier().id() - 1] * density;
		int count = (int) budget + (random.nextFloat() < budget - (int) budget ? 1 : 0);
		boolean apex = data.getTier() == Tier.APEX;
		switch (data.getElement()) {
			case IGNITED -> ignited(world, mob, random, count, density, apex);
			case PERMAFROST -> permafrost(world, mob, random, count, density, apex);
			case TOXIC -> toxic(world, mob, random, count, density, apex);
			case NONE -> {
				// an element-less champion still marks itself
				if (data.isChampion() && random.nextFloat() < 0.5F * density) {
					spawn(world, ParticleTypes.WITCH, mob.getX(), mob.getY() + mob.getHeight() + 0.2, mob.getZ(), 0, 0, 0);
				}
			}
			case ABYSSAL -> {
			}
		}
		if (data.getArchetype() == Archetype.BRUTE) {
			footfalls(world, mob, random, density);
		} else if (data.getArchetype() == Archetype.STALKER && Stalking.pace(mob) > 0 && random.nextFloat() < 0.7F * density) {
			// a Stalker closing on a turned back trails shadow: its pace is a synced speed modifier
			spawn(world, ParticleTypes.SMOKE, mob.getX() + (random.nextDouble() - 0.5) * mob.getWidth(), mob.getY() + 0.15,
					mob.getZ() + (random.nextDouble() - 0.5) * mob.getWidth(), 0, 0.01, 0);
		}
	}

	/** A walking Brute kicks up the ground it stands on, like a sprinting player but heavier. */
	private static void footfalls(ClientWorld world, MobEntity mob, Random random, float density) {
		double dx = mob.getX() - mob.prevX;
		double dz = mob.getZ() - mob.prevZ;
		if (!mob.isOnGround() || dx * dx + dz * dz < 0.0016 || random.nextFloat() >= 0.6F * density) {
			return;
		}
		BlockState ground = world.getBlockState(mob.getLandingPos());
		if (ground.getRenderType() != BlockRenderType.INVISIBLE) {
			spawn(world, new BlockStateParticleEffect(ParticleTypes.BLOCK, ground), mob.getX() + (random.nextDouble() - 0.5) * mob.getWidth(),
					mob.getY() + 0.1, mob.getZ() + (random.nextDouble() - 0.5) * mob.getWidth(), -dx * 4, 1.5, -dz * 4);
		}
	}

	/** Embers streaming off the shoulders, tongues of flame, soul fire and a crackling lava pop for an Apex. */
	private static void ignited(ClientWorld world, MobEntity mob, Random random, int count, float density, boolean apex) {
		double shoulders = mob.getY() + mob.getHeight() * 0.78;
		double spread = mob.getWidth() * 0.55;
		for (int i = 0; i < count; i++) {
			spawn(world, EvolutaParticles.EMBER, mob.getX() + (random.nextDouble() - 0.5) * 2 * spread, shoulders + random.nextDouble() * 0.15,
					mob.getZ() + (random.nextDouble() - 0.5) * 2 * spread, 0, 0.035 + random.nextDouble() * 0.04, 0);
		}
		if (random.nextFloat() < 0.45F * density) {
			spawn(world, ParticleTypes.FLAME, mob.getX() + (random.nextDouble() - 0.5) * 2 * spread, shoulders,
					mob.getZ() + (random.nextDouble() - 0.5) * 2 * spread, 0, 0.02, 0);
		}
		if (apex && random.nextFloat() < 0.3F * density) {
			spawn(world, ParticleTypes.SOUL_FIRE_FLAME, mob.getX() + (random.nextDouble() - 0.5) * 2 * spread, shoulders + 0.1,
					mob.getZ() + (random.nextDouble() - 0.5) * 2 * spread, 0, 0.03, 0);
		}
		if (apex && random.nextFloat() < 0.04F * density) {
			spawn(world, ParticleTypes.LAVA, mob.getX(), mob.getY() + mob.getHeight() * 0.5, mob.getZ(), 0, 0, 0);
		}
	}

	/** Frost mist circling the feet and torso, snow falling off it, and a cold breath now and then. */
	private static void permafrost(ClientWorld world, MobEntity mob, Random random, int count, float density, boolean apex) {
		for (int i = 0; i < count; i++) {
			double radius = mob.getWidth() * (0.6 + random.nextDouble() * 0.4);
			spawn(world, EvolutaParticles.FROST_MIST, mob.getX(), mob.getY() + random.nextDouble() * mob.getHeight() * 0.65, mob.getZ(),
					radius, 0.004 + random.nextDouble() * 0.01, (random.nextBoolean() ? 1 : -1) * (0.12 + random.nextDouble() * 0.1));
		}
		if (random.nextFloat() < 0.35F * density) {
			spawn(world, ParticleTypes.SNOWFLAKE, mob.getX() + (random.nextDouble() - 0.5) * mob.getWidth(), mob.getY() + mob.getHeight(),
					mob.getZ() + (random.nextDouble() - 0.5) * mob.getWidth(), 0, -0.02, 0);
		}
		if (random.nextFloat() < (apex ? 0.06F : 0.03F)) {
			breathe(world, mob, random, EvolutaParticles.FROST_BREATH, 3 + (apex ? 2 : 0), 0.08);
		}
		if (apex && random.nextFloat() < 0.25F * density) {
			spawn(world, ParticleTypes.END_ROD, mob.getX() + (random.nextDouble() - 0.5) * mob.getWidth() * 1.5,
					mob.getY() + random.nextDouble() * mob.getHeight(), mob.getZ() + (random.nextDouble() - 0.5) * mob.getWidth() * 1.5, 0, -0.01, 0);
		}
	}

	/** Glowing acid dripping off the body, a sickly haze at the feet, a poison sneeze now and then. */
	private static void toxic(ClientWorld world, MobEntity mob, Random random, int count, float density, boolean apex) {
		double spread = mob.getWidth() * 0.5;
		for (int i = 0; i < count; i++) {
			spawn(world, EvolutaParticles.ACID_DRIP, mob.getX() + (random.nextDouble() - 0.5) * 2 * spread,
					mob.getY() + mob.getHeight() * (0.35 + random.nextDouble() * 0.45), mob.getZ() + (random.nextDouble() - 0.5) * 2 * spread, 0, -0.02, 0);
		}
		if (random.nextFloat() < 0.45F * density) {
			spawn(world, EvolutaParticles.TOXIC_HAZE, mob.getX(), mob.getY() + 0.1 + random.nextDouble() * 0.3, mob.getZ(),
					mob.getWidth() * (0.7 + random.nextDouble() * 0.4), 0.003, (random.nextBoolean() ? 1 : -1) * (0.08 + random.nextDouble() * 0.06));
		}
		if (random.nextFloat() < (apex ? 0.05F : 0.025F)) {
			breathe(world, mob, random, ParticleTypes.SNEEZE, 4, 0.12);
		}
		if (apex && random.nextFloat() < 0.2F * density) {
			spawn(world, ParticleTypes.ITEM_SLIME, mob.getX() + (random.nextDouble() - 0.5) * 2 * spread,
					mob.getY() + mob.getHeight() * 0.6, mob.getZ() + (random.nextDouble() - 0.5) * 2 * spread, 0, 0, 0);
		}
	}

	/**
	 * Adds the particle past vanilla's own filter, which drops every particle over 32 blocks from the camera and all
	 * of them on Minimal: an aura is how a player tells the element apart at range, so it keeps to {@link #RANGE}
	 * and to the density {@link #tick} worked out from the Particles setting instead.
	 */
	private static void spawn(ClientWorld world, ParticleEffect particle, double x, double y, double z, double vx, double vy, double vz) {
		world.addParticle(particle, true, x, y, z, vx, vy, vz);
	}

	/** A small puff out of the mouth, along where the mob is looking. */
	private static void breathe(ClientWorld world, MobEntity mob, Random random, ParticleEffect particle, int puffs, double speed) {
		Vec3d look = mob.getRotationVec(1.0F);
		Vec3d mouth = mob.getEyePos().add(look.multiply(mob.getWidth() * 0.5));
		for (int i = 0; i < puffs; i++) {
			spawn(world, particle, mouth.x, mouth.y - 0.1, mouth.z,
					look.x * speed + (random.nextDouble() - 0.5) * 0.02, look.y * speed + 0.005, look.z * speed + (random.nextDouble() - 0.5) * 0.02);
		}
	}
}
