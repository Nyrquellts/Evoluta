package com.nyr.evoluta.common.mutation;

import com.mojang.serialization.Codec;
import com.nyr.evoluta.common.Evoluta;
import com.nyr.evoluta.common.soul.GraftCombat;
import com.nyr.evoluta.common.tactics.BruteCharge;
import net.fabricmc.fabric.api.attachment.v1.AttachmentRegistry;
import net.fabricmc.fabric.api.attachment.v1.AttachmentSyncPredicate;
import net.fabricmc.fabric.api.attachment.v1.AttachmentType;

public final class EvolutaAttachments {
	/**
	 * Set only on mutated mobs, so an ordinary mob pays nothing. Saved with the entity (under
	 * {@code fabric:attachments}) and mirrored to every client that tracks the mob, which is how the client knows
	 * what to draw.
	 */
	public static final AttachmentType<MutationData> MUTATION = AttachmentRegistry.<MutationData>builder()
			.persistent(MutationData.CODEC)
			.syncWith(MutationData.PACKET_CODEC, AttachmentSyncPredicate.all())
			.buildAndRegister(Evoluta.id("mutation"));



	/** World time of a Stalker's last lunge, for its cooldown. Neither saved nor synced: it only matters while loaded. */
	public static final AttachmentType<Long> STALKER_LAST_LUNGE = AttachmentRegistry.create(Evoluta.id("stalker_last_lunge"));

	/** A Brute's charge: planted, rushing or spent, and when. Neither saved nor synced, like the lunge. */
	public static final AttachmentType<BruteCharge.State> BRUTE_CHARGE = AttachmentRegistry.create(Evoluta.id("brute_charge"));

	/** World time of a mutant creeper's last blast, for its recharge. Neither saved nor synced, like the lunge. */
	public static final AttachmentType<Long> CREEPER_LAST_BLAST = AttachmentRegistry.create(Evoluta.id("creeper_last_blast"));

	/** Set once a champion's own gear has been grafted (MutantGear), so a reload never grafts it again. Saved. */
	public static final AttachmentType<Boolean> GEAR_GRAFTED = AttachmentRegistry.<Boolean>builder()
			.persistent(Codec.BOOL)
			.buildAndRegister(Evoluta.id("gear_grafted"));

	/** A player's swing while it lands: its target and charge, so grafts count full swings only. Lives one attack. */
	public static final AttachmentType<GraftCombat.Swing> SWING = AttachmentRegistry.create(Evoluta.id("swing"));

	/** World time until which a player's Tunnel Sense stays quiet (after a chime, or a feel that found no ore). Neither saved nor synced. */
	public static final AttachmentType<Long> TUNNEL_SENSE = AttachmentRegistry.create(Evoluta.id("tunnel_sense"));

	/** A creeper graft's hits toward its next burst. Not saved: a relog resets the count. */
	public static final AttachmentType<GraftCombat.Charge> CREEPER_CHARGE = AttachmentRegistry.create(Evoluta.id("creeper_charge"));

	private EvolutaAttachments() {
	}

	/** Loads this class, which registers the attachment types above. */
	public static void register() {
	}
}
