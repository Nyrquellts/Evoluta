package com.nyr.evoluta.client.render;

import com.nyr.evoluta.common.mutation.Element;
import org.joml.Matrix4f;

/**
 * How each element's two glint layers move over gear, in place of vanilla's single diagonal slide. The sheen's bands
 * sweep across the item (up for fire, down for ooze, slowly for frost); the detail over it drifts its own way and at
 * its own scale (wisps climb, drips fall, glints hang and creep), so the two layers slide past each other and the
 * enchantment has depth. Time is seconds scaled by the player's Glint Speed setting (1 at the default), so a player
 * who stopped the glint stops these too.
 */
public final class GlintMotion {
	/**
	 * Pattern repeats across UV space. Items' UVs are atlas UVs (a sprite is a sliver of them), armour and entity
	 * models' span their own texture, so armour takes an eighth of the item scale to show the same share of the
	 * pattern on a chestplate as on a sword. Vanilla's glint uses 8 and 0.16.
	 */
	static final float SHEEN_ITEM_SCALE = 6.0F;
	static final float SHEEN_ARMOR_SCALE = 0.75F;
	static final float DETAIL_ITEM_SCALE = 12.0F;
	static final float DETAIL_ARMOR_SCALE = 1.5F;

	private GlintMotion() {
	}

	/**
	 * The glint texture matrix at {@code seconds}: UV times the scale, plus an offset that moves the pattern. The offset
	 * wraps with the texture, so it never grows large enough to lose precision.
	 */
	public static Matrix4f matrix(Element element, GlintArt.Layer layer, boolean armor, double seconds) {
		float scale = layer == GlintArt.Layer.SHEEN ? armor ? SHEEN_ARMOR_SCALE : SHEEN_ITEM_SCALE : armor ? DETAIL_ARMOR_SCALE : DETAIL_ITEM_SCALE;
		float[] offset = offset(element, layer, seconds);
		return new Matrix4f().translation(offset[0], offset[1], 0.0F).scale(scale);
	}

	/**
	 * The (u, v) offset: a growing v samples farther down the texture, which moves what is drawn up the gear; a
	 * shrinking v moves it down.
	 */
	static float[] offset(Element element, GlintArt.Layer layer, double seconds) {
		if (layer == GlintArt.Layer.SHEEN) {
			return switch (element) {
				// the bands sweep up the item, heat rising
				case IGNITED -> new float[]{frac(seconds * -0.012), frac(seconds * 0.05)};
				// a slow, even sweep across, as light moves over ice
				case PERMAFROST -> new float[]{frac(seconds * 0.02), frac(seconds * 0.015)};
				// the bands slide down, as ooze does
				case TOXIC -> new float[]{frac(seconds * 0.01), -frac(seconds * 0.035)};
				default -> new float[]{0.0F, 0.0F};
			};
		}
		return switch (element) {
			// wisps and embers climb faster than the sheen, swaying
			case IGNITED -> new float[]{0.012F * (float) Math.sin(seconds * 1.7), frac(seconds * 0.16)};
			// glints hang, creeping the other way to the sheen
			case PERMAFROST -> new float[]{-frac(seconds * 0.012), frac(seconds * 0.006)};
			// bubbles and drips fall
			case TOXIC -> new float[]{0.0F, -frac(seconds * 0.07)};
			default -> new float[]{0.0F, 0.0F};
		};
	}

	private static float frac(double value) {
		return (float) (value - Math.floor(value));
	}
}
