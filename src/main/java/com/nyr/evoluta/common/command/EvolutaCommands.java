package com.nyr.evoluta.common.command;

import static net.minecraft.server.command.CommandManager.argument;
import static net.minecraft.server.command.CommandManager.literal;

import com.mojang.brigadier.arguments.FloatArgumentType;
import com.mojang.brigadier.arguments.StringArgumentType;
import com.mojang.brigadier.builder.LiteralArgumentBuilder;
import com.mojang.brigadier.context.CommandContext;
import com.mojang.brigadier.exceptions.CommandSyntaxException;
import com.mojang.brigadier.exceptions.Dynamic2CommandExceptionType;
import com.mojang.brigadier.exceptions.SimpleCommandExceptionType;
import com.mojang.brigadier.suggestion.SuggestionProvider;
import com.nyr.evoluta.common.combat.HazardBlocks;
import com.nyr.evoluta.common.config.EvolutaConfig;
import com.nyr.evoluta.common.item.EvolutaItems;
import com.nyr.evoluta.common.item.SoulMeatItem;
import com.nyr.evoluta.common.mutation.Archetype;
import com.nyr.evoluta.common.mutation.Element;
import com.nyr.evoluta.common.mutation.MutationData;
import com.nyr.evoluta.common.mutation.Mutations;
import com.nyr.evoluta.common.mutation.Tier;
import com.nyr.evoluta.common.soul.Graft;
import com.nyr.evoluta.common.soul.Grafting;
import com.nyr.evoluta.common.soul.MutantGear;
import com.nyr.evoluta.common.soul.SoulKind;
import com.nyr.evoluta.common.world.EvolutaWorlds;
import com.nyr.evoluta.common.world.WorldState;
import java.util.ArrayList;
import java.util.List;
import java.util.stream.Stream;
import net.fabricmc.fabric.api.command.v2.CommandRegistrationCallback;
import net.minecraft.command.CommandSource;
import net.minecraft.command.argument.EntityArgumentType;
import net.minecraft.entity.Entity;
import net.minecraft.entity.LivingEntity;
import net.minecraft.entity.mob.MobEntity;
import net.minecraft.item.ItemStack;
import net.minecraft.server.command.ServerCommandSource;
import net.minecraft.server.world.ServerWorld;
import net.minecraft.text.Text;
import net.minecraft.util.Hand;

/**
 * {@code /evoluta}: admin tools to make, inspect and clear mutants, graft gear and reload the config. Needs permission
 * level 2.
 */
public final class EvolutaCommands {
	private static final Dynamic2CommandExceptionType UNKNOWN = new Dynamic2CommandExceptionType(
			(what, value) -> Text.translatable("commands.evoluta.unknown", what, value));
	private static final SimpleCommandExceptionType NO_MOBS = new SimpleCommandExceptionType(Text.translatable("commands.evoluta.no_mobs"));
	private static final SimpleCommandExceptionType NO_GEAR = new SimpleCommandExceptionType(Text.translatable("commands.evoluta.graft.none"));

	private static final SuggestionProvider<ServerCommandSource> TIERS = (context, builder) ->
			CommandSource.suggestMatching(Stream.of(Tier.values()).map(Tier::key), builder);
	private static final SuggestionProvider<ServerCommandSource> ELEMENTS = (context, builder) ->
			CommandSource.suggestMatching(Stream.concat(Stream.of(Element.NONE), Element.IMPLEMENTED.stream()).map(Element::key), builder);
	private static final SuggestionProvider<ServerCommandSource> ARCHETYPES = (context, builder) ->
			CommandSource.suggestMatching(Stream.concat(Stream.of(Archetype.NONE), Archetype.IMPLEMENTED.stream()).map(Archetype::key), builder);
	private static final SuggestionProvider<ServerCommandSource> KINDS = (context, builder) ->
			CommandSource.suggestMatching(SoulKind.ALL.stream().map(SoulKind::key), builder);
	/** Only elements with Soul Meat can be grafted. */
	private static final SuggestionProvider<ServerCommandSource> MEAT_ELEMENTS = (context, builder) ->
			CommandSource.suggestMatching(Element.IMPLEMENTED.stream().map(Element::key), builder);

	private EvolutaCommands() {
	}

	public static void register() {
		CommandRegistrationCallback.EVENT.register((dispatcher, registryAccess, environment) -> dispatcher.register(root()));
	}

	static LiteralArgumentBuilder<ServerCommandSource> root() {
		return literal("evoluta")
				.requires(source -> source.hasPermissionLevel(2))
				.then(literal("mutate").then(argument("targets", EntityArgumentType.entities())
						.then(argument("tier", StringArgumentType.word()).suggests(TIERS)
								.executes(context -> mutate(context, false, false))
								.then(argument("element", StringArgumentType.word()).suggests(ELEMENTS)
										.executes(context -> mutate(context, true, false))
										.then(argument("archetype", StringArgumentType.word()).suggests(ARCHETYPES)
												.executes(context -> mutate(context, true, true)))))))
				.then(literal("clear").then(argument("targets", EntityArgumentType.entities())
						.executes(EvolutaCommands::clear)))
				.then(literal("graft").then(argument("targets", EntityArgumentType.entities())
						.then(argument("kind", StringArgumentType.word()).suggests(KINDS)
								.then(argument("element", StringArgumentType.word()).suggests(MEAT_ELEMENTS)
										.then(argument("grade", StringArgumentType.word()).suggests(TIERS)
												.executes(context -> graft(context, false))
												.then(argument("power", FloatArgumentType.floatArg(0.0F, 1.0F))
														.executes(context -> graft(context, true))))))))
				.then(literal("inspect").then(argument("target", EntityArgumentType.entity())
						.executes(EvolutaCommands::inspect)))
				.then(literal("reload")
						.executes(EvolutaCommands::reload))
				.then(literal("stats")
						.executes(EvolutaCommands::stats));
	}

	private static int mutate(CommandContext<ServerCommandSource> context, boolean hasElement, boolean hasArchetype)
			throws CommandSyntaxException {
		String tierKey = StringArgumentType.getString(context, "tier");
		Tier tier = Tier.byKey(tierKey);
		if (tier == null) {
			throw UNKNOWN.create("tier", tierKey);
		}
		Element element = Element.NONE;
		if (hasElement) {
			String key = StringArgumentType.getString(context, "element");
			element = Element.byKey(key);
			if (element == null || (element != Element.NONE && !Element.IMPLEMENTED.contains(element))) {
				throw UNKNOWN.create("element", key);
			}
		}
		Archetype archetype = Archetype.NONE;
		if (hasArchetype) {
			String key = StringArgumentType.getString(context, "archetype");
			archetype = Archetype.byKey(key);
			if (archetype == null || (archetype != Archetype.NONE && !Archetype.IMPLEMENTED.contains(archetype))) {
				throw UNKNOWN.create("archetype", key);
			}
		}
		MutationData data = MutationData.of(tier, element, archetype);
		List<MobEntity> mobs = mobs(EntityArgumentType.getEntities(context, "targets"));
		for (MobEntity mob : mobs) {
			Mutations.apply(mob, data, EvolutaConfig.get());
			Mutations.equipLoadout(mob, data);
			// a champion's gear carries its mutation, as it does when one spawns
			MutantGear.graftOnce(mob, data, mob.getRandom());
		}
		context.getSource().sendFeedback(() -> Text.translatable("commands.evoluta.mutate.success", mobs.size(), Mutations.describe(data)), true);
		return mobs.size();
	}

	/**
	 * Grafts Soul Meat of a kind, element and grade onto the gear each target holds in its main hand, as the Soul Forge
	 * would: at {@code power}, or rolled in the grade's range. Gear that takes no more grafts is skipped.
	 */
	private static int graft(CommandContext<ServerCommandSource> context, boolean hasPower) throws CommandSyntaxException {
		String kindKey = StringArgumentType.getString(context, "kind");
		SoulKind kind = SoulKind.byKey(kindKey);
		if (kind == null) {
			throw UNKNOWN.create("kind", kindKey);
		}
		String elementKey = StringArgumentType.getString(context, "element");
		Element element = Element.byKey(elementKey);
		SoulMeatItem meat = element == null ? null : EvolutaItems.soulMeat(element, kind);
		if (meat == null) {
			throw UNKNOWN.create("element", elementKey);
		}
		String gradeKey = StringArgumentType.getString(context, "grade");
		Tier grade = Tier.byKey(gradeKey);
		if (grade == null) {
			throw UNKNOWN.create("grade", gradeKey);
		}
		int grafted = 0;
		for (Entity entity : EntityArgumentType.getEntities(context, "targets")) {
			if (entity instanceof LivingEntity living && Grafting.canGraft(living.getMainHandStack())) {
				float power = hasPower ? FloatArgumentType.getFloat(context, "power") : Graft.roll(grade, living.getRandom());
				living.setStackInHand(Hand.MAIN_HAND, Grafting.graft(living.getMainHandStack(), meat, grade, power));
				grafted++;
			}
		}
		if (grafted == 0) {
			throw NO_GEAR.create();
		}
		int count = grafted;
		context.getSource().sendFeedback(() -> Text.translatable("commands.evoluta.graft.success", new ItemStack(meat).getName(),
				Text.translatable("evoluta.tier." + grade.key()), count), true);
		return count;
	}

	private static int clear(CommandContext<ServerCommandSource> context) throws CommandSyntaxException {
		int cleared = 0;
		for (MobEntity mob : mobs(EntityArgumentType.getEntities(context, "targets"))) {
			if (Mutations.clear(mob)) {
				cleared++;
			}
		}
		int count = cleared;
		context.getSource().sendFeedback(() -> Text.translatable("commands.evoluta.clear.success", count), true);
		return count;
	}

	private static int inspect(CommandContext<ServerCommandSource> context) throws CommandSyntaxException {
		Entity target = EntityArgumentType.getEntity(context, "target");
		MutationData data = Mutations.get(target);
		if (data == null || !(target instanceof MobEntity mob)) {
			context.getSource().sendFeedback(() -> Text.translatable("commands.evoluta.inspect.plain", target.getDisplayName()), false);
			return 0;
		}
		String health = "%.1f/%.1f".formatted(mob.getHealth(), mob.getMaxHealth());
		context.getSource().sendFeedback(() -> Text.translatable("commands.evoluta.inspect.mutated",
				target.getDisplayName(), Mutations.describe(data), health), false);
		return data.tier();
	}

	private static int reload(CommandContext<ServerCommandSource> context) {
		List<String> warnings = EvolutaConfig.load(true);
		ServerCommandSource source = context.getSource();
		source.sendFeedback(() -> Text.translatable("commands.evoluta.reload.success", warnings.size()), true);
		for (String warning : warnings) {
			source.sendFeedback(() -> Text.translatable("commands.evoluta.reload.warning", warning), false);
		}
		return warnings.isEmpty() ? 1 : 0;
	}

	/** Per world: what is loaded, and what Evoluta's server logic cost per tick over the last 200 ticks. */
	private static int stats(CommandContext<ServerCommandSource> context) {
		ServerCommandSource source = context.getSource();
		int worlds = 0;
		for (ServerWorld world : source.getServer().getWorlds()) {
			WorldState state = EvolutaWorlds.peek(world);
			if (state == null) {
				continue;
			}
			worlds++;
			String name = world.getRegistryKey().getValue().toString();
			String median = "%.1f".formatted(state.meter.percentileMicros(50));
			String p99 = "%.1f".formatted(state.meter.percentileMicros(99));
			String max = "%.1f".formatted(state.meter.maxMicros());
			String average = "%.1f".formatted(state.meter.averageMicros());
			source.sendFeedback(() -> Text.translatable("commands.evoluta.stats.world", name, state.loaded.size(),
					state.champions.size(), HazardBlocks.count(world), state.meter.ticks(), median, p99, max, average), false);
		}
		if (worlds == 0) {
			source.sendFeedback(() -> Text.translatable("commands.evoluta.stats.none"), false);
		}
		return worlds;
	}

	private static List<MobEntity> mobs(Iterable<? extends Entity> entities) throws CommandSyntaxException {
		List<MobEntity> mobs = new ArrayList<>();
		for (Entity entity : entities) {
			if (entity instanceof MobEntity mob) {
				mobs.add(mob);
			}
		}
		if (mobs.isEmpty()) {
			throw NO_MOBS.create();
		}
		return mobs;
	}
}
