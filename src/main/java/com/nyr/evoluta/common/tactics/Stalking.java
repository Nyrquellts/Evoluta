package com.nyr.evoluta.common.tactics;

import com.nyr.evoluta.common.Evoluta;
import com.nyr.evoluta.common.combat.Hazards;
import com.nyr.evoluta.common.mutation.EvolutaAttachments;
import net.minecraft.entity.Entity;
import net.minecraft.entity.attribute.EntityAttributeInstance;
import net.minecraft.entity.attribute.EntityAttributeModifier;
import net.minecraft.entity.attribute.EntityAttributes;
import net.minecraft.entity.mob.MobEntity;
import net.minecraft.entity.player.PlayerEntity;
import net.minecraft.item.RangedWeaponItem;
import net.minecraft.sound.SoundCategory;
import net.minecraft.sound.SoundEvents;
import net.minecraft.util.Identifier;
import net.minecraft.util.math.Vec3d;

/**
 * The Stalker hunts by sight. Within {@link #RANGE} blocks of its target it sprints while the target's back is turned
 * and, close enough, lunges without a sound; while the target looks at it, it keeps coming, weaving side to side across
 * the target's view. Nothing that hunts holds back, because one that stays back can be bullied. Every Apex
 * hunts this way, whatever its archetype, and harder: it comes on faster while watched ({@link #ADVANCE}) and lunges
 * whichever way the target faces. Everything is read on the tactical tick (once every 10 ticks per mob), so the
 * reaction lags by up to half a second: it feels like a predator deciding, and costs a few multiplications.
 */
public final class Stalking {
	/** Stalking starts inside this many blocks of the target. */
	public static final double RANGE = 14;
	/** One speed modifier whose value is the Stalker's current pace. Temporary: never saved with the mob. */
	public static final Identifier PACE = Evoluta.id("stalker_pace");
	/** Pace while the target's back is turned: a 50% sprint. */
	public static final double BURST = 0.5;
	/** An Apex's pace while the target can see it: it comes on. A lesser Stalker keeps its own pace. */
	public static final double ADVANCE = 0.25;
	/** A player looks at a Stalker when it stands within 60 degrees of where they face. */
	public static final double WATCH_COS = 0.5;
	/** Inside this many blocks of an unaware target, a melee Stalker lunges. */
	public static final double LUNGE_RANGE = 4.5;
	public static final int LUNGE_COOLDOWN = 60;
	static final double LUNGE_SPEED = 0.75;
	static final double LUNGE_LIFT = 0.28;
	static final double SIDESTEP_SPEED = 0.42;
	static final float SIDESTEP_CHANCE = 0.4F;

	private Stalking() {
	}

	/**
	 * @param apex   comes on faster while watched and lunges whichever way the target faces, not only at its back
	 * @param silent a Stalker: its lunge makes no sound; anything else's comes with a rush of air
	 */
	public static void tick(MobEntity mob, long worldTime, boolean apex, boolean silent) {
		if (!(mob.getTarget() instanceof PlayerEntity player) || !Hazards.isVulnerablePlayer(player)
				|| mob.squaredDistanceTo(player) > RANGE * RANGE) {
			setPace(mob, 0);
			return;
		}
		double seen = facing(player, mob);
		boolean inReach = mob.squaredDistanceTo(player) <= LUNGE_RANGE * LUNGE_RANGE;
		if (seen < 0) {
			setPace(mob, BURST);
			if (inReach && canLunge(mob, worldTime)) {
				lunge(mob, player, worldTime, silent);
			}
			return;
		}
		setPace(mob, apex ? ADVANCE : 0);
		if (apex && inReach && canLunge(mob, worldTime)) {
			lunge(mob, player, worldTime, silent);
		} else if (seen >= WATCH_COS && mob.isOnGround() && mob.getRandom().nextFloat() < SIDESTEP_CHANCE) {
			// watched: it weaves across the target's view, and keeps coming
			sidestep(mob, player);
		}
	}

	/** Drops the pace, as when no player is near or the mob stops being a Stalker. */
	public static void rest(MobEntity mob) {
		setPace(mob, 0);
	}

	/**
	 * How squarely the player faces {@code other} on the horizontal plane: 1 dead ahead, 0 square to the side, -1
	 * straight behind. Something standing on the player counts as seen.
	 */
	public static double facing(PlayerEntity player, Entity other) {
		Vec3d facing = Vec3d.fromPolar(0.0F, player.getYaw());
		double dx = other.getX() - player.getX();
		double dz = other.getZ() - player.getZ();
		double length = Math.sqrt(dx * dx + dz * dz);
		return length < 1.0E-6 ? 1.0 : (facing.x * dx + facing.z * dz) / length;
	}

	public static double pace(MobEntity mob) {
		EntityAttributeInstance speed = mob.getAttributeInstance(EntityAttributes.GENERIC_MOVEMENT_SPEED);
		EntityAttributeModifier modifier = speed == null ? null : speed.getModifier(PACE);
		return modifier == null ? 0 : modifier.value();
	}

	static void setPace(MobEntity mob, double value) {
		EntityAttributeInstance speed = mob.getAttributeInstance(EntityAttributes.GENERIC_MOVEMENT_SPEED);
		if (speed == null) {
			return;
		}
		EntityAttributeModifier current = speed.getModifier(PACE);
		if (current != null && current.value() == value) {
			return;
		}
		if (current != null) {
			speed.removeModifier(PACE);
		}
		if (value != 0) {
			speed.addTemporaryModifier(new EntityAttributeModifier(PACE, value, EntityAttributeModifier.Operation.ADD_MULTIPLIED_BASE));
		}
	}

	/** On the ground, off cooldown, and fighting in melee: an archer Stalker keeps its distance and shoots instead. */
	private static boolean canLunge(MobEntity mob, long worldTime) {
		if (!mob.isOnGround() || mob.getMainHandStack().getItem() instanceof RangedWeaponItem) {
			return false;
		}
		Long last = mob.getAttached(EvolutaAttachments.STALKER_LAST_LUNGE);
		return last == null || worldTime - last >= LUNGE_COOLDOWN;
	}

	private static void lunge(MobEntity mob, PlayerEntity player, long worldTime, boolean silent) {
		Vec3d toward = new Vec3d(player.getX() - mob.getX(), 0, player.getZ() - mob.getZ()).normalize();
		mob.setVelocity(toward.x * LUNGE_SPEED, LUNGE_LIFT, toward.z * LUNGE_SPEED);
		mob.velocityModified = true;
		mob.setAttached(EvolutaAttachments.STALKER_LAST_LUNGE, worldTime);
		if (!silent) {
			mob.getWorld().playSound(null, mob.getX(), mob.getY(), mob.getZ(), SoundEvents.ENTITY_PHANTOM_SWOOP, SoundCategory.HOSTILE, 0.9F, 1.25F);
		}
	}

	/** A quick slide across the player's line of sight, left or right. */
	private static void sidestep(MobEntity mob, PlayerEntity player) {
		Vec3d facing = Vec3d.fromPolar(0.0F, player.getYaw());
		double side = mob.getRandom().nextBoolean() ? SIDESTEP_SPEED : -SIDESTEP_SPEED;
		mob.addVelocity(-facing.z * side, 0.12, facing.x * side);
		mob.velocityModified = true;
	}
}
