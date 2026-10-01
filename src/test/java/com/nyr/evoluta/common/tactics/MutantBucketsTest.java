package com.nyr.evoluta.common.tactics;

import static org.junit.jupiter.api.Assertions.assertEquals;

import org.junit.jupiter.api.Test;

class MutantBucketsTest {
	/** The stagger rule: a mutant with tickOffset = id % 10 runs when (worldTime + tickOffset) % 10 == 0. */
	@Test
	void theDueBucketIsExactlyTheStaggerRule() {
		for (long time = 0; time < 1_000; time++) {
			for (int offset = 0; offset < Tactics.INTERVAL; offset++) {
				boolean expected = (time + offset) % Tactics.INTERVAL == 0;
				assertEquals(expected, MutantBuckets.dueOffset(time) == offset, "time " + time + ", offset " + offset);
			}
		}
	}

	@Test
	void everyOffsetRunsOncePerIntervalWhateverTheStartTime() {
		for (long start : new long[]{0, 7, 123_456_789_011L, Long.MAX_VALUE - 20}) {
			int[] runs = new int[Tactics.INTERVAL];
			for (long time = start; time < start + Tactics.INTERVAL; time++) {
				runs[MutantBuckets.dueOffset(time)]++;
			}
			for (int offset = 0; offset < Tactics.INTERVAL; offset++) {
				assertEquals(1, runs[offset], "offset " + offset + " from " + start);
			}
		}
	}
}
