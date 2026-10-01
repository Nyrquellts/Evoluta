package com.nyr.evoluta.gametest;

import com.nyr.evoluta.common.combat.BlightBlock;
import com.nyr.evoluta.common.combat.HazardBlockTypes;
import com.nyr.evoluta.common.mutation.Archetype;
import com.nyr.evoluta.common.mutation.Element;
import com.nyr.evoluta.common.mutation.Tier;
import com.nyr.evoluta.common.tactics.Tactics;
import net.fabricmc.fabric.api.gametest.v1.FabricGameTest;
import net.minecraft.block.BlockState;
import net.minecraft.entity.EntityType;
import net.minecraft.entity.EquipmentSlot;
import net.minecraft.entity.effect.StatusEffectInstance;
import net.minecraft.entity.effect.StatusEffects;
import net.minecraft.entity.mob.CreeperEntity;
import net.minecraft.entity.mob.SkeletonEntity;
import net.minecraft.entity.mob.ZombieEntity;
import net.minecraft.entity.player.PlayerEntity;
import net.minecraft.entity.projectile.ArrowEntity;
import net.minecraft.item.ItemStack;
import net.minecraft.item.Items;
import net.minecraft.test.GameTest;
import net.minecraft.test.TestContext;
import net.minecraft.util.Hand;
import net.minecraft.util.math.BlockPos;

/** Step 3's event-driven combat: hits, blocks and deaths, each against the vanilla code path it hooks. */
public final class CombatGameTest implements FabricGameTest {
	@GameTest(templateName = EMPTY_STRUCTURE)
	public void ignitedHitsSetTheTargetAlight(TestContext context) {
		ZombieEntity ignited = Mutants.still(context, EntityType.ZOMBIE, 1, 1, Tier.ELITE, Element.IGNITED, Archetype.NONE);
		PlayerEntity player = Mutants.target(context, 1, 2, 180);
		context.assertTrue(ignited.tryAttack(player), "the hit did not land");
		context.assertTrue(player.getFireTicks() == 80, "an Elite Ignited hit should burn 4 s (80 ticks), burns " + player.getFireTicks());

		ZombieEntity plain = context.spawnMob(EntityType.ZOMBIE, 5, 2, 1);
		PlayerEntity other = Mutants.target(context, 5, 2, 180);
		plain.tryAttack(other);
		context.assertFalse(other.isOnFire(), "an ordinary zombie set its target alight");
		context.complete();
	}

	@GameTest(templateName = EMPTY_STRUCTURE)
	public void permafrostHitsFreezeAndNumbUnlessWearingLeather(TestContext context) {
		ZombieEntity frost = Mutants.still(context, EntityType.ZOMBIE, 1, 1, Tier.ELITE, Element.PERMAFROST, Archetype.NONE);
		PlayerEntity bare = Mutants.target(context, 1, 2, 180);
		frost.tryAttack(bare);
		context.assertTrue(bare.getFrozenTicks() == 90, "an Elite Permafrost hit should add 90 frozen ticks, added " + bare.getFrozenTicks());
		context.assertTrue(bare.hasStatusEffect(StatusEffects.MINING_FATIGUE), "no Mining Fatigue: attack speed untouched");

		PlayerEntity leather = Mutants.target(context, 1, 2, 180);
		leather.equipStack(EquipmentSlot.FEET, new ItemStack(Items.LEATHER_BOOTS));
		frost.tryAttack(leather);
		context.assertTrue(leather.getFrozenTicks() == 0, "leather boots should stop the freeze, got " + leather.getFrozenTicks());
		context.assertTrue(leather.hasStatusEffect(StatusEffects.MINING_FATIGUE), "leather should not stop the numbing");
		context.complete();
	}

	@GameTest(templateName = EMPTY_STRUCTURE)
	public void permafrostMutantsDoNotFreeze(TestContext context) {
		ZombieEntity frost = Mutants.still(context, EntityType.ZOMBIE, 1, 1, Tier.EVOLVED, Element.PERMAFROST, Archetype.NONE);
		float health = frost.getHealth();
		context.assertFalse(frost.damage(context.getWorld().getDamageSources().freeze(), 5), "freezing damage landed on a Permafrost mutant");
		context.assertTrue(frost.getHealth() == health, "Permafrost mutant lost health to freezing");

		ZombieEntity plain = context.spawnMob(EntityType.ZOMBIE, 5, 2, 1);
		plain.damage(context.getWorld().getDamageSources().freeze(), 5);
		context.assertTrue(plain.getHealth() < plain.getMaxHealth(), "an ordinary zombie should take freezing damage");
		context.complete();
	}

	@GameTest(templateName = EMPTY_STRUCTURE)
	public void bruteHitsLockARaisedShield(TestContext context) {
		PlayerEntity player = Mutants.target(context, 1, 1, 0);
		player.setStackInHand(Hand.OFF_HAND, new ItemStack(Items.SHIELD));
		player.setCurrentHand(Hand.OFF_HAND);
		Mutants.holdUp(player, 10);
		context.assertTrue(player.isBlocking(), "setup: the shield is not raised");

		// yaw 0 faces +z: the Brute stands in front of the shield
		ZombieEntity brute = Mutants.still(context, EntityType.ZOMBIE, 1, 2, Tier.EVOLVED, Element.NONE, Archetype.BRUTE);
		brute.tryAttack(player);
		context.assertTrue(player.getItemCooldownManager().isCoolingDown(Items.SHIELD), "the Brute's blocked hit left the shield usable");
		context.assertFalse(player.isBlocking(), "the shield is still raised after a Brute hit");

		PlayerEntity other = Mutants.target(context, 5, 1, 0);
		other.setStackInHand(Hand.OFF_HAND, new ItemStack(Items.SHIELD));
		other.setCurrentHand(Hand.OFF_HAND);
		Mutants.holdUp(other, 10);
		ZombieEntity plain = context.spawnMob(EntityType.ZOMBIE, 5, 2, 2);
		plain.setAiDisabled(true);
		plain.tryAttack(other);
		context.assertFalse(other.getItemCooldownManager().isCoolingDown(Items.SHIELD), "an ordinary zombie locked a shield");
		context.assertTrue(other.isBlocking(), "an ordinary zombie's blocked hit lowered the shield");
		context.complete();
	}

	@GameTest(templateName = EMPTY_STRUCTURE)
	public void stalkerArrowsMarkTheTargetAndCallTheHunt(TestContext context) {
		SkeletonEntity stalker = Mutants.still(context, EntityType.SKELETON, 1, 1, Tier.ELITE, Element.NONE, Archetype.STALKER);
		ZombieEntity idleMutant = Mutants.still(context, EntityType.ZOMBIE, 5, 1, Tier.EVOLVED, Element.NONE, Archetype.NONE);
		ZombieEntity ordinary = context.spawnMob(EntityType.ZOMBIE, 5, 2, 4);
		ordinary.setAiDisabled(true);
		PlayerEntity player = Mutants.target(context, 1, 6, 0);

		ArrowEntity arrow = new ArrowEntity(context.getWorld(), stalker, new ItemStack(Items.ARROW), null);
		context.assertTrue(player.damage(context.getWorld().getDamageSources().arrow(arrow, stalker), 2.0F), "the arrow did not land");

		context.assertTrue(player.hasStatusEffect(StatusEffects.GLOWING), "a Stalker arrow did not mark its target");
		context.assertTrue(idleMutant.getTarget() == player, "an idle mutant nearby was not sent after the marked player");
		context.assertTrue(ordinary.getTarget() == null, "an ordinary mob answered the hunt call");
		context.complete();
	}

	/** An Elite Toxic death crusts the ground with Elite blight, 2 blocks out: a disc of 13 blocks on open ground. */
	@GameTest(templateName = EMPTY_STRUCTURE)
	public void toxicDeathLeavesBlight(TestContext context) {
		Mutants.floor(context);
		ZombieEntity toxic = Mutants.still(context, EntityType.ZOMBIE, 3, 3, Tier.ELITE, Element.TOXIC, Archetype.NONE);
		toxic.kill();
		int blight = 0;
		for (int x = 0; x < 8; x++) {
			for (int z = 0; z < 8; z++) {
				BlockState state = context.getBlockState(new BlockPos(x, 2, z));
				if (state.isOf(HazardBlockTypes.BLIGHT)) {
					blight++;
					context.assertTrue(state.get(BlightBlock.TIER) == Tier.ELITE.id(), "blight of tier " + state.get(BlightBlock.TIER));
				}
			}
		}
		context.assertTrue(blight == 13, "an Elite Toxic death left " + blight + " blight blocks");
		context.complete();
	}

	/** An Evolved Ignited death sets the ground burning 1.5 blocks out: the 9 blocks round where it fell. */
	@GameTest(templateName = EMPTY_STRUCTURE)
	public void ignitedDeathSetsFire(TestContext context) {
		Mutants.floor(context);
		ZombieEntity ignited = Mutants.still(context, EntityType.ZOMBIE, 3, 3, Tier.EVOLVED, Element.IGNITED, Archetype.NONE);
		ignited.kill();
		int fire = 0;
		for (int x = 0; x < 8; x++) {
			for (int z = 0; z < 8; z++) {
				fire += context.getBlockState(new BlockPos(x, 2, z)).isOf(HazardBlockTypes.MUTANT_FIRE) ? 1 : 0;
			}
		}
		context.assertTrue(fire == 9, "an Evolved Ignited death lit " + fire + " fire blocks");
		context.complete();
	}

	@GameTest(templateName = EMPTY_STRUCTURE)
	public void toxicMutantsCannotBePoisoned(TestContext context) {
		CreeperEntity toxic = Mutants.still(context, EntityType.CREEPER, 1, 1, Tier.EVOLVED, Element.TOXIC, Archetype.NONE);
		CreeperEntity plain = context.spawnMob(EntityType.CREEPER, 5, 2, 1);
		plain.setAiDisabled(true);
		context.assertFalse(toxic.addStatusEffect(new StatusEffectInstance(StatusEffects.POISON, 100)), "a Toxic creeper was poisoned");
		context.assertTrue(plain.addStatusEffect(new StatusEffectInstance(StatusEffects.POISON, 100)), "an ordinary creeper could not be poisoned");
		toxic.discard();
		plain.discard();
		context.complete();
	}

	@GameTest(templateName = EMPTY_STRUCTURE)
	public void onlyStalkersMoveSilently(TestContext context) {
		ZombieEntity stalker = Mutants.still(context, EntityType.ZOMBIE, 1, 1, Tier.EVOLVED, Element.NONE, Archetype.STALKER);
		ZombieEntity brute = Mutants.still(context, EntityType.ZOMBIE, 3, 1, Tier.EVOLVED, Element.NONE, Archetype.BRUTE);
		ZombieEntity plain = context.spawnMob(EntityType.ZOMBIE, 5, 2, 1);
		context.assertTrue(Tactics.movesSilently(stalker), "a Stalker makes step sounds");
		context.assertFalse(Tactics.movesSilently(brute), "a Brute is silent");
		context.assertFalse(Tactics.movesSilently(plain), "an ordinary zombie is silent");
		context.complete();
	}

	@GameTest(templateName = EMPTY_STRUCTURE)
	public void behindMeansOutsideTheFacingHalf(TestContext context) {
		PlayerEntity south = Mutants.target(context, 4, 4, 0);
		ZombieEntity inFront = context.spawnMob(EntityType.ZOMBIE, 4, 2, 7);
		ZombieEntity behind = context.spawnMob(EntityType.ZOMBIE, 4, 2, 1);
		context.assertFalse(Tactics.isBehind(south, inFront), "yaw 0 faces +z: a mob at +z is in front");
		context.assertTrue(Tactics.isBehind(south, behind), "yaw 0 faces +z: a mob at -z is behind");
		PlayerEntity north = Mutants.target(context, 4, 4, 180);
		context.assertTrue(Tactics.isBehind(north, inFront), "yaw 180 faces -z: a mob at +z is behind");
		context.assertFalse(Tactics.isBehind(north, behind), "yaw 180 faces -z: a mob at -z is in front");
		context.complete();
	}
}
