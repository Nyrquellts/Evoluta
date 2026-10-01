package com.nyr.evoluta.common.soul;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

import com.nyr.evoluta.common.mutation.Element;
import com.nyr.evoluta.common.mutation.Tier;
import java.util.List;
import org.junit.jupiter.api.Test;

class ToolGraftsTest {
	private static List<Grafting.Weighted> one(SoulKind kind, Element element, Tier grade, float power) {
		return Grafting.weighted(List.of(new Graft(kind, element, grade, power)));
	}

	@Test
	void numbersAtTheEndsOfTheRolls() {
		float best = Graft.maxPower(Tier.APEX);
		float worst = Graft.minPower(Tier.EVOLVED);
		assertEquals(0.5F, ToolGrafts.smeltChance(one(SoulKind.ZOMBIE, Element.IGNITED, Tier.APEX, best)), 1.0E-6F);
		assertEquals(0.22F, ToolGrafts.smeltChance(one(SoulKind.ZOMBIE, Element.IGNITED, Tier.EVOLVED, worst)), 1.0E-6F);
		assertEquals(0.3F, ToolGrafts.prospectChance(one(SoulKind.ZOMBIE_VILLAGER, Element.TOXIC, Tier.APEX, best)), 1.0E-6F);
		assertEquals(0.35F, ToolGrafts.bountyChance(one(SoulKind.BOGGED, Element.TOXIC, Tier.APEX, best)), 1.0E-6F);
		assertEquals(0.2F, ToolGrafts.blastChance(one(SoulKind.CREEPER, Element.TOXIC, Tier.APEX, best)), 1.0E-6F);
		assertEquals(0.25F, ToolGrafts.silkChance(one(SoulKind.SPIDER, Element.TOXIC, Tier.APEX, best)), 1.0E-6F);
		assertEquals(1.8F, ToolGrafts.sandsifter(one(SoulKind.HUSK, Element.TOXIC, Tier.APEX, best)), 1.0E-6F);
		assertEquals(1.5F, ToolGrafts.gnaw(one(SoulKind.ZOMBIE, Element.TOXIC, Tier.APEX, best)), 1.0E-6F);
		assertEquals(4, ToolGrafts.blastCount(one(SoulKind.CREEPER, Element.TOXIC, Tier.APEX, best)));
		assertEquals(2, ToolGrafts.blastCount(one(SoulKind.CREEPER, Element.TOXIC, Tier.EVOLVED, worst)));
		assertEquals(5, ToolGrafts.tunnelRange(one(SoulKind.CAVE_SPIDER, Element.TOXIC, Tier.APEX, best)));
		assertEquals(3, ToolGrafts.tunnelRange(one(SoulKind.CAVE_SPIDER, Element.TOXIC, Tier.EVOLVED, worst)));
	}

	@Test
	void aGraftDoesOnlyWhatItsKindAndElementDo() {
		List<Grafting.Weighted> zombie = one(SoulKind.ZOMBIE, Element.TOXIC, Tier.APEX, 1.0F);
		assertEquals(0.0F, ToolGrafts.smeltChance(zombie));
		assertEquals(0.0F, ToolGrafts.silkChance(zombie));
		assertEquals(0.0F, ToolGrafts.blastChance(zombie));
		assertEquals(1.0F, ToolGrafts.sandsifter(zombie));
		assertEquals(0, ToolGrafts.tunnelRange(zombie));
		assertEquals(0.0F, ToolGrafts.gnaw(one(SoulKind.HUSK, Element.IGNITED, Tier.APEX, 1.0F)));
	}

	@Test
	void stackedGraftsNeverMakeAChanceCertain() {
		List<Grafting.Weighted> ignited = Grafting.weighted(List.of(
				new Graft(SoulKind.ZOMBIE, Element.IGNITED, Tier.APEX, 1.0F),
				new Graft(SoulKind.HUSK, Element.IGNITED, Tier.APEX, 1.0F),
				new Graft(SoulKind.DROWNED, Element.IGNITED, Tier.APEX, 1.0F)));
		float uncapped = 0;
		for (Grafting.Weighted graft : ignited) {
			uncapped += 0.5F * graft.weight();
		}
		assertTrue(uncapped > ToolGrafts.MAX_CHANCE, "setup: three Apex Ignited grafts add up to " + uncapped);
		assertEquals(ToolGrafts.MAX_CHANCE, ToolGrafts.smeltChance(ignited));
	}
}
