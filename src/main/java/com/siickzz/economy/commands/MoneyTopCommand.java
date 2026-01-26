package com.siickzz.economy.commands;

import com.mojang.brigadier.arguments.IntegerArgumentType;
import com.mojang.brigadier.context.CommandContext;
import com.siickzz.economy.economy.EconomyManager;
import net.fabricmc.fabric.api.command.v2.CommandRegistrationCallback;
import net.minecraft.server.MinecraftServer;
import net.minecraft.server.command.ServerCommandSource;
import net.minecraft.server.network.ServerPlayerEntity;
import net.minecraft.text.Text;

import java.util.List;
import java.util.Locale;
import java.util.UUID;

import static net.minecraft.server.command.CommandManager.argument;
import static net.minecraft.server.command.CommandManager.literal;

public final class MoneyTopCommand {
	private MoneyTopCommand() {
	}

	public static void register() {
		CommandRegistrationCallback.EVENT.register((dispatcher, registryAccess, environment) -> {
			dispatcher.register(
				literal("leaderboard")
					.executes(ctx -> show(ctx, 10))
					.then(argument("limit", IntegerArgumentType.integer(1, 50))
						.executes(ctx -> show(ctx, IntegerArgumentType.getInteger(ctx, "limit")))
					)
			);

			dispatcher.register(
				literal("moneytop")
					.executes(ctx -> show(ctx, 10))
					.then(argument("limit", IntegerArgumentType.integer(1, 50))
						.executes(ctx -> show(ctx, IntegerArgumentType.getInteger(ctx, "limit")))
					)
			);

			// Avoid conflicting with vanilla /scoreboard. Provide an alias under /economy.
			dispatcher.register(
				literal("economy")
					.then(literal("scoreboard")
						.executes(ctx -> show(ctx, 10))
						.then(argument("limit", IntegerArgumentType.integer(1, 50))
							.executes(ctx -> show(ctx, IntegerArgumentType.getInteger(ctx, "limit")))
						)
					)
			);
		});
	}

	private static int show(CommandContext<ServerCommandSource> ctx, int limit) {
		ServerCommandSource src = ctx.getSource();
		MinecraftServer server = src.getServer();

		List<EconomyManager.BalanceEntry> top = EconomyManager.topBalances(limit);
		if (top.isEmpty()) {
			src.sendFeedback(() -> Text.literal("§7Aucun compte trouvé."), false);
			return 0;
		}

		ServerPlayerEntity viewer = null;
		try {
			viewer = src.getPlayer();
		} catch (Exception ignored) {
		}

		Text header = Text.literal("§6--- Leaderboard Argent (Top " + top.size() + ") ---");
		if (viewer != null) {
			viewer.sendMessage(header, false);
		} else {
			src.sendFeedback(() -> header, false);
		}

		for (int i = 0; i < top.size(); i++) {
			EconomyManager.BalanceEntry e = top.get(i);
			String name = resolveName(server, e.uuid());
			String line = String.format(Locale.ROOT, "§e#%d §f%s §7- §6%s ₽", i + 1, name, formatMoney(e.balance()));
			Text msg = Text.literal(line);
			if (viewer != null) {
				viewer.sendMessage(msg, false);
			} else {
				src.sendFeedback(() -> msg, false);
			}
		}

		return top.size();
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

	private static String formatMoney(double value) {
		if (value == (long) value) {
			return Long.toString((long) value);
		}
		return String.format(Locale.ROOT, "%.2f", value);
	}
}
