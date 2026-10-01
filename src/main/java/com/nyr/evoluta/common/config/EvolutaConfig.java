package com.nyr.evoluta.common.config;

import com.google.gson.Gson;
import com.google.gson.GsonBuilder;
import com.google.gson.JsonArray;
import com.google.gson.JsonElement;
import com.google.gson.JsonObject;
import com.google.gson.JsonParseException;
import com.google.gson.JsonParser;
import com.google.gson.JsonPrimitive;
import com.nyr.evoluta.common.Evoluta;
import com.nyr.evoluta.common.mutation.Element;
import com.nyr.evoluta.common.mutation.Tier;
import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.Collections;
import java.util.EnumSet;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Locale;
import java.util.Set;
import net.fabricmc.loader.api.FabricLoader;
import net.minecraft.entity.SpawnReason;
import net.minecraft.util.Identifier;

/**
 * Server settings, read from {@code config/evoluta.json}. A missing key takes its default, a bad value takes the
 * nearest valid one with a warning, and the file is rewritten with every key so server owners see what they can change.
 * Which mobs mutate is not here: that is the {@code #evoluta:can_mutate} tag.
 *
 * @param mutationChance       chance that an eligible spawn mutates, before difficulty and night
 * @param localDifficultyBonus added to the chance, scaled by the clamped local difficulty (0 to 1)
 * @param nightMultiplier      multiplies the chance while the world is at night
 * @param apexOnlyAtNight      whether Apex mutants only spawn at night
 * @param tierWeights          relative weights of Evolved, Elite and Apex among mutants
 * @param elementChance        per tier, the chance a mutant carries an element
 * @param archetypeChance      per tier, the chance a mutant takes an archetype its type allows
 * @param healthBonus          per tier, added share of base max health (0.8 = +80%)
 * @param damageBonus          per tier, added share of base attack damage
 * @param elementWeights       relative weights of the elements; 0 turns one off
 * @param spawnReasons         spawn reasons that can mutate; spawners and spawn eggs are left out by default
 * @param disabledDimensions   dimensions where nothing mutates
 * @param maxChampionsPerChunk Elite and Apex mutants a chunk may hold before new spawns there cap at Evolved; 0 means
 *                             champions never spawn
 * @param locatorRange         blocks within which the Blight Locator finds champions
 * @param xpBonus              per tier, experience added to what vanilla drops for the mob
 */
public record EvolutaConfig(
		double mutationChance,
		double localDifficultyBonus,
		double nightMultiplier,
		boolean apexOnlyAtNight,
		PerTier tierWeights,
		PerTier elementChance,
		PerTier archetypeChance,
		PerTier healthBonus,
		PerTier damageBonus,
		ElementWeights elementWeights,
		Set<SpawnReason> spawnReasons,
		Set<Identifier> disabledDimensions,
		int maxChampionsPerChunk,
		double locatorRange,
		PerTier xpBonus
) {
	public static final String FILE_NAME = "evoluta.json";

	public static final EvolutaConfig DEFAULTS = new EvolutaConfig(
			0.05,
			0.10,
			1.5,
			true,
			new PerTier(75, 20, 5),
			new PerTier(0.5, 0.9, 1.0),
			new PerTier(0.35, 0.65, 1.0),
			new PerTier(0.3, 0.8, 1.5),
			new PerTier(0.15, 0.35, 0.6),
			new ElementWeights(1, 1, 1),
			Collections.unmodifiableSet(EnumSet.of(SpawnReason.NATURAL, SpawnReason.CHUNK_GENERATION, SpawnReason.STRUCTURE,
					SpawnReason.PATROL, SpawnReason.REINFORCEMENT, SpawnReason.JOCKEY, SpawnReason.EVENT)),
			Set.of(),
			1,
			128,
			new PerTier(5, 15, 40)
	);

	private static final Gson GSON = new GsonBuilder().setPrettyPrinting().disableHtmlEscaping().create();
	private static volatile EvolutaConfig current = DEFAULTS;

	public EvolutaConfig {
		spawnReasons = spawnReasons.isEmpty() ? Set.of() : Collections.unmodifiableSet(EnumSet.copyOf(spawnReasons));
		disabledDimensions = Set.copyOf(disabledDimensions);
	}

	public static EvolutaConfig get() {
		return current;
	}

	/** Makes {@code config} current without touching the file (game tests and embedding mods). */
	public static void use(EvolutaConfig config) {
		current = config;
	}

	public static Path path() {
		return FabricLoader.getInstance().getConfigDir().resolve(FILE_NAME);
	}

	/**
	 * Reads the file and makes it current. When the file cannot be read at all, startup falls back to the defaults
	 * while a reload keeps the settings already in use; the file is then left untouched for the server owner to fix.
	 *
	 * @return every problem found, one line each
	 */
	public static synchronized List<String> load(boolean keepCurrentOnError) {
		Path path = path();
		List<String> warnings = new ArrayList<>();
		EvolutaConfig loaded = DEFAULTS;
		if (Files.isRegularFile(path)) {
			try {
				JsonElement root = JsonParser.parseString(Files.readString(path));
				if (!root.isJsonObject()) {
					throw new JsonParseException("the top level must be a JSON object");
				}
				loaded = parse(root.getAsJsonObject(), warnings);
			} catch (IOException | JsonParseException e) {
				warnings.add("could not read " + FILE_NAME + " (" + e.getMessage() + "); "
						+ (keepCurrentOnError ? "keeping the settings in use" : "using defaults") + " and leaving the file as it is");
				warnings.forEach(warning -> Evoluta.LOGGER.warn("[config] {}", warning));
				if (!keepCurrentOnError) {
					current = DEFAULTS;
				}
				return warnings;
			}
		}
		try {
			Files.createDirectories(path.getParent());
			Files.writeString(path, GSON.toJson(loaded.toJson()) + System.lineSeparator());
		} catch (IOException e) {
			warnings.add("could not write " + FILE_NAME + " (" + e.getMessage() + ")");
		}
		current = loaded;
		warnings.forEach(warning -> Evoluta.LOGGER.warn("[config] {}", warning));
		return warnings;
	}

	public static EvolutaConfig parse(JsonObject json, List<String> warnings) {
		EvolutaConfig d = DEFAULTS;
		return new EvolutaConfig(
				number(json, "mutationChance", d.mutationChance, 0, 1, warnings),
				number(json, "localDifficultyBonus", d.localDifficultyBonus, 0, 1, warnings),
				number(json, "nightMultiplier", d.nightMultiplier, 0, 100, warnings),
				bool(json, "apexOnlyAtNight", d.apexOnlyAtNight, warnings),
				perTier(json, "tierWeights", d.tierWeights, 1_000_000, warnings),
				perTier(json, "elementChance", d.elementChance, 1, warnings),
				perTier(json, "archetypeChance", d.archetypeChance, 1, warnings),
				perTier(json, "healthBonus", d.healthBonus, 100, warnings),
				perTier(json, "damageBonus", d.damageBonus, 100, warnings),
				elementWeights(json, d.elementWeights, warnings),
				spawnReasons(json, d.spawnReasons, warnings),
				dimensions(json, d.disabledDimensions, warnings),
				(int) Math.round(number(json, "maxChampionsPerChunk", d.maxChampionsPerChunk, 0, 64, warnings)),
				number(json, "locatorRange", d.locatorRange, 16, 512, warnings),
				perTier(json, "xpBonus", d.xpBonus, 10_000, warnings)
		);
	}

	public Builder toBuilder() {
		return new Builder(this);
	}

	public JsonObject toJson() {
		JsonObject json = new JsonObject();
		json.addProperty("mutationChance", this.mutationChance);
		json.addProperty("localDifficultyBonus", this.localDifficultyBonus);
		json.addProperty("nightMultiplier", this.nightMultiplier);
		json.addProperty("apexOnlyAtNight", this.apexOnlyAtNight);
		json.add("tierWeights", this.tierWeights.toJson());
		json.add("elementChance", this.elementChance.toJson());
		json.add("archetypeChance", this.archetypeChance.toJson());
		json.add("healthBonus", this.healthBonus.toJson());
		json.add("damageBonus", this.damageBonus.toJson());
		json.add("elementWeights", this.elementWeights.toJson());
		JsonArray reasons = new JsonArray();
		for (SpawnReason reason : SpawnReason.values()) {
			if (this.spawnReasons.contains(reason)) {
				reasons.add(reason.name().toLowerCase(Locale.ROOT));
			}
		}
		json.add("spawnReasons", reasons);
		JsonArray dimensions = new JsonArray();
		this.disabledDimensions.stream().map(Identifier::toString).sorted().forEach(dimensions::add);
		json.add("disabledDimensions", dimensions);
		json.addProperty("maxChampionsPerChunk", this.maxChampionsPerChunk);
		json.addProperty("locatorRange", this.locatorRange);
		json.add("xpBonus", this.xpBonus.toJson());
		return json;
	}

	/** A copy with some settings changed: {@code EvolutaConfig.DEFAULTS.toBuilder().mutationChance(1).build()}. */
	public static final class Builder {
		private double mutationChance;
		private double localDifficultyBonus;
		private double nightMultiplier;
		private boolean apexOnlyAtNight;
		private PerTier tierWeights;
		private PerTier elementChance;
		private PerTier archetypeChance;
		private PerTier healthBonus;
		private PerTier damageBonus;
		private ElementWeights elementWeights;
		private Set<SpawnReason> spawnReasons;
		private Set<Identifier> disabledDimensions;
		private int maxChampionsPerChunk;
		private double locatorRange;
		private PerTier xpBonus;

		private Builder(EvolutaConfig config) {
			this.mutationChance = config.mutationChance;
			this.localDifficultyBonus = config.localDifficultyBonus;
			this.nightMultiplier = config.nightMultiplier;
			this.apexOnlyAtNight = config.apexOnlyAtNight;
			this.tierWeights = config.tierWeights;
			this.elementChance = config.elementChance;
			this.archetypeChance = config.archetypeChance;
			this.healthBonus = config.healthBonus;
			this.damageBonus = config.damageBonus;
			this.elementWeights = config.elementWeights;
			this.spawnReasons = config.spawnReasons;
			this.disabledDimensions = config.disabledDimensions;
			this.maxChampionsPerChunk = config.maxChampionsPerChunk;
			this.locatorRange = config.locatorRange;
			this.xpBonus = config.xpBonus;
		}

		public Builder mutationChance(double value) {
			this.mutationChance = value;
			return this;
		}

		public Builder localDifficultyBonus(double value) {
			this.localDifficultyBonus = value;
			return this;
		}

		public Builder nightMultiplier(double value) {
			this.nightMultiplier = value;
			return this;
		}

		public Builder apexOnlyAtNight(boolean value) {
			this.apexOnlyAtNight = value;
			return this;
		}

		public Builder tierWeights(double evolved, double elite, double apex) {
			this.tierWeights = new PerTier(evolved, elite, apex);
			return this;
		}

		public Builder elementChance(double evolved, double elite, double apex) {
			this.elementChance = new PerTier(evolved, elite, apex);
			return this;
		}

		public Builder archetypeChance(double evolved, double elite, double apex) {
			this.archetypeChance = new PerTier(evolved, elite, apex);
			return this;
		}

		public Builder healthBonus(double evolved, double elite, double apex) {
			this.healthBonus = new PerTier(evolved, elite, apex);
			return this;
		}

		public Builder damageBonus(double evolved, double elite, double apex) {
			this.damageBonus = new PerTier(evolved, elite, apex);
			return this;
		}

		public Builder elementWeights(double ignited, double permafrost, double toxic) {
			this.elementWeights = new ElementWeights(ignited, permafrost, toxic);
			return this;
		}

		public Builder spawnReasons(Set<SpawnReason> value) {
			this.spawnReasons = value;
			return this;
		}

		public Builder disabledDimensions(Set<Identifier> value) {
			this.disabledDimensions = value;
			return this;
		}

		public Builder maxChampionsPerChunk(int value) {
			this.maxChampionsPerChunk = value;
			return this;
		}

		public Builder locatorRange(double value) {
			this.locatorRange = value;
			return this;
		}

		public Builder xpBonus(double evolved, double elite, double apex) {
			this.xpBonus = new PerTier(evolved, elite, apex);
			return this;
		}

		public EvolutaConfig build() {
			return new EvolutaConfig(this.mutationChance, this.localDifficultyBonus, this.nightMultiplier, this.apexOnlyAtNight,
					this.tierWeights, this.elementChance, this.archetypeChance, this.healthBonus, this.damageBonus,
					this.elementWeights, this.spawnReasons, this.disabledDimensions, this.maxChampionsPerChunk, this.locatorRange,
					this.xpBonus);
		}
	}

	/** One value per tier. */
	public record PerTier(double evolved, double elite, double apex) {
		public double get(Tier tier) {
			return switch (tier) {
				case EVOLVED -> this.evolved;
				case ELITE -> this.elite;
				case APEX -> this.apex;
			};
		}

		JsonObject toJson() {
			JsonObject json = new JsonObject();
			json.addProperty("evolved", this.evolved);
			json.addProperty("elite", this.elite);
			json.addProperty("apex", this.apex);
			return json;
		}
	}

	/** Relative roll weights of the elements that have behaviour. */
	public record ElementWeights(double ignited, double permafrost, double toxic) {
		public double get(Element element) {
			return switch (element) {
				case IGNITED -> this.ignited;
				case PERMAFROST -> this.permafrost;
				case TOXIC -> this.toxic;
				case NONE, ABYSSAL -> 0;
			};
		}

		JsonObject toJson() {
			JsonObject json = new JsonObject();
			json.addProperty("ignited", this.ignited);
			json.addProperty("permafrost", this.permafrost);
			json.addProperty("toxic", this.toxic);
			return json;
		}
	}

	private static double number(JsonObject json, String key, double fallback, double min, double max, List<String> warnings) {
		return number(json, "", key, fallback, min, max, warnings);
	}

	private static double number(JsonObject json, String prefix, String key, double fallback, double min, double max, List<String> warnings) {
		JsonElement element = json.get(key);
		if (element == null) {
			return fallback;
		}
		if (!(element instanceof JsonPrimitive primitive) || !primitive.isNumber()) {
			warnings.add(prefix + key + " is not a number; using " + fallback);
			return fallback;
		}
		double value = primitive.getAsDouble();
		if (Double.isNaN(value)) {
			warnings.add(prefix + key + " is not a number; using " + fallback);
			return fallback;
		}
		if (value < min || value > max) {
			double clamped = Math.max(min, Math.min(max, value));
			warnings.add(prefix + key + " = " + value + " is outside " + min + " to " + max + "; using " + clamped);
			return clamped;
		}
		return value;
	}

	private static boolean bool(JsonObject json, String key, boolean fallback, List<String> warnings) {
		JsonElement element = json.get(key);
		if (element == null) {
			return fallback;
		}
		if (!(element instanceof JsonPrimitive primitive) || !primitive.isBoolean()) {
			warnings.add(key + " is not true or false; using " + fallback);
			return fallback;
		}
		return primitive.getAsBoolean();
	}

	private static JsonObject object(JsonObject json, String key, List<String> warnings) {
		JsonElement element = json.get(key);
		if (element == null) {
			return new JsonObject();
		}
		if (!element.isJsonObject()) {
			warnings.add(key + " is not an object; using its defaults");
			return new JsonObject();
		}
		return element.getAsJsonObject();
	}

	private static PerTier perTier(JsonObject json, String key, PerTier fallback, double max, List<String> warnings) {
		JsonObject values = object(json, key, warnings);
		String prefix = key + ".";
		return new PerTier(
				number(values, prefix, "evolved", fallback.evolved, 0, max, warnings),
				number(values, prefix, "elite", fallback.elite, 0, max, warnings),
				number(values, prefix, "apex", fallback.apex, 0, max, warnings)
		);
	}

	private static ElementWeights elementWeights(JsonObject json, ElementWeights fallback, List<String> warnings) {
		JsonObject values = object(json, "elementWeights", warnings);
		return new ElementWeights(
				number(values, "elementWeights.", "ignited", fallback.ignited, 0, 1_000_000, warnings),
				number(values, "elementWeights.", "permafrost", fallback.permafrost, 0, 1_000_000, warnings),
				number(values, "elementWeights.", "toxic", fallback.toxic, 0, 1_000_000, warnings)
		);
	}

	private static Set<SpawnReason> spawnReasons(JsonObject json, Set<SpawnReason> fallback, List<String> warnings) {
		JsonElement element = json.get("spawnReasons");
		if (element == null) {
			return fallback;
		}
		if (!element.isJsonArray()) {
			warnings.add("spawnReasons is not a list; using the defaults");
			return fallback;
		}
		Set<SpawnReason> reasons = EnumSet.noneOf(SpawnReason.class);
		for (JsonElement entry : element.getAsJsonArray()) {
			String name = entry.isJsonPrimitive() ? entry.getAsString() : entry.toString();
			try {
				reasons.add(SpawnReason.valueOf(name.toUpperCase(Locale.ROOT)));
			} catch (IllegalArgumentException e) {
				warnings.add("spawnReasons: unknown spawn reason \"" + name + "\" skipped");
			}
		}
		return reasons;
	}

	private static Set<Identifier> dimensions(JsonObject json, Set<Identifier> fallback, List<String> warnings) {
		JsonElement element = json.get("disabledDimensions");
		if (element == null) {
			return fallback;
		}
		if (!element.isJsonArray()) {
			warnings.add("disabledDimensions is not a list; using the defaults");
			return fallback;
		}
		Set<Identifier> dimensions = new LinkedHashSet<>();
		for (JsonElement entry : element.getAsJsonArray()) {
			Identifier id = entry.isJsonPrimitive() ? Identifier.tryParse(entry.getAsString()) : null;
			if (id == null) {
				warnings.add("disabledDimensions: \"" + entry + "\" is not a dimension id; skipped");
			} else {
				dimensions.add(id);
			}
		}
		return dimensions;
	}
}
