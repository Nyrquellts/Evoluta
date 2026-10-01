package com.nyr.evoluta.common.combat;

import com.nyr.evoluta.common.mutation.Archetype;
import com.nyr.evoluta.common.mutation.Element;
import com.nyr.evoluta.common.mutation.MutationData;
import com.nyr.evoluta.common.mutation.Tier;
import com.nyr.evoluta.common.network.ShakePayload;
import com.nyr.evoluta.common.world.EvolutaWorlds;
import com.nyr.evoluta.common.world.WorldState;
import net.fabricmc.fabric.api.networking.v1.ServerPlayNetworking;
import net.minecraft.block.Blocks;
import net.minecraft.entity.LivingEntity;
import net.minecraft.entity.mob.MobEntity;
import net.minecraft.item.ItemStack;
import net.minecraft.item.Items;
import net.minecraft.particle.BlockStateParticleEffect;
import net.minecraft.particle.DustParticleEffect;
import net.minecraft.particle.ItemStackParticleEffect;
import net.minecraft.particle.ParticleEffect;
import net.minecraft.particle.ParticleTypes;
import net.minecraft.server.network.ServerPlayerEntity;
import net.minecraft.server.world.ServerWorld;
import net.minecraft.sound.SoundCategory;
import net.minecraft.sound.SoundEvents;
import net.minecraft.util.math.MathHelper;
import net.minecraft.util.math.Vec3d;
import org.joml.Vector3f;

/**
 * What makes a fight land: a burst of the element and a sound where a blow lands, a camera shake for the player it
 * lands on, a champion's soul bursting out of it when it dies, the lane a Brute is about to charge down. Everything
 * runs from events that happen anyway (a hit, a death, a charge), never per tick, and the particles and sounds are
 * vanilla's.
 */
public final class Juice {
	/** Shake a mutant's blow gives the player it lands on, by tier (Evolved, Elite, Apex); a Brute's hits more. */
	static final float[] HIT_TRAUMA = {0.16F, 0.24F, 0.34F};
	static final float BRUTE_TRAUMA = 0.12F;
	/** A Brute's slam: shake for the player it lands on, and for players around. */
	public static final float SLAM_TRAUMA = 0.6F;
	/** A mutant creeper's blast, felt out to {@link #BLAST_RANGE} blocks. */
	public static final float BLAST_TRAUMA = 0.55F;
	public static final double BLAST_RANGE = 16;
	/** An Apex's roar (waking from ambush), felt out to 16 blocks. */
	public static final float ROAR_TRAUMA = 0.25F;
	/** A champion's death, felt out to 10 blocks. */
	static final float[] DEATH_TRAUMA = {0.0F, 0.15F, 0.3F};
	/** Bursts are sent to the players this close only: farther off they are lost in the distance anyway. */
	static final double VIEW_RANGE = 16;
	/**
	 * At most this many hit bursts per world per tick: a brawl where dozens of blows land at once would otherwise
	 * send a flood of particles in one tick. Blows past the cap still land, just without a burst.
	 */
	public static final int IMPACTS_PER_TICK = 12;

	private Juice() {
	}

	/** How hard a mutant's blow shakes the player it lands on. */
	public static float hitTrauma(MutationData data) {
		return HIT_TRAUMA[data.getTier().id() - 1] + (data.getArchetype() == Archetype.BRUTE ? BRUTE_TRAUMA : 0.0F);
	}

	/** Shakes one player's camera; nothing for a client without Evoluta's channel (it could not read the message). */
	public static void shake(ServerPlayerEntity player, float trauma) {
		if (trauma > 0.0F && ServerPlayNetworking.canSend(player, ShakePayload.ID)) {
			ServerPlayNetworking.send(player, new ShakePayload(Math.min(1.0F, trauma)));
		}
	}

	/** Shakes every player within {@code range} of {@code at}, less the farther off they stand. */
	public static void shakeAround(ServerWorld world, Vec3d at, double range, float trauma) {
		for (ServerPlayerEntity player : world.getPlayers()) {
			double distance = player.getPos().distanceTo(at);
			if (distance < range && Hazards.isVulnerablePlayer(player)) {
				shake(player, (float) (trauma * (1.0 - distance / range)));
			}
		}
	}

	/**
	 * A blow of {@code element} lands on {@code victim}: a burst of it out of the body and a sound, bigger by
	 * {@code strength} (0 to 1). Ignited bursts in flame and cinders, Permafrost in ice shards and snow, Toxic in
	 * splattering goo; an element-less blow throws sparks.
	 */
	public static void impact(ServerWorld world, LivingEntity victim, Element element, float strength) {
		WorldState state = EvolutaWorlds.of(world);
		long tick = world.getTime();
		if (state.impactTick != tick) {
			state.impactTick = tick;
			state.impacts = 0;
		}
		if (state.impacts++ >= IMPACTS_PER_TICK) {
			return;
		}
		double x = victim.getX();
		double y = victim.getBodyY(0.6);
		double z = victim.getZ();
		double spread = victim.getWidth() * 0.35;
		int count = 6 + Math.round(8 * strength);
		switch (element) {
			case IGNITED -> {
				burst(world, ParticleTypes.FLAME, x, y, z, count, spread, 0.06);
				burst(world, ParticleTypes.LAVA, x, y, z, 1 + Math.round(2 * strength), spread, 0.0);
				world.playSound(null, x, y, z, SoundEvents.ENTITY_BLAZE_SHOOT, SoundCategory.HOSTILE, 0.45F + 0.3F * strength, 1.35F);
			}
			case PERMAFROST -> {
				burst(world, new BlockStateParticleEffect(ParticleTypes.BLOCK, Blocks.BLUE_ICE.getDefaultState()), x, y, z, count + 4, spread, 0.15);
				burst(world, ParticleTypes.SNOWFLAKE, x, y, z, count / 2, spread, 0.05);
				world.playSound(null, x, y, z, SoundEvents.BLOCK_GLASS_BREAK, SoundCategory.HOSTILE, 0.45F + 0.3F * strength, 1.55F);
			}
			case TOXIC -> {
				burst(world, new ItemStackParticleEffect(ParticleTypes.ITEM, new ItemStack(Items.SLIME_BALL)), x, y, z, count + 2, spread, 0.12);
				burst(world, ParticleTypes.SNEEZE, x, y, z, count / 2, spread, 0.03);
				world.playSound(null, x, y, z, SoundEvents.ENTITY_SLIME_SQUISH, SoundCategory.HOSTILE, 0.55F + 0.3F * strength, 0.8F);
			}
			default -> burst(world, ParticleTypes.CRIT, x, y, z, count, spread, 0.25);
		}
	}

	/**
	 * A champion dies: its soul core bursts out of its chest in the element's colour, a ring of the element runs out
	 * along the ground, and the players near it feel it.
	 */
	public static void soulBurst(ServerWorld world, MobEntity mob, MutationData data) {
		if (!data.isChampion()) {
			return;
		}
		boolean apex = data.getTier() == Tier.APEX;
		double x = mob.getX();
		double y = mob.getBodyY(0.6);
		double z = mob.getZ();
		DustParticleEffect soul = new DustParticleEffect(colour(data.getElement()), apex ? 2.0F : 1.4F);
		burst(world, soul, x, y, z, apex ? 40 : 24, 0.25, 0.25);
		burst(world, ParticleTypes.SOUL, x, y, z, apex ? 12 : 6, 0.2, 0.08);
		ParticleEffect ring = switch (data.getElement()) {
			case IGNITED -> ParticleTypes.FLAME;
			case PERMAFROST -> ParticleTypes.SNOWFLAKE;
			case TOXIC -> ParticleTypes.SNEEZE;
			default -> ParticleTypes.CLOUD;
		};
		int spokes = apex ? 24 : 16;
		for (int i = 0; i < spokes; i++) {
			double angle = Math.PI * 2 * i / spokes;
			// count 0: one particle, flung along (dx, dy, dz) at the given speed
			fling(world, ring, x, mob.getY() + 0.15, z, Math.cos(angle), 0.02, Math.sin(angle), apex ? 0.45 : 0.3);
		}
		world.playSound(null, x, y, z, SoundEvents.PARTICLE_SOUL_ESCAPE.value(), SoundCategory.HOSTILE, apex ? 2.0F : 1.4F, 0.8F);
		world.playSound(null, x, y, z, SoundEvents.ENTITY_GENERIC_EXPLODE.value(), SoundCategory.HOSTILE, apex ? 0.5F : 0.3F, 1.6F);
		shakeAround(world, new Vec3d(x, y, z), 10, DEATH_TRAUMA[data.getTier().id() - 1]);
	}

	/** The line a planted Brute is about to rush down, in dull red dust along the ground, 10 blocks at most. */
	public static void chargeLane(ServerWorld world, MobEntity brute, Vec3d target) {
		Vec3d from = brute.getPos();
		Vec3d along = new Vec3d(target.x - from.x, 0.0, target.z - from.z);
		double length = Math.min(10.0, along.length());
		if (length < 1.0E-3) {
			return;
		}
		Vec3d step = along.normalize().multiply(0.5);
		DustParticleEffect lane = new DustParticleEffect(new Vector3f(0.85F, 0.12F, 0.05F), 1.2F);
		for (int i = 1; i <= (int) (length / 0.5); i++) {
			burst(world, lane, from.x + step.x * i, from.y + 0.1, from.z + step.z * i, 1, 0.05, 0.0);
		}
	}

	private static void burst(ServerWorld world, ParticleEffect effect, double x, double y, double z, int count, double spread, double speed) {
		if (count <= 0) {
			return;
		}
		for (ServerPlayerEntity player : world.getPlayers()) {
			if (player.squaredDistanceTo(x, y, z) < VIEW_RANGE * VIEW_RANGE) {
				world.spawnParticles(player, effect, false, x, y, z, count, spread, spread, spread, speed);
			}
		}
	}

	/** One particle flung along (dx, dy, dz) at {@code speed}, for the players within {@link #VIEW_RANGE} blocks. */
	private static void fling(ServerWorld world, ParticleEffect effect, double x, double y, double z, double dx, double dy, double dz, double speed) {
		for (ServerPlayerEntity player : world.getPlayers()) {
			if (player.squaredDistanceTo(x, y, z) < VIEW_RANGE * VIEW_RANGE) {
				world.spawnParticles(player, effect, false, x, y, z, 0, dx, dy, dz, speed);
			}
		}
	}

	/** The element's colour as dust: the colour its mutants' eyes glow. */
	static Vector3f colour(Element element) {
		int rgb = switch (element) {
			case IGNITED -> 0xFF8A2A;
			case PERMAFROST -> 0x8FEFFF;
			case TOXIC -> 0x9CFF45;
			case ABYSSAL -> 0xB070FF;
			case NONE -> 0xFF3B3B;
		};
		return new Vector3f((rgb >> 16 & 0xFF) / 255.0F, (rgb >> 8 & 0xFF) / 255.0F, (rgb & 0xFF) / 255.0F);
	}

	/** How strong a blow is, 0 to 1, from its tier. */
	public static float strength(Tier tier) {
		return MathHelper.clamp((tier.id() - 1) / 2.0F, 0.0F, 1.0F);
	}
}
