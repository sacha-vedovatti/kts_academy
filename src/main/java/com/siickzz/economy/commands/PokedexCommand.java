package com.siickzz.economy.commands;

import com.cobblemon.mod.common.client.pokedex.PokedexType;
import com.cobblemon.mod.common.net.messages.client.ui.PokedexUIPacket;
import com.mojang.brigadier.context.CommandContext;
import net.fabricmc.fabric.api.command.v2.CommandRegistrationCallback;
import net.minecraft.server.command.ServerCommandSource;
import net.minecraft.server.network.ServerPlayerEntity;
import net.minecraft.text.Text;
import net.minecraft.util.Identifier;

import static net.minecraft.server.command.CommandManager.literal;

public final class PokedexCommand {
	private static final Identifier DEFAULT_INIT_SPECIES = Identifier.of("cobblemon", "bulbasaur");
	private static final PokedexType DEFAULT_POKEDEX_TYPE = PokedexType.BLUE;

	private PokedexCommand() {
	}

	public static void register() {
		CommandRegistrationCallback.EVENT.register((dispatcher, registryAccess, environment) -> {
			dispatcher.register(
				literal("pokedex")
					.executes(PokedexCommand::open)
			);
		});
	}

	private static int open(CommandContext<ServerCommandSource> ctx) throws com.mojang.brigadier.exceptions.CommandSyntaxException {
		ServerPlayerEntity player = ctx.getSource().getPlayerOrThrow();

		try {
			new PokedexUIPacket(
				DEFAULT_POKEDEX_TYPE,
				DEFAULT_INIT_SPECIES,
				player.getBlockPos()
			).sendToPlayer(player);
		} catch (Throwable t) {
			player.sendMessage(Text.literal("§cImpossible d'ouvrir le Pokédex (Cobblemon)."), false);
			return 0;
		}

		return 1;
	}
}
