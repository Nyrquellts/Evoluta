package com.nyr.evoluta.common.combat;

import java.util.ArrayList;
import java.util.Comparator;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.PriorityQueue;
import net.fabricmc.fabric.api.event.lifecycle.v1.ServerChunkEvents;
import net.fabricmc.fabric.api.event.player.PlayerBlockBreakEvents;
import net.minecraft.block.Block;
import net.minecraft.block.BlockState;
import net.minecraft.nbt.NbtCompound;
import net.minecraft.nbt.NbtElement;
import net.minecraft.nbt.NbtHelper;
import net.minecraft.nbt.NbtList;
import net.minecraft.registry.RegistryEntryLookup;
import net.minecraft.registry.RegistryKeys;
import net.minecraft.registry.RegistryWrapper;
import net.minecraft.server.world.ServerWorld;
import net.minecraft.state.property.Properties;
import net.minecraft.util.math.BlockPos;
import net.minecraft.util.math.ChunkPos;
import net.minecraft.util.math.ChunkSectionPos;
import net.minecraft.world.PersistentState;
import net.minecraft.world.WorldEvents;
import net.minecraft.world.chunk.WorldChunk;
import org.jetbrains.annotations.Nullable;

/**
 * Blocks a mutant's blast leaves for a while (a magma floor, slime goop, ice spikes, powder snow) and puts back
 * exactly as they were. Saved with the world, so a restart or an unloaded chunk only delays the restoring. Nothing
 * gets them out (no free magma, slime or packed ice): players cannot mine them, explosions pass over them
 * ({@code ExplosionMixin}) and pistons cannot move them ({@code PistonBlockMixin}). They only ever stand in for air or
 * for plain blocks the blast could have broken: never a chest, never bedrock. One queue ordered by expiry: a tick
 * with nothing due costs a peek.
 */
public final class HazardBlocks extends PersistentState {
	static final String ID = "evoluta_hazard_blocks";
	/** Fabric API loads saved data with no data-fix type as it is. */
	static final PersistentState.Type<HazardBlocks> TYPE = new PersistentState.Type<>(HazardBlocks::new, HazardBlocks::fromNbt, null);

	private record Hazard(BlockPos pos, BlockState placed, BlockState original, long until) {
	}

	private final Map<BlockPos, Hazard> byPos = new HashMap<>();
	private final PriorityQueue<Hazard> byTime = new PriorityQueue<>(Comparator.comparingLong(Hazard::until));
	/**
	 * Hazards whose time came while their chunk was unloaded, by chunk. Nothing polls them: they go back when their
	 * chunk loads. (Polled, every hazard left behind in a chunk nobody revisits cost work every few seconds forever.)
	 */
	private final Map<Long, List<Hazard>> waiting = new HashMap<>();

	/**
	 * Players breaking a hazard block get nothing: the break is refused and the block goes back on its own. A chunk
	 * that loads brings back the hazards whose time came while it was away.
	 */
	public static void register() {
		PlayerBlockBreakEvents.BEFORE.register((world, player, pos, state, blockEntity) ->
				!(world instanceof ServerWorld server) || !holds(server, pos));
		ServerChunkEvents.CHUNK_LOAD.register(HazardBlocks::onChunkLoad);
	}

	/** A chunk came back: its hazards whose time came while it was away go back at the end of this tick. */
	private static void onChunkLoad(ServerWorld world, WorldChunk chunk) {
		HazardBlocks hazards = peek(world);
		if (hazards == null || hazards.waiting.isEmpty()) {
			return;
		}
		List<Hazard> due = hazards.waiting.remove(chunk.getPos().toLong());
		if (due != null) {
			long now = world.getTime();
			for (Hazard hazard : due) {
				hazards.add(new Hazard(hazard.pos(), hazard.placed(), hazard.original(), now));
			}
		}
	}

	public static HazardBlocks of(ServerWorld world) {
		return world.getPersistentStateManager().getOrCreate(TYPE, ID);
	}

	@Nullable
	private static HazardBlocks peek(ServerWorld world) {
		return world.getPersistentStateManager().get(TYPE, ID);
	}

	public static boolean holds(ServerWorld world, BlockPos pos) {
		HazardBlocks hazards = peek(world);
		return hazards != null && hazards.holds(pos);
	}

	/** Takes every hazard block out of an explosion's list of blocks to break, before any breaks or is sent to clients. */
	public static void spare(ServerWorld world, List<BlockPos> blocks) {
		HazardBlocks hazards = peek(world);
		if (hazards != null && !hazards.byPos.isEmpty()) {
			blocks.removeIf(hazards::holds);
		}
	}

	/** End of every world tick: restores what is due. */
	public static void tick(ServerWorld world) {
		HazardBlocks hazards = peek(world);
		if (hazards != null && !hazards.byTime.isEmpty()) {
			hazards.restoreDue(world);
		}
	}

	public boolean holds(BlockPos pos) {
		return this.byPos.containsKey(pos);
	}

	/** How many hazard blocks stand in {@code world} right now. */
	public static int count(ServerWorld world) {
		HazardBlocks hazards = peek(world);
		return hazards == null ? 0 : hazards.byPos.size();
	}

	/**
	 * Puts {@code placed} at {@code pos} for {@code ticks}, then restores what was there. Refused (false) when the
	 * spot already holds a hazard or its block may not be replaced.
	 */
	public boolean place(ServerWorld world, BlockPos pos, BlockState placed, int ticks) {
		BlockPos at = pos.toImmutable();
		BlockState original = world.getBlockState(at);
		if (this.byPos.containsKey(at) || !mayReplace(world, at, original)) {
			return false;
		}
		world.setBlockState(at, placed, Block.NOTIFY_ALL);
		this.remember(at, placed, original, world.getTime() + ticks);
		return true;
	}

	/** Notes that {@code placed} stands at {@code pos} until world time {@code until}, then {@code original} goes back. */
	void remember(BlockPos pos, BlockState placed, BlockState original, long until) {
		this.add(new Hazard(pos.toImmutable(), placed, original, until));
	}

	/**
	 * Air and plants, or a plain block no tougher than stone that carries no block entity; never half of a two-block
	 * thing (tall grass, a sunflower, a door), whose other half would fall and never come back.
	 */
	static boolean mayReplace(ServerWorld world, BlockPos pos, BlockState state) {
		if (state.contains(Properties.DOUBLE_BLOCK_HALF)) {
			return false;
		}
		if (state.isAir() || state.isReplaceable()) {
			return state.getFluidState().isEmpty();
		}
		float hardness = state.getHardness(world, pos);
		return !state.hasBlockEntity() && hardness >= 0.0F && hardness <= 3.0F && state.getBlock().getBlastResistance() <= 6.0F;
	}

	private void add(Hazard hazard) {
		this.byPos.put(hazard.pos(), hazard);
		this.byTime.add(hazard);
		this.markDirty();
	}

	private void restoreDue(ServerWorld world) {
		long now = world.getTime();
		while (!this.byTime.isEmpty() && this.byTime.peek().until() <= now) {
			Hazard hazard = this.byTime.poll();
			BlockPos pos = hazard.pos();
			int chunkX = ChunkSectionPos.getSectionCoord(pos.getX());
			int chunkZ = ChunkSectionPos.getSectionCoord(pos.getZ());
			if (!world.isChunkLoaded(chunkX, chunkZ)) {
				// loading a chunk to tidy it would cost more than waiting for a player to bring it back: it goes back then
				this.waiting.computeIfAbsent(ChunkPos.toLong(chunkX, chunkZ), key -> new ArrayList<>()).add(hazard);
				continue;
			}
			this.byPos.remove(pos);
			this.markDirty();
			// something else took the spot (a piston, a player's block): leave it be
			if (world.getBlockState(pos).isOf(hazard.placed().getBlock())) {
				world.setBlockState(pos, hazard.original(), Block.NOTIFY_ALL);
				world.syncWorldEvent(WorldEvents.BLOCK_BROKEN, pos, Block.getRawIdFromState(hazard.placed()));
			}
		}
	}

	@Override
	public NbtCompound writeNbt(NbtCompound nbt, RegistryWrapper.WrapperLookup registries) {
		NbtList list = new NbtList();
		for (Hazard hazard : this.byPos.values()) {
			NbtCompound entry = new NbtCompound();
			entry.putLong("pos", hazard.pos().asLong());
			entry.put("placed", NbtHelper.fromBlockState(hazard.placed()));
			entry.put("original", NbtHelper.fromBlockState(hazard.original()));
			entry.putLong("until", hazard.until());
			list.add(entry);
		}
		nbt.put("hazards", list);
		return nbt;
	}

	private static HazardBlocks fromNbt(NbtCompound nbt, RegistryWrapper.WrapperLookup registries) {
		HazardBlocks hazards = new HazardBlocks();
		RegistryEntryLookup<Block> blocks = registries.getWrapperOrThrow(RegistryKeys.BLOCK);
		for (NbtElement element : nbt.getList("hazards", NbtElement.COMPOUND_TYPE)) {
			NbtCompound entry = (NbtCompound) element;
			Hazard hazard = new Hazard(BlockPos.fromLong(entry.getLong("pos")), NbtHelper.toBlockState(blocks, entry.getCompound("placed")),
					NbtHelper.toBlockState(blocks, entry.getCompound("original")), entry.getLong("until"));
			hazards.byPos.put(hazard.pos(), hazard);
			hazards.byTime.add(hazard);
		}
		return hazards;
	}
}
