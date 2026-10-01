package com.nyr.evoluta.gametest;

import com.nyr.evoluta.common.combat.HazardBlockTypes;
import com.nyr.evoluta.common.config.EvolutaConfig;
import com.nyr.evoluta.common.mutation.Archetype;
import com.nyr.evoluta.common.mutation.Element;
import com.nyr.evoluta.common.mutation.EvolutaAttachments;
import com.nyr.evoluta.common.mutation.MutationData;
import com.nyr.evoluta.common.mutation.Mutations;
import com.nyr.evoluta.common.mutation.Tier;
import com.nyr.evoluta.common.tactics.Stalking;
import com.nyr.evoluta.common.tactics.Tactics;
import java.util.List;
import net.fabricmc.fabric.api.gametest.v1.FabricGameTest;
import net.minecraft.entity.EntityType;
import net.minecraft.entity.EquipmentSlot;
import net.minecraft.entity.mob.MobEntity;
import net.minecraft.entity.mob.SkeletonEntity;
import net.minecraft.entity.mob.ZombieEntity;
import net.minecraft.entity.passive.PigEntity;
import net.minecraft.item.ItemStack;
import net.minecraft.item.Items;
import net.minecraft.server.network.ServerPlayerEntity;
import net.minecraft.test.GameTest;
import net.minecraft.test.TestContext;
import net.minecraft.util.math.BlockPos;

/**
 * The tactical tick, driven by the real scheduler with real in-world players. Each batch runs alone, so a player
 * from one test is never within range of another test's mutants.
 */
public final class TacticsGameTest implements FabricGameTest {
	private static final String STALK = "evoluta_stalk";
	private static final String HAZARDS = "evoluta_hazards";

	/**
	 * A Stalker hunts by its target's gaze: it sprints while the target's back is turned, keeps its pace (weaving) while
	 * the target looks at it rather than hanging back, and forgets the hunt beyond {@link Stalking#RANGE} blocks and
	 * once no player is within {@link Tactics#ACTIVE_RANGE}. The checks sit 15 ticks apart: every mutant is revisited
	 * within 10.
	 */
	@GameTest(templateName = EMPTY_STRUCTURE, batchId = STALK, tickLimit = 200)
	public void stalkersHuntByTheirTargetsGaze(TestContext context) {
		Mutants.floor(context);
		ZombieEntity stalker = Mutants.still(context, EntityType.ZOMBIE, 1, 7, Tier.ELITE, Element.NONE, Archetype.STALKER);
		// yaw 180 faces -z: the Stalker at z = 7 is 6 blocks behind a player at z = 1, outside its lunge reach
		ServerPlayerEntity player = TestPlayers.survival(context, new BlockPos(1, 2, 1), 180);
		stalker.setTarget(player);

		context.runAtTick(15, () -> {
			assertPace(context, stalker, Stalking.BURST, "the target's back is turned");
			player.setYaw(0);
		});
		context.runAtTick(30, () -> {
			assertPace(context, stalker, 0, "the target looks straight at it");
			// 70 degrees off: in view, outside the 60-degree cone it weaves in (exactly side-on is the boundary with a
			// turned back, so the test stays clear of it)
			player.setYaw(70);
		});
		context.runAtTick(45, () -> {
			assertPace(context, stalker, 0, "the target sees it from the corner of its eye");
			TestPlayers.moveTo(context, player, new BlockPos(1, 2, -13), 180);
		});
		context.runAtTick(60, () -> {
			assertPace(context, stalker, 0, "the target is 20 blocks away, beyond stalking range");
			TestPlayers.moveTo(context, player, new BlockPos(1, 2, 1), 180);
		});
		context.runAtTick(75, () -> {
			assertPace(context, stalker, Stalking.BURST, "the target came back and turned away");
			TestPlayers.moveTo(context, player, new BlockPos(1, 2, 57), 180);
		});
		context.runAtTick(90, () -> {
			assertPace(context, stalker, 0, "no player is within 32 blocks");
			TestPlayers.remove(context, player);
			context.complete();
		});
	}

	/** Close behind a turned back, a melee Stalker leaps at its target; an archer Stalker keeps its distance. */
	@GameTest(templateName = EMPTY_STRUCTURE, batchId = STALK, tickLimit = 200)
	public void meleeStalkersLungeAtATurnedBack(TestContext context) {
		Mutants.floor(context);
		// spawnMob strips the goals, so only the lunge moves this zombie; its AI stays on, or it could not move at all
		ZombieEntity melee = context.spawnMob(EntityType.ZOMBIE, 1, 2, 5);
		Mutations.apply(melee, MutationData.of(Tier.ELITE, Element.NONE, Archetype.STALKER), EvolutaConfig.get());
		SkeletonEntity archer = context.spawnMob(EntityType.SKELETON, 4, 2, 5);
		archer.equipStack(EquipmentSlot.MAINHAND, new ItemStack(Items.BOW));
		Mutations.apply(archer, MutationData.of(Tier.ELITE, Element.NONE, Archetype.STALKER), EvolutaConfig.get());
		// yaw 180 faces -z: both Stalkers are behind the player, 3 and 4.2 blocks away
		ServerPlayerEntity player = TestPlayers.survival(context, new BlockPos(1, 2, 2), 180);
		melee.setTarget(player);
		archer.setTarget(player);
		double start = melee.squaredDistanceTo(player);

		context.runAtTick(20, () -> {
			context.assertTrue(melee.getAttached(EvolutaAttachments.STALKER_LAST_LUNGE) != null, "the melee Stalker did not lunge at a turned back 3 blocks away");
			context.assertTrue(melee.squaredDistanceTo(player) < start - 2, "the lunge did not carry the Stalker toward its target: "
					+ Math.sqrt(start) + " -> " + Math.sqrt(melee.squaredDistanceTo(player)) + " blocks");
			context.assertTrue(archer.getAttached(EvolutaAttachments.STALKER_LAST_LUNGE) == null, "an archer Stalker lunged instead of shooting");
			TestPlayers.remove(context, player);
			context.complete();
		});
	}

	@GameTest(templateName = EMPTY_STRUCTURE, batchId = HAZARDS)
	public void permafrostAuraChillsPlayersInReach(TestContext context) {
		Mutants.floor(context);
		Mutants.still(context, EntityType.ZOMBIE, 3, 3, Tier.ELITE, Element.PERMAFROST, Archetype.NONE);
		ServerPlayerEntity near = TestPlayers.survival(context, new BlockPos(3, 2, 5), 0);
		ServerPlayerEntity leather = TestPlayers.survival(context, new BlockPos(5, 2, 3), 0);
		leather.equipStack(EquipmentSlot.FEET, new ItemStack(Items.LEATHER_BOOTS));
		ServerPlayerEntity far = TestPlayers.survival(context, new BlockPos(3, 2, 7), 0);

		context.runAtTick(25, () -> {
			context.assertTrue(near.getFrozenTicks() >= Tactics.CHILL_AURA_TICKS, "a player 2 blocks away did not start to freeze: " + near.getFrozenTicks());
			context.assertTrue(leather.getFrozenTicks() == 0, "leather boots did not keep the aura out: " + leather.getFrozenTicks());
			context.assertTrue(far.getFrozenTicks() == 0, "a player 4 blocks away froze: " + far.getFrozenTicks());
			for (ServerPlayerEntity player : List.of(near, leather, far)) {
				TestPlayers.remove(context, player);
			}
			context.complete();
		});
	}

	/** An Apex Ignited death sets the ground burning 2.5 blocks out: what stands inside burns, what stands beyond does not. */
	@GameTest(templateName = EMPTY_STRUCTURE, batchId = HAZARDS)
	public void anIgnitedDeathSetsTheGroundBurning(TestContext context) {
		Mutants.floor(context);
		ZombieEntity ignited = Mutants.still(context, EntityType.ZOMBIE, 3, 3, Tier.APEX, Element.IGNITED, Archetype.NONE);
		// spawnMob strips goals but leaves the AI on, so these pigs touch the blocks they stand in
		PigEntity inside = context.spawnMob(EntityType.PIG, 3, 2, 4);
		PigEntity outside = context.spawnMob(EntityType.PIG, 3, 2, 7);
		ignited.kill();

		context.runAtTick(15, () -> {
			context.assertTrue(context.getBlockState(new BlockPos(3, 2, 3)).isOf(HazardBlockTypes.MUTANT_FIRE), "no fire where the Apex died");
			context.assertTrue(inside.isOnFire(), "a pig standing in an Apex death's fire is not burning");
			context.assertFalse(outside.isOnFire(), "a pig 4 blocks from a 2.5-block fire is burning");
			context.complete();
		});
	}

	@GameTest(templateName = EMPTY_STRUCTURE, batchId = HAZARDS, tickLimit = 200)
	public void toxicMutantsLeaveATrailWhileChasing(TestContext context) {
		Mutants.floor(context);
		// spawnEntity, not spawnMob: spawnMob strips a mob's AI goals, and this zombie has to chase
		ZombieEntity toxic = context.spawnEntity(EntityType.ZOMBIE, 1, 2, 1);
		Mutations.apply(toxic, MutationData.of(Tier.EVOLVED, Element.TOXIC, Archetype.NONE), EvolutaConfig.get());
		ServerPlayerEntity player = TestPlayers.survival(context, new BlockPos(6, 2, 6), 0);
		toxic.setTarget(player);

		boolean[] trail = {false};
		for (int tick = 1; tick <= 80; tick++) {
			context.runAtTick(tick, () -> {
				for (int x = 0; x < 8 && !trail[0]; x++) {
					for (int z = 0; z < 8 && !trail[0]; z++) {
						trail[0] = context.getBlockState(new BlockPos(x, 2, z)).isOf(HazardBlockTypes.BLIGHT);
					}
				}
			});
		}
		context.runAtTick(80, () -> {
			context.assertTrue(trail[0], "a Toxic zombie chased for 4 seconds without leaving blight behind");
			TestPlayers.remove(context, player);
			context.complete();
		});
	}

	private static void assertPace(TestContext context, MobEntity stalker, double expected, String when) {
		double pace = Stalking.pace(stalker);
		context.assertTrue(pace == expected, "Stalker pace " + pace + " when " + when + ", expected " + expected);
	}
}
