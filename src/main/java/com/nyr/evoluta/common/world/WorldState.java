package com.nyr.evoluta.common.world;

import com.nyr.evoluta.common.champion.ChunkSpatialIndex;
import com.nyr.evoluta.common.tactics.MutantBuckets;
import it.unimi.dsi.fastutil.objects.ReferenceArrayList;
import it.unimi.dsi.fastutil.objects.ReferenceOpenHashSet;
import java.util.List;
import java.util.Set;
import net.minecraft.entity.mob.MobEntity;

/** Evoluta's runtime state for one server world. Server thread only. */
public final class WorldState {
	/** Every loaded mutant. */
	public final Set<MobEntity> loaded = new ReferenceOpenHashSet<>();
	/** The loaded mutants that have tactical work (see {@code Tactics#needsTicks}), staggered for the tactical tick. */
	public final MutantBuckets mutants = new MutantBuckets();
	/** Loaded Elite and Apex mutants by chunk: what the Blight Locator searches, and the per-chunk spawn cap. */
	public final ChunkSpatialIndex<MobEntity> champions = new ChunkSpatialIndex<>();
	/** Brutes in the middle of a charge's rush: pushed on every tick until it lands, hits a wall or runs out. */
	public final List<MobEntity> charging = new ReferenceArrayList<>();
	/** Where the players stood at the start of this tick's tactics. */
	public final PlayerSnapshot players = new PlayerSnapshot();
	public final TickMeter meter = new TickMeter();
	/** Hit bursts sent in tick {@link #impactTick}, for {@code Juice}'s per-tick cap. */
	public long impactTick = -1;
	public int impacts;
}
