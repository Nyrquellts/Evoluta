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
 * Jagged ice spikes a Permafrost mutant raises from the ground, built from vanilla's packed and blue ice. Nothing
 * stops you walking into them, but they drag at you and, while you push through, cut you (freezing damage, by tier)
 * and freeze you. Permafrost mutants pass unharmed. {@link HazardBlocks} takes them away after a few seconds.
 */
public final class FrostSpikesBlock extends Block {
	public static final MapCodec<FrostSpikesBlock> CODEC = createCodec(FrostSpikesBlock::new);
	/** The tier of the mutant that raised them (1 Evolved, 2 Elite, 3 Apex). */
	public static final IntProperty TIER = IntProperty.of("tier", 1, 3);
	private static final VoxelShape SHAPE = Block.createCuboidShape(2.0, 0.0, 2.0, 14.0, 14.0, 14.0);

	public FrostSpikesBlock(Settings settings) {
		super(settings);
		this.setDefaultState(this.getDefaultState().with(TIER, 1));
	}

	@Override
	protected MapCodec<FrostSpikesBlock> getCodec() {
		return CODEC;
	}

	@Override
	protected void appendProperties(StateManager.Builder<Block, BlockState> builder) {
		builder.add(TIER);
	}

	public static BlockState of(Tier tier) {
		return HazardBlockTypes.FROST_SPIKES.getDefaultState().with(TIER, (int) tier.id());
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

	/** Sweet-berry-bush rules: always slows, cuts only an entity that is moving through. */
	@Override
	protected void onEntityCollision(BlockState state, World world, BlockPos pos, Entity entity) {
		if (!(entity instanceof LivingEntity living) || isPermafrost(living)) {
			return;
		}
		entity.slowMovement(state, new Vec3d(0.6, 0.75, 0.6));
		if (world.isClient) {
			return;
		}
		double dx = Math.abs(entity.getX() - entity.lastRenderX);
		double dz = Math.abs(entity.getZ() - entity.lastRenderZ);
		if (dx >= 0.003 || dz >= 0.003) {
			int tier = state.get(TIER);
			if (living.damage(world.getDamageSources().freeze(), 1.0F + tier)) {
				Hazards.freeze(living, 40, Tier.byId(tier));
			}
		}
	}

	@Override
	public void randomDisplayTick(BlockState state, World world, BlockPos pos, Random random) {
		if (random.nextInt(3) == 0) {
			world.addParticle(ParticleTypes.SNOWFLAKE, pos.getX() + random.nextDouble(), pos.getY() + 0.3 + random.nextDouble() * 0.6,
					pos.getZ() + random.nextDouble(), 0.0, 0.01, 0.0);
		}
	}

	private static boolean isPermafrost(Entity entity) {
		MutationData data = Mutations.get(entity);
		return data != null && data.getElement() == Element.PERMAFROST;
	}
}
