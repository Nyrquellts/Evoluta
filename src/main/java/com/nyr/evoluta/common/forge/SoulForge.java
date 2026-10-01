package com.nyr.evoluta.common.forge;

import com.mojang.serialization.MapCodec;
import com.nyr.evoluta.common.Evoluta;
import net.minecraft.block.AbstractBlock;
import net.minecraft.block.Block;
import net.minecraft.block.BlockState;
import net.minecraft.block.MapColor;
import net.minecraft.entity.player.PlayerEntity;
import net.minecraft.item.BlockItem;
import net.minecraft.item.Item;
import net.minecraft.particle.ParticleTypes;
import net.minecraft.registry.Registries;
import net.minecraft.registry.Registry;
import net.minecraft.resource.featuretoggle.FeatureFlags;
import net.minecraft.screen.NamedScreenHandlerFactory;
import net.minecraft.screen.ScreenHandlerContext;
import net.minecraft.screen.ScreenHandlerType;
import net.minecraft.screen.SimpleNamedScreenHandlerFactory;
import net.minecraft.sound.BlockSoundGroup;
import net.minecraft.text.Text;
import net.minecraft.util.ActionResult;
import net.minecraft.util.hit.BlockHitResult;
import net.minecraft.util.math.BlockPos;
import net.minecraft.util.math.random.Random;
import net.minecraft.world.World;

/**
 * The Soul Forge, where Soul Meat is grafted onto gear. Built from vanilla block textures (crying obsidian top,
 * chiseled blackstone sides), it glows faintly and breathes soul-fire wisps; it keeps nothing, like a smithing table.
 */
public final class SoulForge extends Block {
	public static final MapCodec<SoulForge> CODEC = createCodec(SoulForge::new);
	private static final Text TITLE = Text.translatable("container.evoluta.soul_forge");

	public static final Block BLOCK = Registry.register(Registries.BLOCK, Evoluta.id("soul_forge"), new SoulForge(AbstractBlock.Settings.create()
			.mapColor(MapColor.BLACK).requiresTool().strength(3.5F, 6.0F).sounds(BlockSoundGroup.LODESTONE).luminance(state -> 7)));
	public static final Item ITEM = Registry.register(Registries.ITEM, Evoluta.id("soul_forge"), new BlockItem(BLOCK, new Item.Settings()));
	public static final ScreenHandlerType<SoulForgeScreenHandler> SCREEN = Registry.register(Registries.SCREEN_HANDLER, Evoluta.id("soul_forge"),
			new ScreenHandlerType<>(SoulForgeScreenHandler::new, FeatureFlags.VANILLA_FEATURES));

	public SoulForge(Settings settings) {
		super(settings);
	}

	/** Loads this class, which registers the block, its item and its screen. */
	public static void register() {
	}

	@Override
	protected MapCodec<SoulForge> getCodec() {
		return CODEC;
	}

	@Override
	protected ActionResult onUse(BlockState state, World world, BlockPos pos, PlayerEntity player, BlockHitResult hit) {
		if (!world.isClient) {
			player.openHandledScreen(state.createScreenHandlerFactory(world, pos));
		}
		return ActionResult.success(world.isClient);
	}

	@Override
	protected NamedScreenHandlerFactory createScreenHandlerFactory(BlockState state, World world, BlockPos pos) {
		return new SimpleNamedScreenHandlerFactory((syncId, inventory, player) ->
				new SoulForgeScreenHandler(syncId, inventory, ScreenHandlerContext.create(world, pos)), TITLE);
	}

	@Override
	public void randomDisplayTick(BlockState state, World world, BlockPos pos, Random random) {
		if (random.nextInt(3) == 0) {
			world.addParticle(ParticleTypes.SOUL_FIRE_FLAME, pos.getX() + 0.2 + random.nextDouble() * 0.6, pos.getY() + 1.05,
					pos.getZ() + 0.2 + random.nextDouble() * 0.6, 0.0, 0.02, 0.0);
		}
		if (random.nextInt(8) == 0) {
			world.addParticle(ParticleTypes.SOUL, pos.getX() + 0.5, pos.getY() + 1.1, pos.getZ() + 0.5, 0.0, 0.03, 0.0);
		}
	}
}
