package com.nyr.evoluta.common.tactics;

import com.nyr.evoluta.common.combat.Hazards;
import com.nyr.evoluta.common.mutation.Archetype;
import com.nyr.evoluta.common.mutation.Element;
import com.nyr.evoluta.common.mutation.MutationData;
import com.nyr.evoluta.common.mutation.Mutations;
import com.nyr.evoluta.common.mutation.Tier;
import com.nyr.evoluta.common.world.PlayerSnapshot;
import com.nyr.evoluta.common.world.WorldState;
import net.minecraft.entity.Entity;
import net.minecraft.entity.mob.MobEntity;
import net.minecraft.entity.player.PlayerEntity;
import net.minecraft.server.network.ServerPlayerEntity;
import net.minecraft.server.world.ServerWorld;
import net.minecraft.util.math.ChunkPos;

/**
 * The per-mutant behaviour that has to look at the world instead of waiting for an event. Runs once every
 * {@link #INTERVAL} ticks per mutant, and not at all while no player is within {@link #ACTIVE_RANGE}: a mutant
 * nobody is near keeps no tactical state and costs one distance check.
 */
public final class Tactics {
	public static final int INTERVAL = 10;
	public static final double ACTIVE_RANGE = 32;

	/** Players this close to a Permafrost mutant start to freeze. */
	public static final double CHILL_AURA_RANGE = 3.0;
	/** Frozen ticks added per tactical tick; vanilla thaws 2 per tick, so a player standing in the aura nets +10. */
	public static final int CHILL_AURA_TICKS = 30;

	private Tactics() {
	}

	/**
	 * Whether a mutant has anything to do on the tactical tick. Plain Evolved mutants and Evolved Brutes without an
	 * element act only through combat events, so they are never visited at all.
	 */
	public static boolean needsTicks(MutationData data) {
		return data.isChampion() || hasTacticalWork(data);
	}

	/**
	 * Stalking, Apex hunting and ambushes, Brute charges, each element's trail and the Permafrost aura: the behaviour
	 * that needs a player near.
	 */
	static boolean hasTacticalWork(MutationData data) {
		return hunts(data) || BruteCharge.charges(data) || Hazards.groundHazard(data.getElement(), data.getTier()) != null;
	}

	/**
	 * Stalkers hunt by their target's gaze, and so does every Apex, whatever its archetype: all of them sprint at a
	 * turned back, weave while watched and lunge; none holds back (see {@link Stalking}).
	 */
	public static boolean hunts(MutationData data) {
		return data.getArchetype() == Archetype.STALKER || data.getTier() == Tier.APEX;
	}

	public static void tick(MobEntity mob, ServerWorld world, long worldTime, WorldState state) {
		MutationData data = Mutations.get(mob);
		if (data == null || !mob.isAlive()) {
			return;
		}
		if (data.isChampion()) {
			// champions wander even when no player is near: keep the locator's index on the right chunk
			ChunkPos chunk = mob.getChunkPos();
			state.champions.put(mob, chunk.x, chunk.z);
		}
		if (!hasTacticalWork(data)) {
			return;
		}
		boolean hunter = hunts(data);
		boolean charger = BruteCharge.charges(data);
		if (!state.players.anyWithin(mob.getX(), mob.getY(), mob.getZ(), ACTIVE_RANGE)) {
			if (hunter) {
				Stalking.rest(mob);
			}
			if (charger) {
				BruteCharge.rest(mob);
			}
			return;
		}
		if (data.getTier() == Tier.APEX && Ambush.tick(mob, world, state.players)) {
			// lying in wait: nothing moves, nothing trails
			Stalking.rest(mob);
			BruteCharge.rest(mob);
			return;
		}
		// a planted or rushing Brute belongs to its charge: no pace, no lunge
		boolean charging = charger && BruteCharge.tick(world, mob, worldTime, state);
		if (hunter && !charging) {
			Stalking.tick(mob, worldTime, data.getTier() == Tier.APEX, data.getArchetype() == Archetype.STALKER);
		}
		// a trail block every other visit: 20 ticks apart, each standing 60
		if ((worldTime / INTERVAL) % 2 == 0) {
			Hazards.leaveTrail(world, mob, data);
		}
		if (data.getElement() == Element.PERMAFROST) {
			chill(state.players, mob, data);
		}
	}

	/** Drops every piece of tactical state, as when no player is near or the mob stops being a mutant. */
	public static void rest(MobEntity mob) {
		Stalking.rest(mob);
		BruteCharge.rest(mob);
	}

	/** Stalkers move without step sounds or step vibrations. */
	public static boolean movesSilently(Entity entity) {
		if (!(entity instanceof MobEntity)) {
			return false;
		}
		MutationData data = Mutations.get(entity);
		return data != null && data.getArchetype() == Archetype.STALKER;
	}

	/** True when {@code other} is behind the player: the player's facing points away from it. */
	public static boolean isBehind(PlayerEntity player, Entity other) {
		return Stalking.facing(player, other) < 0;
	}

	private static void chill(PlayerSnapshot players, MobEntity mob, MutationData data) {
		for (int i = 0; i < players.size(); i++) {
			ServerPlayerEntity player = players.within(i, mob.getX(), mob.getY(), mob.getZ(), CHILL_AURA_RANGE);
			if (player != null && Hazards.isVulnerablePlayer(player)) {
				Hazards.freeze(player, CHILL_AURA_TICKS, data.getTier());
			}
		}
	}
}
