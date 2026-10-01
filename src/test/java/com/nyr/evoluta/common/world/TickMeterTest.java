package com.nyr.evoluta.common.world;

import static org.junit.jupiter.api.Assertions.assertEquals;

import org.junit.jupiter.api.Test;

class TickMeterTest {
	@Test
	void averagesAndMaximaCoverTheCommittedTicks() {
		TickMeter meter = new TickMeter();
		meter.add(1_000);
		meter.add(2_000);
		meter.commit();
		meter.commit();
		meter.add(9_000);
		meter.commit();
		assertEquals(3, meter.ticks());
		assertEquals(4.0, meter.averageMicros(), 1e-9, "(3 + 0 + 9) us over 3 ticks");
		assertEquals(9.0, meter.maxMicros(), 1e-9);
	}

	@Test
	void onlyTheLastWindowCounts() {
		TickMeter meter = new TickMeter();
		meter.add(1_000_000);
		meter.commit();
		for (int i = 0; i < TickMeter.WINDOW; i++) {
			meter.add(500);
			meter.commit();
		}
		assertEquals(TickMeter.WINDOW, meter.ticks());
		assertEquals(0.5, meter.averageMicros(), 1e-9, "the 1 ms tick has left the window");
		assertEquals(0.5, meter.maxMicros(), 1e-9);
	}

	@Test
	void percentilesUseTheNearestRank() {
		TickMeter meter = new TickMeter();
		for (int i = 1; i <= 100; i++) {
			meter.add(i * 1_000L);
			meter.commit();
		}
		assertEquals(50.0, meter.percentileMicros(50), 1e-9);
		assertEquals(99.0, meter.percentileMicros(99), 1e-9);
		assertEquals(100.0, meter.percentileMicros(100), 1e-9);
		assertEquals(1.0, meter.percentileMicros(0), 1e-9);
	}

	@Test
	void anUnusedMeterReadsZero() {
		TickMeter meter = new TickMeter();
		assertEquals(0, meter.ticks());
		assertEquals(0.0, meter.averageMicros());
		assertEquals(0.0, meter.maxMicros());
	}
}
