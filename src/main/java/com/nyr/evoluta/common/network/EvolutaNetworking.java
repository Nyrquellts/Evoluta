package com.nyr.evoluta.common.network;

import net.fabricmc.fabric.api.networking.v1.PayloadTypeRegistry;

public final class EvolutaNetworking {
	private EvolutaNetworking() {
	}

	/** Registers every payload type; runs on both sides. */
	public static void register() {
		PayloadTypeRegistry.playS2C().register(TargetPosPayload.ID, TargetPosPayload.CODEC);
		PayloadTypeRegistry.playS2C().register(ShakePayload.ID, ShakePayload.CODEC);
	}
}
