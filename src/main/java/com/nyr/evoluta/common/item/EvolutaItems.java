package com.nyr.evoluta.common.item;

import com.nyr.evoluta.common.Evoluta;
import com.nyr.evoluta.common.forge.SoulForge;
import com.nyr.evoluta.common.mutation.Element;
import com.nyr.evoluta.common.mutation.Tier;
import com.nyr.evoluta.common.soul.SoulComponents;
import com.nyr.evoluta.common.soul.SoulKind;
import java.util.ArrayList;
import java.util.Collections;
import java.util.EnumMap;
import java.util.List;
import java.util.Map;
import net.fabricmc.fabric.api.itemgroup.v1.FabricItemGroup;
import net.fabricmc.fabric.api.itemgroup.v1.ItemGroupEvents;
import net.minecraft.item.Item;
import net.minecraft.item.ItemGroup;
import net.minecraft.item.ItemGroups;
import net.minecraft.item.ItemStack;
import net.minecraft.item.Items;
import net.minecraft.registry.Registries;
import net.minecraft.registry.Registry;
import net.minecraft.text.Text;
import net.minecraft.util.Rarity;
import org.jetbrains.annotations.Nullable;

public final class EvolutaItems {
	/** A compass and any Soul Meat: points at the nearest champion while held. */
	public static final Item BLIGHT_LOCATOR = register("blight_locator",
			new BlightLocatorItem(new Item.Settings().maxCount(1).rarity(Rarity.UNCOMMON)));

	/** Wipes one graft off gear at the Soul Forge; three uses. */
	public static final Item DIAMOND_SILK_RAG = register("diamond_silk_rag", new DiamondSilkRagItem(new Item.Settings().maxDamage(3).rarity(Rarity.RARE)));

	/** Soul Meat by element, then kind: {@code evoluta:<element>_<kind>_soul_meat}. */
	private static final Map<Element, Map<SoulKind, SoulMeatItem>> SOUL_MEAT = new EnumMap<>(Element.class);
	private static final List<SoulMeatItem> ALL_SOUL_MEAT = new ArrayList<>();

	static {
		for (Element element : Element.IMPLEMENTED) {
			Map<SoulKind, SoulMeatItem> byKind = new EnumMap<>(SoulKind.class);
			for (SoulKind kind : SoulKind.ALL) {
				SoulMeatItem item = (SoulMeatItem) register(element.key() + "_" + kind.key() + "_soul_meat",
						new SoulMeatItem(kind, element, new Item.Settings()));
				byKind.put(kind, item);
				ALL_SOUL_MEAT.add(item);
			}
			SOUL_MEAT.put(element, byKind);
		}
	}

	public static final ItemGroup GROUP = Registry.register(Registries.ITEM_GROUP, Evoluta.id("evoluta"), FabricItemGroup.builder()
			.icon(() -> {
				ItemStack icon = new ItemStack(soulMeat(Element.IGNITED, SoulKind.ZOMBIE));
				icon.set(SoulComponents.SOUL_GRADE, Tier.APEX);
				return icon;
			})
			.displayName(Text.translatable("itemGroup.evoluta"))
			.entries((context, entries) -> {
				entries.add(SoulForge.ITEM);
				entries.add(BLIGHT_LOCATOR);
				entries.add(DIAMOND_SILK_RAG);
				for (Tier grade : Tier.values()) {
					for (SoulMeatItem meat : ALL_SOUL_MEAT) {
						ItemStack stack = new ItemStack(meat);
						stack.set(SoulComponents.SOUL_GRADE, grade);
						entries.add(stack);
					}
				}
			})
			.build());

	private EvolutaItems() {
	}

	/** The Soul Meat of that element and kind, or null for an element without one (none, Abyssal). */
	@Nullable
	public static SoulMeatItem soulMeat(Element element, SoulKind kind) {
		Map<SoulKind, SoulMeatItem> byKind = SOUL_MEAT.get(element);
		return byKind == null ? null : byKind.get(kind);
	}

	public static List<SoulMeatItem> allSoulMeat() {
		return Collections.unmodifiableList(ALL_SOUL_MEAT);
	}

	/** Loads this class, which registers the items above, and lists them in the creative tabs. */
	public static void register() {
		ItemGroupEvents.modifyEntriesEvent(ItemGroups.TOOLS).register(entries -> entries.addAfter(Items.RECOVERY_COMPASS, BLIGHT_LOCATOR));
	}

	private static Item register(String path, Item item) {
		return Registry.register(Registries.ITEM, Evoluta.id(path), item);
	}
}
