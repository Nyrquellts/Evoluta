package com.nyr.evoluta.client.mixin;

import com.nyr.evoluta.client.render.ScreenShake;
import net.minecraft.client.render.Camera;
import net.minecraft.entity.Entity;
import net.minecraft.world.BlockView;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Shadow;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

/**
 * Shakes the view after vanilla has placed the camera, on heavy moments ({@link ScreenShake}). Only the view turns:
 * where the player aims is untouched. {@code require = 0}: a mod that takes over the camera costs the shake, not a crash.
 */
@Mixin(Camera.class)
abstract class CameraMixin {
	@Shadow
	protected abstract void setRotation(float yaw, float pitch);

	@Shadow
	public abstract float getYaw();

	@Shadow
	public abstract float getPitch();

	@Inject(method = "update", at = @At("TAIL"), require = 0)
	private void evoluta$shake(BlockView area, Entity focusedEntity, boolean thirdPerson, boolean inverseView, float tickDelta, CallbackInfo ci) {
		float[] offset = ScreenShake.frame();
		if (offset != null) {
			this.setRotation(this.getYaw() + offset[0], this.getPitch() + offset[1]);
		}
	}
}
