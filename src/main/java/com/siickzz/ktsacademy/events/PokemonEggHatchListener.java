package com.siickzz.ktsacademy.events;

import com.siickzz.ktsacademy.quests.QuestManager;
import net.minecraft.server.MinecraftServer;
import net.minecraft.server.network.ServerPlayerEntity;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.lang.reflect.Field;
import java.lang.reflect.InvocationHandler;
import java.lang.reflect.Method;
import java.lang.reflect.Proxy;
import java.util.Locale;
import java.util.UUID;

public final class PokemonEggHatchListener {
    private static final Logger LOGGER = LoggerFactory.getLogger("KTSAcademy");
    private static volatile boolean LOGGED_HANDLER_ERROR = false;
    private static volatile boolean LOGGED_EVENT_SHAPE = false;

    private PokemonEggHatchListener() {}

    public static void register()
    {
        String[] candidates = {"HATCH_EGG_POST", "HATCH_EGG_PRE", "HATCH_EGG"};
        boolean hooked = false;
        String hookedName = null;
        for (String name : candidates) {
            if (tryHookCobblemonEvents(name)) {
                hooked = true;
                hookedName = name;
                break;
            }
        }
        if (hooked)
            LOGGER.info("[KTSAcademy] Egg hatch listener hooked successfully ({})", hookedName);
        else
            LOGGER.warn("[KTSAcademy] Could not hook Cobblemon hatch events; hatch quests will not progress.");
    }

    private static boolean tryHookCobblemonEvents(String name)
    {
        return tryHookEvent("com.cobblemon.mod.common.api.events.CobblemonEvents", name);
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
            Object listener = Proxy.newProxyInstance(listenerType.getClassLoader(), new Class<?>[]{listenerType}, new HatchHandler());
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

    private static final class HatchHandler implements InvocationHandler {
        @Override
        public Object invoke(Object proxy, Method method, Object[] args) {
            if (args == null || args.length == 0 || args[0] == null)
                return defaultReturn(method);

            Object event = args[0];
            try {
                if (!LOGGED_EVENT_SHAPE) {
                    LOGGED_EVENT_SHAPE = true;
                    LOGGER.info("[KTSAcademy] Hatch event class: {}", event.getClass().getName());
                }

                ServerPlayerEntity player = resolvePlayer(event);
                Object pokemon = resolvePokemon(event);
                if (player != null)
                    QuestManager.onEggHatched(player, pokemon);
            } catch (Throwable t) {
                if (!LOGGED_HANDLER_ERROR) {
                    LOGGED_HANDLER_ERROR = true;
                    LOGGER.warn("[KTSAcademy] Hatch handler error (logged once)", t);
                }
            }
            return defaultReturn(method);
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

    private static ServerPlayerEntity resolvePlayer(Object event)
    {
        ServerPlayerEntity player = resolvePlayerByMethods(event, "getPlayer", "getHatcher", "getOwner", "getTrainer", "getPlayerEntity");
        if (player != null)
            return player;

        MinecraftServer server = resolveServer(event);
        UUID uuid = readUuid(firstNonNull(
            callNoArg(event, "getPlayerUuid"),
            callNoArg(event, "getHatcherUuid"),
            callNoArg(event, "getOwnerUuid")
        ));
        if (server != null && uuid != null)
            return server.getPlayerManager().getPlayer(uuid);
        return null;
    }

    private static ServerPlayerEntity resolvePlayerByMethods(Object event, String... methodNames)
    {
        for (String n : methodNames) {
            Object v = callNoArg(event, n);
            if (v instanceof ServerPlayerEntity sp)
                return sp;
        }
        return null;
    }

    private static Object resolvePokemon(Object event)
    {
        Object pokemon = firstNonNull(
            callNoArg(event, "getPokemon"),
            callNoArg(event, "getHatchedPokemon"),
            callNoArg(event, "getResult"),
            callNoArg(event, "getPokemonProperties"),
            callNoArg(event, "getProperties")
        );
        if (pokemon != null)
            return pokemon;

        Object egg = callNoArg(event, "getEgg");
        if (egg == null)
            return null;

        Object props = firstNonNull(
            callNoArg(egg, "getPokemonProperties"),
            callNoArg(egg, "getProperties"),
            callNoArg(egg, "getPokemon")
        );
        return props != null ? props : egg;
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
            Method method = target.getClass().getMethod(methodName);
            return method.invoke(target);
        } catch (Throwable ignored) {
            return null;
        }
    }
}

