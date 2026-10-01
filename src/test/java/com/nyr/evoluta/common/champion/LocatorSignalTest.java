package com.nyr.evoluta.common.champion;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

import org.junit.jupiter.api.Test;

class LocatorSignalTest {
	@Test
	void clicksAreSteadyFarOutAndSpeedUpInside20Blocks() {
		assertEquals(LocatorSignal.FAR_INTERVAL, LocatorSignal.clickInterval(20));
		assertEquals(LocatorSignal.FAR_INTERVAL, LocatorSignal.clickInterval(127));
		assertEquals(LocatorSignal.FAR_INTERVAL, LocatorSignal.clickInterval(Double.NaN));
		assertEquals(LocatorSignal.CLOSEST_INTERVAL, LocatorSignal.clickInterval(0));
		int previous = LocatorSignal.clickInterval(20);
		for (double distance = 19.5; distance >= 0; distance -= 0.5) {
			int interval = LocatorSignal.clickInterval(distance);
			assertTrue(interval <= previous, "clicks slowed down moving closer at " + distance);
			assertTrue(interval >= LocatorSignal.CLOSEST_INTERVAL, "faster than the floor at " + distance);
			previous = interval;
		}
	}

	@Test
	void pitchRisesAsTheTargetNears() {
		assertEquals(0.8F, LocatorSignal.clickPitch(50));
		assertEquals(1.6F, LocatorSignal.clickPitch(0), 1e-6);
		assertTrue(LocatorSignal.clickPitch(5) > LocatorSignal.clickPitch(15));
	}

	/** Vanilla's compass convention: 0 is the needle straight up (ahead), 0.5 straight down (behind). */
	@Test
	void theNeedlePointsAheadAtATargetInFront() {
		// yaw 0 faces +z
		assertEquals(0.0, wrapped(LocatorSignal.needleAngle(0, 0, 0, 0, 10)), 1e-9);
		assertEquals(0.5, LocatorSignal.needleAngle(0, 0, 0, 0, -10), 1e-9);
		// yaw 90 faces -x
		assertEquals(0.0, wrapped(LocatorSignal.needleAngle(0, 0, 90, -10, 0)), 1e-9);
		assertEquals(0.5, LocatorSignal.needleAngle(0, 0, 90, 10, 0), 1e-9);
		// a quarter turn of the holder is a quarter turn of the needle, the other way
		double ahead = LocatorSignal.needleAngle(0, 0, 0, 3, 7);
		double turned = LocatorSignal.needleAngle(0, 0, 90, 3, 7);
		assertEquals(0.75, wrapped(turned - ahead), 1e-9);
		// yaw may run past 360 or below 0, as the player's does
		assertEquals(LocatorSignal.needleAngle(0, 0, 30, 3, 7), LocatorSignal.needleAngle(0, 0, 30 + 720, 3, 7), 1e-9);
		assertEquals(LocatorSignal.needleAngle(0, 0, 30, 3, 7), LocatorSignal.needleAngle(0, 0, 30 - 360, 3, 7), 1e-9);
	}

	private static double wrapped(double angle) {
		double value = angle - Math.floor(angle);
		return value > 1 - 1e-9 ? 0 : value;
	}
}
