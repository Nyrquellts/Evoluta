package com.nyr.evoluta.common.mutation;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertTrue;

import com.nyr.evoluta.common.config.EvolutaConfig;
import java.util.EnumMap;
import java.util.Map;
import net.minecraft.util.math.random.Random;
import org.junit.jupiter.api.Test;

class MutationRollerTest {
	private static final int ROLLS = 20_000;
	private static final MutationRoller.Conditions DAY = new MutationRoller.Conditions(false, 0, true, true, true);
	private static final MutationRoller.Conditions NIGHT = new MutationRoller.Conditions(true, 0, true, true, true);

	@Test
	void chanceGrowsWithDifficultyAndNightAndStaysAProbability() {
		EvolutaConfig config = EvolutaConfig.DEFAULTS;
		assertEquals(0.05, MutationRoller.chance(config, DAY), 1e-9);
		assertEquals(0.075, MutationRoller.chance(config, NIGHT), 1e-9);
		assertEquals(0.15, MutationRoller.chance(config, new MutationRoller.Conditions(false, 1, true, true, true)), 1e-9);
		assertEquals(0.225, MutationRoller.chance(config, new MutationRoller.Conditions(true, 1, true, true, true)), 1e-9);
		EvolutaConfig huge = with(config, 0.9, 0.5, 10);
		assertEquals(1.0, MutationRoller.chance(huge, NIGHT), 1e-9);
	}

	@Test
	void zeroChanceNeverMutatesAndCertainChanceAlwaysDoes() {
		Random random = Random.create(1);
		EvolutaConfig never = with(EvolutaConfig.DEFAULTS, 0, 0, 1);
		EvolutaConfig always = with(EvolutaConfig.DEFAULTS, 1, 0, 1);
		for (int i = 0; i < ROLLS; i++) {
			assertNull(MutationRoller.roll(random, never, NIGHT));
			assertNotNull(MutationRoller.roll(random, always, DAY));
		}
	}

	@Test
	void defaultChanceMatchesItsRateOverManyRolls() {
		Random random = Random.create(20260923);
		int mutated = 0;
		for (int i = 0; i < 100_000; i++) {
			if (MutationRoller.roll(random, EvolutaConfig.DEFAULTS, NIGHT) != null) {
				mutated++;
			}
		}
		// 7.5% at night with no local difficulty; 100k rolls put 4 standard deviations at about 0.33%
		assertEquals(0.075, mutated / 100_000.0, 0.004);
	}

	@Test
	void tiersFollowTheirWeightsAndApexWaitsForNight() {
		EvolutaConfig always = with(EvolutaConfig.DEFAULTS, 1, 0, 1);
		Map<Tier, Integer> night = count(always, NIGHT, Random.create(7));
		assertEquals(0.75, night.get(Tier.EVOLVED) / (double) ROLLS, 0.02);
		assertEquals(0.20, night.get(Tier.ELITE) / (double) ROLLS, 0.02);
		assertEquals(0.05, night.get(Tier.APEX) / (double) ROLLS, 0.01);

		Map<Tier, Integer> day = count(always, DAY, Random.create(8));
		assertEquals(0, day.get(Tier.APEX), "apexOnlyAtNight is on by default");
		assertEquals(20.0 / 95.0, day.get(Tier.ELITE) / (double) ROLLS, 0.02);
	}

	@Test
	void aChampionSlotThatIsTakenCapsTheRollAtEvolved() {
		EvolutaConfig eliteOnly = EvolutaConfig.DEFAULTS.toBuilder().mutationChance(1).localDifficultyBonus(0).nightMultiplier(1)
				.apexOnlyAtNight(false).tierWeights(0, 1, 1).build();
		MutationRoller.Conditions taken = new MutationRoller.Conditions(true, 1, true, true, false);
		Random random = Random.create(3);
		for (int i = 0; i < ROLLS; i++) {
			assertEquals(Tier.EVOLVED, MutationRoller.roll(random, eliteOnly, taken).getTier());
		}
	}

	@Test
	void archetypesOnlyComeFromWhatTheTypeAllows() {
		EvolutaConfig always = with(EvolutaConfig.DEFAULTS, 1, 0, 1);
		EvolutaConfig everyArchetype = always.toBuilder().archetypeChance(1, 1, 1).build();
		Random random = Random.create(11);
		MutationRoller.Conditions bruteOnly = new MutationRoller.Conditions(true, 0, true, false, true);
		MutationRoller.Conditions neither = new MutationRoller.Conditions(true, 0, false, false, true);
		int brutes = 0;
		int stalkers = 0;
		for (int i = 0; i < ROLLS; i++) {
			assertEquals(Archetype.BRUTE, MutationRoller.roll(random, everyArchetype, bruteOnly).getArchetype());
			assertEquals(Archetype.NONE, MutationRoller.roll(random, everyArchetype, neither).getArchetype());
			Archetype both = MutationRoller.roll(random, everyArchetype, NIGHT).getArchetype();
			if (both == Archetype.BRUTE) {
				brutes++;
			} else if (both == Archetype.STALKER) {
				stalkers++;
			}
		}
		assertEquals(ROLLS, brutes + stalkers, "archetypeChance 1 always picks one");
		assertEquals(0.5, brutes / (double) ROLLS, 0.02);
	}

	@Test
	void elementWeightsPickAndZeroTurnsAnElementOff() {
		EvolutaConfig base = with(EvolutaConfig.DEFAULTS, 1, 0, 1);
		EvolutaConfig toxicOnly = base.toBuilder().elementChance(1, 1, 1).elementWeights(0, 0, 5).build();
		EvolutaConfig noElements = base.toBuilder().elementChance(1, 1, 1).elementWeights(0, 0, 0).build();
		EvolutaConfig even = base.toBuilder().elementChance(1, 1, 1).elementWeights(1, 1, 1).build();
		Random random = Random.create(5);
		Map<Element, Integer> counts = new EnumMap<>(Element.class);
		for (int i = 0; i < ROLLS; i++) {
			assertEquals(Element.TOXIC, MutationRoller.roll(random, toxicOnly, NIGHT).getElement());
			assertEquals(Element.NONE, MutationRoller.roll(random, noElements, NIGHT).getElement());
			counts.merge(MutationRoller.roll(random, even, NIGHT).getElement(), 1, Integer::sum);
		}
		for (Element element : Element.IMPLEMENTED) {
			assertEquals(1.0 / 3.0, counts.getOrDefault(element, 0) / (double) ROLLS, 0.02, element.key());
		}
		assertTrue(!counts.containsKey(Element.ABYSSAL) && !counts.containsKey(Element.NONE), "reserved or missing elements rolled: " + counts);
	}

	private static Map<Tier, Integer> count(EvolutaConfig config, MutationRoller.Conditions conditions, Random random) {
		Map<Tier, Integer> counts = new EnumMap<>(Tier.class);
		for (Tier tier : Tier.values()) {
			counts.put(tier, 0);
		}
		for (int i = 0; i < ROLLS; i++) {
			counts.merge(MutationRoller.roll(random, config, conditions).getTier(), 1, Integer::sum);
		}
		return counts;
	}

	private static EvolutaConfig with(EvolutaConfig config, double chance, double difficultyBonus, double night) {
		return config.toBuilder().mutationChance(chance).localDifficultyBonus(difficultyBonus).nightMultiplier(night).build();
	}
}
