package com.nyr.evoluta.client;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

import com.nyr.evoluta.common.mutation.Element;
import org.junit.jupiter.api.Test;

class LocatorGemTest {
	private static int red(int color) {
		return color >> 16 & 0xFF;
	}

	private static int green(int color) {
		return color >> 8 & 0xFF;
	}

	private static int blue(int color) {
		return color & 0xFF;
	}

	private static int brightness(int color) {
		return red(color) + green(color) + blue(color);
	}

	@Test
	void searchingIsADimVioletEmber() {
		for (long time = 0; time < 3000; time += 250) {
			int color = LocatorGem.tint(null, 0, time);
			assertEquals(0xFF, color >>> 24);
			assertTrue(blue(color) > red(color) && red(color) > green(color), "not violet: " + Integer.toHexString(color));
			assertTrue(blue(color) <= 0x80, "too bright to read as searching: " + Integer.toHexString(color));
		}
	}

	@Test
	void aFoundChampionShowsItsElement() {
		int ignited = LocatorGem.tint(Element.IGNITED, 10_000, 0);
		int permafrost = LocatorGem.tint(Element.PERMAFROST, 10_000, 0);
		int toxic = LocatorGem.tint(Element.TOXIC, 10_000, 0);
		int none = LocatorGem.tint(Element.NONE, 10_000, 0);
		assertTrue(red(ignited) > green(ignited) && green(ignited) > blue(ignited), "Ignited is not orange: " + Integer.toHexString(ignited));
		assertTrue(blue(permafrost) > red(permafrost) && green(permafrost) > red(permafrost), "Permafrost is not icy: " + Integer.toHexString(permafrost));
		assertTrue(green(toxic) > red(toxic) && green(toxic) > blue(toxic), "Toxic is not green: " + Integer.toHexString(toxic));
		assertTrue(red(none) > 2 * green(none) && red(none) > 2 * blue(none), "an element-less champion is not red: " + Integer.toHexString(none));
		assertTrue(brightness(LocatorGem.tint(null, 10_000, 0)) < brightness(toxic), "a found champion should outshine searching");
	}

	@Test
	void eachClickFlaresTheGemAndTheFlareFades() {
		for (Element element : Element.values()) {
			int flare = LocatorGem.tint(element, 0, 0);
			int fading = LocatorGem.tint(element, 150, 0);
			int rest = LocatorGem.tint(element, 5_000, 0);
			assertTrue(brightness(flare) > brightness(fading) && brightness(fading) > brightness(rest), element + " does not flare and fade");
			assertEquals(0xFF, flare >>> 24);
		}
	}
}
