package com.siickzz.ktsacademy.messages;

import java.util.Map;
import java.util.UUID;
import java.util.concurrent.ConcurrentHashMap;

public final class PrivateMsgManager {

    private PrivateMsgManager() {}

    private static final Map<UUID, UUID> lastConversation = new ConcurrentHashMap<>();

    public static void setLastConversation(UUID from, UUID to)
    {
        lastConversation.put(from, to);
    }

    public static UUID getLastConversation(UUID player)
    {
        return lastConversation.get(player);
    }

    public static void remove(UUID player)
    {
        lastConversation.remove(player);
    }
}
