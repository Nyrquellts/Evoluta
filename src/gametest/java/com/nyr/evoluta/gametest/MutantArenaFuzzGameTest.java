package com.nyr.evoluta.gametest;

import com.nyr.evoluta.common.combat.HazardBlockTypes;
import com.nyr.evoluta.common.combat.HazardBlocks;
import com.nyr.evoluta.common.config.EvolutaConfig;
import com.nyr.evoluta.common.mutation.Archetype;
import com.nyr.evoluta.common.mutation.Element;
import com.nyr.evoluta.common.mutation.MutationData;
import com.nyr.evoluta.common.mutation.Mutations;
import com.nyr.evoluta.common.mutation.Tier;
import com.nyr.evoluta.common.soul.Grafting;
import com.nyr.evoluta.common.soul.MutantGear;
import com.nyr.evoluta.common.tag.EvolutaTags;
import com.nyr.evoluta.common.world.EvolutaWorlds;
import com.nyr.evoluta.common.world.WorldState;
import java.lang.reflect.Field;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.SplittableRandom;
import net.fabricmc.fabric.api.gametest.v1.FabricGameTest;
import net.minecraft.block.Block;
import net.minecraft.block.BlockState;
import net.minecraft.block.Blocks;
import net.minecraft.entity.Entity;
import net.minecraft.entity.EntityType;
import net.minecraft.entity.EquipmentSlot;
import net.minecraft.entity.ItemEntity;
import net.minecraft.entity.LivingEntity;
import net.minecraft.entity.SpawnReason;
import net.minecraft.entity.mob.MobEntity;
import net.minecraft.entity.player.PlayerEntity;
import net.minecraft.item.Item;
import net.minecraft.item.Items;
import net.minecraft.registry.Registries;
import net.minecraft.registry.entry.RegistryEntry;
import net.minecraft.server.network.ServerPlayerEntity;
import net.minecraft.server.world.ServerWorld;
import net.minecraft.test.AfterBatch;
import net.minecraft.test.BeforeBatch;
import net.minecraft.test.GameTest;
import net.minecraft.test.TestContext;
import net.minecraft.util.math.BlockPos;
import net.minecraft.util.math.Box;
import net.minecraft.util.math.ChunkPos;
import net.minecraft.util.math.Vec3d;
import net.minecraft.world.GameRules;
import net.minecraft.world.level.ServerWorldProperties;

/**
 * Mutants of every kind, tier, element and archetype, AI on, fighting grafted players in walled arenas while the world
 * ticks: rounds of a few mutants and one or two players who hit back. Every tick, every creature in the arena must be
 * sound (a finite place, a sane speed, health within its maximum, freezing within the documented caps) and every
 * mutant must carry a mutation its type may have; champions stay in the locator's index near where they are, and
 * leave it when they go. With mob griefing off, once a round's hazards have run out the arena must be exactly as it
 * was, with nothing hazards are made of left lying about.
 */
public final class MutantArenaFuzzGameTest implements FabricGameTest {
	private static final org.slf4j.Logger LOGGER = org.slf4j.LoggerFactory.getLogger("Evoluta arena fuzz");
	private static final String BATCH = "evoluta_mutant_arena";
	private static final int PLACE = Block.NOTIFY_LISTENERS | Block.FORCE_STATE;
	private static final int ROUND = 100;
	private static final int TICKS = Math.max(200, ToolFuzz.CYCLES / 10);
	private static final Item[] LEAKS = {Items.MAGMA_BLOCK, Items.SLIME_BLOCK, Items.PACKED_ICE};
	private static boolean griefing;
	/** Blows by mutants on players, per arena (players and mutants of one arena stay inside it). */
	private static final int[] BLOWS = new int[4];

	static {
		net.fabricmc.fabric.api.entity.event.v1.ServerLivingEntityEvents.AFTER_DAMAGE.register((entity, source, base, taken, blocked) -> {
			if (entity instanceof PlayerEntity && source.getAttacker() instanceof MobEntity mob && Mutations.get(mob) != null) {
				for (String tag : entity.getCommandTags()) {
					if (tag.startsWith("evoluta.arena.")) {
						BLOWS[Integer.parseInt(tag.substring(14))]++;
					}
				}
			}
		});
	}

	@BeforeBatch(batchId = BATCH)
	public void noGriefing(ServerWorld world) {
		GameRules.BooleanRule rule = world.getGameRules().get(GameRules.DO_MOB_GRIEFING);
		griefing = rule.get();
		rule.set(false, world.getServer());
		Mutants.discardAll(world);
	}

	@AfterBatch(batchId = BATCH)
	public void griefingBack(ServerWorld world) {
		world.getGameRules().get(GameRules.DO_MOB_GRIEFING).set(griefing, world.getServer());
	}

	@GameTest(templateName = EMPTY_STRUCTURE, batchId = BATCH, tickLimit = 20000)
	public void arena0(TestContext context) {
		arena(context, 0);
	}

	@GameTest(templateName = EMPTY_STRUCTURE, batchId = BATCH, tickLimit = 20000)
	public void arena1(TestContext context) {
		arena(context, 1);
	}

	@GameTest(templateName = EMPTY_STRUCTURE, batchId = BATCH, tickLimit = 20000)
	public void arena2(TestContext context) {
		arena(context, 2);
	}

	@GameTest(templateName = EMPTY_STRUCTURE, batchId = BATCH, tickLimit = 20000)
	public void arena3(TestContext context) {
		arena(context, 3);
	}

	/** One arena's state across its rounds. */
	private static final class Arena {
		final TestContext context;
		final int index;
		final ToolFuzz.Failures failures;
		final List<EntityType<?>> kinds;
		final Box box;
		final Map<BlockPos, BlockState> built = new HashMap<>();
		final List<ServerPlayerEntity> players = new ArrayList<>();
		final List<MobEntity> mutants = new ArrayList<>();
		int round = -1;
		String what = "";
		/** What happened, for the log: proof the rounds were fights and not standing about. */
		double damageTaken;
		int blows;
		int targeting;
		int waiting;
		int spawned;
		int killed;
		int mostHazards;

		Arena(TestContext context, int index) {
			this.context = context;
			this.index = index;
			this.failures = new ToolFuzz.Failures("mutant arena " + index);
			this.kinds = new ArrayList<>();
			for (RegistryEntry<EntityType<?>> entry : Registries.ENTITY_TYPE.iterateEntries(EvolutaTags.CAN_MUTATE)) {
				if (EvolutaTags.canMutate(entry.value())) {
					this.kinds.add(entry.value());
				}
			}
			this.box = new Box(context.getAbsolutePos(new BlockPos(0, 1, 0))).union(new Box(context.getAbsolutePos(new BlockPos(7, 7, 7))));
		}

		ServerWorld world() {
			return this.context.getWorld();
		}
	}

	private static void arena(TestContext context, int index) {
		Arena arena = new Arena(context, index);
		build(arena);
		context.runAtEveryTick(() -> tick(arena));
		context.runAtTick(TICKS + 1, () -> {
			endRound(arena);
			for (ServerPlayerEntity player : arena.players) {
				TestPlayers.remove(context, player);
			}
			LOGGER.info("arena {}: {} rounds over {} ticks, {} mutants ({} after a player at the end of their round), {} killed by the players, "
					+ "{} blows on the players for {} damage, {} mutant-ticks lying in wait, up to {} hazards standing in the arena", index, arena.round + 1,
					TICKS, arena.spawned, arena.targeting, arena.killed, BLOWS[index], Math.round(arena.damageTaken), arena.waiting, arena.mostHazards);
			arena.failures.throwIfAny();
			context.complete();
		});
	}

	/**
	 * A floor of stone and walls round a 6x6 floor, three blocks high, a few stones to walk round: glass walls under the
	 * sky for arenas 0 and 1; stone walls and a stone roof for 2 and 3, dark enough for an Apex to lie in wait.
	 */
	private static void build(Arena arena) {
		ServerWorld world = arena.world();
		SplittableRandom random = ToolFuzz.random("arena-build", arena.index);
		boolean dark = arena.index >= 2;
		for (BlockPos pos : BlockPos.iterate(0, 1, 0, 7, 7, 7)) {
			BlockState state;
			if (pos.getY() == 1) {
				state = random.nextInt(8) == 0 ? Blocks.GRASS_BLOCK.getDefaultState() : Blocks.STONE.getDefaultState();
			} else if (dark && pos.getY() == 5) {
				state = Blocks.STONE.getDefaultState();
			} else if ((pos.getX() == 0 || pos.getX() == 7 || pos.getZ() == 0 || pos.getZ() == 7) && pos.getY() <= 4) {
				state = dark ? Blocks.STONE.getDefaultState() : Blocks.GLASS.getDefaultState();
			} else if (pos.getY() == 2 && random.nextInt(14) == 0) {
				state = Blocks.COBBLESTONE.getDefaultState();
			} else {
				state = Blocks.AIR.getDefaultState();
			}
			BlockPos at = arena.context.getAbsolutePos(pos).toImmutable();
			world.setBlockState(at, state, PLACE);
		}
		for (BlockPos pos : BlockPos.iterate(0, 1, 0, 7, 7, 7)) {
			BlockPos at = arena.context.getAbsolutePos(pos).toImmutable();
			arena.built.put(at, world.getBlockState(at));
		}
	}

	private static void tick(Arena arena) {
		TestContext context = arena.context;
		long tick = context.getTick();
		if (tick > TICKS) {
			return;
		}
		if (tick % ROUND == 0) {
			if (arena.round >= 0) {
				endRound(arena);
			}
			arena.round++;
			startRound(arena);
		}
		ServerWorld world = arena.world();
		SplittableRandom random = ToolFuzz.random("arena-tick-" + arena.index, (int) tick);
		// the players live through it and hit back now and then, fully charged
		for (ServerPlayerEntity player : arena.players) {
			arena.damageTaken += player.getMaxHealth() - player.getHealth();
			player.setHealth(player.getMaxHealth());
			if (tick % 10 == arena.index && !arena.mutants.isEmpty()) {
				MobEntity near = ToolFuzz.pick(random, arena.mutants);
				if (near.isAlive() && near.squaredDistanceTo(player) < 25) {
					setField(LivingEntity.class, player, "lastAttackedTicks", 100);
					player.attack(near);
				}
			}
		}
		// every creature sound, every mutant a mutation its kind may have
		WorldState state = EvolutaWorlds.of(world);
		HazardBlocks held = HazardBlocks.of(world);
		int inArena = 0;
		for (BlockPos pos : arena.built.keySet()) {
			inArena += held.holds(pos) ? 1 : 0;
		}
		arena.mostHazards = Math.max(arena.mostHazards, inArena);
		for (LivingEntity living : world.getEntitiesByClass(LivingEntity.class, arena.box.expand(1), entity -> true)) {
			check(arena, living, tick);
		}
		for (MobEntity mob : arena.mutants) {
			if (mob.isAlive() && !arena.box.expand(1).contains(mob.getPos())) {
				// one that got out (a spider over the wall) is still watched
				check(arena, mob, tick);
			}
		}
		for (MobEntity mob : arena.mutants) {
			if (!mob.isAlive() || mob.isRemoved()) {
				continue;
			}
			arena.waiting += com.nyr.evoluta.common.tactics.Ambush.isWaiting(mob) ? 1 : 0;
			MutationData data = Mutations.get(mob);
			arena.failures.check(data != null, arena.round, () -> arena.what + ": a mutant " + name(mob) + " lost its mutation at tick " + tick);
			if (data != null) {
				arena.failures.check(EvolutaTags.canBe(mob.getType(), data.getArchetype()), arena.round, () -> arena.what + ": " + name(mob)
						+ " is a " + data.getArchetype() + ", which its kind may not be");
				if (data.isChampion() && tick % 10 == 5) {
					ChunkPos now = mob.getChunkPos();
					boolean near = false;
					for (int dx = -1; dx <= 1 && !near; dx++) {
						for (int dz = -1; dz <= 1 && !near; dz++) {
							near = state.champions.isIn(mob, now.x + dx, now.z + dz);
						}
					}
					boolean indexed = near;
					arena.failures.check(indexed, arena.round, () -> arena.what + ": the champion " + name(mob) + " at " + mob.getBlockPos()
							+ " is not in the locator's index near its chunk at tick " + tick);
				}
			}
		}
	}

	private static void check(Arena arena, LivingEntity living, long tick) {
		Vec3d pos = living.getPos();
		Vec3d velocity = living.getVelocity();
		String who = name(living);
		arena.failures.check(Double.isFinite(pos.x) && Double.isFinite(pos.y) && Double.isFinite(pos.z), arena.round, () -> arena.what + ": " + who
				+ " is at " + pos + " at tick " + tick);
		arena.failures.check(Double.isFinite(velocity.x) && Double.isFinite(velocity.y) && Double.isFinite(velocity.z) && velocity.length() < 20, arena.round,
				() -> arena.what + ": " + who + " moves at " + velocity + " at tick " + tick);
		float health = living.getHealth();
		arena.failures.check(Float.isFinite(health) && health <= living.getMaxHealth() + 1.0E-3F, arena.round, () -> arena.what + ": " + who + " has "
				+ health + " of " + living.getMaxHealth() + " health at tick " + tick);
		int frozen = living.getFrozenTicks();
		arena.failures.check(frozen >= 0 && frozen <= 200, arena.round, () -> arena.what + ": " + who + " is frozen " + frozen + " ticks at tick " + tick);
	}

	/** New mutants and players: 3 to 6 mutants of any kind that may mutate, any tier, element and archetype their kind allows. */
	private static void startRound(Arena arena) {
		ServerWorld world = arena.world();
		TestContext context = arena.context;
		SplittableRandom random = ToolFuzz.random("arena-" + arena.index, arena.round);
		world.getRandom().setSeed(random.nextLong());
		List<String> cast = new ArrayList<>();
		for (int i = 1 + random.nextInt(2); i > 0; i--) {
			ServerPlayerEntity player = TestPlayers.survival(context, new BlockPos(1 + random.nextInt(6), 2, 1 + random.nextInt(6)), (float) random.nextDouble() * 360);
			TestPlayers.vulnerable(player);
			player.addCommandTag("evoluta.arena." + arena.index);
			// health enough that no single blow ends the round, topped up every tick
			player.getAttributeInstance(net.minecraft.entity.attribute.EntityAttributes.GENERIC_MAX_HEALTH).setBaseValue(1000);
			player.setHealth(1000);
			for (EquipmentSlot slot : new EquipmentSlot[]{EquipmentSlot.HEAD, EquipmentSlot.CHEST, EquipmentSlot.LEGS, EquipmentSlot.FEET}) {
				if (random.nextInt(3) == 0) {
					player.equipStack(slot, armour(random, world, slot));
				}
			}
			player.equipStack(EquipmentSlot.MAINHAND, ToolFuzz.grafted(ToolFuzz.tool(random, world, ToolFuzz.pick(random, ToolFuzz.items(Grafting.WEAPONS))),
					ToolFuzz.grafts(random, Grafting.MAX_GRAFTS)));
			arena.players.add(player);
			cast.add("player with " + ToolFuzz.describe(player.getMainHandStack()));
		}
		for (int i = 3 + random.nextInt(4); i > 0; i--) {
			EntityType<?> kind = ToolFuzz.pick(random, arena.kinds);
			if (!(kind.create(world) instanceof MobEntity mob)) {
				continue;
			}
			BlockPos at = context.getAbsolutePos(new BlockPos(1 + random.nextInt(6), 2, 1 + random.nextInt(6)));
			mob.refreshPositionAndAngles(at.getX() + 0.5, at.getY(), at.getZ() + 0.5, (float) random.nextDouble() * 360, 0);
			// vanilla's own gear (a skeleton's bow) without a roll: commands are not a spawn reason Evoluta mutates
			mob.initialize(world, world.getLocalDifficulty(at), SpawnReason.COMMAND, null);
			List<Archetype> archetypes = new ArrayList<>();
			for (Archetype archetype : Archetype.values()) {
				if (archetype != Archetype.ALPHA && EvolutaTags.canBe(kind, archetype)) {
					archetypes.add(archetype);
				}
			}
			MutationData data = MutationData.of(ToolFuzz.pick(random, Tier.values()),
					ToolFuzz.pick(random, new Element[]{Element.NONE, Element.IGNITED, Element.PERMAFROST, Element.TOXIC}),
					archetypes.isEmpty() ? Archetype.NONE : ToolFuzz.pick(random, archetypes));
			Mutations.apply(mob, data, EvolutaConfig.get());
			Mutations.equipLoadout(mob, data);
			MutantGear.graftOnce(mob, data, mob.getRandom());
			if (!world.spawnEntity(mob)) {
				continue;
			}
			arena.mutants.add(mob);
			arena.spawned++;
			cast.add(data.getTier().key() + " " + data.getElement().key() + " " + data.getArchetype().name().toLowerCase() + " " + name(mob));
		}
		arena.what = "arena " + arena.index + " round " + arena.round + " " + cast;
	}

	private static net.minecraft.item.ItemStack armour(SplittableRandom random, ServerWorld world, EquipmentSlot slot) {
		List<Item> pieces = new ArrayList<>();
		for (Item item : ToolFuzz.items(Grafting.ARMOR)) {
			if (item instanceof net.minecraft.item.Equipment equipment && equipment.getSlotType() == slot) {
				pieces.add(item);
			}
		}
		return ToolFuzz.grafted(ToolFuzz.tool(random, world, ToolFuzz.pick(random, pieces)), ToolFuzz.grafts(random, Grafting.MAX_GRAFTS));
	}

	/**
	 * The round is over: every mutant goes (and leaves the index), time moves on past every hazard, and the arena must be
	 * exactly as it was, with no hazard material lying about.
	 */
	private static void endRound(Arena arena) {
		ServerWorld world = arena.world();
		WorldState state = EvolutaWorlds.of(world);
		for (MobEntity mob : arena.mutants) {
			arena.killed += mob.isDead() ? 1 : 0;
			arena.targeting += mob.getTarget() instanceof PlayerEntity ? 1 : 0;
			mob.discard();
		}
		for (MobEntity mob : arena.mutants) {
			arena.failures.check(!state.champions.contains(mob), arena.round, () -> arena.what + ": " + name(mob) + " is still in the index after it left");
		}
		for (Item leak : LEAKS) {
			for (ItemEntity item : world.getEntitiesByClass(ItemEntity.class, arena.box.expand(1), entity -> entity.getStack().isOf(leak))) {
				arena.failures.add(arena.round, arena.what + ": " + item.getStack() + " lies in the arena");
			}
		}
		// only this arena's leftovers: its neighbours are mid-round in the same tick
		for (Entity entity : world.getEntitiesByClass(Entity.class, arena.box.expand(1), entity -> !(entity instanceof LivingEntity))) {
			entity.discard();
		}
		for (ServerPlayerEntity player : arena.players) {
			TestPlayers.remove(arena.context, player);
		}
		arena.players.clear();
		arena.mutants.clear();
		// every hazard runs out: the clock moves on for the moment it takes, then back, since a game test counts its
		// ticks by the world's clock
		ServerWorldProperties clock = (ServerWorldProperties) world.getLevelProperties();
		long clockNow = clock.getTime();
		clock.setTime(clockNow + 400);
		HazardBlocks.tick(world);
		clock.setTime(clockNow);
		HazardBlocks hazards = HazardBlocks.of(world);
		for (Map.Entry<BlockPos, BlockState> entry : arena.built.entrySet()) {
			BlockState now = world.getBlockState(entry.getKey());
			arena.failures.check(now.equals(entry.getValue()) && !hazards.holds(entry.getKey()), arena.round, () -> arena.what + ": after the round "
					+ entry.getKey() + " is " + ToolFuzz.describe(now) + ", was " + ToolFuzz.describe(entry.getValue()));
			if (!now.equals(entry.getValue()) && !now.isOf(HazardBlockTypes.MUTANT_FIRE)) {
				// put it back so the next round starts from the arena as built
				world.setBlockState(entry.getKey(), entry.getValue(), PLACE);
			}
			if (now.isOf(HazardBlockTypes.MUTANT_FIRE)) {
				world.setBlockState(entry.getKey(), entry.getValue(), PLACE);
			}
		}
		arena.failures.ran();
	}

	private static String name(Entity entity) {
		return Registries.ENTITY_TYPE.getId(entity.getType()).getPath() + "#" + entity.getId();
	}

	private static void setField(Class<?> owner, Object target, String name, Object value) {
		try {
			Field field = owner.getDeclaredField(name);
			field.setAccessible(true);
			field.set(target, value);
		} catch (ReflectiveOperationException e) {
			throw new IllegalStateException("dev runtime without Yarn names?", e);
		}
	}
}
