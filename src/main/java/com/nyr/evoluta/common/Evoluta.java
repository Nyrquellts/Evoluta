package com.nyr.evoluta.common;

import com.nyr.evoluta.common.combat.CombatEvents;
import com.nyr.evoluta.common.combat.HazardBlockTypes;
import com.nyr.evoluta.common.combat.HazardBlocks;
import com.nyr.evoluta.common.command.EvolutaCommands;
import com.nyr.evoluta.common.config.EvolutaConfig;
import com.nyr.evoluta.common.forge.SoulForge;
import com.nyr.evoluta.common.item.EvolutaItems;
import com.nyr.evoluta.common.mutation.EvolutaAttachments;
import com.nyr.evoluta.common.mutation.SpawnMutations;
import com.nyr.evoluta.common.network.EvolutaNetworking;
import com.nyr.evoluta.common.particle.EvolutaParticles;
import com.nyr.evoluta.common.soul.GraftCombat;
import com.nyr.evoluta.common.soul.SoulComponents;
import com.nyr.evoluta.common.soul.SoulMeatLootFunction;
import com.nyr.evoluta.common.soul.ToolGrafts;
import com.nyr.evoluta.common.sound.EvolutaSounds;
import com.nyr.evoluta.common.world.EvolutaWorlds;
import net.fabricmc.api.ModInitializer;
import net.minecraft.util.Identifier;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

public final class Evoluta implements ModInitializer {
	public static final String MOD_ID = "evoluta";
	public static final Logger LOGGER = LoggerFactory.getLogger("Evoluta");

	public static Identifier id(String path) {
		return Identifier.of(MOD_ID, path);
	}

	@Override
	public void onInitialize() {
		EvolutaAttachments.register();
		SoulComponents.register();
		EvolutaItems.register();
		SoulForge.register();
		SoulMeatLootFunction.register();
		EvolutaSounds.register();
		EvolutaParticles.register();
		EvolutaNetworking.register();
		EvolutaConfig.load(false);
		SpawnMutations.register();
		EvolutaWorlds.register();
		CombatEvents.register();
		HazardBlocks.register();
		HazardBlockTypes.register();
		GraftCombat.register();
		ToolGrafts.register();
		EvolutaCommands.register();
	}
}
