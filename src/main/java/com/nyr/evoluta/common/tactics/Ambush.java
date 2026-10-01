package com.nyr.evoluta.common.tactics;

import com.nyr.evoluta.common.Evoluta;
import com.nyr.evoluta.common.combat.CombatEvents;
import com.nyr.evoluta.common.combat.Hazards;
import com.nyr.evoluta.common.combat.Juice;
import com.nyr.evoluta.common.world.PlayerSnapshot;
import net.minecraft.entity.LivingEntity;
import net.minecraft.entity.attribute.EntityAttributeInstance;
import net.minecraft.entity.attribute.EntityAttributeModifier;
import net.minecraft.entity.attribute.EntityAttributes;
import net.minecraft.entity.mob.MobEntity;
import net.minecraft.entity.mob.Monster;
import net.minecraft.entity.player.PlayerEntity;
import net.minecraft.particle.ParticleTypes;
import net.minecraft.server.network.ServerPlayerEntity;
import net.minecraft.server.world.ServerWorld;
import net.minecraft.sound.SoundCategory;
import net.minecraft.sound.SoundEvents;
import net.minecraft.util.Identifier;
import org.jetbrains.annotations.Nullable;

/**
 * Apex mutants lie in wait. An Apex monster with no target, somewhere dark (light {@link #DARK} or less), stands still
 * and silent, its eyes still glowing, and takes no target however long it sees one. It wakes with a roar when a
 * player comes within {@link #WAKE_CLOSE} blocks, turns their back to it within {@link #WAKE_BACK} blocks in its
 * sight, or hurts it: that player becomes its target, and idle mutants within 16 blocks join the hunt. Waiting is
 * one temporary speed modifier, so it is never saved: a reloaded Apex simply lies in wait again if it is still dark.
 * Checked on the tactical tick, which already stops while no player is within 32 blocks.
 */
public final class Ambush {
	public static final Identifier STILL = Evoluta.id("ambush_still");
	public static final int DARK = 7;
	public static final double WAKE_CLOSE = 6;
	public static final double WAKE_BACK = 16;
	/** Within the waking range, a player whose facing is below this has their back to the Apex. */
	static final double BACK_TURNED = 0.2;

	private Ambush() {
	}

	public static boolean isWaiting(MobEntity mob) {
		EntityAttributeInstance speed = mob.getAttributeInstance(EntityAttributes.GENERIC_MOVEMENT_SPEED);
		return speed != null && speed.hasModifier(STILL);
	}

	/**
	 * One tactical visit to an Apex. Returns true while it lies in wait (the caller skips its hunting), false once it
	 * is awake.
	 */
	public static boolean tick(MobEntity mob, ServerWorld world, PlayerSnapshot players) {
		if (isWaiting(mob)) {
			PlayerEntity waker = waker(mob, players);
			if (waker == null) {
				return true;
			}
			wake(world, mob, waker);
			return false;
		}
		if (!(mob instanceof Monster) || mob.getTarget() != null || world.getLightLevel(mob.getBlockPos()) > DARK || waker(mob, players) != null) {
			return false;
		}
		lieInWait(mob);
		return true;
	}

	/** Stops the Apex where it stands. */
	public static void lieInWait(MobEntity mob) {
		EntityAttributeInstance speed = mob.getAttributeInstance(EntityAttributes.GENERIC_MOVEMENT_SPEED);
		if (speed == null || speed.hasModifier(STILL)) {
			return;
		}
		speed.addTemporaryModifier(new EntityAttributeModifier(STILL, -1.0, EntityAttributeModifier.Operation.ADD_MULTIPLIED_TOTAL));
		mob.getNavigation().stop();
		mob.setTarget(null);
	}

	/** Ends the wait with a roar; a player who woke it is its target, and nearby idle mutants join in. */
	public static void wake(ServerWorld world, MobEntity mob, @Nullable LivingEntity cause) {
		EntityAttributeInstance speed = mob.getAttributeInstance(EntityAttributes.GENERIC_MOVEMENT_SPEED);
		if (speed == null || !speed.hasModifier(STILL)) {
			return;
		}
		speed.removeModifier(STILL);
		world.playSound(null, mob.getX(), mob.getY(), mob.getZ(), SoundEvents.ENTITY_RAVAGER_ROAR, SoundCategory.HOSTILE, 1.1F, 1.35F);
		Juice.shakeAround(world, mob.getPos(), 16, Juice.ROAR_TRAUMA);
		world.spawnParticles(ParticleTypes.SCULK_SOUL, mob.getX(), mob.getEyeY(), mob.getZ(), 12, 0.4, 0.3, 0.4, 0.04);
		if (cause instanceof PlayerEntity player && Hazards.isVulnerablePlayer(player)) {
			mob.setTarget(player);
			CombatEvents.callTheHunt(world, mob, player);
		}
	}

	/**
	 * A player close enough to hear, or else the nearest one with their back to the Apex within its sight; null when
	 * nobody wakes it. At most one line-of-sight raycast per visit, for that nearest turned back.
	 */
	@Nullable
	static PlayerEntity waker(MobEntity mob, PlayerSnapshot players) {
		ServerPlayerEntity turned = null;
		double turnedDistance = Double.MAX_VALUE;
		for (int i = 0; i < players.size(); i++) {
			ServerPlayerEntity player = players.within(i, mob.getX(), mob.getY(), mob.getZ(), WAKE_BACK);
			if (player == null || !Hazards.isVulnerablePlayer(player)) {
				continue;
			}
			double distance = mob.squaredDistanceTo(player);
			if (distance <= WAKE_CLOSE * WAKE_CLOSE) {
				return player;
			}
			if (distance < turnedDistance && Stalking.facing(player, mob) < BACK_TURNED) {
				turned = player;
				turnedDistance = distance;
			}
		}
		return turned != null && mob.canSee(turned) ? turned : null;
	}
}
