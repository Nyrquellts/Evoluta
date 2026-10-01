package com.nyr.evoluta.gametest;

import com.nyr.evoluta.common.combat.BlightBlock;
import com.nyr.evoluta.common.combat.FrostSpikesBlock;
import com.nyr.evoluta.common.combat.HazardBlockTypes;
import com.nyr.evoluta.common.combat.HazardBlocks;
import com.nyr.evoluta.common.config.EvolutaConfig;
import com.nyr.evoluta.common.mutation.Archetype;
import com.nyr.evoluta.common.mutation.Element;
import com.nyr.evoluta.common.mutation.MutationData;
import com.nyr.evoluta.common.mutation.Mutations;
import com.nyr.evoluta.common.mutation.Tier;
import net.fabricmc.fabric.api.gametest.v1.FabricGameTest;
import net.minecraft.block.BlockState;
import net.minecraft.block.Blocks;
import net.minecraft.entity.EntityType;
import net.minecraft.entity.ItemEntity;
import net.minecraft.entity.effect.StatusEffects;
import net.minecraft.entity.passive.PigEntity;
import net.minecraft.item.ItemStack;
import net.minecraft.item.Items;
import net.minecraft.server.world.ServerWorld;
import net.minecraft.test.GameTest;
import net.minecraft.test.TestContext;
import net.minecraft.util.math.BlockPos;
import net.minecraft.util.math.Vec3d;

/**
 * The blocks elemental mutants leave: each hurts what walks into it and spares mutants of its own element. The pigs
 * come from spawnMob (goals stripped, AI on): a mob with its AI off never touches the blocks it stands in.
 */
public final class HazardBlockGameTest implements FabricGameTest {
	private static final String BATCH = "evoluta_hazard_blocks";

	private static void hazard(TestContext context, BlockPos relative, BlockState state) {
		ServerWorld world = context.getWorld();
		context.assertTrue(HazardBlocks.of(world).place(world, context.getAbsolutePos(relative), state, 200), "setup: no hazard at " + relative);
	}

	private static PigEntity pig(TestContext context, BlockPos relative, Element element) {
		PigEntity pig = context.spawnMob(EntityType.PIG, relative);
		if (element != Element.NONE) {
			Mutations.apply(pig, MutationData.of(Tier.EVOLVED, element, Archetype.NONE), EvolutaConfig.get());
		}
		return pig;
	}

	@GameTest(templateName = EMPTY_STRUCTURE, batchId = BATCH, tickLimit = 60)
	public void mutantFireBurnsTheLivingButNotIgnitedNorLoot(TestContext context) {
		Mutants.floor(context);
		for (int x : new int[]{1, 3, 5}) {
			hazard(context, new BlockPos(x, 2, 3), HazardBlockTypes.MUTANT_FIRE.getDefaultState());
		}
		PigEntity plain = pig(context, new BlockPos(1, 2, 3), Element.NONE);
		PigEntity ignited = pig(context, new BlockPos(3, 2, 3), Element.IGNITED);
		Vec3d at = context.getAbsolute(new Vec3d(5.5, 2.0, 3.5));
		ItemEntity loot = new ItemEntity(context.getWorld(), at.x, at.y, at.z, new ItemStack(Items.ROTTEN_FLESH));
		context.getWorld().spawnEntity(loot);
		context.runAtTick(30, () -> {
			context.assertTrue(plain.isOnFire(), "a pig standing in mutant fire is not burning");
			context.assertFalse(ignited.isOnFire(), "an Ignited mutant caught fire in mutant fire");
			context.assertTrue(loot.isAlive(), "mutant fire burnt the loot lying in it");
			context.complete();
		});
	}

	@GameTest(templateName = EMPTY_STRUCTURE, batchId = BATCH, tickLimit = 60)
	public void frostSpikesCutWhatPushesThroughAndSparePermafrost(TestContext context) {
		Mutants.floor(context);
		hazard(context, new BlockPos(2, 2, 2), FrostSpikesBlock.of(Tier.APEX));
		hazard(context, new BlockPos(2, 2, 5), FrostSpikesBlock.of(Tier.APEX));
		PigEntity plain = pig(context, new BlockPos(2, 2, 2), Element.NONE);
		PigEntity permafrost = pig(context, new BlockPos(2, 2, 5), Element.PERMAFROST);
		// push both slowly through their spikes: the spikes only cut what moves
		for (int tick = 1; tick <= 12; tick++) {
			context.runAtTick(tick, () -> {
				plain.setVelocity(0.03, plain.getVelocity().y, 0.0);
				permafrost.setVelocity(0.03, permafrost.getVelocity().y, 0.0);
			});
		}
		context.runAtTick(20, () -> {
			context.assertTrue(plain.getHealth() < plain.getMaxHealth(), "frost spikes did not cut a pig pushing through them");
			context.assertTrue(permafrost.getHealth() == permafrost.getMaxHealth(), "frost spikes cut a Permafrost mutant: " + permafrost.getHealth());
			context.complete();
		});
	}

	@GameTest(templateName = EMPTY_STRUCTURE, batchId = BATCH, tickLimit = 60)
	public void blightPoisonsWhatWadesInAndSparesToxic(TestContext context) {
		Mutants.floor(context);
		hazard(context, new BlockPos(2, 2, 2), BlightBlock.of(Tier.EVOLVED));
		hazard(context, new BlockPos(5, 2, 2), BlightBlock.of(Tier.EVOLVED));
		PigEntity plain = pig(context, new BlockPos(2, 2, 2), Element.NONE);
		PigEntity toxic = pig(context, new BlockPos(5, 2, 2), Element.TOXIC);
		context.runAtTick(10, () -> {
			context.assertTrue(plain.hasStatusEffect(StatusEffects.POISON), "a pig standing in blight was not poisoned");
			context.assertFalse(toxic.hasStatusEffect(StatusEffects.POISON), "blight poisoned a Toxic mutant");
			context.complete();
		});
	}

	/** Hazard blocks need ground under them: take it away and they go, instead of floating. */
	@GameTest(templateName = EMPTY_STRUCTURE, batchId = BATCH, tickLimit = 40)
	public void hazardBlocksFallWithTheirGround(TestContext context) {
		Mutants.floor(context);
		hazard(context, new BlockPos(3, 2, 3), HazardBlockTypes.MUTANT_FIRE.getDefaultState());
		hazard(context, new BlockPos(4, 2, 3), FrostSpikesBlock.of(Tier.EVOLVED));
		hazard(context, new BlockPos(5, 2, 3), BlightBlock.of(Tier.EVOLVED));
		for (int x = 3; x <= 5; x++) {
			context.setBlockState(new BlockPos(x, 1, 3), Blocks.AIR);
		}
		context.runAtTick(2, () -> {
			for (int x = 3; x <= 5; x++) {
				context.assertTrue(context.getBlockState(new BlockPos(x, 2, 3)).isAir(), "a hazard block floats at x " + x);
			}
			context.complete();
		});
	}
}
