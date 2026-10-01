package com.nyr.evoluta.common.soul;

import com.nyr.evoluta.common.mutation.Element;
import com.nyr.evoluta.common.mutation.EvolutaAttachments;
import java.util.ArrayDeque;
import java.util.ArrayList;
import java.util.HashSet;
import java.util.List;
import java.util.Optional;
import java.util.Set;
import net.fabricmc.fabric.api.event.player.PlayerBlockBreakEvents;
import net.fabricmc.fabric.api.tag.convention.v2.ConventionalBlockTags;
import net.minecraft.block.Block;
import net.minecraft.block.BlockState;
import net.minecraft.block.Blocks;
import net.minecraft.block.CocoaBlock;
import net.minecraft.block.CropBlock;
import net.minecraft.block.FrostedIceBlock;
import net.minecraft.block.IceBlock;
import net.minecraft.block.InfestedBlock;
import net.minecraft.block.NetherWartBlock;
import net.minecraft.block.entity.BlockEntity;
import net.minecraft.enchantment.Enchantment;
import net.minecraft.enchantment.Enchantments;
import net.minecraft.entity.Entity;
import net.minecraft.entity.ExperienceOrbEntity;
import net.minecraft.entity.player.PlayerEntity;
import net.minecraft.fluid.FluidState;
import net.minecraft.fluid.Fluids;
import net.minecraft.item.ItemStack;
import net.minecraft.network.packet.s2c.play.WorldEventS2CPacket;
import net.minecraft.particle.ParticleTypes;
import net.minecraft.recipe.RecipeEntry;
import net.minecraft.recipe.RecipeType;
import net.minecraft.recipe.SmeltingRecipe;
import net.minecraft.recipe.input.SingleStackRecipeInput;
import net.minecraft.registry.RegistryKeys;
import net.minecraft.registry.entry.RegistryEntry;
import net.minecraft.registry.tag.BlockTags;
import net.minecraft.registry.tag.FluidTags;
import net.minecraft.server.network.ServerPlayerEntity;
import net.minecraft.server.world.ServerWorld;
import net.minecraft.sound.SoundCategory;
import net.minecraft.sound.SoundEvents;
import net.minecraft.util.math.BlockPos;
import net.minecraft.util.math.Direction;
import net.minecraft.util.math.MathHelper;
import net.minecraft.util.math.Vec3d;
import net.minecraft.util.math.random.Random;
import net.minecraft.world.WorldEvents;
import org.jetbrains.annotations.Nullable;

/**
 * What grafts do on mining gear.
 * Pickaxes, shovels, hoes and axes take grafts; each gives its element's effect and its creature's ability, scaled by
 * the roll and the stacking weight like any graft. Lasting ones are attribute modifiers ({@link Grafting}): Corrosion
 * (Toxic), Aqua Lung (drowned), Frenzy (baby zombie), Long Reach (skeleton). The rest run here, from events that
 * happen anyway (a block's drops rolled, a block broken), never per tick:
 * <ul>
 * <li>drops ({@link #modifyDrops}, from {@code BlockMixin} on vanilla's drop roll, so any mod's block works): Silk
 * Thread (spider) and Ice Cutter (stray) keep the block whole, Prospector (zombie villager) adds one to an ore and
 * Bountiful (bogged) to a ripe crop, Smelting Touch (Ignited) smelts, with the furnace's experience;</li>
 * <li>after the break ({@link #afterBreak}): Cold Snap (Permafrost) hardens the lava it touched and freezes the water,
 * Gnaw (zombie) feeds the miner on ore, Blast Mining (creeper) blasts out more of the ore's vein, Tunnel Sense (cave
 * spider) points toward a nearby ore;</li>
 * <li>speed ({@link #breakSpeed}, from {@code PlayerEntityMixin}): Sandsifter (husk) on sand, gravel and dirt.</li>
 * </ul>
 * None makes something from nothing: an ore that drops itself (kept whole by Silk Thread or Silk Touch, or ancient
 * debris) never gets one more, since placed and mined again it would multiply; nor does an unripe crop, which gives
 * back its seed; Silk Thread leaves alone blocks where Silk Touch does more than change the drops (a beehive's bees,
 * melting ice, an infested block's silverfish, any block entity); Blast Mining breaks only ore that was there, through
 * vanilla's own break, and Cold Snap asks the same break event first, so claims, spawn protection and every mod's
 * hooks apply. The mining-gear fuzz ({@code ToolGraftFuzzGameTest}) holds them to these rules.
 */
public final class ToolGrafts {
	/** No roll of chances ever makes a chance certain. */
	static final float MAX_CHANCE = 0.75F;
	/** Tunnel Sense chimes at most this often. */
	public static final int TUNNEL_SENSE_COOLDOWN = 120;
	/** After feeling for ore and finding none, Tunnel Sense waits this long: each feel reads up to 1331 blocks. */
	public static final int TUNNEL_SENSE_RETRY = 10;

	/** Set while Blast Mining breaks a vein, so the ore it breaks does not blast in turn. Server thread only. */
	private static boolean blasting;

	private ToolGrafts() {
	}

	public static void register() {
		PlayerBlockBreakEvents.AFTER.register((world, player, pos, state, blockEntity) -> {
			if (world instanceof ServerWorld server && player instanceof ServerPlayerEntity miner) {
				afterBreak(server, miner, pos, state);
			}
		});
	}

	/** Chance that a drop comes out smelted. */
	public static float smeltChance(List<Grafting.Weighted> grafts) {
		return chance(grafts, null, Element.IGNITED, 0.15F, 0.35F);
	}

	/** Chance that an ore drops one more of its main drop. */
	public static float prospectChance(List<Grafting.Weighted> grafts) {
		return chance(grafts, SoulKind.ZOMBIE_VILLAGER, null, 0.1F, 0.2F);
	}

	/** Chance that a ripe crop drops one more. */
	public static float bountyChance(List<Grafting.Weighted> grafts) {
		return chance(grafts, SoulKind.BOGGED, null, 0.1F, 0.25F);
	}

	/** Chance that an ore blasts out more of its vein. */
	public static float blastChance(List<Grafting.Weighted> grafts) {
		return chance(grafts, SoulKind.CREEPER, null, 0.08F, 0.12F);
	}

	/** Chance that a block drops itself. */
	public static float silkChance(List<Grafting.Weighted> grafts) {
		return chance(grafts, SoulKind.SPIDER, null, 0.08F, 0.17F);
	}

	/** How much faster sand, gravel and dirt break: 1 for no husk graft. */
	public static float sandsifter(List<Grafting.Weighted> grafts) {
		return 1.0F + sum(grafts, SoulKind.HUSK, null, 0.3F, 0.5F);
	}

	/** Blocks of ore the blast takes out beyond the one mined. */
	public static int blastCount(List<Grafting.Weighted> grafts) {
		return 2 + Math.round(2 * best(grafts, SoulKind.CREEPER, null));
	}

	/** How far Tunnel Sense reaches, in blocks: 0 for no cave spider graft. */
	public static int tunnelRange(List<Grafting.Weighted> grafts) {
		float power = best(grafts, SoulKind.CAVE_SPIDER, null);
		return power < 0 ? 0 : 3 + Math.round(2 * power);
	}

	/** Saturation Gnaw gives per ore. */
	public static float gnaw(List<Grafting.Weighted> grafts) {
		return sum(grafts, SoulKind.ZOMBIE, null, 0.5F, 1.0F);
	}

	/** Ice Cutter: a stray graft on mining gear takes ice whole and leaves no water where it stood. */
	public static boolean cutsIce(ItemStack tool) {
		if (!tool.contains(SoulComponents.GRAFTS) || !Grafting.isTool(tool)) {
			return false;
		}
		for (Graft graft : Grafting.grafts(tool)) {
			if (graft.kind() == SoulKind.STRAY) {
				return true;
			}
		}
		return false;
	}

	/**
	 * A block's drops, as a grafted tool changes them: kept whole (Silk Thread, Ice Cutter), one more (Prospector,
	 * Bountiful), then smelted (Smelting Touch). Every other tool takes vanilla's drops untouched. {@code random}
	 * rolls the chances (the world's in play; tests force the rolls); the Silk Touch roll of the drops uses the world's.
	 */
	public static List<ItemStack> modifyDrops(BlockState state, ServerWorld world, BlockPos pos, BlockEntity blockEntity, Entity entity, ItemStack tool,
			List<ItemStack> drops, Random random) {
		if (!Grafting.isTool(tool)) {
			return drops;
		}
		List<Grafting.Weighted> grafts = Grafting.weighted(Grafting.grafts(tool));
		if (grafts.isEmpty()) {
			return drops;
		}
		// a copy (another mod's list may not change) without the empty stacks a count rolled as 0 leaves: they drop nothing
		List<ItemStack> result = new ArrayList<>(drops.size());
		for (ItemStack stack : drops) {
			if (!stack.isEmpty()) {
				result.add(stack);
			}
		}
		boolean whole = false;
		if (state.isIn(BlockTags.ICE) && !state.isOf(Blocks.FROSTED_ICE) && has(grafts, SoulKind.STRAY)) {
			result = new ArrayList<>(List.of(new ItemStack(state.getBlock())));
			whole = true;
		} else if (silkThreadTakes(state, blockEntity) && random.nextFloat() < silkChance(grafts)) {
			result = silkDrops(state, world, pos, blockEntity, entity, tool);
			result.removeIf(ItemStack::isEmpty);
			whole = true;
		}
		if (!result.isEmpty()) {
			if (state.isIn(ConventionalBlockTags.ORES)) {
				// never one more of an ore that drops itself: placed and mined again, it would multiply
				if (!whole && !result.get(0).isOf(state.getBlock().asItem()) && random.nextFloat() < prospectChance(grafts)) {
					result.get(0).increment(1);
				}
			} else if (isRipe(state) && random.nextFloat() < bountyChance(grafts)) {
				result.get(0).increment(1);
			}
		}
		float smelt = smeltChance(grafts);
		if (smelt > 0) {
			float experience = 0;
			List<ItemStack> smelted = new ArrayList<>(result.size());
			for (ItemStack stack : result) {
				Optional<RecipeEntry<SmeltingRecipe>> recipe = random.nextFloat() < smelt
						? world.getRecipeManager().getFirstMatch(RecipeType.SMELTING, new SingleStackRecipeInput(stack.copyWithCount(1)), world)
						: Optional.empty();
				ItemStack output = recipe.map(entry -> entry.value().getResult(world.getRegistryManager())).orElse(ItemStack.EMPTY);
				if (output.isEmpty()) {
					smelted.add(stack);
					continue;
				}
				// as many as the furnace makes of the stack, in whole stacks
				for (int left = output.getCount() * stack.getCount(); left > 0; left -= output.getMaxCount()) {
					smelted.add(output.copyWithCount(Math.min(left, output.getMaxCount())));
				}
				experience += recipe.get().value().getExperience() * stack.getCount();
			}
			result = smelted;
			if (experience > 0) {
				int orbs = MathHelper.floor(experience);
				ExperienceOrbEntity.spawn(world, Vec3d.ofCenter(pos), orbs + (random.nextFloat() < experience - orbs ? 1 : 0));
				world.spawnParticles(ParticleTypes.FLAME, pos.getX() + 0.5, pos.getY() + 0.5, pos.getZ() + 0.5, 6, 0.25, 0.25, 0.25, 0.02);
			}
		}
		return result;
	}

	/**
	 * Whether Silk Thread may keep this block whole: not where Silk Touch does more than change the drops. A block entity
	 * may hold what breaking it lets out (a beehive's bees would come out and stay in the hive), melting ice would leave
	 * its water and the ice, an infested block its silverfish and the stone.
	 */
	private static boolean silkThreadTakes(BlockState state, @Nullable BlockEntity blockEntity) {
		Block block = state.getBlock();
		return blockEntity == null && !state.hasBlockEntity() && !(block instanceof IceBlock) && !(block instanceof InfestedBlock);
	}

	/** The drops a Silk Touch copy of the tool would roll: the copy carries no grafts, so this cannot come back here. */
	private static List<ItemStack> silkDrops(BlockState state, ServerWorld world, BlockPos pos, BlockEntity blockEntity, Entity entity, ItemStack tool) {
		RegistryEntry<Enchantment> silkTouch = world.getRegistryManager().get(RegistryKeys.ENCHANTMENT).getEntry(Enchantments.SILK_TOUCH).orElse(null);
		if (silkTouch == null) {
			return new ArrayList<>();
		}
		ItemStack silk = tool.copy();
		silk.remove(SoulComponents.GRAFTS);
		silk.addEnchantment(silkTouch, 1);
		return new ArrayList<>(Block.getDroppedStacks(state, world, pos, blockEntity, entity, silk));
	}

	/**
	 * After a grafted tool breaks a block: Cold Snap, Gnaw, Blast Mining, Tunnel Sense. Fabric calls this once the
	 * block is gone and before its drops fall.
	 */
	static void afterBreak(ServerWorld world, ServerPlayerEntity player, BlockPos pos, BlockState state) {
		ItemStack tool = player.getMainHandStack();
		if (!Grafting.isTool(tool)) {
			return;
		}
		List<Grafting.Weighted> grafts = Grafting.weighted(Grafting.grafts(tool));
		if (grafts.isEmpty()) {
			return;
		}
		if (hasElement(grafts, Element.PERMAFROST)) {
			coldSnap(world, player, pos);
		}
		if (state.isIn(ConventionalBlockTags.ORES)) {
			float saturation = gnaw(grafts);
			if (saturation > 0) {
				// HungerManager#add gives saturation of twice food times the modifier
				player.getHungerManager().add(1, saturation / 2.0F);
			}
			float blast = blastChance(grafts);
			if (blast > 0 && !blasting && world.getRandom().nextFloat() < blast) {
				blastVein(world, player, pos, state, tool, blastCount(grafts));
			}
		}
		int range = tunnelRange(grafts);
		if (range > 0 && isStone(state)) {
			tunnelSense(world, player, pos, range);
		}
	}

	/**
	 * Lava touching the broken block hardens (a source to obsidian, a flow to cobblestone); still water freezes to
	 * Frost Walker's ice, which melts back in the light. Only where the miner may build: spawn protection, and the break
	 * event claim mods answer, asked for each block before it changes.
	 */
	static void coldSnap(ServerWorld world, ServerPlayerEntity player, BlockPos pos) {
		for (Direction direction : Direction.values()) {
			BlockPos at = pos.offset(direction);
			BlockState there = world.getBlockState(at);
			FluidState fluid = there.getFluidState();
			boolean lava = fluid.isIn(FluidTags.LAVA);
			if (!lava && !fluid.isOf(Fluids.WATER) || !there.isOf(fluid.getBlockState().getBlock())) {
				// no lava or still water, or a waterlogged block rather than the fluid itself: leave it be
				continue;
			}
			if (!world.canPlayerModifyAt(player, at) || !PlayerBlockBreakEvents.BEFORE.invoker().beforeBlockBreak(world, player, at, there, null)) {
				continue;
			}
			if (lava) {
				world.setBlockState(at, fluid.isStill() ? Blocks.OBSIDIAN.getDefaultState() : Blocks.COBBLESTONE.getDefaultState());
				world.syncWorldEvent(WorldEvents.LAVA_EXTINGUISHED, at, 0);
			} else {
				world.setBlockState(at, Blocks.FROSTED_ICE.getDefaultState().with(FrostedIceBlock.AGE, 0));
				world.scheduleBlockTick(at, Blocks.FROSTED_ICE, MathHelper.nextInt(world.getRandom(), 60, 120));
				world.spawnParticles(ParticleTypes.SNOWFLAKE, at.getX() + 0.5, at.getY() + 0.8, at.getZ() + 0.5, 5, 0.3, 0.1, 0.3, 0.01);
			}
		}
	}

	/**
	 * The ore's vein bursts: up to {@code count} more of the same block, nearest first, break through vanilla's own
	 * break with the tool (its grafts and enchantments apply, durability wears as usual, claims and spawn protection
	 * hold). The burst spreads only from blocks it broke, never through one a claim kept, and stops short of breaking
	 * the tool, so the block being mined still drops.
	 */
	static void blastVein(ServerWorld world, ServerPlayerEntity player, BlockPos origin, BlockState ore, ItemStack tool, int count) {
		ArrayDeque<BlockPos> open = new ArrayDeque<>();
		Set<BlockPos> seen = new HashSet<>();
		seen.add(origin);
		reach(world, origin, ore, seen, open);
		int broken = 0;
		blasting = true;
		try {
			while (!open.isEmpty() && broken < count) {
				if (player.getMainHandStack() != tool || tool.isEmpty() || tool.isDamageable() && tool.getMaxDamage() - tool.getDamage() <= 2) {
					break;
				}
				BlockPos at = open.poll();
				BlockState state = world.getBlockState(at);
				if (state.isOf(ore.getBlock()) && player.interactionManager.tryBreakBlock(at)) {
					broken++;
					// vanilla shows a player's own break only to the others: the miner did not break these by hand
					player.networkHandler.sendPacket(new WorldEventS2CPacket(WorldEvents.BLOCK_BROKEN, at, Block.getRawIdFromState(state), false));
					reach(world, at, ore, seen, open);
				}
			}
		} finally {
			blasting = false;
		}
		if (broken == 0) {
			return;
		}
		Vec3d center = Vec3d.ofCenter(origin);
		world.spawnParticles(ParticleTypes.EXPLOSION, center.x, center.y, center.z, 1, 0.0, 0.0, 0.0, 0.0);
		world.playSound(null, origin, SoundEvents.ENTITY_GENERIC_EXPLODE.value(), SoundCategory.BLOCKS, 0.45F, 1.7F);
	}

	/** Queues the blocks of {@code ore} beside {@code at} not seen yet. */
	private static void reach(ServerWorld world, BlockPos at, BlockState ore, Set<BlockPos> seen, ArrayDeque<BlockPos> open) {
		for (Direction direction : Direction.values()) {
			BlockPos next = at.offset(direction);
			if (seen.add(next) && world.getBlockState(next).isOf(ore.getBlock())) {
				open.add(next);
			}
		}
	}

	/**
	 * Mining stone near an ore, a cave spider graft feels it: a soft chime for the miner alone, pitched by how near it
	 * is, and a short trail of sparks from the broken block toward it. Not the ore's place: the way to dig.
	 */
	static void tunnelSense(ServerWorld world, ServerPlayerEntity player, BlockPos pos, int range) {
		Long quietUntil = player.getAttached(EvolutaAttachments.TUNNEL_SENSE);
		long now = world.getTime();
		if (quietUntil != null && now < quietUntil) {
			return;
		}
		BlockPos nearest = null;
		double best = Double.MAX_VALUE;
		BlockPos.Mutable at = new BlockPos.Mutable();
		for (int dx = -range; dx <= range; dx++) {
			for (int dy = -range; dy <= range; dy++) {
				for (int dz = -range; dz <= range; dz++) {
					at.set(pos.getX() + dx, pos.getY() + dy, pos.getZ() + dz);
					double distance = dx * dx + dy * dy + dz * dz;
					if (distance < best && world.getBlockState(at).isIn(ConventionalBlockTags.ORES)) {
						best = distance;
						nearest = at.toImmutable();
					}
				}
			}
		}
		if (nearest == null) {
			player.setAttached(EvolutaAttachments.TUNNEL_SENSE, now + TUNNEL_SENSE_RETRY);
			return;
		}
		player.setAttached(EvolutaAttachments.TUNNEL_SENSE, now + TUNNEL_SENSE_COOLDOWN);
		Vec3d from = Vec3d.ofCenter(pos);
		Vec3d toward = Vec3d.ofCenter(nearest).subtract(from).normalize();
		for (int i = 1; i <= 4; i++) {
			Vec3d spark = from.add(toward.multiply(i * 0.35));
			world.spawnParticles(player, ParticleTypes.ELECTRIC_SPARK, false, spark.x, spark.y, spark.z, 2, 0.03, 0.03, 0.03, 0.0);
		}
		float closeness = 1.0F - (float) Math.sqrt(best) / (range + 1);
		player.playSoundToPlayer(SoundEvents.BLOCK_AMETHYST_BLOCK_CHIME, SoundCategory.PLAYERS, 0.8F, 0.8F + 0.8F * closeness);
	}

	/** Sandsifter: sand, gravel, dirt and the rest of the shovel's blocks break faster. Runs on both sides. */
	public static float breakSpeed(PlayerEntity player, BlockState state, float speed) {
		ItemStack tool = player.getMainHandStack();
		if (!tool.contains(SoulComponents.GRAFTS) || !state.isIn(BlockTags.SHOVEL_MINEABLE) || !Grafting.isTool(tool)) {
			return speed;
		}
		return speed * sandsifter(Grafting.weighted(Grafting.grafts(tool)));
	}

	/** A crop grown to harvest, any mod's included when it builds on vanilla's crop. */
	private static boolean isRipe(BlockState state) {
		Block block = state.getBlock();
		if (block instanceof CropBlock crop) {
			return crop.isMature(state);
		}
		if (block instanceof NetherWartBlock) {
			return state.get(NetherWartBlock.AGE) >= NetherWartBlock.MAX_AGE;
		}
		if (block instanceof CocoaBlock) {
			return state.get(CocoaBlock.AGE) >= CocoaBlock.MAX_AGE;
		}
		return false;
	}

	private static boolean isStone(BlockState state) {
		return state.isIn(BlockTags.BASE_STONE_OVERWORLD) || state.isIn(BlockTags.BASE_STONE_NETHER);
	}

	private static boolean has(List<Grafting.Weighted> grafts, SoulKind kind) {
		for (Grafting.Weighted graft : grafts) {
			if (graft.graft().kind() == kind) {
				return true;
			}
		}
		return false;
	}

	private static boolean hasElement(List<Grafting.Weighted> grafts, Element element) {
		for (Grafting.Weighted graft : grafts) {
			if (graft.graft().element() == element) {
				return true;
			}
		}
		return false;
	}

	/** Sum over the grafts of this kind or element of (base + scale x power) x weight. */
	private static float sum(List<Grafting.Weighted> grafts, SoulKind kind, Element element, float base, float scale) {
		float total = 0;
		for (Grafting.Weighted graft : grafts) {
			if ((kind != null && graft.graft().kind() == kind) || (element != null && graft.graft().element() == element)) {
				total += (base + scale * graft.power()) * graft.weight();
			}
		}
		return total;
	}

	private static float chance(List<Grafting.Weighted> grafts, SoulKind kind, Element element, float base, float scale) {
		return Math.min(MAX_CHANCE, sum(grafts, kind, element, base, scale));
	}

	/** The strongest power among the grafts of this kind or element, or -1 when there are none. */
	private static float best(List<Grafting.Weighted> grafts, SoulKind kind, Element element) {
		float best = -1;
		for (Grafting.Weighted graft : grafts) {
			if ((kind != null && graft.graft().kind() == kind) || (element != null && graft.graft().element() == element)) {
				best = Math.max(best, graft.power());
			}
		}
		return best;
	}
}
