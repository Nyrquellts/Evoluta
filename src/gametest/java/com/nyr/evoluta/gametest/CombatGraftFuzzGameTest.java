package com.nyr.evoluta.gametest;

import com.nyr.evoluta.common.combat.HazardBlockTypes;
import com.nyr.evoluta.common.mutation.Element;
import com.nyr.evoluta.common.mutation.EvolutaAttachments;
import com.nyr.evoluta.common.mutation.Tier;
import com.nyr.evoluta.common.soul.Graft;
import com.nyr.evoluta.common.soul.GraftCombat;
import com.nyr.evoluta.common.soul.Grafting;
import com.nyr.evoluta.common.soul.SoulKind;
import java.lang.reflect.Field;
import java.util.ArrayList;
import java.util.EnumMap;
import java.util.HashMap;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.SplittableRandom;
import net.fabricmc.fabric.api.entity.event.v1.ServerLivingEntityEvents;
import net.fabricmc.fabric.api.gametest.v1.FabricGameTest;
import net.minecraft.block.BlockState;
import net.minecraft.entity.Entity;
import net.minecraft.entity.EntityType;
import net.minecraft.entity.EquipmentSlot;
import net.minecraft.entity.LivingEntity;
import net.minecraft.entity.damage.DamageSource;
import net.minecraft.entity.damage.DamageSources;
import net.minecraft.entity.damage.DamageTypes;
import net.minecraft.entity.effect.StatusEffect;
import net.minecraft.entity.effect.StatusEffectInstance;
import net.minecraft.entity.effect.StatusEffects;
import net.minecraft.entity.mob.MobEntity;
import net.minecraft.entity.mob.ZombieEntity;
import net.minecraft.entity.passive.PigEntity;
import net.minecraft.entity.player.PlayerEntity;
import net.minecraft.entity.projectile.ArrowEntity;
import net.minecraft.entity.projectile.PersistentProjectileEntity;
import net.minecraft.entity.projectile.ProjectileEntity;
import net.minecraft.entity.projectile.TridentEntity;
import net.minecraft.item.Equipment;
import net.minecraft.item.Item;
import net.minecraft.item.ItemStack;
import net.minecraft.item.Items;
import net.minecraft.item.RangedWeaponItem;
import net.minecraft.registry.Registries;
import net.minecraft.registry.entry.RegistryEntry;
import net.minecraft.registry.tag.DamageTypeTags;
import net.minecraft.registry.tag.EntityTypeTags;
import net.minecraft.server.network.ServerPlayerEntity;
import net.minecraft.server.world.ServerWorld;
import net.minecraft.test.GameTest;
import net.minecraft.test.TestContext;
import net.minecraft.util.Hand;
import net.minecraft.util.UseAction;
import net.minecraft.util.math.BlockPos;
import net.minecraft.util.math.Box;
import net.minecraft.util.math.MathHelper;
import net.minecraft.util.math.random.RandomSeed;
import org.jetbrains.annotations.Nullable;

/**
 * Fuzz of the grafts in a fight against the documented rules (docs/soul-meat.md), every living entity type a victim:
 * which hits count, the bonus damage and the wards to the number, the poison ward, then real hits (a player's swing,
 * a mob's, arrows, tridents) and what they leave on the victim, the wielder and whoever stands by: effects, freezing,
 * fire, bursts, retaliation, feasts and soul fire; and skeleton volleys. Every case derives from the fuzz seed and its
 * number ({@link ToolFuzz}).
 */
public final class CombatGraftFuzzGameTest implements FabricGameTest {
	private static final String BATCH = "evoluta_combat_fuzz";
	/** Documented: a burst every third counted hit, then five seconds' rest; a hit's freezing never piles past 200 ticks. */
	private static final int BURST_EVERY = 3;
	private static final int BURST_REST = 100;
	private static final int FREEZE_CAP = 200;
	private static final EquipmentSlot[] ARMOR_SLOTS = {EquipmentSlot.HEAD, EquipmentSlot.CHEST, EquipmentSlot.LEGS, EquipmentSlot.FEET};

	/** Fabric's damage events while a case runs: every attempt, every hit that landed and was survived, every death. */
	private static final List<Map.Entry<LivingEntity, DamageSource>> ATTEMPTS = new ArrayList<>();
	private static final List<Map.Entry<LivingEntity, DamageSource>> LANDED = new ArrayList<>();
	private static final List<Map.Entry<LivingEntity, DamageSource>> DEATHS = new ArrayList<>();
	private static boolean recording;

	static {
		ServerLivingEntityEvents.ALLOW_DAMAGE.register((entity, source, amount) -> {
			if (recording) {
				ATTEMPTS.add(Map.entry(entity, source));
			}
			return true;
		});
		ServerLivingEntityEvents.AFTER_DAMAGE.register((entity, source, base, taken, blocked) -> {
			if (recording && !blocked) {
				LANDED.add(Map.entry(entity, source));
			}
		});
		ServerLivingEntityEvents.AFTER_DEATH.register((entity, source) -> {
			if (recording) {
				DEATHS.add(Map.entry(entity, source));
			}
		});
	}

	/** Graftable gear by use. */
	private static final class Gear {
		final List<Item> weapons = ToolFuzz.items(Grafting.WEAPONS);
		final List<Item> melee = new ArrayList<>();
		final List<Item> launchers = new ArrayList<>();
		final List<Item> others = new ArrayList<>();
		final Map<EquipmentSlot, List<Item>> armour = new EnumMap<>(EquipmentSlot.class);

		Gear() {
			for (Item item : this.weapons) {
				if (item instanceof RangedWeaponItem) {
					this.launchers.add(item);
				} else if (item != Items.TRIDENT) {
					this.melee.add(item);
				}
			}
			for (Item item : ToolFuzz.items(Grafting.ARMOR)) {
				if (item instanceof Equipment equipment) {
					this.armour.computeIfAbsent(equipment.getSlotType(), slot -> new ArrayList<>()).add(item);
				}
				this.others.add(item);
			}
			for (Item item : ToolFuzz.items(Grafting.TOOLS)) {
				if (!this.weapons.contains(item)) {
					this.others.add(item);
				}
			}
		}

		/** Something to hold: mostly a grafted weapon, now and then grafted non-weapon gear, a plain weapon or nothing. */
		ItemStack held(SplittableRandom random, ServerWorld world) {
			int roll = random.nextInt(20);
			if (roll == 0) {
				return ItemStack.EMPTY;
			}
			Item item = roll < 4 ? ToolFuzz.pick(random, this.others) : ToolFuzz.pick(random, this.weapons);
			ItemStack stack = ToolFuzz.tool(random, world, item);
			return roll == 4 ? stack : ToolFuzz.grafted(stack, ToolFuzz.grafts(random, Grafting.MAX_GRAFTS));
		}

		ItemStack grafted(SplittableRandom random, ServerWorld world, Item item) {
			return ToolFuzz.grafted(ToolFuzz.tool(random, world, item), ToolFuzz.grafts(random, Grafting.MAX_GRAFTS));
		}

		/** Random grafted armour in some slots, the rest bare. */
		void dress(SplittableRandom random, ServerWorld world, LivingEntity entity) {
			for (EquipmentSlot slot : ARMOR_SLOTS) {
				List<Item> pieces = this.armour.get(slot);
				entity.equipStack(slot, pieces != null && random.nextInt(5) < 2 ? this.grafted(random, world, ToolFuzz.pick(random, pieces)) : ItemStack.EMPTY);
			}
		}
	}

	private static List<EntityType<?>> livingTypes(ServerWorld world, boolean toSpawn) {
		List<EntityType<?>> types = new ArrayList<>();
		for (EntityType<?> type : Registries.ENTITY_TYPE) {
			if (type == EntityType.PLAYER || toSpawn && (type == EntityType.ENDER_DRAGON || type == EntityType.WITHER)) {
				continue;
			}
			Entity entity = type.create(world);
			if (entity instanceof LivingEntity) {
				types.add(type);
			}
			if (entity != null) {
				entity.discard();
			}
		}
		return types;
	}

	private static void setField(Class<?> owner, Object target, String name, Object value) {
		try {
			Field field = owner.getDeclaredField(name);
			field.setAccessible(true);
			field.set(target, value);
		} catch (ReflectiveOperationException e) {
			throw new IllegalStateException("dev runtime without Yarn names?", e);
		}
	}

	private static String describe(LivingEntity entity) {
		StringBuilder text = new StringBuilder(Registries.ENTITY_TYPE.getId(entity.getType()).getPath());
		List<String> worn = new ArrayList<>();
		for (ItemStack piece : entity.getArmorItems()) {
			if (!piece.isEmpty()) {
				worn.add(ToolFuzz.describe(piece));
			}
		}
		if (!worn.isEmpty()) {
			text.append(" wearing ").append(worn);
		}
		return text.toString();
	}

	// ---------------------------------------------------------------------------------------------------------------
	// The documented rules, written out

	/** Melee: a player's or mob's own blow, or a sting. Only these are weapon hits, and only these draw retaliation. */
	private static boolean melee(DamageSource source) {
		return source.isOf(DamageTypes.PLAYER_ATTACK) || source.isOf(DamageTypes.MOB_ATTACK) || source.isOf(DamageTypes.MOB_ATTACK_NO_AGGRO)
				|| source.isOf(DamageTypes.STING);
	}

	/** The weapon whose grafts a hit carries: a projectile's launcher (or sword), a mob's weapon, a player's on a full swing at this victim. */
	@Nullable
	private static ItemStack counted(LivingEntity victim, DamageSource source) {
		Entity direct = source.getSource();
		if (direct instanceof ProjectileEntity projectile) {
			return projectile.getWeaponStack();
		}
		if (!(direct instanceof LivingEntity attacker) || direct != source.getAttacker() || !melee(source)) {
			return null;
		}
		if (attacker instanceof PlayerEntity player) {
			GraftCombat.Swing swing = player.getAttached(EvolutaAttachments.SWING);
			if (swing == null || swing.target() != victim.getId() || swing.strength() < GraftCombat.FULL_SWING) {
				return null;
			}
		}
		ItemStack held = attacker.getMainHandStack();
		return held.isIn(Grafting.WEAPONS) ? held : null;
	}

	private static List<Grafting.Weighted> grafts(@Nullable ItemStack stack) {
		return stack == null ? List.of() : Grafting.weighted(Grafting.grafts(stack));
	}

	private static float bonus(LivingEntity victim, DamageSource source, List<Grafting.Weighted> grafts) {
		float bonus = 0;
		for (Grafting.Weighted graft : grafts) {
			float p = graft.power();
			float w = graft.weight();
			float element = (0.5F + 1.5F * p) * w;
			switch (graft.graft().element()) {
				case IGNITED -> bonus += victim.isFireImmune() ? 0 : element;
				case PERMAFROST -> bonus += victim.getType().isIn(EntityTypeTags.FREEZE_IMMUNE_ENTITY_TYPES) ? 0
						: victim.getType().isIn(EntityTypeTags.FREEZE_HURTS_EXTRA_TYPES) ? 2 * element : element;
				case TOXIC -> bonus += element;
				default -> {
				}
			}
			if (graft.graft().kind() == SoulKind.DROWNED && victim.isWet()) {
				bonus += (1 + 2 * p) * w;
			}
			if (graft.graft().kind() == SoulKind.SKELETON && source.isIn(DamageTypeTags.IS_PROJECTILE)) {
				bonus += (0.5F + p) * w;
			}
		}
		return bonus;
	}

	private static float ward(LivingEntity victim, DamageSource source) {
		boolean projectile = source.isIn(DamageTypeTags.IS_PROJECTILE);
		boolean explosion = source.isIn(DamageTypeTags.IS_EXPLOSION);
		boolean freezing = source.isIn(DamageTypeTags.IS_FREEZING);
		float ward = 0;
		for (ItemStack piece : victim.getArmorItems()) {
			for (Grafting.Weighted graft : grafts(piece)) {
				float p = graft.power();
				float w = graft.weight();
				SoulKind kind = graft.graft().kind();
				if (projectile && kind == SoulKind.SKELETON) {
					ward += (0.05F + 0.1F * p) * w;
				}
				if (explosion && kind == SoulKind.CREEPER) {
					ward += (0.1F + 0.2F * p) * w;
				}
				if (freezing && (kind == SoulKind.STRAY || graft.graft().element() == Element.PERMAFROST)) {
					ward += (0.25F + 0.5F * p) * w;
				}
			}
		}
		return Math.min(freezing ? 0.8F : 0.6F, ward);
	}

	private static float poisonWard(LivingEntity wearer) {
		float ward = 0;
		for (ItemStack piece : wearer.getArmorItems()) {
			for (Grafting.Weighted graft : grafts(piece)) {
				if (graft.graft().kind() == SoulKind.BOGGED || graft.graft().element() == Element.TOXIC) {
					ward += (0.25F + 0.5F * graft.power()) * graft.weight();
				}
			}
		}
		return Math.min(0.8F, ward);
	}

	private static boolean has(List<Grafting.Weighted> grafts, SoulKind kind) {
		return grafts.stream().anyMatch(graft -> graft.graft().kind() == kind);
	}

	private static boolean has(List<Grafting.Weighted> grafts, Element element) {
		return grafts.stream().anyMatch(graft -> graft.graft().element() == element);
	}

	// ---------------------------------------------------------------------------------------------------------------
	// Damage and wards

	private enum Blow {
		SWING, MOB, MOB_NO_AGGRO, STING, ARROW, TRIDENT, EXPLOSION, FREEZING, THORNS, SONIC_BOOM, GENERIC
	}

	/**
	 * Every living entity type as a victim, bare or in random grafted armour, wet or dry; a player or a mob attacking
	 * with anything in hand (grafted weapons, grafted tools and armour, plain gear); every kind of blow, from a full or
	 * half swing, aimed at the victim or elsewhere, to arrows, tridents, blasts, cold, thorns: the damage that reaches
	 * vanilla is the blow plus the counted weapon's documented bonus, less the victim's documented ward. And any status
	 * effect, of any length (infinite too), meets the poison ward as documented: only poison shortens, never below a
	 * tick, and an infinite one stays infinite.
	 */
	@GameTest(templateName = EMPTY_STRUCTURE, batchId = BATCH, tickLimit = 400)
	public void damageAndWardsFollowTheRules(TestContext context) {
		ServerWorld world = context.getWorld();
		DamageSources sources = world.getDamageSources();
		ServerPlayerEntity player = TestPlayers.survival(context, new BlockPos(1, 2, 1), 0);
		ZombieEntity zombie = EntityType.ZOMBIE.create(world);
		zombie.refreshPositionAndAngles(context.getAbsolutePos(new BlockPos(1, 2, 6)), 0, 0);
		BlockPos at = context.getAbsolutePos(new BlockPos(4, 2, 4));
		List<EntityType<?>> living = livingTypes(world, false);
		List<RegistryEntry<StatusEffect>> effects = new ArrayList<>(Registries.STATUS_EFFECT.streamEntries().toList());
		Gear gear = new Gear();
		ToolFuzz.Failures failures = new ToolFuzz.Failures("damage and ward fuzz");
		try {
			for (int cycle = 0; cycle < ToolFuzz.CYCLES; cycle++) {
				SplittableRandom random = ToolFuzz.random("damage", cycle);
				failures.ran();
				EntityType<?> type = cycle < living.size() ? living.get(cycle) : ToolFuzz.pick(random, living);
				String what = Registries.ENTITY_TYPE.getId(type).getPath();
				try {
					LivingEntity victim = (LivingEntity) type.create(world);
					victim.refreshPositionAndAngles(at, 0, 0);
					setField(Entity.class, victim, "touchingWater", random.nextBoolean());
					gear.dress(random, world, victim);
					LivingEntity attacker = random.nextBoolean() ? player : zombie;
					attacker.equipStack(EquipmentSlot.MAINHAND, gear.held(random, world));
					player.removeAttached(EvolutaAttachments.SWING);
					Blow blow = ToolFuzz.pick(random, Blow.values());
					ItemStack projectileWeapon = ItemStack.EMPTY;
					DamageSource source = switch (blow) {
						case SWING -> {
							if (attacker == player) {
								int aim = random.nextInt(4);
								if (aim > 0) {
									float strength = aim == 3 ? (float) random.nextDouble() * GraftCombat.FULL_SWING : GraftCombat.FULL_SWING
											+ (float) random.nextDouble() * (1 - GraftCombat.FULL_SWING);
									player.setAttached(EvolutaAttachments.SWING, new GraftCombat.Swing(aim == 2 ? victim.getId() + 1 : victim.getId(), strength));
								}
								yield sources.playerAttack(player);
							}
							yield sources.mobAttack(attacker);
						}
						case MOB -> sources.mobAttack(attacker);
						case MOB_NO_AGGRO -> sources.mobAttackNoAggro(attacker);
						case STING -> sources.sting(attacker);
						case ARROW -> {
							projectileWeapon = gear.grafted(random, world, ToolFuzz.pick(random, random.nextBoolean() ? gear.launchers : gear.melee));
							yield sources.arrow(new ArrowEntity(world, attacker, new ItemStack(Items.ARROW), projectileWeapon), attacker);
						}
						case TRIDENT -> {
							projectileWeapon = gear.grafted(random, world, Items.TRIDENT);
							yield sources.trident(new TridentEntity(world, attacker, projectileWeapon), attacker);
						}
						case EXPLOSION -> sources.explosion(attacker, attacker);
						case FREEZING -> sources.freeze();
						case THORNS -> sources.thorns(attacker);
						case SONIC_BOOM -> sources.sonicBoom(attacker);
						case GENERIC -> sources.generic();
					};
					float amount = (float) random.nextDouble() * 20;
					what = describe(victim) + (victim.isWet() ? " (wet)" : "") + " took " + blow + " " + amount + " from "
							+ (attacker == player ? "a player" : "a zombie") + " holding " + ToolFuzz.describe(attacker.getMainHandStack())
							+ (projectileWeapon.isEmpty() ? "" : ", fired from " + ToolFuzz.describe(projectileWeapon))
							+ (player.hasAttached(EvolutaAttachments.SWING) ? ", swing " + player.getAttached(EvolutaAttachments.SWING) + " victim " + victim.getId() : "");
					ItemStack weapon = counted(victim, source);
					float expected = amount + (weapon == null ? 0 : bonus(victim, source, grafts(weapon)));
					if (source.isIn(DamageTypeTags.IS_PROJECTILE) || source.isIn(DamageTypeTags.IS_EXPLOSION) || source.isIn(DamageTypeTags.IS_FREEZING)) {
						expected *= 1 - ward(victim, source);
					}
					float got = GraftCombat.modifyDamage(victim, source, amount);
					float want = expected;
					String hit = what;
					failures.check(Math.abs(got - want) <= 1.0E-4F * Math.max(1, Math.abs(want)), cycle, () -> hit + ": " + got + " reached vanilla, the rules say "
							+ want);

					RegistryEntry<StatusEffect> kind = random.nextInt(5) < 3 ? StatusEffects.POISON : ToolFuzz.pick(random, effects);
					int duration = random.nextInt(6) == 0 ? StatusEffectInstance.INFINITE : 1 + random.nextInt(2400);
					StatusEffectInstance effect = new StatusEffectInstance(kind, duration, random.nextInt(4), random.nextBoolean(), random.nextBoolean(),
							random.nextBoolean());
					StatusEffectInstance warded = GraftCombat.wardEffect(victim, effect);
					float ward = poisonWard(victim);
					int wantDuration = !kind.equals(StatusEffects.POISON) || ward <= 0 || effect.isInfinite() ? duration
							: Math.max(1, Math.round(duration * (1 - ward)));
					failures.check(warded.getEffectType().equals(kind) && warded.getDuration() == wantDuration && warded.getAmplifier() == effect.getAmplifier()
							&& warded.isAmbient() == effect.isAmbient() && warded.shouldShowParticles() == effect.shouldShowParticles()
							&& warded.shouldShowIcon() == effect.shouldShowIcon(), cycle, () -> hit + ": " + effect + " became " + warded
							+ " against a poison ward of " + ward + ", the rules say " + wantDuration + " ticks");
				} catch (RuntimeException e) {
					failures.add(cycle, what + " threw " + e);
				}
			}
		} finally {
			player.removeAttached(EvolutaAttachments.SWING);
			TestPlayers.remove(context, player);
			zombie.discard();
		}
		failures.throwIfAny();
		context.complete();
	}

	// ---------------------------------------------------------------------------------------------------------------
	// Real hits

	private enum Attack {
		SWING, HALF_SWING, MOB, ARROW, TRIDENT
	}

	/**
	 * Real hits on every living entity type but the two bosses: a player's full or half swing, a mob's blow, an arrow
	 * and a trident, with random grafted gear on both sides and a grafted bystander beside the victim. What must follow:
	 * a counted hit on a victim that lives sets it burning, freezing and poisoned exactly as its grafts say and nothing
	 * else; an uncounted one leaves nothing; a burst comes exactly on the documented count, hits whoever stands near but
	 * never the wielder, and sets off nothing further; retaliation answers only a melee blow on the wearer; a counted kill
	 * feeds a zombie graft's wielder exactly its documented heal, and only a player's Apex Ignited kill leaves soul fire;
	 * no swing is left behind. After the last case, every block the soul fire took is back.
	 */
	@GameTest(templateName = EMPTY_STRUCTURE, batchId = BATCH, tickLimit = 400)
	public void realHitsLeaveOnlyWhatTheirGraftsSay(TestContext context) {
		ServerWorld world = context.getWorld();
		DamageSources sources = world.getDamageSources();
		Mutants.floor(context);
		ServerPlayerEntity player = TestPlayers.survival(context, new BlockPos(1, 2, 1), 0);
		TestPlayers.vulnerable(player);
		ZombieEntity zombie = EntityType.ZOMBIE.create(world);
		zombie.refreshPositionAndAngles(context.getAbsolutePos(new BlockPos(0, 2, 7)), 0, 0);
		zombie.setAiDisabled(true);
		world.spawnEntity(zombie);
		BlockPos victimAt = context.getAbsolutePos(new BlockPos(4, 2, 4));
		BlockPos bystanderAt = context.getAbsolutePos(new BlockPos(5, 2, 4));
		Box area = new Box(context.getAbsolutePos(new BlockPos(0, 1, 0))).union(new Box(context.getAbsolutePos(new BlockPos(7, 5, 7))));
		Map<BlockPos, BlockState> before = new HashMap<>();
		for (BlockPos pos : BlockPos.iterate(context.getAbsolutePos(new BlockPos(0, 1, 0)), context.getAbsolutePos(new BlockPos(7, 5, 7)))) {
			before.put(pos.toImmutable(), world.getBlockState(pos));
		}
		List<EntityType<?>> victims = livingTypes(world, true);
		Gear gear = new Gear();
		ToolFuzz.Failures failures = new ToolFuzz.Failures("real hit fuzz");
		int cycles = Math.max(300, ToolFuzz.CYCLES / 5);
		try {
			for (int cycle = 0; cycle < cycles; cycle++) {
				SplittableRandom random = ToolFuzz.random("hits", cycle);
				failures.ran();
				EntityType<?> type = cycle < victims.size() ? victims.get(cycle) : ToolFuzz.pick(random, victims);
				String what = Registries.ENTITY_TYPE.getId(type).getPath();
				LivingEntity victim = null;
				PigEntity bystander = null;
				try {
					victim = (LivingEntity) type.create(world);
					victim.refreshPositionAndAngles(victimAt, 0, 0);
					if (victim instanceof MobEntity mob) {
						mob.setAiDisabled(true);
					}
					if (!world.spawnEntity(victim)) {
						continue;
					}
					boolean kill = random.nextInt(6) == 0;
					victim.setHealth(kill ? 0.01F : victim.getMaxHealth());
					victim.clearStatusEffects();
					victim.setFireTicks(0);
					victim.setFrozenTicks(0);
					gear.dress(random, world, victim);
					bystander = EntityType.PIG.create(world);
					bystander.refreshPositionAndAngles(bystanderAt, 0, 0);
					bystander.setAiDisabled(true);
					world.spawnEntity(bystander);
					gear.dress(random, world, bystander);
					Attack attack = ToolFuzz.pick(random, Attack.values());
					LivingEntity attacker = attack == Attack.MOB || attack != Attack.SWING && attack != Attack.HALF_SWING && random.nextBoolean() ? zombie : player;
					for (LivingEntity side : new LivingEntity[]{player, zombie}) {
						side.setHealth(Math.max(1.0F, side.getMaxHealth() - 8.0F));
						side.clearStatusEffects();
						side.setFireTicks(0);
						side.setFrozenTicks(0);
						side.removeAttached(EvolutaAttachments.CREEPER_CHARGE);
						for (EquipmentSlot slot : ARMOR_SLOTS) {
							side.equipStack(slot, ItemStack.EMPTY);
						}
					}
					long now = world.getTime();
					GraftCombat.Charge charge = random.nextBoolean() ? null : new GraftCombat.Charge(random.nextInt(BURST_EVERY), now - random.nextInt(2 * BURST_REST));
					if (charge != null) {
						attacker.setAttached(EvolutaAttachments.CREEPER_CHARGE, charge);
					}
					ItemStack held = gear.held(random, world);
					attacker.equipStack(EquipmentSlot.MAINHAND, held);
					ItemStack projectileWeapon = attack == Attack.ARROW ? gear.grafted(random, world, ToolFuzz.pick(random, random.nextBoolean() ? gear.launchers : gear.melee))
							: attack == Attack.TRIDENT ? gear.grafted(random, world, Items.TRIDENT) : null;
					what = describe(victim) + (kill ? " (at death's door)" : "") + " hit by " + attack + " from " + (attacker == player ? "a player" : "a zombie")
							+ " holding " + ToolFuzz.describe(held) + (projectileWeapon == null ? "" : ", fired from " + ToolFuzz.describe(projectileWeapon))
							+ ", bystander " + describe(bystander) + (charge == null ? "" : ", charge " + charge.hits() + " hits, last burst " + (now - charge.lastBurst()) + " ago");
					int soulFireBefore = count(world, area, HazardBlockTypes.SOUL_FIRE);
					float attackerHealth = attacker.getHealth();
					// as it was before the blow: a weapon on its last points breaks from the hit's own wear, after its grafts acted
					ItemStack heldBefore = held.copy();
					ATTEMPTS.clear();
					LANDED.clear();
					DEATHS.clear();
					recording = true;
					try {
						switch (attack) {
							case SWING, HALF_SWING -> {
								setField(LivingEntity.class, player, "lastAttackedTicks", attack == Attack.SWING ? 100 : random.nextInt(4));
								player.attack(victim);
							}
							case MOB -> zombie.tryAttack(victim);
							case ARROW -> victim.damage(sources.arrow(new ArrowEntity(world, attacker, new ItemStack(Items.ARROW), projectileWeapon), attacker),
									2.0F + random.nextInt(6));
							case TRIDENT -> victim.damage(sources.trident(new TridentEntity(world, attacker, projectileWeapon), attacker), 8.0F);
						}
					} finally {
						recording = false;
					}
					checkHit(world, context, player, attacker, victim, bystander, attack, heldBefore, projectileWeapon, charge, now, soulFireBefore,
							attackerHealth, area, failures, cycle, what);
				} catch (RuntimeException e) {
					failures.add(cycle, what + " threw " + e);
				} finally {
					player.removeAttached(EvolutaAttachments.SWING);
					for (Entity entity : world.getEntitiesByClass(Entity.class, area.expand(4), entity -> !(entity instanceof PlayerEntity) && entity != zombie)) {
						entity.discard();
					}
				}
			}
		} finally {
			world.getRandom().setSeed(RandomSeed.getSeed());
			TestPlayers.remove(context, player);
		}
		ToolFuzz.Failures restored = failures;
		context.waitAndRun(100, () -> {
			zombie.discard();
			for (Map.Entry<BlockPos, BlockState> entry : before.entrySet()) {
				BlockState now = world.getBlockState(entry.getKey());
				restored.check(now.equals(entry.getValue()), -1, () -> "after the fuzz, " + entry.getKey() + " is " + ToolFuzz.describe(now) + ", was "
						+ ToolFuzz.describe(entry.getValue()));
			}
			restored.throwIfAny();
			context.complete();
		});
	}

	private static int count(ServerWorld world, Box area, net.minecraft.block.Block block) {
		int count = 0;
		for (BlockPos pos : BlockPos.iterate(BlockPos.ofFloored(area.minX, area.minY, area.minZ), BlockPos.ofFloored(area.maxX - 1, area.maxY - 1, area.maxZ - 1))) {
			if (world.getBlockState(pos).isOf(block)) {
				count++;
			}
		}
		return count;
	}

	private static boolean recorded(List<Map.Entry<LivingEntity, DamageSource>> events, LivingEntity entity) {
		return events.stream().anyMatch(event -> event.getKey() == entity);
	}

	private static void checkHit(ServerWorld world, TestContext context, ServerPlayerEntity player, LivingEntity attacker, LivingEntity victim,
			PigEntity bystander, Attack attack, ItemStack held, @Nullable ItemStack projectileWeapon, @Nullable GraftCombat.Charge charge, long now,
			int soulFireBefore, float attackerHealth, Box area, ToolFuzz.Failures failures, int cycle, String what) {
		// which weapon the hit carries, by the rules
		ItemStack weapon = switch (attack) {
			case SWING -> held.isIn(Grafting.WEAPONS) ? held : null;
			case HALF_SWING -> null;
			case MOB -> held.isIn(Grafting.WEAPONS) ? held : null;
			case ARROW, TRIDENT -> projectileWeapon;
		};
		List<Grafting.Weighted> grafts = grafts(weapon);
		// landed: the blow's own damage went through and the victim lived through it (Fabric fires nothing on a kill)
		boolean landed = LANDED.stream().anyMatch(event -> event.getKey() == victim && event.getValue().getAttacker() == attacker
				&& !event.getValue().isIn(DamageTypeTags.IS_EXPLOSION));
		// killed by the blow itself, not by a burst that followed it
		boolean died = DEATHS.stream().anyMatch(event -> event.getKey() == victim && !event.getValue().isIn(DamageTypeTags.IS_EXPLOSION));
		boolean struck = landed && !grafts.isEmpty();
		boolean meleeBlow = attack == Attack.SWING || attack == Attack.HALF_SWING || attack == Attack.MOB;

		failures.check(!player.hasAttached(EvolutaAttachments.SWING), cycle, () -> what + ": a swing was left on the player");

		// the victim, if it is still alive: exactly its grafts' fire, freezing and effects
		if (victim.isAlive()) {
			float burn = 0;
			int freeze = 0;
			for (Grafting.Weighted graft : struck ? grafts : List.<Grafting.Weighted>of()) {
				if (graft.graft().element() == Element.IGNITED) {
					burn = Math.max(burn, 2.0F + 4.0F * graft.power());
				}
				if (graft.graft().element() == Element.PERMAFROST) {
					freeze += Math.round((40.0F + 80.0F * graft.power()) * graft.weight());
				}
			}
			int wantFire = burn > 0 && !victim.isFireImmune() ? MathHelper.floor(burn * 20.0F) : 0;
			int fire = Math.max(0, victim.getFireTicks());
			// vanilla: a burning zombie's bare-handed blow may set its target alight, and retaliation may have lit it
			boolean passedOn = attack == Attack.MOB && held.isEmpty() && attacker.isOnFire();
			failures.check(fire == wantFire || passedOn && fire > wantFire, cycle, () -> what + ": the victim burns for " + fire + " ticks, the rules say "
					+ wantFire);
			int wantFrozen = freeze > 0 && victim.canFreeze() ? Math.min(FREEZE_CAP, freeze) : 0;
			int frozen = victim.getFrozenTicks();
			failures.check(frozen == wantFrozen, cycle, () -> what + ": the victim is frozen " + frozen + " ticks, the rules say " + wantFrozen);
			// effects: each graft's own, and nothing a graft does not give
			Set<RegistryEntry<StatusEffect>> allowed = new HashSet<>();
			Set<RegistryEntry<StatusEffect>> required = new HashSet<>();
			if (struck) {
				if (has(grafts, SoulKind.ZOMBIE_VILLAGER)) {
					required.add(StatusEffects.WEAKNESS);
				}
				if (has(grafts, SoulKind.HUSK)) {
					required.add(StatusEffects.HUNGER);
				}
				if (has(grafts, SoulKind.STRAY) || has(grafts, Element.PERMAFROST) && victim.canFreeze()) {
					required.add(StatusEffects.SLOWNESS);
				}
				if (has(grafts, Element.TOXIC) || has(grafts, SoulKind.BOGGED)) {
					required.add(StatusEffects.POISON);
				}
				allowed.addAll(required);
				if (has(grafts, SoulKind.SPIDER)) {
					allowed.add(StatusEffects.SLOWNESS);
				}
				if (has(grafts, SoulKind.CAVE_SPIDER)) {
					allowed.add(StatusEffects.POISON);
				}
			}
			for (StatusEffectInstance effect : victim.getStatusEffects()) {
				failures.check(allowed.contains(effect.getEffectType()), cycle, () -> what + ": the victim has " + effect + ", which no graft on the hit gives");
			}
			for (RegistryEntry<StatusEffect> effect : required) {
				boolean could = victim.canHaveStatusEffect(new StatusEffectInstance(effect, 20));
				failures.check(!could || victim.hasStatusEffect(effect), cycle, () -> what + ": the victim lacks " + effect.getIdAsString()
						+ ", which a graft on the hit gives");
			}
			StatusEffectInstance poison = victim.getStatusEffect(StatusEffects.POISON);
			boolean strong = struck && grafts.stream().anyMatch(graft -> graft.graft().element() == Element.TOXIC && graft.power() >= 0.9F);
			failures.check(!strong || poison == null || poison.getAmplifier() >= 1, cycle, () -> what + ": a Toxic roll of 0.9 or more poisons at level "
					+ (poison == null ? "none" : poison.getAmplifier() + 1));
		}

		// the burst: on the documented count, round the victim, never the wielder, nothing set off by it
		boolean creeper = struck && has(grafts, SoulKind.CREEPER);
		GraftCombat.Charge prior = charge == null ? new GraftCombat.Charge(0, Long.MIN_VALUE / 2) : charge;
		boolean resting = now - prior.lastBurst() < BURST_REST;
		boolean burst = creeper && !resting && prior.hits() + 1 >= BURST_EVERY;
		GraftCombat.Charge after = attacker.getAttached(EvolutaAttachments.CREEPER_CHARGE);
		GraftCombat.Charge wantAfter = !creeper || resting ? charge : burst ? new GraftCombat.Charge(0, now) : new GraftCombat.Charge(prior.hits() + 1, prior.lastBurst());
		failures.check(java.util.Objects.equals(after, wantAfter), cycle, () -> what + ": the creeper charge went from " + charge + " to " + after + ", the rules say "
				+ wantAfter);
		List<LivingEntity> blasted = new ArrayList<>();
		for (Map.Entry<LivingEntity, DamageSource> event : ATTEMPTS) {
			if (event.getValue().isIn(DamageTypeTags.IS_EXPLOSION) && event.getValue().getAttacker() == attacker) {
				blasted.add(event.getKey());
			}
		}
		failures.check(burst == !blasted.isEmpty(), cycle, () -> what + ": burst " + !blasted.isEmpty() + ", the rules say " + burst);
		failures.check(!blasted.contains(attacker), cycle, () -> what + ": the burst hit its own wielder");
		failures.check(blasted.size() == new HashSet<>(blasted).size(), cycle, () -> what + ": a burst hit something twice (a chain): " + blasted.size() + " blasts");
		if (burst) {
			failures.check(blasted.contains(bystander), cycle, () -> what + ": the burst missed the bystander beside the victim");
		}

		// retaliation: only a melee blow on a wearer that lived, only the victim's armour
		boolean retaliates = meleeBlow && landed;
		float retaliateBurn = 0;
		int retaliateFreeze = 0;
		boolean retaliatePoison = false;
		for (ItemStack piece : retaliates ? victim.getArmorItems() : List.<ItemStack>of()) {
			for (Grafting.Weighted graft : grafts(piece)) {
				switch (graft.graft().element()) {
					case IGNITED -> retaliateBurn = Math.max(retaliateBurn, 1.0F + 2.0F * graft.power());
					case PERMAFROST -> retaliateFreeze += Math.round((40.0F + 60.0F * graft.power()) * graft.weight());
					case TOXIC -> retaliatePoison = true;
					default -> {
					}
				}
			}
		}
		int wantAttackerFire = retaliateBurn > 0 && !attacker.isFireImmune() ? MathHelper.floor(retaliateBurn * 20.0F) : 0;
		int attackerFire = Math.max(0, attacker.getFireTicks());
		failures.check(attackerFire == wantAttackerFire, cycle, () -> what + ": the wielder burns for " + attackerFire + " ticks, the rules say "
				+ wantAttackerFire);
		int wantAttackerFrozen = retaliateFreeze > 0 && attacker.canFreeze() ? Math.min(FREEZE_CAP, retaliateFreeze) : 0;
		int attackerFrozen = attacker.getFrozenTicks();
		failures.check(attackerFrozen == wantAttackerFrozen, cycle, () -> what + ": the wielder is frozen " + attackerFrozen + " ticks, the rules say "
				+ wantAttackerFrozen);
		boolean poisoned = attacker.hasStatusEffect(StatusEffects.POISON);
		boolean wantPoisoned = retaliatePoison && attacker.canHaveStatusEffect(new StatusEffectInstance(StatusEffects.POISON, 20));
		failures.check(poisoned == wantPoisoned, cycle, () -> what + ": the wielder poisoned " + poisoned + ", the rules say " + wantPoisoned);

		// the feast and soul fire, on a counted kill
		float heal = 0;
		boolean blaze = false;
		if (died && !grafts.isEmpty()) {
			for (Grafting.Weighted graft : grafts) {
				if (graft.graft().kind() == SoulKind.ZOMBIE) {
					heal += (1.0F + 2.0F * graft.power()) * graft.weight();
				}
				blaze |= graft.graft().element() == Element.IGNITED && graft.graft().grade() == Tier.APEX;
			}
		}
		float wantHealth = Math.min(attacker.getMaxHealth(), attackerHealth + heal);
		float health = attacker.getHealth();
		// vanilla thorns (a guardian's spikes) may hurt the wielder; nothing else may
		boolean thorned = ATTEMPTS.stream().anyMatch(event -> event.getKey() == attacker && event.getValue().isOf(DamageTypes.THORNS));
		failures.check(thorned ? health <= wantHealth + 1.0E-3F : Math.abs(health - wantHealth) < 1.0E-3F, cycle, () -> what
				+ ": the wielder's health went from " + attackerHealth + " to " + health + ", the rules say " + wantHealth);
		int soulFire = count(world, area, HazardBlockTypes.SOUL_FIRE);
		boolean wantBlaze = blaze && attacker instanceof PlayerEntity;
		failures.check(wantBlaze || soulFire <= soulFireBefore, cycle, () -> what + ": soul fire from a kill that should leave none (" + soulFireBefore + " to "
				+ soulFire + ")");
	}

	// ---------------------------------------------------------------------------------------------------------------
	// Volleys

	/**
	 * Any graftable item or a plain one, in either hand, a shield or not in the other, sneaking or not, cooling down or
	 * not: using it fires a volley exactly when the rules say (a skeleton graft on a main-hand weapon that has no use of
	 * its own, no cooldown, no shield waiting to block), one arrow carrying the weapon's grafts, not to be picked up,
	 * burning for an Ignited graft, with the documented cooldown; and an item with its own use (a trident's throw)
	 * keeps it.
	 */
	@GameTest(templateName = EMPTY_STRUCTURE, batchId = BATCH, tickLimit = 400)
	public void volleysFireOnlyByTheirRules(TestContext context) {
		ServerWorld world = context.getWorld();
		ServerPlayerEntity player = TestPlayers.survival(context, new BlockPos(3, 2, 3), 0);
		Box around = new Box(context.getAbsolutePos(new BlockPos(3, 2, 3))).expand(6);
		Gear gear = new Gear();
		List<Item> items = new ArrayList<>(gear.weapons);
		items.addAll(gear.others);
		ToolFuzz.Failures failures = new ToolFuzz.Failures("volley fuzz");
		try {
			for (int cycle = 0; cycle < Math.max(300, ToolFuzz.CYCLES / 3); cycle++) {
				SplittableRandom random = ToolFuzz.random("volleys", cycle);
				failures.ran();
				Item item = random.nextInt(8) == 0 ? Items.TRIDENT : ToolFuzz.pick(random, items);
				List<Graft> grafts = new ArrayList<>(ToolFuzz.grafts(random, Grafting.MAX_GRAFTS));
				if (random.nextBoolean()) {
					Graft first = grafts.get(0);
					grafts.set(0, new Graft(SoulKind.SKELETON, first.element(), first.grade(), first.power()));
				}
				ItemStack stack = random.nextInt(8) == 0 ? ToolFuzz.tool(random, world, item) : ToolFuzz.grafted(ToolFuzz.tool(random, world, item), grafts);
				Hand hand = random.nextInt(5) == 0 ? Hand.OFF_HAND : Hand.MAIN_HAND;
				ItemStack other = random.nextInt(3) == 0 ? new ItemStack(Items.SHIELD) : ItemStack.EMPTY;
				boolean sneaking = random.nextBoolean();
				boolean cooling = random.nextInt(4) == 0;
				String what = ToolFuzz.describe(stack) + " in the " + (hand == Hand.MAIN_HAND ? "main" : "off") + " hand, " + (other.isEmpty() ? "nothing" : "a shield")
						+ " in the other" + (sneaking ? ", sneaking" : "") + (cooling ? ", cooling down" : "");
				try {
					player.stopUsingItem();
					player.getItemCooldownManager().remove(item);
					player.getItemCooldownManager().remove(Items.SHIELD);
					world.getEntitiesByClass(ArrowEntity.class, around, arrow -> true).forEach(Entity::discard);
					player.setStackInHand(hand, stack);
					player.setStackInHand(hand == Hand.MAIN_HAND ? Hand.OFF_HAND : Hand.MAIN_HAND, other);
					player.setSneaking(sneaking);
					if (cooling) {
						player.getItemCooldownManager().set(item, 20);
					}
					player.interactionManager.interactItem(player, world, stack, hand);
					List<Grafting.Weighted> weighted = grafts(stack);
					Grafting.Weighted volley = null;
					for (Grafting.Weighted graft : weighted) {
						if (graft.graft().kind() == SoulKind.SKELETON && (volley == null || graft.power() * graft.weight() > volley.power() * volley.weight())) {
							volley = graft;
						}
					}
					boolean fires = hand == Hand.MAIN_HAND && volley != null && stack.isIn(Grafting.WEAPONS) && !(stack.getItem() instanceof RangedWeaponItem)
							&& stack.getUseAction() == UseAction.NONE && !cooling && !(other.getUseAction() == UseAction.BLOCK && !sneaking);
					List<ArrowEntity> arrows = world.getEntitiesByClass(ArrowEntity.class, around, arrow -> arrow.getOwner() == player);
					boolean wantFires = fires;
					failures.check(arrows.size() == (wantFires ? 1 : 0), cycle, () -> what + ": " + arrows.size() + " arrows, the rules say " + (wantFires ? 1 : 0));
					if (fires && arrows.size() == 1) {
						ArrowEntity arrow = arrows.get(0);
						Grafting.Weighted strongest = volley;
						failures.check(arrow.getWeaponStack() != null && Grafting.grafts(arrow.getWeaponStack()).equals(Grafting.grafts(stack)), cycle,
								() -> what + ": the arrow does not carry the weapon's grafts");
						failures.check(arrow.pickupType == PersistentProjectileEntity.PickupPermission.DISALLOWED, cycle, () -> what + ": the volley arrow can be picked up");
						failures.check(arrow.isOnFire() == (strongest.graft().element() == Element.IGNITED), cycle, () -> what + ": the arrow burns " + arrow.isOnFire()
								+ " for a strongest skeleton graft of " + strongest.graft().element().key());
						int wantCooldown = Math.round(80.0F - 40.0F * strongest.power());
						int ticks = 0;
						while (player.getItemCooldownManager().isCoolingDown(item) && ticks < 200) {
							player.getItemCooldownManager().update();
							ticks++;
						}
						int cooled = ticks;
						failures.check(cooled == wantCooldown, cycle, () -> what + ": the volley cooled down for " + cooled + " ticks, the rules say " + wantCooldown);
					}
					if (item == Items.TRIDENT && hand == Hand.MAIN_HAND && !cooling && stack.getDamage() < stack.getMaxDamage() - 1) {
						failures.check(player.isUsingItem(), cycle, () -> what + ": the trident did not start its throw");
					}
				} catch (RuntimeException e) {
					failures.add(cycle, what + " threw " + e);
				}
			}
		} finally {
			player.stopUsingItem();
			world.getEntitiesByClass(ArrowEntity.class, around, arrow -> true).forEach(Entity::discard);
			TestPlayers.remove(context, player);
		}
		failures.throwIfAny();
		context.complete();
	}
}
