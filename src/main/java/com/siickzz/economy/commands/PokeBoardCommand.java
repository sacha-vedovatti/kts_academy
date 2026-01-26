package com.siickzz.economy.commands;

import com.mojang.brigadier.arguments.IntegerArgumentType;
import com.mojang.brigadier.context.CommandContext;
import com.siickzz.economy.economy.EconomyManager;
import com.siickzz.economy.events.PokedexMilestoneListener;
import net.fabricmc.fabric.api.command.v2.CommandRegistrationCallback;
import net.minecraft.server.MinecraftServer;
import net.minecraft.server.command.ServerCommandSource;
import net.minecraft.server.network.ServerPlayerEntity;
import net.minecraft.text.Text;

import java.util.ArrayList;
import java.util.Comparator;
import java.util.List;
import java.util.Locale;
import java.util.UUID;

import static net.minecraft.server.command.CommandManager.argument;
import static net.minecraft.server.command.CommandManager.literal;

public final class PokeBoardCommand {
	private PokeBoardCommand() {
	}

	private record Entry(UUID uuid, int caught) {
	}

	public static void register() {
		CommandRegistrationCallback.EVENT.register((dispatcher, registryAccess, environment) -> {
			dispatcher.register(
				literal("pokeboard")
					.executes(ctx -> show(ctx, 10))
					.then(argument("limit", IntegerArgumentType.integer(1, 50))
						.executes(ctx -> show(ctx, IntegerArgumentType.getInteger(ctx, "limit")))
					)
			);
		});
	}

	private static int show(CommandContext<ServerCommandSource> ctx, int limit) {
		ServerCommandSource src = ctx.getSource();
		MinecraftServer server = src.getServer();

		// Include players with economy accounts AND currently online players.
		// Cobblemon player data is always resolvable for online players, but may not be for offline.
		java.util.LinkedHashSet<UUID> uuids = new java.util.LinkedHashSet<>(EconomyManager.knownPlayers());
		for (ServerPlayerEntity p : server.getPlayerManager().getPlayerList()) {
			uuids.add(p.getUuid());
		}

		List<Entry> entries = new ArrayList<>();
		for (UUID uuid : uuids) {
			ServerPlayerEntity online = server.getPlayerManager().getPlayer(uuid);
			int caught = online != null
				? PokedexMilestoneListener.computeCaughtCountForPlayer(online)
				: PokedexMilestoneListener.computeCaughtCountForUuid(uuid);
			if (caught < 0) continue;
			entries.add(new Entry(uuid, caught));
		}

		if (entries.isEmpty()) {
			src.sendFeedback(() -> Text.literal("§7Aucune donnée Pokédex trouvée."), false);
			return 0;
		}

		entries.sort(Comparator.comparingInt(Entry::caught).reversed());
		int safeLimit = Math.max(1, Math.min(limit, 50));
		if (entries.size() > safeLimit) {
			entries = entries.subList(0, safeLimit);
		}

		ServerPlayerEntity viewer = null;
		try {
			viewer = src.getPlayer();
		} catch (Exception ignored) {
		}

		Text header = Text.literal("§6--- PokéBoard (Top " + entries.size() + ") ---");
		send(src, viewer, header);

		for (int i = 0; i < entries.size(); i++) {
			Entry e = entries.get(i);
			String name = resolveName(server, e.uuid());
			String line = String.format(Locale.ROOT, "§e#%d §f%s §7- §a%d §7capturés", i + 1, name, e.caught());
			send(src, viewer, Text.literal(line));
		}

		return entries.size();
	}

	private static void send(ServerCommandSource src, ServerPlayerEntity viewer, Text msg) {
		if (viewer != null) {
			viewer.sendMessage(msg, false);
		} else {
			src.sendFeedback(() -> msg, false);
		}
	}

	private static String resolveName(MinecraftServer server, UUID uuid) {
		if (server == null || uuid == null) return "?";

		ServerPlayerEntity online = server.getPlayerManager().getPlayer(uuid);
		if (online != null) {
			return online.getName().getString();
		}

		try {
			var cache = server.getUserCache();
			if (cache != null) {
				var profile = cache.getByUuid(uuid);
				if (profile != null && profile.isPresent() && profile.get().getName() != null) {
					return profile.get().getName();
				}
			}
		} catch (Throwable ignored) {
		}

		String s = uuid.toString();
		return s.substring(0, Math.min(8, s.length()));
	}
}
