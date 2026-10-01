package com.nyr.evoluta.common.combat;

import com.nyr.evoluta.common.mutation.Element;
import com.nyr.evoluta.common.mutation.MutationData;
import com.nyr.evoluta.common.mutation.Tier;
import java.util.ArrayList;
import java.util.List;
import net.minecraft.block.BlockState;
import net.minecraft.block.Blocks;
import net.minecraft.entity.Entity;
import net.minecraft.entity.LivingEntity;
import net.minecraft.entity.mob.MobEntity;
import net.minecraft.entity.player.PlayerEntity;
import net.minecraft.server.world.ServerWorld;
import net.minecraft.sound.SoundCategory;
import net.minecraft.sound.SoundEvents;
import net.minecraft.state.property.Properties;
import net.minecraft.util.Util;
import net.minecraft.util.math.BlockPos;
import net.minecraft.util.math.Box;
import net.minecraft.util.math.Direction;
import net.minecraft.util.math.MathHelper;
import net.minecraft.util.math.random.Random;
import org.jetbrains.annotations.Nullable;

/**
 * What elemental mutants leave behind, as blocks you can see and not a haze you might take for decoration: real fire
 * ({@link MutantFireBlock}), jagged frost spikes ({@link FrostSpikesBlock}) and toxic blight ({@link BlightBlock}) on
 * the ground where they hunt and die, and a crater's worth where a mutant creeper blows up.
 * Every block stands a few seconds only: {@link HazardBlocks} puts back exactly what was there, saves them with the
 * world and lets nobody take them.
 */
public final class Hazards {
	/** How long a mutant creeper's blast hazard lasts, by tier (Evolved, Elite, Apex); each block adds up to a second. */
	public static final int[] BLAST_HAZARD_TICKS = {120, 160, 200};
	/** A hunting mutant's trail: a block every 20 ticks while it chases, each standing 3 seconds. */
	public static final int TRAIL_TICKS = 60;
	/** What a mutant leaves where it dies stands 5 seconds, each block up to a second more. */
	public static final int DEATH_TICKS = 100;
	/** Soul fire from an Apex Ignited graft's kill stands 3 seconds, each block up to half a second more. */
	public static final int SOUL_FIRE_TICKS = 60;
	/** Apex chill may push a player past vanilla's freeze-damage threshold; lesser tiers stop just short of it. */
	public static final int APEX_FREEZE_CAP = 180;

	private Hazards() {
	}

	/** The block an element leaves on the ground at a mutant's tier, or null for an element that leaves nothing. */
	@Nullable
	public static BlockState groundHazard(Element element, Tier tier) {
		return switch (element) {
			case IGNITED -> HazardBlockTypes.MUTANT_FIRE.getDefaultState();
			case PERMAFROST -> FrostSpikesBlock.of(tier);
			case TOXIC -> BlightBlock.of(tier);
			default -> null;
		};
	}

	/**
	 * A mutant died: its element's blocks cover the ground around it, 1.5, 2 or 2.5 blocks out by tier. Fire and blight
	 * carpet it; frost spikes stand apart, about every other block.
	 */
	public static void onDeathBurst(ServerWorld world, MobEntity mob, MutationData data) {
		BlockState hazard = groundHazard(data.getElement(), data.getTier());
		if (hazard == null) {
			return;
		}
		double radius = 1.0 + 0.5 * data.getTier().id();
		Random random = world.getRandom();
		boolean sparse = data.getElement() == Element.PERMAFROST;
		HazardBlocks hazards = HazardBlocks.of(world);
		for (BlockPos pos : ground(world, mob.getBlockPos(), MathHelper.ceil(radius))) {
			if (within(pos, mob, radius) && (!sparse || random.nextBoolean())) {
				hazards.place(world, pos, hazard, DEATH_TICKS + random.nextInt(20));
			}
		}
	}

	/** While a mutant chases on foot, a block of its element where it stands: fire, frost spikes or blight. */
	public static void leaveTrail(ServerWorld world, MobEntity mob, MutationData data) {
		if (mob.getTarget() == null || !mob.isOnGround() || mob.isTouchingWater() || mob.getVelocity().horizontalLengthSquared() < 1.0E-4) {
			return;
		}
		BlockState hazard = groundHazard(data.getElement(), data.getTier());
		BlockPos at = mob.getBlockPos();
		if (hazard != null && standsOn(world, at)) {
			HazardBlocks.of(world).place(world, at, hazard, TRAIL_TICKS);
		}
	}

	/**
	 * Soul fire where an Apex Ignited graft's kill fell, 1.5 blocks out plus {@code strength} (the graft's power times
	 * its stacking weight). Blue, so nobody takes it for a mutant's fire, and it burns monsters only.
	 */
	public static void kindleSoulFire(ServerWorld world, Entity at, float strength) {
		double radius = 1.5 + strength;
		Random random = world.getRandom();
		HazardBlocks hazards = HazardBlocks.of(world);
		for (BlockPos pos : ground(world, at.getBlockPos(), MathHelper.ceil(radius))) {
			if (within(pos, at, radius)) {
				hazards.place(world, pos, HazardBlockTypes.SOUL_FIRE.getDefaultState(), SOUL_FIRE_TICKS + random.nextInt(10));
			}
		}
	}

	/**
	 * What a mutant creeper's blast leaves for a few seconds, every block put back afterwards. Ignited: the crater
	 * floor turns to magma with fire licking over most of it. Toxic: the floor turns to slime goop that drags at your
	 * feet, crusted with blight. Permafrost: ice pillars erupt round the crater, hurting, freezing and throwing up
	 * whoever stands by them, and frost spikes cover the rest; Elite and Apex blasts fill half of it with powder snow.
	 */
	public static void blastHazards(ServerWorld world, MobEntity mob, MutationData data, float power, HazardBlocks hazards) {
		Tier tier = data.getTier();
		int ticks = BLAST_HAZARD_TICKS[tier.id() - 1];
		int radius = MathHelper.ceil(power) + 1;
		List<BlockPos> floor = floor(world, mob.getBlockPos(), radius);
		Random random = world.getRandom();
		switch (data.getElement()) {
			case IGNITED -> {
				for (BlockPos pos : floor) {
					hazards.place(world, pos, Blocks.MAGMA_BLOCK.getDefaultState(), ticks + random.nextInt(20));
					if (random.nextFloat() < 0.6F) {
						hazards.place(world, pos.up(), HazardBlockTypes.MUTANT_FIRE.getDefaultState(), ticks + random.nextInt(20));
					}
				}
			}
			case TOXIC -> {
				for (BlockPos pos : floor) {
					hazards.place(world, pos, Blocks.SLIME_BLOCK.getDefaultState(), ticks + random.nextInt(20));
					hazards.place(world, pos.up(), BlightBlock.of(tier), ticks + random.nextInt(20));
				}
			}
			case PERMAFROST -> {
				raiseIceSpikes(world, mob, tier, floor, ticks, hazards, random);
				for (BlockPos pos : floor) {
					BlockState cover = tier.isChampion() && random.nextBoolean() ? Blocks.POWDER_SNOW.getDefaultState() : FrostSpikesBlock.of(tier);
					hazards.place(world, pos.up(), cover, ticks + random.nextInt(20));
				}
			}
			default -> {
			}
		}
	}

	/** The top of the ground in each column within {@code radius}: a full-topped block with air above it. */
	static List<BlockPos> floor(ServerWorld world, BlockPos center, int radius) {
		List<BlockPos> floor = new ArrayList<>();
		BlockPos.Mutable pos = new BlockPos.Mutable();
		for (int dx = -radius; dx <= radius; dx++) {
			for (int dz = -radius; dz <= radius; dz++) {
				if (dx * dx + dz * dz > radius * radius) {
					continue;
				}
				for (int dy = 1; dy >= -radius - 1; dy--) {
					pos.set(center.getX() + dx, center.getY() + dy, center.getZ() + dz);
					BlockState state = world.getBlockState(pos);
					if (!state.isAir() && state.isSideSolidFullSquare(world, pos, Direction.UP)) {
						if (world.getBlockState(pos.up()).isAir()) {
							floor.add(pos.toImmutable());
						}
						break;
					}
				}
			}
		}
		return floor;
	}

	/**
	 * Where a hazard can stand in each column within {@code radius}: the space just above a full-topped block, empty
	 * or holding only plants (which come back when the hazard goes).
	 */
	static List<BlockPos> ground(ServerWorld world, BlockPos center, int radius) {
		List<BlockPos> ground = new ArrayList<>();
		BlockPos.Mutable pos = new BlockPos.Mutable();
		for (int dx = -radius; dx <= radius; dx++) {
			for (int dz = -radius; dz <= radius; dz++) {
				if (dx * dx + dz * dz > radius * radius) {
					continue;
				}
				for (int dy = 1; dy >= -radius - 1; dy--) {
					pos.set(center.getX() + dx, center.getY() + dy, center.getZ() + dz);
					if (standsOn(world, pos)) {
						ground.add(pos.toImmutable());
						break;
					}
					if (!isOpen(world.getBlockState(pos))) {
						break;
					}
				}
			}
		}
		return ground;
	}

	/** Whether a hazard can stand at {@code pos}: open space over a full-topped block. */
	private static boolean standsOn(ServerWorld world, BlockPos pos) {
		BlockPos below = pos.down();
		return isOpen(world.getBlockState(pos)) && world.getBlockState(below).isSideSolidFullSquare(world, below, Direction.UP);
	}

	/** Air or a plant with no water in it, and not half of a tall one (its other half would fall). */
	private static boolean isOpen(BlockState state) {
		return (state.isAir() || state.isReplaceable()) && state.getFluidState().isEmpty() && !state.contains(Properties.DOUBLE_BLOCK_HALF);
	}

	private static boolean within(BlockPos pos, Entity center, double radius) {
		double dx = pos.getX() + 0.5 - center.getX();
		double dz = pos.getZ() + 0.5 - center.getZ();
		return dx * dx + dz * dz <= radius * radius;
	}

	/** Spikes of packed ice, one to three blocks tall, round the crater; each erupts under whoever stands there. */
	private static void raiseIceSpikes(ServerWorld world, MobEntity mob, Tier tier, List<BlockPos> floor, int ticks, HazardBlocks hazards,
			Random random) {
		List<BlockPos> ring = new ArrayList<>();
		for (BlockPos pos : floor) {
			double dx = pos.getX() + 0.5 - mob.getX();
			double dz = pos.getZ() + 0.5 - mob.getZ();
			if (dx * dx + dz * dz >= 2.25) {
				ring.add(pos);
			}
		}
		Util.shuffle(ring, random);
		int spikes = Math.min(ring.size(), 3 + 2 * tier.id());
		float damage = 2.0F + 1.5F * tier.id();
		for (int i = 0; i < spikes; i++) {
			BlockPos base = ring.get(i).up();
			for (LivingEntity victim : world.getEntitiesByClass(LivingEntity.class, new Box(base).expand(0.6, 0.5, 0.6), e -> e != mob && e.isAlive())) {
				victim.damage(world.getDamageSources().freeze(), damage);
				freeze(victim, 80, tier);
				victim.addVelocity(0.0, 0.45, 0.0);
				victim.velocityModified = true;
			}
			int height = 1 + random.nextInt(tier == Tier.APEX ? 3 : 2);
			for (int h = 0; h < height; h++) {
				BlockPos at = base.up(h);
				// only up through open air, never through the ground or a ceiling (what grows on top would fall), and never
				// inside a body: the eruption throws it up instead
				if (!isOpen(world.getBlockState(at)) || !world.getEntitiesByClass(LivingEntity.class, new Box(at), LivingEntity::isAlive).isEmpty()
						|| !hazards.place(world, at, Blocks.PACKED_ICE.getDefaultState(), ticks + random.nextInt(20))) {
					break;
				}
			}
			world.playSound(null, base, SoundEvents.BLOCK_GLASS_BREAK, SoundCategory.HOSTILE, 0.7F, 1.3F + random.nextFloat() * 0.4F);
		}
	}

	/** Adds frozen ticks, never beyond what the tier allows. A target that cannot freeze (leather armour) is spared. */
	public static void freeze(LivingEntity target, int ticks, Tier tier) {
		if (!target.canFreeze()) {
			return;
		}
		int cap = tier == Tier.APEX ? APEX_FREEZE_CAP : target.getMinFreezeDamageTicks() - 1;
		int frozen = target.getFrozenTicks();
		if (frozen < cap) {
			target.setFrozenTicks(Math.min(cap, frozen + ticks));
		}
	}

	/** Players tactics and hazards act on: alive, and neither spectating nor in creative. */
	public static boolean isVulnerablePlayer(PlayerEntity player) {
		return player.isAlive() && !player.isSpectator() && !player.isCreative();
	}
}
