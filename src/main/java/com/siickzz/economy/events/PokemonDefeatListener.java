package com.siickzz.economy.events;

import com.siickzz.economy.economy.EconomyManager;
import net.fabricmc.fabric.api.entity.event.v1.ServerEntityCombatEvents;
import net.minecraft.entity.Entity;
import net.minecraft.entity.LivingEntity;
import net.minecraft.server.MinecraftServer;
import net.minecraft.server.network.ServerPlayerEntity;
import net.minecraft.server.world.ServerWorld;
import net.minecraft.text.Text;

import java.lang.reflect.Method;
import java.util.Locale;
import java.util.UUID;

public final class PokemonDefeatListener {
	private PokemonDefeatListener() {
	}

	public static void register() {
		ServerEntityCombatEvents.AFTER_KILLED_OTHER_ENTITY.register(PokemonDefeatListener::onKill);
	}

	private static void onKill(ServerWorld world, Entity attacker, LivingEntity killed) {
		if (world == null || killed == null) {
			return;
		}

		Object killedPokemon = getPokemonObject(killed);
		if (killedPokemon == null) {
			return;
		}

		ServerPlayerEntity player = resolvePlayer(world.getServer(), attacker);
		if (player == null) {
			return;
		}

		int level = readLevel(killedPokemon);
		double reward = PokemonRewardUtil.rewardForDefeat(killedPokemon);

		EconomyManager.get(player).add(reward);
		player.sendMessage(
			Text.literal("§a+ " + formatMoney(reward) + " ₽ §7pour avoir vaincu §f" + safeName(killedPokemon, killed) + levelSuffix(level)),
			false
		);
	}

	private static String levelSuffix(int level) {
		if (level > 0) {
			return " §8(lvl " + level + ")";
		}
		return "";
	}

	private static Object getPokemonObject(LivingEntity entity) {
		// Cobblemon PokemonEntity has getPokemon(), but we keep it reflective.
		Object pokemon = callNoArg(entity, "getPokemon");
		if (pokemon != null) {
			return pokemon;
		}
		String cn = entity.getClass().getName();
		if (cn.contains("cobblemon") && cn.toLowerCase(Locale.ROOT).contains("pokemon")) {
			// Try common alternatives
			pokemon = callNoArg(entity, "pokemon");
			if (pokemon != null) return pokemon;
		}
		return null;
	}

	private static ServerPlayerEntity resolvePlayer(MinecraftServer server, Entity attacker) {
		if (server == null || attacker == null) {
			return null;
		}
		if (attacker instanceof ServerPlayerEntity sp) {
			return sp;
		}

		// Try owner object
		Object owner = callNoArg(attacker, "getOwner");
		if (owner instanceof ServerPlayerEntity sp) {
			return sp;
		}

		// Try owner uuid methods
		UUID ownerUuid = readUuid(callNoArg(attacker, "getOwnerUuid"));
		if (ownerUuid == null) ownerUuid = readUuid(callNoArg(attacker, "getOwnerUUID"));
		if (ownerUuid == null) ownerUuid = readUuid(callNoArg(attacker, "getOwnerId"));
		if (ownerUuid != null) {
			return server.getPlayerManager().getPlayer(ownerUuid);
		}

		// If attacker is a PokemonEntity, try attacker.getPokemon().getOwnerUuid()
		Object attackerPokemon = callNoArg(attacker, "getPokemon");
		if (attackerPokemon != null) {
			ownerUuid = readUuid(callNoArg(attackerPokemon, "getOwnerUuid"));
			if (ownerUuid == null) ownerUuid = readUuid(callNoArg(attackerPokemon, "getOwnerUUID"));
			if (ownerUuid != null) {
				return server.getPlayerManager().getPlayer(ownerUuid);
			}
		}

		return null;
	}

	private static UUID readUuid(Object v) {
		if (v instanceof UUID u) return u;
		if (v != null) {
			try {
				return UUID.fromString(v.toString());
			} catch (IllegalArgumentException ignored) {
			}
		}
		return null;
	}

	private static int readLevel(Object pokemon) {
		Object v = callNoArg(pokemon, "getLevel");
		if (v instanceof Number n) return n.intValue();
		v = callNoArg(pokemon, "level");
		if (v instanceof Number n) return n.intValue();
		return -1;
	}

	private static String safeName(Object pokemon, LivingEntity fallbackEntity) {
		Object display = callNoArg(pokemon, "getDisplayName");
		if (display instanceof Text t) return t.getString();
		if (display != null) return display.toString();
		if (fallbackEntity != null) return fallbackEntity.getName().getString();
		return "?";
	}

	private static Object callNoArg(Object target, String methodName) {
		try {
			Method method = target.getClass().getMethod(methodName);
			return method.invoke(target);
		} catch (Throwable ignored) {
			return null;
		}
	}

	private static String formatMoney(double value) {
		if (value == (long) value) {
			return Long.toString((long) value);
		}
		return String.format(Locale.ROOT, "%.2f", value);
	}
}
