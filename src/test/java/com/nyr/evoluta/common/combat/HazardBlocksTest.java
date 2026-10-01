package com.nyr.evoluta.common.combat;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.nio.file.Path;
import java.util.HashSet;
import java.util.Set;
import java.util.stream.Stream;
import net.minecraft.Bootstrap;
import net.minecraft.SharedConstants;
import net.minecraft.block.Blocks;
import net.minecraft.block.SnowyBlock;
import net.minecraft.nbt.NbtCompound;
import net.minecraft.nbt.NbtElement;
import net.minecraft.registry.Registries;
import net.minecraft.registry.RegistryWrapper;
import net.minecraft.util.math.BlockPos;
import net.minecraft.world.PersistentStateManager;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

/** Hazard blocks waiting to go back outlive a restart: saved and loaded again through vanilla's own saved-data path. */
class HazardBlocksTest {
	private static RegistryWrapper.WrapperLookup registries;

	@BeforeAll
	static void bootstrap() {
		SharedConstants.createGameVersion();
		Bootstrap.initialize();
		registries = RegistryWrapper.WrapperLookup.of(Stream.of(Registries.BLOCK.getReadOnlyWrapper()));
	}

	private static Set<NbtElement> entries(HazardBlocks hazards) {
		return new HashSet<>(hazards.writeNbt(new NbtCompound(), registries).getList("hazards", NbtElement.COMPOUND_TYPE));
	}

	@Test
	void hazardsWaitingToGoBackSurviveASaveAndARestart(@TempDir Path dir) {
		BlockPos magma = new BlockPos(10, 64, -3);
		BlockPos snow = new BlockPos(-29_999_000, -60, 7);
		HazardBlocks before = new HazardBlocks();
		before.remember(magma, Blocks.MAGMA_BLOCK.getDefaultState(), Blocks.GRASS_BLOCK.getDefaultState().with(SnowyBlock.SNOWY, true), 1_200L);
		before.remember(snow, Blocks.POWDER_SNOW.getDefaultState(), Blocks.AIR.getDefaultState(), 900L);

		PersistentStateManager running = new PersistentStateManager(dir.toFile(), null, registries);
		running.set(HazardBlocks.ID, before);
		running.save();
		assertTrue(dir.resolve(HazardBlocks.ID + ".dat").toFile().isFile(), "nothing was written");

		HazardBlocks after = new PersistentStateManager(dir.toFile(), null, registries).get(HazardBlocks.TYPE, HazardBlocks.ID);
		assertNotNull(after, "the saved hazards did not load");
		assertTrue(after.holds(magma) && after.holds(snow), "a hazard was lost on the way");
		assertFalse(after.holds(magma.up()), "a hazard appeared from nowhere");
		assertEquals(entries(before), entries(after), "a hazard came back changed (block states, place or time)");
	}
}
