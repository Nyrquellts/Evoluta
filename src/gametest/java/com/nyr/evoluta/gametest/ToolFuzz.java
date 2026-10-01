package com.nyr.evoluta.gametest;

import com.nyr.evoluta.common.item.EvolutaItems;
import com.nyr.evoluta.common.mutation.Element;
import com.nyr.evoluta.common.mutation.Tier;
import com.nyr.evoluta.common.soul.Graft;
import com.nyr.evoluta.common.soul.Grafting;
import com.nyr.evoluta.common.soul.SoulKind;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.SplittableRandom;
import java.util.function.Supplier;
import net.minecraft.block.BlockState;
import net.minecraft.enchantment.Enchantment;
import net.minecraft.enchantment.Enchantments;
import net.minecraft.item.Item;
import net.minecraft.item.ItemStack;
import net.minecraft.registry.Registries;
import net.minecraft.registry.Registry;
import net.minecraft.registry.RegistryKey;
import net.minecraft.registry.RegistryKeys;
import net.minecraft.registry.entry.RegistryEntry;
import net.minecraft.registry.tag.TagKey;
import net.minecraft.server.world.ServerWorld;
import net.minecraft.test.GameTestException;
import net.minecraft.util.math.random.Random;
import net.minecraft.util.math.random.RandomSplitter;

/**
 * Shared pieces of the mining-graft fuzz: its knobs, one seeded random per case (any case replays alone from the seed
 * and its number), random tools and grafts as players would carry them, chance rolls forced one way, and failure
 * collection that reports how to replay what failed.
 */
final class ToolFuzz {
	/** Cases per fuzz test: {@code ./gradlew runGametest -Pevoluta.fuzz.cycles=20000}. */
	static final int CYCLES = Integer.getInteger("evoluta.fuzz.cycles", 3000);
	/** The seed every case derives from: {@code -Pevoluta.fuzz.seed=42}. The default keeps the build repeatable. */
	static final long SEED = Long.getLong("evoluta.fuzz.seed", 20260924L);
	/** Replays one case alone, the rest skipped: {@code -Pevoluta.fuzz.only=333}. */
	static final int ONLY = Integer.getInteger("evoluta.fuzz.only", -1);

	/** Whether case {@code cycle} runs: all of them, or the one being replayed. */
	static boolean runs(int cycle) {
		return ONLY < 0 || cycle == ONLY;
	}
	/** Elements Soul Meat comes in, so the only ones a graft can carry. */
	static final Element[] ELEMENTS = {Element.IGNITED, Element.PERMAFROST, Element.TOXIC};
	/** A roll that fires every chance above zero. */
	static final float ALWAYS = 0.0F;
	/** A roll that fires no chance: every chance is capped below 1. */
	static final float NEVER = Math.nextDown(1.0F);

	private ToolFuzz() {
	}

	static SplittableRandom random(String test, int cycle) {
		long mixed = SEED * 0x9E3779B97F4A7C15L + test.hashCode();
		return new SplittableRandom(mixed * 0x9E3779B97F4A7C15L + cycle);
	}

	static <T> T pick(SplittableRandom random, List<T> list) {
		return list.get(random.nextInt(list.size()));
	}

	static <T> T pick(SplittableRandom random, T[] array) {
		return array[random.nextInt(array.length)];
	}

	static List<Item> items(TagKey<Item> tag) {
		List<Item> items = new ArrayList<>();
		for (RegistryEntry<Item> entry : Registries.ITEM.iterateEntries(tag)) {
			items.add(entry.value());
		}
		if (items.isEmpty()) {
			throw new GameTestException("setup: the tag " + tag.id() + " holds no items");
		}
		return items;
	}

	/** One graft as the forge rolls it: any kind, an element Soul Meat comes in, any grade, a power inside it or at its ends. */
	static Graft graft(SplittableRandom random) {
		Tier grade = pick(random, Tier.values());
		float min = Graft.minPower(grade);
		float max = Graft.maxPower(grade);
		float power = switch (random.nextInt(8)) {
			case 0 -> min;
			case 1 -> max;
			default -> min + (float) random.nextDouble() * (max - min);
		};
		return new Graft(pick(random, SoulKind.values()), pick(random, ELEMENTS), grade, power);
	}

	/** One to {@code most} grafts; now and then several of one kind or element, to exercise the overlap weights. */
	static List<Graft> grafts(SplittableRandom random, int most) {
		int count = 1 + random.nextInt(most);
		List<Graft> grafts = new ArrayList<>();
		for (int i = 0; i < count; i++) {
			Graft graft = graft(random);
			if (i > 0 && random.nextInt(3) == 0) {
				Graft earlier = grafts.get(random.nextInt(i));
				graft = random.nextBoolean()
						? new Graft(earlier.kind(), graft.element(), graft.grade(), graft.power())
						: new Graft(graft.kind(), earlier.element(), graft.grade(), graft.power());
			}
			grafts.add(graft);
		}
		return grafts;
	}

	/** {@code gear} with {@code grafts} put on one by one, as the forge does. */
	static ItemStack grafted(ItemStack gear, List<Graft> grafts) {
		ItemStack stack = gear;
		for (Graft graft : grafts) {
			stack = Grafting.graft(stack, EvolutaItems.soulMeat(graft.element(), graft.kind()), graft.grade(), graft.power());
		}
		return stack;
	}

	/** A tool as players carry them: now and then worn, with Fortune or Silk Touch, Efficiency, Unbreaking. */
	static ItemStack tool(SplittableRandom random, ServerWorld world, Item item) {
		ItemStack stack = new ItemStack(item);
		Registry<Enchantment> enchantments = world.getRegistryManager().get(RegistryKeys.ENCHANTMENT);
		switch (random.nextInt(4)) {
			case 0 -> stack.addEnchantment(entry(enchantments, Enchantments.FORTUNE), 1 + random.nextInt(3));
			case 1 -> stack.addEnchantment(entry(enchantments, Enchantments.SILK_TOUCH), 1);
			default -> {
			}
		}
		if (random.nextInt(3) == 0) {
			stack.addEnchantment(entry(enchantments, Enchantments.EFFICIENCY), 1 + random.nextInt(5));
		}
		if (random.nextInt(3) == 0) {
			stack.addEnchantment(entry(enchantments, Enchantments.UNBREAKING), 1 + random.nextInt(3));
		}
		if (stack.isDamageable() && random.nextInt(3) == 0) {
			stack.setDamage(random.nextInt(stack.getMaxDamage()));
		}
		return stack;
	}

	static RegistryEntry<Enchantment> entry(Registry<Enchantment> registry, RegistryKey<Enchantment> key) {
		return registry.getEntry(key).orElseThrow(() -> new GameTestException("setup: no enchantment " + key.getValue()));
	}

	static RegistryEntry<Enchantment> enchantment(ServerWorld world, RegistryKey<Enchantment> key) {
		return entry(world.getRegistryManager().get(RegistryKeys.ENCHANTMENT), key);
	}

	/** A random whose every float roll comes out {@code roll}; every other draw is a real, seeded one. */
	static Random forced(float roll, long seed) {
		Random real = Random.create(seed);
		return new Random() {
			@Override
			public Random split() {
				return real.split();
			}

			@Override
			public RandomSplitter nextSplitter() {
				return real.nextSplitter();
			}

			@Override
			public void setSeed(long newSeed) {
				real.setSeed(newSeed);
			}

			@Override
			public int nextInt() {
				return real.nextInt();
			}

			@Override
			public int nextInt(int bound) {
				return real.nextInt(bound);
			}

			@Override
			public long nextLong() {
				return real.nextLong();
			}

			@Override
			public boolean nextBoolean() {
				return real.nextBoolean();
			}

			@Override
			public float nextFloat() {
				return roll;
			}

			@Override
			public double nextDouble() {
				return real.nextDouble();
			}

			@Override
			public double nextGaussian() {
				return real.nextGaussian();
			}
		};
	}

	static String describe(ItemStack stack) {
		StringBuilder text = new StringBuilder(Registries.ITEM.getId(stack.getItem()).getPath());
		for (RegistryEntry<Enchantment> enchantment : stack.getEnchantments().getEnchantments()) {
			text.append(' ').append(enchantment.getKey().map(key -> key.getValue().getPath()).orElse("?")).append(' ')
					.append(stack.getEnchantments().getLevel(enchantment));
		}
		if (stack.isDamaged()) {
			text.append(" damage ").append(stack.getDamage()).append('/').append(stack.getMaxDamage());
		}
		List<Graft> grafts = Grafting.grafts(stack);
		if (!grafts.isEmpty()) {
			text.append(" grafts [");
			for (int i = 0; i < grafts.size(); i++) {
				Graft graft = grafts.get(i);
				text.append(i > 0 ? ", " : "").append(graft.element().key()).append(' ').append(graft.kind().key()).append(' ')
						.append(graft.grade().key()).append(String.format(" %.3f", graft.power()));
			}
			text.append(']');
		}
		return text.toString();
	}

	static String describe(List<ItemStack> stacks) {
		List<String> parts = new ArrayList<>();
		for (ItemStack stack : stacks) {
			String id = Registries.ITEM.getId(stack.getItem()).getPath();
			parts.add(stack.getCount() + " " + id + (stack.getComponentChanges().isEmpty() ? "" : " " + stack.getComponentChanges()));
		}
		return parts.toString();
	}

	static String describe(BlockState state) {
		return state.toString().replace("Block{minecraft:", "{").replace("Block{", "{");
	}

	static boolean sameStacks(List<ItemStack> a, List<ItemStack> b) {
		if (a.size() != b.size()) {
			return false;
		}
		for (int i = 0; i < a.size(); i++) {
			if (!ItemStack.areEqual(a.get(i), b.get(i))) {
				return false;
			}
		}
		return true;
	}

	static List<ItemStack> copies(List<ItemStack> stacks) {
		List<ItemStack> copies = new ArrayList<>(stacks.size());
		for (ItemStack stack : stacks) {
			copies.add(stack.copy());
		}
		return copies;
	}

	/**
	 * Failed cases of one test: counted, grouped by the check that failed (each check's message is its own lambda),
	 * two kept of each, thrown together at the end so one run shows every kind of failure.
	 */
	static final class Failures {
		private static final int PER_KIND = 2;
		private static final int KINDS = 40;
		private final String test;
		private final Map<String, List<String>> kinds = new LinkedHashMap<>();
		private final Map<String, Integer> counts = new HashMap<>();
		private int count;
		private int cases;

		Failures(String test) {
			this.test = test;
		}

		void ran() {
			this.cases++;
		}

		/** A case that threw: grouped by what it threw. */
		void add(int cycle, String what) {
			int thrown = what.lastIndexOf(" threw ");
			String kind = thrown < 0 ? what : "threw " + what.substring(thrown + 7).split("[:\\s]", 2)[0];
			this.record(cycle, what, kind);
		}

		void check(boolean ok, int cycle, Supplier<String> what) {
			if (!ok) {
				this.record(cycle, what.get(), what.getClass().getName());
			}
		}

		private void record(int cycle, String what, String kind) {
			this.count++;
			this.counts.merge(kind, 1, Integer::sum);
			List<String> examples = this.kinds.get(kind);
			if (examples == null && this.kinds.size() < KINDS) {
				examples = new ArrayList<>();
				this.kinds.put(kind, examples);
			}
			if (examples != null && examples.size() < PER_KIND) {
				examples.add("case " + cycle + ": " + what);
			}
		}

		int cases() {
			return this.cases;
		}

		void throwIfAny() {
			if (this.count > 0) {
				StringBuilder report = new StringBuilder(this.test + ": " + this.count + " failures in " + this.cases + " cases (seed " + SEED
						+ ", replay with -Pevoluta.fuzz.seed=" + SEED + "), " + this.kinds.size() + " kinds:");
				for (Map.Entry<String, List<String>> kind : this.kinds.entrySet()) {
					report.append("\n  ").append(this.counts.get(kind.getKey())).append(" like:");
					for (String example : kind.getValue()) {
						report.append("\n      ").append(example);
					}
				}
				throw new GameTestException(report.toString());
			}
		}
	}
}
