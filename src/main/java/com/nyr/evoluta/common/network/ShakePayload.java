package com.nyr.evoluta.common.network;

import com.nyr.evoluta.common.Evoluta;
import io.netty.buffer.ByteBuf;
import net.minecraft.network.codec.PacketCodec;
import net.minecraft.network.codec.PacketCodecs;
import net.minecraft.network.packet.CustomPayload;

/**
 * Server to client, on the few moments that should hit hard (a mutant's blow, a Brute's slam, a creeper's blast, a
 * champion's roar or death): shake the camera by {@code trauma} (0 to 1, added to what is already shaking). The client
 * scales it by its Distortion Effects setting, so a player who turned those off feels nothing.
 */
public record ShakePayload(float trauma) implements CustomPayload {
	public static final CustomPayload.Id<ShakePayload> ID = new CustomPayload.Id<>(Evoluta.id("shake"));
	public static final PacketCodec<ByteBuf, ShakePayload> CODEC = PacketCodec.tuple(PacketCodecs.FLOAT, ShakePayload::trauma, ShakePayload::new);

	@Override
	public CustomPayload.Id<ShakePayload> getId() {
		return ID;
	}
}
