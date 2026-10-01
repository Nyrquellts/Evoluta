package com.nyr.evoluta.common.tactics;

import it.unimi.dsi.fastutil.objects.ReferenceArrayList;
import java.util.List;
import net.minecraft.entity.Entity;
import net.minecraft.entity.mob.MobEntity;

/**
 * The loaded mutants of one world, split by {@code tickOffset = entityId % INTERVAL}. The mutants whose
 * {@code (worldTime + tickOffset) % INTERVAL == 0} are due, so each tick touches one bucket and every mutant runs
 * once every {@link Tactics#INTERVAL} ticks, spread evenly instead of all at once. Server thread only.
 */
public final class MutantBuckets {
	private final List<ReferenceArrayList<MobEntity>> buckets;
	private int size;

	public MutantBuckets() {
		ReferenceArrayList<ReferenceArrayList<MobEntity>> buckets = new ReferenceArrayList<>(Tactics.INTERVAL);
		for (int i = 0; i < Tactics.INTERVAL; i++) {
			buckets.add(new ReferenceArrayList<>());
		}
		this.buckets = buckets;
	}

	public static int tickOffset(Entity entity) {
		return Math.floorMod(entity.getId(), Tactics.INTERVAL);
	}

	/** The tick offset whose mutants are due at {@code worldTime}. */
	public static int dueOffset(long worldTime) {
		return (int) Math.floorMod(-worldTime, (long) Tactics.INTERVAL);
	}

	public boolean add(MobEntity mob) {
		ReferenceArrayList<MobEntity> bucket = this.buckets.get(tickOffset(mob));
		if (bucket.contains(mob)) {
			return false;
		}
		bucket.add(mob);
		this.size++;
		return true;
	}

	public boolean remove(MobEntity mob) {
		if (this.buckets.get(tickOffset(mob)).remove(mob)) {
			this.size--;
			return true;
		}
		return false;
	}

	public boolean contains(MobEntity mob) {
		return this.buckets.get(tickOffset(mob)).contains(mob);
	}

	/** The mutants due this tick. Index it rather than iterate it; do not keep it past the tick. */
	public List<MobEntity> due(long worldTime) {
		return this.buckets.get(dueOffset(worldTime));
	}

	public int size() {
		return this.size;
	}
}
