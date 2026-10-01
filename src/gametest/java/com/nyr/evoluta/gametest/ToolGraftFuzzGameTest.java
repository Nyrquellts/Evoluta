package com.nyr.evoluta.gametest;

import com.nyr.evoluta.common.mutation.Element;
import com.nyr.evoluta.common.mutation.EvolutaAttachments;
import com.nyr.evoluta.common.soul.Graft;
import com.nyr.evoluta.common.soul.Grafting;
import com.nyr.evoluta.common.soul.SoulKind;
import com.nyr.evoluta.common.soul.ToolGrafts;
import it.unimi.dsi.fastutil.longs.LongOpenHashSet;
import it.unimi.dsi.fastutil.longs.LongSet;
import java.util.ArrayDeque;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.Optional;
import java.util.Set;
import java.util.SplittableRandom;
import net.fabricmc.fabric.api.event.player.PlayerBlockBreakEvents;
import net.fabricmc.fabric.api.gametest.v1.FabricGameTest;
import net.fabricmc.fabric.api.tag.convention.v2.ConventionalBlockTags;
import net.minecraft.block.Block;
import net.minecraft.block.BlockState;
import net.minecraft.block.Blocks;
import net.minecraft.block.CocoaBlock;
import net.minecraft.block.CropBlock;
import net.minecraft.block.FluidBlock;
import net.minecraft.block.FrostedIceBlock;
import net.minecraft.block.IceBlock;
import net.minecraft.block.InfestedBlock;
import net.minecraft.block.NetherWartBlock;
import net.minecraft.block.entity.BeehiveBlockEntity;
import net.minecraft.block.entity.BlockEntity;
import net.minecraft.component.DataComponentTypes;
import net.minecraft.enchantment.Enchantments;
import net.minecraft.entity.Entity;
import net.minecraft.entity.ExperienceOrbEntity;
import net.minecraft.entity.ItemEntity;
import net.minecraft.entity.effect.StatusEffectInstance;
import net.minecraft.entity.effect.StatusEffects;
import net.minecraft.entity.passive.BeeEntity;
import net.minecraft.entity.player.PlayerEntity;
import net.minecraft.item.Item;
import net.minecraft.item.ItemStack;
import net.minecraft.item.Items;
import net.minecraft.nbt.NbtCompound;
import net.minecraft.network.packet.Packet;
import net.minecraft.network.packet.s2c.play.ParticleS2CPacket;
import net.minecraft.network.packet.s2c.play.PlaySoundS2CPacket;
import net.minecraft.network.packet.s2c.play.WorldEventS2CPacket;
import net.minecraft.particle.ParticleTypes;
import net.minecraft.recipe.RecipeEntry;
import net.minecraft.recipe.RecipeType;
import net.minecraft.recipe.SmeltingRecipe;
import net.minecraft.recipe.input.SingleStackRecipeInput;
import net.minecraft.registry.Registries;
import net.minecraft.registry.entry.RegistryEntry;
import net.minecraft.registry.tag.BlockTags;
import net.minecraft.server.network.ServerPlayerEntity;
import net.minecraft.server.world.ServerWorld;
import net.minecraft.sound.SoundEvents;
import net.minecraft.state.property.Properties;
import net.minecraft.test.GameTest;
import net.minecraft.test.TestContext;
import net.minecraft.util.Hand;
import net.minecraft.util.math.BlockPos;
import net.minecraft.util.math.Box;
import net.minecraft.util.math.Direction;
import net.minecraft.util.math.MathHelper;
import net.minecraft.util.math.random.RandomSeed;
import net.minecraft.world.GameMode;
import net.minecraft.world.WorldEvents;
import org.jetbrains.annotations.Nullable;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

/**
 * Fuzz of the mining grafts against the rules they are meant to keep, not against their own code. Each test states
 * its rules; every case derives from {@link ToolFuzz#SEED} and its number, so any failure replays. The break fuzz
 * compares every block around a grafted break with the same break by the same tool without its grafts: whatever
 * differs must be one of the grafts' documented effects.
 */
public final class ToolGraftFuzzGameTest implements FabricGameTest {
	private static final Logger LOGGER = LoggerFactory.getLogger("Evoluta tool fuzz");
	private static final String BATCH = "evoluta_tool_fuzz";
	private static final int PLACE = Block.NOTIFY_LISTENERS | Block.FORCE_STATE;
	/** Blocks a claim protects, as a claim mod would through Fabric's break event. Empty outside a break. */
	private static final LongSet CLAIMED = new LongOpenHashSet();
	/**
	 * Blocks Fabric's after-break event fired for, the event the grafts act on. It does not fire when the block is gone
	 * before vanilla removes it (the top of a door mined with the wrong tool takes the bottom and so itself with it).
	 */
	private static final LongSet REMOVED = new LongOpenHashSet();

	static {
		PlayerBlockBreakEvents.BEFORE.register((world, player, pos, state, blockEntity) -> !CLAIMED.contains(pos.asLong()));
		PlayerBlockBreakEvents.AFTER.register((world, player, pos, state, blockEntity) -> REMOVED.add(pos.asLong()));
	}

	// ---------------------------------------------------------------------------------------------------------------
	// Drops

	/**
	 * Every block of the game in a random state, broken by random grafted gear (a tenth of it weapons). With every
	 * chance forced to fire the drops must be exactly what the rules say, experience included; with none firing,
	 * exactly vanilla's; rolled for real through vanilla's drop roll, whole stacks only. And whatever the rules say, no
	 * block may yield more of itself than vanilla does with or without Silk Touch.
	 */
	@GameTest(templateName = EMPTY_STRUCTURE, batchId = BATCH, tickLimit = 400)
	public void dropsFollowTheRulesOnEveryBlock(TestContext context) {
		ServerWorld world = context.getWorld();
		BlockPos at = context.getAbsolutePos(new BlockPos(4, 3, 4));
		world.setBlockState(at.down(), Blocks.STONE.getDefaultState());
		Box area = new Box(at).expand(2.0);
		ServerPlayerEntity player = TestPlayers.survival(context, new BlockPos(1, 2, 1), 0);
		List<Block> blocks = Registries.BLOCK.stream().toList();
		List<Item> tools = ToolFuzz.items(Grafting.TOOLS);
		List<Item> weapons = ToolFuzz.items(Grafting.WEAPONS).stream().filter(item -> !new ItemStack(item).isIn(Grafting.TOOLS)).toList();
		RegistryEntry<net.minecraft.enchantment.Enchantment> silkTouch = ToolFuzz.enchantment(world, Enchantments.SILK_TOUCH);
		ToolFuzz.Failures failures = new ToolFuzz.Failures("drops fuzz");
		Timings timings = new Timings();
		int cycles = Math.max(ToolFuzz.CYCLES, blocks.size());
		try {
			for (int cycle = 0; cycle < cycles; cycle++) {
				SplittableRandom random = ToolFuzz.random("drops", cycle);
				Block block = cycle < blocks.size() ? blocks.get(cycle) : ToolFuzz.pick(random, blocks);
				BlockState chosen = random.nextInt(3) == 0 ? block.getDefaultState() : ToolFuzz.pick(random, block.getStateManager().getStates());
				Item item = random.nextInt(10) == 0 ? ToolFuzz.pick(random, weapons) : ToolFuzz.pick(random, tools);
				ItemStack plain = ToolFuzz.tool(random, world, item);
				ItemStack tool = ToolFuzz.grafted(plain, ToolFuzz.grafts(random, Grafting.MAX_GRAFTS));
				long seed = random.nextLong();
				failures.ran();
				String what = ToolFuzz.describe(chosen) + " by " + ToolFuzz.describe(tool);
				try {
					world.setBlockState(at, chosen, PLACE);
					BlockState state = world.getBlockState(at);
					checkDrops(world, at, state, world.getBlockEntity(at), player, plain, tool, silkTouch, seed, area, failures, cycle,
							ToolFuzz.describe(state) + " by " + ToolFuzz.describe(tool), timings);
				} catch (RuntimeException e) {
					failures.add(cycle, what + " threw " + e);
				} finally {
					world.setBlockState(at, Blocks.AIR.getDefaultState(), PLACE);
					discardAround(world, area);
				}
			}
		} finally {
			world.getRandom().setSeed(RandomSeed.getSeed());
			TestPlayers.remove(context, player);
		}
		timings.log("drop rolls, vanilla's roll with the plain tool against the same roll through the grafts' hook");
		failures.throwIfAny();
		context.complete();
	}

	/** How long vanilla's work took against the same work with grafts, per case, for the log. */
	private static final class Timings {
		private final List<Long> vanilla = new ArrayList<>();
		private final List<Long> grafted = new ArrayList<>();

		void vanilla(long nanos) {
			this.vanilla.add(nanos);
		}

		void grafted(long nanos) {
			this.grafted.add(nanos);
		}

		void log(String what) {
			LOGGER.info("{} {}: ungrafted {}, grafted {}", this.grafted.size(), what, summary(this.vanilla), summary(this.grafted));
		}

		private static String summary(List<Long> nanos) {
			if (nanos.isEmpty()) {
				return "none";
			}
			List<Long> sorted = new ArrayList<>(nanos);
			sorted.sort(null);
			double mean = sorted.stream().mapToLong(Long::longValue).average().orElse(0) / 1000.0;
			return String.format(java.util.Locale.ROOT, "median %.1f us, mean %.1f, p99 %.1f", sorted.get(sorted.size() / 2) / 1000.0, mean,
					sorted.get(Math.min(sorted.size() - 1, (int) (sorted.size() * 0.99))) / 1000.0);
		}
	}

	private static void checkDrops(ServerWorld world, BlockPos at, BlockState state, @Nullable BlockEntity blockEntity, ServerPlayerEntity player,
			ItemStack plain, ItemStack tool, RegistryEntry<net.minecraft.enchantment.Enchantment> silkTouch, long seed, Box area,
			ToolFuzz.Failures failures, int cycle, String what, Timings timings) {
		List<ItemStack> vanilla = Block.getDroppedStacks(state, world, at, blockEntity, player, plain);
		ItemStack silkTool = plain.copy();
		silkTool.addEnchantment(silkTouch, 1);
		rewind(world, state, seed);
		List<ItemStack> silk = Block.getDroppedStacks(state, world, at, blockEntity, player, silkTool);
		Rules rules = new Rules(state, blockEntity, tool);

		List<ItemStack> none = ToolGrafts.modifyDrops(state, world, at, blockEntity, player, tool, ToolFuzz.copies(vanilla),
				ToolFuzz.forced(ToolFuzz.NEVER, seed));
		List<ItemStack> expectNone = !rules.tool ? vanilla : rules.iceCutter ? List.of(new ItemStack(state.getBlock())) : nonEmpty(vanilla);
		failures.check(ToolFuzz.sameStacks(none, expectNone), cycle, () -> what + ", no chance firing: dropped " + ToolFuzz.describe(none)
				+ ", the rules say " + ToolFuzz.describe(expectNone));

		discardOrbs(world, area);
		rewind(world, state, seed);
		List<ItemStack> all = ToolGrafts.modifyDrops(state, world, at, blockEntity, player, tool, ToolFuzz.copies(vanilla),
				ToolFuzz.forced(ToolFuzz.ALWAYS, seed));
		int experience = orbExperience(world, area);
		Expected expected = rules.allFiring(world, state, vanilla, silk);
		failures.check(ToolFuzz.sameStacks(all, expected.drops()), cycle, () -> what + ", every chance firing: dropped " + ToolFuzz.describe(all)
				+ ", the rules say " + ToolFuzz.describe(expected.drops()) + " (vanilla " + ToolFuzz.describe(vanilla) + ", Silk Touch "
				+ ToolFuzz.describe(silk) + ")");
		failures.check(experience == expected.experience(), cycle, () -> what + ", every chance firing: " + experience
				+ " experience, the furnace gives " + expected.experience());
		Item own = state.getBlock().asItem();
		int bound = Math.max(count(vanilla, own), count(silk, own)) + (rules.ripe ? 1 : 0);
		failures.check(own == Items.AIR || count(all, own) <= bound, cycle, () -> what + ": " + count(all, own) + " of itself from one block, "
				+ "vanilla gives at most " + bound + " (vanilla " + ToolFuzz.describe(vanilla) + ", Silk Touch " + ToolFuzz.describe(silk) + ")");

		// timed back to back with the same roll by the plain tool, taking turns at going first so neither is the warmer
		List<ItemStack> real = null;
		for (int turn = 0; turn < 2; turn++) {
			boolean grafted = (turn == 0) == (cycle % 2 == 0);
			long start = System.nanoTime();
			List<ItemStack> rolled = Block.getDroppedStacks(state, world, at, blockEntity, player, grafted ? tool : plain);
			long nanos = System.nanoTime() - start;
			if (grafted) {
				timings.grafted(nanos);
				real = rolled;
			} else {
				timings.vanilla(nanos);
			}
		}
		for (ItemStack stack : real) {
			// an empty stack is vanilla's (a count rolled as 0) and drops nothing; an overfull one would not
			failures.check(stack.getCount() <= stack.getMaxCount(), cycle, () -> what + ": an overfull stack " + stack);
		}
	}

	/** Puts the block's drop roll back to where it was, so two rolls of one table come out the same. */
	private static void rewind(ServerWorld world, BlockState state, long seed) {
		world.getRandomSequences().reset(state.getBlock().getLootTableKey().getValue());
		world.getRandom().setSeed(seed);
	}

	private record Expected(List<ItemStack> drops, int experience) {
	}

	/** What the rules say a grafted piece of gear does to one block's drops, written from the documented rules. */
	private static final class Rules {
		final boolean tool;
		final List<Grafting.Weighted> grafts;
		final boolean iceCutter;
		final boolean silkEligible;
		final boolean ore;
		final boolean ripe;

		Rules(BlockState state, @Nullable BlockEntity blockEntity, ItemStack stack) {
			this.tool = stack.isIn(Grafting.TOOLS);
			this.grafts = Grafting.weighted(Grafting.grafts(stack));
			this.iceCutter = this.tool && state.isIn(BlockTags.ICE) && !state.isOf(Blocks.FROSTED_ICE) && this.has(SoulKind.STRAY);
			// Silk Thread never where Silk Touch does more than change the drops: a block entity (a beehive's bees would
			// come out and stay in the hive), melting ice (its water and the ice), an infested block (silverfish and stone)
			this.silkEligible = blockEntity == null && !state.hasBlockEntity() && !(state.getBlock() instanceof IceBlock)
					&& !(state.getBlock() instanceof InfestedBlock);
			this.ore = state.isIn(ConventionalBlockTags.ORES);
			this.ripe = ripe(state);
		}

		boolean has(SoulKind kind) {
			return this.grafts.stream().anyMatch(graft -> graft.graft().kind() == kind);
		}

		boolean has(Element element) {
			return this.grafts.stream().anyMatch(graft -> graft.graft().element() == element);
		}

		Expected allFiring(ServerWorld world, BlockState state, List<ItemStack> vanilla, List<ItemStack> silk) {
			if (!this.tool) {
				return new Expected(ToolFuzz.copies(vanilla), 0);
			}
			List<ItemStack> drops;
			boolean whole;
			if (this.iceCutter) {
				drops = new ArrayList<>(List.of(new ItemStack(state.getBlock())));
				whole = true;
			} else if (this.has(SoulKind.SPIDER) && this.silkEligible) {
				drops = ToolFuzz.copies(nonEmpty(silk));
				whole = true;
			} else {
				drops = ToolFuzz.copies(nonEmpty(vanilla));
				whole = false;
			}
			Item own = state.getBlock().asItem();
			if (!drops.isEmpty()) {
				if (this.ore) {
					// never one more of an ore that drops itself: placed and mined again, it would multiply
					if (!whole && this.has(SoulKind.ZOMBIE_VILLAGER) && !drops.get(0).isOf(own)) {
						drops.get(0).increment(1);
					}
				} else if (this.ripe && this.has(SoulKind.BOGGED)) {
					drops.get(0).increment(1);
				}
			}
			int experience = 0;
			if (this.has(Element.IGNITED)) {
				List<ItemStack> smelted = new ArrayList<>();
				float xp = 0;
				for (ItemStack stack : drops) {
					Optional<RecipeEntry<SmeltingRecipe>> recipe = world.getRecipeManager().getFirstMatch(RecipeType.SMELTING,
							new SingleStackRecipeInput(stack.copyWithCount(1)), world);
					ItemStack result = recipe.map(entry -> entry.value().getResult(world.getRegistryManager())).orElse(ItemStack.EMPTY);
					if (result.isEmpty()) {
						smelted.add(stack);
						continue;
					}
					for (int left = result.getCount() * stack.getCount(); left > 0; left -= result.getMaxCount()) {
						smelted.add(result.copyWithCount(Math.min(left, result.getMaxCount())));
					}
					xp += recipe.get().value().getExperience() * stack.getCount();
				}
				drops = smelted;
				experience = xp > 0 ? MathHelper.ceil(xp) : 0;
			}
			return new Expected(drops, experience);
		}
	}

	private static List<ItemStack> nonEmpty(List<ItemStack> stacks) {
		return stacks.stream().filter(stack -> !stack.isEmpty()).toList();
	}

	static boolean ripe(BlockState state) {
		if (state.getBlock() instanceof CropBlock crop) {
			return crop.getAge(state) >= crop.getMaxAge();
		}
		if (state.isOf(Blocks.NETHER_WART)) {
			return state.get(NetherWartBlock.AGE) >= 3;
		}
		if (state.isOf(Blocks.COCOA)) {
			return state.get(CocoaBlock.AGE) >= 2;
		}
		return false;
	}

	private static int count(List<ItemStack> stacks, Item item) {
		int count = 0;
		for (ItemStack stack : stacks) {
			if (stack.isOf(item)) {
				count += stack.getCount();
			}
		}
		return count;
	}

	private static void discardOrbs(ServerWorld world, Box area) {
		world.getEntitiesByClass(ExperienceOrbEntity.class, area, orb -> true).forEach(Entity::discard);
	}

	private static int orbExperience(ServerWorld world, Box area) {
		int total = 0;
		for (ExperienceOrbEntity orb : world.getEntitiesByClass(ExperienceOrbEntity.class, area, orb -> true)) {
			NbtCompound nbt = orb.writeNbt(new NbtCompound());
			total += orb.getExperienceAmount() * Math.max(1, nbt.getInt("Count"));
		}
		return total;
	}

	private static void discardAround(ServerWorld world, Box area) {
		world.getEntitiesByClass(Entity.class, area, entity -> !(entity instanceof PlayerEntity)).forEach(Entity::discard);
	}

	// ---------------------------------------------------------------------------------------------------------------
	// Breaks

	/** The broken block, in the middle of a 5x5x5 scene; the snapshot adds a shell of one block round the scene. */
	private static final BlockPos TARGET = new BlockPos(3, 4, 3);
	private static final int LOW = 1;
	private static final int HIGH = 5;

	/**
	 * A survival (now and then creative or adventure) player breaks the middle of a random scene: ore veins, lava and
	 * water sources and flows, waterlogged blocks, kelp, crops, ice, hives full of bees, stone, anything, with claimed
	 * blocks here and there, holding a random grafted tool. The same break with the same tool minus its grafts is the
	 * baseline. Every block that differs must be a graft's documented effect (Blast Mining's vein, Cold Snap's
	 * neighbours, Ice Cutter's missing water, vanilla's lava meeting water beside one of those); claimed blocks never
	 * change; the tool wears one point per blasted block and never breaks for it; Gnaw feeds one point per ore;
	 * Tunnel Sense chimes for the miner alone exactly when the rules say, pitched by the nearest ore; bees never
	 * multiply.
	 */
	@GameTest(templateName = EMPTY_STRUCTURE, batchId = BATCH, tickLimit = 400)
	public void breaksChangeOnlyWhatTheGraftsSay(TestContext context) {
		ServerWorld world = context.getWorld();
		ServerPlayerEntity miner = TestPlayers.survival(context, new BlockPos(0, 7, 0), 0);
		ServerPlayerEntity bystander = TestPlayers.survival(context, new BlockPos(6, 7, 6), 0);
		Palette palette = new Palette();
		List<Item> tools = ToolFuzz.items(Grafting.TOOLS);
		ToolFuzz.Failures failures = new ToolFuzz.Failures("break fuzz");
		Timings timings = new Timings();
		int cycles = Math.max(200, ToolFuzz.CYCLES / 3);
		try {
			for (int cycle = 0; cycle < cycles; cycle++) {
				SplittableRandom random = ToolFuzz.random("breaks", cycle);
				Scene scene = Scene.random(random, world, palette, tools);
				failures.ran();
				String what = scene.describe();
				try {
					int range = ToolGrafts.tunnelRange(Grafting.weighted(Grafting.grafts(scene.tool())));
					// the two runs take turns at going first, so neither is timed warmer
					Run vanilla;
					Run grafted;
					if (cycle % 2 == 0) {
						vanilla = run(context, scene, scene.plain(), miner, bystander, 0);
						grafted = run(context, scene, scene.tool(), miner, bystander, range);
					} else {
						grafted = run(context, scene, scene.tool(), miner, bystander, range);
						vanilla = run(context, scene, scene.plain(), miner, bystander, 0);
					}
					timings.vanilla(vanilla.nanos());
					timings.grafted(grafted.nanos());
					checkBreak(scene, vanilla, grafted, range, failures, cycle, what);
				} catch (RuntimeException e) {
					failures.add(cycle, what + " threw " + e);
				}
			}
		} finally {
			world.getRandom().setSeed(RandomSeed.getSeed());
			CLAIMED.clear();
			clearScene(context);
			TestPlayers.remove(context, miner);
			TestPlayers.remove(context, bystander);
		}
		timings.log("breaks by a player, the same scene broken with the plain tool and with the grafted one (blasts included)");
		failures.throwIfAny();
		context.complete();
	}

	/** Blocks the break fuzz draws from. */
	private static final class Palette {
		final List<Block> ores = blocks(ConventionalBlockTags.ORES);
		final List<Block> stones = new ArrayList<>();
		final List<Block> all = Registries.BLOCK.stream().toList();
		final Block[] shovel = {Blocks.DIRT, Blocks.GRASS_BLOCK, Blocks.SAND, Blocks.RED_SAND, Blocks.GRAVEL, Blocks.CLAY, Blocks.SNOW_BLOCK,
				Blocks.MUD, Blocks.SOUL_SAND, Blocks.SOUL_SOIL};
		final Block[] ice = {Blocks.ICE, Blocks.PACKED_ICE, Blocks.BLUE_ICE, Blocks.FROSTED_ICE};
		final Block[] special = {Blocks.INFESTED_STONE, Blocks.SCULK, Blocks.SCULK_CATALYST, Blocks.SPAWNER, Blocks.ENDER_CHEST, Blocks.GLASS,
				Blocks.BOOKSHELF, Blocks.GLOWSTONE, Blocks.MELON, Blocks.OAK_LEAVES, Blocks.OAK_LOG, Blocks.SPONGE, Blocks.WET_SPONGE};
		final Block[] crops = {Blocks.WHEAT, Blocks.CARROTS, Blocks.POTATOES, Blocks.BEETROOTS, Blocks.TORCHFLOWER_CROP};

		Palette() {
			this.stones.addAll(blocks(BlockTags.BASE_STONE_OVERWORLD));
			this.stones.addAll(blocks(BlockTags.BASE_STONE_NETHER));
		}

		private static List<Block> blocks(net.minecraft.registry.tag.TagKey<Block> tag) {
			List<Block> blocks = new ArrayList<>();
			for (RegistryEntry<Block> entry : Registries.BLOCK.iterateEntries(tag)) {
				blocks.add(entry.value());
			}
			return blocks;
		}
	}

	/**
	 * One case of the break fuzz, fixed before either run so both see the same world: blocks by position (relative),
	 * bees in a hive at the target, claimed blocks, game mode, the tool with and without its grafts, and how long
	 * Tunnel Sense was already quiet for (null: never felt).
	 */
	private record Scene(Map<BlockPos, BlockState> blocks, int bees, Set<BlockPos> claimed, GameMode mode, ItemStack plain, ItemStack tool,
			@Nullable Integer quietFor, long seed) {
		static Scene random(SplittableRandom random, ServerWorld world, Palette palette, List<Item> tools) {
			Map<BlockPos, BlockState> blocks = new HashMap<>();
			for (BlockPos pos : BlockPos.iterate(LOW, 2, LOW, HIGH, 6, HIGH)) {
				int roll = random.nextInt(100);
				BlockState background = roll < 40 ? Blocks.STONE.getDefaultState() : roll < 50 ? Blocks.DEEPSLATE.getDefaultState()
						: roll < 60 ? Blocks.DIRT.getDefaultState() : Blocks.AIR.getDefaultState();
				blocks.put(pos.toImmutable(), background);
			}
			int bees = 0;
			BlockState target;
			int category = random.nextInt(100);
			if (category < 28) {
				target = ToolFuzz.pick(random, palette.ores).getDefaultState();
			} else if (category < 50) {
				target = ToolFuzz.pick(random, palette.stones).getDefaultState();
			} else if (category < 60) {
				target = ToolFuzz.pick(random, palette.shovel).getDefaultState();
			} else if (category < 68) {
				target = ToolFuzz.pick(random, palette.ice).getDefaultState();
			} else if (category < 76) {
				target = crop(random, palette, blocks);
			} else if (category < 82) {
				target = (random.nextBoolean() ? Blocks.BEEHIVE : Blocks.BEE_NEST).getDefaultState();
				bees = random.nextInt(4);
			} else if (category < 90) {
				target = ToolFuzz.pick(random, palette.special).getDefaultState();
			} else {
				// anything, but a mushroom: whether it stays depends on the light, which vanilla settles lazily
				Block any = ToolFuzz.pick(random, palette.all);
				while (any instanceof net.minecraft.block.MushroomPlantBlock) {
					any = ToolFuzz.pick(random, palette.all);
				}
				target = ToolFuzz.pick(random, any.getStateManager().getStates());
			}
			blocks.put(TARGET, target);
			// round the target: fluids of every kind, waterlogged blocks, kelp, more of the same block (a vein), other ores
			for (Direction direction : Direction.values()) {
				BlockPos pos = TARGET.offset(direction);
				if (blocks.get(pos) != null && isSupport(target, direction)) {
					continue;
				}
				int roll = random.nextInt(100);
				if (roll < 30) {
					continue;
				}
				blocks.put(pos, roll < 43 ? Blocks.LAVA.getDefaultState()
						: roll < 49 ? Blocks.LAVA.getDefaultState().with(FluidBlock.LEVEL, 1 + random.nextInt(7))
						: roll < 62 ? Blocks.WATER.getDefaultState()
						: roll < 68 ? Blocks.WATER.getDefaultState().with(FluidBlock.LEVEL, 1 + random.nextInt(7))
						: roll < 72 ? Blocks.STONE_STAIRS.getDefaultState().with(Properties.WATERLOGGED, true)
						: roll < 75 ? Blocks.STONE_SLAB.getDefaultState().with(Properties.WATERLOGGED, true)
						: roll < 78 ? Blocks.KELP_PLANT.getDefaultState()
						: roll < 90 ? target.getBlock().getDefaultState()
						: ToolFuzz.pick(random, palette.ores).getDefaultState());
			}
			// a longer vein now and then, walking out from the neighbours
			if (target.isIn(ConventionalBlockTags.ORES)) {
				for (int i = random.nextInt(5); i > 0; i--) {
					BlockPos from = TARGET.offset(ToolFuzz.pick(random, Direction.values()));
					BlockPos pos = from.offset(ToolFuzz.pick(random, Direction.values()));
					if (inScene(pos) && !pos.equals(TARGET)) {
						blocks.put(pos, target.getBlock().getDefaultState());
					}
				}
			}
			// ore further off, for Tunnel Sense
			for (int i = random.nextInt(4); i > 0; i--) {
				BlockPos pos = new BlockPos(LOW + random.nextInt(5), 2 + random.nextInt(5), LOW + random.nextInt(5));
				if (!pos.equals(TARGET)) {
					blocks.put(pos, ToolFuzz.pick(random, palette.ores).getDefaultState());
				}
			}
			Set<BlockPos> claimed = new HashSet<>();
			for (BlockPos pos : blocks.keySet()) {
				if (random.nextInt(pos.equals(TARGET) ? 25 : 18) == 0) {
					claimed.add(pos);
				}
			}
			int modeRoll = random.nextInt(10);
			GameMode mode = modeRoll == 0 ? GameMode.CREATIVE : modeRoll == 1 ? GameMode.ADVENTURE : GameMode.SURVIVAL;
			ItemStack plain = ToolFuzz.tool(random, world, ToolFuzz.pick(random, tools));
			if (plain.isDamageable() && random.nextInt(4) == 0) {
				plain.setDamage(plain.getMaxDamage() - 1 - random.nextInt(4));
			}
			List<Graft> grafts = new ArrayList<>(ToolFuzz.grafts(random, Grafting.MAX_GRAFTS));
			if (random.nextBoolean()) {
				// half the cases carry the graft that matters most here, so every effect meets every scene often
				Graft first = grafts.get(0);
				SoulKind kind = target.isIn(ConventionalBlockTags.ORES) ? ToolFuzz.pick(random, new SoulKind[]{SoulKind.CREEPER, SoulKind.ZOMBIE})
						: target.isIn(BlockTags.ICE) ? SoulKind.STRAY
						: target.getBlock() instanceof net.minecraft.block.BeehiveBlock ? SoulKind.SPIDER
						: ToolFuzz.pick(random, new SoulKind[]{SoulKind.CAVE_SPIDER, SoulKind.SPIDER, SoulKind.BOGGED});
				Element element = random.nextBoolean() ? Element.PERMAFROST : first.element();
				grafts.set(0, new Graft(kind, element, first.grade(), first.power()));
			}
			ItemStack tool = ToolFuzz.grafted(plain.copy(), grafts);
			int quietRoll = random.nextInt(4);
			Integer quietFor = quietRoll < 2 ? null : quietRoll == 2 ? -1 : 1 + random.nextInt(20);
			return new Scene(blocks, bees, claimed, mode, plain, tool, quietFor, random.nextLong());
		}

		private static BlockState crop(SplittableRandom random, Palette palette, Map<BlockPos, BlockState> blocks) {
			int kind = random.nextInt(7);
			if (kind == 5) {
				blocks.put(TARGET.down(), Blocks.SOUL_SAND.getDefaultState());
				return Blocks.NETHER_WART.getDefaultState().with(NetherWartBlock.AGE, random.nextInt(4));
			}
			if (kind == 6) {
				Direction facing = ToolFuzz.pick(random, new Direction[]{Direction.NORTH, Direction.SOUTH, Direction.EAST, Direction.WEST});
				blocks.put(TARGET.offset(facing), Blocks.JUNGLE_LOG.getDefaultState());
				return Blocks.COCOA.getDefaultState().with(CocoaBlock.FACING, facing).with(CocoaBlock.AGE, random.nextInt(3));
			}
			blocks.put(TARGET.down(), Blocks.FARMLAND.getDefaultState());
			CropBlock crop = (CropBlock) palette.crops[Math.min(kind, palette.crops.length - 1)];
			return crop.withAge(random.nextInt(crop.getMaxAge() + 1));
		}

		/** Whether the block {@code direction} of the target holds it up (farmland, soul sand, a cocoa's log). */
		private static boolean isSupport(BlockState target, Direction direction) {
			return (target.getBlock() instanceof CropBlock || target.isOf(Blocks.NETHER_WART)) && direction == Direction.DOWN
					|| target.isOf(Blocks.COCOA) && direction == target.get(CocoaBlock.FACING);
		}

		static boolean inScene(BlockPos pos) {
			return pos.getX() >= LOW && pos.getX() <= HIGH && pos.getY() >= 2 && pos.getY() <= 6 && pos.getZ() >= LOW && pos.getZ() <= HIGH;
		}

		String describe() {
			StringBuilder text = new StringBuilder(ToolFuzz.describe(this.blocks.get(TARGET)));
			if (this.bees > 0) {
				text.append(" with ").append(this.bees).append(" bees");
			}
			List<String> around = new ArrayList<>();
			for (Direction direction : Direction.values()) {
				BlockState state = this.blocks.get(TARGET.offset(direction));
				if (state != null && !state.isAir()) {
					around.add(direction.asString() + " " + ToolFuzz.describe(state));
				}
			}
			text.append(" round it ").append(around).append(", ").append(this.mode.asString()).append(" by ").append(ToolFuzz.describe(this.tool));
			if (!this.claimed.isEmpty()) {
				text.append(", claimed ").append(this.claimed);
			}
			if (this.quietFor != null) {
				text.append(", Tunnel Sense quiet for ").append(this.quietFor);
			}
			return text.toString();
		}
	}

	/** What one run of a scene left: the blocks, the tool, the miner's food and Tunnel Sense wait, packets, bees. */
	private record Run(long nanos, boolean broke, boolean removed, BlockPos absoluteTarget, BlockState target, Map<BlockPos, BlockState> after, ItemStack held, int food,
			@Nullable Long quiet, long now, List<Packet<?>> minerPackets, List<Packet<?>> bystanderPackets, int bees, @Nullable Double nearestOre) {
		BlockState at(BlockPos pos) {
			return this.after.get(pos);
		}
	}

	private static Run run(TestContext context, Scene scene, ItemStack held, ServerPlayerEntity miner, ServerPlayerEntity bystander, int tunnelRange) {
		ServerWorld world = context.getWorld();
		clearScene(context);
		// vanilla's randomness while the scene goes up (a copper bulb's update tops the kelp below it at a random age)
		world.getRandom().setSeed(~scene.seed());
		List<Map.Entry<BlockPos, BlockState>> order = new ArrayList<>(scene.blocks().entrySet());
		// supports first, then the rest; the same order in both runs
		order.sort((a, b) -> Long.compare(a.getKey().asLong(), b.getKey().asLong()));
		for (Map.Entry<BlockPos, BlockState> entry : order) {
			world.setBlockState(context.getAbsolutePos(entry.getKey()), entry.getValue(), PLACE);
		}
		BlockPos target = context.getAbsolutePos(TARGET);
		if (scene.bees() > 0 && world.getBlockEntity(target) instanceof BeehiveBlockEntity hive) {
			for (int i = 0; i < scene.bees(); i++) {
				hive.addBee(BeehiveBlockEntity.BeeData.create(0));
			}
		}
		miner.changeGameMode(scene.mode());
		ItemStack stack = held.copy();
		miner.setStackInHand(Hand.MAIN_HAND, stack);
		miner.getHungerManager().setFoodLevel(10);
		long now = world.getTime();
		if (scene.quietFor() == null) {
			miner.removeAttached(EvolutaAttachments.TUNNEL_SENSE);
		} else {
			miner.setAttached(EvolutaAttachments.TUNNEL_SENSE, now + scene.quietFor());
		}
		TestPlayers.drain(miner);
		TestPlayers.drain(bystander);
		BlockState before = world.getBlockState(target);
		boolean broke;
		CLAIMED.clear();
		for (BlockPos pos : scene.claimed()) {
			CLAIMED.add(context.getAbsolutePos(pos).asLong());
		}
		// vanilla's own randomness during the break (kelp's age when its top goes) the same in both runs
		world.getRandom().setSeed(scene.seed());
		REMOVED.clear();
		long start = System.nanoTime();
		try {
			broke = miner.interactionManager.tryBreakBlock(target);
		} finally {
			CLAIMED.clear();
		}
		long nanos = System.nanoTime() - start;
		Map<BlockPos, BlockState> after = new HashMap<>();
		for (BlockPos pos : BlockPos.iterate(LOW - 1, 1, LOW - 1, HIGH + 1, 7, HIGH + 1)) {
			after.put(pos.toImmutable(), world.getBlockState(context.getAbsolutePos(pos)));
		}
		Double nearest = null;
		if (tunnelRange > 0) {
			for (BlockPos pos : BlockPos.iterate(target.add(-tunnelRange, -tunnelRange, -tunnelRange), target.add(tunnelRange, tunnelRange, tunnelRange))) {
				if (world.getBlockState(pos).isIn(ConventionalBlockTags.ORES)) {
					double distance = pos.getSquaredDistance(target);
					nearest = nearest == null ? distance : Math.min(nearest, distance);
				}
			}
		}
		Box area = new Box(context.getAbsolutePos(new BlockPos(LOW - 2, 0, LOW - 2))).union(new Box(context.getAbsolutePos(new BlockPos(HIGH + 2, 8, HIGH + 2))));
		int bees = 0;
		for (Entity entity : world.getEntitiesByClass(Entity.class, area, entity -> !(entity instanceof PlayerEntity))) {
			if (entity instanceof BeeEntity) {
				bees++;
			} else if (entity instanceof ItemEntity item) {
				bees += item.getStack().getOrDefault(DataComponentTypes.BEES, List.of()).size();
			}
		}
		Run run = new Run(nanos, broke, REMOVED.contains(target.asLong()), target, before, after, stack, miner.getHungerManager().getFoodLevel(), miner.getAttached(EvolutaAttachments.TUNNEL_SENSE),
				now, TestPlayers.sent(miner), TestPlayers.sent(bystander), bees, nearest);
		discardAround(world, area);
		return run;
	}

	private static void clearScene(TestContext context) {
		ServerWorld world = context.getWorld();
		for (BlockPos pos : BlockPos.iterate(LOW, 2, LOW, HIGH, 6, HIGH)) {
			world.setBlockState(context.getAbsolutePos(pos), Blocks.AIR.getDefaultState(), PLACE);
		}
	}

	private static void checkBreak(Scene scene, Run vanilla, Run grafted, int range, ToolFuzz.Failures failures, int cycle, String what) {
		List<Grafting.Weighted> grafts = Grafting.weighted(Grafting.grafts(scene.tool()));
		failures.check(vanilla.broke() == grafted.broke(), cycle, () -> what + ": broke " + grafted.broke() + " with grafts, " + vanilla.broke() + " without");
		BlockState target = grafted.target();
		// the grafts act when Fabric's after-break event fires, which the vanilla run shows as well as the grafted one
		failures.check(vanilla.removed() == grafted.removed(), cycle, () -> what + ": the block went with grafts " + grafted.removed()
				+ ", without " + vanilla.removed());
		boolean broke = grafted.removed();
		boolean ore = target.isIn(ConventionalBlockTags.ORES);

		// Blast Mining: ore of the target's kind gone, joined to the target, at most blastCount, never a claimed block
		Set<BlockPos> changed = new HashSet<>();
		for (BlockPos pos : grafted.after().keySet()) {
			if (!grafted.at(pos).equals(vanilla.at(pos))) {
				changed.add(pos);
			}
		}
		Set<BlockPos> vein = new HashSet<>();
		for (BlockPos pos : changed) {
			if (!pos.equals(TARGET) && vanilla.at(pos).isOf(target.getBlock()) && grafted.at(pos).isAir()) {
				vein.add(pos);
			}
		}
		boolean canBlast = broke && ore && has(grafts, SoulKind.CREEPER);
		int most = canBlast ? ToolGrafts.blastCount(grafts) : 0;
		failures.check(vein.size() <= most, cycle, () -> what + ": blasted " + vein.size() + " blocks " + vein + ", the most is " + most);
		for (BlockPos pos : vein) {
			failures.check(!scene.claimed().contains(pos), cycle, () -> what + ": blasted the claimed block at " + pos);
		}
		failures.check(joined(vein), cycle, () -> what + ": a blasted block " + vein + " is not joined to the mined one");

		// Cold Snap: every lava and still water beside the target and the blasted blocks, unless claimed
		Set<BlockPos> snapped = new HashSet<>();
		if (broke && hasElement(grafts, Element.PERMAFROST)) {
			Set<BlockPos> sources = new HashSet<>(vein);
			sources.add(TARGET);
			for (BlockPos source : sources) {
				for (Direction direction : Direction.values()) {
					BlockPos pos = source.offset(direction);
					BlockState expected = coldSnapped(vanilla.at(pos));
					if (expected == null || pos.equals(TARGET) || vein.contains(pos) || scene.claimed().contains(pos)) {
						continue;
					}
					snapped.add(pos);
					failures.check(grafted.at(pos).equals(expected), cycle, () -> what + ": Cold Snap left " + ToolFuzz.describe(vanilla.at(pos))
							+ " at " + pos + " as " + ToolFuzz.describe(grafted.at(pos)));
				}
			}
		}

		// nothing else may differ, but the water cut ice does not leave, and vanilla's own reactions to what the grafts
		// changed: lava meeting water beside a changed block, lava that no melt water reached, kelp losing its top
		boolean cutIce = broke && target.isIn(BlockTags.ICE) && has(grafts, SoulKind.STRAY) && grafted.at(TARGET).isAir()
				&& vanilla.at(TARGET).isOf(Blocks.WATER);
		Set<BlockPos> graftChanged = new HashSet<>(vein);
		graftChanged.addAll(snapped);
		if (cutIce) {
			graftChanged.add(TARGET);
		}
		for (BlockPos pos : changed) {
			if (vein.contains(pos) || snapped.contains(pos) || cutIce && pos.equals(TARGET)) {
				continue;
			}
			boolean lavaMet = vanilla.at(pos).isOf(Blocks.LAVA) && (grafted.at(pos).isOf(Blocks.OBSIDIAN) || grafted.at(pos).isOf(Blocks.COBBLESTONE))
					&& touches(pos, graftChanged);
			boolean unmelted = cutIce && grafted.at(pos).isOf(Blocks.LAVA) && (vanilla.at(pos).isOf(Blocks.OBSIDIAN) || vanilla.at(pos).isOf(Blocks.COBBLESTONE))
					&& touches(pos, Set.of(TARGET));
			// kelp whose top a graft changed grows a new top, of a random age
			boolean kelpTopped = vanilla.at(pos).isOf(Blocks.KELP_PLANT) && grafted.at(pos).isOf(Blocks.KELP) && touches(pos, graftChanged);
			failures.check(lavaMet || unmelted || kelpTopped, cycle, () -> what + ": unexplained change at " + pos + ": "
					+ ToolFuzz.describe(vanilla.at(pos)) + " without grafts, " + ToolFuzz.describe(grafted.at(pos)) + " with");
		}
		// a claimed block is never blasted or snapped (vanilla's own fluids may still react round it, claims or not)
		for (BlockPos pos : scene.claimed()) {
			BlockState snappedState = coldSnapped(vanilla.at(pos));
			boolean graftTouched = vein.contains(pos) || snappedState != null && grafted.at(pos).equals(snappedState) && !snappedState.equals(vanilla.at(pos));
			failures.check(!graftTouched, cycle, () -> what + ": the grafts changed the claimed block at " + pos + " from "
					+ ToolFuzz.describe(vanilla.at(pos)) + " to " + ToolFuzz.describe(grafted.at(pos)));
		}

		// the tool: one point of wear per blasted block (no Unbreaking to hide it), never broken by the grafts
		if (broke && scene.mode() == GameMode.SURVIVAL) {
			boolean unbreaking = level(scene.plain(), Enchantments.UNBREAKING) > 0;
			if (!unbreaking && !vanilla.held().isEmpty()) {
				failures.check(grafted.held().getDamage() == vanilla.held().getDamage() + vein.size(), cycle, () -> what + ": the tool took "
						+ grafted.held().getDamage() + " damage, " + vanilla.held().getDamage() + " without grafts, " + vein.size() + " blasted");
			}
			// a blast stops with two points left, so the mined block's own wear never breaks the tool; without a blast,
			// Unbreaking's roll may spare the tool in one run and not the other
			failures.check(vein.isEmpty() || !grafted.held().isEmpty(), cycle, () -> what + ": the tool broke after blasting " + vein.size());
			boolean lastPoint = scene.plain().getMaxDamage() - scene.plain().getDamage() <= 1;
			failures.check(!grafted.held().isEmpty() || vanilla.held().isEmpty() || lastPoint && unbreaking, cycle, () -> what
					+ ": the grafts broke the tool");
		}

		// Gnaw: a point of food per ore broken, the blasted ones too
		int ores = broke && ore ? 1 + vein.size() : 0;
		int food = Math.min(20, vanilla.food() + (has(grafts, SoulKind.ZOMBIE) ? ores : 0));
		failures.check(grafted.food() == food, cycle, () -> what + ": food " + grafted.food() + ", expected " + food);

		// Tunnel Sense
		int chimes = 0;
		float pitch = Float.NaN;
		int sparks = 0;
		Set<BlockPos> blastedShown = new HashSet<>();
		for (Packet<?> packet : grafted.minerPackets()) {
			if (packet instanceof PlaySoundS2CPacket sound && sound.getSound().value() == SoundEvents.BLOCK_AMETHYST_BLOCK_CHIME) {
				chimes++;
				pitch = sound.getPitch();
			} else if (packet instanceof ParticleS2CPacket particle && particle.getParameters().getType() == ParticleTypes.ELECTRIC_SPARK) {
				sparks++;
			} else if (packet instanceof WorldEventS2CPacket event && event.getEventId() == WorldEvents.BLOCK_BROKEN) {
				blastedShown.add(event.getPos());
			}
		}
		int heard = 0;
		for (Packet<?> packet : grafted.bystanderPackets()) {
			if (packet instanceof PlaySoundS2CPacket sound && sound.getSound().value() == SoundEvents.BLOCK_AMETHYST_BLOCK_CHIME
					|| packet instanceof ParticleS2CPacket particle && particle.getParameters().getType() == ParticleTypes.ELECTRIC_SPARK) {
				heard++;
			}
		}
		int bystanderHeard = heard;
		failures.check(bystanderHeard == 0, cycle, () -> what + ": the bystander got " + bystanderHeard + " Tunnel Sense packets");
		boolean feels = broke && has(grafts, SoulKind.CAVE_SPIDER) && (target.isIn(BlockTags.BASE_STONE_OVERWORLD) || target.isIn(BlockTags.BASE_STONE_NETHER));
		Long prior = scene.quietFor() == null ? null : grafted.now() + scene.quietFor();
		if (!feels || prior != null && grafted.now() < prior) {
			int heardChimes = chimes;
			int heardSparks = sparks;
			failures.check(Objects.equals(grafted.quiet(), prior), cycle, () -> what + ": Tunnel Sense set its wait to " + grafted.quiet() + " from " + prior);
			failures.check(heardChimes == 0 && heardSparks == 0, cycle, () -> what + ": Tunnel Sense chimed " + heardChimes + " and sparked "
					+ heardSparks + " when it should not feel");
		} else if (grafted.nearestOre() == null) {
			int heardChimes = chimes;
			failures.check(Objects.equals(grafted.quiet(), grafted.now() + ToolGrafts.TUNNEL_SENSE_RETRY), cycle, () -> what
					+ ": with no ore in range Tunnel Sense set its wait to " + grafted.quiet() + " at " + grafted.now());
			failures.check(heardChimes == 0, cycle, () -> what + ": Tunnel Sense chimed with no ore in range");
		} else {
			float closeness = 1.0F - (float) Math.sqrt(grafted.nearestOre()) / (range + 1);
			float expectedPitch = 0.8F + 0.8F * closeness;
			int heardChimes = chimes;
			int heardSparks = sparks;
			float heardPitch = pitch;
			failures.check(Objects.equals(grafted.quiet(), grafted.now() + ToolGrafts.TUNNEL_SENSE_COOLDOWN), cycle, () -> what
					+ ": after a chime Tunnel Sense set its wait to " + grafted.quiet() + " at " + grafted.now());
			failures.check(heardChimes == 1 && Math.abs(heardPitch - expectedPitch) < 1.0E-4F && heardSparks == 4, cycle, () -> what
					+ ": ore at distance " + Math.sqrt(grafted.nearestOre()) + " in range " + range + ": " + heardChimes + " chimes at pitch "
					+ heardPitch + " (expected 1 at " + expectedPitch + "), " + heardSparks + " sparks (expected 4)");
		}

		// Blast Mining shows the miner each blasted block breaking; vanilla shows the others
		Set<BlockPos> expectedShown = new HashSet<>();
		for (Packet<?> packet : vanilla.minerPackets()) {
			if (packet instanceof WorldEventS2CPacket event && event.getEventId() == WorldEvents.BLOCK_BROKEN) {
				expectedShown.add(event.getPos());
			}
		}
		for (BlockPos pos : vein) {
			expectedShown.add(absolute(grafted, pos));
		}
		failures.check(blastedShown.equals(expectedShown), cycle, () -> what + ": the miner saw breaks at " + blastedShown + ", blasted " + expectedShown);

		// bees never multiply: those flying plus those carried in a hive item
		if (scene.bees() > 0) {
			failures.check(grafted.bees() <= scene.bees(), cycle, () -> what + ": " + scene.bees() + " bees in the hive, " + grafted.bees()
					+ " after the break (" + vanilla.bees() + " without grafts)");
		}
	}

	/** The absolute position of a scene position, from the target's: the runs keep the target's absolute position. */
	private static BlockPos absolute(Run run, BlockPos relative) {
		return run.absoluteTarget().add(relative.subtract(TARGET));
	}

	private static boolean joined(Set<BlockPos> vein) {
		if (vein.isEmpty()) {
			return true;
		}
		Set<BlockPos> reached = new HashSet<>();
		ArrayDeque<BlockPos> open = new ArrayDeque<>(List.of(TARGET));
		while (!open.isEmpty()) {
			BlockPos at = open.poll();
			for (Direction direction : Direction.values()) {
				BlockPos next = at.offset(direction);
				if (vein.contains(next) && reached.add(next)) {
					open.add(next);
				}
			}
		}
		return reached.size() == vein.size();
	}

	private static boolean touches(BlockPos pos, Set<BlockPos> positions) {
		for (Direction direction : Direction.values()) {
			if (positions.contains(pos.offset(direction))) {
				return true;
			}
		}
		return false;
	}

	/** What Cold Snap makes of a block: lava hardens (a source to obsidian, a flow to cobblestone), still water freezes; null: untouched. */
	@Nullable
	private static BlockState coldSnapped(@Nullable BlockState state) {
		if (state == null) {
			return null;
		}
		if (state.isOf(Blocks.LAVA)) {
			return state.getFluidState().isStill() ? Blocks.OBSIDIAN.getDefaultState() : Blocks.COBBLESTONE.getDefaultState();
		}
		if (state.isOf(Blocks.WATER) && state.getFluidState().isStill()) {
			return Blocks.FROSTED_ICE.getDefaultState().with(FrostedIceBlock.AGE, 0);
		}
		return null;
	}

	private static boolean has(List<Grafting.Weighted> grafts, SoulKind kind) {
		return grafts.stream().anyMatch(graft -> graft.graft().kind() == kind);
	}

	private static boolean hasElement(List<Grafting.Weighted> grafts, Element element) {
		return grafts.stream().anyMatch(graft -> graft.graft().element() == element);
	}

	private static int level(ItemStack stack, net.minecraft.registry.RegistryKey<net.minecraft.enchantment.Enchantment> key) {
		for (RegistryEntry<net.minecraft.enchantment.Enchantment> entry : stack.getEnchantments().getEnchantments()) {
			if (entry.matchesKey(key)) {
				return stack.getEnchantments().getLevel(entry);
			}
		}
		return 0;
	}

	// ---------------------------------------------------------------------------------------------------------------
	// Break speed

	/**
	 * Any block in any state, any graftable item with random grafts, Efficiency, Haste or Mining Fatigue, on the ground
	 * or not: the grafted item breaks at exactly the plain item's speed times Sandsifter's factor, which is above 1 only
	 * for a husk graft on mining gear and a shovel's block.
	 */
	@GameTest(templateName = EMPTY_STRUCTURE, batchId = BATCH, tickLimit = 400)
	public void breakSpeedSiftsOnlyShovelBlocksWithMiningGear(TestContext context) {
		ServerWorld world = context.getWorld();
		ServerPlayerEntity player = TestPlayers.survival(context, new BlockPos(1, 2, 1), 0);
		List<Block> blocks = Registries.BLOCK.stream().toList();
		List<Item> tools = ToolFuzz.items(Grafting.TOOLS);
		List<Item> others = new ArrayList<>(ToolFuzz.items(Grafting.WEAPONS));
		others.addAll(ToolFuzz.items(Grafting.ARMOR));
		others.removeIf(item -> new ItemStack(item).isIn(Grafting.TOOLS));
		ToolFuzz.Failures failures = new ToolFuzz.Failures("break speed fuzz");
		try {
			for (int cycle = 0; cycle < ToolFuzz.CYCLES; cycle++) {
				SplittableRandom random = ToolFuzz.random("speed", cycle);
				Block block = ToolFuzz.pick(random, blocks);
				BlockState state = ToolFuzz.pick(random, block.getStateManager().getStates());
				Item item = random.nextInt(4) == 0 ? ToolFuzz.pick(random, others) : ToolFuzz.pick(random, tools);
				ItemStack plain = ToolFuzz.tool(random, world, item);
				ItemStack grafted = ToolFuzz.grafted(plain, ToolFuzz.grafts(random, Grafting.MAX_GRAFTS));
				failures.ran();
				try {
					player.clearStatusEffects();
					if (random.nextInt(3) == 0) {
						player.addStatusEffect(new StatusEffectInstance(StatusEffects.HASTE, 200, random.nextInt(3)));
					}
					if (random.nextInt(4) == 0) {
						player.addStatusEffect(new StatusEffectInstance(StatusEffects.MINING_FATIGUE, 200, random.nextInt(3)));
					}
					player.setOnGround(random.nextInt(4) != 0);
					player.setStackInHand(Hand.MAIN_HAND, plain);
					float base = player.getBlockBreakingSpeed(state);
					player.setStackInHand(Hand.MAIN_HAND, grafted);
					float speed = player.getBlockBreakingSpeed(state);
					float total = 0;
					if (grafted.isIn(Grafting.TOOLS) && state.isIn(BlockTags.SHOVEL_MINEABLE)) {
						for (Grafting.Weighted graft : Grafting.weighted(Grafting.grafts(grafted))) {
							if (graft.graft().kind() == SoulKind.HUSK) {
								total += (0.3F + 0.5F * graft.power()) * graft.weight();
							}
						}
					}
					float expected = base * (1.0F + total);
					failures.check(Math.abs(speed - expected) <= 1.0E-5F * Math.max(1.0F, Math.abs(expected)), cycle, () -> ToolFuzz.describe(state)
							+ " by " + ToolFuzz.describe(grafted) + ": speed " + speed + ", plain " + base + ", expected " + expected);
				} catch (RuntimeException e) {
					failures.add(cycle, ToolFuzz.describe(state) + " by " + ToolFuzz.describe(grafted) + " threw " + e);
				}
			}
		} finally {
			player.clearStatusEffects();
			TestPlayers.remove(context, player);
		}
		failures.throwIfAny();
		context.complete();
	}
}
