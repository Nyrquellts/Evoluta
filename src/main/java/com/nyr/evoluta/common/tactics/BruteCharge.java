package com.nyr.evoluta.common.tactics;

import com.nyr.evoluta.common.Evoluta;
import com.nyr.evoluta.common.combat.Hazards;
import com.nyr.evoluta.common.combat.Juice;
import com.nyr.evoluta.common.mutation.Archetype;
import com.nyr.evoluta.common.mutation.Element;
import com.nyr.evoluta.common.mutation.EvolutaAttachments;
import com.nyr.evoluta.common.mutation.MutationData;
import com.nyr.evoluta.common.mutation.Mutations;
import com.nyr.evoluta.common.world.WorldState;
import java.util.List;
import net.minecraft.block.BlockState;
import net.minecraft.entity.attribute.EntityAttributeInstance;
import net.minecraft.entity.attribute.EntityAttributeModifier;
import net.minecraft.entity.attribute.EntityAttributes;
import net.minecraft.entity.effect.StatusEffectInstance;
import net.minecraft.entity.effect.StatusEffects;
import net.minecraft.entity.mob.MobEntity;
import net.minecraft.entity.player.PlayerEntity;
import net.minecraft.item.RangedWeaponItem;
import net.minecraft.item.TridentItem;
import net.minecraft.particle.BlockStateParticleEffect;
import net.minecraft.particle.ParticleTypes;
import net.minecraft.server.network.ServerPlayerEntity;
import net.minecraft.server.world.ServerWorld;
import net.minecraft.sound.SoundCategory;
import net.minecraft.sound.SoundEvents;
import net.minecraft.util.Identifier;
import net.minecraft.util.math.MathHelper;
import net.minecraft.util.math.Vec3d;
import org.jetbrains.annotations.Nullable;

/**
 * Elite and Apex Brutes charge. With its target between {@link #MIN_RANGE} and {@link #MAX_RANGE} blocks off, in
 * sight, on its own level and the Brute's feet on the ground, a Brute plants itself and growls for one tactical visit
 * (half a second: the tell), then rushes in a straight line at where the target stands. Reaching it, it slams: a full
 * melee hit and a heavy shove. Step aside and it thunders past; if it runs into a wall it staggers, an opening. Once
 * every {@link #COOLDOWN} ticks. The rush is the only per-tick work, and only the Brutes mid-rush do it
 * ({@link WorldState#charging}).
 */
public final class BruteCharge {
	/** Closer than this a Brute just fights (an Apex lunges: {@link Stalking#LUNGE_RANGE}). */
	public static final double MIN_RANGE = Stalking.LUNGE_RANGE;
	public static final double MAX_RANGE = 10.0;
	/** Ticks from one charge's launch to the next plant at the earliest. */
	public static final int COOLDOWN = 100;
	/** A rush lasts this many ticks at most: about 10 blocks. */
	public static final int RUSH_TICKS = 15;
	/** Blocks per tick while rushing; a sprinting player makes about 0.28. */
	public static final double RUSH_SPEED = 0.7;
	/** The slam's shove, on top of the hit's own knockback. */
	public static final double SLAM_KNOCKBACK = 1.4;
	/** Slowness IV this long after rushing into a wall. */
	public static final int STAGGER_TICKS = 30;
	/** Holds a planted Brute still. A temporary modifier: never saved. */
	public static final Identifier PLANTED = Evoluta.id("brute_planted");

	public enum Phase {
		/** Winding up since {@code since}. */
		PLANTED,
		/** Rushing along (dirX, dirZ) since {@code since}. */
		RUSHING,
		/** The rush reached its target and slammed it. From here on {@code since} is the launch, for the cooldown. */
		LANDED,
		/** The rush ran into a wall. */
		STAGGERED,
		/** The rush ran out, or there was nothing left to rush at. */
		SPENT
	}

	/** Where a Brute's charge stands. Kept in an unsaved attachment: a reload forgets it, as it should. */
	public record State(Phase phase, long since, double dirX, double dirZ) {
	}

	private BruteCharge() {
	}

	public static boolean charges(MutationData data) {
		return data.getArchetype() == Archetype.BRUTE && data.isChampion();
	}

	/**
	 * The tactical visit: launches a Brute that planted a visit ago, or plants one whose target is in reach. True
	 * while the charge owns the Brute (planted or rushing), so nothing else moves it this visit.
	 */
	public static boolean tick(ServerWorld world, MobEntity mob, long worldTime, WorldState state) {
		State charge = mob.getAttached(EvolutaAttachments.BRUTE_CHARGE);
		if (charge != null && charge.phase() == Phase.PLANTED) {
			return launch(world, mob, worldTime, state);
		}
		if (charge != null && (charge.phase() == Phase.RUSHING || worldTime - charge.since() < COOLDOWN)) {
			return charge.phase() == Phase.RUSHING;
		}
		if (!(mob.getTarget() instanceof PlayerEntity player) || !Hazards.isVulnerablePlayer(player) || !mob.isOnGround()
				|| mob.isTouchingWater() || holdsRanged(mob) || Math.abs(player.getY() - mob.getY()) > 2.0) {
			return false;
		}
		double distance = mob.distanceTo(player);
		if (distance < MIN_RANGE || distance > MAX_RANGE || !mob.canSee(player)) {
			return false;
		}
		plant(world, mob, player, worldTime);
		return true;
	}

	/** Every tick: pushes each rushing Brute on, and ends its rush when it lands, hits a wall or runs out. */
	public static void tickRushes(ServerWorld world, WorldState state, long worldTime) {
		List<MobEntity> charging = state.charging;
		for (int i = charging.size() - 1; i >= 0; i--) {
			MobEntity mob = charging.get(i);
			State charge = mob.getAttached(EvolutaAttachments.BRUTE_CHARGE);
			if (!mob.isAlive() || charge == null || charge.phase() != Phase.RUSHING) {
				charging.remove(i);
				continue;
			}
			Phase over = rush(world, mob, charge, worldTime);
			if (over != null) {
				mob.setAttached(EvolutaAttachments.BRUTE_CHARGE, new State(over, charge.since(), 0.0, 0.0));
				charging.remove(i);
			}
		}
	}

	/** Drops a charge in progress, as when no player is near or the Brute lies in wait. */
	public static void rest(MobEntity mob) {
		if (mob.hasAttached(EvolutaAttachments.BRUTE_CHARGE)) {
			unplant(mob);
			mob.removeAttached(EvolutaAttachments.BRUTE_CHARGE);
		}
	}

	private static void plant(ServerWorld world, MobEntity mob, PlayerEntity player, long worldTime) {
		mob.setAttached(EvolutaAttachments.BRUTE_CHARGE, new State(Phase.PLANTED, worldTime, 0.0, 0.0));
		EntityAttributeInstance speed = mob.getAttributeInstance(EntityAttributes.GENERIC_MOVEMENT_SPEED);
		if (speed != null && !speed.hasModifier(PLANTED)) {
			speed.addTemporaryModifier(new EntityAttributeModifier(PLANTED, -1.0, EntityAttributeModifier.Operation.ADD_MULTIPLIED_TOTAL));
		}
		mob.getNavigation().stop();
		mob.getLookControl().lookAt(player, 30.0F, 30.0F);
		kickUpGround(world, mob, 20);
		Juice.chargeLane(world, mob, player.getPos());
		world.playSound(null, mob.getX(), mob.getY(), mob.getZ(), SoundEvents.ENTITY_POLAR_BEAR_WARNING, SoundCategory.HOSTILE, 1.5F, 0.75F);
	}

	private static boolean launch(ServerWorld world, MobEntity mob, long worldTime, WorldState state) {
		unplant(mob);
		double dx = 0.0;
		double dz = 0.0;
		if (mob.getTarget() instanceof PlayerEntity player && player.isAlive()) {
			dx = player.getX() - mob.getX();
			dz = player.getZ() - mob.getZ();
		}
		double length = Math.sqrt(dx * dx + dz * dz);
		if (length < 1.0E-3) {
			// the target is gone (or on top of it): the wind-up was for nothing, and still costs the cooldown
			mob.setAttached(EvolutaAttachments.BRUTE_CHARGE, new State(Phase.SPENT, worldTime, 0.0, 0.0));
			return false;
		}
		mob.setAttached(EvolutaAttachments.BRUTE_CHARGE, new State(Phase.RUSHING, worldTime, dx / length, dz / length));
		state.charging.add(mob);
		world.playSound(null, mob.getX(), mob.getY(), mob.getZ(), SoundEvents.ENTITY_RAVAGER_ROAR, SoundCategory.HOSTILE, 1.3F, 0.85F);
		return true;
	}

	/** One tick of a rush: how it ended, or null while it goes on. */
	@Nullable
	private static Phase rush(ServerWorld world, MobEntity mob, State charge, long worldTime) {
		long ticks = worldTime - charge.since();
		if (ticks > RUSH_TICKS) {
			return Phase.SPENT;
		}
		if (mob.getTarget() instanceof PlayerEntity player && player.isAlive() && mob.getBoundingBox().expand(0.35).intersects(player.getBoundingBox())) {
			slam(world, mob, player, charge);
			return Phase.LANDED;
		}
		if (ticks > 1 && mob.horizontalCollision) {
			stagger(world, mob);
			return Phase.STAGGERED;
		}
		Vec3d velocity = mob.getVelocity();
		mob.setVelocity(charge.dirX() * RUSH_SPEED, velocity.y, charge.dirZ() * RUSH_SPEED);
		mob.velocityModified = true;
		float yaw = (float) (MathHelper.atan2(charge.dirZ(), charge.dirX()) * MathHelper.DEGREES_PER_RADIAN) - 90.0F;
		mob.setYaw(yaw);
		mob.setBodyYaw(yaw);
		mob.setHeadYaw(yaw);
		if (ticks % 3 == 0) {
			kickUpGround(world, mob, 5);
		}
		return null;
	}

	/** The rush lands: a full hit, then a shove along the rush, even through a raised shield. */
	private static void slam(ServerWorld world, MobEntity mob, PlayerEntity player, State charge) {
		mob.tryAttack(player);
		player.takeKnockback(SLAM_KNOCKBACK, -charge.dirX(), -charge.dirZ());
		player.velocityModified = true;
		MutationData data = Mutations.get(mob);
		Juice.impact(world, player, data == null ? Element.NONE : data.getElement(), 1.0F);
		if (player instanceof ServerPlayerEntity hit) {
			Juice.shake(hit, Juice.SLAM_TRAUMA);
		}
		world.spawnParticles(ParticleTypes.EXPLOSION, player.getX(), player.getBodyY(0.5), player.getZ(), 1, 0.0, 0.0, 0.0, 0.0);
		world.playSound(null, player.getX(), player.getY(), player.getZ(), SoundEvents.ENTITY_PLAYER_ATTACK_KNOCKBACK, SoundCategory.HOSTILE, 1.4F, 0.6F);
	}

	private static void stagger(ServerWorld world, MobEntity mob) {
		mob.addStatusEffect(new StatusEffectInstance(StatusEffects.SLOWNESS, STAGGER_TICKS, 3));
		world.spawnParticles(ParticleTypes.CRIT, mob.getX(), mob.getEyeY() + 0.3, mob.getZ(), 12, 0.3, 0.1, 0.3, 0.1);
		world.playSound(null, mob.getX(), mob.getY(), mob.getZ(), SoundEvents.ENTITY_ZOMBIE_ATTACK_WOODEN_DOOR, SoundCategory.HOSTILE, 1.0F, 0.6F);
	}

	private static void kickUpGround(ServerWorld world, MobEntity mob, int count) {
		BlockState ground = world.getBlockState(mob.getSteppingPos());
		if (!ground.isAir()) {
			world.spawnParticles(new BlockStateParticleEffect(ParticleTypes.BLOCK, ground), mob.getX(), mob.getY() + 0.1, mob.getZ(), count,
					0.4, 0.05, 0.4, 0.15);
		}
	}

	private static void unplant(MobEntity mob) {
		EntityAttributeInstance speed = mob.getAttributeInstance(EntityAttributes.GENERIC_MOVEMENT_SPEED);
		if (speed != null) {
			speed.removeModifier(PLANTED);
		}
	}

	/** Bows, crossbows and tridents: a Brute that shoots does not charge. */
	private static boolean holdsRanged(MobEntity mob) {
		return mob.getMainHandStack().getItem() instanceof RangedWeaponItem || mob.getMainHandStack().getItem() instanceof TridentItem;
	}
}
