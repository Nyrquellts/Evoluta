package com.nyr.evoluta.client;

import com.nyr.evoluta.common.champion.LocatorSignal;
import com.nyr.evoluta.common.champion.LocatorSync;
import com.nyr.evoluta.common.sound.EvolutaSounds;
import net.minecraft.client.MinecraftClient;
import net.minecraft.client.network.ClientPlayerEntity;
import net.minecraft.util.Util;
import net.minecraft.util.math.GlobalPos;

/** The Blight Locator's soft, rhythmic tick: steady far out, quicker and higher within 20 blocks, silent with no target. */
final class LocatorClicks {
	private static int cooldown;
	/** When the last click played, in {@link Util#getMeasuringTimeMs()}: the gem flares with it. */
	private static long lastClickMs;

	private LocatorClicks() {
	}

	static void tick(MinecraftClient client) {
		ClientPlayerEntity player = client.player;
		GlobalPos target = player == null || client.world == null || client.isPaused() || !LocatorSync.holdsLocator(player)
				? null
				: ClientLocatorState.target(client.world);
		if (target == null) {
			cooldown = 0;
			return;
		}
		if (cooldown > 0) {
			cooldown--;
			return;
		}
		double dx = target.pos().getX() + 0.5 - player.getX();
		double dz = target.pos().getZ() + 0.5 - player.getZ();
		double distance = Math.sqrt(dx * dx + dz * dz);
		player.playSound(EvolutaSounds.BLIGHT_LOCATOR_CLICK, 0.35F, LocatorSignal.clickPitch(distance));
		lastClickMs = Util.getMeasuringTimeMs();
		cooldown = LocatorSignal.clickInterval(distance) - 1;
	}

	static long lastClickMs() {
		return lastClickMs;
	}
}
