package com.nyr.evoluta.gametest;

import com.nyr.evoluta.common.combat.HazardBlockTypes;
import com.nyr.evoluta.common.combat.HazardBlocks;
import com.nyr.evoluta.common.combat.Hazards;
import com.nyr.evoluta.common.combat.MutantCreepers;
import com.nyr.evoluta.common.mutation.Archetype;
import com.nyr.evoluta.common.mutation.Element;
import com.nyr.evoluta.common.mutation.EvolutaAttachments;
import com.nyr.evoluta.common.mutation.Tier;
import java.util.ArrayList;
import java.util.List;
import net.fabricmc.fabric.api.gametest.v1.FabricGameTest;
import net.minecraft.block.Block;
import net.minecraft.block.Blocks;
import net.minecraft.entity.EntityType;
import net.minecraft.entity.ItemEntity;
import net.minecraft.entity.mob.CreeperEntity;
import net.minecraft.item.Items;
import net.minecraft.server.network.ServerPlayerEntity;
import net.minecraft.server.world.ServerWorld;
import net.minecraft.state.property.Properties;
import net.minecraft.test.GameTest;
import net.minecraft.test.TestContext;
import net.minecraft.util.math.BlockPos;
import net.minecraft.util.math.Box;
import net.minecraft.util.math.Direction;
import net.minecraft.util.math.Vec3d;
import net.minecraft.world.World;

/**
 * Mutant creepers: a blast they survive and must recharge from, and a hazard of their element that is put back when
 * it runs out. Every creeper stands in the middle of its own 8x8 area on a two-block stone floor; the checks come
 * after the 30-tick fuse.
 */
public final class CreeperGameTest implements FabricGameTest {
	private static final String BLASTS = "evoluta_creeper";
	/** The fuse (30), the blast, and a margin. */
	private static final int AFTER_BLAST = 40;

	@GameTest(templateName = EMPTY_STRUCTURE, batchId = BLASTS, tickLimit = 200)
	public void aMutantCreeperSurvivesItsBlastAndRecharges(TestContext context) {
		CreeperEntity creeper = litCreeper(context, Tier.EVOLVED, Element.NONE);
		context.runAtTick(AFTER_BLAST, () -> {
			context.assertTrue(creeper.isAlive(), "a mutant creeper died from its own blast");
			Long blast = creeper.getAttached(EvolutaAttachments.CREEPER_LAST_BLAST);
			context.assertTrue(blast != null, "the creeper never blasted");
			context.assertTrue(creeper.getHealth() < creeper.getMaxHealth(), "the blast cost the creeper nothing");
			context.assertFalse(creeper.isIgnited(), "the creeper is still lit after its blast");
			// lit again straight away: the recharge holds the fuse
			creeper.ignite();
		});
		context.runAtTick(AFTER_BLAST + 50, () -> {
			long first = creeper.getAttached(EvolutaAttachments.CREEPER_LAST_BLAST);
			context.assertTrue(context.getWorld().getTime() - first < MutantCreepers.RECHARGE[0], "setup: the recharge is already over");
			context.assertFalse(creeper.isIgnited(), "a recharging creeper stayed lit");
			context.assertTrue(creeper.getFuseSpeed() < 0, "a recharging creeper's fuse is running");
			context.complete();
		});
	}

	@GameTest(templateName = EMPTY_STRUCTURE, batchId = BLASTS, tickLimit = 240)
	public void anIgnitedBlastLeavesAMagmaFloorThatGoesBack(TestContext context) {
		litCreeper(context, Tier.EVOLVED, Element.IGNITED);
		List<BlockPos> magma = new ArrayList<>();
		context.runAtTick(AFTER_BLAST, () -> {
			magma.addAll(find(context, Blocks.MAGMA_BLOCK));
			context.assertTrue(magma.size() >= 5, "an Ignited blast left " + magma.size() + " magma blocks");
			context.assertTrue(!find(context, HazardBlockTypes.MUTANT_FIRE).isEmpty(), "an Ignited blast left no fire over its magma");
		});
		// the hazard lasts 120 ticks plus up to 20 per block
		context.runAtTick(AFTER_BLAST + Hazards.BLAST_HAZARD_TICKS[0] + 30, () -> {
			for (BlockPos pos : magma) {
				context.assertTrue(context.getBlockState(pos).isOf(Blocks.STONE), "magma at " + pos + " was not put back: " + context.getBlockState(pos));
			}
			context.complete();
		});
	}

	@GameTest(templateName = EMPTY_STRUCTURE, batchId = BLASTS, tickLimit = 240)
	public void aToxicBlastLeavesGoopCrustedWithBlight(TestContext context) {
		litCreeper(context, Tier.ELITE, Element.TOXIC);
		context.runAtTick(AFTER_BLAST, () -> {
			context.assertTrue(find(context, Blocks.SLIME_BLOCK).size() >= 5, "a Toxic blast left no goop floor");
			context.assertTrue(find(context, HazardBlockTypes.BLIGHT).size() >= 5, "a Toxic blast left no blight on the goop");
		});
		context.runAtTick(AFTER_BLAST + Hazards.BLAST_HAZARD_TICKS[1] + 30, () -> {
			context.assertTrue(find(context, Blocks.SLIME_BLOCK).isEmpty(), "the goop outlived its hazard");
			context.assertTrue(find(context, HazardBlockTypes.BLIGHT).isEmpty(), "the blight outlived its hazard");
			context.complete();
		});
	}

	@GameTest(templateName = EMPTY_STRUCTURE, batchId = BLASTS, tickLimit = 300)
	public void aPermafrostBlastRaisesIceSpikesAndPowderSnow(TestContext context) {
		litCreeper(context, Tier.APEX, Element.PERMAFROST);
		context.runAtTick(AFTER_BLAST, () -> {
			context.assertTrue(!find(context, Blocks.PACKED_ICE).isEmpty(), "a Permafrost blast raised no ice spikes");
			context.assertTrue(!find(context, Blocks.POWDER_SNOW).isEmpty(), "an Apex Permafrost blast left no powder snow");
			context.assertTrue(!find(context, HazardBlockTypes.FROST_SPIKES).isEmpty(), "an Apex Permafrost blast left no frost spikes");
		});
		context.runAtTick(AFTER_BLAST + Hazards.BLAST_HAZARD_TICKS[2] + 30, () -> {
			context.assertTrue(find(context, Blocks.PACKED_ICE).isEmpty() && find(context, Blocks.POWDER_SNOW).isEmpty(),
					"the ice outlived its hazard");
			context.complete();
		});
	}

	/** No free magma: a survival player cannot break a hazard block, it simply stays until it goes back. */
	@GameTest(templateName = EMPTY_STRUCTURE, batchId = BLASTS, tickLimit = 200)
	public void playersCannotMineHazardBlocks(TestContext context) {
		litCreeper(context, Tier.EVOLVED, Element.IGNITED);
		context.runAtTick(AFTER_BLAST, () -> {
			List<BlockPos> magma = find(context, Blocks.MAGMA_BLOCK);
			context.assertTrue(!magma.isEmpty(), "setup: no magma to mine");
			BlockPos target = magma.get(0);
			ServerPlayerEntity player = TestPlayers.survival(context, target.up(3), 0);
			boolean broken = player.interactionManager.tryBreakBlock(context.getAbsolutePos(target));
			context.assertFalse(broken, "a player broke a hazard block");
			context.assertTrue(context.getBlockState(target).isOf(Blocks.MAGMA_BLOCK), "the hazard block is gone after a refused break");
			TestPlayers.remove(context, player);
			context.complete();
		});
	}

	/** TNT next to a magma floor and slime goop breaks the stone around them and leaves them, dropping neither. */
	@GameTest(templateName = EMPTY_STRUCTURE, batchId = "evoluta_hazard_tnt", tickLimit = 40)
	public void explosionsPassOverHazardBlocks(TestContext context) {
		stoneFloor(context);
		ServerWorld world = context.getWorld();
		BlockPos slime = new BlockPos(3, 1, 4);
		BlockPos magma = new BlockPos(5, 1, 4);
		HazardBlocks hazards = HazardBlocks.of(world);
		context.assertTrue(hazards.place(world, context.getAbsolutePos(slime), Blocks.SLIME_BLOCK.getDefaultState(), 200)
				&& hazards.place(world, context.getAbsolutePos(magma), Blocks.MAGMA_BLOCK.getDefaultState(), 200), "setup: no hazard blocks");
		Vec3d at = context.getAbsolute(new Vec3d(4.5, 2.0, 4.5));
		world.createExplosion(null, at.x, at.y, at.z, 4.0F, World.ExplosionSourceType.TNT);
		context.assertTrue(context.getBlockState(new BlockPos(4, 1, 4)).isAir(), "setup: the blast broke nothing");
		context.assertTrue(context.getBlockState(slime).isOf(Blocks.SLIME_BLOCK), "the blast took the slime goop");
		context.assertTrue(context.getBlockState(magma).isOf(Blocks.MAGMA_BLOCK), "the blast took the magma floor");
		Box area = new Box(context.getAbsolutePos(BlockPos.ORIGIN).toCenterPos(), context.getAbsolutePos(new BlockPos(8, 8, 8)).toCenterPos());
		context.assertTrue(world.getEntitiesByClass(ItemEntity.class, area,
				item -> item.getStack().isOf(Items.SLIME_BLOCK) || item.getStack().isOf(Items.MAGMA_BLOCK)).isEmpty(), "the blast dropped a hazard block");
		context.complete();
	}

	/** A powered piston cannot shift slime goop (the control piston beside it pushes plain slime). */
	@GameTest(templateName = EMPTY_STRUCTURE, batchId = "evoluta_hazard_piston", tickLimit = 40)
	public void pistonsCannotMoveHazardBlocks(TestContext context) {
		ServerWorld world = context.getWorld();
		for (int z : new int[]{2, 5}) {
			context.setBlockState(new BlockPos(2, 3, z), Blocks.PISTON.getDefaultState().with(Properties.FACING, Direction.EAST));
		}
		context.assertTrue(HazardBlocks.of(world).place(world, context.getAbsolutePos(new BlockPos(3, 3, 2)), Blocks.SLIME_BLOCK.getDefaultState(), 200),
				"setup: no hazard block");
		context.setBlockState(new BlockPos(3, 3, 5), Blocks.SLIME_BLOCK);
		context.setBlockState(new BlockPos(1, 3, 2), Blocks.REDSTONE_BLOCK);
		context.setBlockState(new BlockPos(1, 3, 5), Blocks.REDSTONE_BLOCK);
		context.runAtTick(10, () -> {
			context.assertTrue(context.getBlockState(new BlockPos(4, 3, 5)).isOf(Blocks.SLIME_BLOCK), "setup: the control piston pushed nothing");
			context.assertTrue(context.getBlockState(new BlockPos(3, 3, 2)).isOf(Blocks.SLIME_BLOCK), "a piston moved a hazard block");
			context.assertTrue(context.getBlockState(new BlockPos(4, 3, 2)).isAir(), "a piston pushed the hazard along");
			context.complete();
		});
	}

	private static void stoneFloor(TestContext context) {
		for (int x = 0; x < 8; x++) {
			for (int z = 0; z < 8; z++) {
				context.setBlockState(x, 0, z, Blocks.STONE);
				context.setBlockState(x, 1, z, Blocks.STONE);
			}
		}
	}

	/** A mutant creeper in the middle of a two-layer stone floor, lit. */
	private static CreeperEntity litCreeper(TestContext context, Tier tier, Element element) {
		stoneFloor(context);
		CreeperEntity creeper = Mutants.still(context, EntityType.CREEPER, 4, 4, tier, element, Archetype.NONE);
		creeper.ignite();
		return creeper;
	}

	/** Relative positions of every {@code block} in the test area. */
	private static List<BlockPos> find(TestContext context, Block block) {
		List<BlockPos> found = new ArrayList<>();
		for (int x = 0; x < 8; x++) {
			for (int y = 0; y < 8; y++) {
				for (int z = 0; z < 8; z++) {
					if (context.getBlockState(new BlockPos(x, y, z)).isOf(block)) {
						found.add(new BlockPos(x, y, z));
					}
				}
			}
		}
		return found;
	}
}
