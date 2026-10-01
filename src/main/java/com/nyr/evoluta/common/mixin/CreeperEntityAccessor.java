package com.nyr.evoluta.common.mixin;

import net.minecraft.entity.data.TrackedData;
import net.minecraft.entity.mob.CreeperEntity;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.gen.Accessor;

/** What a mutant creeper needs to put its fuse out after a blast; vanilla never does, since its creeper is gone. */
@Mixin(CreeperEntity.class)
public interface CreeperEntityAccessor {
	@Accessor("currentFuseTime")
	void evoluta$setCurrentFuseTime(int ticks);

	@Accessor("IGNITED")
	static TrackedData<Boolean> evoluta$ignited() {
		throw new AssertionError("mixin accessor");
	}
}
