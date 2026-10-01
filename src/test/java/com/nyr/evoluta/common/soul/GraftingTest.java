package com.nyr.evoluta.common.soul;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

import com.mojang.serialization.JsonOps;
import com.nyr.evoluta.common.mutation.Element;
import com.nyr.evoluta.common.mutation.Tier;
import java.util.List;
import net.minecraft.util.math.random.Random;
import org.junit.jupiter.api.Test;

class GraftingTest {
	private static Graft graft(SoulKind kind, Element element, float power) {
		return new Graft(kind, element, Tier.ELITE, power);
	}

	@Test
	void overlappingGraftsFallOffStrongestFirst() {
		List<Grafting.Weighted> weighted = Grafting.weighted(List.of(
				graft(SoulKind.SKELETON, Element.IGNITED, 0.5F),
				graft(SoulKind.SKELETON, Element.PERMAFROST, 0.9F),
				graft(SoulKind.ZOMBIE, Element.TOXIC, 0.7F)));
		assertEquals(0.9F, weighted.get(0).power());
		assertEquals(1.0F, weighted.get(0).weight(), "the strongest graft works in full");
		assertEquals(1.0F, weighted.get(1).weight(), "a graft sharing neither kind nor element works in full");
		assertEquals(0.6F, weighted.get(2).weight(), "a skeleton graft behind a stronger skeleton works at 60%");
	}

	@Test
	void threeOfAKindWorkAtFullSixtyAndThirtyFive() {
		List<Grafting.Weighted> weighted = Grafting.weighted(List.of(
				graft(SoulKind.SPIDER, Element.TOXIC, 0.6F),
				graft(SoulKind.SPIDER, Element.TOXIC, 0.8F),
				graft(SoulKind.SPIDER, Element.TOXIC, 0.7F)));
		assertEquals(1.0F, weighted.get(0).weight());
		assertEquals(0.6F, weighted.get(1).weight());
		assertEquals(0.35F, weighted.get(2).weight());
	}

	@Test
	void rollsStayInsideTheirGrade() {
		Random random = Random.create(20260923L);
		for (Tier grade : Tier.values()) {
			for (int i = 0; i < 500; i++) {
				float power = Graft.roll(grade, random);
				assertTrue(power >= Graft.minPower(grade) && power <= Graft.maxPower(grade), grade + " rolled " + power);
				float quality = new Graft(SoulKind.ZOMBIE, Element.IGNITED, grade, power).quality();
				assertTrue(quality >= 0.0F && quality <= 1.0F, grade + " quality " + quality);
			}
		}
		assertTrue(Graft.minPower(Tier.APEX) > Graft.minPower(Tier.ELITE) && Graft.minPower(Tier.ELITE) > Graft.minPower(Tier.EVOLVED),
				"each grade rolls higher than the one below");
	}

	@Test
	void graftsSaveAndLoadByKey() {
		Graft graft = new Graft(SoulKind.CAVE_SPIDER, Element.PERMAFROST, Tier.APEX, 0.93F);
		var json = Graft.CODEC.encodeStart(JsonOps.INSTANCE, graft).getOrThrow();
		assertEquals("cave_spider", json.getAsJsonObject().get("kind").getAsString());
		assertEquals("apex", json.getAsJsonObject().get("grade").getAsString());
		assertEquals(graft, Graft.CODEC.parse(JsonOps.INSTANCE, json).getOrThrow());
	}
}
