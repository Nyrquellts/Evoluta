package com.nyr.evoluta.common.combat;

import com.nyr.evoluta.common.mutation.Archetype;
import com.nyr.evoluta.common.mutation.Element;
import com.nyr.evoluta.common.mutation.MutationData;
import com.nyr.evoluta.common.mutation.Mutations;
import com.nyr.evoluta.common.mutation.Tier;
import com.nyr.evoluta.common.tactics.Ambush;
import com.nyr.evoluta.common.world.EvolutaWorlds;
import net.fabricmc.fabric.api.entity.event.v1.ServerLivingEntityEvents;
import net.minecraft.entity.EntityStatuses;
import net.minecraft.entity.LivingEntity;
import net.minecraft.entity.damage.DamageSource;
import net.minecraft.entity.effect.StatusEffectInstance;
import net.minecraft.entity.effect.StatusEffects;
import net.minecraft.entity.mob.MobEntity;
import net.minecraft.entity.player.PlayerEntity;
import net.minecraft.item.Item;
import net.minecraft.item.ItemStack;
import net.minecraft.item.Items;
import net.minecraft.registry.tag.DamageTypeTags;
import net.minecraft.server.network.ServerPlayerEntity;
import net.minecraft.server.world.ServerWorld;
import net.minecraft.util.math.Box;

/**
 * Everything a mutant does in a fight, driven by damage and death events only: nothing here polls for collisions
 * or runs per tick.
 */
public final class CombatEvents {
	/** Brutes lock the shield they hit for 3 seconds. */
	public static final int SHIELD_LOCK_TICKS = 60;
	/** Stalker projectiles mark their target, and call idle mutants this close to hunt it. */
	public static final double HUNT_CALL_RANGE = 16;

	private CombatEvents() {
	}

	public static void register() {
		ServerLivingEntityEvents.ALLOW_DAMAGE.register(CombatEvents::allowDamage);
		ServerLivingEntityEvents.AFTER_DAMAGE.register(CombatEvents::afterDamage);
		ServerLivingEntityEvents.AFTER_DEATH.register(CombatEvents::afterDeath);
	}

	/** Permafrost mutants do not freeze. */
	static boolean allowDamage(LivingEntity victim, DamageSource source, float amount) {
		if (!source.isIn(DamageTypeTags.IS_FREEZING)) {
			return true;
		}
		MutationData data = Mutations.get(victim);
		return data == null || data.getElement() != Element.PERMAFROST;
	}

	static void afterDamage(LivingEntity victim, DamageSource source, float baseDamageTaken, float damageTaken, boolean blocked) {
		if (victim instanceof MobEntity waiting && victim.getWorld() instanceof ServerWorld hurtIn && Ambush.isWaiting(waiting)) {
			// an Apex lying in wait that gets hurt is done waiting
			Ambush.wake(hurtIn, waiting, source.getAttacker() instanceof LivingEntity attacker ? attacker : null);
		}
		if (!(source.getAttacker() instanceof MobEntity attacker) || !(victim.getWorld() instanceof ServerWorld world)) {
			return;
		}
		MutationData data = Mutations.get(attacker);
		if (data == null) {
			return;
		}
		long start = System.nanoTime();
		boolean projectile = source.isIn(DamageTypeTags.IS_PROJECTILE);
		if (blocked) {
			if (data.getArchetype() == Archetype.BRUTE && !projectile && victim instanceof PlayerEntity player) {
				lockShield(player);
			}
		} else if (damageTaken > 0) {
			Tier tier = data.getTier();
			switch (data.getElement()) {
				case IGNITED -> victim.setOnFireFor(igniteSeconds(tier));
				case PERMAFROST -> chill(victim, tier);
				default -> {
				}
			}
			// the blow lands: its element bursts out of the victim, and a player feels it
			Juice.impact(world, victim, data.getElement(), Juice.strength(tier));
			if (victim instanceof ServerPlayerEntity player) {
				Juice.shake(player, Juice.hitTrauma(data));
			}
			if (data.getArchetype() == Archetype.STALKER && projectile) {
				markForTheHunt(world, attacker, victim, tier);
			}
		}
		EvolutaWorlds.of(world).meter.add(System.nanoTime() - start);
	}

	static void afterDeath(LivingEntity entity, DamageSource source) {
		if (entity instanceof MobEntity mob && mob.getWorld() instanceof ServerWorld world) {
			MutationData data = Mutations.get(mob);
			if (data != null) {
				long start = System.nanoTime();
				Hazards.onDeathBurst(world, mob, data);
				Juice.soulBurst(world, mob, data);
				EvolutaWorlds.of(world).meter.add(System.nanoTime() - start);
			}
		}
	}

	/** Ignited hits burn for 3, 4 or 5 seconds by tier. */
	public static float igniteSeconds(Tier tier) {
		return 2.0F + tier.id();
	}

	/**
	 * Permafrost hits freeze (vanilla freezing: the frost overlay and up to half speed as it builds) and numb the
	 * hands: Mining Fatigue, which in vanilla also takes 10% off attack speed per level.
	 */
	static void chill(LivingEntity victim, Tier tier) {
		Hazards.freeze(victim, 30 + 30 * tier.id(), tier);
		victim.addStatusEffect(new StatusEffectInstance(StatusEffects.MINING_FATIGUE, 40 + 20 * tier.id(), tier == Tier.APEX ? 1 : 0));
	}

	/** Like an axe, but on any Brute attack and for 3 seconds: the shield drops and cannot be raised. */
	static void lockShield(PlayerEntity player) {
		ItemStack active = player.getActiveItem();
		Item shield = active.isEmpty() ? Items.SHIELD : active.getItem();
		if (player.getItemCooldownManager().isCoolingDown(shield)) {
			return;
		}
		player.getItemCooldownManager().set(shield, SHIELD_LOCK_TICKS);
		player.clearActiveItem();
		player.getWorld().sendEntityStatus(player, EntityStatuses.BREAK_SHIELD);
	}

	/** A Stalker's arrow marks its target with Glowing and sends idle mutants nearby after a marked player. */
	static void markForTheHunt(ServerWorld world, MobEntity stalker, LivingEntity victim, Tier tier) {
		victim.addStatusEffect(new StatusEffectInstance(StatusEffects.GLOWING, 60 + 20 * tier.id(), 0), stalker);
		if (victim instanceof PlayerEntity player && Hazards.isVulnerablePlayer(player)) {
			callTheHunt(world, stalker, player);
		}
	}

	/** Sends idle mutants within {@link #HUNT_CALL_RANGE} blocks of {@code caller} after {@code player}; an Apex lying in wait stays put. */
	public static void callTheHunt(ServerWorld world, MobEntity caller, PlayerEntity player) {
		Box area = caller.getBoundingBox().expand(HUNT_CALL_RANGE);
		for (MobEntity ally : world.getEntitiesByClass(MobEntity.class, area,
				mob -> mob != caller && mob.isAlive() && mob.getTarget() == null && Mutations.get(mob) != null && !Ambush.isWaiting(mob))) {
			ally.setTarget(player);
		}
	}
}
