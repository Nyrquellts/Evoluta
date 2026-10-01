package com.nyr.evoluta.client.render;

import com.nyr.evoluta.common.mutation.Element;
import com.nyr.evoluta.common.mutation.MutationData;
import com.nyr.evoluta.common.mutation.Mutations;
import com.nyr.evoluta.common.mutation.Tier;
import com.nyr.evoluta.common.soul.Graft;
import com.nyr.evoluta.common.soul.SoulComponents;
import java.util.List;
import net.minecraft.component.DataComponentTypes;
import net.minecraft.component.type.NbtComponent;
import net.minecraft.entity.LivingEntity;
import net.minecraft.item.ItemStack;
import net.minecraft.nbt.NbtElement;
import net.minecraft.util.Util;
import org.jetbrains.annotations.Nullable;

/** Client-only interpretation of existing item data and the already-synced mutation attachment. */
public final class GlintColors {
	public static final int PERMAFROST_GLINT = 0xFF00E5FF;
	public static final int TOXIC_GLINT = 0xFF76FF03;
	public static final int IGNITED_GLINT = 0xFFFF6D00;
	public static final int DEFAULT_PURPLE = 0xFF8040CC;
	/** Apex grafts shine brighter: the same hues pushed toward white. */
	public static final int PERMAFROST_APEX_GLINT = 0xFFB8F8FF;
	public static final int TOXIC_APEX_GLINT = 0xFFCCFF70;
	public static final int IGNITED_APEX_GLINT = 0xFFFFB040;
	/** An item grafted with several elements shows each in turn for this long. */
	static final long CYCLE_MS = 2000;
	/** A string inside minecraft:custom_data; no new component registry entry or packet is needed. */
	public static final String INFUSION_KEY = "evoluta:elemental_infusion";

	private GlintColors() {
	}

	public static int getGlintColor(ItemStack stack, @Nullable LivingEntity holder) {
		int grafted = graftedColor(stack.getOrDefault(SoulComponents.GRAFTS, List.of()), Util.getMeasuringTimeMs());
		if (grafted != 0) {
			return grafted;
		}
		Element infusion = infusion(stack);
		// An item infusion wins even when held by a mutant of a different element.
		if (infusion != null) {
			return color(infusion);
		}
		return resolve(null, holder == null ? null : Mutations.get(holder));
	}

	@Nullable
	static Element infusion(ItemStack stack) {
		if (stack.isEmpty()) {
			return null;
		}
		NbtComponent customData = stack.get(DataComponentTypes.CUSTOM_DATA);
		if (customData == null || !customData.contains(INFUSION_KEY)) {
			return null;
		}
		var data = customData.copyNbt();
		if (!data.contains(INFUSION_KEY, NbtElement.STRING_TYPE)) {
			return null;
		}
		Element element = Element.byKey(data.getString(INFUSION_KEY));
		return element != null && Element.IMPLEMENTED.contains(element) ? element : null;
	}

	static int resolve(@Nullable Element infusion, @Nullable MutationData mutation) {
		if (infusion != null && Element.IMPLEMENTED.contains(infusion)) {
			return color(infusion);
		}
		return mutation != null && mutation.tier() >= 1 ? color(mutation.getElement()) : DEFAULT_PURPLE;
	}

	static int color(@Nullable Element element) {
		if (element == null) {
			return DEFAULT_PURPLE;
		}
		return switch (element) {
			case PERMAFROST -> PERMAFROST_GLINT;
			case TOXIC -> TOXIC_GLINT;
			case IGNITED -> IGNITED_GLINT;
			default -> DEFAULT_PURPLE;
		};
	}

	/** The element a glint colour stands for, or null for one that is not elemental. */
	@Nullable
	static Element element(int argb) {
		if (argb == PERMAFROST_GLINT || argb == PERMAFROST_APEX_GLINT) {
			return Element.PERMAFROST;
		}
		if (argb == TOXIC_GLINT || argb == TOXIC_APEX_GLINT) {
			return Element.TOXIC;
		}
		if (argb == IGNITED_GLINT || argb == IGNITED_APEX_GLINT) {
			return Element.IGNITED;
		}
		return null;
	}

	/** Whether a glint colour is an Apex graft's brighter one. */
	static boolean isApex(int argb) {
		return argb == PERMAFROST_APEX_GLINT || argb == TOXIC_APEX_GLINT || argb == IGNITED_APEX_GLINT;
	}

	public static boolean isElemental(int argb) {
		return argb == PERMAFROST_GLINT || argb == TOXIC_GLINT || argb == IGNITED_GLINT
				|| argb == PERMAFROST_APEX_GLINT || argb == TOXIC_APEX_GLINT || argb == IGNITED_APEX_GLINT;
	}

	/**
	 * Grafted gear's glint, or 0 when it has no grafts: its strongest element's colour, brighter when an Apex graft
	 * carries it; with several elements, each in turn, strongest first, every {@link #CYCLE_MS}. No allocation beyond
	 * three small arrays, since this runs for every grafted item drawn.
	 */
	static int graftedColor(List<Graft> grafts, long timeMs) {
		if (grafts.isEmpty()) {
			return 0;
		}
		Element[] elements = new Element[grafts.size()];
		float[] strongest = new float[grafts.size()];
		boolean[] apex = new boolean[grafts.size()];
		int count = 0;
		for (Graft graft : grafts) {
			int at = 0;
			while (at < count && elements[at] != graft.element()) {
				at++;
			}
			if (at == count) {
				elements[count] = graft.element();
				strongest[count] = -1.0F;
				count++;
			}
			strongest[at] = Math.max(strongest[at], graft.power());
			apex[at] |= graft.grade() == Tier.APEX;
		}
		// strongest element first
		for (int i = 0; i < count; i++) {
			for (int j = i + 1; j < count; j++) {
				if (strongest[j] > strongest[i]) {
					Element element = elements[i];
					elements[i] = elements[j];
					elements[j] = element;
					float power = strongest[i];
					strongest[i] = strongest[j];
					strongest[j] = power;
					boolean bright = apex[i];
					apex[i] = apex[j];
					apex[j] = bright;
				}
			}
		}
		int pick = count == 1 ? 0 : (int) (timeMs / CYCLE_MS % count);
		return apex[pick] ? apexColor(elements[pick]) : color(elements[pick]);
	}

	static int apexColor(Element element) {
		return switch (element) {
			case PERMAFROST -> PERMAFROST_APEX_GLINT;
			case TOXIC -> TOXIC_APEX_GLINT;
			case IGNITED -> IGNITED_APEX_GLINT;
			default -> DEFAULT_PURPLE;
		};
	}

	/**
	 * Only the renderer receives this copy. Never enchant or change the entity's actual equipment or inventory.
	 * Already-shimmering and ordinary stacks take the allocation-free path.
	 */
	public static ItemStack forRender(ItemStack stack, @Nullable LivingEntity holder) {
		if (stack.isEmpty() || stack.hasGlint() || !isElemental(getGlintColor(stack, holder))) {
			return stack;
		}
		ItemStack visual = stack.copy();
		visual.set(DataComponentTypes.ENCHANTMENT_GLINT_OVERRIDE, true);
		return visual;
	}
}
