package com.nyr.evoluta.client;

import com.nyr.evoluta.common.item.SoulMeatItem;
import com.nyr.evoluta.common.mutation.Tier;
import com.nyr.evoluta.common.soul.Graft;
import com.nyr.evoluta.common.soul.Grafting;
import com.nyr.evoluta.common.soul.SoulComponents;
import com.nyr.evoluta.common.soul.ToolGrafts;
import java.util.ArrayList;
import java.util.List;
import java.util.Locale;
import net.fabricmc.fabric.api.client.item.v1.ItemTooltipCallback;
import net.minecraft.item.ItemStack;
import net.minecraft.text.MutableText;
import net.minecraft.text.Text;
import net.minecraft.text.TextColor;
import net.minecraft.util.Formatting;

/**
 * Grafted gear lists its grafts under its name, strongest first: the element and kind in the element's colour, the
 * grade's stars and how good the roll was within it, then what the graft does on this item. Client-only text read
 * from the synced grafts component.
 */
final class SoulTooltips {
	private SoulTooltips() {
	}

	static void register() {
		ItemTooltipCallback.EVENT.register((stack, context, type, lines) -> {
			if (stack.contains(SoulComponents.GRAFTS) && !lines.isEmpty()) {
				lines.addAll(1, describe(stack));
			}
		});
	}

	static List<Text> describe(ItemStack stack) {
		List<Text> lines = new ArrayList<>();
		boolean armor = Grafting.isArmor(stack);
		for (Grafting.Weighted weighted : Grafting.weighted(Grafting.grafts(stack))) {
			Graft graft = weighted.graft();
			int color = SoulMeatItem.color(graft.element());
			MutableText header = Text.literal("◆ ").append(Text.translatable("evoluta.element." + graft.element().key()))
					.append(" ").append(Text.translatable("evoluta.soul_kind." + graft.kind().key()))
					.styled(style -> style.withColor(TextColor.fromRgb(color)));
			header.append(Text.literal("  " + stars(graft.grade())).formatted(Formatting.GOLD));
			header.append(Text.literal(" " + Math.round(graft.quality() * 100) + "%").formatted(Formatting.GRAY));
			lines.add(header);
			boolean tool = Grafting.isTool(stack);
			boolean fights = armor || Grafting.isWeapon(stack);
			if (fights) {
				String what = armor ? "armor" : "weapon";
				MutableText effect = Text.literal("   ").append(Text.translatable("evoluta.graft." + what + "." + graft.kind().key()));
				if (armor) {
					effect.append(" · ").append(Text.translatable("evoluta.graft.retaliate." + graft.element().key()));
				} else {
					float bonus = (0.5F + 1.5F * graft.power()) * weighted.weight();
					effect.append(" · ").append(Text.translatable("evoluta.graft.damage." + graft.element().key(),
							String.format(Locale.ROOT, "%.1f", bonus)));
				}
				lines.add(overlap(effect, weighted));
			}
			if (tool) {
				lines.add(overlap(mining(weighted), weighted));
			}
		}
		return lines;
	}

	/** A mining graft's line: its creature's ability, then its element's effect, with their numbers. */
	private static MutableText mining(Grafting.Weighted weighted) {
		List<Grafting.Weighted> one = List.of(weighted);
		Graft graft = weighted.graft();
		Object value = switch (graft.kind()) {
			case HUSK -> percent(ToolGrafts.sandsifter(one) - 1.0F);
			case ZOMBIE_VILLAGER -> percent(ToolGrafts.prospectChance(one));
			case BOGGED -> percent(ToolGrafts.bountyChance(one));
			case CREEPER -> percent(ToolGrafts.blastChance(one));
			case SPIDER -> percent(ToolGrafts.silkChance(one));
			case CAVE_SPIDER -> ToolGrafts.tunnelRange(one);
			default -> "";
		};
		MutableText line = Text.literal("   ").append(Text.translatable("evoluta.graft.tool." + graft.kind().key(), value));
		line.append(" · ").append(Text.translatable("evoluta.graft.tool_element." + graft.element().key(), percent(ToolGrafts.smeltChance(one))));
		return line;
	}

	private static MutableText overlap(MutableText line, Grafting.Weighted weighted) {
		if (weighted.weight() < 1.0F) {
			line.append(" · ").append(Text.translatable("evoluta.graft.overlap", Math.round(weighted.weight() * 100)));
		}
		return line.formatted(Formatting.DARK_GRAY);
	}

	private static int percent(float share) {
		return Math.round(share * 100);
	}

	private static String stars(Tier grade) {
		return switch (grade) {
			case EVOLVED -> "★☆☆";
			case ELITE -> "★★☆";
			case APEX -> "★★★";
		};
	}
}
