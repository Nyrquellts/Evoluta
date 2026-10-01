package com.nyr.evoluta.gametest;

import com.nyr.evoluta.common.champion.LocatorSync;
import com.nyr.evoluta.common.config.EvolutaConfig;
import com.nyr.evoluta.common.mutation.Archetype;
import com.nyr.evoluta.common.mutation.Element;
import com.nyr.evoluta.common.mutation.MutationData;
import com.nyr.evoluta.common.mutation.Mutations;
import com.nyr.evoluta.common.mutation.Tier;
import com.nyr.evoluta.common.world.EvolutaWorlds;
import com.nyr.evoluta.common.world.WorldState;
import java.util.Optional;
import net.fabricmc.fabric.api.gametest.v1.FabricGameTest;
import net.minecraft.entity.EntityType;
import net.minecraft.entity.SpawnReason;
import net.minecraft.entity.mob.ZombieEntity;
import net.minecraft.entity.player.PlayerEntity;
import net.minecraft.item.ItemStack;
import net.minecraft.item.Items;
import net.minecraft.server.world.ServerWorld;
import net.minecraft.test.AfterBatch;
import net.minecraft.test.BeforeBatch;
import net.minecraft.test.GameTest;
import net.minecraft.test.TestContext;
import net.minecraft.util.Hand;
import net.minecraft.util.math.BlockPos;
import net.minecraft.util.math.ChunkPos;

/** Step 4: the chunk index of champions and what the locator reads from it. */
public final class ChampionGameTest implements FabricGameTest {
	private static final String CAP = "evoluta_champion_cap";
	private static final String LOCATOR = "evoluta_locator";
	private static EvolutaConfig before = EvolutaConfig.DEFAULTS;

	@GameTest(templateName = EMPTY_STRUCTURE, tickLimit = 60)
	public void championsAreIndexedUntilTheyAreGone(TestContext context) {
		WorldState state = EvolutaWorlds.of(context.getWorld());
		ZombieEntity elite = Mutants.still(context, EntityType.ZOMBIE, 1, 1, Tier.ELITE, Element.NONE, Archetype.NONE);
		ZombieEntity apex = Mutants.still(context, EntityType.ZOMBIE, 3, 1, Tier.APEX, Element.NONE, Archetype.NONE);
		ZombieEntity evolved = Mutants.still(context, EntityType.ZOMBIE, 5, 1, Tier.EVOLVED, Element.NONE, Archetype.NONE);
		context.assertTrue(state.champions.contains(elite) && state.champions.contains(apex), "Elite and Apex mutants are not indexed");
		context.assertFalse(state.champions.contains(evolved), "an Evolved mutant is indexed as a champion");

		elite.discard();
		context.assertFalse(state.champions.contains(elite), "a discarded champion is still indexed");

		Mutations.apply(apex, MutationData.of(Tier.EVOLVED, Element.NONE, Archetype.NONE), EvolutaConfig.get());
		context.assertFalse(state.champions.contains(apex), "a champion demoted to Evolved is still indexed");
		Mutations.apply(evolved, MutationData.of(Tier.APEX, Element.NONE, Archetype.NONE), EvolutaConfig.get());
		context.assertTrue(state.champions.contains(evolved), "a mutant promoted to Apex is not indexed");

		evolved.kill();
		// a dead mob stays loaded for its death animation (20 ticks), then unloads
		context.runAtTick(40, () -> {
			context.assertFalse(state.champions.contains(evolved), "a dead champion is still indexed after it unloaded");
			context.complete();
		});
	}

	@GameTest(templateName = EMPTY_STRUCTURE, tickLimit = 60)
	public void championsFollowTheirChunk(TestContext context) {
		WorldState state = EvolutaWorlds.of(context.getWorld());
		ZombieEntity champion = Mutants.still(context, EntityType.ZOMBIE, 1, 1, Tier.ELITE, Element.NONE, Archetype.NONE);
		ChunkPos from = champion.getChunkPos();
		// 20 blocks: always the next chunk or the one after, which stay loaded around the test's own force-loaded
		// chunk; three chunks off (48 blocks) the champion stayed loaded only when another test happened to sit there
		champion.requestTeleport(champion.getX() + 20, champion.getY(), champion.getZ());
		context.assertFalse(from.equals(champion.getChunkPos()), "setup: the champion did not change chunk");

		context.runAtTick(15, () -> {
			ChunkPos now = champion.getChunkPos();
			context.assertTrue(state.champions.isIn(champion, now.x, now.z), "the index did not follow the champion to its new chunk");
			context.assertFalse(state.champions.isIn(champion, from.x, from.z), "the champion is still listed in its old chunk");
			champion.discard();
			context.complete();
		});
	}

	@BeforeBatch(batchId = CAP)
	public void onlyElites(ServerWorld world) {
		Mutants.discardAll(world);
		before = EvolutaConfig.get();
		EvolutaConfig.use(EvolutaConfig.DEFAULTS.toBuilder().mutationChance(1).localDifficultyBonus(0).nightMultiplier(1)
				.tierWeights(0, 1, 0).build());
	}

	@AfterBatch(batchId = CAP)
	public void restore(ServerWorld world) {
		EvolutaConfig.use(before);
	}

	/** With the default cap of one champion per chunk, the second spawn in a chunk that holds one caps at Evolved. */
	@GameTest(templateName = EMPTY_STRUCTURE, batchId = CAP)
	public void aChunkHoldsOneChampionAtSpawn(TestContext context) {
		ZombieEntity first = spawnInWorld(context, new BlockPos(3, 2, 3));
		ZombieEntity second = spawnInWorld(context, new BlockPos(3, 2, 3));
		ZombieEntity elsewhere = spawnInWorld(context, new BlockPos(3 + 64, 2, 3));

		context.assertTrue(Mutations.get(first).getTier() == Tier.ELITE, "first spawn in the chunk rolled " + Mutations.get(first));
		context.assertTrue(Mutations.get(second).getTier() == Tier.EVOLVED, "second spawn in the same chunk rolled " + Mutations.get(second));
		context.assertTrue(Mutations.get(elsewhere).getTier() == Tier.ELITE, "a spawn 4 chunks away rolled " + Mutations.get(elsewhere));
		first.discard();
		second.discard();
		elsewhere.discard();
		context.complete();
	}

	/** Mutants left by earlier batches would compete for "nearest": start from a world without any. */
	@BeforeBatch(batchId = LOCATOR)
	public void noOtherMutants(ServerWorld world) {
		Mutants.discardAll(world);
	}

	@GameTest(templateName = EMPTY_STRUCTURE, batchId = LOCATOR, tickLimit = 60)
	public void theLocatorPointsAtTheNearestLivingChampion(TestContext context) {
		WorldState state = EvolutaWorlds.of(context.getWorld());
		PlayerEntity player = Mutants.target(context, 1, 1, 0);
		context.assertFalse(LocatorSync.holdsLocator(player), "an empty-handed player holds a locator");
		player.setStackInHand(Hand.OFF_HAND, new ItemStack(Items.RECOVERY_COMPASS));
		context.assertTrue(LocatorSync.holdsLocator(player), "a tagged item in the off hand is not a locator");

		ZombieEntity near = Mutants.still(context, EntityType.ZOMBIE, 5, 4, Tier.ELITE, Element.NONE, Archetype.NONE);
		ZombieEntity far = Mutants.still(context, EntityType.ZOMBIE, 1, 30, Tier.APEX, Element.NONE, Archetype.NONE);
		Mutants.still(context, EntityType.ZOMBIE, 2, 2, Tier.EVOLVED, Element.NONE, Archetype.NONE);

		context.assertTrue(LocatorSync.target(state, player).equals(Optional.of(near.getBlockPos())),
				"expected the Elite 5 blocks away, got " + LocatorSync.target(state, player));
		near.kill();
		context.assertTrue(LocatorSync.target(state, player).equals(Optional.of(far.getBlockPos())),
				"a dead champion is still the target: " + LocatorSync.target(state, player));
		far.requestTeleport(far.getX() + 400, far.getY(), far.getZ());
		context.runAtTick(15, () -> {
			context.assertTrue(LocatorSync.target(state, player).isEmpty(), "a champion 400 blocks away is in range: " + LocatorSync.target(state, player));
			far.discard();
			context.complete();
		});
	}

	private static ZombieEntity spawnInWorld(TestContext context, BlockPos relative) {
		ServerWorld world = context.getWorld();
		ZombieEntity zombie = EntityType.ZOMBIE.create(world);
		BlockPos pos = context.getAbsolutePos(relative);
		zombie.refreshPositionAndAngles(pos, 0, 0);
		zombie.initialize(world, world.getLocalDifficulty(pos), SpawnReason.NATURAL, null);
		zombie.setAiDisabled(true);
		world.spawnEntity(zombie);
		return zombie;
	}
}
