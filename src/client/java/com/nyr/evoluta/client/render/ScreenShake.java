package com.nyr.evoluta.client.render;

import net.minecraft.client.MinecraftClient;

/**
 * The camera shake the server asks for on heavy moments ({@code ShakePayload}): "trauma" from 0 to 1 that decays over
 * about half a second to a second, turned into a small shake of the view (yaw and pitch, not where the player aims),
 * growing with the square of the trauma so light blows barely register and a slam rattles. Scaled by the Distortion
 * Effects setting: at 0, nothing shakes. Render thread only.
 */
public final class ScreenShake {
	/** Degrees at full trauma. */
	static final float MAX_YAW = 3.5F;
	static final float MAX_PITCH = 2.5F;
	/** Trauma lost per second. */
	static final float DECAY = 1.6F;

	private static float trauma;
	private static long lastNanos;

	private ScreenShake() {
	}

	public static void add(float amount) {
		trauma = Math.min(1.0F, trauma + Math.max(0.0F, amount));
	}

	/** Called once per frame from the camera: the (yaw, pitch) to add to the view, or null when nothing shakes. */
	public static float[] frame() {
		long now = System.nanoTime();
		float seconds = lastNanos == 0 ? 0.0F : Math.min(0.25F, (now - lastNanos) / 1.0E9F);
		lastNanos = now;
		MinecraftClient client = MinecraftClient.getInstance();
		if (client.isPaused()) {
			return null;
		}
		trauma = decay(trauma, seconds);
		if (trauma <= 0.0F) {
			return null;
		}
		double scale = client.options.getDistortionEffectScale().getValue();
		return offset(trauma, (float) scale, now / 1.0E9);
	}

	static float decay(float trauma, float seconds) {
		return Math.max(0.0F, trauma - DECAY * seconds);
	}

	/**
	 * The view offset for {@code trauma} at {@code timeSeconds}, scaled by {@code scale} (the Distortion Effects
	 * setting): two sines at unrelated rates per axis, so the shake never settles into a rhythm.
	 */
	static float[] offset(float trauma, float scale, double timeSeconds) {
		if (scale <= 0.0F) {
			return null;
		}
		float shake = trauma * trauma * scale;
		float yaw = (float) (Math.sin(timeSeconds * 47.0) * 0.6 + Math.sin(timeSeconds * 73.0 + 1.3) * 0.4) * MAX_YAW * shake;
		float pitch = (float) (Math.sin(timeSeconds * 59.0 + 0.7) * 0.6 + Math.sin(timeSeconds * 89.0 + 2.1) * 0.4) * MAX_PITCH * shake;
		return new float[]{yaw, pitch};
	}

	static void reset() {
		trauma = 0.0F;
		lastNanos = 0;
	}
}
