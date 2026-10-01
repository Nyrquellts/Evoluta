package com.nyr.evoluta.common.item;

import java.util.List;
import net.minecraft.item.Item;
import net.minecraft.item.ItemStack;
import net.minecraft.item.tooltip.TooltipType;
import net.minecraft.text.Text;
import net.minecraft.util.Formatting;

/**
 * Held in either hand, points at the nearest living Elite or Apex mutant and clicks faster as it closes in. The
 * server side is {@code LocatorSync} (any item in {@code #evoluta:champion_locators} works); the needle and the
 * clicks are drawn and played on the client.
 */
public final class BlightLocatorItem extends Item {
	public BlightLocatorItem(Settings settings) {
		super(settings);
	}

	@Override
	public void appendTooltip(ItemStack stack, Item.TooltipContext context, List<Text> tooltip, TooltipType type) {
		tooltip.add(Text.translatable("item.evoluta.blight_locator.tooltip").formatted(Formatting.GRAY));
		tooltip.add(Text.translatable("item.evoluta.blight_locator.tooltip.clicks").formatted(Formatting.DARK_GRAY));
	}
}
