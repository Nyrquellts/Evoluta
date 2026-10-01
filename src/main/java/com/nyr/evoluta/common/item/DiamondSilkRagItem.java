package com.nyr.evoluta.common.item;

import java.util.List;
import net.minecraft.item.Item;
import net.minecraft.item.ItemStack;
import net.minecraft.item.tooltip.TooltipType;
import net.minecraft.text.Text;
import net.minecraft.util.Formatting;

/** The Diamond Silk Rag: at the Soul Forge it wipes one graft off gear, a use each time, three uses in all. */
public final class DiamondSilkRagItem extends Item {
	public DiamondSilkRagItem(Settings settings) {
		super(settings);
	}

	@Override
	public void appendTooltip(ItemStack stack, TooltipContext context, List<Text> tooltip, TooltipType type) {
		tooltip.add(Text.translatable("item.evoluta.diamond_silk_rag.flavor").formatted(Formatting.GRAY));
	}
}
