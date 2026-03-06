package com.siickzz.ktsacademy.events;

import com.siickzz.ktsacademy.economy.EconomyManager;
import com.siickzz.ktsacademy.quests.QuestManager;
import net.minecraft.server.MinecraftServer;
import net.minecraft.server.network.ServerPlayerEntity;
import net.minecraft.text.Text;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.lang.reflect.Field;
import java.lang.reflect.InvocationHandler;
import java.lang.reflect.Method;
import java.lang.reflect.Proxy;
import java.util.Locale;
import java.util.UUID;

/**
 * Awards money only when a Cobblemon Pokémon battle is won.
 * Uses reflection so it stays compatible across Cobblemon versions.
 */
public final class PokemonBattleWinListener {
	private static final Logger LOGGER = LoggerFactory.getLogger("CobbleEconomy");
	private static volatile boolean LOGGED_HANDLER_ERROR = false;
	private static volatile boolean LOGGED_EVENT_SHAPE = false;

	private PokemonBattleWinListener() {
	}

	public static void register() {
		// Try a bunch of likely event names across Cobblemon versions.
		String[] candidates = {
			"BATTLE_VICTORY_POST",
			"BATTLE_VICTORY_PRE",
			"BATTLE_VICTORY",
			"BATTLE_WON_POST",
			"BATTLE_WON_PRE",
			"BATTLE_WON",
			"BATTLE_WIN_POST",
			"BATTLE_WIN_PRE",
			"BATTLE_WIN",
			"BATTLE_ENDED_POST",
			"BATTLE_ENDED_PRE",
			"BATTLE_ENDED",
			"BATTLE_END_POST",
			"BATTLE_END_PRE",
			"BATTLE_END",
			"BATTLE_FINISHED_POST",
			"BATTLE_FINISHED_PRE",
			"BATTLE_FINISHED",
			"BATTLE_COMPLETED_POST",
			"BATTLE_COMPLETED_PRE",
			"BATTLE_COMPLETED"
		};

		boolean hooked = false;
		String hookedName = null;
		String hookedHolder = null;
		for (String name : candidates) {
			HookResult r = tryHookCobblemonEvents(name);
			hooked = r.hooked;
			if (hooked) {
				hookedName = name;
				hookedHolder = r.holderClass;
				break;
			}
		}

		if (hooked) {
			LOGGER.info("[CobbleEconomy] Battle win listener hooked successfully ({} via {})", hookedName, hookedHolder);
		} else {
			LOGGER.warn("[CobbleEconomy] Could not hook Cobblemon battle events; no money will be awarded on battle wins.");
		}
	}

	private record HookResult(boolean hooked, String holderClass) {
	}

	private static HookResult tryHookCobblemonEvents(String name) {
		// Cobblemon has moved/duplicated event holders across versions.
		String[] holders = {
			"com.cobblemon.mod.common.api.events.CobblemonEvents",
			"com.cobblemon.mod.common.api.events.battle.BattleEvents",
			"com.cobblemon.mod.common.api.events.battle.BattleEventHandler",
			"com.cobblemon.mod.common.api.events.battle.BattleEventHandlers"
		};
		for (String holder : holders) {
			if (tryHookEvent(holder, name)) {
				return new HookResult(true, holder);
			}
		}
		return new HookResult(false, null);
	}

	private static boolean tryHookEvent(String eventHolderClassName, String eventFieldName) {
		try {
			Class<?> holder = Class.forName(eventHolderClassName);
			Object event = getStaticMember(holder, eventFieldName);
			if (event == null) {
				return false;
			}

			Method registerMethod = findRegisterMethod(event);
			Class<?> listenerType = registerMethod.getParameterTypes()[0];

			Object listener = Proxy.newProxyInstance(
				listenerType.getClassLoader(),
				new Class<?>[]{listenerType},
				new BattleWinHandler()
			);

			registerMethod.invoke(event, listener);
			return true;
		} catch (Throwable ignored) {
			return false;
		}
	}

	private static Object getStaticMember(Class<?> holder, String name) {
		try {
			Field field = holder.getField(name);
			return field.get(null);
		} catch (Throwable ignored) {
		}

		try {
			Field field = holder.getDeclaredField(name);
			field.setAccessible(true);
			return field.get(null);
		} catch (Throwable ignored) {
		}

		String getter = "get" + name;
		try {
			Method method = holder.getMethod(getter);
			return method.invoke(null);
		} catch (Throwable ignored) {
		}
		try {
			Method method = holder.getDeclaredMethod(getter);
			method.setAccessible(true);
			return method.invoke(null);
		} catch (Throwable ignored) {
		}
		return null;
	}

	private static Method findRegisterMethod(Object event) throws NoSuchMethodException {
		String[] candidates = {"register", "subscribe", "add", "listen"};

		for (String name : candidates) {
			for (Method method : event.getClass().getMethods()) {
				if (!method.getName().equals(name)) continue;
				if (method.getParameterCount() != 1) continue;
				return method;
			}
		}
		for (Method method : event.getClass().getMethods()) {
			if (method.getParameterCount() != 1) continue;
			String n = method.getName().toLowerCase(Locale.ROOT);
			if (n.contains("subscr") || n.contains("regist") || n.contains("listen")) return method;
		}

		throw new NoSuchMethodException("No listener registration method found on: " + event.getClass());
	}

	private static final class BattleWinHandler implements InvocationHandler {
		@Override
		public Object invoke(Object proxy, Method method, Object[] args) {
			if (args == null || args.length == 0 || args[0] == null) {
				return defaultReturn(method);
			}

			Object event = args[0];
			try {
				ServerPlayerEntity player = resolvePlayer(event);
				if (player == null) {
					return defaultReturn(method);
				}

				if (!LOGGED_EVENT_SHAPE) {
					LOGGED_EVENT_SHAPE = true;
					LOGGER.info("[CobbleEconomy] Battle event class: {}", event.getClass().getName());
				}

				/* DISABLED */
//				Object defeatedPokemon = resolveDefeatedPokemon(event);
//				int level = readLevel(defeatedPokemon);
//				double reward = defeatedPokemon != null
//					? PokemonRewardUtil.rewardForDefeat(defeatedPokemon)
//					: rewardForLevel(level);
//				EconomyManager.get(player).add(reward);
//				player.sendMessage(Text.literal("§a+ " + formatMoney(reward) + " ₽ §7(victoire combat)" + levelSuffix(level)), true);

				QuestManager.onBattleWon(player);
			} catch (Throwable t) {
				if (!LOGGED_HANDLER_ERROR) {
					LOGGED_HANDLER_ERROR = true;
					LOGGER.warn("[CobbleEconomy] Battle win handler error (logged once)", t);
				}
			}

			return defaultReturn(method);
		}

		private static Object defaultReturn(Method method) {
			Class<?> rt = method.getReturnType();
			if (rt == boolean.class) return false;
			if (rt == byte.class) return (byte) 0;
			if (rt == short.class) return (short) 0;
			if (rt == int.class) return 0;
			if (rt == long.class) return 0L;
			if (rt == float.class) return 0f;
			if (rt == double.class) return 0d;
			if (rt == char.class) return (char) 0;
			return null;
		}
	}

	private static ServerPlayerEntity resolvePlayer(Object event) {
		Object v = callNoArg(event, "getPlayer");
		if (v instanceof ServerPlayerEntity sp) return sp;

		v = callNoArg(event, "getWinner");
		if (v instanceof ServerPlayerEntity sp) return sp;

		v = callNoArg(event, "getWinningPlayer");
		if (v instanceof ServerPlayerEntity sp) return sp;

		Object battle = callNoArg(event, "getBattle");
		if (battle != null) {
			v = callNoArg(battle, "getWinningPlayer");
			if (v instanceof ServerPlayerEntity sp) return sp;
			v = callNoArg(battle, "getWinner");
			if (v instanceof ServerPlayerEntity sp) return sp;

			// Some versions expose participants lists
			v = callNoArg(battle, "getPlayers");
			ServerPlayerEntity fromList = firstPlayerFromIterable(v);
			if (fromList != null) return fromList;
			v = callNoArg(battle, "getParticipants");
			fromList = firstPlayerFromIterable(v);
			if (fromList != null) return fromList;
		}

		// Event-level participants lists
		v = callNoArg(event, "getPlayers");
		ServerPlayerEntity fromList = firstPlayerFromIterable(v);
		if (fromList != null) return fromList;
		v = callNoArg(event, "getParticipants");
		fromList = firstPlayerFromIterable(v);
		if (fromList != null) return fromList;

		// UUID fallback
		UUID uuid = readUuid(callNoArg(event, "getPlayerUuid"));
		if (uuid == null) uuid = readUuid(callNoArg(event, "getWinnerUuid"));
		if (uuid == null) uuid = readUuid(callNoArg(event, "getWinningPlayerUuid"));
		if (uuid == null && battle != null) uuid = readUuid(callNoArg(battle, "getWinningPlayerUuid"));
		if (uuid != null) {
			MinecraftServer server = resolveServer(event);
			if (server != null) {
				return server.getPlayerManager().getPlayer(uuid);
			}
		}

		return null;
	}

	private static ServerPlayerEntity firstPlayerFromIterable(Object maybeIterable) {
		if (maybeIterable == null) return null;
		if (maybeIterable instanceof Iterable<?> it) {
			for (Object o : it) {
				if (o instanceof ServerPlayerEntity sp) return sp;
				// Some APIs wrap player objects
				Object p = callNoArg(o, "getPlayer");
				if (p instanceof ServerPlayerEntity sp2) return sp2;
			}
		}
		return null;
	}

	private static MinecraftServer resolveServer(Object event) {
		Object v = callNoArg(event, "getServer");
		if (v instanceof MinecraftServer s) return s;
		Object player = callNoArg(event, "getPlayer");
		if (player instanceof ServerPlayerEntity sp) return sp.getServer();
		return null;
	}

	private static Object resolveDefeatedPokemon(Object event) {
		Object v = callNoArg(event, "getDefeatedPokemon");
		if (v != null) return v;
		v = callNoArg(event, "getLoserPokemon");
		if (v != null) return v;
		v = callNoArg(event, "getDefeated");
		if (v != null) return v;

		Object battle = callNoArg(event, "getBattle");
		if (battle != null) {
			v = callNoArg(battle, "getDefeatedPokemon");
			if (v != null) return v;
			v = callNoArg(battle, "getLoserPokemon");
			if (v != null) return v;
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
		if (pokemon == null) return -1;
		Object v = callNoArg(pokemon, "getLevel");
		if (v instanceof Number n) return n.intValue();
		v = callNoArg(pokemon, "level");
		if (v instanceof Number n) return n.intValue();
		return -1;
	}

	/**
	 * Tiered reward by level, with the first tier at 5 Pokédollars.
	 * 1-10 => 5, 11-20 => 10, ...
	 */
	private static double rewardForLevel(int level) {
		int effectiveLevel = Math.max(1, level);
		int tier = (effectiveLevel - 1) / 10;
		return 5.0 + (tier * 5.0);
	}

	private static String levelSuffix(int level) {
		if (level > 0) {
			return " §8(lvl " + level + ")";
		}
		return "";
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
