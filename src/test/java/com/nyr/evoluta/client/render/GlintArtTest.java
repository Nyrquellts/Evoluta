package com.nyr.evoluta.client.render;

import static org.junit.jupiter.api.Assertions.assertArrayEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertTrue;

import com.nyr.evoluta.client.render.GlintArt.Layer;
import com.nyr.evoluta.common.mutation.Element;
import java.util.Arrays;
import java.util.List;
import org.junit.jupiter.api.Test;

class GlintArtTest {
	private static final int N = GlintArt.SIZE;
	private static final long SEED = 20260924L;
	private static final List<Element> ELEMENTS = List.of(Element.IGNITED, Element.PERMAFROST, Element.TOXIC);

	private static float light(int argb) {
		return Math.max(argb >> 16 & 0xFF, Math.max(argb >> 8 & 0xFF, argb & 0xFF)) / 255.0F;
	}

	private static float at(int[] pixels, int x, int y) {
		return light(pixels[Math.floorMod(y, N) * N + Math.floorMod(x, N)]);
	}

	/** The mean jump in light from column {@code line} to the next (or row to the next). */
	private static double step(int[] pixels, int line, boolean across) {
		double sum = 0;
		for (int i = 0; i < N; i++) {
			sum += across ? Math.abs(at(pixels, line, i) - at(pixels, line + 1, i)) : Math.abs(at(pixels, i, line) - at(pixels, i, line + 1));
		}
		return sum / N;
	}

	private static double share(int[] pixels, float min, float max) {
		double count = 0;
		for (int argb : pixels) {
			float light = light(argb);
			count += light >= min && light < max ? 1 : 0;
		}
		return count / pixels.length;
	}

	@Test
	void onlyTheElementsWithBehaviourHaveArt() {
		for (Layer layer : Layer.values()) {
			assertNull(GlintArt.paint(Element.NONE, layer, false, N, SEED));
			assertNull(GlintArt.paint(Element.ABYSSAL, layer, true, N, SEED));
		}
	}

	/**
	 * The shader wraps the texture: the step across the edge must look like a step inside it. One edge is one sample,
	 * so it is weighed against the typical step over several seeds: a real seam jumps every time.
	 */
	@Test
	void everyLayerTilesWithoutASeam() {
		int seeds = 4;
		for (Element element : ELEMENTS) {
			for (Layer layer : Layer.values()) {
				for (boolean across : new boolean[]{true, false}) {
					double seam = 0;
					double inside = 0;
					for (int s = 0; s < seeds; s++) {
						int[] pixels = GlintArt.paint(element, layer, false, N, SEED + s);
						seam += step(pixels, N - 1, across) / seeds;
						for (int line = 0; line < N - 1; line++) {
							inside += step(pixels, line, across) / ((N - 1) * seeds);
						}
					}
					assertTrue(seam <= inside * 1.5 + 0.005, element + " " + layer + (across ? " columns" : " rows")
							+ ": the edge jumps " + seam + " on average, a step inside " + inside);
				}
			}
		}
	}

	@Test
	void eachElementGlowsInItsOwnColours() {
		for (Element element : ELEMENTS) {
			long red = 0;
			long green = 0;
			long blue = 0;
			for (int argb : GlintArt.paint(element, Layer.SHEEN, false, N, SEED)) {
				red += argb >> 16 & 0xFF;
				green += argb >> 8 & 0xFF;
				blue += argb & 0xFF;
			}
			String sums = element + ": red " + red + ", green " + green + ", blue " + blue;
			switch (element) {
				case IGNITED -> assertTrue(red > green && green > blue, sums);
				case PERMAFROST -> assertTrue(blue > green && green > red, sums);
				case TOXIC -> assertTrue(green > red && red > blue, sums);
				default -> {
				}
			}
		}
	}

	/**
	 * The sheen is smooth light, not pixel art: neighbouring pixels differ little on average (vanilla's glint scores
	 * 0.02 at this resolution; a crisp highlight band takes a little more, pasted-on
	 * shapes far more). It leaves gaps where the item shows as itself, and has bright highlights.
	 */
	@Test
	void theSheenIsSmoothLightWithGapsAndHighlights() {
		for (Element element : ELEMENTS) {
			int[] pixels = GlintArt.paint(element, Layer.SHEEN, false, N, SEED);
			double roughness = 0;
			for (int line = 0; line < N - 1; line++) {
				roughness += (step(pixels, line, true) + step(pixels, line, false)) / (2 * (N - 1));
			}
			double clear = share(pixels, 0.0F, 0.06F);
			double bright = share(pixels, 0.6F, 2.0F);
			String what = element + ": roughness " + roughness + ", clear " + clear + ", bright " + bright;
			assertTrue(roughness < 0.05, what);
			assertTrue(clear > 0.15 && clear < 0.85, what);
			assertTrue(bright > 0.01, what);
		}
	}

	/** Wherever a sword sits on the sheen (a 24-pixel window at its scale), light crosses it. */
	@Test
	void everyWindowOfGearCatchesTheSheen() {
		for (Element element : ELEMENTS) {
			int[] pixels = GlintArt.paint(element, Layer.SHEEN, false, N, SEED);
			for (int wy = 0; wy < N; wy += 12) {
				for (int wx = 0; wx < N; wx += 12) {
					int lit = 0;
					for (int y = wy; y < wy + 24; y++) {
						for (int x = wx; x < wx + 24; x++) {
							lit += at(pixels, x, y) > 0.3F ? 1 : 0;
						}
					}
					assertTrue(lit >= 16, element + ": the window at " + wx + "," + wy + " catches " + lit + " lit pixels");
				}
			}
		}
	}

	/** The detail is sparse, drifting over the sheen, and busier and brighter at Apex. */
	@Test
	void theDetailIsSparseAndApexIsBusier() {
		for (Element element : ELEMENTS) {
			int[] plain = GlintArt.paint(element, Layer.DETAIL, false, N, SEED);
			int[] apex = GlintArt.paint(element, Layer.DETAIL, true, N, SEED);
			double clear = share(plain, 0.0F, 0.06F);
			double lit = share(plain, 0.3F, 2.0F);
			double apexLit = share(apex, 0.3F, 2.0F);
			String what = element + ": clear " + clear + ", lit " + lit + ", Apex lit " + apexLit;
			assertTrue(clear > 0.5, what);
			assertTrue(lit > 0.01, what);
			assertTrue(apexLit > lit, what);
		}
	}

	@Test
	void theSeedPicksThePattern() {
		for (Element element : ELEMENTS) {
			for (Layer layer : Layer.values()) {
				assertArrayEquals(GlintArt.paint(element, layer, false, N, SEED), GlintArt.paint(element, layer, false, N, SEED), element + " " + layer + " painted twice differently");
				assertFalse(Arrays.equals(GlintArt.paint(element, layer, false, N, SEED), GlintArt.paint(element, layer, false, N, SEED + 1)), element + " " + layer + " ignored its seed");
			}
		}
	}
}
