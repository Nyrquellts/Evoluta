package com.nyr.evoluta.gametest;

import com.nyr.evoluta.common.item.EvolutaItems;
import com.nyr.evoluta.common.mutation.Archetype;
import com.nyr.evoluta.common.mutation.Element;
import com.nyr.evoluta.common.mutation.EvolutaAttachments;
import com.nyr.evoluta.common.mutation.Mutations;
import com.nyr.evoluta.common.mutation.Tier;
import com.nyr.evoluta.common.forge.SoulForgeScreenHandler;
import com.nyr.evoluta.common.soul.Graft;
import com.nyr.evoluta.common.soul.Grafting;
import com.nyr.evoluta.common.soul.MutantGear;
import com.nyr.evoluta.common.soul.SoulComponents;
import com.nyr.evoluta.common.soul.SoulKind;
import io.netty.buffer.Unpooled;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.SplittableRandom;
import java.util.function.ToDoubleFunction;
import net.fabricmc.fabric.api.gametest.v1.FabricGameTest;
import net.minecraft.block.Block;
import net.minecraft.block.BlockState;
import net.minecraft.block.Blocks;
import net.minecraft.block.CropBlock;
import net.minecraft.component.DataComponentTypes;
import net.minecraft.entity.EntityType;
import net.minecraft.entity.EquipmentSlot;
import net.minecraft.entity.ExperienceOrbEntity;
import net.minecraft.entity.ItemEntity;
import net.minecraft.entity.attribute.EntityAttribute;
import net.minecraft.entity.attribute.EntityAttributeInstance;
import net.minecraft.entity.attribute.EntityAttributeModifier;
import net.minecraft.entity.attribute.EntityAttributes;
import net.minecraft.entity.mob.ZombieEntity;
import net.minecraft.entity.player.PlayerEntity;
import net.minecraft.item.Item;
import net.minecraft.item.ItemStack;
import net.minecraft.item.Items;
import net.minecraft.nbt.NbtCompound;
import net.minecraft.nbt.NbtElement;
import net.minecraft.nbt.NbtOps;
import net.minecraft.network.RegistryByteBuf;
import net.minecraft.registry.entry.RegistryEntry;
import net.minecraft.screen.ScreenHandlerContext;
import net.minecraft.server.network.ServerPlayerEntity;
import net.minecraft.server.world.ServerWorld;
import net.minecraft.test.GameTest;
import net.minecraft.test.TestContext;
import net.minecraft.text.Text;
import net.minecraft.util.Hand;
import net.minecraft.util.Identifier;
import net.minecraft.util.math.BlockPos;
import net.minecraft.util.math.Box;
import net.minecraft.util.math.random.Random;
import net.minecraft.world.GameMode;

/**
 * The mining grafts from the other ends: every graftable item's attribute modifiers against the documented numbers,
 * saved, sent, equipped and scrubbed; the chances measured against the numbers the README states; the Soul Forge,
 * the command and mutants' gear putting grafts on every tool.
 */
public final class ToolGraftAuditGameTest implements FabricGameTest {
	private static final String BATCH = "evoluta_tool_audit";
	private static final int PLACE = Block.NOTIFY_LISTENERS | Block.FORCE_STATE;

	private record Key(RegistryEntry<EntityAttribute> attribute, Identifier id) {
	}

	/** The modifiers {@code stack} gives in {@code slot}, by attribute and id; a repeated pair is a failure of its own. */
	private static Map<Key, EntityAttributeModifier> modifiers(ItemStack stack, EquipmentSlot slot, List<Key> repeated) {
		Map<Key, EntityAttributeModifier> modifiers = new HashMap<>();
		stack.applyAttributeModifiers(slot, (attribute, modifier) -> {
			Key key = new Key(attribute, modifier.id());
			if (modifiers.put(key, modifier) != null) {
				repeated.add(key);
			}
		});
		return modifiers;
	}

	private static boolean isGraftModifier(Identifier id) {
		return id.getNamespace().equals("evoluta") && id.getPath().startsWith("graft/");
	}

	/**
	 * Every weapon, armour piece and tool that takes grafts, eight times over with random grafts (up to the limit),
	 * enchantments, wear and names: the item keeps every modifier it had, gains only graft modifiers, never one
	 * attribute and id twice (equipping it would throw); mining gear gets exactly the documented mining bonuses and a
	 * pickaxe no weapon bonus; the stack saves, loads and crosses the network unchanged; the Soul Forge refuses a fourth
	 * graft; scrubbing every graft gives back exactly the item it was.
	 */
	@GameTest(templateName = EMPTY_STRUCTURE, batchId = BATCH, tickLimit = 400)
	public void graftedGearKeepsItsOwnModifiersAndGetsExactlyItsBonuses(TestContext context) {
		ServerWorld world = context.getWorld();
		ServerPlayerEntity player = TestPlayers.survival(context, new BlockPos(1, 2, 1), 0);
		Set<Item> gear = new LinkedHashSet<>(ToolFuzz.items(Grafting.WEAPONS));
		gear.addAll(ToolFuzz.items(Grafting.ARMOR));
		gear.addAll(ToolFuzz.items(Grafting.TOOLS));
		ToolFuzz.Failures failures = new ToolFuzz.Failures("modifier fuzz");
		int rounds = Math.max(8, ToolFuzz.CYCLES / 300);
		int cycle = 0;
		try {
			for (Item item : gear) {
				for (int round = 0; round < rounds; round++, cycle++) {
					SplittableRandom random = ToolFuzz.random("modifiers", cycle);
					ItemStack before = random.nextBoolean() ? ToolFuzz.tool(random, world, item) : new ItemStack(item);
					if (random.nextInt(4) == 0) {
						before.set(DataComponentTypes.CUSTOM_NAME, Text.literal("fuzz " + cycle));
					}
					List<Graft> grafts = ToolFuzz.grafts(random, Grafting.MAX_GRAFTS);
					failures.ran();
					try {
						checkModifiers(world, player, before, grafts, failures, cycle);
					} catch (RuntimeException e) {
						failures.add(cycle, ToolFuzz.describe(ToolFuzz.grafted(before, grafts)) + " threw " + e);
					}
				}
			}
		} finally {
			TestPlayers.remove(context, player);
		}
		failures.throwIfAny();
		context.complete();
	}

	private static void checkModifiers(ServerWorld world, ServerPlayerEntity player, ItemStack before, List<Graft> grafts, ToolFuzz.Failures failures,
			int cycle) {
		EquipmentSlot slot = player.getPreferredEquipmentSlot(before);
		List<Key> repeated = new ArrayList<>();
		Map<Key, EntityAttributeModifier> own = modifiers(before, slot, repeated);
		ItemStack stack = before;
		for (Graft graft : grafts) {
			ItemStack current = stack;
			failures.check(Grafting.canGraft(current), cycle, () -> ToolFuzz.describe(current) + " refused a graft below the limit");
			stack = Grafting.graft(stack, EvolutaItems.soulMeat(graft.element(), graft.kind()), graft.grade(), graft.power());
		}
		ItemStack grafted = stack;
		String what = ToolFuzz.describe(grafted);
		if (grafts.size() == Grafting.MAX_GRAFTS) {
			failures.check(!Grafting.canGraft(grafted), cycle, () -> what + " took a graft past the limit");
		}
		Map<Key, EntityAttributeModifier> after = modifiers(grafted, slot, repeated);
		failures.check(repeated.isEmpty(), cycle, () -> what + ": attribute and id given twice " + repeated);
		for (Map.Entry<Key, EntityAttributeModifier> entry : own.entrySet()) {
			failures.check(entry.getValue().equals(after.get(entry.getKey())), cycle, () -> what + " lost or changed its own modifier " + entry.getValue()
					+ " on " + entry.getKey().attribute().getIdAsString() + ": now " + after.get(entry.getKey()));
		}
		Map<RegistryEntry<EntityAttribute>, Double> added = new HashMap<>();
		for (Map.Entry<Key, EntityAttributeModifier> entry : after.entrySet()) {
			if (!own.containsKey(entry.getKey())) {
				failures.check(isGraftModifier(entry.getKey().id()), cycle, () -> what + " gained a modifier that is not a graft's: " + entry.getValue());
				added.merge(entry.getKey().attribute(), entry.getValue().value(), Double::sum);
			}
		}
		List<Grafting.Weighted> weighted = Grafting.weighted(Grafting.grafts(grafted));
		boolean tool = grafted.isIn(Grafting.TOOLS);
		checkSum(failures, cycle, what, added, EntityAttributes.PLAYER_MINING_EFFICIENCY, tool ? sum(weighted, graft -> graft.graft().element() == Element.TOXIC
				? (1 + 3 * (double) graft.power()) * graft.weight() : 0) : 0);
		checkSum(failures, cycle, what, added, EntityAttributes.PLAYER_SUBMERGED_MINING_SPEED, tool ? sum(weighted, graft -> graft.graft().kind() == SoulKind.DROWNED
				? (0.4 + 0.4 * graft.power()) * graft.weight() : 0) : 0);
		checkSum(failures, cycle, what, added, EntityAttributes.PLAYER_BLOCK_BREAK_SPEED, tool ? sum(weighted, graft -> graft.graft().kind() == SoulKind.BABY_ZOMBIE
				? (0.05 + 0.15 * graft.power()) * graft.weight() : 0) : 0);
		checkSum(failures, cycle, what, added, EntityAttributes.PLAYER_BLOCK_INTERACTION_RANGE, tool ? sum(weighted, graft -> graft.graft().kind() == SoulKind.SKELETON
				? (0.5 + graft.power()) * graft.weight() : 0) : 0);
		if (tool && !grafted.isIn(Grafting.WEAPONS)) {
			failures.check(!added.containsKey(EntityAttributes.GENERIC_ATTACK_SPEED) && !added.containsKey(EntityAttributes.GENERIC_ATTACK_DAMAGE), cycle,
					() -> what + " (mining gear, not a weapon) gained a weapon bonus: " + added);
		}
		// equipped: every modifier applies (a repeated id would throw), then comes off again
		for (Map.Entry<Key, EntityAttributeModifier> entry : after.entrySet()) {
			EntityAttributeInstance instance = player.getAttributeInstance(entry.getKey().attribute());
			if (instance == null) {
				continue;
			}
			try {
				instance.addTemporaryModifier(entry.getValue());
			} catch (IllegalArgumentException e) {
				failures.add(cycle, what + ": equipping threw " + e.getMessage());
			} finally {
				instance.removeModifier(entry.getValue().id());
			}
		}
		// saved and loaded, sent and received
		var ops = world.getRegistryManager().getOps(NbtOps.INSTANCE);
		NbtElement saved = ItemStack.CODEC.encodeStart(ops, grafted).getOrThrow();
		ItemStack loaded = ItemStack.CODEC.parse(ops, saved).getOrThrow();
		failures.check(ItemStack.areEqual(loaded, grafted), cycle, () -> what + " changed through a save: " + loaded.getComponentChanges());
		RegistryByteBuf buf = new RegistryByteBuf(Unpooled.buffer(), world.getRegistryManager());
		ItemStack.PACKET_CODEC.encode(buf, grafted);
		ItemStack received = ItemStack.PACKET_CODEC.decode(buf);
		failures.check(ItemStack.areEqual(received, grafted), cycle, () -> what + " changed on the wire: " + received.getComponentChanges());
		// scrubbed one by one, in a random order, back to exactly what it was
		ItemStack scrubbed = grafted;
		SplittableRandom order = new SplittableRandom(cycle);
		while (!Grafting.grafts(scrubbed).isEmpty()) {
			scrubbed = Grafting.scrub(scrubbed, order.nextInt(Grafting.grafts(scrubbed).size()));
		}
		ItemStack clean = scrubbed;
		failures.check(ItemStack.areEqual(clean, before), cycle, () -> what + " scrubbed clean is not what it was: " + clean.getComponentChanges()
				+ " against " + before.getComponentChanges());
		failures.check(!clean.contains(SoulComponents.GRAFTS), cycle, () -> what + " kept an empty grafts component");
	}

	private static double sum(List<Grafting.Weighted> grafts, ToDoubleFunction<Grafting.Weighted> value) {
		double total = 0;
		for (Grafting.Weighted graft : grafts) {
			total += value.applyAsDouble(graft);
		}
		return total;
	}

	private static void checkSum(ToolFuzz.Failures failures, int cycle, String what, Map<RegistryEntry<EntityAttribute>, Double> added,
			RegistryEntry<EntityAttribute> attribute, double expected) {
		double actual = added.getOrDefault(attribute, 0.0);
		failures.check(Math.abs(actual - expected) < 1.0E-6, cycle, () -> what + ": " + attribute.getIdAsString() + " +" + actual + ", documented +" + expected);
	}

	// ---------------------------------------------------------------------------------------------------------------

	private enum Chance {
		SMELT(null, Element.IGNITED, 0.15, 0.35), PROSPECT(SoulKind.ZOMBIE_VILLAGER, null, 0.1, 0.2), BOUNTY(SoulKind.BOGGED, null, 0.1, 0.25),
		SILK(SoulKind.SPIDER, null, 0.08, 0.17), BLAST(SoulKind.CREEPER, null, 0.08, 0.12);

		final SoulKind kind;
		final Element element;
		final double base;
		final double scale;

		Chance(SoulKind kind, Element element, double base, double scale) {
			this.kind = kind;
			this.element = element;
			this.base = base;
			this.scale = scale;
		}

		boolean matches(Graft graft) {
			return graft.kind() == this.kind || graft.element() == this.element;
		}

		/** The documented chance: base + scale x power for each matching graft, weighted, at most 75%. */
		double of(List<Grafting.Weighted> grafts) {
			double total = 0;
			for (Grafting.Weighted graft : grafts) {
				if (this.matches(graft.graft())) {
					total += (this.base + this.scale * graft.power()) * graft.weight();
				}
			}
			return Math.min(0.75, total);
		}
	}

	/**
	 * Each chance measured through vanilla's own drop roll (Blast Mining through a survival player's real break), for
	 * random grades, rolls and overlapping grafts, one of them always the chance's own: every rate lands within five
	 * standard deviations of the documented number, and Smelting Touch's experience averages the furnace's 0.7 per iron.
	 */
	@GameTest(templateName = EMPTY_STRUCTURE, batchId = BATCH, tickLimit = 400)
	public void chancesMatchTheirDocumentedNumbers(TestContext context) {
		ServerWorld world = context.getWorld();
		BlockPos at = context.getAbsolutePos(new BlockPos(3, 2, 3));
		Mutants.floor(context);
		Box area = new Box(at).expand(3.0);
		ServerPlayerEntity player = TestPlayers.survival(context, new BlockPos(0, 2, 0), 0);
		ToolFuzz.Failures failures = new ToolFuzz.Failures("chance measurement");
		int cycle = 0;
		try {
			for (Chance chance : Chance.values()) {
				for (int config = 0; config < 4; config++, cycle++) {
					SplittableRandom random = ToolFuzz.random("chances", cycle);
					List<Graft> grafts = new ArrayList<>();
					do {
						grafts.clear();
						for (Graft graft : ToolFuzz.grafts(random, Grafting.MAX_GRAFTS)) {
							// keep out what would blur this chance's count: Silk Thread on ore and stone, smelting on stone
							boolean blurs = chance == Chance.PROSPECT && graft.kind() == SoulKind.SPIDER
									|| chance == Chance.SILK && graft.element() == Element.IGNITED;
							if (!blurs) {
								grafts.add(graft);
							}
						}
						if (grafts.isEmpty() || grafts.stream().noneMatch(chance::matches)) {
							Graft own = ToolFuzz.graft(random);
							grafts.add(0, new Graft(chance.kind != null ? chance.kind : own.kind(),
									chance.element != null ? chance.element : chance == Chance.SILK && own.element() == Element.IGNITED ? Element.TOXIC : own.element(),
									own.grade(), own.power()));
						}
					} while (grafts.size() > Grafting.MAX_GRAFTS);
					ItemStack tool = ToolFuzz.grafted(new ItemStack(chance == Chance.BOUNTY ? Items.IRON_HOE : Items.DIAMOND_PICKAXE), grafts);
					double expected = chance.of(Grafting.weighted(Grafting.grafts(tool)));
					failures.ran();
					try {
						measure(context, world, at, area, player, chance, tool, expected, failures, cycle);
					} catch (RuntimeException e) {
						failures.add(cycle, chance + " " + ToolFuzz.describe(tool) + " threw " + e);
					}
				}
			}
		} finally {
			world.setBlockState(at, Blocks.AIR.getDefaultState(), PLACE);
			TestPlayers.remove(context, player);
		}
		failures.throwIfAny();
		context.complete();
	}

	private static void measure(TestContext context, ServerWorld world, BlockPos at, Box area, ServerPlayerEntity player, Chance chance, ItemStack tool,
			double expected, ToolFuzz.Failures failures, int cycle) {
		int trials = chance == Chance.BLAST ? 700 : 1500;
		int hits = 0;
		int smelted = 0;
		int experience = 0;
		BlockState state = switch (chance) {
			case SMELT -> Blocks.IRON_ORE.getDefaultState();
			case PROSPECT, BLAST -> Blocks.COAL_ORE.getDefaultState();
			case BOUNTY -> ((CropBlock) Blocks.WHEAT).withAge(CropBlock.MAX_AGE);
			case SILK -> Blocks.STONE.getDefaultState();
		};
		BlockPos vein = at.east();
		player.setStackInHand(Hand.MAIN_HAND, tool);
		for (int trial = 0; trial < trials; trial++) {
			world.getEntitiesByClass(ItemEntity.class, area, entity -> true).forEach(ItemEntity::discard);
			world.getEntitiesByClass(ExperienceOrbEntity.class, area, entity -> true).forEach(ExperienceOrbEntity::discard);
			world.setBlockState(at, state, PLACE);
			if (chance == Chance.BLAST) {
				world.setBlockState(vein, state, PLACE);
				tool.setDamage(0);
				player.interactionManager.tryBreakBlock(at);
				hits += world.getBlockState(vein).isAir() ? 1 : 0;
				world.setBlockState(vein, Blocks.AIR.getDefaultState(), PLACE);
				continue;
			}
			List<ItemStack> drops = Block.getDroppedStacks(state, world, at, null, player, tool);
			switch (chance) {
				case SMELT -> {
					int ingots = drops.stream().filter(stack -> stack.isOf(Items.IRON_INGOT)).mapToInt(ItemStack::getCount).sum();
					hits += ingots > 0 ? 1 : 0;
					if (ingots > 0) {
						// a Prospector graft can make it two: the furnace gives 0.7 for each
						smelted += ingots;
						for (ExperienceOrbEntity orb : world.getEntitiesByClass(ExperienceOrbEntity.class, area, entity -> true)) {
							NbtCompound nbt = orb.writeNbt(new NbtCompound());
							experience += orb.getExperienceAmount() * Math.max(1, nbt.getInt("Count"));
						}
					}
				}
				case PROSPECT -> hits += drops.stream().filter(stack -> stack.isOf(Items.COAL)).mapToInt(ItemStack::getCount).sum() == 2 ? 1 : 0;
				case BOUNTY -> hits += !drops.isEmpty() && drops.get(0).isOf(Items.WHEAT) && drops.get(0).getCount() == 2 ? 1 : 0;
				case SILK -> hits += drops.stream().anyMatch(stack -> stack.isOf(Items.STONE)) ? 1 : 0;
				default -> {
				}
			}
		}
		world.setBlockState(at, Blocks.AIR.getDefaultState(), PLACE);
		double rate = hits / (double) trials;
		double tolerance = 5 * Math.sqrt(expected * (1 - expected) / trials) + 0.003;
		int count = hits;
		failures.check(Math.abs(rate - expected) <= tolerance, cycle, () -> chance + " with " + ToolFuzz.describe(tool) + ": " + count + " in " + trials
				+ String.format(" = %.3f, documented %.3f (allowed +-%.3f)", rate, expected, tolerance));
		if (chance == Chance.SMELT && smelted > 50) {
			double mean = experience / (double) smelted;
			double allowed = 5 * Math.sqrt(0.21 / smelted) + 0.01;
			int ingots = smelted;
			int total = experience;
			failures.check(Math.abs(mean - 0.7) <= allowed, cycle, () -> "Smelting Touch gave " + total + " experience for " + ingots
					+ String.format(" ingots = %.3f each, the furnace gives 0.7 (allowed +-%.3f)", mean, allowed));
		}
	}

	// ---------------------------------------------------------------------------------------------------------------

	/**
	 * Every tool through the Soul Forge (shift-clicked in; three grafts, each in its grade's range; a fourth refused;
	 * three rag scrubs back to the plain tool), every kind and element through {@code /evoluta graft} on a held tool, and champions'
	 * shovels grafted with their own kind, element and tier, while an Evolved zombie's stays plain.
	 */
	@GameTest(templateName = EMPTY_STRUCTURE, batchId = BATCH, tickLimit = 200)
	public void everyToolTakesGraftsFromTheForgeTheCommandAndMutants(TestContext context) {
		ServerWorld world = context.getWorld();
		ToolFuzz.Failures failures = new ToolFuzz.Failures("forge, command and mutant gear");
		SplittableRandom random = ToolFuzz.random("paths", 0);
		PlayerEntity smith = context.createMockPlayer(GameMode.SURVIVAL);
		int cycle = 0;
		for (Item item : ToolFuzz.items(Grafting.TOOLS)) {
			SoulForgeScreenHandler forge = new SoulForgeScreenHandler(1, smith.getInventory(),
					ScreenHandlerContext.create(world, context.getAbsolutePos(new BlockPos(1, 2, 1))));
			failures.ran();
			String name = ToolFuzz.describe(new ItemStack(item));
			int c = cycle++;
			failures.check(forge.getSlot(SoulForgeScreenHandler.GEAR).canInsert(new ItemStack(item)), c, () -> "the forge's gear slot refused " + name);
			// shift-clicked in from the first inventory slot, as a player puts it there
			smith.getInventory().setStack(9, new ItemStack(item));
			forge.quickMove(smith, 3);
			failures.check(forge.gear().isOf(item) && smith.getInventory().getStack(9).isEmpty(), c, () -> "shift-clicking " + name
					+ " left the gear slot holding " + ToolFuzz.describe(forge.gear()));
			forge.getSlot(SoulForgeScreenHandler.GEAR).setStack(new ItemStack(item));
			for (int i = 0; i < Grafting.MAX_GRAFTS; i++) {
				Graft wanted = ToolFuzz.graft(random);
				ItemStack meat = new ItemStack(EvolutaItems.soulMeat(wanted.element(), wanted.kind()));
				meat.set(SoulComponents.SOUL_GRADE, wanted.grade());
				forge.getSlot(SoulForgeScreenHandler.MEAT).setStack(meat);
				int n = i;
				failures.check(forge.onButtonClick(smith, SoulForgeScreenHandler.GRAFT), c, () -> "the forge refused graft " + (n + 1) + " on " + name);
				List<Graft> grafts = Grafting.grafts(forge.gear());
				Graft got = grafts.isEmpty() ? null : grafts.get(grafts.size() - 1);
				failures.check(grafts.size() == i + 1 && got.kind() == wanted.kind() && got.element() == wanted.element() && got.grade() == wanted.grade()
						&& got.power() >= Graft.minPower(wanted.grade()) && got.power() <= Graft.maxPower(wanted.grade()), c,
						() -> "the forge put " + grafts + " on " + name + " for " + wanted);
			}
			ItemStack spare = new ItemStack(EvolutaItems.soulMeat(Element.TOXIC, SoulKind.ZOMBIE));
			forge.getSlot(SoulForgeScreenHandler.MEAT).setStack(spare);
			failures.check(!forge.onButtonClick(smith, SoulForgeScreenHandler.GRAFT), c, () -> "the forge put a fourth graft on " + name);
			forge.getSlot(SoulForgeScreenHandler.RAG).setStack(new ItemStack(EvolutaItems.DIAMOND_SILK_RAG));
			for (int i = 0; i < Grafting.MAX_GRAFTS; i++) {
				forge.onButtonClick(smith, SoulForgeScreenHandler.SCRUB);
			}
			ItemStack scrubbed = forge.gear();
			failures.check(ItemStack.areEqual(scrubbed, new ItemStack(item)), c, () -> "three scrubs left " + name + " as " + scrubbed.getComponentChanges());
		}
		for (Item other : new Item[]{Items.SHEARS, Items.STICK, Items.FISHING_ROD, Items.FLINT_AND_STEEL, Items.BRUSH}) {
			SoulForgeScreenHandler forge = new SoulForgeScreenHandler(1, smith.getInventory(),
					ScreenHandlerContext.create(world, context.getAbsolutePos(new BlockPos(1, 2, 1))));
			failures.ran();
			failures.check(!forge.getSlot(SoulForgeScreenHandler.GEAR).canInsert(new ItemStack(other)), cycle++, () -> "the forge took " + other);
		}

		ServerPlayerEntity operator = TestPlayers.survival(context, new BlockPos(2, 2, 2), 0);
		List<Item> tools = ToolFuzz.items(Grafting.TOOLS);
		try {
			for (SoulKind kind : SoulKind.values()) {
				for (Element element : ToolFuzz.ELEMENTS) {
					Tier grade = ToolFuzz.pick(random, Tier.values());
					float power = Graft.minPower(grade) + (float) random.nextDouble() * (Graft.maxPower(grade) - Graft.minPower(grade));
					ItemStack held = new ItemStack(ToolFuzz.pick(random, tools));
					operator.setStackInHand(Hand.MAIN_HAND, held.copy());
					String command = "evoluta graft @s " + kind.key() + " " + element.key() + " " + grade.key() + " " + power;
					world.getServer().getCommandManager().executeWithPrefix(operator.getCommandSource().withLevel(4), command);
					ItemStack expected = Grafting.graft(held, EvolutaItems.soulMeat(element, kind), grade, power);
					ItemStack got = operator.getMainHandStack();
					failures.ran();
					failures.check(ItemStack.areEqual(got, expected), cycle++, () -> "/" + command + " on " + ToolFuzz.describe(held) + " gave "
							+ ToolFuzz.describe(got) + ", expected " + ToolFuzz.describe(expected));
				}
			}
		} finally {
			TestPlayers.remove(context, operator);
		}

		for (Tier tier : Tier.values()) {
			for (Element element : new Element[]{Element.NONE, Element.IGNITED, Element.PERMAFROST, Element.TOXIC}) {
				ZombieEntity zombie = Mutants.still(context, EntityType.ZOMBIE, 5, 5, tier, element, Archetype.NONE);
				zombie.equipStack(EquipmentSlot.MAINHAND, new ItemStack(Items.IRON_SHOVEL));
				zombie.removeAttached(EvolutaAttachments.GEAR_GRAFTED);
				MutantGear.graftOnce(zombie, Mutations.get(zombie), Random.create(cycle));
				List<Graft> grafts = Grafting.grafts(zombie.getMainHandStack());
				boolean champion = tier != Tier.EVOLVED;
				boolean expectGraft = champion && element != Element.NONE;
				failures.ran();
				failures.check(expectGraft ? grafts.size() == 1 && grafts.get(0).kind() == SoulKind.ZOMBIE && grafts.get(0).element() == element
						&& grafts.get(0).grade() == tier : grafts.isEmpty(), cycle++, () -> "a " + tier.key() + " " + element.key() + " zombie's shovel got " + grafts);
				zombie.discard();
			}
		}
		failures.throwIfAny();
		context.complete();
	}
}
