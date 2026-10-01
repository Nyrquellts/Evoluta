package com.nyr.evoluta.client;

import com.nyr.evoluta.client.particle.EvolutaParticleFactories;
import com.nyr.evoluta.client.render.GlintTextures;
import com.nyr.evoluta.client.render.HazardBlockLooks;
import com.nyr.evoluta.client.render.MutantAuras;
import com.nyr.evoluta.client.render.MutantFeatureRenderer;
import com.nyr.evoluta.client.render.MutantSkins;
import com.nyr.evoluta.client.render.ScreenShake;
import com.nyr.evoluta.common.item.EvolutaItems;
import com.nyr.evoluta.common.network.ShakePayload;
import com.nyr.evoluta.common.network.TargetPosPayload;
import net.fabricmc.api.ClientModInitializer;
import net.fabricmc.fabric.api.client.event.lifecycle.v1.ClientTickEvents;
import net.fabricmc.fabric.api.client.networking.v1.ClientPlayConnectionEvents;
import net.fabricmc.fabric.api.client.networking.v1.ClientPlayNetworking;
import net.fabricmc.fabric.api.client.rendering.v1.LivingEntityFeatureRendererRegistrationCallback;
import net.minecraft.client.item.ModelPredicateProviderRegistry;
import net.minecraft.client.render.entity.LivingEntityRenderer;
import net.minecraft.client.render.entity.model.EntityModel;
import net.minecraft.client.render.entity.model.EntityModelLayers;
import net.minecraft.client.render.entity.model.SkeletonEntityModel;
import net.minecraft.entity.EntityType;
import net.minecraft.entity.mob.BoggedEntity;
import net.minecraft.util.Identifier;
import org.jetbrains.annotations.Nullable;

public final class EvolutaClient implements ClientModInitializer {
	@Override
	public void onInitializeClient() {
		ClientPlayNetworking.registerGlobalReceiver(TargetPosPayload.ID, (payload, context) -> ClientLocatorState.receive(payload, context.client()));
		ClientPlayNetworking.registerGlobalReceiver(ShakePayload.ID, (payload, context) -> ScreenShake.add(payload.trauma()));
		ClientPlayConnectionEvents.DISCONNECT.register((handler, client) -> ClientLocatorState.clear());
		ModelPredicateProviderRegistry.register(EvolutaItems.BLIGHT_LOCATOR, Identifier.ofVanilla("angle"), new LocatorNeedle());
		LocatorGem.register();
		EvolutaParticleFactories.register();
		MutantSkins.register();
		HazardBlockLooks.register();
		SoulMeatClient.register();
		GlintTextures.register();
		ClientTickEvents.END_CLIENT_TICK.register(LocatorClicks::tick);
		ClientTickEvents.END_CLIENT_TICK.register(MutantAuras::tick);
		// any mob can be tagged into mutating, so every mob renderer gets the layer; players and armour stands cannot mutate
		LivingEntityFeatureRendererRegistrationCallback.EVENT.register((entityType, renderer, helper, context) -> {
			if (entityType == EntityType.PLAYER || entityType == EntityType.ARMOR_STAND) {
				return;
			}
			// the bogged's moss layer is opaque over its eyes, so its eyes glow on that layer
			EntityModel<?> shell = entityType == EntityType.BOGGED
					? new SkeletonEntityModel<BoggedEntity>(context.getPart(EntityModelLayers.BOGGED_OUTER))
					: null;
			addMutantLayer(renderer, helper, shell);
		});
	}

	@SuppressWarnings({"unchecked", "rawtypes"})
	private static void addMutantLayer(LivingEntityRenderer<?, ?> renderer, LivingEntityFeatureRendererRegistrationCallback.RegistrationHelper helper,
			@Nullable EntityModel<?> shell) {
		helper.register(new MutantFeatureRenderer(renderer, (EntityModel) shell));
	}
}
