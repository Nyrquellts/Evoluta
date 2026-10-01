package com.nyr.evoluta.common.item;

import com.nyr.evoluta.common.mutation.Element;
import com.nyr.evoluta.common.mutation.Tier;
import com.nyr.evoluta.common.soul.SoulComponents;
import com.nyr.evoluta.common.soul.SoulKind;
import java.util.List;
import net.minecraft.item.Item;
import net.minecraft.item.ItemStack;
import net.minecraft.item.tooltip.TooltipType;
import net.minecraft.text.Text;
import net.minecraft.text.TextColor;
import net.minecraft.util.Formatting;

/**
 * A mutant's Soul Meat: one item per kind of creature and element (an Ignited Skeleton's, a Toxic Drowned's...),
 * carrying the grade of the mutant it came from in {@link SoulComponents#SOUL_GRADE}. Grafted onto gear it passes on
 * the kind's ability and the element's damage (docs/soul-meat.md).
 */
public final class SoulMeatItem extends Item {
	private final SoulKind kind;
	private final Element element;

	public SoulMeatItem(SoulKind kind, Element element, Settings settings) {
		super(settings.component(SoulComponents.SOUL_GRADE, Tier.EVOLVED));
		this.kind = kind;
		this.element = element;
	}

	public SoulKind kind() {
		return this.kind;
	}

	public Element element() {
		return this.element;
	}

	public static Tier grade(ItemStack stack) {
		return stack.getOrDefault(SoulComponents.SOUL_GRADE, Tier.EVOLVED);
	}

	/** The element's colour, as its name, its glow and its glint show it. */
	public static int color(Element element) {
		return switch (element) {
			case IGNITED -> 0xFF8A2A;
			case PERMAFROST -> 0x8FEFFF;
			case TOXIC -> 0x9CFF45;
			case ABYSSAL -> 0xB070FF;
			case NONE -> 0xE8E2D0;
		};
	}

	/** "Ignited Skeleton Soul Meat", in the element's colour. */
	@Override
	public Text getName() {
		return Text.translatable("item.evoluta.soul_meat", Text.translatable("evoluta.element." + this.element.key()),
				Text.translatable("evoluta.soul_kind." + this.kind.key())).styled(style -> style.withColor(TextColor.fromRgb(color(this.element))));
	}

	@Override
	public Text getName(ItemStack stack) {
		return this.getName();
	}

	@Override
	public void appendTooltip(ItemStack stack, TooltipContext context, List<Text> tooltip, TooltipType type) {
		Tier grade = grade(stack);
		tooltip.add(Text.translatable("item.evoluta.soul_meat.grade", Text.translatable("evoluta.tier." + grade.key()), stars(grade))
				.formatted(switch (grade) {
					case EVOLVED -> Formatting.GRAY;
					case ELITE -> Formatting.YELLOW;
					case APEX -> Formatting.GOLD;
				}));
		tooltip.add(Text.translatable("item.evoluta.soul_meat.flavor").formatted(Formatting.DARK_GRAY, Formatting.ITALIC));
	}

	private static String stars(Tier grade) {
		return switch (grade) {
			case EVOLVED -> "★☆☆";
			case ELITE -> "★★☆";
			case APEX -> "★★★";
		};
	}
}
