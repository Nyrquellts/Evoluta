package com.nyr.evoluta.common.soul;

import com.nyr.evoluta.common.item.EvolutaItems;
import com.nyr.evoluta.common.item.SoulMeatItem;
import com.nyr.evoluta.common.mutation.EvolutaAttachments;
import com.nyr.evoluta.common.mutation.MutationData;
import net.minecraft.entity.EquipmentSlot;
import net.minecraft.entity.mob.MobEntity;
import net.minecraft.item.ItemStack;
import net.minecraft.util.math.random.Random;

/**
 * Champions fight with mutated gear: every graftable piece an Elite or Apex mutant wears or holds carries one graft of
 * its own kind and element, rolled at its tier. It works in the mutant's hands (its hits carry the graft), shimmers
 * on it, and drops at vanilla's equipment chances (8.5%, more with Looting): rare loot, never a new texture. Done
 * once, the first time the mutant loads, after its kind has handed it whatever gear it spawns with.
 */
public final class MutantGear {
	private MutantGear() {
	}

	public static void graftOnce(MobEntity mob, MutationData data, Random random) {
		if (!data.isChampion() || mob.hasAttached(EvolutaAttachments.GEAR_GRAFTED)) {
			return;
		}
		mob.setAttached(EvolutaAttachments.GEAR_GRAFTED, true);
		SoulKind kind = SoulKind.of(mob);
		SoulMeatItem meat = kind == null ? null : EvolutaItems.soulMeat(data.getElement(), kind);
		if (meat == null) {
			return;
		}
		for (EquipmentSlot slot : EquipmentSlot.values()) {
			ItemStack stack = mob.getEquippedStack(slot);
			if (Grafting.canGraft(stack) && Grafting.grafts(stack).isEmpty()) {
				mob.equipStack(slot, Grafting.graft(stack, meat, data.getTier(), Graft.roll(data.getTier(), random)));
			}
		}
	}
}
