package com.nyr.evoluta.common.mutation;

import com.nyr.evoluta.common.Evoluta;
import com.nyr.evoluta.common.config.EvolutaConfig;
import com.nyr.evoluta.common.soul.MutantGear;
import com.nyr.evoluta.common.tag.EvolutaTags;
import com.nyr.evoluta.common.world.EvolutaWorlds;
import com.nyr.evoluta.common.world.WorldState;
import java.util.Locale;
import java.util.Set;
import net.fabricmc.fabric.api.entity.event.v1.ServerLivingEntityEvents;
import net.fabricmc.fabric.api.event.lifecycle.v1.CommonLifecycleEvents;
import net.fabricmc.fabric.api.event.lifecycle.v1.ServerEntityEvents;
import net.minecraft.entity.Entity;
import net.minecraft.entity.EntityType;
import net.minecraft.entity.SpawnReason;
import net.minecraft.entity.mob.MobEntity;
import net.minecraft.registry.DynamicRegistryManager;
import net.minecraft.registry.Registries;
import net.minecraft.registry.entry.RegistryEntry;
import net.minecraft.server.world.ServerWorld;
import net.minecraft.util.math.ChunkPos;
import net.minecraft.world.LocalDifficulty;
import org.jetbrains.annotations.Nullable;

/** Where mobs become mutants: at spawn, on conversion (zombie to drowned and the like) and when loaded. */
public final class SpawnMutations {
	private SpawnMutations() {
	}

	public static void register() {
		ServerEntityEvents.ENTITY_LOAD.register(SpawnMutations::onLoad);
		ServerLivingEntityEvents.MOB_CONVERSION.register(SpawnMutations::onConversion);
		CommonLifecycleEvents.TAGS_LOADED.register(SpawnMutations::logTags);
	}

	/** Command tags that carry a spawn's roll from a world-generation thread to the mob's first load. */
	public static final String PENDING_TAG = "evoluta.pending_roll.";

	/**
	 * Called when {@code MobEntity#initialize} returns, which every spawn path runs before the mob enters the world:
	 * natural spawning, structures, patrols, reinforcements, spawners, eggs and summons.
	 *
	 * <p>Structure and chunk-generation spawns run on world-generation threads, where neither the champion index nor
	 * Fabric's attachment sync may be touched. There the mob only gets a command tag naming the spawn reason, and
	 * rolls when it first loads on the server thread.
	 */
	public static void onInitialize(MobEntity mob, ServerWorld world, LocalDifficulty difficulty, SpawnReason reason) {
		if (!world.getServer().isOnThread()) {
			EvolutaConfig config = EvolutaConfig.get();
			if (config.spawnReasons().contains(reason) && EvolutaTags.canMutate(mob.getType())) {
				mob.addCommandTag(PENDING_TAG + reason.name().toLowerCase(Locale.ROOT));
			}
			return;
		}
		tryMutate(mob, world, difficulty, reason, EvolutaConfig.get());
	}

	/** Rolls a mutation for {@code mob} and applies it. Returns what it rolled, or null when the mob stays ordinary. */
	@Nullable
	public static MutationData tryMutate(MobEntity mob, ServerWorld world, LocalDifficulty difficulty, SpawnReason reason,
			EvolutaConfig config) {
		if (Mutations.get(mob) != null || !config.spawnReasons().contains(reason)) {
			return null;
		}
		EntityType<?> type = mob.getType();
		if (!EvolutaTags.canMutate(type) || config.disabledDimensions().contains(world.getRegistryKey().getValue())) {
			return null;
		}
		MutationRoller.Conditions conditions = new MutationRoller.Conditions(
				world.isNight(),
				difficulty.getClampedLocalDifficulty(),
				type.isIn(EvolutaTags.BRUTES),
				type.isIn(EvolutaTags.STALKERS),
				championsIn(world, mob) < config.maxChampionsPerChunk()
		);
		MutationData data = MutationRoller.roll(mob.getRandom(), config, conditions);
		if (data != null) {
			Mutations.apply(mob, data, config);
		}
		return data;
	}

	/** Champions already loaded in the chunk the mob is spawning in. */
	private static int championsIn(ServerWorld world, MobEntity mob) {
		WorldState state = EvolutaWorlds.peek(world);
		if (state == null) {
			return 0;
		}
		ChunkPos chunk = mob.getChunkPos();
		return state.champions.countIn(chunk.x, chunk.z);
	}

	/**
	 * A mob spawned by world generation rolls here, on its first load. A mutant entering the world gets its
	 * modifiers checked against the current config: a summon with NBT, a conversion or a changed config all end up
	 * with the same stats as a fresh spawn.
	 */
	private static void onLoad(Entity entity, ServerWorld world) {
		if (!(entity instanceof MobEntity mob)) {
			return;
		}
		SpawnReason pending = takePendingRoll(mob);
		if (pending != null) {
			MutationData rolled = tryMutate(mob, world, world.getLocalDifficulty(mob.getBlockPos()), pending, EvolutaConfig.get());
			if (rolled != null) {
				// a skeleton picked up its bow in initialize, after the roll would have run
				Mutations.equipLoadout(mob, rolled);
				MutantGear.graftOnce(mob, rolled, mob.getRandom());
			}
			return;
		}
		MutationData data = Mutations.get(mob);
		if (data != null) {
			Mutations.syncAttributes(mob, data, EvolutaConfig.get());
			// by now the mob's kind has handed it whatever gear it spawns with
			MutantGear.graftOnce(mob, data, mob.getRandom());
		}
	}

	/** Removes the pending-roll tag, if any, and returns the spawn reason it carried. */
	@Nullable
	private static SpawnReason takePendingRoll(MobEntity mob) {
		Set<String> tags = mob.getCommandTags();
		if (tags.isEmpty()) {
			return null;
		}
		String pending = null;
		for (String tag : tags) {
			if (tag.startsWith(PENDING_TAG)) {
				pending = tag;
				break;
			}
		}
		if (pending == null) {
			return null;
		}
		mob.removeCommandTag(pending);
		try {
			return SpawnReason.valueOf(pending.substring(PENDING_TAG.length()).toUpperCase(Locale.ROOT));
		} catch (IllegalArgumentException e) {
			return null;
		}
	}

	/** The converted mob inherits the mutation when its own type may mutate, minus an archetype it may not take. */
	private static void onConversion(MobEntity previous, MobEntity converted, boolean keepEquipment) {
		MutationData data = Mutations.get(previous);
		if (data == null || !EvolutaTags.canMutate(converted.getType())) {
			return;
		}
		if (!EvolutaTags.canBe(converted.getType(), data.getArchetype())) {
			data = data.withArchetype(Archetype.NONE);
		}
		Mutations.apply(converted, data, EvolutaConfig.get());
		Mutations.equipLoadout(converted, data);
	}

	private static void logTags(DynamicRegistryManager registries, boolean client) {
		if (client) {
			return;
		}
		int eligible = 0;
		int blocked = 0;
		for (RegistryEntry<EntityType<?>> entry : Registries.ENTITY_TYPE.iterateEntries(EvolutaTags.CAN_MUTATE)) {
			if (entry.value().isIn(EvolutaTags.BLACKLIST)) {
				blocked++;
			} else {
				eligible++;
			}
		}
		Evoluta.LOGGER.info("#evoluta:can_mutate holds {} mob types that can mutate ({} more are blacklisted)", eligible, blocked);
	}
}
