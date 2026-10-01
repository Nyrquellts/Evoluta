package com.nyr.evoluta.common.mutation;

import com.nyr.evoluta.common.config.EvolutaConfig;
import net.minecraft.util.math.random.Random;
import org.jetbrains.annotations.Nullable;

/** Decides whether a spawning mob mutates and into what. Pure: the world only enters through {@link Conditions}. */
public final class MutationRoller {
	private MutationRoller() {
	}

	/**
	 * What the roll depends on besides the config.
	 *
	 * @param night           the world is at night
	 * @param localDifficulty clamped local difficulty at the spawn, 0 to 1
	 * @param canBrute        the mob's type may be a Brute
	 * @param canStalker      the mob's type may be a Stalker
	 * @param championAllowed an Elite or Apex may spawn here (false caps the roll at Evolved)
	 */
	public record Conditions(boolean night, float localDifficulty, boolean canBrute, boolean canStalker, boolean championAllowed) {
	}

	@Nullable
	public static MutationData roll(Random random, EvolutaConfig config, Conditions conditions) {
		if (random.nextDouble() >= chance(config, conditions)) {
			return null;
		}
		Tier tier = rollTier(random, config, conditions);
		Element element = random.nextDouble() < config.elementChance().get(tier) ? rollElement(random, config) : Element.NONE;
		Archetype archetype = random.nextDouble() < config.archetypeChance().get(tier) ? rollArchetype(random, conditions) : Archetype.NONE;
		return MutationData.of(tier, element, archetype);
	}

	public static double chance(EvolutaConfig config, Conditions conditions) {
		double chance = config.mutationChance() + config.localDifficultyBonus() * conditions.localDifficulty();
		if (conditions.night()) {
			chance *= config.nightMultiplier();
		}
		return Math.max(0.0, Math.min(1.0, chance));
	}

	static Tier rollTier(Random random, EvolutaConfig config, Conditions conditions) {
		EvolutaConfig.PerTier weights = config.tierWeights();
		double evolved = weights.evolved();
		double elite = conditions.championAllowed() ? weights.elite() : 0;
		double apex = conditions.championAllowed() && (conditions.night() || !config.apexOnlyAtNight()) ? weights.apex() : 0;
		double total = evolved + elite + apex;
		if (total <= 0) {
			return Tier.EVOLVED;
		}
		double pick = random.nextDouble() * total;
		if (pick < evolved) {
			return Tier.EVOLVED;
		}
		return pick < evolved + elite ? Tier.ELITE : Tier.APEX;
	}

	static Element rollElement(Random random, EvolutaConfig config) {
		double total = 0;
		for (Element element : Element.IMPLEMENTED) {
			total += config.elementWeights().get(element);
		}
		if (total <= 0) {
			return Element.NONE;
		}
		// the running sum repeats the total's additions in the same order, so it reaches the total exactly
		double pick = random.nextDouble() * total;
		double reached = 0;
		Element last = Element.NONE;
		for (Element element : Element.IMPLEMENTED) {
			double weight = config.elementWeights().get(element);
			reached += weight;
			if (weight > 0) {
				last = element;
				if (pick < reached) {
					return element;
				}
			}
		}
		return last;
	}

	static Archetype rollArchetype(Random random, Conditions conditions) {
		if (conditions.canBrute() && conditions.canStalker()) {
			return random.nextBoolean() ? Archetype.BRUTE : Archetype.STALKER;
		}
		if (conditions.canBrute()) {
			return Archetype.BRUTE;
		}
		return conditions.canStalker() ? Archetype.STALKER : Archetype.NONE;
	}
}
