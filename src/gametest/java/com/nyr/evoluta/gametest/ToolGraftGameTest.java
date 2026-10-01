package com.nyr.evoluta.gametest;

import com.nyr.evoluta.common.item.EvolutaItems;
import com.nyr.evoluta.common.mutation.Element;
import com.nyr.evoluta.common.mutation.EvolutaAttachments;
import com.nyr.evoluta.common.mutation.Tier;
import com.nyr.evoluta.common.soul.Grafting;
import com.nyr.evoluta.common.soul.SoulKind;
import com.nyr.evoluta.common.soul.ToolGrafts;
import java.util.HashMap;
import java.util.Map;
import net.fabricmc.fabric.api.gametest.v1.FabricGameTest;
import net.minecraft.block.Block;
import net.minecraft.block.BlockState;
import net.minecraft.block.Blocks;
import net.minecraft.block.CropBlock;
import net.minecraft.entity.EquipmentSlot;
import net.minecraft.entity.ItemEntity;
import net.minecraft.entity.attribute.EntityAttribute;
import net.minecraft.entity.attribute.EntityAttributes;
import net.minecraft.item.Item;
import net.minecraft.item.ItemStack;
import net.minecraft.item.Items;
import net.minecraft.registry.entry.RegistryEntry;
import net.minecraft.server.network.ServerPlayerEntity;
import net.minecraft.test.GameTest;
import net.minecraft.test.TestContext;
import net.minecraft.util.Hand;
import net.minecraft.util.math.BlockPos;
import net.minecraft.util.math.Box;

/**
 * Grafts on mining gear, through vanilla's own break path: a real survival player breaking real blocks with a
 * grafted tool (drops rolled with the tool, then the after-break effects). Chance-based buffs are mined often enough
 * that a false failure is rarer than one in a hundred thousand. Drops are counted right at the mined block, in the
 * tick they fall, so a neighbouring test's drops never count.
 */
public final class ToolGraftGameTest implements FabricGameTest {
	private static final String BATCH = "evoluta_tool_grafts";
	private static final BlockPos AT = new BlockPos(3, 2, 3);

	private static ItemStack grafted(Item tool, Element element, SoulKind kind) {
		return Grafting.graft(new ItemStack(tool), EvolutaItems.soulMeat(element, kind), Tier.APEX, 1.0F);
	}

	private static ServerPlayerEntity miner(TestContext context, ItemStack tool) {
		ServerPlayerEntity player = TestPlayers.survival(context, new BlockPos(0, 2, 0), 0);
		player.setStackInHand(Hand.MAIN_HAND, tool);
		return player;
	}

	/** Places {@code state} at {@code relative} and has the player break it; true when it broke. */
	private static boolean mine(TestContext context, ServerPlayerEntity player, BlockPos relative, BlockState state) {
		context.setBlockState(relative, state);
		return player.interactionManager.tryBreakBlock(context.getAbsolutePos(relative));
	}

	private static boolean mine(TestContext context, ServerPlayerEntity player, BlockPos relative, Block block) {
		return mine(context, player, relative, block.getDefaultState());
	}

	/** How many of {@code item} lie on the block at {@code relative}: drops fall within a quarter block of its centre. */
	private static int dropped(TestContext context, BlockPos relative, Item item) {
		Box area = new Box(context.getAbsolutePos(relative)).expand(0.5);
		int count = 0;
		for (ItemEntity entity : context.getWorld().getEntitiesByClass(ItemEntity.class, area, e -> e.getStack().isOf(item))) {
			count += entity.getStack().getCount();
		}
		return count;
	}

	private static Map<RegistryEntry<EntityAttribute>, Double> mainHand(ItemStack stack) {
		Map<RegistryEntry<EntityAttribute>, Double> totals = new HashMap<>();
		stack.applyAttributeModifiers(EquipmentSlot.MAINHAND, (attribute, modifier) -> totals.merge(attribute, modifier.value(), Double::sum));
		return totals;
	}

	@GameTest(templateName = EMPTY_STRUCTURE, batchId = BATCH)
	public void miningGearTakesGraftsAndTheirLastingBonuses(TestContext context) {
		for (Item tool : new Item[]{Items.WOODEN_PICKAXE, Items.STONE_SHOVEL, Items.IRON_HOE, Items.GOLDEN_AXE, Items.NETHERITE_PICKAXE}) {
			context.assertTrue(Grafting.canGraft(new ItemStack(tool)), tool + " takes no graft");
		}
		context.assertFalse(Grafting.canGraft(new ItemStack(Items.SHEARS)), "shears took a graft");
		double efficiency = mainHand(grafted(Items.IRON_PICKAXE, Element.TOXIC, SoulKind.ZOMBIE)).getOrDefault(EntityAttributes.PLAYER_MINING_EFFICIENCY, 0.0);
		context.assertTrue(Math.abs(efficiency - 4.0) < 1.0E-6, "an Apex Toxic pickaxe's Corrosion is " + efficiency);
		double reach = mainHand(grafted(Items.IRON_PICKAXE, Element.IGNITED, SoulKind.SKELETON)).getOrDefault(EntityAttributes.PLAYER_BLOCK_INTERACTION_RANGE, 0.0);
		context.assertTrue(Math.abs(reach - 1.5) < 1.0E-6, "an Apex skeleton pickaxe's Long Reach is " + reach);
		double underwater = mainHand(grafted(Items.IRON_SHOVEL, Element.IGNITED, SoulKind.DROWNED)).getOrDefault(EntityAttributes.PLAYER_SUBMERGED_MINING_SPEED, 0.0);
		context.assertTrue(Math.abs(underwater - 0.8) < 1.0E-6, "an Apex drowned shovel's Aqua Lung is " + underwater);
		Map<RegistryEntry<EntityAttribute>, Double> axe = mainHand(grafted(Items.IRON_AXE, Element.IGNITED, SoulKind.BABY_ZOMBIE));
		context.assertTrue(axe.getOrDefault(EntityAttributes.PLAYER_BLOCK_BREAK_SPEED, 0.0) > 0.0, "an axe's Frenzy does not dig faster");
		context.assertTrue(axe.containsKey(EntityAttributes.GENERIC_ATTACK_SPEED), "an axe lost its weapon Frenzy");
		context.complete();
	}

	@GameTest(templateName = EMPTY_STRUCTURE, batchId = BATCH)
	public void coldSnapHardensLavaAndFreezesWater(TestContext context) {
		Mutants.floor(context);
		ServerPlayerEntity player = miner(context, grafted(Items.IRON_PICKAXE, Element.PERMAFROST, SoulKind.ZOMBIE));
		context.setBlockState(new BlockPos(4, 2, 3), Blocks.LAVA);
		context.setBlockState(new BlockPos(2, 2, 3), Blocks.WATER);
		try {
			context.assertTrue(mine(context, player, AT, Blocks.STONE), "setup: the stone did not break");
			context.assertTrue(context.getBlockState(new BlockPos(4, 2, 3)).isOf(Blocks.OBSIDIAN), "the lava beside the stone did not harden");
			context.assertTrue(context.getBlockState(new BlockPos(2, 2, 3)).isOf(Blocks.FROSTED_ICE), "the water beside the stone did not freeze");
			context.complete();
		} finally {
			TestPlayers.remove(context, player);
		}
	}

	/** Packed and blue ice come away whole; plain ice too, and leaves no water where it stood (it would, on stone). */
	@GameTest(templateName = EMPTY_STRUCTURE, batchId = BATCH)
	public void iceCutterKeepsIceWhole(TestContext context) {
		Mutants.floor(context);
		ServerPlayerEntity player = miner(context, grafted(Items.IRON_PICKAXE, Element.PERMAFROST, SoulKind.STRAY));
		try {
			BlockPos plain = new BlockPos(5, 2, 5);
			BlockPos blue = new BlockPos(5, 2, 1);
			mine(context, player, AT, Blocks.PACKED_ICE);
			mine(context, player, blue, Blocks.BLUE_ICE);
			mine(context, player, plain, Blocks.ICE);
			context.assertTrue(dropped(context, AT, Items.PACKED_ICE) == 1 && dropped(context, blue, Items.BLUE_ICE) == 1,
					"a stray pickaxe did not keep packed and blue ice whole");
			context.assertTrue(dropped(context, plain, Items.ICE) == 1, "a stray pickaxe did not keep plain ice whole");
			context.assertTrue(context.getBlockState(plain).isAir(), "cut ice left " + context.getBlockState(plain) + " behind");
			context.complete();
		} finally {
			TestPlayers.remove(context, player);
		}
	}

	/** Apex Smelting Touch smelts half the drops: of 24 iron ores, some come out ingots and some raw. */
	@GameTest(templateName = EMPTY_STRUCTURE, batchId = BATCH)
	public void smeltingTouchSmeltsSomeOfTheOre(TestContext context) {
		Mutants.floor(context);
		ServerPlayerEntity player = miner(context, grafted(Items.IRON_PICKAXE, Element.IGNITED, SoulKind.ZOMBIE));
		try {
			for (int i = 0; i < 24; i++) {
				mine(context, player, AT, Blocks.IRON_ORE);
			}
			int ingots = dropped(context, AT, Items.IRON_INGOT);
			int raw = dropped(context, AT, Items.RAW_IRON);
			context.assertTrue(ingots > 0 && raw > 0, "24 iron ores gave " + ingots + " ingots and " + raw + " raw iron");
			context.complete();
		} finally {
			TestPlayers.remove(context, player);
		}
	}

	/** Apex Silk Thread keeps a quarter of blocks whole: of 40 stone, some drop stone and some cobblestone. */
	@GameTest(templateName = EMPTY_STRUCTURE, batchId = BATCH)
	public void silkThreadKeepsSomeBlocksWhole(TestContext context) {
		Mutants.floor(context);
		ServerPlayerEntity player = miner(context, grafted(Items.IRON_PICKAXE, Element.TOXIC, SoulKind.SPIDER));
		try {
			for (int i = 0; i < 40; i++) {
				mine(context, player, AT, Blocks.STONE);
			}
			int whole = dropped(context, AT, Items.STONE);
			int cobble = dropped(context, AT, Items.COBBLESTONE);
			context.assertTrue(whole > 0 && cobble > 0 && whole + cobble == 40, "40 stone gave " + whole + " stone and " + cobble + " cobblestone");
			context.complete();
		} finally {
			TestPlayers.remove(context, player);
		}
	}

	/** Apex Prospector finds an extra ore three times in ten: 40 coal ores give more than 40 coal. */
	@GameTest(templateName = EMPTY_STRUCTURE, batchId = BATCH)
	public void prospectorFindsExtraOre(TestContext context) {
		Mutants.floor(context);
		ServerPlayerEntity player = miner(context, grafted(Items.IRON_PICKAXE, Element.TOXIC, SoulKind.ZOMBIE_VILLAGER));
		try {
			for (int i = 0; i < 40; i++) {
				mine(context, player, AT, Blocks.COAL_ORE);
			}
			int coal = dropped(context, AT, Items.COAL);
			context.assertTrue(coal > 40, "40 coal ores gave " + coal + " coal");
			context.complete();
		} finally {
			TestPlayers.remove(context, player);
		}
	}

	/**
	 * Apex Bountiful gives one more crop a third of the time, from ripe crops only: 30 ripe wheat give more than 30
	 * wheat, while 40 unripe ones give back exactly their 40 seeds (one more of those would multiply seed).
	 */
	@GameTest(templateName = EMPTY_STRUCTURE, batchId = BATCH)
	public void bountifulHarvestsMoreFromRipeCropsOnly(TestContext context) {
		Mutants.floor(context);
		BlockPos unripe = new BlockPos(6, 2, 6);
		context.setBlockState(AT.down(), Blocks.FARMLAND);
		context.setBlockState(unripe.down(), Blocks.FARMLAND);
		ServerPlayerEntity player = miner(context, grafted(Items.IRON_HOE, Element.TOXIC, SoulKind.BOGGED));
		try {
			for (int i = 0; i < 30; i++) {
				mine(context, player, AT, Blocks.WHEAT.getDefaultState().with(CropBlock.AGE, CropBlock.MAX_AGE));
			}
			for (int i = 0; i < 40; i++) {
				mine(context, player, unripe, Blocks.WHEAT);
			}
			int wheat = dropped(context, AT, Items.WHEAT);
			int seeds = dropped(context, unripe, Items.WHEAT_SEEDS);
			context.assertTrue(wheat > 30, "30 ripe wheat gave " + wheat + " wheat");
			context.assertTrue(seeds == 40, "40 unripe wheat gave " + seeds + " seeds");
			context.complete();
		} finally {
			TestPlayers.remove(context, player);
		}
	}

	@GameTest(templateName = EMPTY_STRUCTURE, batchId = BATCH)
	public void gnawFeedsTheMiner(TestContext context) {
		Mutants.floor(context);
		ServerPlayerEntity player = miner(context, grafted(Items.IRON_PICKAXE, Element.TOXIC, SoulKind.ZOMBIE));
		try {
			player.getHungerManager().setFoodLevel(10);
			mine(context, player, AT, Blocks.STONE);
			context.assertTrue(player.getHungerManager().getFoodLevel() == 10, "stone fed a zombie-grafted miner");
			mine(context, player, AT, Blocks.COAL_ORE);
			context.assertTrue(player.getHungerManager().getFoodLevel() == 11, "ore did not feed a zombie-grafted miner");
			context.complete();
		} finally {
			TestPlayers.remove(context, player);
		}
	}

	/** Apex Blast Mining blasts the vein one time in five: in 60 veins of three, at least one goes up, not half. */
	@GameTest(templateName = EMPTY_STRUCTURE, batchId = BATCH)
	public void blastMiningBlastsOutTheVein(TestContext context) {
		Mutants.floor(context);
		ItemStack pickaxe = grafted(Items.DIAMOND_PICKAXE, Element.TOXIC, SoulKind.CREEPER);
		ServerPlayerEntity player = miner(context, pickaxe);
		try {
			int blasts = 0;
			for (int i = 0; i < 60; i++) {
				context.setBlockState(new BlockPos(4, 2, 3), Blocks.COAL_ORE);
				context.setBlockState(new BlockPos(5, 2, 3), Blocks.COAL_ORE);
				mine(context, player, AT, Blocks.COAL_ORE);
				blasts += context.getBlockState(new BlockPos(4, 2, 3)).isAir() && context.getBlockState(new BlockPos(5, 2, 3)).isAir() ? 1 : 0;
			}
			context.assertTrue(blasts > 0, "60 coal veins never blasted");
			context.assertTrue(blasts < 30, "the vein blasted " + blasts + " times in 60: the chance is off");
			// every block, blasted or mined, wore the pickaxe once through vanilla's own break
			context.assertTrue(pickaxe.getDamage() == 60 + 2 * blasts, "the pickaxe took " + pickaxe.getDamage() + " damage for " + (60 + 2 * blasts) + " blocks");
			context.complete();
		} finally {
			TestPlayers.remove(context, player);
		}
	}

	/** Blast Mining stops short of breaking the tool, so the block being mined still drops. */
	@GameTest(templateName = EMPTY_STRUCTURE, batchId = BATCH)
	public void blastMiningSparesANearlyBrokenTool(TestContext context) {
		Mutants.floor(context);
		ItemStack pickaxe = grafted(Items.DIAMOND_PICKAXE, Element.TOXIC, SoulKind.CREEPER);
		pickaxe.setDamage(pickaxe.getMaxDamage() - 2);
		ServerPlayerEntity player = miner(context, pickaxe);
		try {
			for (int i = 0; i < 40; i++) {
				context.setBlockState(new BlockPos(4, 2, 3), Blocks.COAL_ORE);
				mine(context, player, AT, Blocks.COAL_ORE);
				context.assertTrue(context.getBlockState(new BlockPos(4, 2, 3)).isOf(Blocks.COAL_ORE), "a blast wore a nearly broken pickaxe");
				pickaxe.setDamage(pickaxe.getMaxDamage() - 2);
			}
			context.assertTrue(dropped(context, AT, Items.COAL) == 40, "40 coal ores mined with a nearly broken pickaxe dropped " + dropped(context, AT, Items.COAL));
			context.complete();
		} finally {
			TestPlayers.remove(context, player);
		}
	}

	/** Apex Sandsifter digs sand 80% faster and leaves stone, which no shovel is for, alone. */
	@GameTest(templateName = EMPTY_STRUCTURE, batchId = BATCH)
	public void sandsifterDigsSandFaster(TestContext context) {
		ServerPlayerEntity player = miner(context, new ItemStack(Items.IRON_SHOVEL));
		try {
			float plainSand = player.getBlockBreakingSpeed(Blocks.SAND.getDefaultState());
			float plainStone = player.getBlockBreakingSpeed(Blocks.STONE.getDefaultState());
			// Ignited: no mining attribute of its own to muddle the numbers
			player.setStackInHand(Hand.MAIN_HAND, grafted(Items.IRON_SHOVEL, Element.IGNITED, SoulKind.HUSK));
			float sand = player.getBlockBreakingSpeed(Blocks.SAND.getDefaultState());
			float stone = player.getBlockBreakingSpeed(Blocks.STONE.getDefaultState());
			context.assertTrue(Math.abs(sand / plainSand - 1.8F) < 1.0E-3, "a husk shovel digs sand at " + sand + " against " + plainSand);
			context.assertTrue(stone == plainStone, "a husk shovel changed stone from " + plainStone + " to " + stone);
			context.complete();
		} finally {
			TestPlayers.remove(context, player);
		}
	}

	/**
	 * An Evolved cave spider graft feels three blocks around (inside this test's own space): with no ore near it waits
	 * a moment before feeling again; with iron ore two blocks off it chimes, then keeps quiet for its cooldown.
	 */
	@GameTest(templateName = EMPTY_STRUCTURE, batchId = BATCH)
	public void tunnelSenseFeelsForNearbyOre(TestContext context) {
		Mutants.floor(context);
		ItemStack pickaxe = Grafting.graft(new ItemStack(Items.IRON_PICKAXE), EvolutaItems.soulMeat(Element.TOXIC, SoulKind.CAVE_SPIDER), Tier.EVOLVED, 0.2F);
		ServerPlayerEntity player = miner(context, pickaxe);
		BlockPos stone = new BlockPos(4, 3, 4);
		long start = context.getWorld().getTime();
		mine(context, player, stone, Blocks.STONE);
		Long quiet = player.getAttached(EvolutaAttachments.TUNNEL_SENSE);
		context.assertTrue(quiet != null && quiet == start + ToolGrafts.TUNNEL_SENSE_RETRY, "with no ore near, Tunnel Sense set " + quiet + " at " + start);
		context.setBlockState(new BlockPos(4, 3, 6), Blocks.IRON_ORE);
		mine(context, player, stone, Blocks.STONE);
		context.assertTrue(quiet.equals(player.getAttached(EvolutaAttachments.TUNNEL_SENSE)), "Tunnel Sense felt again before its wait was over");
		context.waitAndRun(ToolGrafts.TUNNEL_SENSE_RETRY + 1, () -> {
			try {
				long now = context.getWorld().getTime();
				mine(context, player, stone, Blocks.STONE);
				Long chimed = player.getAttached(EvolutaAttachments.TUNNEL_SENSE);
				context.assertTrue(chimed != null && chimed == now + ToolGrafts.TUNNEL_SENSE_COOLDOWN, "Tunnel Sense did not feel iron ore two blocks away");
				context.complete();
			} finally {
				TestPlayers.remove(context, player);
			}
		});
	}
}
