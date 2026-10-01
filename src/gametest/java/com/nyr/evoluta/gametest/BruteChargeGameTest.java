package com.nyr.evoluta.gametest;

import com.nyr.evoluta.common.config.EvolutaConfig;
import com.nyr.evoluta.common.mutation.Archetype;
import com.nyr.evoluta.common.mutation.Element;
import com.nyr.evoluta.common.mutation.EvolutaAttachments;
import com.nyr.evoluta.common.mutation.MutationData;
import com.nyr.evoluta.common.mutation.Mutations;
import com.nyr.evoluta.common.mutation.Tier;
import com.nyr.evoluta.common.tactics.BruteCharge;
import net.fabricmc.fabric.api.gametest.v1.FabricGameTest;
import net.minecraft.entity.EntityType;
import net.minecraft.entity.mob.ZombieEntity;
import net.minecraft.server.network.ServerPlayerEntity;
import net.minecraft.test.GameTest;
import net.minecraft.test.TestContext;
import net.minecraft.util.math.BlockPos;

/**
 * Elite and Apex Brutes charge: plant and growl, rush in a straight line, slam. The Brute comes from spawnMob, which
 * strips its goals, so the charge is the only thing that can move it or hurt the player. The player is a real
 * in-world survival player, as the tactical tick only runs with one near.
 */
public final class BruteChargeGameTest implements FabricGameTest {
	@GameTest(templateName = EMPTY_STRUCTURE, batchId = "evoluta_brute_charge", tickLimit = 100)
	public void aBruteChampionPlantsThenRushesAndSlams(TestContext context) {
		Mutants.floor(context);
		// yaw 0 faces +z: the Brute at z = 7 stands seven blocks in front of the player
		ServerPlayerEntity player = TestPlayers.survival(context, new BlockPos(1, 2, 0), 0);
		TestPlayers.vulnerable(player);
		ZombieEntity brute = context.spawnMob(EntityType.ZOMBIE, 1, 2, 7);
		Mutations.apply(brute, MutationData.of(Tier.ELITE, Element.NONE, Archetype.BRUTE), EvolutaConfig.get());
		brute.setTarget(player);
		double startZ = brute.getZ();
		boolean[] planted = {false};
		for (int tick = 1; tick <= 25; tick++) {
			context.runAtTick(tick, () -> {
				BruteCharge.State charge = brute.getAttached(EvolutaAttachments.BRUTE_CHARGE);
				planted[0] |= charge != null && charge.phase() == BruteCharge.Phase.PLANTED;
			});
		}
		context.runAtTick(70, () -> {
			try {
				BruteCharge.State charge = brute.getAttached(EvolutaAttachments.BRUTE_CHARGE);
				context.assertTrue(planted[0], "the Brute charged without planting first");
				context.assertTrue(charge != null && charge.phase() == BruteCharge.Phase.LANDED, "the charge ended as " + charge);
				context.assertTrue(startZ - brute.getZ() > 4.0, "the Brute rushed only " + (startZ - brute.getZ()) + " blocks toward the player");
				context.assertTrue(player.getHealth() < player.getMaxHealth(), "the slam did not hurt the player");
				context.complete();
			} finally {
				TestPlayers.remove(context, player);
			}
		});
	}

	/** Brutes below Elite fight as they always did: no plant, no rush. */
	@GameTest(templateName = EMPTY_STRUCTURE, batchId = "evoluta_brute_charge", tickLimit = 100)
	public void anEvolvedBruteDoesNotCharge(TestContext context) {
		Mutants.floor(context);
		ServerPlayerEntity player = TestPlayers.survival(context, new BlockPos(1, 2, 0), 0);
		ZombieEntity brute = context.spawnMob(EntityType.ZOMBIE, 1, 2, 7);
		Mutations.apply(brute, MutationData.of(Tier.EVOLVED, Element.NONE, Archetype.BRUTE), EvolutaConfig.get());
		brute.setTarget(player);
		double startZ = brute.getZ();
		context.runAtTick(40, () -> {
			try {
				context.assertFalse(brute.hasAttached(EvolutaAttachments.BRUTE_CHARGE), "an Evolved Brute began a charge");
				context.assertTrue(Math.abs(startZ - brute.getZ()) < 0.5, "an Evolved Brute moved " + (startZ - brute.getZ()));
				context.complete();
			} finally {
				TestPlayers.remove(context, player);
			}
		});
	}
}
