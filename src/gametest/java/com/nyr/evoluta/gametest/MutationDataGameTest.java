package com.nyr.evoluta.gametest;

import com.nyr.evoluta.common.mutation.Archetype;
import com.nyr.evoluta.common.mutation.Element;
import com.nyr.evoluta.common.mutation.EvolutaAttachments;
import com.nyr.evoluta.common.mutation.MutationData;
import com.nyr.evoluta.common.mutation.Tier;
import net.fabricmc.fabric.api.gametest.v1.FabricGameTest;
import net.minecraft.entity.EntityType;
import net.minecraft.entity.mob.ZombieEntity;
import net.minecraft.nbt.NbtCompound;
import net.minecraft.test.GameTest;
import net.minecraft.test.TestContext;

public final class MutationDataGameTest implements FabricGameTest {
	@GameTest(templateName = EMPTY_STRUCTURE)
	public void mutationSurvivesSaveAndLoad(TestContext context) {
		ZombieEntity zombie = context.spawnMob(EntityType.ZOMBIE, 1, 2, 1);
		MutationData data = MutationData.of(Tier.APEX, Element.PERMAFROST, Archetype.BRUTE);
		zombie.setAttached(EvolutaAttachments.MUTATION, data);

		NbtCompound saved = zombie.writeNbt(new NbtCompound());
		ZombieEntity reloaded = EntityType.ZOMBIE.create(context.getWorld());
		context.assertTrue(reloaded != null, "zombie could not be created");
		reloaded.readNbt(saved);

		context.assertTrue(data.equals(reloaded.getAttached(EvolutaAttachments.MUTATION)),
				"expected " + data + " after reload, got " + reloaded.getAttached(EvolutaAttachments.MUTATION));
		context.complete();
	}

	@GameTest(templateName = EMPTY_STRUCTURE)
	public void ordinaryMobCarriesNothing(TestContext context) {
		ZombieEntity zombie = context.spawnMob(EntityType.ZOMBIE, 1, 2, 1);
		NbtCompound saved = zombie.writeNbt(new NbtCompound());

		context.assertFalse(zombie.hasAttached(EvolutaAttachments.MUTATION), "an unmutated zombie has a mutation attachment");
		context.assertFalse(saved.contains("fabric:attachments"), "an unmutated zombie saves attachment data: " + saved.get("fabric:attachments"));
		context.complete();
	}
}
