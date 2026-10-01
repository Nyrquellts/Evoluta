package com.nyr.evoluta.client;

import com.nyr.evoluta.common.champion.LocatorSync;
import com.nyr.evoluta.common.mutation.Element;
import com.nyr.evoluta.common.network.TargetPosPayload;
import net.minecraft.client.MinecraftClient;
import net.minecraft.client.world.ClientWorld;
import net.minecraft.util.math.GlobalPos;
import org.jetbrains.annotations.Nullable;

/** The last target the server sent. Render thread only (payload handlers, item models and client ticks all run there). */
final class ClientLocatorState {
	/** Two missed updates mean the champion is gone or the locator left the hand: stop pointing. */
	static final long STALE_TICKS = 2L * LocatorSync.INTERVAL + 10;

	@Nullable
	private static GlobalPos target;
	private static Element element = Element.NONE;
	private static long receivedAt;

	private ClientLocatorState() {
	}

	static void receive(TargetPosPayload payload, MinecraftClient client) {
		ClientWorld world = client.world;
		if (world == null) {
			return;
		}
		target = payload.target().map(pos -> GlobalPos.create(world.getRegistryKey(), pos)).orElse(null);
		element = payload.element();
		receivedAt = world.getTime();
	}

	/** The target, if it is fresh and in this world. */
	@Nullable
	static GlobalPos target(ClientWorld world) {
		GlobalPos current = target;
		if (current == null || current.dimension() != world.getRegistryKey() || world.getTime() - receivedAt > STALE_TICKS) {
			return null;
		}
		return current;
	}

	/** The target's element, if there is a fresh target in this world; null otherwise. */
	@Nullable
	static Element element(ClientWorld world) {
		return target(world) == null ? null : element;
	}

	static void clear() {
		target = null;
	}
}
