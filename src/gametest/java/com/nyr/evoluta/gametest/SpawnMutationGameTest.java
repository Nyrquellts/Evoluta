package com.nyr.evoluta.gametest;

import com.nyr.evoluta.common.config.EvolutaConfig;
import com.nyr.evoluta.common.mutation.Archetype;
import com.nyr.evoluta.common.mutation.BoneDagger;
import com.nyr.evoluta.common.mutation.Element;
import com.nyr.evoluta.common.mutation.EvolutaAttachments;
import com.nyr.evoluta.common.mutation.MutationData;
import com.nyr.evoluta.common.mutation.Mutations;
import com.nyr.evoluta.common.mutation.SpawnMutations;
import com.nyr.evoluta.common.mutation.Tier;
import com.nyr.evoluta.common.soul.Graft;
import com.nyr.evoluta.common.soul.Grafting;
import com.nyr.evoluta.common.soul.MutantGear;
import com.nyr.evoluta.common.soul.SoulComponents;
import com.nyr.evoluta.common.soul.SoulKind;
import com.nyr.evoluta.common.tag.EvolutaTags;
import java.util.List;
import java.util.concurrent.CompletableFuture;
import net.fabricmc.fabric.api.gametest.v1.FabricGameTest;
import net.minecraft.entity.EntityType;
import net.minecraft.entity.EquipmentSlot;
import net.minecraft.entity.SpawnReason;
import net.minecraft.entity.attribute.EntityAttributes;
import net.minecraft.entity.boss.WitherEntity;
import net.minecraft.entity.mob.DrownedEntity;
import net.minecraft.entity.mob.MobEntity;
import net.minecraft.entity.mob.SkeletonEntity;
import net.minecraft.entity.mob.ZombieEntity;
import net.minecraft.entity.passive.PigEntity;
import net.minecraft.item.ItemStack;
import net.minecraft.item.Items;
import net.minecraft.server.world.ServerWorld;
import net.minecraft.test.AfterBatch;
import net.minecraft.test.BeforeBatch;
import net.minecraft.test.GameTest;
import net.minecraft.test.TestContext;
import net.minecraft.util.Hand;
import net.minecraft.util.math.BlockPos;
import net.minecraft.world.LocalDifficulty;

/**
 * The spawn path end to end: {@code MobEntity#initialize} runs the real mixin. The "forced" batch swaps in a config
 * where every eligible spawn becomes an Elite Permafrost mutant, so outcomes are certain; batches run one at a time,
 * so no other test sees it.
 */
public final class SpawnMutationGameTest implements FabricGameTest {
	private static final String FORCED = "evoluta_forced_spawns";
	private static final EvolutaConfig ALWAYS_ELITE_PERMAFROST = EvolutaConfig.DEFAULTS.toBuilder()
			.mutationChance(1).localDifficultyBonus(0).nightMultiplier(1)
			.tierWeights(0, 1, 0).elementChance(1, 1, 1).archetypeChance(1, 1, 1).elementWeights(0, 1, 0)
			.build();

	private static EvolutaConfig before = EvolutaConfig.DEFAULTS;

	@BeforeBatch(batchId = FORCED)
	public void forceMutations(ServerWorld world) {
		before = EvolutaConfig.get();
		EvolutaConfig.use(ALWAYS_ELITE_PERMAFROST);
	}

	@AfterBatch(batchId = FORCED)
	public void restoreConfig(ServerWorld world) {
		EvolutaConfig.use(before);
	}

	@GameTest(templateName = EMPTY_STRUCTURE, batchId = FORCED)
	public void naturalSpawnMutatesAtFullHealth(TestContext context) {
		ZombieEntity zombie = initialize(context, EntityType.ZOMBIE, SpawnReason.NATURAL);
		MutationData data = Mutations.get(zombie);

		context.assertTrue(data != null, "a natural zombie spawn with chance 1 stayed ordinary");
		context.assertTrue(data.getTier() == Tier.ELITE && data.getElement() == Element.PERMAFROST, "rolled " + data);
		context.assertTrue(data.getArchetype() == Archetype.BRUTE || data.getArchetype() == Archetype.STALKER,
				"zombies may be Brutes or Stalkers and archetypeChance is 1, got " + data.getArchetype());
		double healthBonus = zombie.getAttributeInstance(EntityAttributes.GENERIC_MAX_HEALTH).getModifier(Mutations.TIER_HEALTH).value();
		context.assertTrue(healthBonus == EvolutaConfig.DEFAULTS.healthBonus().elite(), "elite health modifier is " + healthBonus);
		context.assertTrue(zombie.getMaxHealth() >= 20 * 1.8F - 0.01F, "elite zombie max health " + zombie.getMaxHealth());
		context.assertTrue(zombie.getHealth() == zombie.getMaxHealth(), "fresh mutant spawned hurt: " + zombie.getHealth() + "/" + zombie.getMaxHealth());
		context.assertTrue(zombie.getAttributeInstance(EntityAttributes.GENERIC_ATTACK_DAMAGE).hasModifier(Mutations.TIER_DAMAGE), "no damage bonus");
		double scale = zombie.getAttributeValue(EntityAttributes.GENERIC_SCALE);
		double expectedScale = 1 + (data.getArchetype() == Archetype.BRUTE ? Mutations.BRUTE_SCALE_BONUS : Mutations.STALKER_SCALE_BONUS);
		context.assertTrue(Math.abs(scale - expectedScale) < 1e-6, data.getArchetype() + " scale is " + scale);
		zombie.discard();
		context.complete();
	}

	/** A champion's own gear carries a graft of its kind and element at its tier, once, from its first load. */
	@GameTest(templateName = EMPTY_STRUCTURE, batchId = FORCED)
	public void championsSpawnWithGraftedGear(TestContext context) {
		ZombieEntity zombie = EntityType.ZOMBIE.create(context.getWorld());
		BlockPos pos = context.getAbsolutePos(new BlockPos(1, 2, 1));
		zombie.refreshPositionAndAngles(pos, 0, 0);
		zombie.equipStack(EquipmentSlot.CHEST, new ItemStack(Items.IRON_CHESTPLATE));
		zombie.equipStack(EquipmentSlot.MAINHAND, new ItemStack(Items.IRON_SWORD));
		// a grown zombie: one in twenty rolls a baby, whose grafts are the baby zombie's
		zombie.initialize(context.getWorld(), context.getWorld().getLocalDifficulty(pos), SpawnReason.NATURAL, new ZombieEntity.ZombieData(false, false));
		context.getWorld().spawnEntity(zombie);
		for (EquipmentSlot slot : new EquipmentSlot[]{EquipmentSlot.CHEST, EquipmentSlot.MAINHAND}) {
			List<Graft> grafts = Grafting.grafts(zombie.getEquippedStack(slot));
			context.assertTrue(grafts.size() == 1, "an Elite's " + slot + " carries " + grafts.size() + " grafts");
			Graft graft = grafts.get(0);
			context.assertTrue(graft.kind() == SoulKind.ZOMBIE && graft.element() == Element.PERMAFROST && graft.grade() == Tier.ELITE,
					"the " + slot + " graft is " + graft);
		}
		// a reload grafts nothing more
		MutantGear.graftOnce(zombie, Mutations.get(zombie), zombie.getRandom());
		context.assertTrue(Grafting.grafts(zombie.getEquippedStack(EquipmentSlot.CHEST)).size() == 1, "a reload grafted the chestplate again");
		zombie.discard();
		context.complete();
	}

	@GameTest(templateName = EMPTY_STRUCTURE, batchId = FORCED)
	public void spawnEggsAndSpawnersStayOrdinary(TestContext context) {
		for (SpawnReason reason : new SpawnReason[]{SpawnReason.SPAWN_EGG, SpawnReason.SPAWNER, SpawnReason.COMMAND}) {
			ZombieEntity zombie = initialize(context, EntityType.ZOMBIE, reason);
			context.assertFalse(zombie.hasAttached(EvolutaAttachments.MUTATION), reason + " spawn mutated with default spawnReasons");
			zombie.discard();
		}
		context.complete();
	}

	@GameTest(templateName = EMPTY_STRUCTURE, batchId = FORCED)
	public void untaggedMobsStayOrdinary(TestContext context) {
		MobEntity cow = initialize(context, EntityType.COW, SpawnReason.NATURAL);
		context.assertFalse(cow.hasAttached(EvolutaAttachments.MUTATION), "a cow is not in #evoluta:can_mutate but mutated");
		cow.discard();
		context.complete();
	}

	/** The test mod adds a pig to #evoluta:can_mutate: the stand-in for a modded mob that needs no code to mutate. */
	@GameTest(templateName = EMPTY_STRUCTURE, batchId = FORCED)
	public void aMobAddedByTagMutatesWithoutCode(TestContext context) {
		PigEntity pig = initialize(context, EntityType.PIG, SpawnReason.NATURAL);
		MutationData data = Mutations.get(pig);
		context.assertTrue(data != null, "a pig added to #evoluta:can_mutate did not mutate");
		context.assertTrue(data.getArchetype() == Archetype.NONE, "pigs are in no archetype tag, got " + data.getArchetype());
		pig.discard();
		context.complete();
	}

	/** The test mod also adds the wither to #evoluta:can_mutate; the blacklist (#c:bosses) must still win. */
	@GameTest(templateName = EMPTY_STRUCTURE, batchId = FORCED)
	public void blacklistBeatsCanMutate(TestContext context) {
		context.assertTrue(EntityType.WITHER.isIn(EvolutaTags.CAN_MUTATE), "test data should put the wither in can_mutate");
		WitherEntity wither = EntityType.WITHER.create(context.getWorld());
		BlockPos pos = context.getAbsolutePos(new BlockPos(1, 2, 1));
		wither.refreshPositionAndAngles(pos, 0, 0);
		wither.initialize(context.getWorld(), context.getWorld().getLocalDifficulty(pos), SpawnReason.NATURAL, null);
		context.assertFalse(wither.hasAttached(EvolutaAttachments.MUTATION), "a blacklisted boss mutated");
		wither.discard();
		context.complete();
	}

	/**
	 * Structure and chunk-generation spawns run initialize on a world-generation thread: there the mob is only
	 * tagged, and it rolls, gear included, when it first loads on the server thread.
	 */
	@GameTest(templateName = EMPTY_STRUCTURE, batchId = FORCED)
	public void worldGenerationSpawnsRollWhenTheyFirstLoad(TestContext context) {
		ServerWorld world = context.getWorld();
		SkeletonEntity skeleton = EntityType.SKELETON.create(world);
		BlockPos pos = context.getAbsolutePos(new BlockPos(1, 2, 1));
		skeleton.refreshPositionAndAngles(pos, 0, 0);
		LocalDifficulty difficulty = world.getLocalDifficulty(pos);
		CompletableFuture.runAsync(() -> skeleton.initialize(world, difficulty, SpawnReason.STRUCTURE, null)).join();

		context.assertFalse(skeleton.hasAttached(EvolutaAttachments.MUTATION), "a mob was mutated off the server thread");
		context.assertTrue(skeleton.getCommandTags().contains(SpawnMutations.PENDING_TAG + "structure"),
				"no pending roll was left for the load: " + skeleton.getCommandTags());

		world.spawnEntity(skeleton);
		MutationData data = Mutations.get(skeleton);
		context.assertTrue(data != null && data.getTier() == Tier.ELITE && data.getElement() == Element.PERMAFROST,
				"the pending roll did not happen on load: " + data);
		context.assertTrue(skeleton.getCommandTags().stream().noneMatch(tag -> tag.startsWith(SpawnMutations.PENDING_TAG)),
				"the pending tag outlived the roll: " + skeleton.getCommandTags());
		if (data.getArchetype() == Archetype.BRUTE) {
			context.assertTrue(BoneDagger.is(skeleton.getMainHandStack()), "a Brute rolled on load kept its bow");
		}
		skeleton.discard();
		context.complete();
	}

	@GameTest(templateName = EMPTY_STRUCTURE)
	public void bruteSkeletonTradesItsBowForABoneDagger(TestContext context) {
		SkeletonEntity skeleton = EntityType.SKELETON.create(context.getWorld());
		BlockPos pos = context.getAbsolutePos(new BlockPos(1, 2, 1));
		skeleton.refreshPositionAndAngles(pos, 0, 0);
		// already a Brute before its own initialize hands it a bow: the loadout must still end up in its hand
		Mutations.apply(skeleton, MutationData.of(Tier.ELITE, Element.NONE, Archetype.BRUTE), EvolutaConfig.get());
		skeleton.initialize(context.getWorld(), context.getWorld().getLocalDifficulty(pos), SpawnReason.NATURAL, null);

		context.assertTrue(BoneDagger.is(skeleton.getMainHandStack()), "Brute skeleton holds " + skeleton.getMainHandStack());
		context.assertFalse(skeleton.isHolding(Items.BOW), "Brute skeleton still holds a bow");
		skeleton.discard();

		SkeletonEntity stalker = EntityType.SKELETON.create(context.getWorld());
		stalker.refreshPositionAndAngles(pos, 0, 0);
		Mutations.apply(stalker, MutationData.of(Tier.ELITE, Element.NONE, Archetype.STALKER), EvolutaConfig.get());
		stalker.initialize(context.getWorld(), context.getWorld().getLocalDifficulty(pos), SpawnReason.NATURAL, null);
		context.assertTrue(stalker.getMainHandStack().isOf(Items.BOW), "Stalker skeleton should keep its bow, holds " + stalker.getMainHandStack());
		stalker.discard();
		context.complete();
	}

	@GameTest(templateName = EMPTY_STRUCTURE)
	public void conversionPassesTheMutationOn(TestContext context) {
		ZombieEntity zombie = context.spawnMob(EntityType.ZOMBIE, 1, 2, 1);
		MutationData data = MutationData.of(Tier.APEX, Element.TOXIC, Archetype.STALKER);
		Mutations.apply(zombie, data, EvolutaConfig.get());

		DrownedEntity drowned = zombie.convertTo(EntityType.DROWNED, true);
		context.assertTrue(drowned != null, "zombie did not convert");
		context.assertTrue(data.equals(Mutations.get(drowned)), "drowned carries " + Mutations.get(drowned));
		context.assertTrue(drowned.getAttributeInstance(EntityAttributes.GENERIC_MAX_HEALTH).hasModifier(Mutations.TIER_HEALTH),
				"converted mutant lost its health bonus");
		context.assertTrue(drowned.getHealth() == drowned.getMaxHealth(), "converted mutant is hurt");
		drowned.discard();
		context.complete();
	}

	/** A summon with only the attachment in its NBT gets its stats when it enters the world. */
	@GameTest(templateName = EMPTY_STRUCTURE)
	public void loadingAMutantFromNbtRestoresItsStats(TestContext context) {
		ZombieEntity zombie = EntityType.ZOMBIE.create(context.getWorld());
		zombie.refreshPositionAndAngles(context.getAbsolutePos(new BlockPos(1, 2, 1)), 0, 0);
		zombie.setAttached(EvolutaAttachments.MUTATION, MutationData.of(Tier.ELITE, Element.IGNITED, Archetype.BRUTE));
		context.assertFalse(zombie.getAttributeInstance(EntityAttributes.GENERIC_MAX_HEALTH).hasModifier(Mutations.TIER_HEALTH),
				"modifier present before load");

		context.getWorld().spawnEntity(zombie);

		context.assertTrue(zombie.getAttributeInstance(EntityAttributes.GENERIC_MAX_HEALTH).hasModifier(Mutations.TIER_HEALTH),
				"loading did not add the tier modifier");
		context.assertTrue(Math.abs(zombie.getAttributeValue(EntityAttributes.GENERIC_SCALE) - (1 + Mutations.BRUTE_SCALE_BONUS)) < 1e-6,
				"Brute scale missing after load");
		context.assertTrue(zombie.getHealth() == zombie.getMaxHealth(), "loaded mutant is hurt: " + zombie.getHealth());
		zombie.discard();
		context.complete();
	}

	@GameTest(templateName = EMPTY_STRUCTURE)
	public void commandsMutateAndClear(TestContext context) {
		ZombieEntity zombie = context.spawnMob(EntityType.ZOMBIE, 1, 2, 1);
		zombie.setHealth(10);
		var server = context.getWorld().getServer();
		var source = server.getCommandSource().withSilent();

		server.getCommandManager().executeWithPrefix(source, "evoluta mutate " + zombie.getUuidAsString() + " apex ignited brute");
		MutationData data = Mutations.get(zombie);
		context.assertTrue(MutationData.of(Tier.APEX, Element.IGNITED, Archetype.BRUTE).equals(data), "command gave " + data);
		context.assertTrue(Math.abs(zombie.getHealth() / zombie.getMaxHealth() - 0.5F) < 1e-4, "mutating changed the share of health");

		server.getCommandManager().executeWithPrefix(source, "evoluta mutate " + zombie.getUuidAsString() + " elite abyssal");
		context.assertTrue(Mutations.get(zombie).getTier() == Tier.APEX, "a reserved element was accepted");

		try {
			int worlds = server.getCommandManager().getDispatcher().execute("evoluta stats", source);
			context.assertTrue(worlds >= 1, "/evoluta stats reported no world while a mutant is loaded");
		} catch (com.mojang.brigadier.exceptions.CommandSyntaxException e) {
			throw new AssertionError("/evoluta stats failed: " + e.getMessage(), e);
		}

		server.getCommandManager().executeWithPrefix(source, "evoluta clear " + zombie.getUuidAsString());
		context.assertFalse(zombie.hasAttached(EvolutaAttachments.MUTATION), "clear left the attachment");
		context.assertTrue(zombie.getMaxHealth() == 20, "clear left max health at " + zombie.getMaxHealth());
		context.assertTrue(Math.abs(zombie.getAttributeValue(EntityAttributes.GENERIC_SCALE) - 1.0) < 1e-6, "clear left the Brute scale");
		context.assertTrue(Math.abs(zombie.getHealth() - 10) < 1e-3, "clear changed the share of health: " + zombie.getHealth());
		zombie.discard();
		context.complete();
	}

	@GameTest(templateName = EMPTY_STRUCTURE)
	public void commandsGraftHeldGearAndMutatedChampionsTheirOwn(TestContext context) {
		var server = context.getWorld().getServer();
		var source = server.getCommandSource().withSilent();
		ZombieEntity holder = context.spawnMob(EntityType.ZOMBIE, 1, 2, 1);
		holder.setStackInHand(Hand.MAIN_HAND, new ItemStack(Items.IRON_SWORD));
		server.getCommandManager().executeWithPrefix(source, "evoluta graft " + holder.getUuidAsString() + " stray permafrost apex 1");
		server.getCommandManager().executeWithPrefix(source, "evoluta graft " + holder.getUuidAsString() + " husk ignited elite");
		server.getCommandManager().executeWithPrefix(source, "evoluta graft " + holder.getUuidAsString() + " wither ignited elite");
		server.getCommandManager().executeWithPrefix(source, "evoluta graft " + holder.getUuidAsString() + " husk abyssal elite");
		List<Graft> grafts = Grafting.grafts(holder.getMainHandStack());
		context.assertTrue(grafts.size() == 2, "expected the two good grafts, got " + grafts);
		context.assertTrue(grafts.get(0).equals(new Graft(SoulKind.STRAY, Element.PERMAFROST, Tier.APEX, 1.0F)), "the first graft is " + grafts.get(0));
		Graft rolled = grafts.get(1);
		context.assertTrue(rolled.kind() == SoulKind.HUSK && rolled.element() == Element.IGNITED && rolled.grade() == Tier.ELITE
				&& rolled.power() >= Graft.minPower(Tier.ELITE) && rolled.power() <= Graft.maxPower(Tier.ELITE), "the rolled graft is " + rolled);

		ZombieEntity stick = context.spawnMob(EntityType.ZOMBIE, 3, 2, 1);
		stick.setStackInHand(Hand.MAIN_HAND, new ItemStack(Items.STICK));
		server.getCommandManager().executeWithPrefix(source, "evoluta graft " + stick.getUuidAsString() + " zombie toxic apex");
		context.assertFalse(stick.getMainHandStack().contains(SoulComponents.GRAFTS), "a stick took a graft");

		ZombieEntity champion = context.spawnMob(EntityType.ZOMBIE, 5, 2, 1);
		champion.setStackInHand(Hand.MAIN_HAND, new ItemStack(Items.IRON_SWORD));
		server.getCommandManager().executeWithPrefix(source, "evoluta mutate " + champion.getUuidAsString() + " elite toxic");
		List<Graft> gear = Grafting.grafts(champion.getMainHandStack());
		context.assertTrue(gear.size() == 1 && gear.get(0).kind() == SoulKind.ZOMBIE && gear.get(0).element() == Element.TOXIC
				&& gear.get(0).grade() == Tier.ELITE, "a champion made by command fights with " + gear);
		holder.discard();
		stick.discard();
		champion.discard();
		context.complete();
	}

	private static <T extends MobEntity> T initialize(TestContext context, EntityType<T> type, SpawnReason reason) {
		ServerWorld world = context.getWorld();
		T mob = type.create(world);
		BlockPos pos = context.getAbsolutePos(new BlockPos(1, 2, 1));
		mob.refreshPositionAndAngles(pos, 0, 0);
		mob.initialize(world, world.getLocalDifficulty(pos), reason, null);
		return mob;
	}
}
