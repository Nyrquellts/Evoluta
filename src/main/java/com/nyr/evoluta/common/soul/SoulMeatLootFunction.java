package com.nyr.evoluta.common.soul;

import com.mojang.serialization.MapCodec;
import com.mojang.serialization.codecs.RecordCodecBuilder;
import com.nyr.evoluta.common.Evoluta;
import com.nyr.evoluta.common.item.EvolutaItems;
import com.nyr.evoluta.common.item.SoulMeatItem;
import com.nyr.evoluta.common.mutation.MutationData;
import com.nyr.evoluta.common.mutation.Mutations;
import java.util.List;
import java.util.Set;
import net.minecraft.entity.Entity;
import net.minecraft.entity.LivingEntity;
import net.minecraft.item.ItemStack;
import net.minecraft.loot.condition.LootCondition;
import net.minecraft.loot.context.LootContext;
import net.minecraft.loot.context.LootContextParameter;
import net.minecraft.loot.context.LootContextParameters;
import net.minecraft.loot.function.ConditionalLootFunction;
import net.minecraft.loot.function.LootFunctionType;
import net.minecraft.registry.Registries;
import net.minecraft.registry.Registry;

/**
 * Loot function {@code evoluta:soul_meat}: turns the entry's item into the Soul Meat of the mutant being looted, of its
 * kind and element, graded by its tier, keeping the count. A mob with no soul kind or no element drops nothing from
 * the entry. The mutant loot tables name any Soul Meat as the entry's item; this function decides which one.
 */
public final class SoulMeatLootFunction extends ConditionalLootFunction {
	public static final MapCodec<SoulMeatLootFunction> CODEC = RecordCodecBuilder.mapCodec(instance ->
			addConditionsField(instance).apply(instance, SoulMeatLootFunction::new));
	public static final LootFunctionType<SoulMeatLootFunction> TYPE = Registry.register(Registries.LOOT_FUNCTION_TYPE,
			Evoluta.id("soul_meat"), new LootFunctionType<>(CODEC));

	private SoulMeatLootFunction(List<LootCondition> conditions) {
		super(conditions);
	}

	/** Loads this class, which registers the function type. */
	public static void register() {
	}

	@Override
	public LootFunctionType<SoulMeatLootFunction> getType() {
		return TYPE;
	}

	@Override
	public Set<LootContextParameter<?>> getRequiredParameters() {
		return Set.of(LootContextParameters.THIS_ENTITY);
	}

	@Override
	protected ItemStack process(ItemStack stack, LootContext context) {
		Entity entity = context.get(LootContextParameters.THIS_ENTITY);
		if (!(entity instanceof LivingEntity living)) {
			return ItemStack.EMPTY;
		}
		MutationData data = Mutations.get(living);
		SoulKind kind = SoulKind.of(living);
		SoulMeatItem meat = data == null || kind == null ? null : EvolutaItems.soulMeat(data.getElement(), kind);
		if (meat == null) {
			return ItemStack.EMPTY;
		}
		ItemStack result = new ItemStack(meat, stack.getCount());
		result.set(SoulComponents.SOUL_GRADE, data.getTier());
		return result;
	}
}
