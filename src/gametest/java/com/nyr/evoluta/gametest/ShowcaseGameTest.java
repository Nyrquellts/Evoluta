package com.nyr.evoluta.gametest;

import com.nyr.evoluta.common.forge.SoulForge;
import com.nyr.evoluta.common.item.EvolutaItems;
import com.nyr.evoluta.common.item.SoulMeatItem;
import com.nyr.evoluta.common.mutation.Archetype;
import com.nyr.evoluta.common.mutation.Element;
import com.nyr.evoluta.common.mutation.MutationData;
import com.nyr.evoluta.common.mutation.Mutations;
import com.nyr.evoluta.common.mutation.Tier;
import com.nyr.evoluta.common.soul.Graft;
import com.nyr.evoluta.common.soul.Grafting;
import com.nyr.evoluta.common.soul.SoulKind;
import java.util.ArrayList;
import java.util.List;
import net.fabricmc.fabric.api.gametest.v1.FabricGameTest;
import net.minecraft.entity.EquipmentSlot;
import net.minecraft.entity.mob.CreeperEntity;
import net.minecraft.entity.mob.MobEntity;
import net.minecraft.entity.mob.SkeletonEntity;
import net.minecraft.entity.mob.ZombieEntity;
import net.minecraft.item.ItemStack;
import net.minecraft.item.Items;
import net.minecraft.server.MinecraftServer;
import net.minecraft.server.network.ServerPlayerEntity;
import net.minecraft.server.world.ServerWorld;
import net.minecraft.test.GameTest;
import net.minecraft.test.TestContext;
import net.minecraft.util.math.BlockPos;
import net.minecraft.util.math.Box;
import net.minecraft.world.Difficulty;
import net.minecraft.world.GameRules;

/**
 * The dev client's showcase scene, run on the test server exactly as a player would run it. Its own batch: the
 * scene clears zombies, skeletons and creepers within 64 blocks. World settings it changes are put back afterwards.
 */
public final class ShowcaseGameTest implements FabricGameTest {
	@GameTest(templateName = EMPTY_STRUCTURE, batchId = "evoluta_showcase", tickLimit = 40)
	public void theShowcaseSceneBuilds(TestContext context) {
		ServerWorld world = context.getWorld();
		MinecraftServer server = world.getServer();
		long timeOfDay = world.getTimeOfDay();
		boolean daylight = world.getGameRules().getBoolean(GameRules.DO_DAYLIGHT_CYCLE);
		boolean weather = world.getGameRules().getBoolean(GameRules.DO_WEATHER_CYCLE);
		boolean spawning = world.getGameRules().getBoolean(GameRules.DO_MOB_SPAWNING);
		Difficulty difficulty = world.getDifficulty();

		Mutants.floor(context);
		ServerPlayerEntity player = TestPlayers.survival(context, new BlockPos(3, 2, 1), 0);
		server.getCommandManager().executeWithPrefix(player.getCommandSource().withLevel(2).withSilent(), "function evoluta_showcase:scene");

		Box around = player.getBoundingBox().expand(24);
		List<ZombieEntity> zombies = world.getEntitiesByClass(ZombieEntity.class, around, zombie -> zombie.isAlive());
		List<SkeletonEntity> skeletons = world.getEntitiesByClass(SkeletonEntity.class, around, skeleton -> skeleton.isAlive());
		List<CreeperEntity> creepers = world.getEntitiesByClass(CreeperEntity.class, around, creeper -> creeper.isAlive());
		List<MobEntity> mobs = new ArrayList<>(zombies);
		mobs.addAll(skeletons);
		mobs.addAll(creepers);
		try {
			context.assertTrue(zombies.size() == 2 && skeletons.size() == 1 && creepers.size() == 1,
					"the scene should hold two zombies, a skeleton and a creeper, holds " + mobs);
			ZombieEntity bruteZombie = zombies.stream().filter(zombie -> !zombie.isBaby()).findFirst().orElseThrow();
			ZombieEntity baby = zombies.stream().filter(ZombieEntity::isBaby).findFirst().orElseThrow();
			expect(context, bruteZombie, Tier.ELITE, Element.IGNITED, Archetype.BRUTE);
			expect(context, skeletons.get(0), Tier.APEX, Element.PERMAFROST, Archetype.STALKER);
			expect(context, baby, Tier.ELITE, Element.TOXIC, Archetype.NONE);
			expect(context, creepers.get(0), Tier.ELITE, Element.TOXIC, Archetype.NONE);
			context.assertTrue(skeletons.get(0).getMainHandStack().isOf(Items.BOW), "the Stalker skeleton has no bow to mark targets with");

			// champions fight with their own mutation grafted onto their gear
			grafted(context, bruteZombie.getMainHandStack(), new Graft(SoulKind.ZOMBIE, Element.IGNITED, Tier.ELITE, 0.0F), "the Brute's sword");
			grafted(context, bruteZombie.getEquippedStack(EquipmentSlot.CHEST), new Graft(SoulKind.ZOMBIE, Element.IGNITED, Tier.ELITE, 0.0F),
					"the Brute's chestplate");
			grafted(context, skeletons.get(0).getMainHandStack(), new Graft(SoulKind.SKELETON, Element.PERMAFROST, Tier.APEX, 0.0F),
					"the Stalker's bow");

			ItemStack sword = player.getMainHandStack();
			context.assertTrue(sword.isOf(Items.DIAMOND_SWORD) && Grafting.grafts(sword).equals(List.of(
					new Graft(SoulKind.SKELETON, Element.IGNITED, Tier.APEX, 1.0F), new Graft(SoulKind.CREEPER, Element.TOXIC, Tier.ELITE, 0.8F))),
					"the player's sword is " + sword + " with " + Grafting.grafts(sword));
			ItemStack chest = player.getEquippedStack(EquipmentSlot.CHEST);
			context.assertTrue(chest.isOf(Items.DIAMOND_CHESTPLATE)
					&& Grafting.grafts(chest).equals(List.of(new Graft(SoulKind.ZOMBIE, Element.PERMAFROST, Tier.APEX, 1.0F))),
					"the player's chestplate is " + chest + " with " + Grafting.grafts(chest));
			context.assertTrue(player.getOffHandStack().isOf(EvolutaItems.BLIGHT_LOCATOR), "no Blight Locator in the off hand");
			for (Element element : Element.IMPLEMENTED) {
				context.assertTrue(player.getInventory().main.stream().anyMatch(stack -> stack.getItem() instanceof SoulMeatItem meat
						&& meat.element() == element && SoulMeatItem.grade(stack) == Tier.APEX), "no Apex " + element.key() + " Soul Meat");
			}
			context.assertTrue(player.getInventory().main.stream().anyMatch(stack -> stack.isOf(EvolutaItems.DIAMOND_SILK_RAG)), "no rag");
			context.assertTrue(BlockPos.stream(player.getBoundingBox().expand(4)).anyMatch(pos -> world.getBlockState(pos).isOf(SoulForge.BLOCK)),
					"no Soul Forge beside the player");
			// isNight() only catches up on the next world tick; the clock itself is set at once
			context.assertTrue(world.getTimeOfDay() % 24000 == 18000, "the scene did not set midnight: " + world.getTimeOfDay());
			context.complete();
		} finally {
			mobs.forEach(MobEntity::discard);
			BlockPos.stream(player.getBoundingBox().expand(4)).filter(pos -> world.getBlockState(pos).isOf(SoulForge.BLOCK))
					.forEach(pos -> world.removeBlock(pos, false));
			TestPlayers.remove(context, player);
			world.setTimeOfDay(timeOfDay);
			world.getGameRules().get(GameRules.DO_DAYLIGHT_CYCLE).set(daylight, server);
			world.getGameRules().get(GameRules.DO_WEATHER_CYCLE).set(weather, server);
			world.getGameRules().get(GameRules.DO_MOB_SPAWNING).set(spawning, server);
			server.setDifficulty(difficulty, true);
		}
	}

	private static void expect(TestContext context, MobEntity mob, Tier tier, Element element, Archetype archetype) {
		MutationData data = Mutations.get(mob);
		context.assertTrue(MutationData.of(tier, element, archetype).equals(data), mob.getType().getUntranslatedName() + " is " + data);
	}

	/** One graft of the expected kind, element and grade; its power is rolled, so it is not compared. */
	private static void grafted(TestContext context, ItemStack gear, Graft expected, String what) {
		List<Graft> grafts = Grafting.grafts(gear);
		context.assertTrue(grafts.size() == 1 && grafts.get(0).kind() == expected.kind() && grafts.get(0).element() == expected.element()
				&& grafts.get(0).grade() == expected.grade(), what + " carries " + grafts);
	}
}
