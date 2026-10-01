package com.nyr.evoluta.common.network;

import com.nyr.evoluta.common.Evoluta;
import com.nyr.evoluta.common.mutation.Element;
import io.netty.buffer.ByteBuf;
import java.util.Optional;
import net.minecraft.network.codec.PacketCodec;
import net.minecraft.network.codec.PacketCodecs;
import net.minecraft.network.packet.CustomPayload;
import net.minecraft.util.math.BlockPos;

/**
 * Server to client, every 30 ticks while a player holds a champion locator: where the nearest living champion is and
 * its element (the locator's gem shows it), or nothing when none is in range. The client turns the needle toward it
 * every frame on its own.
 */
public record TargetPosPayload(Optional<BlockPos> target, Element element) implements CustomPayload {
	public static final CustomPayload.Id<TargetPosPayload> ID = new CustomPayload.Id<>(Evoluta.id("target_pos"));
	public static final PacketCodec<ByteBuf, TargetPosPayload> CODEC = PacketCodec.tuple(
			PacketCodecs.optional(BlockPos.PACKET_CODEC), TargetPosPayload::target,
			Element.PACKET_CODEC, TargetPosPayload::element,
			TargetPosPayload::new
	);
	public static final TargetPosPayload NOTHING = new TargetPosPayload(Optional.empty(), Element.NONE);

	@Override
	public CustomPayload.Id<TargetPosPayload> getId() {
		return ID;
	}
}
