package com.siickzz.economy.commands;

import com.mojang.brigadier.context.CommandContext;
import com.siickzz.economy.trash.gui.TrashGui;
import net.fabricmc.fabric.api.command.v2.CommandRegistrationCallback;
import net.minecraft.server.command.ServerCommandSource;
import net.minecraft.server.network.ServerPlayerEntity;

import static net.minecraft.server.command.CommandManager.literal;

public final class TrashCommand {
	private TrashCommand() {
	}

	public static void register() {
		CommandRegistrationCallback.EVENT.register((dispatcher, registryAccess, environment) -> {
			dispatcher.register(
				literal("trash")
					.executes(TrashCommand::open)
			);
			dispatcher.register(
				literal("poubelle")
					.executes(TrashCommand::open)
			);
		});
	}

	private static int open(CommandContext<ServerCommandSource> ctx) throws com.mojang.brigadier.exceptions.CommandSyntaxException {
		ServerPlayerEntity player = ctx.getSource().getPlayerOrThrow();
		TrashGui.open(player);
		return 1;
	}
}
