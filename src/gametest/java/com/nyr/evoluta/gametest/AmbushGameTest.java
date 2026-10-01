package com.nyr.evoluta.gametest;

import com.nyr.evoluta.common.config.EvolutaConfig;
import com.nyr.evoluta.common.mutation.Archetype;
import com.nyr.evoluta.common.mutation.Element;
import com.nyr.evoluta.common.mutation.MutationData;
import com.nyr.evoluta.common.mutation.Mutations;
import com.nyr.evoluta.common.mutation.Tier;
import com.nyr.evoluta.common.tactics.Ambush;
import com.nyr.evoluta.common.tactics.Stalking;
import net.fabricmc.fabric.api.gametest.v1.FabricGameTest;
import net.minecraft.block.Blocks;
import net.minecraft.entity.EntityType;
import net.minecraft.entity.mob.ZombieEntity;
import net.minecraft.entity.player.PlayerEntity;
import net.minecraft.server.network.ServerPlayerEntity;
import net.minecraft.test.GameTest;
import net.minecraft.test.TestContext;
import net.minecraft.util.math.BlockPos;

/**
 * Apex hunters: lying in wait in the dark, waking up close, to a turned back or a hit, and hunting by gaze like a
 * Stalker. Each test runs in its own batch, so no other test's player can wake its Apex.
 */
public final class AmbushGameTest implements FabricGameTest {
	/** A sealed stone box over the whole area: dark inside (x and z 1-6, y 2-5) whatever the time of day. */
	private static void darkBox(TestContext context) {
		for (int x = 0; x < 8; x++) {
			for (int z = 0; z < 8; z++) {
				for (int y = 1; y <= 6; y++) {
					boolean shell = y == 1 || y == 6 || x == 0 || x == 7 || z == 0 || z == 7;
					if (shell) {
						context.setBlockState(x, y, z, Blocks.STONE);
					}
				}
			}
		}
	}

	@GameTest(templateName = EMPTY_STRUCTURE, batchId = "evoluta_ambush_close", tickLimit = 200)
	public void anApexLiesInWaitInTheDarkAndWakesUpClose(TestContext context) {
		darkBox(context);
		// with its AI, so it would take a target by sight if it could
		ZombieEntity apex = context.spawnEntity(EntityType.ZOMBIE, 4, 2, 4);
		Mutations.apply(apex, MutationData.of(Tier.APEX, Element.NONE, Archetype.NONE), EvolutaConfig.get());
		// outside the walls, facing the box: close enough to keep the tactical tick running, unseen and not waking it
		ServerPlayerEntity player = TestPlayers.survival(context, new BlockPos(4, 2, 12), 180);
		context.runAtTick(60, () -> {
			context.assertTrue(Ambush.isWaiting(apex), "an Apex in the dark with nobody in sight is not lying in wait");
			context.assertTrue(apex.getTarget() == null, "a waiting Apex took a target");
			TestPlayers.moveTo(context, player, new BlockPos(4, 2, 2), 0);
		});
		context.runAtTick(90, () -> {
			context.assertFalse(Ambush.isWaiting(apex), "a player two blocks away did not wake the Apex");
			context.assertTrue(apex.getTarget() == player, "the Apex woke without taking the player who woke it");
			TestPlayers.remove(context, player);
			context.complete();
		});
	}

	@GameTest(templateName = EMPTY_STRUCTURE, batchId = "evoluta_ambush_back", tickLimit = 200)
	public void aWaitingApexWakesWhenYourBackIsTurned(TestContext context) {
		darkBox(context);
		ZombieEntity apex = Mutants.still(context, EntityType.ZOMBIE, 6, 6, Tier.APEX, Element.NONE, Archetype.NONE);
		Ambush.lieInWait(apex);
		// yaw -45 looks along +x +z, straight at the Apex seven blocks off in the far corner
		ServerPlayerEntity player = TestPlayers.survival(context, new BlockPos(1, 2, 1), -45);
		context.runAtTick(30, () -> {
			context.assertTrue(Ambush.isWaiting(apex), "a player watching from seven blocks woke the Apex");
			player.setYaw(135);
		});
		context.runAtTick(60, () -> {
			context.assertFalse(Ambush.isWaiting(apex), "a turned back within sixteen blocks did not wake the Apex");
			context.assertTrue(apex.getTarget() == player, "the Apex woke without taking the player");
			TestPlayers.remove(context, player);
			context.complete();
		});
	}

	@GameTest(templateName = EMPTY_STRUCTURE, batchId = "evoluta_ambush_hurt")
	public void hurtingAWaitingApexWakesIt(TestContext context) {
		ZombieEntity apex = Mutants.still(context, EntityType.ZOMBIE, 4, 4, Tier.APEX, Element.IGNITED, Archetype.NONE);
		Ambush.lieInWait(apex);
		PlayerEntity player = Mutants.target(context, 4, 6, 180);
		apex.damage(context.getWorld().getDamageSources().playerAttack(player), 1.0F);
		context.assertFalse(Ambush.isWaiting(apex), "a hit did not wake the Apex");
		context.assertTrue(apex.getTarget() == player, "the Apex woke without turning on whoever hit it");
		context.complete();
	}

	/** Inside lunge reach, so the Brute's charge (from 4.5 blocks out) stays out of it. */
	@GameTest(templateName = EMPTY_STRUCTURE, batchId = "evoluta_ambush_hunt", tickLimit = 200)
	public void anApexBruteSprintsAtATurnedBack(TestContext context) {
		Mutants.floor(context);
		ZombieEntity brute = Mutants.still(context, EntityType.ZOMBIE, 1, 4, Tier.APEX, Element.NONE, Archetype.BRUTE);
		// yaw 180 faces -z: the Brute at z = 4 is three blocks behind the player
		ServerPlayerEntity player = TestPlayers.survival(context, new BlockPos(1, 2, 1), 180);
		brute.setTarget(player);
		context.runAtTick(15, () -> {
			context.assertTrue(Stalking.pace(brute) == Stalking.BURST, "an Apex Brute did not sprint at a turned back: pace " + Stalking.pace(brute));
			TestPlayers.remove(context, player);
			context.complete();
		});
	}

	/** Only a Stalker shies from being watched: an Apex Brute looked in the eye comes on. */
	@GameTest(templateName = EMPTY_STRUCTURE, batchId = "evoluta_ambush_hunt", tickLimit = 200)
	public void anApexBruteComesOnUnderYourGaze(TestContext context) {
		Mutants.floor(context);
		ZombieEntity brute = Mutants.still(context, EntityType.ZOMBIE, 5, 4, Tier.APEX, Element.NONE, Archetype.BRUTE);
		// yaw 0 faces +z: the Brute at z = 4 stands three blocks in front of the player
		ServerPlayerEntity player = TestPlayers.survival(context, new BlockPos(5, 2, 1), 0);
		brute.setTarget(player);
		context.runAtTick(15, () -> {
			context.assertTrue(Stalking.pace(brute) == Stalking.ADVANCE, "a watched Apex Brute did not come on: pace " + Stalking.pace(brute));
			TestPlayers.remove(context, player);
			context.complete();
		});
	}
}
