package com.nyr.evoluta.common.config;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.junit.jupiter.api.Assertions.fail;

import com.google.gson.GsonBuilder;
import com.google.gson.JsonArray;
import com.google.gson.JsonElement;
import com.google.gson.JsonNull;
import com.google.gson.JsonObject;
import com.google.gson.JsonParser;
import com.google.gson.JsonPrimitive;
import com.nyr.evoluta.common.mutation.Element;
import com.nyr.evoluta.common.mutation.Tier;
import java.util.ArrayList;
import java.util.List;
import java.util.SplittableRandom;
import net.minecraft.entity.SpawnReason;
import org.junit.jupiter.api.Test;

/**
 * The config file as server owners edit it, and worse: random objects of the right keys and junk ones, holding numbers in and
 * out of range (huge, negative, NaN, infinite), strings, booleans, nulls, lists and objects in any place. Reading one
 * never throws, every setting lands in its documented range with no NaN, and what the mod writes back reads back
 * unchanged with no warning. Seed and size: -Pevoluta.fuzz.seed / -Pevoluta.fuzz.cycles on the Gradle command line.
 */
class EvolutaConfigFuzzTest {
	private static final long SEED = Long.getLong("evoluta.fuzz.seed", 20260924L);
	private static final int CYCLES = Integer.getInteger("evoluta.fuzz.cycles", 3000);
	private static final String[] KEYS = {"mutationChance", "localDifficultyBonus", "nightMultiplier", "apexOnlyAtNight", "tierWeights",
			"elementChance", "archetypeChance", "healthBonus", "damageBonus", "elementWeights", "spawnReasons", "disabledDimensions",
			"maxChampionsPerChunk", "locatorRange", "xpBonus", "junk", "MutationChance", ""};
	private static final String[] SUBKEYS = {"evolved", "elite", "apex", "ignited", "permafrost", "toxic", "abyssal", "none", "Apex"};

	private static JsonElement value(SplittableRandom random, int depth) {
		return switch (random.nextInt(depth > 1 ? 9 : 12)) {
			case 0 -> new JsonPrimitive(random.nextDouble());
			case 1 -> new JsonPrimitive(random.nextDouble() * 2_000_000 - 1_000_000);
			case 2 -> new JsonPrimitive(new double[]{Double.NaN, Double.POSITIVE_INFINITY, Double.NEGATIVE_INFINITY, -0.0, 1e308, -1e308,
					Double.MIN_VALUE}[random.nextInt(7)]);
			case 3 -> new JsonPrimitive(random.nextInt(200) - 100);
			case 4 -> new JsonPrimitive(random.nextBoolean());
			case 5 -> new JsonPrimitive(new String[]{"0.5", "natural", "NATURAL", "minecraft:the_nether", "Bad Id!", "", "true", "NaN"}[random.nextInt(8)]);
			case 6 -> JsonNull.INSTANCE;
			case 7 -> new JsonPrimitive(Long.MAX_VALUE);
			case 8 -> new JsonPrimitive(new java.math.BigDecimal("1e400"));
			case 9 -> {
				JsonArray array = new JsonArray();
				for (int i = random.nextInt(5); i > 0; i--) {
					array.add(random.nextBoolean() ? new JsonPrimitive(SpawnReason.values()[random.nextInt(SpawnReason.values().length)].name()
							.toLowerCase()) : value(random, depth + 1));
				}
				yield array;
			}
			default -> {
				JsonObject object = new JsonObject();
				for (int i = random.nextInt(5); i > 0; i--) {
					object.add(SUBKEYS[random.nextInt(SUBKEYS.length)], value(random, depth + 1));
				}
				yield object;
			}
		};
	}

	private static void inRange(double value, double min, double max, String what) {
		assertTrue(!Double.isNaN(value) && value >= min && value <= max, what + " = " + value + ", outside " + min + " to " + max);
	}

	private static void inRange(EvolutaConfig.PerTier values, double max, String what) {
		for (Tier tier : Tier.values()) {
			inRange(values.get(tier), 0, max, what + "." + tier.key());
		}
	}

	@Test
	void anyFileReadsIntoSettingsInRangeAndWritesBackUnchanged() {
		for (int cycle = 0; cycle < CYCLES; cycle++) {
			SplittableRandom random = new SplittableRandom(SEED * 0x9E3779B97F4A7C15L + cycle);
			JsonObject json = new JsonObject();
			for (int i = random.nextInt(12); i > 0; i--) {
				json.add(KEYS[random.nextInt(KEYS.length)], value(random, 0));
			}
			String what = "case " + cycle + " " + json;
			List<String> warnings = new ArrayList<>();
			EvolutaConfig config;
			try {
				config = EvolutaConfig.parse(json, warnings);
			} catch (RuntimeException e) {
				fail(what + " threw " + e, e);
				return;
			}
			inRange(config.mutationChance(), 0, 1, what + ": mutationChance");
			inRange(config.localDifficultyBonus(), 0, 1, what + ": localDifficultyBonus");
			inRange(config.nightMultiplier(), 0, 100, what + ": nightMultiplier");
			inRange(config.tierWeights(), 1_000_000, what + ": tierWeights");
			inRange(config.elementChance(), 1, what + ": elementChance");
			inRange(config.archetypeChance(), 1, what + ": archetypeChance");
			inRange(config.healthBonus(), 100, what + ": healthBonus");
			inRange(config.damageBonus(), 100, what + ": damageBonus");
			inRange(config.xpBonus(), 10_000, what + ": xpBonus");
			for (Element element : Element.values()) {
				inRange(config.elementWeights().get(element), 0, 1_000_000, what + ": elementWeights." + element.key());
			}
			inRange(config.maxChampionsPerChunk(), 0, 64, what + ": maxChampionsPerChunk");
			inRange(config.locatorRange(), 16, 512, what + ": locatorRange");
			// what the mod writes back to the file reads back as the same settings, with nothing to warn about
			String written = new GsonBuilder().setPrettyPrinting().disableHtmlEscaping().create().toJson(config.toJson());
			List<String> again = new ArrayList<>();
			EvolutaConfig reread = EvolutaConfig.parse(JsonParser.parseString(written).getAsJsonObject(), again);
			assertEquals(config, reread, what + ": written as " + written + " it reads back differently");
			assertTrue(again.isEmpty(), what + ": the written file warns " + again);
		}
	}
}
