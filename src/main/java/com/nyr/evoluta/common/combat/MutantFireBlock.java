package com.nyr.evoluta.common.combat;

import com.mojang.serialization.MapCodec;
import com.nyr.evoluta.common.mutation.Element;
import com.nyr.evoluta.common.mutation.MutationData;
import com.nyr.evoluta.common.mutation.Mutations;
import net.minecraft.block.AbstractFireBlock;
import net.minecraft.block.BlockState;
import net.minecraft.block.Blocks;
import net.minecraft.entity.Entity;
import net.minecraft.entity.LivingEntity;
import net.minecraft.entity.mob.Monster;
import net.minecraft.util.math.BlockPos;
import net.minecraft.util.math.Direction;
import net.minecraft.world.World;
import net.minecraft.world.WorldAccess;
import net.minecraft.world.WorldView;

/**
 * Real fire an elemental mutant leaves on the ground: vanilla's fire models and burning, but it never spreads, never
 * burns a block and never goes out on its own; {@link HazardBlocks} takes it away after a few seconds. Drops nothing.
 * {@link HazardBlockTypes#MUTANT_FIRE} burns whatever walks in but Ignited mutants; {@link HazardBlockTypes#SOUL_FIRE},
 * what a player's Apex Ignited graft leaves, burns monsters only.
 */
public final class MutantFireBlock extends AbstractFireBlock {
	public static final MapCodec<MutantFireBlock> CODEC = createCodec(settings -> new MutantFireBlock(settings, false));
	public static final MapCodec<MutantFireBlock> SOUL_CODEC = createCodec(settings -> new MutantFireBlock(settings, true));

	private final boolean soul;

	public MutantFireBlock(Settings settings, boolean soul) {
		super(settings, soul ? 2.0F : 1.0F);
		this.soul = soul;
	}

	@Override
	protected MapCodec<MutantFireBlock> getCodec() {
		return this.soul ? SOUL_CODEC : CODEC;
	}

	@Override
	protected boolean isFlammable(BlockState state) {
		return false;
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

	/** Only the living burn: the loot and experience a mutant drops into its own death fire are left alone. */
	@Override
	protected void onEntityCollision(BlockState state, World world, BlockPos pos, Entity entity) {
		if (entity instanceof LivingEntity && (this.soul ? entity instanceof Monster : !isIgnited(entity))) {
			super.onEntityCollision(state, world, pos, entity);
		}
	}

	private static boolean isIgnited(Entity entity) {
		MutationData data = Mutations.get(entity);
		return data != null && data.getElement() == Element.IGNITED;
	}
}
