package com.siickzz.ktsacademy.events;

import com.siickzz.ktsacademy.quests.QuestManager;
import net.minecraft.server.network.ServerPlayerEntity;
import net.minecraft.server.MinecraftServer;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.lang.reflect.Field;
import java.lang.reflect.InvocationHandler;
import java.lang.reflect.Method;
import java.lang.reflect.Proxy;
import java.util.Locale;
import java.util.UUID;

public final class PokemonTradeListener {
	private static final Logger LOGGER = LoggerFactory.getLogger("CobbleEconomy");
	private static volatile boolean LOGGED_HANDLER_ERROR = false;
	private static volatile boolean LOGGED_EVENT_SHAPE = false;

	private PokemonTradeListener() {}

	public static void register()
	{
		String[] candidates = {
			"TRADE_COMPLETED_POST", "TRADE_COMPLETED_PRE", "TRADE_COMPLETED", "TRADE_FINISHED_POST", "TRADE_FINISHED_PRE",
			"TRADE_FINISHED", "TRADE_SUCCESS_POST", "TRADE_SUCCESS_PRE", "TRADE_SUCCESS"
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
		if (hooked)
			LOGGER.info("[CobbleEconomy] Trade listener hooked successfully ({} via {})", hookedName, hookedHolder);
		else
			LOGGER.warn("[CobbleEconomy] Could not hook Cobblemon trade events; trade quests will not progress.");
	}

	private record HookResult(boolean hooked, String holderClass) {}

	private static HookResult tryHookCobblemonEvents(String name)
	{
		String[] holders = {
			"com.cobblemon.mod.common.api.events.CobblemonEvents", "com.cobblemon.mod.common.api.events.trade.TradeEvents",
			"com.cobblemon.mod.common.api.events.trade.TradeEventHandler", "com.cobblemon.mod.common.api.events.trade.TradeEventHandlers"
		};

		for (String holder : holders) {
			if (tryHookEvent(holder, name))
				return new HookResult(true, holder);
		}
		return new HookResult(false, null);
	}

	private static boolean tryHookEvent(String eventHolderClassName, String eventFieldName)
	{
		try {
			Class<?> holder = Class.forName(eventHolderClassName);
			Object event = getStaticMember(holder, eventFieldName);
			if (event == null)
				return false;

			Method registerMethod = findRegisterMethod(event);
			Class<?> listenerType = registerMethod.getParameterTypes()[0];
			Object listener = Proxy.newProxyInstance(listenerType.getClassLoader(), new Class<?>[]{listenerType}, new TradeHandler());
			registerMethod.invoke(event, listener);
			return true;
		} catch (Throwable ignored) {
			return false;
		}
	}

	private static Object getStaticMember(Class<?> holder, String name)
	{
		try {
			Field field = holder.getField(name);
			return field.get(null);
		} catch (Throwable ignored) {}
		try {
			Field field = holder.getDeclaredField(name);
			field.setAccessible(true);
			return field.get(null);
		} catch (Throwable ignored) {}

		String getter = "get" + name;
		try {
			Method method = holder.getMethod(getter);
			return method.invoke(null);
		} catch (Throwable ignored) {}
		try {
			Method method = holder.getDeclaredMethod(getter);
			method.setAccessible(true);
			return method.invoke(null);
		} catch (Throwable ignored) {}
		return null;
	}

	private static Method findRegisterMethod(Object event) throws NoSuchMethodException
	{
		String[] candidates = {"register", "subscribe", "add", "listen"};

		for (String name : candidates) {
			for (Method method : event.getClass().getMethods()) {
				if (!method.getName().equals(name))
					continue;
				if (method.getParameterCount() != 1)
					continue;
				return method;
			}
		}
		for (Method method : event.getClass().getMethods()) {
			if (method.getParameterCount() != 1)
				continue;

			String n = method.getName().toLowerCase(Locale.ROOT);
			if (n.contains("subscr") || n.contains("regist") || n.contains("listen"))
				return method;
		}
		throw new NoSuchMethodException("No listener registration method found on: " + event.getClass());
	}

	private static final class TradeHandler implements InvocationHandler {
		@Override
		public Object invoke(Object proxy, Method method, Object[] args) {
			if (args == null || args.length == 0 || args[0] == null)
				return defaultReturn(method);

			Object event = args[0];
			try {
				if (!LOGGED_EVENT_SHAPE) {
					LOGGED_EVENT_SHAPE = true;
					LOGGER.info("[CobbleEconomy] Trade event class: {}", event.getClass().getName());
				}

				ServerPlayerEntity p1 = resolvePlayer(event, "getPlayer", "getPlayer1", "getFirst", "getFirstPlayer", "getInitiator", "getSender");
				ServerPlayerEntity p2 = resolvePlayer(event, "getOtherPlayer", "getPlayer2", "getSecond", "getSecondPlayer", "getReceiver");
				if (p1 == null || p2 == null) {
					MinecraftServer server = resolveServer(event);
					UUID u1 = readUuid(firstNonNull(callNoArg(event, "getPlayerUuid"), callNoArg(event, "getPlayer1Uuid"), callNoArg(event, "getFirstUuid")));
					UUID u2 = readUuid(firstNonNull(callNoArg(event, "getOtherPlayerUuid"), callNoArg(event, "getPlayer2Uuid"), callNoArg(event, "getSecondUuid")));
					if (server != null) {
						if (p1 == null && u1 != null)
							p1 = server.getPlayerManager().getPlayer(u1);
						if (p2 == null && u2 != null)
							p2 = server.getPlayerManager().getPlayer(u2);
					}
				}
				if (p1 != null)
					QuestManager.onTradeCompleted(p1);
				if (p2 != null && (p1 == null || !p2.getUuid().equals(p1.getUuid())))
					QuestManager.onTradeCompleted(p2);
			} catch (Throwable t) {
				if (!LOGGED_HANDLER_ERROR) {
					LOGGED_HANDLER_ERROR = true;
					LOGGER.warn("[CobbleEconomy] Trade handler error (logged once)", t);
				}
			}
			return defaultReturn(method);
		}

		private static ServerPlayerEntity resolvePlayer(Object event, String... methodNames)
		{
			for (String n : methodNames) {
				Object v = callNoArg(event, n);

				if (v instanceof ServerPlayerEntity sp)
					return sp;
			}
			return null;
		}

		private static Object defaultReturn(Method method)
		{
			Class<?> rt = method.getReturnType();

			if (rt == boolean.class)
				return false;
			if (rt == byte.class)
				return (byte) 0;
			if (rt == short.class)
				return (short) 0;
			if (rt == int.class)
				return 0;
			if (rt == long.class)
				return 0L;
			if (rt == float.class)
				return 0f;
			if (rt == double.class)
				return 0d;
			if (rt == char.class)
				return (char) 0;
			return null;
		}
	}

	private static MinecraftServer resolveServer(Object event)
	{
		Object v = callNoArg(event, "getServer");
		if (v instanceof MinecraftServer s)
			return s;

		Object player = callNoArg(event, "getPlayer");
		if (player instanceof ServerPlayerEntity sp)
			return sp.getServer();
		return null;
	}

	private static UUID readUuid(Object v)
	{
		if (v instanceof UUID u)
			return u;
		if (v != null) {
			try {
				return UUID.fromString(v.toString());
			} catch (IllegalArgumentException ignored) {}
		}
		return null;
	}

	private static Object firstNonNull(Object... values)
	{
		if (values == null)
			return null;
		for (Object v : values) {
			if (v != null)
				return v;
		}
		return null;
	}

	private static Object callNoArg(Object target, String methodName)
	{
		try {
			Method m = target.getClass().getMethod(methodName);
			return m.invoke(target);
		} catch (Throwable ignored) {
			return null;
		}
	}
}
