package com.nyr.evoluta.client;

import com.nyr.evoluta.common.champion.LocatorSignal;
import net.minecraft.client.MinecraftClient;
import net.minecraft.client.item.ClampedModelPredicateProvider;
import net.minecraft.client.network.ClientPlayerEntity;
import net.minecraft.client.world.ClientWorld;
import net.minecraft.entity.Entity;
import net.minecraft.entity.LivingEntity;
import net.minecraft.item.ItemStack;
import net.minecraft.util.math.BlockPos;
import net.minecraft.util.math.GlobalPos;
import net.minecraft.util.math.MathHelper;
import org.jetbrains.annotations.Nullable;

/**
 * The Blight Locator's needle, as the vanilla compass "angle" model predicate (0 to 1 turns), so the item is drawn
 * with the recovery compass's own 32 needle frames: no extra geometry, no custom renderer.
 *
 * <p>For the local player it is worked out every frame, not every tick: from the camera yaw and position
 * interpolated to the frame, through a damped spring on real frame time, so the needle stays on the champion while
 * the camera turns and swings to a new target instead of jumping. The server only says where the champion is, once
 * every 1.5 seconds.
 */
final class LocatorNeedle implements ClampedModelPredicateProvider {
	/** Spring on the needle, in turns and seconds: settles in about a third of a second with a slight swing. */
	private static final double STIFFNESS = 90.0;
	private static final double DAMPING = 16.0;
	/** Integration step. The spring is only stable below about 0.098 s per step, so slow frames take several. */
	private static final double STEP = 1.0 / 60.0;

	private double shown;
	private double speed;
	private long lastNanos;

	@Override
	public float unclampedCall(ItemStack stack, @Nullable ClientWorld world, @Nullable LivingEntity holder, int seed) {
		Entity entity = holder != null ? holder : stack.getHolder();
		if (entity == null) {
			return 0.0F;
		}
		ClientWorld clientWorld = world != null ? world : entity.getWorld() instanceof ClientWorld own ? own : null;
		if (clientWorld == null) {
			return 0.0F;
		}
		GlobalPos target = ClientLocatorState.target(clientWorld);
		MinecraftClient client = MinecraftClient.getInstance();
		if (entity == client.player) {
			return (float) this.follow(client, client.player, target);
		}
		// item frames, other players, dropped stacks: point straight at the target, or sweep while searching
		double wanted = target == null ? searching(seed) : bearing(entity.getX(), entity.getZ(), entity.getBodyYaw(), target.pos());
		return (float) MathHelper.floorMod(wanted, 1.0);
	}

	private double follow(MinecraftClient client, ClientPlayerEntity player, @Nullable GlobalPos target) {
		float frame = client.getRenderTickCounter().getTickDelta(true);
		double x = MathHelper.lerp(frame, player.prevX, player.getX());
		double z = MathHelper.lerp(frame, player.prevZ, player.getZ());
		double wanted = target == null ? searching(0) : bearing(x, z, player.getYaw(frame), target.pos());

		long now = System.nanoTime();
		// several calls in one frame (hand, hotbar) integrate nearly nothing; a long gap is capped
		double dt = this.lastNanos == 0 ? 0 : Math.min(0.25, (now - this.lastNanos) / 1.0E9);
		this.lastNanos = now;
		while (dt > 0) {
			double step = Math.min(dt, STEP);
			double error = MathHelper.floorMod(wanted - this.shown + 0.5, 1.0) - 0.5;
			this.speed += (error * STIFFNESS - this.speed * DAMPING) * step;
			this.shown = MathHelper.floorMod(this.shown + this.speed * step, 1.0);
			dt -= step;
		}
		return this.shown;
	}

	private static double bearing(double x, double z, float yaw, BlockPos pos) {
		return LocatorSignal.needleAngle(x, z, yaw, pos.getX() + 0.5, pos.getZ() + 0.5);
	}

	/** No champion in range: a slow sweep with a wobble, a needle hunting for a signal. */
	private static double searching(int seed) {
		double seconds = System.nanoTime() / 1.0E9;
		return seconds * 0.12 + 0.04 * Math.sin(seconds * 2.7) + (seed & 0xFF) / 256.0;
	}
}
