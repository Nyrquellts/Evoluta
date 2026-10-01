package com.nyr.evoluta.common.soul;

import com.nyr.evoluta.common.combat.Hazards;
import com.nyr.evoluta.common.combat.Juice;
import com.nyr.evoluta.common.mutation.Element;
import com.nyr.evoluta.common.mutation.EvolutaAttachments;
import com.nyr.evoluta.common.mutation.Tier;
import java.util.List;
import net.fabricmc.fabric.api.entity.event.v1.ServerLivingEntityEvents;
import net.fabricmc.fabric.api.event.player.UseItemCallback;
import net.minecraft.entity.Entity;
import net.minecraft.entity.LivingEntity;
import net.minecraft.entity.damage.DamageSource;
import net.minecraft.entity.damage.DamageTypes;
import net.minecraft.entity.effect.StatusEffect;
import net.minecraft.entity.effect.StatusEffectInstance;
import net.minecraft.entity.effect.StatusEffects;
import net.minecraft.entity.player.PlayerEntity;
import net.minecraft.entity.projectile.ArrowEntity;
import net.minecraft.entity.projectile.PersistentProjectileEntity;
import net.minecraft.entity.projectile.ProjectileEntity;
import net.minecraft.item.ItemStack;
import net.minecraft.item.Items;
import net.minecraft.item.RangedWeaponItem;
import net.minecraft.particle.ParticleTypes;
import net.minecraft.registry.entry.RegistryEntry;
import net.minecraft.registry.tag.DamageTypeTags;
import net.minecraft.registry.tag.EntityTypeTags;
import net.minecraft.server.world.ServerWorld;
import net.minecraft.sound.SoundCategory;
import net.minecraft.sound.SoundEvents;
import net.minecraft.util.Hand;
import net.minecraft.util.TypedActionResult;
import net.minecraft.util.UseAction;
import net.minecraft.util.math.Box;
import net.minecraft.util.math.Vec3d;
import net.minecraft.world.World;
import org.jetbrains.annotations.Nullable;

/**
 * What grafts do in a fight. Everything runs from events that fire anyway (a hit, a death, a use), never per tick:
 * bonus damage joins the hit itself before armour (head of LivingEntity#damage), effects follow once the hit lands,
 * wards trim what the wearer takes. A player's swing counts only in full (90% charged or more) and only on the
 * entity it was aimed at, not what a sweep catches; a mob's hits and every projectile always count, and arrows carry
 * the grafts of the bow (or the sword) that fired them. Numbers: docs/soul-meat.md.
 */
public final class GraftCombat {
	/** A player's swing counts from this much of the attack cooldown, as vanilla's critical hits do. */
	public static final float FULL_SWING = 0.9F;
	/** A creeper graft bursts on every this-many full hits, then rests. */
	static final int BURST_EVERY = 3;
	static final int BURST_COOLDOWN = 100;
	/** A weapon hit's freezing never piles past this many frozen ticks (140 is where vanilla starts freeze damage). */
	static final int FREEZE_CAP = 200;

	/** A player's swing in progress: whom it was aimed at and how charged it was, before vanilla resets the cooldown. */
	public record Swing(int target, float strength) {
	}

	/** A creeper graft's count of full hits toward its next burst, and when it last burst. */
	public record Charge(int hits, long lastBurst) {
		static final Charge NONE = new Charge(0, Long.MIN_VALUE / 2);
	}

	private GraftCombat() {
	}

	public static void register() {
		ServerLivingEntityEvents.AFTER_DAMAGE.register(GraftCombat::afterDamage);
		ServerLivingEntityEvents.AFTER_DEATH.register(GraftCombat::afterDeath);
		UseItemCallback.EVENT.register(GraftCombat::use);
	}

	/** Head of PlayerEntity#attack, while the cooldown still shows how charged the swing is. */
	public static void beginSwing(PlayerEntity player, Entity target) {
		player.setAttached(EvolutaAttachments.SWING, new Swing(target.getId(), player.getAttackCooldownProgress(0.5F)));
	}

	public static void endSwing(PlayerEntity player) {
		player.removeAttached(EvolutaAttachments.SWING);
	}

	/** A blow struck in person: a player's or a mob's own hit, or a sting. Not a burst, thorns, a sonic boom or a shot. */
	public static boolean isMelee(DamageSource source) {
		return source.isOf(DamageTypes.PLAYER_ATTACK) || source.isOf(DamageTypes.MOB_ATTACK) || source.isOf(DamageTypes.MOB_ATTACK_NO_AGGRO)
				|| source.isOf(DamageTypes.STING);
	}

	/**
	 * The graft-bearing weapon behind a hit, or null when the hit does not count: a projectile's bow, trident or sword;
	 * a mob's main hand; a player's main hand on a full swing at the entity it was aimed at. Bursts and other splash
	 * damage are never weapon hits, so grafts cannot set each other off. Only a weapon counts: a pickaxe's grafts dig
	 * ({@link ToolGrafts}) and a chestplate's guard, whatever hand holds them.
	 */
	@Nullable
	static ItemStack weapon(LivingEntity victim, DamageSource source) {
		Entity direct = source.getSource();
		if (direct instanceof ProjectileEntity projectile) {
			return projectile.getWeaponStack();
		}
		if (!(direct instanceof LivingEntity attacker) || direct != source.getAttacker() || !isMelee(source)) {
			return null;
		}
		if (attacker instanceof PlayerEntity player) {
			Swing swing = player.getAttached(EvolutaAttachments.SWING);
			if (swing == null || swing.target() != victim.getId() || swing.strength() < FULL_SWING) {
				return null;
			}
		}
		ItemStack held = attacker.getMainHandStack();
		return Grafting.isWeapon(held) ? held : null;
	}

	/** Head of LivingEntity#damage: the weapon's element (and the drowned's and skeleton's bonus) joins the hit; wards trim it. */
	public static float modifyDamage(LivingEntity victim, DamageSource source, float amount) {
		if (victim.getWorld().isClient) {
			return amount;
		}
		float result = amount;
		ItemStack weapon = weapon(victim, source);
		if (weapon != null && weapon.contains(SoulComponents.GRAFTS)) {
			result += bonusDamage(victim, source, Grafting.weighted(Grafting.grafts(weapon)));
		}
		if (source.isIn(DamageTypeTags.IS_PROJECTILE) || source.isIn(DamageTypeTags.IS_EXPLOSION) || source.isIn(DamageTypeTags.IS_FREEZING)) {
			result *= 1.0F - ward(victim, source);
		}
		return result;
	}

	static float bonusDamage(LivingEntity victim, DamageSource source, List<Grafting.Weighted> grafts) {
		float bonus = 0.0F;
		for (Grafting.Weighted graft : grafts) {
			float p = graft.power();
			float w = graft.weight();
			float element = (0.5F + 1.5F * p) * w;
			switch (graft.graft().element()) {
				case IGNITED -> bonus += victim.isFireImmune() ? 0.0F : element;
				case PERMAFROST -> {
					if (!victim.getType().isIn(EntityTypeTags.FREEZE_IMMUNE_ENTITY_TYPES)) {
						// blazes, magma cubes and striders take extra from the cold, as vanilla's freezing has them do
						bonus += victim.getType().isIn(EntityTypeTags.FREEZE_HURTS_EXTRA_TYPES) ? 2.0F * element : element;
					}
				}
				case TOXIC -> bonus += element;
				default -> {
				}
			}
			if (graft.graft().kind() == SoulKind.DROWNED && victim.isWet()) {
				bonus += (1.0F + 2.0F * p) * w;
			}
			if (graft.graft().kind() == SoulKind.SKELETON && source.isIn(DamageTypeTags.IS_PROJECTILE)) {
				bonus += (0.5F + p) * w;
			}
		}
		return bonus;
	}

	/** The share of the damage the victim's armour grafts take off: skeleton against arrows, creeper against blasts, stray and Permafrost against the cold. */
	static float ward(LivingEntity victim, DamageSource source) {
		boolean projectile = source.isIn(DamageTypeTags.IS_PROJECTILE);
		boolean explosion = source.isIn(DamageTypeTags.IS_EXPLOSION);
		boolean freezing = source.isIn(DamageTypeTags.IS_FREEZING);
		float ward = 0.0F;
		for (ItemStack armor : victim.getArmorItems()) {
			if (!armor.contains(SoulComponents.GRAFTS)) {
				continue;
			}
			for (Grafting.Weighted graft : Grafting.weighted(Grafting.grafts(armor))) {
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

	/** Head of LivingEntity#addStatusEffect: bogged and Toxic armour grafts shorten poison. */
	public static StatusEffectInstance wardEffect(LivingEntity entity, StatusEffectInstance effect) {
		if (entity.getWorld().isClient || !effect.getEffectType().equals(StatusEffects.POISON) || effect.isInfinite()) {
			// an infinite poison (a command's, a map's) is not the ward's to shorten
			return effect;
		}
		float ward = 0.0F;
		for (ItemStack armor : entity.getArmorItems()) {
			if (!armor.contains(SoulComponents.GRAFTS)) {
				continue;
			}
			for (Grafting.Weighted graft : Grafting.weighted(Grafting.grafts(armor))) {
				if (graft.graft().kind() == SoulKind.BOGGED || graft.graft().element() == Element.TOXIC) {
					ward += (0.25F + 0.5F * graft.power()) * graft.weight();
				}
			}
		}
		ward = Math.min(0.8F, ward);
		if (ward <= 0.0F) {
			return effect;
		}
		return new StatusEffectInstance(effect.getEffectType(), Math.max(1, Math.round(effect.getDuration() * (1.0F - ward))), effect.getAmplifier(),
				effect.isAmbient(), effect.shouldShowParticles(), effect.shouldShowIcon());
	}

	private static void afterDamage(LivingEntity victim, DamageSource source, float baseDamage, float taken, boolean blocked) {
		if (!(victim.getWorld() instanceof ServerWorld world) || blocked) {
			return;
		}
		ItemStack weapon = weapon(victim, source);
		if (weapon != null && weapon.contains(SoulComponents.GRAFTS) && source.getAttacker() instanceof LivingEntity attacker) {
			onHit(world, attacker, victim, source, Grafting.weighted(Grafting.grafts(weapon)));
		}
		// armour strikes back at a blow struck in person, not at a burst, thorns or a sonic boom that came from someone
		if (isMelee(source) && source.getSource() instanceof LivingEntity attacker && source.getSource() == source.getAttacker() && attacker != victim) {
			retaliate(victim, attacker);
		}
	}

	private static void onHit(ServerWorld world, LivingEntity attacker, LivingEntity victim, DamageSource source, List<Grafting.Weighted> grafts) {
		// the strongest graft's element bursts out where the weapon lands
		if (!grafts.isEmpty()) {
			Grafting.Weighted strongest = grafts.get(0);
			Juice.impact(world, victim, strongest.graft().element(), strongest.power() * strongest.weight());
		}
		float burn = 0.0F;
		int freeze = 0;
		int poison = 0;
		boolean strongPoison = false;
		float burst = 0.0F;
		for (Grafting.Weighted graft : grafts) {
			float p = graft.power();
			if (graft.graft().kind() == SoulKind.CREEPER) {
				burst += (2.0F + 4.0F * p) * graft.weight();
			}
			switch (graft.graft().element()) {
				case IGNITED -> burn = Math.max(burn, 2.0F + 4.0F * p);
				case PERMAFROST -> freeze += Math.round((40.0F + 80.0F * p) * graft.weight());
				case TOXIC -> {
					poison = Math.max(poison, Math.round((2.0F + 3.0F * p) * 20.0F));
					strongPoison |= p >= 0.9F;
				}
				default -> {
				}
			}
			kindOnHit(world, attacker, victim, source, graft);
		}
		if (burn > 0.0F && !victim.isFireImmune()) {
			victim.setOnFireFor(burn);
		}
		if (freeze > 0 && victim.canFreeze()) {
			victim.setFrozenTicks(Math.min(FREEZE_CAP, victim.getFrozenTicks() + freeze));
			// numbed hands for a player, numbed legs for anything else
			effect(victim, attacker, victim instanceof PlayerEntity ? StatusEffects.MINING_FATIGUE : StatusEffects.SLOWNESS, 20, 0);
		}
		if (poison > 0) {
			effect(victim, attacker, StatusEffects.POISON, poison, strongPoison ? 1 : 0);
		}
		if (burst > 0.0F) {
			// one count per hit however many creeper grafts the weapon carries; their bursts add up
			charge(world, attacker, victim, burst);
		}
	}

	private static void kindOnHit(ServerWorld world, LivingEntity attacker, LivingEntity victim, DamageSource source, Grafting.Weighted graft) {
		float p = graft.power();
		float w = graft.weight();
		switch (graft.graft().kind()) {
			case HUSK -> effect(victim, attacker, StatusEffects.HUNGER, seconds(3.0F + 3.0F * p), 1);
			case ZOMBIE_VILLAGER -> effect(victim, attacker, StatusEffects.WEAKNESS, seconds(2.0F + 3.0F * p), 0);
			case STRAY -> effect(victim, attacker, StatusEffects.SLOWNESS, seconds(1.0F + 2.0F * p), 1);
			case BOGGED -> effect(victim, attacker, StatusEffects.POISON, seconds(2.0F + 2.0F * p), 0);
			case SPIDER -> {
				if (world.getRandom().nextFloat() < (0.2F + 0.3F * p) * w) {
					effect(victim, attacker, StatusEffects.SLOWNESS, 30, 2);
				}
			}
			case CAVE_SPIDER -> {
				if (world.getRandom().nextFloat() < 0.5F) {
					effect(victim, attacker, StatusEffects.POISON, seconds(1.0F + 2.0F * p), 1);
				}
			}
			case DROWNED -> {
				if (!(source.getSource() instanceof ProjectileEntity)) {
					// the undertow: drags the target back in against vanilla's knockback
					Vec3d pull = attacker.getPos().subtract(victim.getPos()).multiply(1.0, 0.0, 1.0).normalize().multiply((0.4 + 0.4 * p) * w);
					victim.addVelocity(pull.x, 0.1, pull.z);
					victim.velocityModified = true;
				}
			}
			default -> {
				// zombie feasts on kills, skeleton volleys on use and fires harder arrows, baby zombie is an attribute,
				// creeper bursts (counted once per hit in onHit)
			}
		}
	}

	/** Every third full hit, a burst round the target: splash damage and a shove for whatever stands near, never the wielder. */
	private static void charge(ServerWorld world, LivingEntity attacker, LivingEntity victim, float damage) {
		long now = world.getTime();
		Charge charge = attacker.getAttachedOrElse(EvolutaAttachments.CREEPER_CHARGE, Charge.NONE);
		if (now - charge.lastBurst() < BURST_COOLDOWN) {
			return;
		}
		if (charge.hits() + 1 < BURST_EVERY) {
			attacker.setAttached(EvolutaAttachments.CREEPER_CHARGE, new Charge(charge.hits() + 1, charge.lastBurst()));
			return;
		}
		attacker.setAttached(EvolutaAttachments.CREEPER_CHARGE, new Charge(0, now));
		Box around = victim.getBoundingBox().expand(2.5);
		for (LivingEntity near : world.getEntitiesByClass(LivingEntity.class, around, entity -> entity != attacker && entity.isAlive())) {
			near.damage(world.getDamageSources().explosion(attacker, attacker), damage);
			Vec3d push = near.getPos().subtract(victim.getPos()).multiply(1.0, 0.0, 1.0);
			push = push.lengthSquared() < 1.0E-4 ? Vec3d.ZERO : push.normalize().multiply(0.6);
			near.addVelocity(push.x, 0.3, push.z);
			near.velocityModified = true;
		}
		world.spawnParticles(ParticleTypes.EXPLOSION, victim.getX(), victim.getBodyY(0.5), victim.getZ(), 1, 0.0, 0.0, 0.0, 0.0);
		world.playSound(null, victim.getX(), victim.getY(), victim.getZ(), SoundEvents.ENTITY_GENERIC_EXPLODE.value(), SoundCategory.PLAYERS, 0.6F, 1.4F);
	}

	/** Armour grafts strike back at whoever hit the wearer in melee: burning, freezing or poisoning them. */
	private static void retaliate(LivingEntity wearer, LivingEntity attacker) {
		float burn = 0.0F;
		int freeze = 0;
		int poison = 0;
		for (ItemStack armor : wearer.getArmorItems()) {
			if (!armor.contains(SoulComponents.GRAFTS)) {
				continue;
			}
			for (Grafting.Weighted graft : Grafting.weighted(Grafting.grafts(armor))) {
				float p = graft.power();
				switch (graft.graft().element()) {
					case IGNITED -> burn = Math.max(burn, 1.0F + 2.0F * p);
					case PERMAFROST -> freeze += Math.round((40.0F + 60.0F * p) * graft.weight());
					case TOXIC -> poison = Math.max(poison, seconds(1.0F + 2.0F * p));
					default -> {
					}
				}
			}
		}
		if (burn > 0.0F && !attacker.isFireImmune()) {
			attacker.setOnFireFor(burn);
		}
		if (freeze > 0 && attacker.canFreeze()) {
			attacker.setFrozenTicks(Math.min(FREEZE_CAP, attacker.getFrozenTicks() + freeze));
		}
		if (poison > 0) {
			effect(attacker, wearer, StatusEffects.POISON, poison, 0);
		}
	}

	/**
	 * A kill by a counted weapon hit: a zombie graft feasts (the killer heals), and a player's Apex-grade Ignited graft
	 * leaves soul fire where the victim fell.
	 */
	private static void afterDeath(LivingEntity victim, DamageSource source) {
		if (!(source.getAttacker() instanceof LivingEntity killer) || !(victim.getWorld() instanceof ServerWorld world)) {
			return;
		}
		ItemStack weapon = weapon(victim, source);
		if (weapon == null || !weapon.contains(SoulComponents.GRAFTS)) {
			return;
		}
		float heal = 0.0F;
		float blaze = 0.0F;
		for (Grafting.Weighted graft : Grafting.weighted(Grafting.grafts(weapon))) {
			if (graft.graft().kind() == SoulKind.ZOMBIE) {
				heal += (1.0F + 2.0F * graft.power()) * graft.weight();
			}
			if (graft.graft().element() == Element.IGNITED && graft.graft().grade() == Tier.APEX) {
				blaze = Math.max(blaze, graft.power() * graft.weight());
			}
		}
		if (heal > 0.0F) {
			killer.heal(heal);
		}
		if (blaze > 0.0F && killer instanceof PlayerEntity player) {
			Hazards.kindleSoulFire(world, victim, blaze);
		}
	}

	/**
	 * A skeleton graft on a melee weapon: using it fires an arrow that carries the weapon's grafts. Only a weapon with no
	 * use of its own (a trident keeps its throw), and a shield in the other hand keeps its use unless the player sneaks.
	 * The cooldown is the vanilla item cooldown, shown on the hotbar.
	 */
	private static TypedActionResult<ItemStack> use(PlayerEntity player, World world, Hand hand) {
		ItemStack stack = player.getStackInHand(hand);
		if (hand != Hand.MAIN_HAND || player.isSpectator() || stack.getItem() instanceof RangedWeaponItem || !stack.contains(SoulComponents.GRAFTS)
				|| !Grafting.isWeapon(stack) || stack.getUseAction() != UseAction.NONE) {
			return TypedActionResult.pass(stack);
		}
		Grafting.Weighted volley = null;
		for (Grafting.Weighted graft : Grafting.weighted(Grafting.grafts(stack))) {
			if (graft.graft().kind() == SoulKind.SKELETON && (volley == null || graft.power() * graft.weight() > volley.power() * volley.weight())) {
				volley = graft;
			}
		}
		if (volley == null || player.getItemCooldownManager().isCoolingDown(stack.getItem())
				|| player.getOffHandStack().getUseAction() == UseAction.BLOCK && !player.isSneaking()) {
			return TypedActionResult.pass(stack);
		}
		if (world instanceof ServerWorld server) {
			ArrowEntity arrow = new ArrowEntity(server, player, new ItemStack(Items.ARROW), stack);
			arrow.setVelocity(player, player.getPitch(), player.getYaw(), 0.0F, 2.6F, 1.0F);
			arrow.pickupType = PersistentProjectileEntity.PickupPermission.DISALLOWED;
			if (volley.graft().element() == Element.IGNITED) {
				arrow.setOnFireFor(100.0F);
			}
			server.spawnEntity(arrow);
			server.playSound(null, player.getX(), player.getY(), player.getZ(), SoundEvents.ENTITY_SKELETON_SHOOT, SoundCategory.PLAYERS, 1.0F, 1.2F);
			player.getItemCooldownManager().set(stack.getItem(), Math.round(80.0F - 40.0F * volley.power()));
		}
		return TypedActionResult.success(stack, world.isClient());
	}

	private static void effect(LivingEntity target, LivingEntity source, RegistryEntry<StatusEffect> type, int ticks, int amplifier) {
		target.addStatusEffect(new StatusEffectInstance(type, ticks, amplifier), source);
	}

	private static int seconds(float seconds) {
		return Math.round(seconds * 20.0F);
	}
}
