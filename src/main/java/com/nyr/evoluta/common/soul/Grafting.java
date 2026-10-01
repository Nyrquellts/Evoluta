package com.nyr.evoluta.common.soul;

import com.nyr.evoluta.common.Evoluta;
import com.nyr.evoluta.common.item.SoulMeatItem;
import com.nyr.evoluta.common.mutation.Element;
import com.nyr.evoluta.common.mutation.Tier;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.List;
import net.minecraft.component.DataComponentTypes;
import net.minecraft.component.type.AttributeModifierSlot;
import net.minecraft.component.type.AttributeModifiersComponent;
import net.minecraft.entity.EquipmentSlot;
import net.minecraft.entity.attribute.EntityAttribute;
import net.minecraft.entity.attribute.EntityAttributeModifier;
import net.minecraft.entity.attribute.EntityAttributes;
import net.minecraft.item.Equipment;
import net.minecraft.item.Item;
import net.minecraft.item.ItemStack;
import net.minecraft.registry.RegistryKeys;
import net.minecraft.registry.entry.RegistryEntry;
import net.minecraft.registry.tag.TagKey;

/**
 * Grafting Soul Meat onto gear, and what a graft is worth. Grafts live in {@link SoulComponents#GRAFTS}; the lasting
 * bonuses (health, speed, safe fall...) are written into the stack's own vanilla attribute modifiers, so the game
 * applies them with no Evoluta work per tick and other mods see them. Everything that happens on a hit is in
 * {@link GraftCombat}. Numbers: docs/soul-meat.md.
 */
public final class Grafting {
	public static final int MAX_GRAFTS = 3;
	/** Weapons that take grafts: vanilla's enchantable weapons, bows, crossbows, tridents and maces, modded ones included. */
	public static final TagKey<Item> WEAPONS = TagKey.of(RegistryKeys.ITEM, Evoluta.id("graftable/weapon"));
	/** Armour that takes grafts: vanilla's enchantable armour, modded pieces included. */
	public static final TagKey<Item> ARMOR = TagKey.of(RegistryKeys.ITEM, Evoluta.id("graftable/armor"));
	/** Mining gear that takes grafts: vanilla's pickaxes, shovels, axes and hoes, modded ones included. */
	public static final TagKey<Item> TOOLS = TagKey.of(RegistryKeys.ITEM, Evoluta.id("graftable/tool"));
	/** The first graft works in full; a second and third sharing its kind or element at 60% and 35%. */
	private static final float[] OVERLAP = {1.0F, 0.6F, 0.35F};
	private static final String MODIFIER_PREFIX = "graft/";

	/** A graft and the share of it that works on its item. */
	public record Weighted(Graft graft, float weight) {
		public float power() {
			return this.graft.power();
		}
	}

	private Grafting() {
	}

	public static List<Graft> grafts(ItemStack stack) {
		return stack.getOrDefault(SoulComponents.GRAFTS, List.of());
	}

	public static boolean isWeapon(ItemStack stack) {
		return stack.isIn(WEAPONS);
	}

	public static boolean isArmor(ItemStack stack) {
		return stack.isIn(ARMOR);
	}

	/** Mining gear; an axe is a weapon too, and gets both. */
	public static boolean isTool(ItemStack stack) {
		return stack.isIn(TOOLS);
	}

	/** Whether {@code gear} takes another graft: graftable, and not yet full. */
	public static boolean canGraft(ItemStack gear) {
		return !gear.isEmpty() && gear.getCount() == 1 && (isWeapon(gear) || isArmor(gear) || isTool(gear)) && grafts(gear).size() < MAX_GRAFTS;
	}

	/** A copy of {@code gear} with the meat grafted on at {@code power}, its attribute bonuses rewritten. */
	public static ItemStack graft(ItemStack gear, SoulMeatItem meat, Tier grade, float power) {
		List<Graft> grafts = new ArrayList<>(grafts(gear));
		grafts.add(new Graft(meat.kind(), meat.element(), grade, power));
		return withGrafts(gear, grafts);
	}

	/** A copy of {@code gear} without its graft at {@code index}. */
	public static ItemStack scrub(ItemStack gear, int index) {
		List<Graft> grafts = new ArrayList<>(grafts(gear));
		if (index < 0 || index >= grafts.size()) {
			return gear.copy();
		}
		grafts.remove(index);
		return withGrafts(gear, grafts);
	}

	/** The index of the graft with the lowest power, or -1 when there is none. */
	public static int weakest(ItemStack gear) {
		List<Graft> grafts = grafts(gear);
		int weakest = -1;
		for (int i = 0; i < grafts.size(); i++) {
			if (weakest < 0 || grafts.get(i).power() < grafts.get(weakest).power()) {
				weakest = i;
			}
		}
		return weakest;
	}

	/** Every graft with its weight: strongest first, full weight, then 60% and 35% for grafts overlapping a stronger one. */
	public static List<Weighted> weighted(List<Graft> grafts) {
		if (grafts.isEmpty()) {
			return List.of();
		}
		List<Graft> strongestFirst = new ArrayList<>(grafts);
		strongestFirst.sort(Comparator.comparingDouble(Graft::power).reversed());
		List<Weighted> weighted = new ArrayList<>(strongestFirst.size());
		for (int i = 0; i < strongestFirst.size(); i++) {
			Graft graft = strongestFirst.get(i);
			int overlaps = 0;
			for (int j = 0; j < i; j++) {
				Graft stronger = strongestFirst.get(j);
				if (stronger.kind() == graft.kind() || stronger.element() == graft.element()) {
					overlaps++;
				}
			}
			weighted.add(new Weighted(graft, OVERLAP[Math.min(overlaps, OVERLAP.length - 1)]));
		}
		return weighted;
	}

	private static ItemStack withGrafts(ItemStack gear, List<Graft> grafts) {
		ItemStack result = gear.copy();
		if (grafts.isEmpty()) {
			result.remove(SoulComponents.GRAFTS);
		} else {
			result.set(SoulComponents.GRAFTS, List.copyOf(grafts));
		}
		rewriteModifiers(result);
		return result;
	}

	/**
	 * Replaces Evoluta's attribute modifiers on the stack with those of its grafts, keeping every other modifier. Armour
	 * keeps its own armour points on the item, not the stack (ItemStack#applyAttributeModifiers uses the item's only
	 * while the stack has none), so they are copied in first, or the first graft would strip the armour bare; and
	 * once no graft is left, a stack with nothing else changed goes back to exactly the item's defaults.
	 */
	static void rewriteModifiers(ItemStack stack) {
		AttributeModifiersComponent itemDefault = stack.getItem().getComponents()
				.getOrDefault(DataComponentTypes.ATTRIBUTE_MODIFIERS, AttributeModifiersComponent.DEFAULT);
		AttributeModifiersComponent own = stack.getOrDefault(DataComponentTypes.ATTRIBUTE_MODIFIERS, AttributeModifiersComponent.DEFAULT);
		AttributeModifiersComponent base = own.modifiers().isEmpty() ? stack.getItem().getAttributeModifiers() : own;
		AttributeModifiersComponent natural = itemDefault.modifiers().isEmpty() ? stack.getItem().getAttributeModifiers() : itemDefault;
		List<AttributeModifiersComponent.Entry> kept = new ArrayList<>();
		for (AttributeModifiersComponent.Entry entry : base.modifiers()) {
			if (!isGraftModifier(entry.modifier())) {
				kept.add(entry);
			}
		}
		List<Weighted> weighted = weighted(grafts(stack));
		if (weighted.isEmpty() && kept.equals(natural.modifiers())) {
			stack.set(DataComponentTypes.ATTRIBUTE_MODIFIERS, itemDefault);
			return;
		}
		AttributeModifiersComponent.Builder builder = AttributeModifiersComponent.builder();
		for (AttributeModifiersComponent.Entry entry : kept) {
			builder.add(entry.attribute(), entry.modifier(), entry.slot());
		}
		AttributeModifierSlot slot = slotOf(stack);
		for (int i = 0; i < weighted.size(); i++) {
			Weighted graft = weighted.get(i);
			String id = MODIFIER_PREFIX + slot.asString() + "/" + i + "/";
			if (isArmor(stack)) {
				armorBonuses(builder, graft, slot, id);
			} else {
				if (isWeapon(stack)) {
					weaponBonuses(builder, graft, slot, id);
				}
				if (isTool(stack)) {
					toolBonuses(builder, graft, slot, id);
				}
			}
		}
		stack.set(DataComponentTypes.ATTRIBUTE_MODIFIERS, builder.build().withShowInTooltip(own.showInTooltip()));
	}

	/** Lasting weapon bonuses: only the baby zombie's frenzy is an attribute; everything else happens on hits. */
	private static void weaponBonuses(AttributeModifiersComponent.Builder builder, Weighted graft, AttributeModifierSlot slot, String id) {
		if (graft.graft().kind() == SoulKind.BABY_ZOMBIE) {
			add(builder, EntityAttributes.GENERIC_ATTACK_SPEED, id + "frenzy", (0.05 + 0.15 * graft.power()) * graft.weight(),
					EntityAttributeModifier.Operation.ADD_MULTIPLIED_BASE, slot);
		}
	}

	/**
	 * Lasting mining bonuses: Corrosion (Toxic, like Efficiency I to II), Aqua Lung (drowned), Frenzy (baby zombie) and
	 * Long Reach (skeleton). The rest happens as blocks break ({@link ToolGrafts}).
	 */
	private static void toolBonuses(AttributeModifiersComponent.Builder builder, Weighted graft, AttributeModifierSlot slot, String id) {
		double p = graft.power();
		double w = graft.weight();
		EntityAttributeModifier.Operation add = EntityAttributeModifier.Operation.ADD_VALUE;
		switch (graft.graft().kind()) {
			case DROWNED -> add(builder, EntityAttributes.PLAYER_SUBMERGED_MINING_SPEED, id + "aqua_lung", (0.4 + 0.4 * p) * w, add, slot);
			case BABY_ZOMBIE -> add(builder, EntityAttributes.PLAYER_BLOCK_BREAK_SPEED, id + "dig_frenzy", (0.05 + 0.15 * p) * w,
					EntityAttributeModifier.Operation.ADD_MULTIPLIED_BASE, slot);
			case SKELETON -> add(builder, EntityAttributes.PLAYER_BLOCK_INTERACTION_RANGE, id + "long_reach", (0.5 + p) * w, add, slot);
			default -> {
			}
		}
		if (graft.graft().element() == Element.TOXIC) {
			add(builder, EntityAttributes.PLAYER_MINING_EFFICIENCY, id + "corrosion", (1 + 3 * p) * w, add, slot);
		}
	}

	/** Lasting armour bonuses, by the graft's kind and element. The rest (wards, retaliation) is in {@link GraftCombat}. */
	private static void armorBonuses(AttributeModifiersComponent.Builder builder, Weighted graft, AttributeModifierSlot slot, String id) {
		double p = graft.power();
		double w = graft.weight();
		EntityAttributeModifier.Operation add = EntityAttributeModifier.Operation.ADD_VALUE;
		switch (graft.graft().kind()) {
			case ZOMBIE -> add(builder, EntityAttributes.GENERIC_MAX_HEALTH, id + "vigor", (1 + p) * w, add, slot);
			case HUSK -> add(builder, EntityAttributes.GENERIC_BURNING_TIME, id + "heatproof", -(0.1 + 0.2 * p) * w, add, slot);
			case DROWNED -> {
				add(builder, EntityAttributes.GENERIC_OXYGEN_BONUS, id + "gills", (1 + 2 * p) * w, add, slot);
				add(builder, EntityAttributes.GENERIC_WATER_MOVEMENT_EFFICIENCY, id + "swim", (0.1 + 0.2 * p) * w, add, slot);
			}
			case ZOMBIE_VILLAGER -> add(builder, EntityAttributes.GENERIC_LUCK, id + "fortune", (0.5 + p) * w, add, slot);
			case BABY_ZOMBIE -> add(builder, EntityAttributes.GENERIC_MOVEMENT_SPEED, id + "swiftness", (0.02 + 0.04 * p) * w,
					EntityAttributeModifier.Operation.ADD_MULTIPLIED_BASE, slot);
			case CREEPER -> add(builder, EntityAttributes.GENERIC_EXPLOSION_KNOCKBACK_RESISTANCE, id + "anchor", (0.2 + 0.4 * p) * w, add, slot);
			case SPIDER -> {
				add(builder, EntityAttributes.GENERIC_SAFE_FALL_DISTANCE, id + "featherfall", (1 + 2 * p) * w, add, slot);
				if (graft.graft().grade() == Tier.APEX && slot == AttributeModifierSlot.FEET) {
					add(builder, EntityAttributes.GENERIC_STEP_HEIGHT, id + "climb", 0.5, add, slot);
				}
			}
			case CAVE_SPIDER -> add(builder, EntityAttributes.PLAYER_SNEAKING_SPEED, id + "skitter", (0.1 + 0.2 * p) * w, add, slot);
			default -> {
				// skeleton, stray and bogged ward against damage and poison instead (GraftCombat)
			}
		}
		if (graft.graft().element() == Element.IGNITED) {
			add(builder, EntityAttributes.GENERIC_BURNING_TIME, id + "emberskin", -(0.15 + 0.25 * p) * w, add, slot);
		}
	}

	private static void add(AttributeModifiersComponent.Builder builder, RegistryEntry<EntityAttribute> attribute, String id, double value,
			EntityAttributeModifier.Operation operation, AttributeModifierSlot slot) {
		builder.add(attribute, new EntityAttributeModifier(Evoluta.id(id), value, operation), slot);
	}

	static boolean isGraftModifier(EntityAttributeModifier modifier) {
		return modifier.id().getNamespace().equals(Evoluta.MOD_ID) && modifier.id().getPath().startsWith(MODIFIER_PREFIX);
	}

	/** Where the item's bonuses apply: the main hand for weapons, the armour's own slot for armour. */
	static AttributeModifierSlot slotOf(ItemStack stack) {
		if (!isArmor(stack)) {
			return AttributeModifierSlot.MAINHAND;
		}
		Equipment equipment = Equipment.fromStack(stack);
		EquipmentSlot slot = equipment == null ? null : equipment.getSlotType();
		return slot == null || !slot.isArmorSlot() ? AttributeModifierSlot.ARMOR : AttributeModifierSlot.forEquipmentSlot(slot);
	}
}
