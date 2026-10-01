package com.nyr.evoluta.common.combat;

import com.mojang.serialization.MapCodec;
import com.nyr.evoluta.common.mutation.Element;
import com.nyr.evoluta.common.mutation.MutationData;
import com.nyr.evoluta.common.mutation.Mutations;
import com.nyr.evoluta.common.mutation.Tier;
import net.minecraft.block.Block;
import net.minecraft.block.BlockState;
import net.minecraft.block.Blocks;
import net.minecraft.block.ShapeContext;
import net.minecraft.entity.Entity;
import net.minecraft.entity.LivingEntity;
import net.minecraft.entity.effect.StatusEffectInstance;
import net.minecraft.entity.effect.StatusEffects;
import net.minecraft.particle.ParticleTypes;
import net.minecraft.state.StateManager;
import net.minecraft.state.property.IntProperty;
import net.minecraft.util.math.BlockPos;
import net.minecraft.util.math.Direction;
import net.minecraft.util.math.Vec3d;
import net.minecraft.util.math.random.Random;
import net.minecraft.util.shape.VoxelShape;
import net.minecraft.world.BlockView;
import net.minecraft.world.World;
import net.minecraft.world.WorldAccess;
import net.minecraft.world.WorldView;

/**
 * Toxic blight on the ground: a glowing, blistered crust kitbashed from vanilla's sculk, tinted poison green. It drags
 * at whatever wades through it and poisons it (Poison I, Poison II from an Apex). Toxic mutants pass unharmed.
 * {@link HazardBlocks} takes it away after a few seconds.
 */
public final class BlightBlock extends Block {
	public static final MapCodec<BlightBlock> CODEC = createCodec(BlightBlock::new);
	/** The tier of the mutant that left it (1 Evolved, 2 Elite, 3 Apex). */
	public static final IntProperty TIER = IntProperty.of("tier", 1, 3);
	private static final VoxelShape SHAPE = Block.createCuboidShape(0.0, 0.0, 0.0, 16.0, 2.0, 16.0);
	/** Poison is topped up to this many ticks, and only once it has run down below half of it. */
	private static final int POISON_TICKS = 60;

	public BlightBlock(Settings settings) {
		super(settings);
		this.setDefaultState(this.getDefaultState().with(TIER, 1));
	}

	@Override
	protected MapCodec<BlightBlock> getCodec() {
		return CODEC;
	}

	@Override
	protected void appendProperties(StateManager.Builder<Block, BlockState> builder) {
		builder.add(TIER);
	}

	public static BlockState of(Tier tier) {
		return HazardBlockTypes.BLIGHT.getDefaultState().with(TIER, (int) tier.id());
	}

	@Override
	protected VoxelShape getOutlineShape(BlockState state, BlockView world, BlockPos pos, ShapeContext context) {
		return SHAPE;
	}

	@Override
	protected boolean canPlaceAt(BlockState state, WorldView world, BlockPos pos) {
		BlockPos below = pos.down();
		return world.getBlockState(below).isSideSolidFullSquare(world, below, Direction.UP);
	}

	@Override
	protected BlockState getStateForNeighborUpdate(BlockState state, Direction direction, BlockState neighborState, WorldAccess world, BlockPos pos,
			BlockPos neighborPos) {
		return this.canPlaceAt(state, world, pos) ? state : Blocks.AIR.getDefaultState();
	}

	@Override
	protected void onEntityCollision(BlockState state, World world, BlockPos pos, Entity entity) {
		if (!(entity instanceof LivingEntity living) || isToxic(living)) {
			return;
		}
		entity.slowMovement(state, new Vec3d(0.55, 1.0, 0.55));
		if (world.isClient) {
			return;
		}
		StatusEffectInstance poison = living.getStatusEffect(StatusEffects.POISON);
		if (poison == null || poison.getDuration() < POISON_TICKS / 2) {
			living.addStatusEffect(new StatusEffectInstance(StatusEffects.POISON, POISON_TICKS, state.get(TIER) == Tier.APEX.id() ? 1 : 0));
		}
	}

	@Override
	public void randomDisplayTick(BlockState state, World world, BlockPos pos, Random random) {
		if (random.nextInt(4) == 0) {
			world.addParticle(ParticleTypes.ITEM_SLIME, pos.getX() + random.nextDouble(), pos.getY() + 0.15, pos.getZ() + random.nextDouble(),
					0.0, 0.05, 0.0);
		}
	}

	private static boolean isToxic(Entity entity) {
		MutationData data = Mutations.get(entity);
		return data != null && data.getElement() == Element.TOXIC;
	}
}
