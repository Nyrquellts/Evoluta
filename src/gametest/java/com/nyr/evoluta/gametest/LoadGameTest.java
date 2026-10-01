package com.nyr.evoluta.gametest;

import com.nyr.evoluta.common.mutation.Archetype;
import com.nyr.evoluta.common.mutation.Element;
import com.nyr.evoluta.common.mutation.Tier;
import com.nyr.evoluta.common.world.EvolutaWorlds;
import com.nyr.evoluta.common.world.TickMeter;
import com.nyr.evoluta.common.world.WorldState;
import java.util.ArrayList;
import java.util.List;
import net.fabricmc.fabric.api.gametest.v1.FabricGameTest;
import net.minecraft.entity.EntityType;
import net.minecraft.entity.mob.ZombieEntity;
import net.minecraft.server.network.ServerPlayerEntity;
import net.minecraft.test.GameTest;
import net.minecraft.test.TestContext;
import net.minecraft.util.math.BlockPos;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

/**
 * Evoluta's own server cost under load, read from the same meter {@code /evoluta stats} prints: 200 mutants of
 * every element and archetype, each targeting one of 50 survival players within 32 blocks, for 200 ticks. It
 * measures Evoluta's work (tactical tick and combat events), not the vanilla AI the mobs would run anyway.
 */
public final class LoadGameTest implements FabricGameTest {
	private static final Logger LOGGER = LoggerFactory.getLogger("Evoluta load test");
	private static final int MUTANTS = 200;
	private static final int PLAYERS = 50;
	/** The budget for Evoluta's server work per tick. */
	private static final double BUDGET_MICROS = 80;

	@GameTest(templateName = EMPTY_STRUCTURE, batchId = "evoluta_load", tickLimit = 600)
	public void twoHundredMutantsAndFiftyPlayersStayInsideTheBudget(TestContext context) {
		List<ServerPlayerEntity> players = new ArrayList<>();
		for (int i = 0; i < PLAYERS; i++) {
			double angle = Math.PI * 2 * i / PLAYERS;
			players.add(TestPlayers.survival(context, new BlockPos(4 + (int) (Math.cos(angle) * 12), 2, 4 + (int) (Math.sin(angle) * 12)), i * 7.2F));
		}
		List<ZombieEntity> mutants = new ArrayList<>();
		Element[] elements = {Element.NONE, Element.IGNITED, Element.PERMAFROST, Element.TOXIC};
		Archetype[] archetypes = {Archetype.NONE, Archetype.BRUTE, Archetype.STALKER};
		for (int i = 0; i < MUTANTS; i++) {
			ZombieEntity mutant = Mutants.still(context, EntityType.ZOMBIE, -4 + i % 16, -4 + i / 16,
					Tier.values()[i % 3], elements[i % elements.length], archetypes[i % archetypes.length]);
			mutant.setTarget(players.get(i % PLAYERS));
			mutants.add(mutant);
		}
		WorldState state = EvolutaWorlds.of(context.getWorld());
		context.assertTrue(state.loaded.size() >= MUTANTS, "only " + state.loaded.size() + " mutants are tracked");
		// a real client reads its packets; left in the fake channels they would fill the heap and add GC pauses
		context.runAtEveryTick(() -> players.forEach(TestPlayers::drain));

		double[] cold = new double[2];
		context.runAtTick(TickMeter.WINDOW + 20, () -> {
			cold[0] = state.meter.averageMicros();
			cold[1] = state.meter.maxMicros();
		});
		// the second window starts after the JIT has seen this code 200 times: the steady state a live server runs in
		context.runAtTick(2L * TickMeter.WINDOW + 20, () -> {
			TickMeter meter = state.meter;
			double average = meter.averageMicros();
			LOGGER.info("{} mutants, {} players, {} warm ticks: Evoluta cost per tick average {} us, median {}, p95 {}, p99 {}, max {} "
							+ "(first {} ticks after spawning: average {} us, max {})",
					MUTANTS, PLAYERS, meter.ticks(), "%.2f".formatted(average), "%.2f".formatted(meter.percentileMicros(50)),
					"%.2f".formatted(meter.percentileMicros(95)), "%.2f".formatted(meter.percentileMicros(99)),
					"%.2f".formatted(meter.maxMicros()), TickMeter.WINDOW, "%.2f".formatted(cold[0]), "%.2f".formatted(cold[1]));
			mutants.forEach(ZombieEntity::discard);
			players.forEach(player -> TestPlayers.remove(context, player));
			context.assertTrue(average < BUDGET_MICROS, "Evoluta averaged " + average + " us per tick, over the " + BUDGET_MICROS + " us budget");
			context.complete();
		});
	}
}
