package com.nyr.evoluta.gametest;

import com.nyr.evoluta.common.Evoluta;
import com.nyr.evoluta.common.item.EvolutaItems;
import com.nyr.evoluta.common.item.SoulMeatItem;
import com.nyr.evoluta.common.mutation.Archetype;
import com.nyr.evoluta.common.mutation.Element;
import com.nyr.evoluta.common.mutation.Tier;
import com.nyr.evoluta.common.soul.SoulComponents;
import com.nyr.evoluta.common.soul.SoulKind;
import java.util.List;
import net.fabricmc.fabric.api.gametest.v1.FabricGameTest;
import net.minecraft.entity.EntityType;
import net.minecraft.entity.ItemEntity;
import net.minecraft.entity.mob.MobEntity;
import net.minecraft.entity.mob.ZombieEntity;
import net.minecraft.entity.player.PlayerEntity;
import net.minecraft.item.ItemStack;
import net.minecraft.registry.RegistryKeys;
import net.minecraft.registry.tag.TagKey;
import net.minecraft.test.GameTest;
import net.minecraft.test.TestContext;
import net.minecraft.text.TranslatableTextContent;
import net.minecraft.util.math.Box;

/** Soul Meat: every mutant kind drops its own, graded by its tier, only for a player's kill. */
public final class SoulMeatGameTest implements FabricGameTest {
	@GameTest(templateName = EMPTY_STRUCTURE)
	public void anApexDropsSoulMeatOfItsKindElementAndGrade(TestContext context) {
		MobEntity skeleton = Mutants.still(context, EntityType.SKELETON, 4, 4, Tier.APEX, Element.PERMAFROST, Archetype.NONE);
		List<ItemStack> meat = killByPlayer(context, skeleton);
		context.assertTrue(!meat.isEmpty(), "an Apex Permafrost skeleton killed by a player dropped no Soul Meat");
		for (ItemStack stack : meat) {
			context.assertTrue(stack.isOf(EvolutaItems.soulMeat(Element.PERMAFROST, SoulKind.SKELETON)), "dropped " + stack);
			context.assertTrue(stack.get(SoulComponents.SOUL_GRADE) == Tier.APEX, "Apex meat graded " + stack.get(SoulComponents.SOUL_GRADE));
		}
		int count = meat.stream().mapToInt(ItemStack::getCount).sum();
		context.assertTrue(count >= 1 && count <= 2, "an Apex drops 1-2 Soul Meat without Looting, dropped " + count);
		context.complete();
	}

	@GameTest(templateName = EMPTY_STRUCTURE)
	public void aBabyZombieDropsBabySoulMeat(TestContext context) {
		ZombieEntity baby = Mutants.still(context, EntityType.ZOMBIE, 4, 4, Tier.APEX, Element.TOXIC, Archetype.NONE);
		baby.setBaby(true);
		List<ItemStack> meat = killByPlayer(context, baby);
		context.assertTrue(!meat.isEmpty() && meat.stream().allMatch(stack -> stack.isOf(EvolutaItems.soulMeat(Element.TOXIC, SoulKind.BABY_ZOMBIE))),
				"a baby Toxic zombie dropped " + meat);
		context.complete();
	}

	@GameTest(templateName = EMPTY_STRUCTURE)
	public void aMutantWithoutAnElementDropsNoSoulMeat(TestContext context) {
		MobEntity zombie = Mutants.still(context, EntityType.ZOMBIE, 4, 4, Tier.APEX, Element.NONE, Archetype.NONE);
		List<ItemStack> meat = killByPlayer(context, zombie);
		context.assertTrue(meat.isEmpty(), "an element-less Apex dropped Soul Meat: " + meat);
		context.complete();
	}

	/** Every vanilla mob in the kind tags maps to its kind, and every kind and element has an item named for both. */
	@GameTest(templateName = EMPTY_STRUCTURE)
	public void everyKindHasItsTagAndItsItems(TestContext context) {
		assertKind(context, EntityType.HUSK, SoulKind.HUSK);
		assertKind(context, EntityType.DROWNED, SoulKind.DROWNED);
		assertKind(context, EntityType.ZOMBIE_VILLAGER, SoulKind.ZOMBIE_VILLAGER);
		assertKind(context, EntityType.STRAY, SoulKind.STRAY);
		assertKind(context, EntityType.BOGGED, SoulKind.BOGGED);
		assertKind(context, EntityType.CREEPER, SoulKind.CREEPER);
		assertKind(context, EntityType.SPIDER, SoulKind.SPIDER);
		assertKind(context, EntityType.CAVE_SPIDER, SoulKind.CAVE_SPIDER);
		context.assertTrue(EvolutaItems.allSoulMeat().size() == Element.IMPLEMENTED.size() * SoulKind.ALL.size(),
				"expected one Soul Meat per element and kind, found " + EvolutaItems.allSoulMeat().size());
		for (SoulMeatItem meat : EvolutaItems.allSoulMeat()) {
			ItemStack stack = new ItemStack(meat);
			context.assertTrue(stack.get(SoulComponents.SOUL_GRADE) == Tier.EVOLVED, meat + " is not Evolved by default");
			context.assertTrue(stack.getName().getContent() instanceof TranslatableTextContent text && text.getArgs().length == 2,
					meat + " is not named for its element and kind: " + stack.getName());
			context.assertTrue(stack.isIn(TagKey.of(RegistryKeys.ITEM, Evoluta.id("soul_meat/" + meat.kind().key()))),
					meat + " is missing from its kind's item tag");
		}
		context.complete();
	}

	private static void assertKind(TestContext context, EntityType<? extends MobEntity> type, SoulKind kind) {
		MobEntity mob = context.spawnMob(type, 1, 2, 1);
		context.assertTrue(SoulKind.of(mob) == kind, type + " maps to " + SoulKind.of(mob) + ", not " + kind);
		mob.discard();
	}

	private static List<ItemStack> killByPlayer(TestContext context, MobEntity mob) {
		PlayerEntity player = Mutants.target(context, 4, 6, 180);
		Box around = mob.getBoundingBox().expand(1.5);
		mob.damage(context.getWorld().getDamageSources().playerAttack(player), 1_000);
		context.assertFalse(mob.isAlive(), "setup: the mutant survived");
		return context.getWorld().getEntitiesByClass(ItemEntity.class, around, item -> item.getStack().getItem() instanceof SoulMeatItem)
				.stream().map(ItemEntity::getStack).toList();
	}
}
