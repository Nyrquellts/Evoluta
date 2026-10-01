package com.nyr.evoluta.common.champion;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertSame;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.util.ArrayList;
import java.util.List;
import java.util.Random;
import org.junit.jupiter.api.Test;

class ChunkSpatialIndexTest {
	/** A stand-in for a mob: a mutable position. */
	static final class Point {
		double x;
		double z;
		boolean alive = true;

		Point(double x, double z) {
			this.x = x;
			this.z = z;
		}

		int chunkX() {
			return Math.floorDiv((int) Math.floor(this.x), 16);
		}

		int chunkZ() {
			return Math.floorDiv((int) Math.floor(this.z), 16);
		}
	}

	@Test
	void keysMatchVanillaChunkPosPacking() {
		assertEquals(net.minecraft.util.math.ChunkPos.toLong(-3, 7), ChunkSpatialIndex.key(-3, 7));
		assertEquals(net.minecraft.util.math.ChunkPos.toLong(1_875_000, -1_875_000), ChunkSpatialIndex.key(1_875_000, -1_875_000));
	}

	@Test
	void putMovesAndRemovesInPlace() {
		ChunkSpatialIndex<Point> index = new ChunkSpatialIndex<>();
		Point a = new Point(5, 5);
		assertTrue(index.put(a, 0, 0), "first put indexes");
		assertFalse(index.put(a, 0, 0), "same chunk again is a no-op");
		assertEquals(1, index.countIn(0, 0));
		assertFalse(index.put(a, 3, -2), "a move is not a new entry");
		assertEquals(0, index.countIn(0, 0));
		assertEquals(1, index.countIn(3, -2));
		assertTrue(index.isIn(a, 3, -2));
		assertFalse(index.isIn(a, 0, 0));
		assertEquals(1, index.size());
		assertTrue(index.remove(a));
		assertFalse(index.remove(a));
		assertEquals(0, index.size());
		assertEquals(0, index.countIn(3, -2));
	}

	@Test
	void nearestAgreesWithBruteForce() {
		Random random = new Random(20260923);
		for (int round = 0; round < 300; round++) {
			ChunkSpatialIndex<Point> index = new ChunkSpatialIndex<>();
			List<Point> points = new ArrayList<>();
			int count = random.nextInt(60);
			for (int i = 0; i < count; i++) {
				Point p = new Point(random.nextDouble() * 600 - 300, random.nextDouble() * 600 - 300);
				p.alive = random.nextInt(5) != 0;
				points.add(p);
				index.put(p, p.chunkX(), p.chunkZ());
			}
			double qx = random.nextDouble() * 600 - 300;
			double qz = random.nextDouble() * 600 - 300;
			double range = 16 + random.nextDouble() * 200;

			Point expected = null;
			double best = range * range;
			for (Point p : points) {
				double d = (p.x - qx) * (p.x - qx) + (p.z - qz) * (p.z - qz);
				if (p.alive && d <= best && (expected == null || d < best)) {
					best = d;
					expected = p;
				}
			}
			Point found = index.nearest(qx, qz, range, p -> p.x, p -> p.z, p -> p.alive);
			if (expected == null) {
				assertNull(found, "round " + round + ": found something beyond range");
			} else {
				double foundDistance = (found.x - qx) * (found.x - qx) + (found.z - qz) * (found.z - qz);
				assertEquals(best, foundDistance, 1e-9, "round " + round + ": not the nearest");
			}
		}
	}

	@Test
	void outOfRangeAndFilteredValuesAreNeverReturned() {
		ChunkSpatialIndex<Point> index = new ChunkSpatialIndex<>();
		Point far = new Point(500, 0);
		Point dead = new Point(3, 3);
		dead.alive = false;
		index.put(far, far.chunkX(), far.chunkZ());
		index.put(dead, dead.chunkX(), dead.chunkZ());
		assertNull(index.nearest(0, 0, 128, p -> p.x, p -> p.z, p -> p.alive));
		assertSame(far, index.nearest(0, 0, 600, p -> p.x, p -> p.z, p -> p.alive));
		assertNull(new ChunkSpatialIndex<Point>().nearest(0, 0, 128, p -> p.x, p -> p.z, p -> true), "empty index");
	}
}
