package com.nyr.evoluta.common.mutation;

import com.nyr.evoluta.common.Evoluta;
import net.minecraft.component.DataComponentTypes;
import net.minecraft.component.type.AttributeModifierSlot;
import net.minecraft.component.type.AttributeModifiersComponent;
import net.minecraft.entity.attribute.EntityAttributeModifier;
import net.minecraft.entity.attribute.EntityAttributes;
import net.minecraft.item.ItemStack;
import net.minecraft.item.Items;
import net.minecraft.text.Text;
import net.minecraft.util.Identifier;

/**
 * The Brute skeleton's melee weapon: a vanilla bone with a name and an attack bonus in its components, so it needs
 * no new item, texture or registry entry.
 */
public final class BoneDagger {
	public static final Identifier MODIFIER_ID = Evoluta.id("bone_dagger");
	public static final double ATTACK_DAMAGE = 3.0;

	private BoneDagger() {
	}

	public static ItemStack create() {
		ItemStack stack = new ItemStack(Items.BONE);
		stack.set(DataComponentTypes.ITEM_NAME, Text.translatable("item.evoluta.bone_dagger"));
		stack.set(DataComponentTypes.ATTRIBUTE_MODIFIERS, AttributeModifiersComponent.builder()
				.add(EntityAttributes.GENERIC_ATTACK_DAMAGE,
						new EntityAttributeModifier(MODIFIER_ID, ATTACK_DAMAGE, EntityAttributeModifier.Operation.ADD_VALUE),
						AttributeModifierSlot.MAINHAND)
				.build());
		return stack;
	}

	public static boolean is(ItemStack stack) {
		if (!stack.isOf(Items.BONE)) {
			return false;
		}
		AttributeModifiersComponent modifiers = stack.get(DataComponentTypes.ATTRIBUTE_MODIFIERS);
		return modifiers != null && modifiers.modifiers().stream().anyMatch(entry -> entry.modifier().id().equals(MODIFIER_ID));
	}
}
