package com.nyr.evoluta.gametest;

import com.nyr.evoluta.common.combat.HazardBlockTypes;
import com.nyr.evoluta.common.forge.SoulForgeScreenHandler;
import com.nyr.evoluta.common.item.EvolutaItems;
import com.nyr.evoluta.common.mutation.Element;
import com.nyr.evoluta.common.mutation.Tier;
import com.nyr.evoluta.common.soul.Grafting;
import com.nyr.evoluta.common.soul.SoulComponents;
import com.nyr.evoluta.common.soul.SoulKind;
import java.lang.reflect.Field;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import net.fabricmc.fabric.api.gametest.v1.FabricGameTest;
import net.minecraft.entity.EntityType;
import net.minecraft.entity.EquipmentSlot;
import net.minecraft.entity.LivingEntity;
import net.minecraft.entity.attribute.EntityAttribute;
import net.minecraft.entity.attribute.EntityAttributes;
import net.minecraft.entity.effect.StatusEffectInstance;
import net.minecraft.entity.effect.StatusEffects;
import net.minecraft.entity.mob.MobEntity;
import net.minecraft.entity.player.PlayerEntity;
import net.minecraft.entity.projectile.ArrowEntity;
import net.minecraft.item.ItemStack;
import net.minecraft.item.Items;
import net.minecraft.registry.entry.RegistryEntry;
import net.minecraft.screen.ScreenHandlerContext;
import net.minecraft.server.network.ServerPlayerEntity;
import net.minecraft.test.GameTest;
import net.minecraft.test.TestContext;
import net.minecraft.util.Hand;
import net.minecraft.util.math.BlockPos;
import net.minecraft.world.GameMode;

/** Grafts in a fight and at the Soul Forge: real swings, arrows and screen clicks against plain vanilla mobs. */
public final class GraftGameTest implements FabricGameTest {
	private static ItemStack grafted(ItemStack gear, Element element, SoulKind kind, Tier grade, float power) {
		return Grafting.graft(gear, EvolutaItems.soulMeat(element, kind), grade, power);
	}

	/** A plain, still mob to hit. */
	private static <T extends MobEntity> T target(TestContext context, EntityType<T> type, int x, int z) {
		T mob = context.spawnMob(type, x, 2, z);
		mob.setAiDisabled(true);
		return mob;
	}

	/** A survival player holding {@code weapon}, swung at {@code target} fully charged (or not). */
	private static PlayerEntity swing(TestContext context, ItemStack weapon, LivingEntity target, boolean full) {
		PlayerEntity player = Mutants.target(context, 1, 1, 0);
		player.setStackInHand(Hand.MAIN_HAND, weapon);
		charge(player, full ? 100 : 0);
		player.attack(target);
		return player;
	}

	private static void charge(PlayerEntity player, int ticks) {
		try {
			Field field = LivingEntity.class.getDeclaredField("lastAttackedTicks");
			field.setAccessible(true);
			field.setInt(player, ticks);
		} catch (ReflectiveOperationException e) {
			throw new IllegalStateException("dev runtime without Yarn names?", e);
		}
	}

	@GameTest(templateName = EMPTY_STRUCTURE)
	public void anIgnitedGraftHitsHarderAndSetsTargetsAlight(TestContext context) {
		Mutants.floor(context);
		MobEntity plain = target(context, EntityType.PIG, 2, 5);
		MobEntity burnt = target(context, EntityType.PIG, 5, 5);
		swing(context, new ItemStack(Items.IRON_SWORD), plain, true);
		swing(context, grafted(new ItemStack(Items.IRON_SWORD), Element.IGNITED, SoulKind.ZOMBIE, Tier.APEX, 1.0F), burnt, true);
		float plainLoss = plain.getMaxHealth() - plain.getHealth();
		float burntLoss = burnt.getMaxHealth() - burnt.getHealth();
		context.assertTrue(burntLoss >= plainLoss + 1.5F, "an Apex Ignited graft added " + (burntLoss - plainLoss) + " damage, expected about 2");
		context.assertTrue(burnt.isOnFire(), "the Ignited graft did not set its target alight");
		context.assertFalse(plain.isOnFire(), "setup: the plain sword set a fire");
		context.complete();
	}

	@GameTest(templateName = EMPTY_STRUCTURE)
	public void aHalfChargedSwingCarriesNoGraft(TestContext context) {
		Mutants.floor(context);
		MobEntity pig = target(context, EntityType.PIG, 4, 4);
		swing(context, grafted(new ItemStack(Items.IRON_SWORD), Element.IGNITED, SoulKind.ZOMBIE, Tier.APEX, 1.0F), pig, false);
		context.assertFalse(pig.isOnFire(), "a spam click carried the graft");
		context.complete();
	}

	@GameTest(templateName = EMPTY_STRUCTURE)
	public void permafrostFreezesAndToxicPoisons(TestContext context) {
		Mutants.floor(context);
		MobEntity frozen = target(context, EntityType.PIG, 2, 5);
		MobEntity poisoned = target(context, EntityType.PIG, 5, 5);
		swing(context, grafted(new ItemStack(Items.IRON_AXE), Element.PERMAFROST, SoulKind.STRAY, Tier.ELITE, 0.8F), frozen, true);
		swing(context, grafted(new ItemStack(Items.IRON_AXE), Element.TOXIC, SoulKind.SPIDER, Tier.ELITE, 0.8F), poisoned, true);
		context.assertTrue(frozen.getFrozenTicks() > 0, "a Permafrost graft did not freeze its target");
		context.assertTrue(frozen.hasStatusEffect(StatusEffects.SLOWNESS), "the stray graft did not slow its target");
		context.assertTrue(poisoned.hasStatusEffect(StatusEffects.POISON), "a Toxic graft did not poison its target");
		context.complete();
	}

	@GameTest(templateName = EMPTY_STRUCTURE)
	public void aZombieGraftFeastsOnKills(TestContext context) {
		Mutants.floor(context);
		MobEntity chicken = target(context, EntityType.CHICKEN, 4, 4);
		chicken.setHealth(1.0F);
		PlayerEntity player = Mutants.target(context, 1, 1, 0);
		player.setHealth(10.0F);
		player.setStackInHand(Hand.MAIN_HAND, grafted(new ItemStack(Items.IRON_SWORD), Element.TOXIC, SoulKind.ZOMBIE, Tier.APEX, 1.0F));
		charge(player, 100);
		player.attack(chicken);
		context.assertFalse(chicken.isAlive(), "setup: the chicken survived");
		context.assertTrue(player.getHealth() > 10.0F, "a zombie graft's kill healed nothing: " + player.getHealth());
		context.complete();
	}

	@GameTest(templateName = EMPTY_STRUCTURE)
	public void anApexIgnitedKillLeavesSoulFireThatBurnsOnlyMonsters(TestContext context) {
		Mutants.floor(context);
		MobEntity chicken = target(context, EntityType.CHICKEN, 4, 4);
		chicken.setHealth(1.0F);
		// spawnMob strips goals but leaves the AI on, so these two touch the blocks they stand in
		MobEntity spider = context.spawnMob(EntityType.SPIDER, 6, 2, 4);
		MobEntity pig = context.spawnMob(EntityType.PIG, 4, 2, 6);
		swing(context, grafted(new ItemStack(Items.IRON_SWORD), Element.IGNITED, SoulKind.SKELETON, Tier.APEX, 1.0F), chicken, true);
		context.assertFalse(chicken.isAlive(), "setup: the chicken survived");
		context.assertTrue(soulFireWhere(context, chicken), "the kill left no soul fire");
		context.runAtTick(15, () -> {
			context.assertTrue(spider.isOnFire(), "a spider standing in the soul fire did not catch fire");
			context.assertFalse(pig.isOnFire(), "the soul fire burnt a pig");
			context.complete();
		});
	}

	@GameTest(templateName = EMPTY_STRUCTURE)
	public void anEliteIgnitedKillLeavesNoSoulFire(TestContext context) {
		Mutants.floor(context);
		MobEntity chicken = target(context, EntityType.CHICKEN, 4, 4);
		chicken.setHealth(1.0F);
		swing(context, grafted(new ItemStack(Items.IRON_SWORD), Element.IGNITED, SoulKind.SKELETON, Tier.ELITE, 0.8F), chicken, true);
		context.assertFalse(chicken.isAlive(), "setup: the chicken survived");
		context.assertFalse(soulFireWhere(context, chicken), "an Elite graft left soul fire");
		context.complete();
	}

	/** Whether soul fire burns where {@code victim} fell. */
	private static boolean soulFireWhere(TestContext context, LivingEntity victim) {
		return context.getWorld().getBlockState(victim.getBlockPos()).isOf(HazardBlockTypes.SOUL_FIRE);
	}

	@GameTest(templateName = EMPTY_STRUCTURE)
	public void aCreeperGraftBurstsOnEveryThirdFullHit(TestContext context) {
		Mutants.floor(context);
		MobEntity struck = target(context, EntityType.IRON_GOLEM, 4, 4);
		MobEntity bystander = target(context, EntityType.PIG, 5, 4);
		PlayerEntity player = Mutants.target(context, 1, 1, 0);
		player.setStackInHand(Hand.MAIN_HAND, grafted(new ItemStack(Items.IRON_SWORD), Element.IGNITED, SoulKind.CREEPER, Tier.APEX, 1.0F));
		float before = bystander.getHealth();
		for (int hit = 1; hit <= 3; hit++) {
			charge(player, 100);
			struck.timeUntilRegen = 0;
			player.attack(struck);
			if (hit < 3) {
				context.assertTrue(bystander.getHealth() == before, "the burst came on hit " + hit);
			}
		}
		context.assertTrue(bystander.getHealth() < before, "no burst reached the bystander on the third full hit");
		context.complete();
	}

	@GameTest(templateName = EMPTY_STRUCTURE)
	public void arrowsCarryTheirBowsGrafts(TestContext context) {
		Mutants.floor(context);
		MobEntity pig = target(context, EntityType.PIG, 4, 4);
		PlayerEntity archer = Mutants.target(context, 1, 1, 0);
		ItemStack bow = grafted(new ItemStack(Items.BOW), Element.TOXIC, SoulKind.BOGGED, Tier.ELITE, 0.7F);
		ArrowEntity arrow = new ArrowEntity(context.getWorld(), archer, new ItemStack(Items.ARROW), bow);
		pig.damage(context.getWorld().getDamageSources().arrow(arrow, archer), 2.0F);
		context.assertTrue(pig.hasStatusEffect(StatusEffects.POISON), "an arrow from a Toxic bow did not poison");
		context.complete();
	}

	@GameTest(templateName = EMPTY_STRUCTURE)
	public void aSkeletonGraftFiresAVolley(TestContext context) {
		Mutants.floor(context);
		ServerPlayerEntity player = TestPlayers.survival(context, new BlockPos(1, 2, 1), 0);
		ItemStack sword = grafted(new ItemStack(Items.IRON_SWORD), Element.PERMAFROST, SoulKind.SKELETON, Tier.ELITE, 0.6F);
		player.setStackInHand(Hand.MAIN_HAND, sword);
		player.interactionManager.interactItem(player, context.getWorld(), player.getMainHandStack(), Hand.MAIN_HAND);
		List<ArrowEntity> arrows = context.getWorld().getEntitiesByClass(ArrowEntity.class, player.getBoundingBox().expand(3), arrow -> arrow.getOwner() == player);
		context.assertTrue(arrows.size() == 1, "a skeleton graft fired " + arrows.size() + " arrows");
		context.assertTrue(player.getItemCooldownManager().isCoolingDown(Items.IRON_SWORD), "the volley left no cooldown");
		context.assertTrue(Grafting.grafts(arrows.get(0).getWeaponStack()).size() == 1, "the volley arrow does not carry the sword's graft");
		TestPlayers.remove(context, player);
		context.complete();
	}

	/** A pickaxe's grafts dig rather than fight: an Ignited one's full swing sets nothing alight, a skeleton one fires no volley. */
	@GameTest(templateName = EMPTY_STRUCTURE)
	public void graftsOnMiningGearDigRatherThanFight(TestContext context) {
		Mutants.floor(context);
		MobEntity pig = target(context, EntityType.PIG, 4, 4);
		swing(context, grafted(new ItemStack(Items.IRON_PICKAXE), Element.IGNITED, SoulKind.ZOMBIE, Tier.APEX, 1.0F), pig, true);
		context.assertFalse(pig.isOnFire(), "an Ignited pickaxe's hit carried its graft");
		ServerPlayerEntity player = TestPlayers.survival(context, new BlockPos(6, 2, 1), 0);
		player.setStackInHand(Hand.MAIN_HAND, grafted(new ItemStack(Items.IRON_PICKAXE), Element.PERMAFROST, SoulKind.SKELETON, Tier.APEX, 1.0F));
		player.interactionManager.interactItem(player, context.getWorld(), player.getMainHandStack(), Hand.MAIN_HAND);
		List<ArrowEntity> arrows = context.getWorld().getEntitiesByClass(ArrowEntity.class, player.getBoundingBox().expand(3), arrow -> arrow.getOwner() == player);
		TestPlayers.remove(context, player);
		context.assertTrue(arrows.isEmpty(), "a skeleton pickaxe fired " + arrows.size() + " arrows");
		context.complete();
	}

	@GameTest(templateName = EMPTY_STRUCTURE)
	public void boggedArmourShortensPoison(TestContext context) {
		PlayerEntity player = context.createMockPlayer(GameMode.SURVIVAL);
		player.equipStack(EquipmentSlot.CHEST, grafted(new ItemStack(Items.IRON_CHESTPLATE), Element.TOXIC, SoulKind.BOGGED, Tier.APEX, 1.0F));
		player.addStatusEffect(new StatusEffectInstance(StatusEffects.POISON, 200));
		StatusEffectInstance poison = player.getStatusEffect(StatusEffects.POISON);
		context.assertTrue(poison != null && poison.getDuration() < 100, "Apex bogged and Toxic armour left poison at " + (poison == null ? 0 : poison.getDuration()));
		context.complete();
	}

	/** The first graft must not strip armour of its own armour points, and scrubbing the last one restores the item exactly. */
	@GameTest(templateName = EMPTY_STRUCTURE)
	public void graftedArmourKeepsItsArmourAndScrubsClean(TestContext context) {
		ItemStack chestplate = grafted(new ItemStack(Items.IRON_CHESTPLATE), Element.IGNITED, SoulKind.ZOMBIE, Tier.APEX, 1.0F);
		Map<RegistryEntry<EntityAttribute>, Double> totals = new HashMap<>();
		chestplate.applyAttributeModifiers(EquipmentSlot.CHEST, (attribute, modifier) -> totals.merge(attribute, modifier.value(), Double::sum));
		context.assertTrue(totals.getOrDefault(EntityAttributes.GENERIC_ARMOR, 0.0) == 6.0, "the grafted chestplate's armour is " + totals.get(EntityAttributes.GENERIC_ARMOR));
		context.assertTrue(totals.getOrDefault(EntityAttributes.GENERIC_MAX_HEALTH, 0.0) == 2.0, "Apex zombie vigor gave " + totals.get(EntityAttributes.GENERIC_MAX_HEALTH));
		context.assertTrue(totals.getOrDefault(EntityAttributes.GENERIC_BURNING_TIME, 0.0) < 0.0, "Ignited emberskin is missing");
		ItemStack scrubbed = Grafting.scrub(chestplate, 0);
		context.assertTrue(ItemStack.areEqual(scrubbed, new ItemStack(Items.IRON_CHESTPLATE)), "scrubbing left the chestplate changed: " + scrubbed.getComponentChanges());
		ItemStack sword = Grafting.scrub(grafted(new ItemStack(Items.DIAMOND_SWORD), Element.TOXIC, SoulKind.BABY_ZOMBIE, Tier.ELITE, 0.5F), 0);
		context.assertTrue(ItemStack.areEqual(sword, new ItemStack(Items.DIAMOND_SWORD)), "scrubbing left the sword changed: " + sword.getComponentChanges());
		context.complete();
	}

	@GameTest(templateName = EMPTY_STRUCTURE)
	public void theSoulForgeGraftsAndTheRagScrubs(TestContext context) {
		PlayerEntity player = context.createMockPlayer(GameMode.SURVIVAL);
		SoulForgeScreenHandler forge = new SoulForgeScreenHandler(1, player.getInventory(),
				ScreenHandlerContext.create(context.getWorld(), context.getAbsolutePos(new BlockPos(1, 2, 1))));
		ItemStack meat = new ItemStack(EvolutaItems.soulMeat(Element.PERMAFROST, SoulKind.SKELETON), 2);
		meat.set(SoulComponents.SOUL_GRADE, Tier.APEX);
		forge.getSlot(SoulForgeScreenHandler.GEAR).setStack(new ItemStack(Items.DIAMOND_SWORD));
		forge.getSlot(SoulForgeScreenHandler.MEAT).setStack(meat);
		context.assertTrue(forge.onButtonClick(player, SoulForgeScreenHandler.GRAFT), "the forge refused a graft");
		context.assertTrue(Grafting.grafts(forge.gear()).size() == 1, "no graft on the sword");
		float power = Grafting.grafts(forge.gear()).get(0).power();
		context.assertTrue(power >= 0.75F && power <= 1.0F, "an Apex graft rolled " + power);
		context.assertTrue(forge.meat().getCount() == 1, "the graft did not use one piece of meat");
		context.assertFalse(forge.onButtonClick(player, SoulForgeScreenHandler.SCRUB), "scrubbed without a rag");
		forge.getSlot(SoulForgeScreenHandler.RAG).setStack(new ItemStack(EvolutaItems.DIAMOND_SILK_RAG));
		context.assertTrue(forge.onButtonClick(player, SoulForgeScreenHandler.SCRUB), "the rag refused to scrub");
		context.assertTrue(Grafting.grafts(forge.gear()).isEmpty(), "the graft survived the rag");
		context.assertTrue(forge.rag().getDamage() == 1, "the rag's use was not counted: " + forge.rag().getDamage());
		context.complete();
	}
}
