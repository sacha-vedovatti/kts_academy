package com.siickzz.ktsacademy.events;

import com.siickzz.ktsacademy.economy.EconomyManager;
import com.siickzz.ktsacademy.economy.PlayerAccount;
import com.siickzz.ktsacademy.quests.QuestManager;
import net.minecraft.server.network.ServerPlayerEntity;
import net.minecraft.text.Text;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.lang.reflect.Field;
import java.lang.reflect.InvocationHandler;
import java.lang.reflect.Method;
import java.lang.reflect.Proxy;
import java.util.Locale;
import java.util.Map;
import java.util.UUID;

public final class PokedexMilestoneListener {
	private static final Logger LOGGER = LoggerFactory.getLogger("CobbleEconomy");
	private static volatile boolean LOGGED_HANDLER_ERROR = false;
	private static volatile boolean LOGGED_EVENT_SHAPE = false;
	private static volatile boolean LOGGED_PLAYER_LOOKUP_FAILURE = false;

	private static final int POKEMON_PER_TIER = 50;
	private static final double REWARD_PER_TIER = 500.0;

	private PokedexMilestoneListener() {
	}

	public static void register() {
		String[] candidates = {
			"POKEDEX_DATA_CHANGED",
			"POKEDEX_DATA_CHANGED_POST",
			"POKEDEX_DATA_CHANGED_PRE",
			"POKEDEX_CHANGED_POST",
			"POKEDEX_CHANGED_PRE",
			"POKEDEX_CHANGED",
			"POKEDEX_UPDATED_POST",
			"POKEDEX_UPDATED_PRE",
			"POKEDEX_UPDATED"
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
			LOGGER.info("[CobbleEconomy] Pokedex milestone listener hooked successfully ({} via {})", hookedName, hookedHolder);
		} else {
			LOGGER.warn("[CobbleEconomy] Could not hook pokedex change events; no money will be awarded on pokedex tiers.");
		}
	}

	private record HookResult(boolean hooked, String holderClass) {
	}

	private static HookResult tryHookCobblemonEvents(String name) {
		String[] holders = {
			"com.cobblemon.mod.common.api.events.CobblemonEvents",
			"com.cobblemon.mod.common.api.events.pokedex.PokedexEvents",
			"com.cobblemon.mod.common.api.events.pokedex.PokedexEventHandler",
			"com.cobblemon.mod.common.api.events.pokedex.PokedexEventHandlers"
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
			Object observable = getStaticMember(holder, eventFieldName);
			if (observable == null) {
				return false;
			}

			Method subscribeMethod = findSubscribeMethod(observable);
			Class<?> listenerType = subscribeMethod.getParameterTypes()[0];

			Object listener = Proxy.newProxyInstance(
				listenerType.getClassLoader(),
				new Class<?>[]{listenerType},
				new PokedexHandler()
			);

			subscribeMethod.invoke(observable, listener);
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

	private static Method findSubscribeMethod(Object observable) throws NoSuchMethodException {
		String[] candidates = {"subscribe", "register", "add", "listen"};
		for (String name : candidates) {
			for (Method method : observable.getClass().getMethods()) {
				if (!method.getName().equals(name)) {
					continue;
				}
				if (method.getParameterCount() != 1) {
					continue;
				}
				return method;
			}
		}

		for (Method method : observable.getClass().getMethods()) {
			if (method.getParameterCount() != 1) {
				continue;
			}
			String n = method.getName().toLowerCase(Locale.ROOT);
			if (n.contains("subscr") || n.contains("regist") || n.contains("listen")) {
				return method;
			}
		}

		throw new NoSuchMethodException("No subscribe/register method on: " + observable.getClass());
	}

	private static final class PokedexHandler implements InvocationHandler {
		@Override
		public Object invoke(Object proxy, Method method, Object[] args) {
			if (args == null || args.length == 0 || args[0] == null) {
				return defaultReturn(method);
			}

			Object event = args[0];
			try {
				Object playerObj = callNoArg(event, "getPlayer");
				if (!(playerObj instanceof ServerPlayerEntity player)) {
					return defaultReturn(method);
				}

				if (!LOGGED_EVENT_SHAPE) {
					LOGGED_EVENT_SHAPE = true;
					LOGGER.info("[CobbleEconomy] Pokedex event class: {}", event.getClass().getName());
				}

				// Always prefer the same source as /pokeboard.
				int caughtCount = computeCaughtCountForPlayer(player);
				if (caughtCount <= 0) {
					// Fallback: some event types expose a direct count.
					caughtCount = computeCaughtCount(event);
				}
				applyPokedexUpdate(player, caughtCount);
			} catch (Throwable t) {
				if (!LOGGED_HANDLER_ERROR) {
					LOGGED_HANDLER_ERROR = true;
					LOGGER.warn("[CobbleEconomy] Pokedex handler error (logged once)", t);
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

	public static void applyPokedexUpdate(ServerPlayerEntity player, int caughtCount) {
		if (player == null) return;

		// Keep pokedex rewards/quests consistent with /pokeboard.
		int fromApi = computeCaughtCountForPlayer(player);
		if (fromApi > 0) {
			caughtCount = fromApi;
		}
		if (caughtCount <= 0) return;

		// Progress quests (pokedex caught count)
		QuestManager.onPokedexCaughtProgress(player, caughtCount);

		// Reward milestone tiers
		int tier = caughtCount / POKEMON_PER_TIER;
		PlayerAccount account = EconomyManager.get(player);
		int lastTier = account.getPokedexRewardTier();
		if (tier <= lastTier) return;

		int gainedTiers = tier - lastTier;
		double reward = gainedTiers * REWARD_PER_TIER;
		account.setPokedexRewardTier(tier);
		account.add(reward);

		player.sendMessage(
			Text.literal("§a+ " + formatMoney(reward) + " ₽ §7(Pokédex: " + (tier * POKEMON_PER_TIER) + " capturés)"),
			false
		);
	}

	private static int computeCaughtCount(Object pokedexEvent) {
		// Cobblemon 1.7.x uses PokedexDataChangedEvent which may expose direct counts.
		int direct = extractCaughtCount(firstNonNull(
			callNoArg(pokedexEvent, "getCaughtCount"),
			callNoArg(pokedexEvent, "caughtCount"),
			callNoArg(pokedexEvent, "getCaught"),
			callNoArg(pokedexEvent, "caught")
		));
		if (direct > 0) return direct;

		Object manager = firstNonNull(
			callNoArg(pokedexEvent, "getPokedexManager"),
			callNoArg(pokedexEvent, "getPokedex"),
			callNoArg(pokedexEvent, "getNewPokedexManager"),
			callNoArg(pokedexEvent, "getNewPokedex"),
			callNoArg(pokedexEvent, "pokedexManager"),
			callNoArg(pokedexEvent, "pokedex")
		);
		return computeCaughtCountFromManager(manager);
	}

	public static int computeCaughtCountForPlayer(ServerPlayerEntity player) {
		if (player == null) return -1;

		try {
			Object managerDirect = tryGetPokedexManagerDirect(player);
			int direct = computeCaughtCountFromManager(managerDirect);
			if (direct >= 0) return direct;

			Object playerData = tryGetCobblemonPlayerData(player);
			if (playerData == null) {
				if (!LOGGED_PLAYER_LOOKUP_FAILURE) {
					LOGGED_PLAYER_LOOKUP_FAILURE = true;
					LOGGER.warn("[CobbleEconomy] Could not resolve Cobblemon player data for pokedex count (logged once)");
				}
				return -1;
			}

			Object manager = tryGetPokedexManagerFromPlayerData(playerData);
			return computeCaughtCountFromManager(manager);
		} catch (Throwable t) {
			if (!LOGGED_HANDLER_ERROR) {
				LOGGED_HANDLER_ERROR = true;
				LOGGER.warn("[CobbleEconomy] computeCaughtCountForPlayer error (logged once)", t);
			}
			return -1;
		}
	}

	public static int computeCaughtCountForUuid(UUID uuid) {
		if (uuid == null) return -1;
		try {
			Object managerDirect = tryGetPokedexManagerDirect(uuid);
			int direct = computeCaughtCountFromManager(managerDirect);
			if (direct >= 0) return direct;

			Object playerData = tryGetCobblemonPlayerData(uuid);
			if (playerData == null) {
				return -1;
			}
			Object manager = tryGetPokedexManagerFromPlayerData(playerData);
			return computeCaughtCountFromManager(manager);
		} catch (Throwable t) {
			if (!LOGGED_HANDLER_ERROR) {
				LOGGED_HANDLER_ERROR = true;
				LOGGER.warn("[CobbleEconomy] computeCaughtCountForUuid error (logged once)", t);
			}
			return -1;
		}
	}

	private static Object tryGetPokedexManagerDirect(ServerPlayerEntity player) {
		if (player == null) return null;
		try {
			Class<?> cobblemonCls = Class.forName("com.cobblemon.mod.common.Cobblemon");
			Object cobblemon = getStaticMember(cobblemonCls, "INSTANCE");
			Object pdm = firstNonNull(
				callNoArg(cobblemon, "getPlayerDataManager"),
				callNoArg(cobblemon, "playerDataManager"),
				getStaticMember(cobblemonCls, "playerDataManager")
			);
			return firstNonNull(
				callOneArg(pdm, "getPokedexData", player),
				callOneArg(pdm, "getPokedex", player),
				callOneArg(pdm, "getPokedexManager", player)
			);
		} catch (Throwable ignored) {
			return null;
		}
	}

	private static Object tryGetPokedexManagerDirect(UUID uuid) {
		if (uuid == null) return null;
		try {
			Class<?> cobblemonCls = Class.forName("com.cobblemon.mod.common.Cobblemon");
			Object cobblemon = getStaticMember(cobblemonCls, "INSTANCE");
			Object pdm = firstNonNull(
				callNoArg(cobblemon, "getPlayerDataManager"),
				callNoArg(cobblemon, "playerDataManager"),
				getStaticMember(cobblemonCls, "playerDataManager")
			);
			return firstNonNull(
				callOneArg(pdm, "getPokedexData", uuid),
				callOneArg(pdm, "getPokedex", uuid),
				callOneArg(pdm, "getPokedexManager", uuid)
			);
		} catch (Throwable ignored) {
			return null;
		}
	}

	private static int computeCaughtCountFromManager(Object manager) {
		if (manager == null) {
			return -1;
		}

		// Prefer explicit count getters if available.
		int direct = extractCaughtCount(firstNonNull(
			callNoArg(manager, "getCaughtCount"),
			callNoArg(manager, "caughtCount"),
			callNoArg(manager, "getCaught"),
			callNoArg(manager, "caught")
		));
		if (direct > 0) return direct;

		Object speciesRecords = firstNonNull(
			callNoArg(manager, "getSpeciesRecords"),
			callNoArg(manager, "speciesRecords"),
			callNoArg(manager, "getRecords"),
			callNoArg(manager, "getEntries")
		);

		Iterable<?> values;
		if (speciesRecords instanceof Map<?, ?> records) {
			values = records.values();
		} else if (speciesRecords instanceof Iterable<?> it) {
			values = it;
		} else {
			return -1;
		}

		Object caughtEnum = getPokedexEntryProgressCaught();
		int count = 0;
		for (Object record : values) {
			if (record == null) continue;
			if (isCaught(record, caughtEnum)) {
				count++;
			}
		}
		return count;
	}

	private static Object tryGetPokedexManagerFromPlayerData(Object playerData) {
		if (playerData == null) return null;

		Object manager = firstNonNull(
			callNoArg(playerData, "getPokedexManager"),
			callNoArg(playerData, "getPokedex"),
			callNoArg(playerData, "pokedexManager"),
			callNoArg(playerData, "pokedex")
		);
		if (manager != null) return manager;

		// Cobblemon 1.7.x: PlayerData is a store of instanced data; pokedex is accessed via PlayerInstancedDataStoreTypes.POKEDEX.
		try {
			Class<?> storeTypes = Class.forName("com.cobblemon.mod.common.api.storage.player.PlayerInstancedDataStoreTypes");
			Object pokedexKey = getStaticMember(storeTypes, "POKEDEX");
			if (pokedexKey != null) {
				manager = firstNonNull(
					callOneArg(playerData, "get", pokedexKey),
					callOneArg(playerData, "getOrCreate", pokedexKey),
					callOneArg(playerData, "getOrMake", pokedexKey),
					callOneArg(playerData, "getInstanced", pokedexKey),
					callOneArg(playerData, "getInstancedData", pokedexKey),
					callOneArg(playerData, "getData", pokedexKey)
				);
				if (manager != null) return manager;
			}
		} catch (Throwable ignored) {
		}

		return null;
	}

	private static int extractCaughtCount(Object maybeCount) {
		if (maybeCount == null) return -1;
		if (maybeCount instanceof Number n) return n.intValue();

		Object v = firstNonNull(
			callNoArg(maybeCount, "getValue"),
			callNoArg(maybeCount, "value"),
			callNoArg(maybeCount, "getCount"),
			callNoArg(maybeCount, "count"),
			callNoArg(maybeCount, "get" )
		);
		if (v instanceof Number n) return n.intValue();

		try {
			return Integer.parseInt(maybeCount.toString());
		} catch (Throwable ignored) {
			return -1;
		}
	}

	private static Object tryGetCobblemonPlayerData(ServerPlayerEntity player) {
		Object cobblemon = null;
		try {
			Class<?> cobblemonCls = Class.forName("com.cobblemon.mod.common.Cobblemon");
			cobblemon = getStaticMember(cobblemonCls, "INSTANCE");
		} catch (Throwable ignored) {
		}

		// 1) Direct methods on Cobblemon.INSTANCE
		Object data = firstNonNull(
			callOneArg(cobblemon, "getPlayerData", player),
			callOneArg(cobblemon, "getPlayerData", player.getUuid()),
			callOneArg(cobblemon, "getPlayerData", player.getUuid().toString()),
			callOneArg(cobblemon, "playerData", player),
			callOneArg(cobblemon, "playerData", player.getUuid())
		);
		if (data != null) return data;

		// 2) Through storage/store-like objects
		Object storage = firstNonNull(
			callNoArg(cobblemon, "getStorage"),
			callNoArg(cobblemon, "storage"),
			callNoArg(cobblemon, "getPlayerDataStore"),
			callNoArg(cobblemon, "playerDataStore"),
			callNoArg(cobblemon, "getPlayerDataManager"),
			callNoArg(cobblemon, "playerDataManager")
		);
		data = firstNonNull(
			callOneArg(storage, "get", player),
			callOneArg(storage, "get", player.getUuid()),
			callOneArg(storage, "get", player.getUuid().toString()),
			callOneArg(storage, "getPlayerData", player),
			callOneArg(storage, "getPlayerData", player.getUuid())
		);
		if (data != null) return data;

		// 3) Static stores (last resort)
		String[] staticStores = {
			"com.cobblemon.mod.common.api.storage.player.PlayerDataStore",
			"com.cobblemon.mod.common.api.storage.player.PlayerDataStoreKt",
			"com.cobblemon.mod.common.api.storage.player.PlayerDataRegistry"
		};
		for (String clsName : staticStores) {
			try {
				Class<?> cls = Class.forName(clsName);
				Object inst = getStaticMember(cls, "INSTANCE");
				data = firstNonNull(
					callOneArg(inst, "get", player),
					callOneArg(inst, "get", player.getUuid()),
					callOneArg(inst, "getPlayerData", player),
					callOneArg(inst, "getPlayerData", player.getUuid())
				);
				if (data != null) return data;
			} catch (Throwable ignored) {
			}
		}

		return null;
	}

	private static Object tryGetCobblemonPlayerData(UUID uuid) {
		Object cobblemon = null;
		try {
			Class<?> cobblemonCls = Class.forName("com.cobblemon.mod.common.Cobblemon");
			cobblemon = getStaticMember(cobblemonCls, "INSTANCE");
		} catch (Throwable ignored) {
		}

		Object data = firstNonNull(
			callOneArg(cobblemon, "getPlayerData", uuid),
			callOneArg(cobblemon, "getPlayerData", uuid.toString()),
			callOneArg(cobblemon, "playerData", uuid),
			callOneArg(cobblemon, "playerData", uuid.toString())
		);
		if (data != null) return data;

		Object storage = firstNonNull(
			callNoArg(cobblemon, "getStorage"),
			callNoArg(cobblemon, "storage"),
			callNoArg(cobblemon, "getPlayerDataStore"),
			callNoArg(cobblemon, "playerDataStore"),
			callNoArg(cobblemon, "getPlayerDataManager"),
			callNoArg(cobblemon, "playerDataManager")
		);
		data = firstNonNull(
			callOneArg(storage, "get", uuid),
			callOneArg(storage, "get", uuid.toString()),
			callOneArg(storage, "getPlayerData", uuid),
			callOneArg(storage, "getPlayerData", uuid.toString())
		);
		if (data != null) return data;

		String[] staticStores = {
			"com.cobblemon.mod.common.api.storage.player.PlayerDataStore",
			"com.cobblemon.mod.common.api.storage.player.PlayerDataStoreKt",
			"com.cobblemon.mod.common.api.storage.player.PlayerDataRegistry"
		};
		for (String clsName : staticStores) {
			try {
				Class<?> cls = Class.forName(clsName);
				Object inst = getStaticMember(cls, "INSTANCE");
				data = firstNonNull(
					callOneArg(inst, "get", uuid),
					callOneArg(inst, "get", uuid.toString()),
					callOneArg(inst, "getPlayerData", uuid),
					callOneArg(inst, "getPlayerData", uuid.toString())
				);
				if (data != null) return data;
			} catch (Throwable ignored) {
			}
		}

		return null;
	}

	private static Object callOneArg(Object target, String methodName, Object arg) {
		if (target == null || arg == null) return null;
		try {
			for (Method method : target.getClass().getMethods()) {
				if (!method.getName().equals(methodName)) continue;
				if (method.getParameterCount() != 1) continue;
				try {
					return method.invoke(target, arg);
				} catch (Throwable ignored) {
				}
			}
		} catch (Throwable ignored) {
		}
		return null;
	}

	private static Object firstNonNull(Object... values) {
		if (values == null) return null;
		for (Object v : values) {
			if (v != null) return v;
		}
		return null;
	}

	private static Object getPokedexEntryProgressCaught() {
		try {
			Class<?> enumCls = Class.forName("com.cobblemon.mod.common.api.pokedex.PokedexEntryProgress");
			// Kotlin enums compile to normal Java enums
			return Enum.valueOf((Class) enumCls, "CAUGHT");
		} catch (Throwable ignored) {
			return null;
		}
	}

	private static boolean isCaught(Object record, Object caughtEnum) {
		Object direct = callNoArg(record, "isCaught");
		if (direct instanceof Boolean b) return b;
		direct = callNoArg(record, "is_caught");
		if (direct instanceof Boolean b) return b;

		if (caughtEnum != null) {
			Object hasAtLeast = callOneArg(record, "hasAtLeast", caughtEnum);
			if (hasAtLeast instanceof Boolean b) return b;
		}

		Object knowledge = callNoArg(record, "getKnowledge");
		if (knowledge instanceof Map<?, ?> m) {
			// Heuristique: si une des valeurs mentionne CAUGHT, on considère capturé
			for (Object v : m.values()) {
				if (v == null) continue;
				String s = v.toString().toUpperCase(Locale.ROOT);
				if (s.contains("CAUGHT")) return true;
			}
		}
		return false;
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
