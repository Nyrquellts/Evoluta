package com.nyr.evoluta.common.world;

import java.util.Arrays;
import java.util.List;
import net.minecraft.server.network.ServerPlayerEntity;
import net.minecraft.server.world.ServerWorld;

/**
 * The positions of a world's living, non-spectating players, copied into flat arrays once per tick. Every mutant's
 * "is anyone within 32 blocks" check then costs a tight loop of arithmetic instead of walking the player list
 * through entity calls, which keeps the tactical tick cheap as both mutants and players grow. Server thread only.
 */
public final class PlayerSnapshot {
	private ServerPlayerEntity[] players = new ServerPlayerEntity[8];
	private double[] xs = new double[8];
	private double[] ys = new double[8];
	private double[] zs = new double[8];
	private int count;
	private long takenAt = Long.MIN_VALUE;

	/** Copies the players' positions, once per world tick. */
	public void refresh(ServerWorld world, long worldTime) {
		if (worldTime == this.takenAt) {
			return;
		}
		this.takenAt = worldTime;
		List<ServerPlayerEntity> list = world.getPlayers();
		if (this.players.length < list.size()) {
			int size = Math.max(list.size(), this.players.length * 2);
			this.players = Arrays.copyOf(this.players, size);
			this.xs = Arrays.copyOf(this.xs, size);
			this.ys = Arrays.copyOf(this.ys, size);
			this.zs = Arrays.copyOf(this.zs, size);
		}
		int n = 0;
		for (int i = 0; i < list.size(); i++) {
			ServerPlayerEntity player = list.get(i);
			if (player.isSpectator() || !player.isAlive()) {
				continue;
			}
			this.players[n] = player;
			this.xs[n] = player.getX();
			this.ys[n] = player.getY();
			this.zs[n] = player.getZ();
			n++;
		}
		// drop references beyond the new count so departed players can be collected
		Arrays.fill(this.players, n, this.count > n ? this.count : n, null);
		this.count = n;
	}

	public int size() {
		return this.count;
	}

	/** Whether any player stood within {@code range} blocks of (x, y, z) at the start of this tick's tactics. */
	public boolean anyWithin(double x, double y, double z, double range) {
		double rangeSq = range * range;
		for (int i = 0; i < this.count; i++) {
			double dx = this.xs[i] - x;
			double dy = this.ys[i] - y;
			double dz = this.zs[i] - z;
			if (dx * dx + dy * dy + dz * dz < rangeSq) {
				return true;
			}
		}
		return false;
	}

	/** The i-th player, if it stood within {@code range} blocks of (x, y, z); null otherwise. */
	public ServerPlayerEntity within(int i, double x, double y, double z, double range) {
		double dx = this.xs[i] - x;
		double dy = this.ys[i] - y;
		double dz = this.zs[i] - z;
		return dx * dx + dy * dy + dz * dz <= range * range ? this.players[i] : null;
	}
}
