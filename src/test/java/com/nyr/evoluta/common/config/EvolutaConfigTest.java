package com.nyr.evoluta.common.config;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

import com.google.gson.JsonObject;
import com.google.gson.JsonParser;
import java.util.ArrayList;
import java.util.EnumSet;
import java.util.List;
import java.util.Set;
import net.minecraft.entity.SpawnReason;
import net.minecraft.util.Identifier;
import org.junit.jupiter.api.Test;

class EvolutaConfigTest {
	@Test
	void anEmptyFileMeansEveryDefault() {
		List<String> warnings = new ArrayList<>();
		assertEquals(EvolutaConfig.DEFAULTS, EvolutaConfig.parse(new JsonObject(), warnings));
		assertTrue(warnings.isEmpty(), warnings.toString());
	}

	@Test
	void writtenFileReadsBackUnchanged() {
		EvolutaConfig custom = new EvolutaConfig(0.2, 0.3, 2.0, false,
				new EvolutaConfig.PerTier(10, 5, 1), new EvolutaConfig.PerTier(0.1, 0.2, 0.3), new EvolutaConfig.PerTier(0.4, 0.5, 0.6),
				new EvolutaConfig.PerTier(1, 2, 3), new EvolutaConfig.PerTier(0.5, 1, 1.5), new EvolutaConfig.ElementWeights(2, 0, 1),
				EnumSet.of(SpawnReason.NATURAL, SpawnReason.SPAWNER), Set.of(Identifier.of("minecraft", "the_end")), 3, 200,
				new EvolutaConfig.PerTier(1, 20, 300));
		List<String> warnings = new ArrayList<>();
		JsonObject written = JsonParser.parseString(custom.toJson().toString()).getAsJsonObject();
		assertEquals(custom, EvolutaConfig.parse(written, warnings));
		assertTrue(warnings.isEmpty(), warnings.toString());
	}

	@Test
	void badValuesFallBackOneByOneWithAWarningEach() {
		JsonObject json = JsonParser.parseString("""
				{
				  "mutationChance": 5,
				  "nightMultiplier": "lots",
				  "apexOnlyAtNight": "yes",
				  "tierWeights": {"evolved": -3, "elite": 40},
				  "elementChance": [1, 2, 3],
				  "spawnReasons": ["natural", "summoned_by_wizard", "SPAWNER"],
				  "disabledDimensions": ["minecraft:the_nether", "Not A Dimension!"]
				}
				""").getAsJsonObject();
		List<String> warnings = new ArrayList<>();
		EvolutaConfig config = EvolutaConfig.parse(json, warnings);

		assertEquals(1.0, config.mutationChance(), "clamped to a probability");
		assertEquals(EvolutaConfig.DEFAULTS.nightMultiplier(), config.nightMultiplier());
		assertEquals(EvolutaConfig.DEFAULTS.apexOnlyAtNight(), config.apexOnlyAtNight());
		assertEquals(new EvolutaConfig.PerTier(0, 40, EvolutaConfig.DEFAULTS.tierWeights().apex()), config.tierWeights());
		assertEquals(EvolutaConfig.DEFAULTS.elementChance(), config.elementChance());
		assertEquals(EnumSet.of(SpawnReason.NATURAL, SpawnReason.SPAWNER), config.spawnReasons());
		assertEquals(Set.of(Identifier.of("minecraft", "the_nether")), config.disabledDimensions());
		assertEquals(7, warnings.size(), String.join("\n", warnings));
		assertTrue(warnings.stream().anyMatch(w -> w.startsWith("tierWeights.evolved")), String.join("\n", warnings));
	}

	@Test
	void anEmptyReasonListTurnsSpawnMutationOff() {
		EvolutaConfig config = EvolutaConfig.parse(JsonParser.parseString("{\"spawnReasons\": []}").getAsJsonObject(), new ArrayList<>());
		assertTrue(config.spawnReasons().isEmpty());
	}

	@Test
	void defaultsLeaveSpawnersEggsAndCommandsOrdinary() {
		Set<SpawnReason> reasons = EvolutaConfig.DEFAULTS.spawnReasons();
		assertTrue(reasons.contains(SpawnReason.NATURAL) && reasons.contains(SpawnReason.PATROL));
		for (SpawnReason excluded : List.of(SpawnReason.SPAWNER, SpawnReason.TRIAL_SPAWNER, SpawnReason.SPAWN_EGG,
				SpawnReason.COMMAND, SpawnReason.BREEDING, SpawnReason.CONVERSION)) {
			assertTrue(!reasons.contains(excluded), excluded + " must not mutate by default");
		}
	}
}
