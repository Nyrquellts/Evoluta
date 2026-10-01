package com.nyr.evoluta.client.render;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertTrue;

import org.junit.jupiter.api.Test;

class ScreenShakeTest {
	@Test
	void traumaFadesWithinASecondAndNeverGoesBelowZero() {
		assertEquals(0.8F - ScreenShake.DECAY * 0.25F, ScreenShake.decay(0.8F, 0.25F), 1.0E-6);
		assertEquals(0.0F, ScreenShake.decay(0.8F, 1.0F));
		assertEquals(0.0F, ScreenShake.decay(0.0F, 0.05F));
	}

	/** A player who turned Distortion Effects off feels nothing. */
	@Test
	void distortionEffectsOffMeansNoShake() {
		assertNull(ScreenShake.offset(1.0F, 0.0F, 12.3));
	}

	/** Light blows barely register and a slam rattles: the shake grows with the square of the trauma, within its limits. */
	@Test
	void heavierMomentsShakeHarderWithinTheLimits() {
		double light = 0;
		double heavy = 0;
		for (int i = 0; i < 400; i++) {
			double time = i * 0.013;
			float[] soft = ScreenShake.offset(0.2F, 1.0F, time);
			float[] hard = ScreenShake.offset(0.8F, 1.0F, time);
			light = Math.max(light, Math.abs(soft[0]));
			heavy = Math.max(heavy, Math.abs(hard[0]));
			assertTrue(Math.abs(hard[0]) <= ScreenShake.MAX_YAW * 0.64F + 1.0E-4, "yaw beyond its limit: " + hard[0]);
			assertTrue(Math.abs(hard[1]) <= ScreenShake.MAX_PITCH * 0.64F + 1.0E-4, "pitch beyond its limit: " + hard[1]);
		}
		assertTrue(heavy > light * 10, "a slam (0.8) should shake far harder than a light blow (0.2): " + heavy + " against " + light);
	}
}
