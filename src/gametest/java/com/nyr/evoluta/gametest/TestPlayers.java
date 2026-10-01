package com.nyr.evoluta.gametest;

import com.mojang.authlib.GameProfile;
import io.netty.channel.embedded.EmbeddedChannel;
import java.lang.reflect.Field;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.UUID;
import java.util.concurrent.ConcurrentHashMap;
import net.minecraft.network.ClientConnection;
import net.minecraft.network.NetworkSide;
import net.minecraft.network.packet.BundlePacket;
import net.minecraft.network.packet.Packet;
import net.minecraft.server.network.ConnectedClientData;
import net.minecraft.server.network.ServerPlayerEntity;
import net.minecraft.server.world.ServerWorld;
import net.minecraft.test.TestContext;
import net.minecraft.util.math.BlockPos;
import net.minecraft.util.math.Vec3d;
import net.minecraft.world.GameMode;

/**
 * Real, in-world survival players on a fake connection, for tests that need the world to see a player (distance
 * checks, world.getPlayers()). Built the way vanilla's own mock server player is, minus its forced creative mode.
 * They do not tick their movement, so frozen and burning ticks only change through the code under test.
 */
final class TestPlayers {
	/** Nothing reads these channels: packets sent to the fake players pile up here until drained. */
	private static final Map<ServerPlayerEntity, EmbeddedChannel> CHANNELS = new ConcurrentHashMap<>();

	private TestPlayers() {
	}

	static ServerPlayerEntity survival(TestContext context, BlockPos relative, float yaw) {
		ServerWorld world = context.getWorld();
		ConnectedClientData data = ConnectedClientData.createDefault(new GameProfile(UUID.randomUUID(), "evoluta-test"), false);
		ServerPlayerEntity player = new ServerPlayerEntity(world.getServer(), world, data.gameProfile(), data.syncedOptions());
		ClientConnection connection = new ClientConnection(NetworkSide.SERVERBOUND);
		CHANNELS.put(player, new EmbeddedChannel(connection));
		world.getServer().getPlayerManager().onPlayerConnect(connection, player, data);
		player.changeGameMode(GameMode.SURVIVAL);
		moveTo(context, player, relative, yaw);
		return player;
	}

	/**
	 * Ends the 60 ticks of damage immunity a player gets on joining. Vanilla counts them down in the network
	 * handler's tick, which never runs for these players, so without this they cannot be hurt at all.
	 */
	static void vulnerable(ServerPlayerEntity player) {
		try {
			Field field = ServerPlayerEntity.class.getDeclaredField("joinInvulnerabilityTicks");
			field.setAccessible(true);
			field.setInt(player, 0);
		} catch (ReflectiveOperationException e) {
			throw new IllegalStateException("dev runtime without Yarn names?", e);
		}
	}

	static void moveTo(TestContext context, ServerPlayerEntity player, BlockPos relative, float yaw) {
		Vec3d pos = Vec3d.ofBottomCenter(context.getAbsolutePos(relative));
		player.teleport(context.getWorld(), pos.x, pos.y, pos.z, yaw, 0.0F);
	}

	/** The packets sent to {@code player} since the last drain, bundles opened, and drains them. */
	static List<Packet<?>> sent(ServerPlayerEntity player) {
		List<Packet<?>> packets = new ArrayList<>();
		EmbeddedChannel channel = CHANNELS.get(player);
		if (channel == null) {
			return packets;
		}
		channel.flushOutbound();
		for (Object message = channel.readOutbound(); message != null; message = channel.readOutbound()) {
			if (message instanceof BundlePacket<?> bundle) {
				bundle.getPackets().forEach(packets::add);
			} else if (message instanceof Packet<?> packet) {
				packets.add(packet);
			}
		}
		return packets;
	}

	/** Drops the packets sent to {@code player} so far, as a real client would have read them. */
	static void drain(ServerPlayerEntity player) {
		EmbeddedChannel channel = CHANNELS.get(player);
		if (channel != null) {
			channel.releaseOutbound();
		}
	}

	static void remove(TestContext context, ServerPlayerEntity player) {
		context.getWorld().getServer().getPlayerManager().remove(player);
		EmbeddedChannel channel = CHANNELS.remove(player);
		if (channel != null) {
			channel.releaseOutbound();
		}
	}
}
