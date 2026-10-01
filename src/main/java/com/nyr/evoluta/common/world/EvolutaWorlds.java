package com.nyr.evoluta.common.world;

import com.nyr.evoluta.common.champion.LocatorSync;
import com.nyr.evoluta.common.combat.HazardBlocks;
import com.nyr.evoluta.common.mutation.MutationData;
import com.nyr.evoluta.common.mutation.Mutations;
import com.nyr.evoluta.common.tactics.BruteCharge;
import com.nyr.evoluta.common.tactics.Tactics;
import java.util.Collections;
import java.util.List;
import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;
import net.fabricmc.fabric.api.event.lifecycle.v1.ServerEntityEvents;
import net.fabricmc.fabric.api.event.lifecycle.v1.ServerTickEvents;
import net.fabricmc.fabric.api.event.lifecycle.v1.ServerWorldEvents;
import net.minecraft.entity.Entity;
import net.minecraft.entity.mob.MobEntity;
import net.minecraft.server.world.ServerWorld;
import net.minecraft.util.math.ChunkPos;
import org.jetbrains.annotations.Nullable;

/**
 * Keeps each world's {@link WorldState} in step with what is loaded, and runs the tactical tick and the locator
 * sync. A world that has never held a mutant has no state and costs one map lookup per tick.
 */
public final class EvolutaWorlds {
	private static final Map<ServerWorld, WorldState> STATES = new ConcurrentHashMap<>();

	private EvolutaWorlds() {
	}

	public static void register() {
		ServerEntityEvents.ENTITY_LOAD.register(EvolutaWorlds::onLoad);
		ServerEntityEvents.ENTITY_UNLOAD.register(EvolutaWorlds::onUnload);
		ServerTickEvents.END_WORLD_TICK.register(EvolutaWorlds::onEndTick);
		ServerWorldEvents.UNLOAD.register((server, world) -> STATES.remove(world));
	}

	public static WorldState of(ServerWorld world) {
		return STATES.computeIfAbsent(world, w -> new WorldState());
	}

	@Nullable
	public static WorldState peek(ServerWorld world) {
		return STATES.get(world);
	}

	public static Map<ServerWorld, WorldState> all() {
		return Collections.unmodifiableMap(STATES);
	}

	/** Brings tracking in line after a mob in the world gained, changed or lost its mutation (commands). */
	public static void refresh(MobEntity mob) {
		if (!(mob.getWorld() instanceof ServerWorld world) || mob.isRemoved() || world.getEntityById(mob.getId()) != mob) {
			return;
		}
		MutationData data = Mutations.get(mob);
		if (data != null) {
			track(of(world), mob, data);
			return;
		}
		WorldState state = peek(world);
		if (state != null) {
			forget(state, mob);
		}
		Tactics.rest(mob);
	}

	private static void track(WorldState state, MobEntity mob, MutationData data) {
		state.loaded.add(mob);
		if (Tactics.needsTicks(data)) {
			state.mutants.add(mob);
		} else {
			state.mutants.remove(mob);
			Tactics.rest(mob);
		}
		if (data.isChampion()) {
			ChunkPos chunk = mob.getChunkPos();
			state.champions.put(mob, chunk.x, chunk.z);
		} else {
			state.champions.remove(mob);
		}
	}

	private static void onLoad(Entity entity, ServerWorld world) {
		if (entity instanceof MobEntity mob) {
			MutationData data = Mutations.get(mob);
			if (data != null) {
				track(of(world), mob, data);
			}
		}
	}

	private static void onUnload(Entity entity, ServerWorld world) {
		WorldState state = peek(world);
		if (state == null) {
			return;
		}
		if (entity instanceof MobEntity mob) {
			forget(state, mob);
		}
	}

	private static void forget(WorldState state, MobEntity mob) {
		if (state.loaded.remove(mob)) {
			state.mutants.remove(mob);
			state.champions.remove(mob);
			state.charging.remove(mob);
		}
	}

	private static void onEndTick(ServerWorld world) {
		long time = world.getTime();
		long start = System.nanoTime();
		// before anything else: blast hazards saved with the world must go back even if no mutant has loaded yet
		HazardBlocks.tick(world);
		WorldState state = peek(world);
		if (state == null) {
			// no champion was ever here, but a player may still hold a locator and needs to hear there is nothing
			LocatorSync.tick(world, time, null);
			return;
		}
		List<MobEntity> due = state.mutants.due(time);
		if (!due.isEmpty()) {
			state.players.refresh(world, time);
			for (int i = 0; i < due.size(); i++) {
				Tactics.tick(due.get(i), world, time, state);
			}
		}
		if (!state.charging.isEmpty()) {
			BruteCharge.tickRushes(world, state, time);
		}
		LocatorSync.tick(world, time, state);
		state.meter.add(System.nanoTime() - start);
		state.meter.commit();
	}
}
