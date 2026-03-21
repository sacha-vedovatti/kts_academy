package com.siickzz.ktsacademy.chat;

import com.siickzz.ktsacademy.config.MainConfig;
import com.siickzz.ktsacademy.config.MainConfigManager;
import net.fabricmc.fabric.api.message.v1.ServerMessageEvents;
import net.minecraft.network.message.MessageType;
import net.minecraft.network.message.SignedMessage;
import net.minecraft.server.MinecraftServer;
import net.minecraft.server.network.ServerPlayerEntity;
import net.minecraft.text.Text;

public final class ChatListener {
    private static final ThreadLocal<Boolean> REBROADCAST = ThreadLocal.withInitial(() -> false);

    private ChatListener() {}

    public static void register() {
        ServerMessageEvents.ALLOW_CHAT_MESSAGE.register(ChatListener::onAllowChatMessage);
    }

    private static boolean onAllowChatMessage(SignedMessage message, ServerPlayerEntity sender, MessageType.Parameters params) {
        if (Boolean.TRUE.equals(REBROADCAST.get())) {
            return true;
        }
        MainConfig.ChatConfig chat = MainConfigManager.get().chat;
        if (!chat.enabled) {
            return true;
        }
        MinecraftServer server = sender.getServer();
        if (server == null) {
            return true;
        }

        String rawMessage = message.getSignedContent();
        String rendered = ChatTemplate.apply(chat.format, sender.getGameProfile().getName(), rawMessage, chat.allowMessageFormatting);
        Text baseText = LegacyTextParser.parse(rendered);
        Text finalText = PlaceholderService.apply(baseText, sender, chat.usePlaceholderApi);

        REBROADCAST.set(true);
        try {
            server.getPlayerManager().broadcast(message.withUnsignedContent(finalText), sender, params);
        } finally {
            REBROADCAST.set(false);
        }
        return false;
    }
}

