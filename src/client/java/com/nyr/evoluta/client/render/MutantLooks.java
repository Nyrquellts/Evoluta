package com.nyr.evoluta.client.render;

import com.nyr.evoluta.common.mutation.Archetype;
import com.nyr.evoluta.common.mutation.MutationData;
import com.nyr.evoluta.common.mutation.Mutations;
import net.minecraft.client.util.math.MatrixStack;
import net.minecraft.entity.LivingEntity;

/**
 * Silhouettes the scale attribute cannot give on its own, applied by the living-entity renderer mixin. A Brute is drawn
 * {@link #BRUTE_BULK}x broader and deeper on top of its 1.35x size, so it reads as a wall of muscle rather than a
 * big copy of its kind; its hitbox stays the scaled one. A Stalker's body is drawn in shadow while its glowing eyes,
 * drawn after the body, stay bright.
 */
public final class MutantLooks {
	static final float BRUTE_BULK = 1.12F;
	private static final float SHADE_RED = 0.42F;
	private static final float SHADE_GREEN = 0.42F;
	private static final float SHADE_BLUE = 0.52F;

	private MutantLooks() {
	}

	/** Called with the model's pose set up, before the model is drawn. */
	public static void shape(LivingEntity entity, MatrixStack matrices) {
		MutationData data = Mutations.get(entity);
		if (data != null && data.getArchetype() == Archetype.BRUTE) {
			matrices.scale(BRUTE_BULK, 1.0F, BRUTE_BULK);
		}
	}

	/** The ARGB colour the body model is drawn with: a Stalker's in shadow, everyone else's unchanged. */
	public static int bodyColor(LivingEntity entity, int color) {
		MutationData data = Mutations.get(entity);
		return data != null && data.getArchetype() == Archetype.STALKER ? shade(color) : color;
	}

	/** Darkens the colour toward a cold shadow, keeping its alpha (a spectator's see-through view stays see-through). */
	static int shade(int argb) {
		int red = (int) ((argb >> 16 & 0xFF) * SHADE_RED);
		int green = (int) ((argb >> 8 & 0xFF) * SHADE_GREEN);
		int blue = (int) ((argb & 0xFF) * SHADE_BLUE);
		return argb & 0xFF000000 | red << 16 | green << 8 | blue;
	}
}
