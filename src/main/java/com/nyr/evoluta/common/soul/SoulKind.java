package com.nyr.evoluta.common.soul;

import com.mojang.serialization.Codec;
import com.nyr.evoluta.common.Evoluta;
import io.netty.buffer.ByteBuf;
import java.util.List;
import java.util.function.IntFunction;
import net.minecraft.entity.EntityType;
import net.minecraft.entity.LivingEntity;
import net.minecraft.network.codec.PacketCodec;
import net.minecraft.network.codec.PacketCodecs;
import net.minecraft.registry.RegistryKeys;
import net.minecraft.registry.tag.TagKey;
import net.minecraft.util.StringIdentifiable;
import net.minecraft.util.function.ValueLists;
import org.jetbrains.annotations.Nullable;

/**
 * What kind of creature a piece of Soul Meat came from, which decides the ability it grafts. Each kind is an
 * entity-type tag, {@code #evoluta:soul_kind/<kind>}, so a modpack maps its own mobs by datapack. A baby of a
 * zombie kind is a {@link #BABY_ZOMBIE}.
 */
public enum SoulKind implements StringIdentifiable {
	ZOMBIE("zombie", true),
	HUSK("husk", true),
	DROWNED("drowned", true),
	ZOMBIE_VILLAGER("zombie_villager", true),
	BABY_ZOMBIE("baby_zombie", false),
	SKELETON("skeleton", false),
	STRAY("stray", false),
	BOGGED("bogged", false),
	CREEPER("creeper", false),
	SPIDER("spider", false),
	CAVE_SPIDER("cave_spider", false);

	public static final List<SoulKind> ALL = List.of(values());
	public static final Codec<SoulKind> CODEC = StringIdentifiable.createCodec(SoulKind::values);
	private static final IntFunction<SoulKind> BY_ORDINAL = ValueLists.createIdToValueFunction(SoulKind::ordinal, values(),
			ValueLists.OutOfBoundsHandling.ZERO);
	public static final PacketCodec<ByteBuf, SoulKind> PACKET_CODEC = PacketCodecs.indexed(BY_ORDINAL, SoulKind::ordinal);

	private final String key;
	private final boolean zombie;
	private final TagKey<EntityType<?>> tag;

	SoulKind(String key, boolean zombie) {
		this.key = key;
		this.zombie = zombie;
		this.tag = TagKey.of(RegistryKeys.ENTITY_TYPE, Evoluta.id("soul_kind/" + key));
	}

	public String key() {
		return this.key;
	}

	@Override
	public String asString() {
		return this.key;
	}

	public TagKey<EntityType<?>> tag() {
		return this.tag;
	}

	/** The kind of {@code entity}'s Soul Meat, or null when no kind tag lists its type. */
	@Nullable
	public static SoulKind of(LivingEntity entity) {
		EntityType<?> type = entity.getType();
		for (SoulKind kind : ALL) {
			if (type.isIn(kind.tag)) {
				return kind.zombie && entity.isBaby() ? BABY_ZOMBIE : kind;
			}
		}
		return null;
	}

	@Nullable
	public static SoulKind byKey(String key) {
		for (SoulKind kind : ALL) {
			if (kind.key.equals(key)) {
				return kind;
			}
		}
		return null;
	}
}
