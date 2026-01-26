/*
** EPITECH PROJECT, 2026
** Economy
** File description:
** Harvest Listener class file
*/

package com.siickzz.economy.events;

import com.siickzz.economy.quests.QuestManager;
import net.minecraft.item.Item;
import net.minecraft.registry.Registries;
import net.minecraft.server.network.ServerPlayerEntity;
import net.minecraft.util.Identifier;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.lang.reflect.Field;
import java.lang.reflect.InvocationHandler;
import java.lang.reflect.Method;
import java.lang.reflect.Proxy;
import java.util.UUID;

public final class HarvestListener {
	private static final Logger LOGGER = LoggerFactory.getLogger("CobbleEconomy");
	private static volatile boolean LOGGED_COBBLEMON_HOOK_ERROR = false;
	private static volatile boolean LOGGED_APRICORN_EVENT_RECEIVED = false;
	private static final java.util.Map<UUID, Long> LAST_APRICORN_EVENT_LOG_MS = new java.util.concurrent.ConcurrentHashMap<>();
	private static final long EVENT_LOG_MS = 2000L;

	private HarvestListener() {}

	public static void register()
	{
		if (tryHookCobblemonApricornHarvest())
			LOGGER.info("[CobbleEconomy] Harvest listener hooked successfully (APRICORN_HARVESTED)");
		LOGGER.info("[CobbleEconomy] Harvest listener registered");
	}

	private static boolean tryHookCobblemonApricornHarvest()
	{
		return tryHookEvent("com.cobblemon.mod.common.api.events.CobblemonEvents", "APRICORN_HARVESTED");
	}

	private static boolean tryHookEvent(String eventHolderClassName, String eventFieldName)
	{
		try {
			Class<?> holder = Class.forName(eventHolderClassName);
			Object event = getStaticMember(holder, eventFieldName);

			if (event == null) {
				LOGGER.warn("[CobbleEconomy] Cobblemon event field not found: {}.{}", eventHolderClassName, eventFieldName);
				return false;
			}

			boolean ok = trySubscribe(event);
	
			if (ok)
				LOGGER.warn("[CobbleEconomy] Hooked Cobblemon event {}.{} via {}", eventHolderClassName, eventFieldName, event.getClass().getName());
			return ok;
		} catch (Throwable t) {
			if (!LOGGED_COBBLEMON_HOOK_ERROR) {
				LOGGED_COBBLEMON_HOOK_ERROR = true;
				LOGGER.warn("[CobbleEconomy] Could not hook Cobblemon APRICORN_HARVESTED event", t);
			}
			return false;
		}
	}

	private static boolean trySubscribe(Object event)
	{
		if (event == null)
			return false;

		Method[] methods = event.getClass().getMethods();
		String[] nameHints = {"subscribe", "register", "listen", "add"};

		for (String hint : nameHints) {
			for (Method method : methods) {
				if (!method.getName().equals(hint))
					continue;
				if (method.getParameterCount() != 1)
					continue;
				if (tryInvokeSubscribe(event, method))
					return true;
			}
		}
		for (Method method : methods) {
			if (method.getParameterCount() != 1)
				continue;

			String n = method.getName().toLowerCase(java.util.Locale.ROOT);

			if (!(n.contains("subscr") || n.contains("regist") || n.contains("listen") || n.contains("add")))
				continue;
			if (tryInvokeSubscribe(event, method))
				return true;
		}
		LOGGER.warn("[CobbleEconomy] No usable subscribe/register method found on {}", event.getClass().getName());
		return false;
	}

	private static boolean tryInvokeSubscribe(Object event, Method registerMethod)
	{
		try {
			Class<?> listenerType = registerMethod.getParameterTypes()[0];
			Object listener = Proxy.newProxyInstance(
				listenerType.getClassLoader(),
				new Class<?>[]{listenerType},
				new ApricornHarvestHandler()
			);

			registerMethod.invoke(event, listener);
			LOGGER.warn("[CobbleEconomy] Subscribed to {} using {}({})", event.getClass().getName(), registerMethod.getName(), listenerType.getName());
			return true;
		} catch (Throwable t) {
			LOGGER.debug("[CobbleEconomy] Subscribe attempt failed on {}#{}", event.getClass().getName(), registerMethod.getName(), t);
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

	// Kept for backwards compatibility with older builds; superseded by trySubscribe().
	@SuppressWarnings("unused")
	private static Method findRegisterMethod(Object event) throws NoSuchMethodException
	{
		throw new NoSuchMethodException("Superseded by trySubscribe()");
	}

	private static final class ApricornHarvestHandler implements InvocationHandler
	{
		@Override
		public Object invoke(Object proxy, Method method, Object[] args) {
			Object event = (args == null || args.length == 0) ? null : args[0];
			try {
				if (!LOGGED_APRICORN_EVENT_RECEIVED && event != null) {
					LOGGED_APRICORN_EVENT_RECEIVED = true;
					LOGGER.info("[CobbleEconomy] Received Cobblemon ApricornHarvestEvent: {}", event.getClass().getName());
				}

				Object playerObj = callNoArg(event, "getPlayer");

				if (playerObj instanceof ServerPlayerEntity player) {
					String itemId = resolveApricornItemId(event);
					if (itemId == null || itemId.isBlank()) {
						itemId = "apricorn";
					}

					QuestManager.onHarvested(player, itemId, 1);
					// Rate-limited log so we can confirm it fires on the server.
					long now = System.currentTimeMillis();
					UUID id = player.getUuid();
					Long last = LAST_APRICORN_EVENT_LOG_MS.get(id);
					if (last == null || now - last > EVENT_LOG_MS) {
						LAST_APRICORN_EVENT_LOG_MS.put(id, now);
						LOGGER.warn("[CobbleEconomy] Apricorn harvested (event): player={} item={}", player.getName().getString(), itemId);
					}
				}
			} catch (Throwable ignored) {}
			return defaultReturn(method);
		}

		private static String resolveApricornItemId(Object event) {
			Object apricorn = callNoArg(event, "getApricorn");
			Object itemObj = firstNonNull(
				callNoArg(apricorn, "item"),
				callNoArg(apricorn, "getItem")
			);
			if (itemObj instanceof Item item) {
				Identifier id = Registries.ITEM.getId(item);
				return id == null ? null : id.toString();
			}
			return "apricorn";
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
			try {
				Class<?> unit = Class.forName("kotlin.Unit");
				if (rt == unit || rt == Object.class) {
					Field f = unit.getField("INSTANCE");
					return f.get(null);
				}
			} catch (Throwable ignored) {
			}
			return null;
		}
	}

	private static Object firstNonNull(Object... values) {
		if (values == null) return null;
		for (Object v : values) if (v != null) return v;
		return null;
	}

	private static Object callNoArg(Object target, String methodName) {
		if (target == null) return null;
		try {
			Method method = target.getClass().getMethod(methodName);
			return method.invoke(target);
		} catch (Throwable ignored) {
			return null;
		}
	}

	// Compatibility helpers (older builds/mixins may still reference these).
	public static void onApricornBlockInteracted(ServerPlayerEntity player) {
		// No-op: we now count only via Cobblemon APRICORN_HARVESTED.
	}

	public static void onApricornPickedUp(ServerPlayerEntity player, String itemId, int amount) {
		// No-op: we now count only via Cobblemon APRICORN_HARVESTED.
	}

	public static boolean shouldSkipApricornPickup(ServerPlayerEntity player) {
		return false;
	}

}
