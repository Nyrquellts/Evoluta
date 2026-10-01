package com.nyr.evoluta.client.render;

import com.nyr.evoluta.common.mutation.MutationData;
import com.nyr.evoluta.common.mutation.Mutations;
import net.minecraft.client.render.LightmapTextureManager;
import net.minecraft.client.render.OverlayTexture;
import net.minecraft.client.render.RenderLayer;
import net.minecraft.client.render.VertexConsumerProvider;
import net.minecraft.client.render.entity.LivingEntityRenderer;
import net.minecraft.client.render.entity.feature.FeatureRenderer;
import net.minecraft.client.render.entity.feature.FeatureRendererContext;
import net.minecraft.client.render.entity.model.EntityModel;
import net.minecraft.client.util.math.MatrixStack;
import net.minecraft.entity.LivingEntity;
import net.minecraft.entity.mob.MobEntity;
import net.minecraft.util.Identifier;
import net.minecraft.util.math.MathHelper;
import org.jetbrains.annotations.Nullable;

/**
 * A mutant's body, as one render layer on every mob renderer: its element skin ({@link MutantSkins}: a crust in the
 * world's light, then a glow at full brightness that breathes with the element), and on Elite and Apex mutants eyes
 * that glow in the element's colour. Each is one extra pass of the mob's own model, posed as the mob, the way vanilla
 * draws a spider's eyes. The auras are particles ({@link MutantAuras}); size is the vanilla scale attribute; the
 * Brute's bulk and the Stalker's shade come from {@link MutantLooks}.
 */
public final class MutantFeatureRenderer<T extends LivingEntity, M extends EntityModel<T>> extends FeatureRenderer<T, M> {
	@Nullable
	private final EntityModel<T> shell;

	public MutantFeatureRenderer(FeatureRendererContext<T, M> context) {
		this(context, null);
	}

	/**
	 * @param shell the mob's outer layer, for mobs whose outer layer covers their eyes (the bogged's moss): the
	 *              eyes are drawn on it, posed like the mob, instead of on the hidden inner model
	 */
	public MutantFeatureRenderer(FeatureRendererContext<T, M> context, @Nullable EntityModel<T> shell) {
		super(context);
		this.shell = shell;
	}

	@Override
	public void render(MatrixStack matrices, VertexConsumerProvider vertexConsumers, int light, T entity, float limbAngle,
			float limbDistance, float tickDelta, float animationProgress, float headYaw, float headPitch) {
		if (!(entity instanceof MobEntity) || entity.isInvisible()) {
			return;
		}
		MutationData data = Mutations.get(entity);
		if (data == null) {
			return;
		}
		EntityModel<T> model = this.getContextModel();
		MutantSkins.Skin skin = MutantSkins.get(this.getTexture(entity), data.getElement(), data.getTier(), SoulCores.forType(entity.getType()));
		if (skin != null) {
			// the crust is skin: lit like the body, shaded with a Stalker's, flashing red with it when hurt
			model.render(matrices, vertexConsumers.getBuffer(RenderLayer.getEntityTranslucent(skin.crust(), false)), light,
					LivingEntityRenderer.getOverlay(entity, 0.0F), MutantLooks.bodyColor(entity, 0xFFFFFFFF));
			model.render(matrices, vertexConsumers.getBuffer(RenderLayer.getEyes(skin.glow())), LightmapTextureManager.MAX_LIGHT_COORDINATE,
					OverlayTexture.DEFAULT_UV, glowColor(data, animationProgress + entity.getId() * 7.3F));
		}
		if (!data.isChampion()) {
			return;
		}
		Identifier mask = EyeMasks.forType(entity.getType());
		if (mask == null) {
			return;
		}
		if (this.shell != null) {
			model.copyStateTo(this.shell);
			this.shell.animateModel(entity, limbAngle, limbDistance, tickDelta);
			this.shell.setAngles(entity, limbAngle, limbDistance, animationProgress, headYaw, headPitch);
			model = this.shell;
		}
		model.render(matrices, vertexConsumers.getBuffer(RenderLayer.getEyes(mask)),
				LightmapTextureManager.MAX_LIGHT_COORDINATE, OverlayTexture.DEFAULT_UV, eyeColor(data));
	}

	/**
	 * The skin glow's strength as an opaque grey (the eyes layer adds texture times this): stronger by tier, and
	 * alive: magma flickers, blight throbs slowly, frost shimmers. {@code time} is in ticks, offset per mob.
	 */
	static int glowColor(MutationData data, float time) {
		float strength = switch (data.getTier()) {
			case EVOLVED -> 0.55F;
			case ELITE -> 0.8F;
			case APEX -> 1.0F;
		};
		float wave = switch (data.getElement()) {
			case IGNITED -> 0.8F + 0.2F * MathHelper.sin(time * 0.9F) * MathHelper.sin(time * 0.37F);
			case TOXIC -> 0.78F + 0.22F * MathHelper.sin(time * 0.13F);
			default -> 0.9F + 0.1F * MathHelper.sin(time * 0.06F);
		};
		int level = MathHelper.clamp((int) (255 * strength * wave), 0, 255);
		return 0xFF000000 | level << 16 | level << 8 | level;
	}

	/** Opaque ARGB: the eyes layer adds this colour, so brighter reads as a stronger glow. */
	static int eyeColor(MutationData data) {
		return switch (data.getElement()) {
			case IGNITED -> 0xFFFF8A2A;
			case PERMAFROST -> 0xFF8FEFFF;
			case TOXIC -> 0xFF9CFF45;
			case ABYSSAL -> 0xFFB070FF;
			case NONE -> 0xFFFF3B3B;
		};
	}
}
