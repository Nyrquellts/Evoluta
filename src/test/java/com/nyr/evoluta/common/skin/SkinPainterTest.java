package com.nyr.evoluta.common.skin;

import static org.junit.jupiter.api.Assertions.assertArrayEquals;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

import com.nyr.evoluta.common.mutation.Element;
import com.nyr.evoluta.common.mutation.Tier;
import java.util.Arrays;
import java.util.List;
import org.junit.jupiter.api.Test;

class SkinPainterTest {
	private static final int SIZE = 64;
	private static final List<Element> PAINTED = List.of(Element.IGNITED, Element.PERMAFROST, Element.TOXIC);
	private static final long SEED = 20260923L;

	/** A 64x64 texture laid out like a mob's: opaque face blocks with clear gaps, like a biped's UV map. */
	private static int[] texture(int size) {
		int[] pixels = new int[size * size];
		int scale = size / SIZE;
		int[][] faces = {{0, 0, 32, 16}, {16, 16, 56, 32}, {0, 16, 16, 32}, {40, 16, 56, 32}, {0, 48, 48, 64}};
		for (int[] face : faces) {
			for (int y = face[1] * scale; y < face[3] * scale; y++) {
				for (int x = face[0] * scale; x < face[2] * scale; x++) {
					pixels[y * size + x] = 0xFF000000 | (x * 3 & 0xFF) << 16 | (y * 5 & 0xFF) << 8 | 0x40;
				}
			}
		}
		return pixels;
	}

	private static boolean[] painted(int[] layer) {
		boolean[] painted = new boolean[layer.length];
		for (int i = 0; i < layer.length; i++) {
			painted[i] = layer[i] != 0;
		}
		return painted;
	}

	private static int count(boolean[] set) {
		int count = 0;
		for (boolean in : set) {
			count += in ? 1 : 0;
		}
		return count;
	}

	@Test
	void onlyTheThreeElementsWithBehaviourPaintTheSkin() {
		for (Element element : Element.values()) {
			assertEquals(PAINTED.contains(element), SkinPainter.paints(element), element.key());
		}
	}

	@Test
	void clearPixelsStayClear() {
		int[] base = texture(SIZE);
		for (Element element : PAINTED) {
			for (Tier tier : Tier.values()) {
				SkinPainter.Layers layers = SkinPainter.paint(base, SIZE, SIZE, element, tier, SEED);
				for (int i = 0; i < base.length; i++) {
					if (base[i] >>> 24 < 128) {
						assertEquals(0, layers.crust()[i], element + " " + tier + " painted crust on a clear pixel");
						assertEquals(0, layers.glow()[i], element + " " + tier + " painted glow on a clear pixel");
					}
				}
			}
		}
	}

	@Test
	void eachTierCoversMoreAndKeepsThePatternBelowIt() {
		int[] base = texture(SIZE);
		for (Element element : PAINTED) {
			boolean[] lowerCrust = null;
			boolean[] lowerGlow = null;
			for (Tier tier : Tier.values()) {
				SkinPainter.Layers layers = SkinPainter.paint(base, SIZE, SIZE, element, tier, SEED);
				boolean[] crust = painted(layers.crust());
				boolean[] glow = painted(layers.glow());
				if (lowerCrust != null) {
					assertTrue(count(crust) > count(lowerCrust), element + ": " + tier + " covers no more than the tier below");
					for (int i = 0; i < crust.length; i++) {
						assertTrue(!lowerCrust[i] || crust[i], element + ": " + tier + " lost crust pixel " + i + " of the tier below");
						assertTrue(!lowerGlow[i] || glow[i], element + ": " + tier + " lost glow pixel " + i + " of the tier below");
					}
				}
				lowerCrust = crust;
				lowerGlow = glow;
			}
		}
	}

	@Test
	void magmaCoversTheTiersShareOfTheSkin() {
		for (int size : new int[]{SIZE, SIZE * 2}) {
			int[] base = texture(size);
			int opaque = (int) Arrays.stream(base).filter(pixel -> pixel >>> 24 >= 128).count();
			for (Tier tier : Tier.values()) {
				SkinPainter.Layers layers = SkinPainter.paint(base, size, size, Element.IGNITED, tier, SEED);
				float share = count(painted(layers.crust())) / (float) opaque;
				assertEquals(SkinPainter.COVERAGE[tier.id() - 1], share, 0.02F, size + "px " + tier + " charred " + share + " of the skin");
			}
		}
	}

	@Test
	void everyElementGlowsAndGlowIsFullyOpaqueWhereItShows() {
		int[] base = texture(SIZE);
		for (Element element : PAINTED) {
			SkinPainter.Layers layers = SkinPainter.paint(base, SIZE, SIZE, element, Tier.APEX, SEED);
			assertTrue(count(painted(layers.glow())) > 20, element + " barely glows");
			for (int pixel : layers.glow()) {
				assertTrue(pixel == 0 || pixel >>> 24 == 0xFF, element + " glow pixel with partial alpha: " + Integer.toHexString(pixel));
			}
		}
	}

	@Test
	void theSeedPicksThePattern() {
		int[] base = texture(SIZE);
		for (Element element : PAINTED) {
			SkinPainter.Layers first = SkinPainter.paint(base, SIZE, SIZE, element, Tier.ELITE, SEED);
			SkinPainter.Layers again = SkinPainter.paint(base, SIZE, SIZE, element, Tier.ELITE, SEED);
			SkinPainter.Layers other = SkinPainter.paint(base, SIZE, SIZE, element, Tier.ELITE, SEED + 1);
			assertArrayEquals(first.crust(), again.crust(), element + " painted twice differently");
			assertArrayEquals(first.glow(), again.glow(), element + " glowed twice differently");
			assertFalse(Arrays.equals(first.crust(), other.crust()), element + " ignored its seed");
		}
	}

	/** The chest face of a biped's body box, as SoulCores places it; the test texture is opaque there. */
	private static final SkinPainter.Core CHEST = new SkinPainter.Core(20, 20, 8, 12);

	@Test
	void theSoulGlowsInTheChestAtEveryTier() {
		int[] base = texture(SIZE);
		int heart = 24 * SIZE + 24;
		for (Element element : PAINTED) {
			for (Tier tier : Tier.values()) {
				SkinPainter.Layers layers = SkinPainter.paint(base, SIZE, SIZE, element, tier, SEED, CHEST);
				assertTrue(layers.glow()[heart] >>> 24 == 0xFF && (layers.glow()[heart] & 0xFFFFFF) != 0, element + " " + tier + " has no glowing core");
			}
		}
	}

	@Test
	void theMutationSpreadsFromTheSoul() {
		int[] base = texture(SIZE);
		SkinPainter.Layers layers = SkinPainter.paint(base, SIZE, SIZE, Element.IGNITED, Tier.EVOLVED, SEED, CHEST);
		int chest = 0;
		int chestPainted = 0;
		int opaque = 0;
		int painted = 0;
		for (int y = 0; y < SIZE; y++) {
			for (int x = 0; x < SIZE; x++) {
				int i = y * SIZE + x;
				if (base[i] >>> 24 < 128) {
					continue;
				}
				opaque++;
				painted += layers.crust()[i] != 0 ? 1 : 0;
				if (CHEST.x() <= x && x < CHEST.x() + CHEST.width() && CHEST.y() <= y && y < CHEST.y() + CHEST.height()) {
					chest++;
					chestPainted += layers.crust()[i] != 0 ? 1 : 0;
				}
			}
		}
		float chestShare = chestPainted / (float) chest;
		float overall = painted / (float) opaque;
		assertTrue(chestShare > 0.8F && chestShare > 2 * overall, "an Evolved mutation covers " + chestShare + " of the chest and " + overall + " overall");
	}

	@Test
	void withASoulEachTierStillKeepsThePatternBelowIt() {
		int[] base = texture(SIZE);
		for (Element element : PAINTED) {
			boolean[] lower = null;
			for (Tier tier : Tier.values()) {
				boolean[] now = painted(SkinPainter.paint(base, SIZE, SIZE, element, tier, SEED, CHEST).glow());
				if (lower != null) {
					for (int i = 0; i < now.length; i++) {
						assertTrue(!lower[i] || now[i], element + ": " + tier + " lost glow pixel " + i + " of the tier below");
					}
				}
				lower = now;
			}
		}
	}

	/** A see-through ribcage: the core may fill the chest's clear pixels, and no clear pixel outside it. */
	@Test
	void onlyTheSoulMayFillClearPixels() {
		int[] base = texture(SIZE);
		for (int y = 22; y < 27; y++) {
			for (int x = 22; x < 27; x++) {
				base[y * SIZE + x] = 0;
			}
		}
		SkinPainter.Layers layers = SkinPainter.paint(base, SIZE, SIZE, Element.PERMAFROST, Tier.APEX, SEED, CHEST);
		assertTrue(layers.glow()[24 * SIZE + 24] != 0, "the core did not fill the see-through chest");
		for (int y = 0; y < SIZE; y++) {
			for (int x = 0; x < SIZE; x++) {
				int i = y * SIZE + x;
				boolean inChest = CHEST.x() <= x && x < CHEST.x() + CHEST.width() && CHEST.y() <= y && y < CHEST.y() + CHEST.height();
				if (base[i] >>> 24 < 128 && !inChest) {
					assertEquals(0, layers.crust()[i], "crust on a clear pixel outside the chest at " + x + "," + y);
				}
			}
		}
	}

	@Test
	void aClearTextureGetsNothing() {
		SkinPainter.Layers layers = SkinPainter.paint(new int[16 * 16], 16, 16, Element.TOXIC, Tier.APEX, SEED);
		assertEquals(0, count(painted(layers.crust())));
		assertEquals(0, count(painted(layers.glow())));
	}
}
