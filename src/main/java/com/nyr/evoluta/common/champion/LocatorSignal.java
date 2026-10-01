package com.nyr.evoluta.common.champion;

/**
 * How the Blight Locator clicks: a slow, steady tick while the target is far, speeding up and rising in pitch
 * inside {@link #NEAR} blocks. Pure numbers, shared by the client and the tests.
 */
public final class LocatorSignal {
	/** Inside this many blocks the clicking speeds up. */
	public static final double NEAR = 20.0;
	/** Ticks between clicks beyond {@link #NEAR}: 1.5 seconds. */
	public static final int FAR_INTERVAL = 30;
	/** Ticks between clicks when standing on the target. */
	public static final int CLOSEST_INTERVAL = 3;

	private LocatorSignal() {
	}

	/** Ticks until the next click for a target {@code distance} blocks away. */
	public static int clickInterval(double distance) {
		if (!(distance < NEAR)) {
			return FAR_INTERVAL;
		}
		double closeness = 1.0 - Math.max(0.0, distance) / NEAR;
		return (int) Math.round(FAR_INTERVAL - (FAR_INTERVAL - CLOSEST_INTERVAL) * closeness);
	}

	/** Pitch of a click for a target {@code distance} blocks away: 0.8 far out, rising to 1.6 on top of it. */
	public static float clickPitch(double distance) {
		if (!(distance < NEAR)) {
			return 0.8F;
		}
		return (float) (0.8 + 0.8 * (1.0 - Math.max(0.0, distance) / NEAR));
	}

	/**
	 * The needle angle in turns, in the vanilla compass "angle" predicate's convention (0 points straight ahead,
	 * 0.5 straight back), for a holder at (x, z) facing {@code yawDegrees} and a target at (targetX, targetZ).
	 */
	public static double needleAngle(double x, double z, double yawDegrees, double targetX, double targetZ) {
		double toTarget = Math.atan2(targetZ - z, targetX - x) / (Math.PI * 2);
		double facing = yawDegrees / 360.0;
		double angle = 0.5 - (facing - 0.25 - toTarget);
		return angle - Math.floor(angle);
	}
}
