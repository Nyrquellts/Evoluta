package com.nyr.evoluta.gametest;

import com.nyr.evoluta.common.Evoluta;
import com.nyr.evoluta.common.config.EvolutaConfig;
import com.nyr.evoluta.common.item.EvolutaItems;
import com.nyr.evoluta.common.item.SoulMeatItem;
import com.nyr.evoluta.common.mutation.Archetype;
import com.nyr.evoluta.common.mutation.Element;
import com.nyr.evoluta.common.mutation.Tier;
import com.nyr.evoluta.common.soul.SoulKind;
import com.nyr.evoluta.common.tag.EvolutaTags;
import java.util.List;
import net.fabricmc.fabric.api.gametest.v1.FabricGameTest;
import net.minecraft.entity.EntityType;
import net.minecraft.entity.ExperienceOrbEntity;
import net.minecraft.entity.ItemEntity;
import net.minecraft.entity.mob.ZombieEntity;
import net.minecraft.entity.player.PlayerEntity;
import net.minecraft.item.ItemStack;
import net.minecraft.item.Items;
import net.minecraft.recipe.CraftingRecipe;
import net.minecraft.recipe.RecipeEntry;
import net.minecraft.recipe.input.CraftingRecipeInput;
import net.minecraft.test.GameTest;
import net.minecraft.test.TestContext;
import net.minecraft.util.math.Box;

/** Step 5: what champions drop, and the recipe and tag that make the Blight Locator. */
public final class RewardsGameTest implements FabricGameTest {
	@GameTest(templateName = EMPTY_STRUCTURE)
	public void anApexKilledByAPlayerDropsBonusExperience(TestContext context) {
		ZombieEntity apex = Mutants.still(context, EntityType.ZOMBIE, 4, 4, Tier.APEX, Element.NONE, Archetype.NONE);
		PlayerEntity player = Mutants.target(context, 4, 6, 180);
		Box around = apex.getBoundingBox().expand(1.5);
		apex.damage(context.getWorld().getDamageSources().playerAttack(player), 1_000);
		context.assertFalse(apex.isAlive(), "setup: the Apex survived");

		int experience = context.getWorld().getEntitiesByClass(ExperienceOrbEntity.class, around, orb -> true)
				.stream().mapToInt(ExperienceOrbEntity::getExperienceAmount).sum();
		int expected = 5 + (int) EvolutaConfig.get().xpBonus().apex();
		context.assertTrue(experience == expected, "a bare zombie drops 5 experience, an Apex " + expected + "; dropped " + experience);
		context.complete();
	}

	@GameTest(templateName = EMPTY_STRUCTURE)
	public void championsThatDieWithoutAPlayerDropNothingExtra(TestContext context) {
		// an elemental Apex: its Soul Meat needs nothing but a player kill, so any meat here means that condition failed
		ZombieEntity apex = Mutants.still(context, EntityType.ZOMBIE, 4, 4, Tier.APEX, Element.IGNITED, Archetype.NONE);
		Box around = apex.getBoundingBox().expand(1.5);
		apex.kill();
		List<ItemEntity> meat = context.getWorld().getEntitiesByClass(ItemEntity.class, around, item -> item.getStack().getItem() instanceof SoulMeatItem);
		context.assertTrue(meat.isEmpty(), "a champion no player killed dropped Soul Meat");
		context.assertTrue(context.getWorld().getEntitiesByClass(ExperienceOrbEntity.class, around, orb -> true).isEmpty(),
				"a mob no player hit dropped experience");
		context.complete();
	}

	@GameTest(templateName = EMPTY_STRUCTURE)
	public void aCompassAndSoulMeatCraftTheLocator(TestContext context) {
		RecipeEntry<?> entry = context.getWorld().getRecipeManager().get(Evoluta.id("blight_locator")).orElse(null);
		context.assertTrue(entry != null && entry.value() instanceof CraftingRecipe, "no crafting recipe evoluta:blight_locator");
		CraftingRecipe recipe = (CraftingRecipe) entry.value();
		CraftingRecipeInput input = CraftingRecipeInput.create(2, 1, List.of(new ItemStack(EvolutaItems.soulMeat(Element.TOXIC, SoulKind.SPIDER)), new ItemStack(Items.COMPASS)));
		context.assertTrue(recipe.matches(input, context.getWorld()), "compass and Soul Meat do not match the recipe");
		ItemStack result = recipe.craft(input, context.getWorld().getRegistryManager());
		context.assertTrue(result.isOf(EvolutaItems.BLIGHT_LOCATOR), "the recipe made " + result);
		CraftingRecipeInput wrong = CraftingRecipeInput.create(2, 1, List.of(new ItemStack(Items.GHAST_TEAR), new ItemStack(Items.COMPASS)));
		context.assertFalse(recipe.matches(wrong, context.getWorld()), "a ghast tear passes for Soul Meat");
		context.assertTrue(context.getWorld().getServer().getAdvancementLoader().get(Evoluta.id("recipes/tools/blight_locator")) != null,
				"the recipe-unlock advancement did not load");
		context.complete();
	}

	@GameTest(templateName = EMPTY_STRUCTURE)
	public void theBlightLocatorIsAChampionLocator(TestContext context) {
		ItemStack locator = new ItemStack(EvolutaItems.BLIGHT_LOCATOR);
		context.assertTrue(locator.isIn(EvolutaTags.LOCATORS), "the Blight Locator is not in #evoluta:champion_locators");
		context.assertTrue(locator.getMaxCount() == 1, "the Blight Locator stacks");
		context.complete();
	}
}
