package com.nyr.evoluta.client.render;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

import com.nyr.evoluta.client.render.GlintArt.Layer;
import com.nyr.evoluta.common.mutation.Element;
import org.joml.Vector4f;
import org.junit.jupiter.api.Test;

class GlintMotionTest {
	private static float rate(Element element, Layer layer, int axis) {
		double t = 1.0;
		double dt = 0.1;
		return (GlintMotion.offset(element, layer, t + dt)[axis] - GlintMotion.offset(element, layer, t)[axis]) / (float) dt;
	}

	/** A growing v offset moves what is drawn up the gear; a shrinking one moves it down. */
	@Test
	void fireClimbsOozeFallsFrostHangs() {
		for (Layer layer : Layer.values()) {
			assertTrue(rate(Element.IGNITED, layer, 1) > 0, "fire's " + layer + " does not climb");
			assertTrue(rate(Element.TOXIC, layer, 1) < 0, "ooze's " + layer + " does not fall");
			float frost = Math.abs(rate(Element.PERMAFROST, layer, 0)) + Math.abs(rate(Element.PERMAFROST, layer, 1));
			assertTrue(frost < Math.abs(rate(Element.IGNITED, Layer.DETAIL, 1)), "frost's " + layer + " moves as fast as fire");
		}
	}

	/** The two layers slide past each other, which is what gives the enchantment depth. */
	@Test
	void theLayersMoveDifferently() {
		for (Element element : new Element[]{Element.IGNITED, Element.PERMAFROST, Element.TOXIC}) {
			boolean differs = false;
			for (int axis = 0; axis < 2; axis++) {
				differs |= Math.abs(rate(element, Layer.SHEEN, axis) - rate(element, Layer.DETAIL, axis)) > 1.0E-3;
			}
			assertTrue(differs, element + ": sheen and detail move together");
		}
	}

	/** Armour shows the same share of the pattern on a chestplate as a sword does: an eighth of the item scale. */
	@Test
	void armourAndItemsShowTheSameShare() {
		assertEquals(GlintMotion.SHEEN_ITEM_SCALE / 8, GlintMotion.SHEEN_ARMOR_SCALE, 1.0E-6);
		assertEquals(GlintMotion.DETAIL_ITEM_SCALE / 8, GlintMotion.DETAIL_ARMOR_SCALE, 1.0E-6);
		Vector4f corner = GlintMotion.matrix(Element.TOXIC, Layer.SHEEN, false, 0.0).transform(new Vector4f(1, 1, 0, 1));
		assertEquals(GlintMotion.SHEEN_ITEM_SCALE, corner.x, 1.0E-4);
	}

	/** The offset wraps with the texture, so a server up for weeks never loses precision in it. */
	@Test
	void theOffsetStaysSmallHoweverLongTheGameRuns() {
		for (Element element : new Element[]{Element.IGNITED, Element.TOXIC, Element.PERMAFROST}) {
			for (Layer layer : Layer.values()) {
				float[] offset = GlintMotion.offset(element, layer, 3.0E6);
				assertTrue(Math.abs(offset[0]) <= 1.0F && Math.abs(offset[1]) <= 1.0F, element + " " + layer);
			}
		}
	}
}
