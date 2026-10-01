package com.nyr.evoluta.common.mutation;

import com.nyr.evoluta.common.Evoluta;
import com.nyr.evoluta.common.config.EvolutaConfig;
import com.nyr.evoluta.common.world.EvolutaWorlds;
import net.minecraft.entity.Entity;
import net.minecraft.entity.EquipmentSlot;
import net.minecraft.entity.attribute.EntityAttribute;
import net.minecraft.entity.attribute.EntityAttributeInstance;
import net.minecraft.entity.attribute.EntityAttributeModifier;
import net.minecraft.entity.attribute.EntityAttributes;
import net.minecraft.entity.mob.AbstractSkeletonEntity;
import net.minecraft.entity.mob.MobEntity;
import net.minecraft.item.ItemStack;
import net.minecraft.item.RangedWeaponItem;
import net.minecraft.registry.entry.RegistryEntry;
import net.minecraft.screen.ScreenTexts;
import net.minecraft.text.MutableText;
import net.minecraft.text.Text;
import net.minecraft.util.Identifier;
import org.jetbrains.annotations.Nullable;

/**
 * Turns {@link MutationData} into a mob's stats. Every stat change is a persistent attribute modifier with an
 * Evoluta id, so it saves with the mob, never stacks and can be taken back exactly.
 */
public final class Mutations {
	public static final Identifier TIER_HEALTH = Evoluta.id("tier_health");
	public static final Identifier TIER_DAMAGE = Evoluta.id("tier_damage");
	public static final Identifier BRUTE_SCALE = Evoluta.id("brute_scale");
	public static final Identifier BRUTE_KNOCKBACK_RESISTANCE = Evoluta.id("brute_knockback_resistance");
	public static final Identifier STALKER_SCALE = Evoluta.id("stalker_scale");

	/** Brutes stand 1.35x as large (hitbox included) and shrug off half of all knockback. */
	public static final double BRUTE_SCALE_BONUS = 0.35;
	public static final double BRUTE_KNOCKBACK_RESISTANCE_BONUS = 0.5;
	/** Stalkers are 0.88x the size of their kind. */
	public static final double STALKER_SCALE_BONUS = -0.12;

	private Mutations() {
	}

	@Nullable
	public static MutationData get(Entity entity) {
		return entity.getAttached(EvolutaAttachments.MUTATION);
	}

	/** Makes {@code mob} a mutant (or changes its mutation), keeping the share of health it had. */
	public static void apply(MobEntity mob, MutationData data, EvolutaConfig config) {
		mob.setAttached(EvolutaAttachments.MUTATION, data);
		syncAttributes(mob, data, config);
		EvolutaWorlds.refresh(mob);
	}

	/** Turns a mutant back into an ordinary mob, keeping the share of health it had. Returns false if it was not one. */
	public static boolean clear(MobEntity mob) {
		if (mob.removeAttached(EvolutaAttachments.MUTATION) == null) {
			return false;
		}
		float share = healthShare(mob);
		boolean healthChanged = setModifier(mob, EntityAttributes.GENERIC_MAX_HEALTH, TIER_HEALTH, 0, EntityAttributeModifier.Operation.ADD_MULTIPLIED_BASE);
		setModifier(mob, EntityAttributes.GENERIC_ATTACK_DAMAGE, TIER_DAMAGE, 0, EntityAttributeModifier.Operation.ADD_MULTIPLIED_BASE);
		setModifier(mob, EntityAttributes.GENERIC_SCALE, BRUTE_SCALE, 0, EntityAttributeModifier.Operation.ADD_MULTIPLIED_BASE);
		setModifier(mob, EntityAttributes.GENERIC_KNOCKBACK_RESISTANCE, BRUTE_KNOCKBACK_RESISTANCE, 0, EntityAttributeModifier.Operation.ADD_VALUE);
		setModifier(mob, EntityAttributes.GENERIC_SCALE, STALKER_SCALE, 0, EntityAttributeModifier.Operation.ADD_MULTIPLIED_BASE);
		if (healthChanged) {
			mob.setHealth(share * mob.getMaxHealth());
		}
		EvolutaWorlds.refresh(mob);
		return true;
	}

	/**
	 * Brings the mob's Evoluta modifiers in line with {@code data} and the config: adds what is missing, updates
	 * what changed, removes what no longer applies and leaves the rest alone. Safe to call on every load.
	 */
	public static void syncAttributes(MobEntity mob, MutationData data, EvolutaConfig config) {
		Tier tier = data.getTier();
		Archetype archetype = data.getArchetype();
		float share = healthShare(mob);
		boolean healthChanged = setModifier(mob, EntityAttributes.GENERIC_MAX_HEALTH, TIER_HEALTH,
				config.healthBonus().get(tier), EntityAttributeModifier.Operation.ADD_MULTIPLIED_BASE);
		setModifier(mob, EntityAttributes.GENERIC_ATTACK_DAMAGE, TIER_DAMAGE,
				config.damageBonus().get(tier), EntityAttributeModifier.Operation.ADD_MULTIPLIED_BASE);
		setModifier(mob, EntityAttributes.GENERIC_SCALE, BRUTE_SCALE,
				archetype == Archetype.BRUTE ? BRUTE_SCALE_BONUS : 0, EntityAttributeModifier.Operation.ADD_MULTIPLIED_BASE);
		setModifier(mob, EntityAttributes.GENERIC_KNOCKBACK_RESISTANCE, BRUTE_KNOCKBACK_RESISTANCE,
				archetype == Archetype.BRUTE ? BRUTE_KNOCKBACK_RESISTANCE_BONUS : 0, EntityAttributeModifier.Operation.ADD_VALUE);
		setModifier(mob, EntityAttributes.GENERIC_SCALE, STALKER_SCALE,
				archetype == Archetype.STALKER ? STALKER_SCALE_BONUS : 0, EntityAttributeModifier.Operation.ADD_MULTIPLIED_BASE);
		if (healthChanged) {
			mob.setHealth(share * mob.getMaxHealth());
		}
	}

	/**
	 * Gear an archetype brings: a Brute skeleton trades its bow for a {@link BoneDagger}, which switches it to melee.
	 * A skeleton already holding a melee weapon keeps it.
	 */
	public static void equipLoadout(MobEntity mob, MutationData data) {
		if (data.getArchetype() == Archetype.BRUTE && mob instanceof AbstractSkeletonEntity skeleton) {
			ItemStack held = skeleton.getMainHandStack();
			if (held.isEmpty() || held.getItem() instanceof RangedWeaponItem) {
				skeleton.equipStack(EquipmentSlot.MAINHAND, BoneDagger.create());
			}
		}
	}

	/** "Elite Permafrost Brute", leaving out an absent element or archetype. */
	public static MutableText describe(MutationData data) {
		MutableText text = Text.translatable("evoluta.tier." + data.getTier().key());
		if (data.getElement() != Element.NONE) {
			text.append(ScreenTexts.SPACE).append(Text.translatable("evoluta.element." + data.getElement().key()));
		}
		if (data.getArchetype() != Archetype.NONE) {
			text.append(ScreenTexts.SPACE).append(Text.translatable("evoluta.archetype." + data.getArchetype().key()));
		}
		return text;
	}

	private static float healthShare(MobEntity mob) {
		float max = mob.getMaxHealth();
		return max > 0 ? Math.min(1.0F, mob.getHealth() / max) : 1.0F;
	}

	/** Sets modifier {@code id} to {@code value}, or removes it when {@code value} is 0. Returns whether anything changed. */
	private static boolean setModifier(MobEntity mob, RegistryEntry<EntityAttribute> attribute, Identifier id, double value,
			EntityAttributeModifier.Operation operation) {
		EntityAttributeInstance instance = mob.getAttributeInstance(attribute);
		if (instance == null) {
			return false;
		}
		EntityAttributeModifier current = instance.getModifier(id);
		if (value == 0) {
			return current != null && instance.removeModifier(id);
		}
		EntityAttributeModifier wanted = new EntityAttributeModifier(id, value, operation);
		if (wanted.equals(current)) {
			return false;
		}
		instance.overwritePersistentModifier(wanted);
		return true;
	}
}
