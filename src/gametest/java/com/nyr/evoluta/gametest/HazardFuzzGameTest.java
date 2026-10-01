package com.nyr.evoluta.gametest;

import com.nyr.evoluta.common.combat.HazardBlockTypes;
import com.nyr.evoluta.common.combat.HazardBlocks;
import com.nyr.evoluta.common.combat.Hazards;
import com.nyr.evoluta.common.mutation.Archetype;
import com.nyr.evoluta.common.mutation.Element;
import com.nyr.evoluta.common.mutation.MutationData;
import com.nyr.evoluta.common.mutation.Tier;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.SplittableRandom;
import net.fabricmc.fabric.api.gametest.v1.FabricGameTest;
import net.minecraft.block.Block;
import net.minecraft.block.BlockState;
import net.minecraft.block.Blocks;
import net.minecraft.block.SnowBlock;
import net.minecraft.block.TallPlantBlock;
import net.minecraft.entity.Entity;
import net.minecraft.entity.EntityType;
import net.minecraft.entity.ItemEntity;
import net.minecraft.entity.mob.MobEntity;
import net.minecraft.entity.player.PlayerEntity;
import net.minecraft.item.Item;
import net.minecraft.item.Items;
import net.minecraft.server.network.ServerPlayerEntity;
import net.minecraft.server.world.ServerWorld;
import net.minecraft.test.GameTest;
import net.minecraft.test.TestContext;
import net.minecraft.util.math.BlockPos;
import net.minecraft.util.math.Box;
import net.minecraft.world.World;
import net.minecraft.world.level.ServerWorldProperties;

/**
 * Fuzz of the hazard blocks mutants leave (trails, death bursts, soul fire, creeper craters) against their documented
 * rules: they only stand in for air, plants or plain blocks a blast could break (never a chest, never bedrock), take
 * nothing else with them, cannot be mined by players or blown up, and when their time is up every block is exactly as
 * it was, with nothing dropped. Random terrain of floors, plants, tall plants, snow, torches, slabs and overhangs;
 * random sources, elements and tiers, overlapping; now and then a TNT blast in the middle of it. Time is moved on
 * instead of waited for.
 */
public final class HazardFuzzGameTest implements FabricGameTest {
	private static final String BATCH = "evoluta_hazard_fuzz";
	private static final int PLACE = Block.NOTIFY_LISTENERS | Block.FORCE_STATE;
	/** What hazards are made of; none of it is in the terrain, so any of it dropped is a leak. */
	private static final Set<Block> MATERIALS = Set.of(HazardBlockTypes.MUTANT_FIRE, HazardBlockTypes.SOUL_FIRE, HazardBlockTypes.FROST_SPIKES,
			HazardBlockTypes.BLIGHT, Blocks.MAGMA_BLOCK, Blocks.SLIME_BLOCK, Blocks.PACKED_ICE, Blocks.POWDER_SNOW);
	private static final Set<Item> LEAKS = Set.of(Items.MAGMA_BLOCK, Items.SLIME_BLOCK, Items.PACKED_ICE, Items.POWDER_SNOW_BUCKET);
	/** Blocks that stay put when what is under them goes: the hazard floors and powder snow. */
	private static final Set<Block> STANDING = Set.of(Blocks.MAGMA_BLOCK, Blocks.SLIME_BLOCK, Blocks.PACKED_ICE, Blocks.POWDER_SNOW);

	private static final Block[] FLOORS = {Blocks.STONE, Blocks.DIRT, Blocks.GRASS_BLOCK, Blocks.DEEPSLATE, Blocks.COBBLESTONE, Blocks.OAK_PLANKS,
			Blocks.WHITE_WOOL, Blocks.GLASS, Blocks.FARMLAND, Blocks.SANDSTONE, Blocks.NETHERRACK, Blocks.OBSIDIAN, Blocks.BEDROCK, Blocks.CRAFTING_TABLE,
			Blocks.BOOKSHELF, Blocks.OAK_LOG, Blocks.ICE, Blocks.CHEST, Blocks.FURNACE, Blocks.IRON_BLOCK, Blocks.END_STONE, Blocks.MOSSY_COBBLESTONE,
			Blocks.TERRACOTTA, Blocks.SNOW_BLOCK, Blocks.CLAY, Blocks.MUD, Blocks.PODZOL, Blocks.MOSS_BLOCK, Blocks.TUFF, Blocks.CALCITE,
			Blocks.COPPER_BLOCK, Blocks.AMETHYST_BLOCK, Blocks.BARREL, Blocks.JUKEBOX};
	private static final Block[] SMALL_PLANTS = {Blocks.SHORT_GRASS, Blocks.FERN, Blocks.DANDELION, Blocks.POPPY, Blocks.DEAD_BUSH, Blocks.AZURE_BLUET};
	private static final Block[] TALL_PLANTS = {Blocks.TALL_GRASS, Blocks.LARGE_FERN, Blocks.LILAC, Blocks.ROSE_BUSH, Blocks.PEONY, Blocks.SUNFLOWER};
	private static final Block[] SMALL_THINGS = {Blocks.TORCH, Blocks.WHITE_CARPET, Blocks.OAK_SLAB, Blocks.STONE_STAIRS, Blocks.OAK_FENCE,
			Blocks.GLASS_PANE, Blocks.LANTERN, Blocks.FLOWER_POT, Blocks.RAIL, Blocks.REDSTONE_WIRE, Blocks.OAK_PRESSURE_PLATE, Blocks.COBWEB};

	private enum Source {
		DEATH, TRAIL, SOUL_FIRE, BLAST
	}

	/** Relative scene: x and z 0 to 7, y 1 to 6. */
	private static Iterable<BlockPos> scene() {
		return BlockPos.iterate(0, 1, 0, 7, 6, 7);
	}

	private static Map<BlockPos, BlockState> terrain(SplittableRandom random) {
		Map<BlockPos, BlockState> blocks = new HashMap<>();
		for (int x = 0; x <= 7; x++) {
			for (int z = 0; z <= 7; z++) {
				int top = random.nextInt(5) == 0 ? 2 : 1;
				for (int y = 1; y <= top; y++) {
					blocks.put(new BlockPos(x, y, z), ToolFuzz.pick(random, FLOORS).getDefaultState());
				}
				BlockPos above = new BlockPos(x, top + 1, z);
				int roll = random.nextInt(100);
				if (roll < 15) {
					blocks.put(above, ToolFuzz.pick(random, SMALL_PLANTS).getDefaultState());
				} else if (roll < 25) {
					BlockState lower = ToolFuzz.pick(random, TALL_PLANTS).getDefaultState();
					blocks.put(above, lower);
					blocks.put(above.up(), lower.with(TallPlantBlock.HALF, net.minecraft.block.enums.DoubleBlockHalf.UPPER));
				} else if (roll < 32) {
					blocks.put(above, Blocks.SNOW.getDefaultState().with(SnowBlock.LAYERS, 1 + random.nextInt(3)));
				} else if (roll < 45) {
					blocks.put(above, ToolFuzz.pick(random, SMALL_THINGS).getDefaultState());
				}
				if (random.nextInt(8) == 0) {
					// an overhang two or three blocks above the ground
					blocks.put(new BlockPos(x, top + 3 + random.nextInt(2), z), Blocks.STONE.getDefaultState());
				}
			}
		}
		return blocks;
	}

	/** Documented: air or a plant, with no water in it, or a plain block no tougher than stone that holds nothing; never half of a two-block thing. */
	private static boolean mayTake(ServerWorld world, BlockPos pos, BlockState state) {
		if (state.contains(net.minecraft.state.property.Properties.DOUBLE_BLOCK_HALF)) {
			return false;
		}
		if (state.isAir() || state.isReplaceable()) {
			return state.getFluidState().isEmpty();
		}
		float hardness = state.getHardness(world, pos);
		return !state.hasBlockEntity() && hardness >= 0.0F && hardness <= 3.0F && state.getBlock().getBlastResistance() <= 6.0F;
	}

	/**
	 * Hazards on random terrain: every block they change is one of their own, where they may stand, and held until its
	 * time; players cannot mine them and a TNT blast passes over them; once their time is up, every block they took is
	 * back exactly, no hazard is left anywhere, and nothing they were made of lies on the ground. Without a blast, the
	 * whole scene is exactly as it was and nothing at all was dropped.
	 */
	@GameTest(templateName = EMPTY_STRUCTURE, batchId = BATCH, tickLimit = 400)
	public void hazardsTakeOnlyWhatTheyMayAndPutItBack(TestContext context) {
		ServerWorld world = context.getWorld();
		ServerWorldProperties clock = (ServerWorldProperties) world.getLevelProperties();
		ServerPlayerEntity player = TestPlayers.survival(context, new BlockPos(0, 8, 0), 0);
		Box area = new Box(context.getAbsolutePos(new BlockPos(-2, 0, -2))).union(new Box(context.getAbsolutePos(new BlockPos(9, 8, 9))));
		ToolFuzz.Failures failures = new ToolFuzz.Failures("hazard fuzz");
		int cycles = Math.max(300, ToolFuzz.CYCLES / 5);
		try {
			for (int cycle = 0; cycle < cycles; cycle++) {
				if (!ToolFuzz.runs(cycle)) {
					continue;
				}
				SplittableRandom random = ToolFuzz.random("hazards", cycle);
				failures.ran();
				String what = "?";
				// the world's random too (spike heights, powder snow or spikes), so a case replays alone exactly
				world.getRandom().setSeed(random.nextLong());
				try {
					clear(context);
					Map<BlockPos, BlockState> terrain = terrain(random);
					List<BlockPos> order = new ArrayList<>(terrain.keySet());
					order.sort((a, b) -> a.getY() != b.getY() ? Integer.compare(a.getY(), b.getY()) : Long.compare(a.asLong(), b.asLong()));
					for (BlockPos pos : order) {
						BlockState state = terrain.get(pos);
						BlockPos at = context.getAbsolutePos(pos);
						// a decoration only where it can stand (a flower on glass would drop at the first neighbour update)
						if (pos.getY() == 1 || state.isOf(Blocks.STONE) && pos.getY() > 3 || state.canPlaceAt(world, at)) {
							world.setBlockState(at, state, PLACE);
						}
					}
					for (BlockPos pos : order) {
						BlockPos at = context.getAbsolutePos(pos);
						BlockState state = world.getBlockState(at);
						BlockState settled = Block.postProcessState(state, world, at);
						if (settled != state) {
							world.setBlockState(at, settled, PLACE);
						}
					}
					for (Entity entity : world.getEntitiesByClass(Entity.class, area, entity -> !(entity instanceof PlayerEntity))) {
						entity.discard();
					}
					Map<BlockPos, BlockState> before = new HashMap<>();
					for (BlockPos pos : scene()) {
						BlockPos at = context.getAbsolutePos(pos).toImmutable();
						before.put(at, world.getBlockState(at));
					}
					int heldBefore = held(world, before.keySet());
					List<String> sources = new ArrayList<>();
					Map<BlockPos, BlockState> step = new HashMap<>(before);
					StringBuilder log = new StringBuilder();
					for (int i = 1 + random.nextInt(4); i > 0; i--) {
						String source = spawnHazards(context, world, random, player);
						sources.add(source);
						log.append("\n        ").append(source).append(':');
						for (Map.Entry<BlockPos, BlockState> entry : step.entrySet()) {
							BlockState now = world.getBlockState(entry.getKey());
							if (!now.equals(entry.getValue())) {
								log.append(' ').append(entry.getKey().subtract(context.getAbsolutePos(BlockPos.ORIGIN)).toShortString()).append(' ')
										.append(ToolFuzz.describe(entry.getValue())).append("->").append(ToolFuzz.describe(now)).append(';');
								entry.setValue(now);
							}
						}
					}
					String steps = log.toString();
					boolean blast = random.nextInt(4) == 0;
					what = sources + (blast ? " then a TNT blast" : "");
					HazardBlocks hazards = HazardBlocks.of(world);

					// what the hazards took, and what they took with them
					for (Map.Entry<BlockPos, BlockState> entry : before.entrySet()) {
						BlockPos at = entry.getKey();
						BlockState now = world.getBlockState(at);
						BlockState was = entry.getValue();
						String where = what;
						if (!now.isOf(was.getBlock())) {
							BlockPos relative = at.subtract(context.getAbsolutePos(BlockPos.ORIGIN));
							failures.check(MATERIALS.contains(now.getBlock()) && hazards.holds(at), cycle, () -> where + ": " + ToolFuzz.describe(was) + " at "
									+ relative.toShortString() + " became " + ToolFuzz.describe(now) + ", which is no hazard; step by step:" + steps);
							failures.check(!hazards.holds(at) || mayTake(world, at, was), cycle, () -> where + ": a hazard took " + ToolFuzz.describe(was)
									+ " at " + relative.toShortString() + "; step by step:" + steps);
						}
					}
					Map<BlockPos, String> taken = new HashMap<>();
					for (Map.Entry<BlockPos, BlockState> entry : before.entrySet()) {
						if (hazards.holds(entry.getKey())) {
							taken.put(entry.getKey().subtract(context.getAbsolutePos(BlockPos.ORIGIN)), ToolFuzz.describe(entry.getValue()) + " -> "
									+ ToolFuzz.describe(world.getBlockState(entry.getKey())));
						}
					}
					// players cannot mine them
					List<BlockPos> held = new ArrayList<>();
					for (BlockPos at : before.keySet()) {
						if (hazards.holds(at)) {
							held.add(at);
						}
					}
					for (int i = 0; i < 3 && !held.isEmpty(); i++) {
						BlockPos at = ToolFuzz.pick(random, held);
						BlockState hazard = world.getBlockState(at);
						boolean broke = player.interactionManager.tryBreakBlock(at);
						String where = what;
						failures.check(!broke && world.getBlockState(at).equals(hazard), cycle, () -> where + ": a player mined the hazard "
								+ ToolFuzz.describe(hazard) + " at " + at);
					}
					// a blast passes over them
					Map<BlockPos, BlockState> standing = new HashMap<>();
					for (BlockPos at : held) {
						BlockState state = world.getBlockState(at);
						if (STANDING.contains(state.getBlock())) {
							standing.put(at, state);
						}
					}
					if (blast) {
						BlockPos center = context.getAbsolutePos(new BlockPos(1 + random.nextInt(6), 2 + random.nextInt(2), 1 + random.nextInt(6)));
						world.createExplosion(null, center.getX() + 0.5, center.getY() + 0.5, center.getZ() + 0.5, 2.0F + (float) random.nextDouble() * 2.0F,
								World.ExplosionSourceType.TNT);
						for (Map.Entry<BlockPos, BlockState> entry : standing.entrySet()) {
							BlockState now = world.getBlockState(entry.getKey());
							String where = what;
							failures.check(now.equals(entry.getValue()), cycle, () -> where + ": the blast took the hazard " + ToolFuzz.describe(entry.getValue())
									+ " at " + entry.getKey() + ", leaving " + ToolFuzz.describe(now));
						}
					}

					// time passes: every hazard runs out; then the clock goes back, since game tests count ticks by it
					long clockNow = clock.getTime();
					clock.setTime(clockNow + 400);
					HazardBlocks.tick(world);
					clock.setTime(clockNow);
					int heldAfter = held(world, before.keySet());
					String where = what;
					failures.check(heldAfter == heldBefore, cycle, () -> where + ": " + (heldAfter - heldBefore) + " hazards still held after their time");
					for (Map.Entry<BlockPos, BlockState> entry : before.entrySet()) {
						BlockPos at = entry.getKey();
						BlockState now = world.getBlockState(at);
						boolean wasHeld = held.contains(at);
						failures.check(!(now.isOf(HazardBlockTypes.MUTANT_FIRE) || now.isOf(HazardBlockTypes.SOUL_FIRE) || now.isOf(HazardBlockTypes.FROST_SPIKES)
								|| now.isOf(HazardBlockTypes.BLIGHT)), cycle, () -> where + ": a hazard " + ToolFuzz.describe(now) + " is left at " + at);
						// after a blast, only the hazards it passed over must be back; one that stood on something the blast took
						// fell with it, as the plant or snow it stood for would have
						if (!blast || standing.containsKey(at)) {
							failures.check(now.equals(entry.getValue()), cycle, () -> where + ": " + at + " is " + ToolFuzz.describe(now) + " after the hazards, was "
									+ ToolFuzz.describe(entry.getValue()) + (wasHeld ? " (a hazard stood there)" : ""));
						}
					}
					for (ItemEntity item : world.getEntitiesByClass(ItemEntity.class, area, entity -> true)) {
						failures.check(!LEAKS.contains(item.getStack().getItem()) && (blast || item.getStack().isEmpty()), cycle, () -> where + ": "
								+ item.getStack() + " lies on the ground at " + item.getBlockPos() + ", round it " + changes(before, world, item.getBlockPos())
								+ ", hazards taken " + taken);
					}
				} catch (RuntimeException e) {
					failures.add(cycle, what + " threw " + e);
				} finally {
					for (Entity entity : world.getEntitiesByClass(Entity.class, area, entity -> !(entity instanceof PlayerEntity))) {
						entity.discard();
					}
				}
			}
		} finally {
			clear(context);
			world.getRandom().setSeed(net.minecraft.util.math.random.RandomSeed.getSeed());
			TestPlayers.remove(context, player);
		}
		failures.throwIfAny();
		context.complete();
	}

	/**
	 * A hazard whose time comes while its chunk is unloaded waits for the chunk without being polled (none of it stays
	 * in the time queue), and goes back once the chunk loads.
	 */
	@GameTest(templateName = EMPTY_STRUCTURE, batchId = BATCH)
	public void hazardsInAnUnloadedChunkWaitForItUnpolled(TestContext context) throws ReflectiveOperationException {
		ServerWorld world = context.getWorld();
		HazardBlocks hazards = HazardBlocks.of(world);
		BlockPos far = context.getAbsolutePos(new BlockPos(3, 2, 3)).add(16 * 400, 0, 16 * 400);
		net.minecraft.util.math.ChunkPos chunk = new net.minecraft.util.math.ChunkPos(far);
		context.assertFalse(world.isChunkLoaded(chunk.x, chunk.z), "setup: the far chunk is loaded");
		java.lang.reflect.Method remember = HazardBlocks.class.getDeclaredMethod("remember", BlockPos.class, BlockState.class, BlockState.class, long.class);
		remember.setAccessible(true);
		remember.invoke(hazards, far, HazardBlockTypes.MUTANT_FIRE.getDefaultState(), Blocks.AIR.getDefaultState(), world.getTime() - 1);
		java.lang.reflect.Field byTime = HazardBlocks.class.getDeclaredField("byTime");
		byTime.setAccessible(true);
		java.lang.reflect.Field waiting = HazardBlocks.class.getDeclaredField("waiting");
		waiting.setAccessible(true);
		HazardBlocks.tick(world);
		boolean queued = ((java.util.Collection<?>) byTime.get(hazards)).stream().anyMatch(entry -> entry.toString().contains(far.toString()));
		context.assertFalse(queued, "a hazard in an unloaded chunk is still polled");
		context.assertTrue(((Map<?, ?>) waiting.get(hazards)).containsKey(chunk.toLong()) && hazards.holds(far), "the hazard does not wait for its chunk");
		world.getChunk(chunk.x, chunk.z);
		HazardBlocks.tick(world);
		context.assertFalse(hazards.holds(far) || ((Map<?, ?>) waiting.get(hazards)).containsKey(chunk.toLong()), "the chunk loaded and its hazard stayed");
		context.complete();
	}

	/** Empties the scene from the top down, so nothing loses its footing on the way. */
	private static void clear(TestContext context) {
		for (int y = 6; y >= 1; y--) {
			for (BlockPos pos : BlockPos.iterate(0, y, 0, 7, y, 7)) {
				context.getWorld().setBlockState(context.getAbsolutePos(pos), Blocks.AIR.getDefaultState(), PLACE);
			}
		}
	}

	/** How many of {@code positions} a hazard holds (other tests' hazards elsewhere are theirs). */
	private static int held(ServerWorld world, Iterable<BlockPos> positions) {
		HazardBlocks hazards = HazardBlocks.of(world);
		int held = 0;
		for (BlockPos pos : positions) {
			held += hazards.holds(pos) ? 1 : 0;
		}
		return held;
	}

	/** Blocks within two of {@code center} that differ from the scene as it stood. */
	private static List<String> changes(Map<BlockPos, BlockState> before, ServerWorld world, BlockPos center) {
		List<String> changes = new ArrayList<>();
		for (BlockPos pos : BlockPos.iterate(center.add(-2, -2, -2), center.add(2, 2, 2))) {
			BlockState was = before.get(pos);
			BlockState now = world.getBlockState(pos);
			if (was != null && !was.equals(now)) {
				changes.add(pos.subtract(center).toShortString() + " " + ToolFuzz.describe(was) + " -> " + ToolFuzz.describe(now));
			}
		}
		return changes;
	}

	/** One mutant's hazards: a death burst, a trail block, an Apex Ignited kill's soul fire or a creeper's crater. */
	private static String spawnHazards(TestContext context, ServerWorld world, SplittableRandom random, ServerPlayerEntity player) {
		Source source = ToolFuzz.pick(random, Source.values());
		Element element = ToolFuzz.pick(random, ToolFuzz.ELEMENTS);
		Tier tier = ToolFuzz.pick(random, Tier.values());
		int x = 1 + random.nextInt(6);
		int z = 1 + random.nextInt(6);
		int y = 2;
		while (y < 6 && !world.getBlockState(context.getAbsolutePos(new BlockPos(x, y, z))).isAir()) {
			y++;
		}
		BlockPos at = context.getAbsolutePos(new BlockPos(x, y, z));
		MobEntity mob = source == Source.BLAST ? EntityType.CREEPER.create(world) : EntityType.ZOMBIE.create(world);
		mob.refreshPositionAndAngles(at.getX() + random.nextDouble(), at.getY(), at.getZ() + random.nextDouble(), 0, 0);
		MutationData data = MutationData.of(tier, element, Archetype.NONE);
		switch (source) {
			case DEATH -> Hazards.onDeathBurst(world, mob, data);
			case TRAIL -> {
				mob.setTarget(player);
				mob.setOnGround(true);
				mob.setVelocity(0.2, 0.0, -0.1);
				Hazards.leaveTrail(world, mob, data);
			}
			case SOUL_FIRE -> Hazards.kindleSoulFire(world, mob, (float) random.nextDouble());
			case BLAST -> Hazards.blastHazards(world, mob, data, 1.5F + (float) random.nextDouble() * 0.5F, HazardBlocks.of(world));
		}
		mob.discard();
		BlockPos relative = at.subtract(context.getAbsolutePos(BlockPos.ORIGIN));
		return source + " " + element.key() + " " + tier.key() + " at " + relative.toShortString();
	}
}
