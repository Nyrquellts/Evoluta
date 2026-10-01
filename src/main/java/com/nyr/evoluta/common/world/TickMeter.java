package com.nyr.evoluta.common.world;

import java.util.Arrays;

/**
 * What Evoluta's own server logic costs one world per tick, over the last {@link #WINDOW} ticks: the tactical
 * tick plus the combat events that fired during the tick. Server thread only.
 */
public final class TickMeter {
	public static final int WINDOW = 200;

	private final long[] samples = new long[WINDOW];
	private int next;
	private int count;
	private long pending;

	/** Adds work done during the current tick. */
	public void add(long nanos) {
		this.pending += nanos;
	}

	/** Closes the current tick. */
	public void commit() {
		this.samples[this.next] = this.pending;
		this.next = (this.next + 1) % WINDOW;
		this.count = Math.min(this.count + 1, WINDOW);
		this.pending = 0;
	}

	public int ticks() {
		return this.count;
	}

	public double averageMicros() {
		if (this.count == 0) {
			return 0;
		}
		long total = 0;
		for (int i = 0; i < this.count; i++) {
			total += this.samples[i];
		}
		return total / 1000.0 / this.count;
	}

	public double maxMicros() {
		long max = 0;
		for (int i = 0; i < this.count; i++) {
			max = Math.max(max, this.samples[i]);
		}
		return max / 1000.0;
	}

	/** The tick cost that {@code percent}% of the recorded ticks stay at or under (nearest rank). */
	public double percentileMicros(double percent) {
		if (this.count == 0) {
			return 0;
		}
		long[] sorted = Arrays.copyOf(this.samples, this.count);
		Arrays.sort(sorted);
		int rank = (int) Math.ceil(percent / 100.0 * this.count);
		return sorted[Math.max(0, Math.min(this.count - 1, rank - 1))] / 1000.0;
	}
}
