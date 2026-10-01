package com.nyr.evoluta.client.render;

import static org.junit.jupiter.api.Assertions.*;
import com.nyr.evoluta.common.mutation.Archetype;
import com.nyr.evoluta.common.mutation.Element;
import com.nyr.evoluta.common.mutation.MutationData;
import com.nyr.evoluta.common.mutation.Tier;
import com.nyr.evoluta.common.soul.Graft;
import com.nyr.evoluta.common.soul.SoulComponents;
import com.nyr.evoluta.common.soul.SoulKind;
import java.util.List;
import net.minecraft.Bootstrap;
import net.minecraft.SharedConstants;
import net.minecraft.component.DataComponentTypes;
import net.minecraft.component.type.NbtComponent;
import net.minecraft.item.ItemStack;
import net.minecraft.item.Items;
import net.minecraft.nbt.NbtCompound;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.Test;

class GlintColorsTest {
	@BeforeAll
	static void bootstrap() {
		SharedConstants.createGameVersion();
		Bootstrap.initialize();
		// no mod initialiser runs in a unit test: register Evoluta's item components where Fabric would, after bootstrap
		SoulComponents.register();
	}

	private static ItemStack grafted(Graft... grafts) {
		ItemStack stack = new ItemStack(Items.IRON_SWORD);
		stack.set(SoulComponents.GRAFTS, List.of(grafts));
		return stack;
	}

	@Test
	void graftsDriveTheGlintAndOutrankAnInfusion() {
		ItemStack stack = grafted(new Graft(SoulKind.ZOMBIE, Element.TOXIC, Tier.ELITE, 0.6F));
		assertEquals(GlintColors.TOXIC_GLINT, GlintColors.graftedColor(stack.get(SoulComponents.GRAFTS), 0));
		NbtCompound data = new NbtCompound();
		data.putString(GlintColors.INFUSION_KEY, "ignited");
		stack.set(DataComponentTypes.CUSTOM_DATA, NbtComponent.of(data));
		assertEquals(GlintColors.TOXIC_GLINT, GlintColors.getGlintColor(stack, null), "a graft outranks the old infusion key");
		assertEquals(0, GlintColors.graftedColor(List.of(), 0), "no grafts, no grafted glint");
	}

	@Test
	void apexGraftsShineBrighter() {
		assertEquals(GlintColors.PERMAFROST_APEX_GLINT,
				GlintColors.graftedColor(List.of(new Graft(SoulKind.STRAY, Element.PERMAFROST, Tier.APEX, 0.9F)), 0));
		assertTrue(GlintColors.isElemental(GlintColors.PERMAFROST_APEX_GLINT), "the renderer must accept Apex colours");
	}

	@Test
	void severalElementsTakeTurnsStrongestFirst() {
		List<Graft> grafts = List.of(new Graft(SoulKind.SPIDER, Element.IGNITED, Tier.EVOLVED, 0.3F),
				new Graft(SoulKind.SPIDER, Element.PERMAFROST, Tier.ELITE, 0.7F));
		assertEquals(GlintColors.PERMAFROST_GLINT, GlintColors.graftedColor(grafts, 0), "the strongest element shows first");
		assertEquals(GlintColors.IGNITED_GLINT, GlintColors.graftedColor(grafts, GlintColors.CYCLE_MS), "then the next");
		assertEquals(GlintColors.PERMAFROST_GLINT, GlintColors.graftedColor(grafts, 2 * GlintColors.CYCLE_MS), "and round again");
	}

	private static ItemStack infused(String element) {
		ItemStack stack = new ItemStack(Items.IRON_SWORD);
		NbtCompound data = new NbtCompound();
		data.putString(GlintColors.INFUSION_KEY, element);
		data.putString("unrelated", "preserved");
		stack.set(DataComponentTypes.CUSTOM_DATA, NbtComponent.of(data));
		return stack;
	}

	@Test
	void allInfusionsUseTheExactRequestedColorsWithoutAHolder() {
		assertEquals(0xFF00E5FF, GlintColors.getGlintColor(infused("permafrost"), null));
		assertEquals(0xFF76FF03, GlintColors.getGlintColor(infused("toxic"), null));
		assertEquals(0xFFFF6D00, GlintColors.getGlintColor(infused("ignited"), null));
	}

	@Test
	void allMutationTiersUseTheirElementAndInfusionWinsOverTheHolder() {
		for (Tier tier : Tier.values()) {
			for (Element element : Element.IMPLEMENTED) {
				MutationData data = MutationData.of(tier, element, Archetype.NONE);
				assertEquals(GlintColors.color(element), GlintColors.resolve(null, data));
				for (Element infusion : Element.IMPLEMENTED) {
					assertEquals(GlintColors.color(infusion), GlintColors.resolve(infusion, data));
				}
			}
		}
	}

	@Test
	void ordinaryMissingMalformedAndReservedDataFallBack() {
		assertEquals(0xFF8040CC, GlintColors.getGlintColor(ItemStack.EMPTY, null));
		assertEquals(0xFF8040CC, GlintColors.getGlintColor(new ItemStack(Items.IRON_SWORD), null));
		for (String invalid : new String[]{"", "unknown", "none", "abyssal"}) {
			assertEquals(0xFF8040CC, GlintColors.getGlintColor(infused(invalid), null));
		}
		ItemStack malformed = infused("toxic");
		NbtCompound data = new NbtCompound();
		data.putInt(GlintColors.INFUSION_KEY, 3);
		malformed.set(DataComponentTypes.CUSTOM_DATA, NbtComponent.of(data));
		assertEquals(0xFF8040CC, GlintColors.getGlintColor(malformed, null));
		for (Element element : new Element[]{Element.NONE, Element.ABYSSAL}) {
			assertEquals(0xFF8040CC, GlintColors.resolve(null, MutationData.of(Tier.APEX, element, Archetype.NONE)));
		}
		assertEquals(GlintColors.TOXIC_GLINT,
				GlintColors.resolve(GlintColors.infusion(malformed), MutationData.of(Tier.EVOLVED, Element.TOXIC, Archetype.NONE)));
	}

	@Test
	void visualCopyShimmersWithoutChangingSavedStackOrEnchantments() {
		ItemStack original = infused("ignited");
		original.setCount(2);
		original.set(DataComponentTypes.ENCHANTMENT_GLINT_OVERRIDE, false);
		ItemStack snapshot = original.copy();
		ItemStack visual = GlintColors.forRender(original, null);
		assertNotSame(original, visual);
		assertTrue(visual.hasGlint());
		assertFalse(original.hasGlint());
		assertTrue(ItemStack.areEqual(snapshot, original));
		assertEquals(2, visual.getCount());
		assertEquals(original.getEnchantments(), visual.getEnchantments());
		assertEquals(original.get(DataComponentTypes.CUSTOM_DATA), visual.get(DataComponentTypes.CUSTOM_DATA));
		assertSame(visual, GlintColors.forRender(visual, null));
	}

	@Test
	void ordinaryAndAlreadyGlintingItemsKeepIdentityAndComponents() {
		ItemStack ordinary = new ItemStack(Items.IRON_SWORD);
		assertSame(ordinary, GlintColors.forRender(ordinary, null));
		assertSame(ItemStack.EMPTY, GlintColors.forRender(ItemStack.EMPTY, null));
		ItemStack infused = infused("permafrost");
		infused.set(DataComponentTypes.ENCHANTMENT_GLINT_OVERRIDE, true);
		assertSame(infused, GlintColors.forRender(infused, null));
		ordinary.set(DataComponentTypes.ENCHANTMENT_GLINT_OVERRIDE, true);
		assertEquals(GlintColors.DEFAULT_PURPLE, GlintColors.getGlintColor(ordinary, null));
		assertSame(ordinary, GlintColors.forRender(ordinary, null));
	}

	@Test
	void eachGlintColourNamesItsElementAndGrade() {
		assertEquals(Element.PERMAFROST, GlintColors.element(GlintColors.PERMAFROST_APEX_GLINT));
		assertEquals(Element.TOXIC, GlintColors.element(GlintColors.TOXIC_GLINT));
		assertEquals(Element.IGNITED, GlintColors.element(GlintColors.IGNITED_APEX_GLINT));
		assertEquals(null, GlintColors.element(GlintColors.DEFAULT_PURPLE));
		assertTrue(GlintColors.isApex(GlintColors.TOXIC_APEX_GLINT));
		assertFalse(GlintColors.isApex(GlintColors.TOXIC_GLINT));
	}
}
