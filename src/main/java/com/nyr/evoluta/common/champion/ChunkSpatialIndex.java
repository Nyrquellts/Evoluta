package com.nyr.evoluta.common.champion;

import it.unimi.dsi.fastutil.longs.Long2ObjectOpenHashMap;
import it.unimi.dsi.fastutil.objects.Reference2LongOpenHashMap;
import it.unimi.dsi.fastutil.objects.ReferenceArrayList;
import java.util.function.Predicate;
import java.util.function.ToDoubleFunction;
import org.jetbrains.annotations.Nullable;

/**
 * Values by chunk: a hash from chunk key to what was last seen in that chunk, plus each value's chunk, so adding,
 * moving and removing are O(1). The nearest-value query walks rings of chunks outward and stops as soon as no
 * unvisited ring can hold anything closer, so it costs at most (2r+1)^2 hash lookups for a search radius of r
 * chunks, however many values or entities the world holds. Not thread-safe.
 */
public final class ChunkSpatialIndex<T> {
	private static final long ABSENT = Long.MIN_VALUE;

	private final Long2ObjectOpenHashMap<ReferenceArrayList<T>> cells = new Long2ObjectOpenHashMap<>();
	private final Reference2LongOpenHashMap<T> cellOf = new Reference2LongOpenHashMap<>();

	public ChunkSpatialIndex() {
		this.cellOf.defaultReturnValue(ABSENT);
	}

	/** The same packing as {@code ChunkPos#toLong}. */
	public static long key(int chunkX, int chunkZ) {
		return chunkX & 0xFFFFFFFFL | (chunkZ & 0xFFFFFFFFL) << 32;
	}

	/** Indexes {@code value} in chunk (x, z), moving it if it was elsewhere. Returns true if it was not indexed. */
	public boolean put(T value, int chunkX, int chunkZ) {
		long key = key(chunkX, chunkZ);
		long previous = this.cellOf.put(value, key);
		if (previous == key) {
			return false;
		}
		if (previous != ABSENT) {
			this.removeFromCell(previous, value);
		}
		this.cells.computeIfAbsent(key, k -> new ReferenceArrayList<>(2)).add(value);
		return previous == ABSENT;
	}

	public boolean remove(T value) {
		long previous = this.cellOf.removeLong(value);
		if (previous == ABSENT) {
			return false;
		}
		this.removeFromCell(previous, value);
		return true;
	}

	public boolean contains(T value) {
		return this.cellOf.containsKey(value);
	}

	/** Whether {@code value} is indexed in chunk (x, z). */
	public boolean isIn(T value, int chunkX, int chunkZ) {
		return this.cellOf.getLong(value) == key(chunkX, chunkZ);
	}

	public int size() {
		return this.cellOf.size();
	}

	/** How many values are indexed in chunk (x, z). */
	public int countIn(int chunkX, int chunkZ) {
		ReferenceArrayList<T> cell = this.cells.get(key(chunkX, chunkZ));
		return cell == null ? 0 : cell.size();
	}

	/**
	 * The value nearest to block position (x, z) on the horizontal plane, within {@code maxDistance} blocks, among
	 * those that pass {@code filter}; null if there is none.
	 */
	@Nullable
	public T nearest(double x, double z, double maxDistance, ToDoubleFunction<T> xOf, ToDoubleFunction<T> zOf, Predicate<T> filter) {
		if (this.cellOf.isEmpty() || maxDistance <= 0) {
			return null;
		}
		int chunkX = Math.floorDiv((int) Math.floor(x), 16);
		int chunkZ = Math.floorDiv((int) Math.floor(z), 16);
		double inX = x - chunkX * 16.0;
		double inZ = z - chunkZ * 16.0;
		// how far the query point is from the nearest edge of its own chunk
		double edge = Math.min(Math.min(inX, 16.0 - inX), Math.min(inZ, 16.0 - inZ));
		int rings = (int) Math.ceil(maxDistance / 16.0);
		Search<T> search = new Search<>(x, z, maxDistance * maxDistance, xOf, zOf, filter);
		for (int ring = 0; ring <= rings; ring++) {
			if (ring > 0) {
				// every block of ring r is at least (r - 1) chunks plus the edge distance away
				double closest = (ring - 1) * 16.0 + edge;
				if (closest * closest >= search.bestDistanceSq) {
					break;
				}
			}
			if (ring == 0) {
				search.scan(this.cells.get(key(chunkX, chunkZ)));
				continue;
			}
			for (int dx = -ring; dx <= ring; dx++) {
				search.scan(this.cells.get(key(chunkX + dx, chunkZ - ring)));
				search.scan(this.cells.get(key(chunkX + dx, chunkZ + ring)));
			}
			for (int dz = -ring + 1; dz <= ring - 1; dz++) {
				search.scan(this.cells.get(key(chunkX - ring, chunkZ + dz)));
				search.scan(this.cells.get(key(chunkX + ring, chunkZ + dz)));
			}
		}
		return search.best;
	}

	private void removeFromCell(long key, T value) {
		ReferenceArrayList<T> cell = this.cells.get(key);
		if (cell != null) {
			cell.remove(value);
			if (cell.isEmpty()) {
				this.cells.remove(key);
			}
		}
	}

	private static final class Search<T> {
		private final double x;
		private final double z;
		private final ToDoubleFunction<T> xOf;
		private final ToDoubleFunction<T> zOf;
		private final Predicate<T> filter;
		private double bestDistanceSq;
		@Nullable
		private T best;

		Search(double x, double z, double maxDistanceSq, ToDoubleFunction<T> xOf, ToDoubleFunction<T> zOf, Predicate<T> filter) {
			this.x = x;
			this.z = z;
			this.bestDistanceSq = maxDistanceSq;
			this.xOf = xOf;
			this.zOf = zOf;
			this.filter = filter;
		}

		void scan(@Nullable ReferenceArrayList<T> cell) {
			if (cell == null) {
				return;
			}
			for (int i = 0; i < cell.size(); i++) {
				T value = cell.get(i);
				double dx = this.xOf.applyAsDouble(value) - this.x;
				double dz = this.zOf.applyAsDouble(value) - this.z;
				double distanceSq = dx * dx + dz * dz;
				if (distanceSq <= this.bestDistanceSq && (this.best == null || distanceSq < this.bestDistanceSq) && this.filter.test(value)) {
					this.bestDistanceSq = distanceSq;
					this.best = value;
				}
			}
		}
	}
}
